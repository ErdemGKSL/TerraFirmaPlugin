/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/FruitTreeFeature.java @ c3b4d9536
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-27: writes a natural fruit tree. TFC's {@code FruitTreeFeature} puts a growing branch with {@code natural = true} and two to
 * four saplings where the block below is {@code tfc:bush_plantable_on} and the ten cells from the origin up are worldgen-replaceable,
 * and adds 300 days to its tick counter so that it grows at once. Here the branch carries a device record whose last update is 300 days
 * old, and its first settle (the same function the catch-up runs) grows the tree. Region thread of the origin's chunk.
 */
public final class GenTrees {
    /** TFC: {@code ICalendar.CALENDAR_TICKS_IN_DAY * 300} added to the counter. */
    public static final long HEAD_START_TICKS = 300L * 24_000L;
    /** The cells checked from the origin up ({@code for j = 1..10}). */
    public static final int CLEAR_CELLS = 10;

    private final FruitTreeDevice.Services services;
    private final DeviceService devices;
    private final CustomBlocks blocks;

    public GenTrees(FruitTreeDevice.Services services, DeviceService devices, CustomBlocks blocks) {
        this.services = services;
        this.devices = devices;
        this.blocks = blocks;
    }

    /** Whether a cell is air or a replaceable vanilla block (TFC {@code EnvironmentHelpers.isWorldgenReplaceable}); never a custom block. */
    public static boolean replaceable(CustomBlocks blocks, Block block) {
        return blocks.stateAt(block).isEmpty() && (block.getType().isAir() || block.isReplaceable());
    }

    /** @return whether the tree was written */
    public boolean commit(World world, GenPlanner.Spot spot) {
        Optional<FruitTree> tree = spot.feature().tree();
        if (tree.isEmpty() || spot.draws().isEmpty()) {
            return false;
        }
        Block origin = world.getBlockAt(spot.x(), spot.y(), spot.z());
        FruitTreeEffects fx = services.effects();
        if (!fx.plantable(origin.getRelative(0, -1, 0))) {
            return false;
        }
        for (int j = 0; j < CLEAR_CELLS; j++) {
            if (!replaceable(blocks, origin.getRelative(0, j, 0))) {
                return false;
            }
        }
        BlockStateRef ref = FruitTreeDevice.growing(tree.get(), 0, spot.draws().get(0), true, FruitTreeDevice.connections(fx, origin));
        fx.place(origin, ref, null);
        if (blocks.stateAt(origin).filter(ref::equals).isEmpty()) {
            return false; // CraftEngine does not know the block (content not loaded): nothing was written
        }
        fx.track(origin);
        BerryEffects.Environment env = fx.environment(origin);
        @SuppressWarnings("unchecked")
        DeviceKind<FruitTreeState> kind = devices.kinds().find(ref.id()).map(k -> (DeviceKind<FruitTreeState>) k).orElse(null);
        if (kind == null) {
            return true;
        }
        FruitTreeDevice.Parsed parsed = FruitTreeDevice.parse(ref).orElseThrow();
        devices.edit(origin, kind, (record, now) -> {
            FruitTreeState fresh = new FruitTreeState(origin.getX(), origin.getY(), origin.getZ(), now - HEAD_START_TICKS,
                BerryState.NEVER_PICKED, env.hydration(), env.temperature(), env.northern());
            return FruitTreeDevice.tick(services, origin, parsed, fresh, now, 0).map(record::withState).orElse(record.withState(fresh));
        });
        return true;
    }
}
