/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/BananaFeature.java @ c3b4d9536
 */
package dev.tfp.paper.banana;

import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fruittree.GenPlanner;
import dev.tfp.paper.fruittree.GenTrees;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-FARM-27: writes a natural banana plant. TFC's {@code BananaFeature}, where the block below the origin is
 * {@code tfc:bush_plantable_on}, stacks healthy plant blocks upward: {@code height - 1} blocks of stage 0, of stage 1, then one of
 * stage 2 ({@code height} is 2 or 3 each time, drawn by the planner). Every block is its column's stem and is settled at once.
 * Region thread of the origin's chunk.
 */
public final class GenBananas {
    private final BananaDevice.Services services;
    private final DeviceService devices;
    private final CustomBlocks blocks;

    public GenBananas(BananaDevice.Services services, DeviceService devices, CustomBlocks blocks) {
        this.services = services;
        this.devices = devices;
        this.blocks = blocks;
    }

    /** The stages of the column from the bottom, for the drawn heights (stage 0 and 1 each {@code height - 1} blocks, then one of 2). */
    public static List<Integer> column(List<Integer> heights) {
        List<Integer> out = new ArrayList<>();
        for (int stage = 0; stage <= 2; stage++) {
            for (int k = 1; k < heights.get(stage); k++) {
                out.add(stage);
                if (stage == 2) {
                    return out;
                }
            }
        }
        return out;
    }

    /** @return whether the plant was written */
    public boolean commit(World world, GenPlanner.Spot spot) {
        if (spot.draws().size() < 3) {
            return false;
        }
        Block origin = world.getBlockAt(spot.x(), spot.y(), spot.z());
        BananaEffects fx = services.effects();
        if (!fx.plantable(origin.getRelative(0, -1, 0))) {
            return false;
        }
        List<Integer> stages = column(spot.draws());
        for (int i = 0; i < stages.size(); i++) {
            if (!GenTrees.replaceable(blocks, origin.getRelative(0, i, 0))) {
                return false;
            }
        }
        BerryEffects.Environment env = fx.environment(origin, origin.getRelative(0, -1, 0));
        @SuppressWarnings("unchecked")
        DeviceKind<FruitTreeState> kind = devices.kinds().find(BananaDevice.PLANT_ID).map(k -> (DeviceKind<FruitTreeState>) k).orElse(null);
        boolean any = false;
        for (int i = 0; i < stages.size(); i++) {
            Block block = origin.getRelative(0, i, 0);
            BlockStateRef ref = BananaDevice.plant(stages.get(i), Lifecycle.HEALTHY);
            fx.place(block, ref, null);
            if (blocks.stateAt(block).filter(ref::equals).isEmpty()) {
                break; // CraftEngine does not know the block (content not loaded)
            }
            any = true;
            fx.track(block);
            if (kind != null) {
                BananaDevice.Parsed parsed = BananaDevice.parse(ref).orElseThrow();
                devices.edit(block, kind, (record, now) -> {
                    FruitTreeState fresh = new FruitTreeState(origin.getX(), origin.getY(), origin.getZ(), now, BerryState.NEVER_PICKED,
                        env.hydration(), env.temperature(), env.northern());
                    return BananaDevice.tick(services, block, parsed, fresh, now, 0).map(record::withState).orElse(record.withState(fresh));
                });
            }
        }
        return any;
    }
}
