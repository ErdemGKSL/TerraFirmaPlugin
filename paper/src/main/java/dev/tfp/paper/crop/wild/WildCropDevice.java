/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/{WildCropBlock,WildSpreadingCropBlock}.java @ c3b4d9536
 *       (getStateForPlacement, randomTick, updateShape, canSurvive)
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * The wild crops {@code tfp:wild_crop/<crop>} (PAR-FARM-05) as one device kind. TFC's random tick turns {@code mature} when the hemispheric
 * month crosses the window; here nothing ticks: the block is brought to the month of the calendar when it is placed, on a neighbour update
 * and by the catch-up kind of the same id (chunk load, visible-area sweep). The wild pumpkin and melon also show a fruit block on each side
 * ({@code WildSpreadingCropBlock.updateShape}). A wild crop whose ground no longer carries it is removed ({@code canSurvive}).
 */
public final class WildCropDevice {
    public static final String ID = "tfp:wild_crop";
    public static final String CATCH_UP_KIND = ID;
    public static final String MATURE = "mature";

    /** The state the record holds: the calendar tick of the last sync (unused for decisions; the block shows the month). */
    static final VersionedCodec<Long> CODEC = VersionedCodec.<Long>builder("wild_crop", 1)
        .build((tick, out) -> out.putLong("tick", tick), in -> in.getLong("tick", 0L));

    public record Services(WildWorld world, Supplier<ClockSnapshot> clock) {
    }

    private WildCropDevice() {
    }

    /** The crop of a wild block id. */
    public static Optional<CropType> type(String blockId) {
        if (!blockId.startsWith(ID + "/")) {
            return Optional.empty();
        }
        try {
            return Optional.of(CropType.valueOf(blockId.substring(ID.length() + 1).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The fruit block a wild spreading crop's sides look for, or null for the other crops. */
    public static String fruitOf(CropType type) {
        return type.shape() == CropType.Shape.SPREADING ? FruitDevice.fruitId(type) : null;
    }

    public static DeviceKind<Long> kind(Services services) {
        return DeviceKind.builder(ID, CODEC, 0L).slots(0).removal(DeviceKind.RemoveBehaviour.NOOP).handler(new Handler(services)).build();
    }

    /**
     * Brings one wild block to the month and its sides to the neighbours. Returns false when the block was removed or is not a wild crop.
     * Region thread of {@code block}.
     */
    public static boolean settle(Services s, Block block, BlockStateRef state) {
        Optional<CropType> type = type(state.id());
        if (type.isEmpty()) {
            return false;
        }
        WildWorld world = s.world();
        if (!world.wildGrowsOn(block.getRelative(BlockFace.DOWN))) {
            world.remove(block);
            world.untrack(block);
            return false;
        }
        boolean mature = WildRules.isMature(WildWorld.month(block, s.clock().get()));
        BlockStateRef wanted = state.with(MATURE, Boolean.toString(mature));
        if (!wanted.equals(state)) {
            world.show(block, wanted);
        }
        String fruit = fruitOf(type.get());
        if (fruit != null) {
            world.updateSides(block, wanted, fruit);
        }
        world.track(block, CATCH_UP_KIND);
        return true;
    }

    private record Handler(Services services) implements DeviceHandler<Long> {
        /** TFC {@code getStateForPlacement}: mature when the month is in the window. */
        @Override
        public DeviceResult<Long> placed(DeviceContext<Long> ctx) {
            if (type(ctx.state().id()).isEmpty()) {
                return DeviceResult.pass();
            }
            settle(services, ctx.block(), ctx.state());
            return DeviceResult.update(ctx.record().withState(services.clock().get().calendarTicks()));
        }

        @Override
        public DeviceResult<Long> neighbourChanged(DeviceContext<Long> ctx) {
            if (type(ctx.state().id()).isPresent()) {
                settle(services, ctx.block(), ctx.state());
            }
            return DeviceResult.pass();
        }
    }
}
