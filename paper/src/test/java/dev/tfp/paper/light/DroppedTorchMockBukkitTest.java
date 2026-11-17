/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.light.LightSourceRules;
import dev.tfp.paper.light.DroppedTorches.Outcome;
import dev.tfp.paper.light.DroppedTorches.Place;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-22: TFC {@code TorchItem.onEntityItemUpdate} replayed lazily. The pure replay against TFC's draws (batches in
 * water, the age requirement and the 1 % chance on a lit-by-dropped-torch block, the particle draws kept), then on
 * MockBukkit: a stack in water goes out in batches and rolls the burnt-out loot per torch, a torch on leaves lights them
 * and dies, a young torch does not, lava and plain ground do nothing, the arrival tick is not charged for the time before.
 *
 * <p>Stand-ins: vanilla torch = the {@code tfp:torch} item, oak leaves = {@code #tfc:lit_by_dropped_torch}.
 */
class DroppedTorchMockBukkitTest extends MockServerTest {
    /** A random source that answers from scripted queues and counts the draws. */
    static final class Script implements RandomGenerator {
        final Deque<Integer> ints = new ArrayDeque<>();
        final Deque<Float> floats = new ArrayDeque<>();
        final Deque<Double> doubles = new ArrayDeque<>();
        final List<Integer> bounds = new ArrayList<>();
        int floatDraws;
        int doubleDraws;

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return ints.isEmpty() ? 0 : ints.poll();
        }

        @Override
        public float nextFloat() {
            floatDraws++;
            return floats.isEmpty() ? 0.5f : floats.poll();
        }

        @Override
        public double nextDouble() {
            doubleDraws++;
            return doubles.isEmpty() ? 0.5 : doubles.poll();
        }
    }

    static final class Effects implements DroppedTorchListener.Effects {
        final List<Integer> burntOut = new ArrayList<>();
        final List<Block> fires = new ArrayList<>();
        int sounds;

        @Override
        public void burntOut(Location at, int count) {
            burntOut.add(count);
        }

        @Override
        public void extinguishSound(Location at) {
            sounds++;
        }

        @Override
        public void strongFire(Block block) {
            fires.add(block);
        }
    }

    private final Effects effects = new Effects();
    private final List<Item> tracked = new ArrayList<>();
    private World world;
    private DroppedTorchListener listener;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("dropped");
        world.loadChunk(0, 0);
        listener = new DroppedTorchListener(s -> s.getType() == Material.TORCH,
            b -> b.getType() == Material.OAK_LEAVES, effects, tracked::add);
    }

    private Item drop(int x, double y, int z, int count) {
        Item item = world.dropItem(new Location(world, x + 0.5, y, z + 0.5), new ItemStack(Material.TORCH, count));
        item.setVelocity(new org.bukkit.util.Vector());
        return item;
    }

    // ---- the pure replay -------------------------------------------------------------------------------------------

    @Test
    void inWaterTorchesGoOutInTfcBatches() {
        Script r = new Script();
        r.ints.add(4); // 12 > 5: 1 + 4 = 5 go out
        r.ints.add(1); // 7 > 5: 1 + 1 = 2
        Outcome out = DroppedTorches.replay(12, Place.FLUID, 0, 10, r);
        // third tick: 5 left, 5 is not above 5, so all go out and the item is discarded (no draw)
        assertThat(out).isEqualTo(new Outcome(12, 3, 0, false));
        assertThat(r.bounds).containsExactly(5, 5);
        assertThat(out.removed()).isTrue();
    }

    @Test
    void aSmallStackGoesOutAtOnceWithoutADraw() {
        Script r = new Script();
        assertThat(DroppedTorches.replay(3, Place.FLUID, 7, 8, r)).isEqualTo(new Outcome(3, 1, 0, false));
        assertThat(r.bounds).isEmpty();
    }

    @Test
    void onALitBlockTheAgeRequirementGatesTheOnePercentDraw() {
        Script r = new Script();
        r.floats.add(0.5f);
        r.floats.add(0.009f);
        // above the block: 20 ticks. Ages 16..20 draw only the particle double; 21 draws 0.5 (no), 22 draws 0.009 (lit)
        Outcome out = DroppedTorches.replay(4, Place.LIT_BELOW, 15, 30, r);
        assertThat(out).isEqualTo(new Outcome(0, 0, 4, true));
        assertThat(r.floatDraws).isEqualTo(2);
        assertThat(r.doubleDraws).isEqualTo(6); // ages 16..21, the lit tick draws none
        // inside the block: 160 ticks, so nothing at age 100
        Script inside = new Script();
        inside.floats.add(0f);
        assertThat(DroppedTorches.replay(4, Place.LIT_INSIDE, 99, 100, inside).none()).isTrue();
        assertThat(inside.floatDraws).isZero();
        assertThat(DroppedTorches.replay(4, Place.LIT_INSIDE, 160, 161, inside).lit()).isTrue();
    }

    @Test
    void theParticleDrawsAreKeptInTfcsOrder() {
        Script r = new Script();
        r.doubles.add(0.05); // <= 0.1: TFC spawns a lava particle with three more nextDouble draws
        DroppedTorches.replay(1, Place.LIT_BELOW, 0, 1, r);
        assertThat(r.doubleDraws).isEqualTo(4);
        assertThat(r.floatDraws).isZero();
    }

    // ---- on the server ---------------------------------------------------------------------------------------------

    @Test
    void aStackInWaterGoesOutInBatchesAndDropsBurntOutLoot() {
        world.getBlockAt(2, 64, 3).setType(Material.WATER);
        Item item = drop(2, 64.1, 3, 8);
        item.setTicksLived(40);
        Script r = new Script();
        r.ints.add(2); // 8 > 5: 3 go out
        Optional<Outcome> first = listener.evaluate(item, r);
        // it just arrived: only this tick is replayed, not the 40 before
        assertThat(first).contains(new Outcome(3, 1, 5, false));
        assertThat(item.getItemStack().getAmount()).isEqualTo(5);
        assertThat(effects.burntOut).containsExactly(3);
        assertThat(effects.sounds).isEqualTo(1);
        assertThat(listener.evaluate(item, r)).isEmpty(); // no tick elapsed

        item.setTicksLived(50);
        assertThat(listener.evaluate(item, r)).contains(new Outcome(5, 1, 0, false));
        assertThat(effects.burntOut).containsExactly(3, 5);
        assertThat(item.isValid()).isFalse();
    }

    @Test
    void aWaterloggedBlockPutsTorchesOutAndLavaDoesNot() {
        Block slab = world.getBlockAt(2, 64, 3);
        slab.setType(Material.OAK_SLAB);
        Waterlogged data = (Waterlogged) slab.getBlockData();
        data.setWaterlogged(true);
        slab.setBlockData(data);
        assertThat(DroppedTorchListener.extinguishesFire(slab.getBlockData())).isTrue();
        assertThat(DroppedTorchListener.extinguishesFire(Material.LAVA.createBlockData())).isFalse();
        assertThat(DroppedTorchListener.extinguishesFire(Material.AIR.createBlockData())).isFalse();

        world.getBlockAt(5, 64, 5).setType(Material.LAVA);
        Item item = drop(5, 64.1, 5, 2);
        item.setTicksLived(300);
        assertThat(listener.evaluate(item, new Script())).isEmpty();
        assertThat(item.isValid()).isTrue();
        assertThat(effects.burntOut).isEmpty();
    }

    @Test
    void aTorchOnLeavesLightsThemAndDies() {
        Block leaves = world.getBlockAt(2, 64, 3);
        leaves.setType(Material.OAK_LEAVES);
        Item item = drop(2, 65.0, 3, 3);
        item.setTicksLived(100);
        Script r = new Script();
        r.floats.add(0.005f);
        assertThat(listener.evaluate(item, r)).contains(new Outcome(0, 0, 3, true));
        assertThat(effects.fires).containsExactly(leaves);
        assertThat(item.isValid()).isFalse();
    }

    @Test
    void aYoungTorchOnLeavesWaitsAndThenReplaysTheSpan() {
        world.getBlockAt(2, 64, 3).setType(Material.OAK_LEAVES);
        Item item = drop(2, 65.0, 3, 1);
        item.setTicksLived(10);
        Script r = new Script();
        r.floats.add(0f);
        assertThat(listener.evaluate(item, r)).contains(new Outcome(0, 0, 1, false));
        assertThat(r.floatDraws).isZero();
        // ten ticks later, still there: ages 11..20 are replayed, all at most 20, still no draw
        item.setTicksLived(20);
        assertThat(listener.evaluate(item, r).orElseThrow().lit()).isFalse();
        assertThat(r.floatDraws).isZero();
        // age 21 draws (0 < 0.01): lit
        item.setTicksLived(30);
        assertThat(listener.evaluate(item, r).orElseThrow().lit()).isTrue();
        assertThat(r.floatDraws).isEqualTo(1);
        assertThat(effects.fires).hasSize(1);
    }

    @Test
    void onPlainGroundOrForOtherItemsNothingHappens() {
        world.getBlockAt(2, 64, 3).setType(Material.DIRT);
        Item item = drop(2, 65.0, 3, 4);
        item.setTicksLived(500);
        assertThat(listener.evaluate(item, new Script())).isEmpty();
        Item stick = world.dropItem(new Location(world, 7.5, 65, 7.5), new ItemStack(Material.STICK));
        world.getBlockAt(7, 65, 7).setType(Material.WATER);
        stick.setTicksLived(5);
        assertThat(listener.evaluate(stick, new Script())).isEmpty();
        assertThat(listener.isTorch(stick)).isFalse();
        assertThat(effects.burntOut).isEmpty();
        assertThat(effects.fires).isEmpty();
    }

    @Test
    void spawnedAndLoadedTorchItemsAreTracked() {
        Item torch = drop(1, 70, 1, 1);
        Item stick = world.dropItem(new Location(world, 1.5, 70, 1.5), new ItemStack(Material.STICK));
        listener.onSpawn(new org.bukkit.event.entity.ItemSpawnEvent(torch));
        listener.onSpawn(new org.bukkit.event.entity.ItemSpawnEvent(stick));
        listener.onEntitiesLoad(new org.bukkit.event.world.EntitiesLoadEvent(world.getChunkAt(0, 0),
            List.of(torch, stick)));
        assertThat(tracked).containsExactly(torch, torch);
    }

    @Test
    void theBurntOutLootContextIsTfcs() {
        LootContext ctx = PaperDroppedTorchEffects.context();
        assertThat(ctx.burntOut()).isTrue();
        assertThat(ctx.blockId()).isEqualTo("tfp:torch");
        assertThat(ctx.tool()).isEqualTo(LootContext.Tool.EMPTY);
        assertThat(ctx.explosionRadius()).isNull(); // survives_explosion passes
    }

    /** One source for the default: TFC's 86,400, the module default and datagen's constant agree. */
    @Test
    void theTorchTicksDefaultHasOneSource() throws IOException {
        assertThat(LightSourceRules.DEFAULT_TORCH_TICKS).isEqualTo(86_400);
        assertThat(TorchModule.TORCH_TICKS).isEqualTo(LightSourceRules.DEFAULT_TORCH_TICKS);
        String datagen = Files.readString(
            Path.of("../tools/datagen/src/main/java/dev/tfp/datagen/light/TorchBlocks.java"));
        Matcher m = Pattern.compile("int TORCH_TICKS = ([0-9_]+);").matcher(datagen);
        assertThat(m.find()).isTrue();
        assertThat(Integer.parseInt(m.group(1).replace("_", ""))).isEqualTo(LightSourceRules.DEFAULT_TORCH_TICKS);
    }
}
