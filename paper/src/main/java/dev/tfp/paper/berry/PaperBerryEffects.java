/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (useItemOn sound, getFruitBushHydration,
 *       getFruitBushHydrationFromRootPos, mayPlaceOn: BUSH_PLANTABLE_ON),
 *       src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java @ c3b4d9536 (getInstantHydrationFromRainHydration, isSourceBlockPresent)
 */
package dev.tfp.paper.berry;

import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.world.BlockPos;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link BerryEffects}. The visual state is written with {@code setBlockData(data, false)} while the block is still the
 * bush's CraftEngine block (CraftEngine keeps the block entity), on the block's region thread.
 *
 * <p>Divergences (ledger): TFC multiplies the hydration by the soil of the root block ({@code getHydrationMultiplier}); the bush uses 1. The
 * environment is sampled at each access and stored, not at each random tick.
 */
public final class PaperBerryEffects implements BerryEffects {
    /** TFC {@code TFCTags.Blocks.BUSH_PLANTABLE_ON}. */
    public static final String PLANTABLE_ON = "tfc:bush_plantable_on";

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Supplier<BlockVisuals> visuals;
    private final Tags tags;
    private final Supplier<DeviceService> devices;
    private final Supplier<DeviceKind<BerryState>> kind;
    private final Supplier<Optional<CatchUpService>> catchUp;

    public PaperBerryEffects(Scheduler scheduler, CustomBlocks blocks, CustomItems items, Supplier<BlockVisuals> visuals, Tags tags,
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
    public Environment environment(Block block) {
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
        return new Environment(FarmlandRules.instantHydration(rain, false, source, 1f), temperature, northern);
    }

    @Override
    public void show(Block block, BlockStateRef wanted, BerryState state) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().startsWith(BerryBushDevice.ID) || wanted.equals(now.get())) {
                return; // broken or replaced meanwhile, or already right
            }
            if (!wanted.id().equals(now.get().id())) {
                if (blocks.place(block, wanted, false)) { // withered: the dead bush block, with the withered state stored
                    devices.get().edit(block, kind.get(), (record, at) -> record.withState(state));
                }
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void remove(Block block) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            if (blocks.stateAt(block).map(s -> s.id().startsWith(BerryBushDevice.ID)).orElse(false)) {
                blocks.remove(block);
            }
        });
    }

    @Override
    public int bushesInBox(Block block, String blockId) {
        int n = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (blocks.stateAt(block.getRelative(dx, dy, dz)).map(s -> s.id().equals(blockId)).orElse(false)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    @Override
    public boolean canPlant(Block target) {
        return target.getType().isAir() && blocks.stateAt(target).isEmpty()
            && tags.is(target.getRelative(0, -1, 0), PLANTABLE_ON);
    }

    @Override
    public void plant(Block target, BlockStateRef ref, BerryState state) {
        if (!blocks.place(target, ref, true)) {
            return;
        }
        devices.get().edit(target, kind.get(), (record, now) -> record.withState(state));
    }

    @Override
    public void track(Block block) {
        catchUp.get().ifPresent(c -> c.track(block, BerryBushDevice.CATCH_UP_KIND));
    }

    @Override
    public Optional<ItemStack> product(BushSpecies species) {
        return items.create(BerryBushDevice.productId(species), 1);
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
        // TFC: SWEET_BERRY_BUSH_PICK_BERRIES, volume 1, pitch nextFloat + 0.7 + 0.3
        block.getWorld().playSound(new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5),
            org.bukkit.Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1f, r.nextFloat() + 0.7f + 0.3f);
    }

    @Override
    public RandomGenerator random() {
        return ThreadLocalRandom.current();
    }

}
