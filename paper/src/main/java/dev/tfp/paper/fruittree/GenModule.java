/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/{FruitTreeFeature,BananaFeature,SpreadingCropFeature}.java @ c3b4d9536
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.plant.PlantData;
import dev.tfp.paper.banana.BananaDevice;
import dev.tfp.paper.banana.GenBananas;
import dev.tfp.paper.banana.PaperBananaEffects;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.crop.wild.GenWildFruit;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * PAR-FARM-27: the worldgen of the fruit trees, the banana and the wild spreading crop fruit as CraftEngine blocks, run when a chunk of a
 * TFC world is generated (a new chunk loads). Decide async, apply sync: the load event only snapshots the chunk; an async task runs
 * {@link GenPlanner} for every fruit tree and banana feature of the chunk's biome (TFC's climate, heightmap, {@code in_square} and
 * {@code rarity_filter} with the feature's own random) and scans the surface for the wild fruit placeholders; the region thread then
 * re-validates against the live blocks and writes through {@link GenTrees}, {@link GenBananas} and {@link GenWildFruit}. Must be listed
 * after the fruit tree, banana, wild crop, device, tags and catchup modules.
 */
public final class GenModule implements FeatureModule, Listener {
    public static final String ID = "fruit-gen";

    private ModuleContext context;
    private GenTrees trees;
    private GenBananas bananas;
    private GenWildFruit wild;
    private PlantData data;
    private final Map<String, Optional<GenFeature>> features = new ConcurrentHashMap<>();
    private final Map<Integer, List<GenFeature>> byBiome = new ConcurrentHashMap<>();
    private final Map<Long, ChunkDataGenerator> levels = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        this.context = ctx;
        DeviceService devices = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        PaperFruitTreeEffects treeFx = new PaperFruitTreeEffects(ctx.scheduler(), blocks, ctx.integrations().customItems(),
            () -> ctx.integrations().blockVisuals(), tags, () -> devices, kind(devices), () -> ctx.services().get(CatchUpService.class));
        PaperBananaEffects bananaFx = new PaperBananaEffects(ctx.scheduler(), blocks, ctx.integrations().customItems(),
            () -> ctx.integrations().blockVisuals(), tags, () -> devices, kind(devices), () -> ctx.services().get(CatchUpService.class));
        trees = new GenTrees(new FruitTreeDevice.Services(treeFx, () -> ctx.clock().get(), BerryRules.Settings.DEFAULT), devices, blocks);
        bananas = new GenBananas(new BananaDevice.Services(bananaFx, () -> ctx.clock().get(), BerryRules.Settings.DEFAULT), devices, blocks);
        wild = new GenWildFruit(devices, blocks, () -> ctx.services().get(CatchUpService.class));
        ClassLoader loader = GenModule.class.getClassLoader();
        data = PlantData.of(path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        });
        ctx.listen(this);
    }

    @SuppressWarnings("unchecked")
    private static Function<String, DeviceKind<FruitTreeState>> kind(DeviceService devices) {
        return id -> devices.kinds().find(id).map(k -> (DeviceKind<FruitTreeState>) k).orElse(null);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }
        World world = event.getWorld();
        if (!(world.getGenerator() instanceof TfcChunkGenerator generator) || world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        Chunk chunk = event.getChunk();
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, false, false);
        int cx = chunk.getX();
        int cz = chunk.getZ();
        Scheduler scheduler = context.scheduler();
        scheduler.cpu(() -> {
            try {
                Plan plan = plan(world, generator, snapshot, cx, cz);
                if (plan.isEmpty()) {
                    return;
                }
                scheduler.region(world, cx, cz, () -> commit(world, plan));
            } catch (RuntimeException e) {
                context.logger().warning("fruit worldgen of chunk " + cx + "," + cz + " failed: " + e);
            }
        });
    }

    /** What the async phase decided. */
    record Plan(List<GenPlanner.Spot> spots, List<int[]> fruit) {
        boolean isEmpty() {
            return spots.isEmpty() && fruit.isEmpty();
        }
    }

    Plan plan(World world, TfcChunkGenerator generator, ChunkSnapshot snapshot, int cx, int cz) {
        long seed = world.getSeed();
        int biome = generator.seasonalTerrain(() -> seed).biomeId((cx << 4) + 8, (cz << 4) + 8);
        int minY = world.getMinHeight();
        int[] surface = new int[256];
        List<int[]> fruit = new ArrayList<>();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int top = snapshot.getHighestBlockYAt(lx, lz);
                if (top > minY && GenWildFruit.placeholder(snapshot.getBlockType(lx, top, lz))) {
                    fruit.add(new int[] {(cx << 4) + lx, top, (cz << 4) + lz});
                }
                surface[lx + 16 * lz] = groundTop(snapshot, lx, lz, top, minY) + 1;
            }
        }
        List<GenFeature> list = featuresOf(biome);
        List<GenPlanner.Spot> spots = new ArrayList<>();
        if (!list.isEmpty()) {
            ChunkDataGenerator level = levels.computeIfAbsent(seed, ChunkDataGenerator::forLevel);
            ChunkData climate = level.generate(cx, cz).modifyBaseGroundwater(surface);
            GenPlanner.Terrain terrain = terrain(climate, level, minY, surface, cx, cz);
            for (GenFeature feature : list) {
                GenPlanner.plan(feature, seed, cx, cz, terrain).ifPresent(spots::add);
            }
        }
        return new Plan(spots, fruit);
    }

    /** The highest block that TFC's {@code WORLD_SURFACE_WG} would see: not a plant placeholder (not solid and not a fluid). */
    private static int groundTop(ChunkSnapshot snapshot, int lx, int lz, int top, int minY) {
        int y = top;
        while (y > minY) {
            Material m = snapshot.getBlockType(lx, y, lz);
            if (m.isSolid() || m == Material.WATER) {
                break;
            }
            y--;
        }
        return y;
    }

    private static GenPlanner.Terrain terrain(ChunkData climate, ChunkDataGenerator level, int minY, int[] surface, int cx, int cz) {
        return new GenPlanner.Terrain() {
            @Override
            public int minY() {
                return minY;
            }

            @Override
            public int worldSurface(int x, int z) {
                return surface[(x & 15) + 16 * (z & 15)];
            }

            @Override
            public float averageTemperature(int x, int z) {
                return climate.averageSeaLevelTemperature(x, z);
            }

            @Override
            public float averageRainfall(int x, int z) {
                return climate.averageRainfall(x, z);
            }

            @Override
            public float groundwater(int x, int z) {
                return climate.averageGroundwater(x, z);
            }

            @Override
            public float rainVariance(int x, int z) {
                return climate.rainVariance(x, z);
            }

            @Override
            public int forestDensity(int chunkX, int chunkZ) {
                return level.forestType(chunkX, chunkZ).density();
            }
        };
    }

    /** The fruit tree and banana features of a biome's surface decoration, in tag order. */
    List<GenFeature> featuresOf(int biome) {
        return byBiome.computeIfAbsent(biome, b -> {
            List<GenFeature> out = new ArrayList<>();
            String name = TfcBiomes.name(b).toLowerCase(Locale.ROOT);
            for (String id : data.tagValues("tfp:" + PlantData.SURFACE_DECORATION + name)) {
                features.computeIfAbsent(id, i -> GenFeature.read(i, path -> readData(path))).ifPresent(out::add);
            }
            return List.copyOf(out);
        });
    }

    private Optional<String> readData(String path) {
        try (InputStream in = GenModule.class.getClassLoader().getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The region thread: re-validates every decision against the live blocks and writes it. */
    void commit(World world, Plan plan) {
        for (GenPlanner.Spot spot : plan.spots()) {
            try {
                switch (spot.feature().kind()) {
                    case FRUIT_TREE -> trees.commit(world, spot);
                    case BANANA -> bananas.commit(world, spot);
                }
            } catch (RuntimeException e) {
                context.logger().warning("fruit worldgen at " + spot.x() + "," + spot.y() + "," + spot.z() + " failed: " + e);
            }
        }
        for (int[] p : plan.fruit()) {
            wild.commit(world.getBlockAt(p[0], p[1], p[2]));
        }
    }
}
