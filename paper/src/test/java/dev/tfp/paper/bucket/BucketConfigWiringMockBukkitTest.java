/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bucket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.bucket.BucketKind;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-FLUID-12: the bucket listener's config supplier is the one {@code BucketModule} builds ({@link
 * BucketModule#bucketConfig}) over a real {@link ConfigService} and config.yml on disk, so the {@code buckets:} keys and
 * {@code /tfp reload config} reach bucket use.
 */
class BucketConfigWiringMockBukkitTest extends MockServerTest {
    private static final Map<Material, String> IDS = Map.of(
            Material.FLOWER_POT, BucketKind.WOODEN.itemId(),
            Material.IRON_INGOT, BucketKind.RED_STEEL.itemId());

    @TempDir
    Path dir;
    private Path file;
    private ConfigService config;
    private World world;
    private PlayerMock player;
    private BucketListener listener;

    private static String shipped() {
        try (InputStream in = BucketConfigWiringMockBukkitTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        file = dir.resolve("config.yml");
        String shipped = shipped();
        Files.writeString(file, shipped.replace("wooden-bucket-can-place-sources: false",
                "wooden-bucket-can-place-sources: true"));
        config = new ConfigService(file, () -> shipped);
        assertThat(config.load().applied()).isTrue();
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        listener = new BucketListener(
                s -> s == null ? Optional.empty() : Optional.ofNullable(IDS.get(s.getType())),
                (kind, fluid) -> true, BucketModule.bucketConfig(config), id -> Optional.empty());
    }

    private ItemStack filled(Material material) {
        ItemStack s = new ItemStack(material);
        ItemFluids.fill(s, new FluidAmount("minecraft:water", 1000), 1000, true);
        BucketVisuals.sync(s);
        return s;
    }

    /** Empties a filled bucket on stone at x and returns the block above it. */
    private Block empty(Material material, BucketKind kind, int x) {
        Block ground = world.getBlockAt(x, 63, 0);
        ground.setType(Material.STONE);
        Block above = ground.getRelative(BlockFace.UP);
        above.setType(Material.AIR);
        ItemStack held = filled(material);
        player.getInventory().setItemInMainHand(held);
        assertThat(listener.useTank(player, EquipmentSlot.HAND, held, kind,
                new BucketListener.Hit(ground, BlockFace.UP))).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand())).isEmpty();
        assertThat(above.getType()).isEqualTo(Material.WATER);
        return above;
    }

    private static int level(Block b) {
        return ((Levelled) b.getBlockData()).getLevel();
    }

    @Test
    void theConfigFileDecidesSourcePlacementAndAReloadAppliesIt() throws IOException {
        assertThat(config.current().buckets()).isEqualTo(new BucketConfig(true, false));
        // wooden-bucket-can-place-sources: true -> a water source; the metal key stays false -> a flowing block.
        assertThat(level(empty(Material.FLOWER_POT, BucketKind.WOODEN, 0))).isZero();
        assertThat(level(empty(Material.IRON_INGOT, BucketKind.RED_STEEL, 4))).isEqualTo(BucketListener.FLOWING_LEVEL);

        // Edit back to false and reload (what /tfp reload config does): the same listener now places a flowing block.
        Files.writeString(file, Files.readString(file).replace("wooden-bucket-can-place-sources: true",
                "wooden-bucket-can-place-sources: false"));
        assertThat(config.load().applied()).isTrue();
        assertThat(config.current().buckets()).isEqualTo(BucketConfig.DEFAULTS);
        assertThat(level(empty(Material.FLOWER_POT, BucketKind.WOODEN, 8))).isEqualTo(BucketListener.FLOWING_LEVEL);
    }
}
