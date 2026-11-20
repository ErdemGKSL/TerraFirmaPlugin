/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/{CropBlock,DefaultCropBlock,DoubleCropBlock}.java @ c3b4d9536
 *       (canSurvive, getGrowthLimit, postGrowthTick, die)
 */
package dev.tfp.paper.crop;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.crop.CropStages;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.farm.FarmlandState;
import dev.tfp.core.farm.Nutrients;
import dev.tfp.core.farm.SoilTier;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.farm.FarmlandDevice;
import dev.tfp.paper.farm.HydrationSnapshot;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The world side of the crops: what a crop reads around it, the farmland nutrients, and showing a crop's age, its top block and its
 * death. Every method runs on the region thread of the block it is given (the crop and the cell above or below it are in one column).
 */
public final class CropWorld {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Supplier<BlockVisuals> visuals;
    private final Tags tags;
    private final Supplier<DeviceService> devices;
    private final Supplier<Optional<CatchUpService>> catchUp;

    public CropWorld(Scheduler scheduler, CustomBlocks blocks, CustomItems items, Supplier<BlockVisuals> visuals, Tags tags,
            Supplier<DeviceService> devices, Supplier<Optional<CatchUpService>> catchUp) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.items = items;
        this.visuals = visuals;
        this.tags = tags;
        this.devices = devices;
        this.catchUp = catchUp;
    }

    public CustomBlocks blocks() {
        return blocks;
    }

    /** Whether the block below is a farmland block (TFC {@code FARMLANDS}). */
    public boolean farmlandBelow(Block crop) {
        return blocks.stateAt(crop.getRelative(BlockFace.DOWN)).map(s -> FarmlandDevice.kindCovers(s.id())).orElse(false);
    }

    /** TFC {@code canSurvive} of a bottom (or single) block. */
    public boolean canSurviveBottom(Block crop) {
        return farmlandBelow(crop);
    }

    /** What the crop reads: water, light, the cell above and the soil tier. */
    public CropSettle.Environment environment(Block crop, CropType type, BlockStateRef self) {
        Block below = crop.getRelative(BlockFace.DOWN);
        boolean farmland = farmlandBelow(crop);
        HydrationSnapshot water = farmland ? HydrationSnapshot.capture(below, CropWorld::isWater, this::soilHydration) : null;
        Block above = crop.getRelative(BlockFace.UP);
        Optional<BlockStateRef> aboveState = blocks.stateAt(above);
        boolean sameAbove = aboveState.map(s -> s.id().equals(self.id())).orElse(false);
        boolean aboveAir = aboveState.isEmpty() && above.getType().isAir();
        boolean stickTop = sameAbove && aboveState.map(s -> CropIds.hasStick(s) && CropIds.isTop(s)).orElse(false);
        long seed = crop.getWorld().getSeed();
        ChunkClimate chunk = ChunkClimateSource.climateAt(seed, crop.getX(), crop.getZ());
        return new CropSettle.Environment(water, farmland, crop.getLightLevel() >= 12, isWater(crop), aboveAir || sameAbove, stickTop,
            farmland ? soilTier(below) : SoilTier.NORMAL, chunk, seed, new BlockPos(crop.getX(), crop.getY(), crop.getZ()));
    }

    static boolean isWater(Block block) {
        return block.getType() == Material.WATER || block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    private FarmlandRules.SoilHydration soilHydration(Block soil) {
        if (tags.is(soil, "tfc:increases_soil_hydration")) {
            return FarmlandRules.SoilHydration.INCREASES;
        }
        return tags.is(soil, "tfc:decreases_soil_hydration") ? FarmlandRules.SoilHydration.DECREASES : FarmlandRules.SoilHydration.NORMAL;
    }

    /** TFC {@code getSoilModifier}. */
    SoilTier soilTier(Block farmland) {
        if (tags.is(farmland, "tfc:very_rich_farmland")) {
            return SoilTier.VERY_RICH;
        }
        if (tags.is(farmland, "tfc:rich_farmland")) {
            return SoilTier.RICH;
        }
        if (tags.is(farmland, "tfc:poor_farmland")) {
            return SoilTier.POOR;
        }
        return tags.is(farmland, "tfc:very_poor_farmland") ? SoilTier.VERY_POOR : SoilTier.NORMAL;
    }

    @SuppressWarnings("unchecked")
    private Optional<DeviceKind<FarmlandState>> farmKind(Block farmland) {
        return blocks.stateAt(farmland).flatMap(s -> devices.get().kinds().find(s.id())).map(k -> (DeviceKind<FarmlandState>) k);
    }

    /** The nutrients of the farmland below the crop (empty without a readable farmland). */
    public Optional<Nutrients> soil(Block crop) {
        Block farmland = crop.getRelative(BlockFace.DOWN);
        return farmKind(farmland).flatMap(k -> devices.get().edit(farmland, k, (r, now) -> r)).map(r -> r.state().nutrients());
    }

    /** Stores the nutrients the crop left in the farmland below. */
    public void storeSoil(Block crop, Nutrients nutrients) {
        Block farmland = crop.getRelative(BlockFace.DOWN);
        farmKind(farmland).ifPresent(k -> devices.get().edit(farmland, k, (r, now) -> r.withState(r.state().withNutrients(nutrients))));
    }

    /** Lists the crop for catch-up on chunk load. */
    public void track(Block crop) {
        catchUp.get().ifPresent(c -> c.track(crop, CropDevice.CATCH_UP_KIND));
    }

    /**
     * TFC {@code postGrowthTick}: the crop shows the age of its growth; a two-block crop grows its top block once past the bottom's last
     * age, when the cell above is free.
     */
    public void show(Block crop, CropType type, BlockStateRef current, float growth) {
        CropStages.Stage stage = type.stages().stage(growth);
        BlockStateRef wanted = CropIds.withAge(current, stage.age());
        if (!wanted.equals(current)) {
            blocks.stateAt(crop).ifPresent(now -> set(crop, wanted, now));
        }
        if (CropIds.doubled(type) && stage.age() > type.stages().maxSingleAge()) {
            Block above = crop.getRelative(BlockFace.UP);
            Optional<BlockStateRef> aboveState = blocks.stateAt(above);
            BlockStateRef top = CropIds.top(wanted, stage.age());
            if (aboveState.isEmpty() && above.getType().isAir()) {
                blocks.place(above, top, true);
            } else if (aboveState.isPresent() && aboveState.get().id().equals(wanted.id()) && !aboveState.get().equals(top)) {
                set(above, top, aboveState.get());
            }
        }
    }

    private void set(Block block, BlockStateRef wanted, BlockStateRef now) {
        if (wanted.equals(now)) {
            return;
        }
        Optional<BlockData> data = visuals.get().blockData(wanted);
        data.ifPresent(d -> block.setBlockData(d, false));
    }

    /**
     * TFC {@code die}: the crop turns into its dead block. A fully grown two-block crop gets a dead top when the cell above is free or its
     * own; otherwise its top is removed (a climbing crop keeps a dead top whenever it has one).
     */
    public void die(Block crop, CropType type, BlockStateRef living, boolean fullyGrown) {
        Block above = crop.getRelative(BlockFace.UP);
        Optional<BlockStateRef> aboveState = blocks.stateAt(above);
        boolean sameAbove = aboveState.map(s -> s.id().equals(living.id())).orElse(false);
        boolean aboveFree = aboveState.isEmpty() && above.getType().isAir();
        switch (type.shape()) {
            case DOUBLE -> {
                if (fullyGrown && (aboveFree || sameAbove)) {
                    blocks.place(above, CropIds.dead(type, living, true, true), false);
                } else if (sameAbove) {
                    blocks.remove(above);
                }
            }
            case CLIMBING, PICKABLE_CLIMBING -> {
                if (sameAbove) {
                    blocks.place(above, CropIds.dead(type, living, fullyGrown, true), false);
                }
            }
            default -> {
            }
        }
        blocks.place(crop, CropIds.dead(type, living, fullyGrown, false), true);
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    public void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** The fruit stack: item {@code tfp:food/<name>}, {@code count} of them. */
    public Optional<ItemStack> fruit(String name, int count) {
        return items.create("tfp:food/" + name, Math.max(1, count));
    }

    public Scheduler scheduler() {
        return scheduler;
    }
}
