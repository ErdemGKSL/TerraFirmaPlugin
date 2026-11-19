/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import dev.tfp.paper.worldgen.TfcWorldRules;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * {@code world.spawn_rules} (PAR-WORLD-16): the TFC world has TFC's forced game rules (no natural regeneration,
 * phantoms, patrols, traders) and its world spawn is the generator's cached land spawn.
 */
public final class SpawnRulesCase implements SelfTestCase {
    @Override
    public String name() {
        return "world.spawn_rules: TFC game rules forced, world spawn is the TFC spawn (PAR-WORLD-16)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(20);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        CompletableFuture<String> out = new CompletableFuture<>();
        ctx.tfp().scheduler().global(() -> {
            try {
                World world = Bukkit.getWorlds().get(0);
                if (!(world.getGenerator() instanceof TfcChunkGenerator generator)) {
                    out.complete("skipped: the first world is not a TFC world");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (String[] names : TfcWorldRules.FORCED_FALSE) {
                    GameRule<Boolean> rule = TfcWorldRules.find(names[0], names[1]);
                    if (rule == null) {
                        sb.append(names[0]).append("=absent ");
                        continue;
                    }
                    if (!Boolean.FALSE.equals(world.getGameRuleValue(rule))) {
                        throw new AssertionError("game rule " + names[0] + " is not false");
                    }
                    sb.append(names[0]).append("=false ");
                }
                Location spawn = world.getSpawnLocation();
                Location cached = generator.tfcSpawnLocationIfCached(world).orElse(null);
                if (cached != null && (spawn.getBlockX() != cached.getBlockX() || spawn.getBlockZ() != cached.getBlockZ())) {
                    throw new AssertionError("world spawn " + spawn + " differs from the TFC spawn " + cached);
                }
                out.complete(sb + "spawn=" + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ()
                        + (cached == null ? " (no cached TFC spawn)" : ""));
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }
}
