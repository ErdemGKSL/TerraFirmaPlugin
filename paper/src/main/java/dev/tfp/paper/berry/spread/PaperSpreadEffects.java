/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (mayPlaceOn: SPREADING_FRUIT_GROWS_ON),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java @ c3b4d9536 (mayPlaceOn: SEA_BUSH_PLANTABLE_ON),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (getFruitBushHydration)
 */
package dev.tfp.paper.berry.spread;

import dev.tfp.core.berry.BerryState;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link SpreadEffects}. The visual state of a block that keeps its id is written with {@code setBlockData(data, false)}
 * while it is still our CraftEngine block (CraftEngine keeps the block entity); a block that changes id (cane to bush, wither) is placed
 * anew and gets its record stored. Region thread of the block, as {@code PaperBerryEffects}.
 *
 * <p>Divergence (ledger): as the stationary bushes, the hydration is the farmland hydration of the block below without a soil multiplier.
 */
public final class PaperSpreadEffects implements SpreadEffects {
    public static final String SPREADING_GROWS_ON = "tfc:spreading_fruit_grows_on";
    public static final String BUSH_PLANTABLE_ON = "tfc:bush_plantable_on";
    public static final String SEA_BUSH_PLANTABLE_ON = "tfc:sea_bush_plantable_on";

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Supplier<BlockVisuals> visuals;
    private final Tags tags;
    private final Supplier<DeviceService> devices;
    private final Supplier<DeviceKind<BerryState>> kind;
    private final Supplier<Optional<CatchUpService>> catchUp;

    public PaperSpreadEffects(Scheduler scheduler, CustomBlocks blocks, CustomItems items, Supplier<BlockVisuals> visuals, Tags tags,
            Supplier<DeviceService> devices, Supplier<DeviceKind<BerryState>> kind, Supplier<Optional<CatchUpService>> catchUp) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.items = items;
        this.visuals = visuals;
        this.tags = tags;
        this.devices = devices;
        this.kind = kind;
        this.catchUp = catchUp;
    }

    @Override
    public BerryEffects.Environment environment(Block block) {
        World world = block.getWorld();
        long seed = world.getSeed();
        OverworldClimate climate = OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate chunk = ChunkClimateSource.climateAt(seed, block.getX(), block.getZ());
        float temperature = climate.averageTemperature(new BlockPos(block.getX(), block.getY(), block.getZ()), chunk);
        boolean northern = world.getEnvironment() != World.Environment.NORMAL
            || Solar.inNorthernHemisphere(block.getZ(), climate.hemisphereScale());
        Block root = block.getRelative(0, -1, 0);
        int rain = FarmlandRules.rainHydration(chunk.averageRainfall());
        boolean source = FarmlandRules.sourcePresent(FarmlandRules.minCostWater((dx, dy, dz) -> root.getRelative(dx, dy, dz).getType() == Material.WATER));
        return new BerryEffects.Environment(FarmlandRules.instantHydration(rain, false, source, 1f), temperature, northern);
    }

    @Override
    public Optional<BlockStateRef> blockAt(Block block) {
        return blocks.stateAt(block);
    }

    @Override
    public boolean isEmpty(Block block) {
        return block.getType().isAir() && blocks.stateAt(block).isEmpty();
    }

    @Override
    public boolean mayPlaceOn(Block below) {
        return tags.is(below, SPREADING_GROWS_ON);
    }

    @Override
    public boolean canPlantCranberry(Block target) {
        Block below = target.getRelative(0, -1, 0);
        return target.getType() == Material.WATER && blocks.stateAt(target).isEmpty()
            && (tags.is(below, BUSH_PLANTABLE_ON) || tags.is(below, SEA_BUSH_PLANTABLE_ON));
    }

    @Override
    public boolean waterlogged(Block block) {
        return block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    @Override
    public int countSame(Block center, String blockId, int radius, int dyMin, int dyMax) {
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = dyMin; dy <= dyMax; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (blocks.stateAt(center.getRelative(dx, dy, dz)).map(s -> s.id().equals(blockId)).orElse(false)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    @Override
    public void show(Block block, BlockStateRef wanted, BerryState state) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().startsWith(SpreadingBushDevice.ID) || wanted.equals(now.get())) {
                return; // broken or replaced meanwhile, or already right
            }
            if (!wanted.id().equals(now.get().id())) {
                if (blocks.place(block, wanted, false)) {
                    devices.get().edit(block, kind.get(), (record, at) -> record.withState(state));
                }
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void place(Block target, BlockStateRef ref, BerryState state) {
        if (!blocks.place(target, ref, true)) {
            return;
        }
        devices.get().edit(target, kind.get(), (record, now) -> record.withState(state));
    }

    @Override
    public void store(Block block, BerryState state) {
        devices.get().edit(block, kind.get(), (record, now) -> record.withState(state));
    }

    @Override
    public void remove(Block block) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            if (blocks.stateAt(block).map(s -> s.id().startsWith(SpreadingBushDevice.ID)).orElse(false)) {
                blocks.remove(block);
            }
        });
    }

    @Override
    public void track(Block block) {
        catchUp.get().ifPresent(c -> c.track(block, SpreadingBushDevice.CATCH_UP_KIND));
    }

    @Override
    public Optional<ItemStack> product(String itemId) {
        return items.create(itemId, 1);
    }

    @Override
    public void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    @Override
    public void pickSound(Block block) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().playSound(new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5),
            org.bukkit.Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1f, r.nextFloat() + 0.7f + 0.3f);
    }

    @Override
    public RandomGenerator random() {
        return ThreadLocalRandom.current();
    }
}
