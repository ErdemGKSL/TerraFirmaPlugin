/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

/**
 * {@code loot.vanilla_overrides} (PAR-INFRA-30): TFC's replacements of vanilla loot tables, shipped by datagen in the
 * bootstrap datapack under their {@code minecraft:} ids, are the server's own tables.
 *
 * <p>Blocks go through {@link Block#getDrops(ItemStack)} (the server's loot for a break with that tool): glass and a glass
 * pane by hand drop themselves (vanilla: nothing); a snow layer and a snow block with an iron shovel drop nothing (vanilla:
 * snowballs), with a silk-touch shovel themselves. Entities: a spawned zombie and drowned roll their {@code minecraft:}
 * table {@value #ROLLS} times with no killer and must give only 0-2 rotten flesh each time. (Vanilla's rare
 * iron/carrot/potato need a player kill, which the self-test has no player for; the block checks prove the datapack's
 * tables replace vanilla's.)
 */
public final class VanillaLootCase implements SelfTestCase {
    static final int CHUNK_OFFSET = 60;
    static final int ROLLS = 64;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "loot.vanilla_overrides: TFC's glass, pane, snow, zombie and drowned tables replace vanilla's (PAR-INFRA-30)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(6 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        Scheduler s = ctx.tfp().scheduler();
        return OnThread.script("vanilla-loot", () -> {
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            int cz = spawn.getBlockZ() >> 4;
            int x = (cx << 4) + 8;
            int z = (cz << 4) + 8;
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            try {
                List<String> log = new ArrayList<>();
                OnThread.region(s, world, cx, cz, STEP, () -> {
                    int y = Math.min(world.getHighestBlockYAt(x, z) + 8, world.getMaxHeight() - 4);
                    Block b = world.getBlockAt(x, y, z);
                    ItemStack hand = new ItemStack(Material.AIR);
                    ItemStack shovel = new ItemStack(Material.IRON_SHOVEL);
                    ItemStack silk = new ItemStack(Material.IRON_SHOVEL);
                    silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
                    try {
                        log.add(blockCheck(b, Material.GLASS, hand, "by hand", Map.of("minecraft:glass", 1)));
                        log.add(blockCheck(b, Material.WHITE_STAINED_GLASS_PANE, hand, "by hand",
                                Map.of("minecraft:white_stained_glass_pane", 1)));
                        log.add(blockCheck(b, Material.SNOW, shovel, "with a shovel", Map.of()));
                        log.add(blockCheck(b, Material.SNOW_BLOCK, shovel, "with a shovel", Map.of()));
                        log.add(blockCheck(b, Material.SNOW, silk, "with silk touch", Map.of("minecraft:snow", 1)));
                    } finally {
                        b.setType(Material.AIR, false);
                    }
                    Location at = new Location(world, x + 0.5, y + 0.1, z + 0.5);
                    log.add(entityCheck(world, at, Zombie.class, "zombie"));
                    log.add(entityCheck(world, at, Drowned.class, "drowned"));
                    return null;
                });
                return String.join("; ", log);
            } finally {
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
            }
        });
    }

    private static String blockCheck(Block b, Material type, ItemStack tool, String how, Map<String, Integer> expected) {
        b.setType(type, false);
        Map<String, Integer> seen = byId(b.getDrops(tool));
        String what = type.getKey() + " " + how;
        if (!seen.equals(expected)) {
            throw new AssertionError(what + " drops " + seen + ", expected " + expected
                    + " (TFC's minecraft: loot table is not active: is the tfp_seasonal_biomes datapack enabled?)");
        }
        return what + " -> " + seen;
    }

    private static <T extends Entity> String entityCheck(World world, Location at, Class<T> type, String path) {
        T mob = world.spawn(at, type, e -> e.setPersistent(false));
        try {
            if (!mob.isValid()) {
                throw new AssertionError("could not spawn a " + path + " (cancelled by a plugin?)");
            }
            LootTable table = Bukkit.getLootTable(NamespacedKey.minecraft("entities/" + path));
            if (table == null) {
                throw new AssertionError("no minecraft:entities/" + path + " loot table");
            }
            Random random = new Random(30L);
            int max = 0;
            for (int i = 0; i < ROLLS; i++) {
                LootContext context = new LootContext.Builder(at).lootedEntity(mob).build();
                Map<String, Integer> seen = byId(table.populateLoot(random, context));
                int flesh = seen.getOrDefault("minecraft:rotten_flesh", 0);
                if (seen.size() > 1 || (!seen.isEmpty() && flesh == 0) || flesh > 2) {
                    throw new AssertionError(path + " rolled " + seen + ": TFC's table gives only 0-2 rotten flesh");
                }
                max = Math.max(max, flesh);
            }
            return path + ": " + ROLLS + " rolls, only rotten flesh (max " + max + ")";
        } finally {
            mob.remove();
        }
    }

    private static Map<String, Integer> byId(Collection<ItemStack> stacks) {
        Map<String, Integer> out = new TreeMap<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) {
                out.merge(stack.getType().getKey().toString(), stack.getAmount(), Integer::sum);
            }
        }
        return out;
    }
}
