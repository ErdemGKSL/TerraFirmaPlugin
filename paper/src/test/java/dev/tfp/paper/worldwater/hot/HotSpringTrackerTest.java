/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater.hot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FLUID-04: healing in hot spring water and the airless bubble columns, on a MockBukkit world. */
class HotSpringTrackerTest extends MockServerTest {
    private World world;
    private PlayerMock player;
    private final List<Double> drowned = new ArrayList<>();
    private final SplittableRandom random = new SplittableRandom(7);
    /** Every heal the tracker asked for (MockBukkit's {@code heal} is unimplemented, so the test heals by setHealth). */
    private final List<Double> healed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("hot");
        player = server.addPlayer("Bot");
        player.setGameMode(GameMode.SURVIVAL);
    }

    private HotSpringTracker tracker(int biome, boolean providesAir) {
        SpringWater.Chunks chunks = (w, cx, cz) -> Optional.of(ChunkWater.uniform(cx, cz, biome, Flow.NONE, 70));
        HotSpringTracker.Heal heal = (p, amount) -> {
            healed.add(amount);
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + amount));
        };
        return new HotSpringTracker(new SpringWater(chunks), (p, amount) -> drowned.add(amount), heal,
                HotSpringRules.HOT_WATER_HEAL_AMOUNT, providesAir, () -> random);
    }

    /** A 5x5 spring pool, one block deep at y 70 on gravel, with the player standing in its middle. */
    private void poolAndStand() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 69, z).setType(Material.GRAVEL);
                world.getBlockAt(x, 70, z).setType(Material.WATER);
                world.getBlockAt(x, 71, z).setType(Material.AIR);
                world.getBlockAt(x, 72, z).setType(Material.AIR);
            }
        }
        player.teleport(new Location(world, 0.5, 70, 0.5));
    }

    @Test
    void healthRisesInAHotSpring() {
        poolAndStand();
        player.setHealth(10);
        HotSpringTracker t = tracker(TfcBiomes.OLD_MOUNTAINS, false);
        assertThat(t.springBlocksTouched(player)).isEqualTo(1);
        // any exception (also MockBukkit's UnimplementedOperationException, which JUnit would report as a skip) fails here
        assertThatNoException().isThrownBy(() -> {
            for (int i = 0; i < 20; i++) { // 10 s
                t.tick(player);
            }
        });
        // 200 ticks at 1 in 10 of 0.08: about 1.6
        assertThat(healed).isNotEmpty();
        assertThat(player.getHealth()).isGreaterThan(10.5).isLessThan(13);
    }

    @Test
    void noHealingAwayFromSpringBiomesOrForSpectatorsOrAtFullHealth() {
        poolAndStand();
        player.setHealth(10);
        HotSpringTracker plains = tracker(TfcBiomes.PLAINS, false);
        for (int i = 0; i < 20; i++) {
            plains.tick(player);
        }
        assertThat(player.getHealth()).as("a gravel pool far from any spring").isEqualTo(10);

        HotSpringTracker t = tracker(TfcBiomes.CANYONS, false);
        player.setGameMode(GameMode.SPECTATOR);
        for (int i = 0; i < 20; i++) {
            t.tick(player);
        }
        assertThat(player.getHealth()).as("spectator").isEqualTo(10);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20);
        t.tick(player);
        assertThat(player.getHealth()).isEqualTo(20);
        assertThat(healed).as("heal never called").isEmpty();
    }

    @Test
    void noHealingOnDryLandOrInPlainWater() {
        player.teleport(new Location(world, 0.5, 70, 0.5));
        player.setHealth(10);
        HotSpringTracker t = tracker(TfcBiomes.OLD_MOUNTAINS, false);
        assertThat(t.springBlocksTouched(player)).isZero();
        // water on stone, not on gravel
        world.getBlockAt(0, 69, 0).setType(Material.STONE);
        world.getBlockAt(0, 70, 0).setType(Material.WATER);
        assertThat(t.springBlocksTouched(player)).isZero();
        SpringWater springs = new SpringWater((w, cx, cz) -> Optional.of(ChunkWater.uniform(cx, cz, TfcBiomes.CANYONS, Flow.NONE, 70)));
        assertThat(springs.isSpringWater(world.getBlockAt(0, 70, 0))).isFalse();
        world.getBlockAt(0, 69, 0).setType(Material.GRAVEL);
        assertThat(springs.isSpringWater(world.getBlockAt(0, 70, 0))).isTrue();
        SpringWater unknown = new SpringWater((w, cx, cz) -> Optional.empty());
        assertThat(unknown.isSpringWater(world.getBlockAt(0, 70, 0))).as("biomes not cached yet").isFalse();
    }

    /** A drag-down bubble column with the player's eyes in it. */
    private void inBubbleColumn() {
        player.teleport(new Location(world, 0.5, 70, 0.5));
        int eyeY = player.getEyeLocation().getBlockY();
        for (int y = 69; y <= eyeY + 1; y++) {
            BubbleColumn column = (BubbleColumn) Material.BUBBLE_COLUMN.createBlockData();
            column.setDrag(true);
            world.getBlockAt(0, y, 0).setBlockData(column);
        }
    }

    @Test
    void bubbleColumnsGiveNoAirByDefault() {
        inBubbleColumn();
        player.setRemainingAir(5);
        HotSpringTracker t = tracker(TfcBiomes.OLD_MOUNTAINS, false);
        assertThat(t.holdsBreath(player)).isTrue();
        t.tick(player);
        assertThat(player.getRemainingAir()).isEqualTo(-5);
        t.tick(player);
        t.tick(player); // reaches -20 in this period: drowning, back to 0, then loses more
        assertThat(drowned).containsExactly(2.0);
        assertThat(player.getRemainingAir()).isLessThan(0).isGreaterThan(-20);

        // the vanilla gain is cancelled, a loss is not
        EntityAirChangeEvent gain = new EntityAirChangeEvent(player, player.getRemainingAir() + 4);
        HotSpringModule.cancelGain(gain, player, t);
        assertThat(gain.isCancelled()).isTrue();
        EntityAirChangeEvent loss = new EntityAirChangeEvent(player, player.getRemainingAir() - 1);
        HotSpringModule.cancelGain(loss, player, t);
        assertThat(loss.isCancelled()).isFalse();
    }

    @Test
    void bubbleColumnsGiveAirWhenConfiguredOrForInvulnerableAndWaterBreathers() {
        inBubbleColumn();
        player.setRemainingAir(100);
        tracker(TfcBiomes.OLD_MOUNTAINS, true).tick(player);
        assertThat(player.getRemainingAir()).as("bubbleColumnProvidesAir").isEqualTo(100);

        HotSpringTracker t = tracker(TfcBiomes.OLD_MOUNTAINS, false);
        player.setGameMode(GameMode.CREATIVE);
        t.tick(player);
        assertThat(player.getRemainingAir()).as("creative").isEqualTo(100);
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 200, 0));
        t.tick(player);
        assertThat(player.getRemainingAir()).as("water breathing").isEqualTo(100);
        assertThat(t.holdsBreath(player)).isFalse();
    }

    @Test
    void upwardColumnsStayVanilla() {
        player.teleport(new Location(world, 0.5, 70, 0.5));
        int eyeY = player.getEyeLocation().getBlockY();
        BubbleColumn up = (BubbleColumn) Material.BUBBLE_COLUMN.createBlockData();
        up.setDrag(false);
        world.getBlockAt(0, eyeY, 0).setBlockData(up);
        player.setRemainingAir(100);
        HotSpringTracker t = tracker(TfcBiomes.OLD_MOUNTAINS, false);
        t.tick(player);
        assertThat(player.getRemainingAir()).isEqualTo(100);
        assertThat(t.holdsBreath(player)).isFalse();
    }
}
