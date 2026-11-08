/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.thirst.Drinkables;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.SaltWater;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.core.worldwater.WaterTerrain;
import dev.tfp.core.worldwater.WorldWaterRules;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.worldwater.WorldWaterService;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-04, PAR-FLUID-07: which water a drink takes. With the worldwater module the TFC biome of the seed decides (salt in
 * every salty TFC biome, river water fresh); without it, water in a vanilla ocean biome is salt.
 */
class WaterDrinkListenerMockBukkitTest extends MockServerTest {
    private static final long SEED = 1234L;
    private static final int Y = WorldWaterRules.SEA_LEVEL_Y - 1;

    private static WaterDrinkListener listener(Function<Block, String> water) {
        return new WaterDrinkListener(null, () -> 0L, null, p -> { }, () -> Drinkables.EMPTY, water);
    }

    /** A service whose CPU pool runs inline, with the seeded terrain registered for {@code world}. */
    private static WorldWaterService seeded(World world) throws ReflectiveOperationException {
        WorldWaterService service = new WorldWaterService(Runnable::run, Logger.getLogger("test"));
        Method register = WorldWaterService.class.getDeclaredMethod("registerTerrain", UUID.class, Optional.class);
        register.setAccessible(true);
        register.invoke(service, world.getUID(), Optional.of(new WaterTerrain(SEED)));
        return service;
    }

    private static Block water(World world, int x, int z, Biome biome) {
        Block block = world.getBlockAt(x, Y, z);
        block.setType(Material.WATER);
        block.setBiome(biome);
        return block;
    }

    @Test
    void oceanBiomesAreSaltyWithoutTheWorldWaterModule() {
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.OCEAN)).isTrue();
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.DEEP_OCEAN)).isTrue();
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.PLAINS)).isFalse();
        assertThat(WaterDrinkListener.isSaltyBiome(Biome.RIVER)).as("TFC rivers and lakes are fresh").isFalse();
        assertThat(WaterDrinkListener.isSaltyBiome(null)).isFalse();
    }

    @Test
    void withoutTheServiceTheFluidOfASourceFollowsItsVanillaBiome() {
        Block block = server.addSimpleWorld("w").getBlockAt(0, 64, 0);
        WaterDrinkListener drink = listener(WaterDrinkListener.worldWaterFluid(Optional::empty));
        block.setBiome(Biome.OCEAN);
        assertThat(drink.fluidOf(block)).isEqualTo(Drinkables.SALT_WATER);
        block.setBiome(Biome.PLAINS);
        assertThat(drink.fluidOf(block)).isEqualTo(Drinkables.WATER);
    }

    @Test
    void inATfcWorldASaltyLandBiomeDrinksSaltAndARiverDrinksFresh() throws ReflectiveOperationException {
        World world = server.addSimpleWorld("tfc");
        WorldWaterService service = seeded(world);
        WaterDrinkListener drink = listener(WaterDrinkListener.worldWaterFluid(() -> Optional.of(service)));
        WaterTerrain terrain = new WaterTerrain(SEED);

        int[] salt = null;
        String saltBiome = null;
        int[] river = null;
        for (int cx = -32; cx <= 32 && (salt == null || river == null); cx += 2) {
            for (int cz = -32; cz <= 32 && (salt == null || river == null); cz += 2) {
                ChunkWater chunk = terrain.chunk(cx, cz);
                for (int i = 0; i < 256; i++) {
                    int x = i & 15;
                    int z = i >> 4;
                    int biome = chunk.biome(x, z);
                    String name = TfcBiomes.name(biome).toLowerCase(Locale.ROOT);
                    // a salty TFC biome that is not an ocean: the vanilla placeholder biome there is a land biome
                    if (salt == null && SaltWater.isSalty(biome) && !name.contains("ocean")
                            && chunk.kind(x, Y, z) == WaterKind.SALT) {
                        salt = new int[] {(cx << 4) + x, (cz << 4) + z};
                        saltBiome = name;
                    }
                    if (river == null && chunk.kind(x, Y, z) == WaterKind.RIVER) {
                        river = new int[] {(cx << 4) + x, (cz << 4) + z};
                    }
                }
            }
        }
        assertThat(salt).as("a salty TFC land biome near the origin of seed %s", SEED).isNotNull();
        assertThat(river).isNotNull();

        Block saltSource = water(world, salt[0], salt[1], Biome.PLAINS);
        assertThat(drink.fluidOf(saltSource)).as("water of the salty TFC biome %s", saltBiome)
                .isEqualTo(Drinkables.SALT_WATER);
        assertThat(drink.fluidOf(saltSource)).as("from the cached chunk").isEqualTo(Drinkables.SALT_WATER);

        Block riverSource = water(world, river[0], river[1], Biome.OCEAN);
        service.chunk(world, river[0] >> 4, river[1] >> 4);
        assertThat(drink.fluidOf(riverSource)).as("river water is fresh whatever the vanilla biome")
                .isEqualTo(Drinkables.WATER);
    }

    @Test
    void outsideTfcWorldsTheServiceUsesTheVanillaSaltRule() {
        World world = server.addSimpleWorld("plain");
        WorldWaterService service = new WorldWaterService(Runnable::run, Logger.getLogger("test"));
        WaterDrinkListener drink = listener(WaterDrinkListener.worldWaterFluid(() -> Optional.of(service)));
        assertThat(drink.fluidOf(water(world, 0, 0, Biome.BEACH))).isEqualTo(Drinkables.SALT_WATER);
        assertThat(drink.fluidOf(water(world, 1, 0, Biome.DEEP_OCEAN))).isEqualTo(Drinkables.SALT_WATER);
        assertThat(drink.fluidOf(water(world, 2, 0, Biome.RIVER))).isEqualTo(Drinkables.WATER);
    }
}
