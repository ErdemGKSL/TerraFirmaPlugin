/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CropBlockEntity.java @ c3b4d9536 (the stored crop, growth by calendar update),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/CropBlock.java @ c3b4d9536 (tick: canSurvive, growthTick),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/{PickableCropBlock,PickableClimbingCropBlock,IPickableCrop}.java @ c3b4d9536 (picking)
 */
package dev.tfp.paper.crop;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.crop.CropGrowth;
import dev.tfp.core.crop.CropHarvest;
import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The living crops (PAR-FARM-04) as one device kind {@code tfp:crop}: the blocks are {@code tfp:crop/<crop>} (PAR-FARM-03). The record is
 * TFC's {@code CropBlockEntity} state ({@link CropPlant}); nothing ticks. Whenever the block is visited (chunk load and the visible-area
 * sweep through the catch-up kind, a neighbour update, a click) the crop is brought up to the calendar tick of the clock in one pass
 * ({@link CropGrowth#advance}), consuming the farmland's nutrients as it goes, and its age, top block or death are applied to the world.
 *
 * <p>The top block of a two-block crop carries no state of its own; the bottom block owns the crop.
 */
public final class CropDevice {
    public static final String ID = CropIds.LIVING;
    public static final String CATCH_UP_KIND = ID;

    /** What the handler needs. */
    public record Services(CropWorld world, Supplier<ClockSnapshot> clock, Supplier<DeviceService> devices, RandomGenerator random) {
        public Services {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(random, "random");
        }
    }

    private CropDevice() {
    }

    /** The device kind; the record's own last-update tick is unused (the crop keeps a calendar tick). */
    public static DeviceKind<CropPlant> kind(Services services) {
        return DeviceKind.builder(ID, CropPlantCodec.CODEC, CropPlant.planted(0L))
            .slots(0)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(services))
            .build();
    }

    /** What a settle decided: the new crop, and the block state it was settled at. */
    public record Settled(CropGrowth.Result result, BlockStateRef state, CropType type) {
    }

    /**
     * The catch-up of one bottom block: reads the world, takes the soil from the farmland, advances the crop and gives the soil back.
     * Region thread of {@code block}. Dead results are applied by {@link #apply}.
     */
    public static Settled settle(Services s, Block block, BlockStateRef state, CropType type, CropPlant stored) {
        CropWorld world = s.world();
        CropSettle.Environment env = world.environment(block, type, state);
        Nutrients soil = env.farmland() ? world.soil(block).orElse(null) : null;
        CropGrowth.Result result = CropSettle.settle(type, env, stored, soil, s.clock().get());
        if (soil != null && result.soil() != null && !result.soil().equals(soil)) {
            world.storeSoil(block, result.soil());
        }
        return new Settled(result, state, type);
    }

    /** Shows the result in the world after the record was stored. */
    public static void apply(Services s, Block block, Settled settled) {
        CropWorld world = s.world();
        CropGrowth.Result r = settled.result();
        if (r.dead()) {
            world.die(block, settled.type(), settled.state(), r.fullyGrown());
            return;
        }
        world.show(block, settled.type(), settled.state(), r.plant().state().growth());
        world.track(block);
    }

    /** The result of a pick. */
    public record Pick(CropPlant plant, ItemStack stack, boolean picked) {
    }

    /**
     * TFC's pick: at {@code maxAge - 1} the first fruit (if the crop has one), at {@code maxAge} the second. The growth falls back to a
     * band, the yield resets, and the player gets {@code floor(lerp(yield, 1, 5) + nextInt(2))} fruit.
     */
    static Pick pick(Services s, CropType type, BlockStateRef state, CropPlant plant) {
        CropHarvest.Fruits fruits = CropHarvest.fruits(type);
        if (fruits == null) {
            return new Pick(plant, null, false);
        }
        CropHarvest.Fruit which = CropHarvest.fruit(CropIds.age(state), type.stages().maxAge(), fruits.first() != null);
        if (which == CropHarvest.Fruit.NONE) {
            return new Pick(plant, null, false);
        }
        String name = which == CropHarvest.Fruit.FIRST ? fruits.first() : fruits.second();
        float yield = plant.state().yield();
        float growth = CropHarvest.regrowGrowth(which, s.random().nextFloat());
        int count = CropHarvest.pickCount(yield, s.random().nextInt(2));
        Optional<ItemStack> stack = s.world().fruit(name, count);
        if (stack.isEmpty()) {
            return new Pick(plant, null, false);
        }
        return new Pick(plant.picked(growth), stack.get(), true);
    }

    private static Block bottomOf(Block block, BlockStateRef state) {
        return CropIds.isTop(state) ? block.getRelative(BlockFace.DOWN) : block;
    }

    private record Handler(Services services) implements DeviceHandler<CropPlant> {
        /** TFC {@code CropBlockEntity} constructor: the growth clock starts at planting. */
        @Override
        public DeviceResult<CropPlant> placed(DeviceContext<CropPlant> ctx) {
            Optional<CropType> type = CropIds.type(ctx.state().id());
            if (type.isEmpty() || CropIds.isTop(ctx.state())) {
                return DeviceResult.pass();
            }
            services.world().track(ctx.block());
            return DeviceResult.update(ctx.record().withState(CropPlant.planted(services.clock().get().calendarTicks())));
        }

        /** TFC {@code PickableCropBlock.useItemOn}: a ripe pickable crop is picked by hand; the top part passes to the bottom. */
        @Override
        public DeviceResult<CropPlant> useWithItem(DeviceContext<CropPlant> ctx) {
            Optional<CropType> type = CropIds.type(ctx.state().id());
            Player player = ctx.player();
            if (type.isEmpty() || player == null || CropHarvest.fruits(type.get()) == null) {
                return DeviceResult.pass();
            }
            if (CropIds.isTop(ctx.state())) {
                return pickBelow(ctx, type.get(), player);
            }
            Settled settled = settle(services, ctx.block(), ctx.state(), type.get(), ctx.deviceState());
            if (settled.result().dead()) {
                apply(services, ctx.block(), settled);
                return DeviceResult.pass();
            }
            Pick pick = pick(services, type.get(), ctx.state(), settled.result().plant());
            CropPlant plant = pick.plant();
            if (pick.picked()) {
                services.world().give(player, pick.stack());
            }
            apply(services, ctx.block(), new Settled(new CropGrowth.Result(plant, settled.result().soil(), false, false, 0L),
                ctx.state(), type.get()));
            DeviceRecord<CropPlant> record = ctx.record().withState(plant);
            return pick.picked() ? DeviceResult.consume(record) : DeviceResult.update(record);
        }

        private DeviceResult<CropPlant> pickBelow(DeviceContext<CropPlant> ctx, CropType type, Player player) {
            Block bottom = ctx.block().getRelative(BlockFace.DOWN);
            Optional<BlockStateRef> bottomState = services.world().blocks().stateAt(bottom);
            if (bottomState.isEmpty() || !bottomState.get().id().equals(ctx.state().id())) {
                return DeviceResult.pass();
            }
            boolean[] picked = {false};
            services.devices().get().edit(bottom, ctx.kind(), (record, now) -> {
                Settled settled = settle(services, bottom, bottomState.get(), type, record.state());
                if (settled.result().dead()) {
                    apply(services, bottom, settled);
                    return record;
                }
                Pick pick = pick(services, type, bottomState.get(), settled.result().plant());
                if (pick.picked()) {
                    services.world().give(player, pick.stack());
                    picked[0] = true;
                }
                apply(services, bottom, new Settled(new CropGrowth.Result(pick.plant(), settled.result().soil(), false, false, 0L),
                    bottomState.get(), type));
                return record.withState(pick.plant());
            });
            return picked[0] ? DeviceResult.consume() : DeviceResult.pass();
        }

        /** TFC {@code tick}/{@code canSurvive}: a neighbour update visits the crop. */
        @Override
        public DeviceResult<CropPlant> neighbourChanged(DeviceContext<CropPlant> ctx) {
            Optional<CropType> type = CropIds.type(ctx.state().id());
            if (type.isEmpty()) {
                return DeviceResult.pass();
            }
            Block block = ctx.block();
            if (CropIds.isTop(ctx.state())) {
                Optional<BlockStateRef> below = services.world().blocks().stateAt(block.getRelative(BlockFace.DOWN));
                boolean supported = below.map(b -> b.id().equals(ctx.state().id()) && !CropIds.isTop(b)).orElse(false);
                if (!supported) {
                    services.world().scheduler().region(block.getLocation(), () -> services.world().blocks().remove(block));
                }
                return DeviceResult.pass();
            }
            if (!services.world().canSurviveBottom(block)) {
                services.world().scheduler().region(block.getLocation(), () -> services.world().blocks().remove(block));
                return DeviceResult.pass();
            }
            Settled settled = settle(services, block, ctx.state(), type.get(), ctx.deviceState());
            apply(services, block, settled);
            if (settled.result().dead()) {
                return DeviceResult.pass();
            }
            return settled.result().plant().equals(ctx.deviceState()) ? DeviceResult.pass()
                : DeviceResult.update(ctx.record().withState(settled.result().plant()));
        }
    }
}
