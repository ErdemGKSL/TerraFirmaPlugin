/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536 (getFog)
 */
package dev.tfp.paper.climate.fog;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * PAR-CLIM-14: {@code /tfp debug fog [force]}, the fog reading at the player's eye (TFC {@code getFog}: today's roll, the
 * 4-6-10-12 ramp, the rainfall and sky light scaling, the planes TFC's client sets at render distance 12). Nothing is sent
 * to a client: the vanilla-client spike for biome fog distance has not run (ledger), so a vanilla client keeps vanilla fog.
 */
public final class FogModule implements FeatureModule {
    public static final String ID = "climate_fog";

    private Scheduler scheduler;
    private Supplier<ClockSnapshot> clock;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        this.scheduler = context.scheduler();
        this.clock = context.clock()::get;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("fog", node -> node
            .executes(tree.run(ctx -> describe(ctx.getSource().getSender() instanceof Player p ? p : null, false)))
            .then(Commands.literal("force")
                .executes(tree.run(ctx -> describe(ctx.getSource().getSender() instanceof Player p ? p : null, true)))));
    }

    private CompletableFuture<Reply> describe(Player player, boolean force) {
        if (player == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.fog.players_only"));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(player, () -> {
            try {
                Location eye = player.getEyeLocation();
                World world = player.getWorld();
                int sky = eye.getBlock().getLightFromSky();
                ClockSnapshot snapshot = clock.get();
                if (world.getEnvironment() != World.Environment.NORMAL || !snapshot.isSet()) {
                    out.complete(Reply.error("debug.fog.unknown"));
                    return;
                }
                FogForecast.Reading r = FogForecast.at(world.getSeed(), eye.getBlockX(), eye.getBlockZ(), snapshot, sky, force);
                out.complete(Reply.ok("debug.fog.done", r.foggyDay() ? "yes" : "no", String.format(Locale.ROOT, "%.3f", r.dayStrength()),
                    String.format(Locale.ROOT, "%.3f", r.fog()), r.level(), FogForecast.LEVELS,
                    String.format(Locale.ROOT, "%.0f", r.farPlane()), String.format(Locale.ROOT, "%.0f", r.nearPlane()), sky));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
        return out;
    }
}
