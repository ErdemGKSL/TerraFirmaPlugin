/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/SpreadingCropBlock.java @ c3b4d9536 (getGrowthLimit, postGrowthTick, updateShape)
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.core.berry.SpreadRules.Side;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * The part of the living pumpkin and melon crops that lives with the fruit (PAR-FARM-05), provided as a module service for the crop
 * device: the growth limit under crowding, the fruit a fully grown crop sets on the ground beside it, and the fruit sides of the crop block.
 * Region thread of the crop block.
 */
public final class SpreadingFruit {
    private final FruitDevice.Services fruit;
    private final Supplier<DeviceService> devices;
    private final Supplier<DeviceKind<FruitDevice.FruitState>> kind;
    private final RandomGenerator random;

    public SpreadingFruit(FruitDevice.Services fruit, Supplier<DeviceService> devices, Supplier<DeviceKind<FruitDevice.FruitState>> kind,
            RandomGenerator random) {
        this.fruit = fruit;
        this.devices = devices;
        this.kind = kind;
        this.random = random;
    }

    /** TFC {@code getGrowthLimit}: more than two fruit blocks around the crop cap the limit at 0.9. */
    public float growthLimit(Block crop, CropType type, float baseLimit) {
        return WildRules.growthLimit(fruit.world().countAround(crop, FruitDevice.fruitId(type)), baseLimit);
    }

    /**
     * TFC {@code postGrowthTick}: a crop at growth 1 sets its fruit on a random side when the ground takes it. Returns the growth the crop
     * falls back to when a fruit was placed.
     *
     * @param now player tick, the creation date of the new fruit (TFC's {@code setStack(new ItemStack(fruit))} stamps it now)
     */
    public OptionalDouble afterGrowth(Block crop, CropType type, float growth, long now) {
        WildWorld world = fruit.world();
        WildRules.Spread spread = WildRules.spread(growth, random, side -> {
            Block target = WildWorld.side(crop, side);
            return world.fruitGrowsOn(target.getRelative(BlockFace.DOWN)) && world.replaceable(target);
        });
        if (!spread.place()) {
            return OptionalDouble.empty();
        }
        Block target = WildWorld.side(crop, spread.side());
        if (!world.place(target, BlockStateRef.of(FruitDevice.fruitId(type)), true)) {
            return OptionalDouble.empty();
        }
        FruitDevice.FruitState state = new FruitDevice.FruitState(FoodDecay.roundedCreationDate(now, fruit.config().stackTicks()), false);
        devices.get().edit(target, kind.get(), (record, at) -> record.withState(state));
        world.track(target, FruitDevice.CATCH_UP_KIND);
        return OptionalDouble.of(spread.growth());
    }

    /** TFC {@code updateShape}: the four side properties of the living crop show where its fruit is. */
    public void updateSides(Block crop, CropType type, BlockStateRef current) {
        fruit.world().updateSides(crop, current, FruitDevice.fruitId(type));
    }

    /** The fruit block beside a crop on {@code side}, if any (for readouts and tests). */
    public Optional<BlockStateRef> fruitAt(Block crop, Side side) {
        return fruit.world().state(WildWorld.side(crop, side)).filter(s -> FruitDevice.type(s.id()).isPresent());
    }

    FoodConfig config() {
        return fruit.config();
    }
}
