/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBlock.java @ c3b4d9536 (entityInside),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCPassableCactusBlock.java @ c3b4d9536 (entityInside),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBedBlock.java @ c3b4d9536 (entityInside)
 */
package dev.tfp.paper.plant;

import dev.tfp.core.plant.CactusContact;
import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.BoundingBox;

/**
 * PAR-FARM-12: standing in a cactus hurts. Vanilla's {@code entityInside} runs every tick for each block the entity's box overlaps and
 * the invulnerability after a hit lets one in ten through; here a player is checked every {@value CactusContact#PERIOD_TICKS} ticks on
 * its own thread, which is the same cadence. Creative and spectator players are not hurt (the TFC needs are off there). Only players are
 * checked: mobs that stand in a cactus are not hurt (ledger).
 */
public final class CactusContactTask implements Listener {
    private static final double EPS = 1.0e-5;

    private final CustomBlocks blocks;
    private final Scheduler scheduler;

    public CactusContactTask(CustomBlocks blocks, Scheduler scheduler) {
        this.blocks = blocks;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    /** Starts the check of one player (also for the players online when the module is enabled). */
    public void start(Player player) {
        scheduler.entityRepeating(player, CactusContact.PERIOD_TICKS, CactusContact.PERIOD_TICKS, () -> tick(player), () -> {
        });
    }

    private void tick(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isDead() || !player.isValid()) {
            return;
        }
        if (touches(player.getWorld(), player.getBoundingBox())) {
            player.damage(CactusContact.DAMAGE, DamageSource.builder(DamageType.CACTUS).build());
        }
    }

    /** Whether the box overlaps a cell holding a plant that hurts. */
    boolean touches(World world, BoundingBox box) {
        for (int x = (int) Math.floor(box.getMinX() + EPS); x <= (int) Math.floor(box.getMaxX() - EPS); x++) {
            for (int y = (int) Math.floor(box.getMinY() + EPS); y <= (int) Math.floor(box.getMaxY() - EPS); y++) {
                for (int z = (int) Math.floor(box.getMinZ() + EPS); z <= (int) Math.floor(box.getMaxZ() - EPS); z++) {
                    try {
                        Optional<BlockStateRef> state = blocks.stateAt(world.getBlockAt(x, y, z));
                        if (state.isPresent()) {
                            PlantType type = PlantType.of(state.get().id());
                            if (type != null && CactusContact.hurts(type)) {
                                return true;
                            }
                        }
                    } catch (IllegalStateException e) {
                        // a cell owned by a neighbouring region: its own player's check covers it
                    }
                }
            }
        }
        return false;
    }
}
