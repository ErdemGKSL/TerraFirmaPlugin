/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.aquatic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.Carrier;
import dev.tfp.core.aquatic.AquaticFluid;
import dev.tfp.core.aquatic.AquaticSpecies;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FAUNA-11: bucket pickup of a fish, the water column finder, the ocean test. */
class AquaticModuleMockBukkitTest extends MockServerTest {
    private World world;
    private PlayerMock player;
    private Cow fish;
    private AquaticSpecies species = AquaticSpecies.COD;
    private AquaticFluid held = AquaticFluid.SALT_WATER;
    private boolean itemExists = true;
    private AquaticModule module;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        fish = world.spawn(new Location(world, 2, 64, 2), Cow.class);
        module = new AquaticModule();
        module.initForTest(null, e -> e == fish ? Optional.of(species) : Optional.empty(), item -> held, item -> Optional.empty(),
            (id, n) -> itemExists ? Optional.of(new ItemStack(Material.COD_BUCKET, n)) : Optional.empty());
        server.getPluginManager().registerEvents(module, plugin);
        player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
    }

    private PlayerInteractEntityEvent click() {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, fish, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aMatchingBucketPicksTheFishUpIntoItsMobBucket() {
        assertThat(click().isCancelled()).isTrue();
        assertThat(fish.isValid()).isFalse();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.COD_BUCKET);
    }

    @Test
    void aBucketOfAnotherFluidLeavesTheFishAlone() {
        held = AquaticFluid.VANILLA_WATER;
        assertThat(click().isCancelled()).isFalse();
        assertThat(fish.isValid()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.WATER_BUCKET);
    }

    @Test
    void anEmptyHandOrUnknownMobBucketItemDoesNothing() {
        itemExists = false;
        assertThat(click().isCancelled()).isFalse();
        assertThat(fish.isValid()).isTrue();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        assertThat(click().isCancelled()).isFalse();
    }

    @Test
    void aSpeciesWithoutABucketIsNeverPickedUp() {
        species = AquaticSpecies.DOLPHIN;
        assertThat(click().isCancelled()).isFalse();
        assertThat(fish.isValid()).isTrue();
    }

    @Test
    void stackedBucketsLoseOneAndKeepTheRest() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET, 2));
        click();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.WATER_BUCKET);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(player.getInventory().contains(Material.COD_BUCKET)).isTrue();
    }

    @Test
    void creativeKeepsTheBucket() {
        player.setGameMode(GameMode.CREATIVE);
        click();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.WATER_BUCKET);
        assertThat(player.getInventory().contains(Material.COD_BUCKET)).isTrue();
    }

    @Test
    void mobBucketIdsFollowTheSpeciesName() {
        assertThat(AquaticModule.bucketId(AquaticSpecies.LARGEMOUTH_BASS)).isEqualTo("tfp:bucket/largemouth_bass");
    }

    @Test
    void oceanBiomesAreSaltWater() {
        assertThat(AquaticModule.oceanBiome("deep_ocean")).isTrue();
        assertThat(AquaticModule.oceanBiome("river")).isFalse();
        assertThat(AquaticModule.oceanBiome("lake")).isFalse();
    }

    @Test
    void waterPlaceFindsADepthInsideTheColumn() {
        for (int y = 40; y <= 50; y++) {
            world.getBlockAt(5, y, 5).setType(Material.WATER);
        }
        Carrier c = new Carrier("tfc:cod", 0.5f, 0.3f, "WATER_AMBIENT", 4, "minecraft:cod", 0.5f, 0.3f, false, "", "", "");
        Location low = AquaticModule.waterPlace(world, 5, 5, c, 0.0);
        Location high = AquaticModule.waterPlace(world, 5, 5, c, 0.999);
        assertThat(low).isNotNull();
        assertThat(low.getBlockY()).isEqualTo(40);
        assertThat(high.getBlockY()).isEqualTo(50);
        assertThat(AquaticModule.waterPlace(world, 9, 9, c, 0.5)).isNull();
    }
}
