/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.quern.QuernRules;
import dev.tfp.core.quern.QuernVisuals;
import dev.tfp.paper.integration.FakeBlockVisuals;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.joml.Quaternionf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-04: the quern's displays at TFC's renderer positions, the has_handstone state written on the next region task,
 * the handstone turn as interpolated steps ending with the finish callback, and the handstone damage rule.
 */
class PaperQuernEffectsMockBukkitTest extends MockServerTest {
    /** Region tasks run at once; delayed ones are queued with their delay. */
    static final class QueueScheduler implements Scheduler {
        record Delayed(long ticks, Runnable task) {
        }

        final List<Delayed> delayed = new ArrayList<>();

        void runDelayed() {
            List<Delayed> now = new ArrayList<>(delayed);
            delayed.clear();
            now.sort(java.util.Comparator.comparingLong(Delayed::ticks));
            now.forEach(d -> d.task().run());
        }

        @Override
        public void region(Location location, Runnable task) {
            task.run();
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            delayed.add(new Delayed(delayTicks, task));
            return handle();
        }

        @Override
        public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            task.run();
            return true;
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Executor ioExecutor() {
            return Runnable::run;
        }

        @Override
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }

        private static TaskHandle handle() {
            return new TaskHandle() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(QuernDevice.ID);
    private final FakeBlockVisuals visuals = new FakeBlockVisuals();
    private final QueueScheduler scheduler = new QueueScheduler();
    private final List<Block> finished = new ArrayList<>();
    private PaperQuernEffects effects;
    private World world;
    private Block quern;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("quern-effects");
        quern = world.getBlockAt(3, 70, -5);
        blocks.place(quern, QuernDevice.state(false), true);
        visuals.define(QuernDevice.state(true), Bukkit.createBlockData(Material.SNOW));
        effects = new PaperQuernEffects(scheduler, blocks, () -> visuals, finished::add);
    }

    private List<ItemDisplay> all() {
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            if (e instanceof ItemDisplay d) {
                out.add(d);
            }
        }
        return out;
    }

    private ItemDisplay part(PaperQuernEffects.Part p) {
        List<ItemDisplay> found = PaperQuernEffects.displays(quern, p);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    @Test
    void displaysSitWhereTfcRendersAndTheStateFollows() {
        effects.show(quern, new QuernEffects.View(new ItemStack(Material.FLINT), new ItemStack(Material.WHEAT, 5),
            new ItemStack(Material.SUGAR, 3), false));
        assertThat(all()).hasSize(3);
        ItemDisplay handstone = part(PaperQuernEffects.Part.HANDSTONE);
        assertThat(handstone.getLocation().getY()).isCloseTo(70 + QuernVisuals.HANDSTONE_Y, within(1e-9));
        assertThat(handstone.getTransformation().getScale().x()).isEqualTo(QuernVisuals.HANDSTONE_SCALE);
        assertThat(handstone.getItemDisplayTransform()).isEqualTo(ItemDisplay.ItemDisplayTransform.FIXED);
        ItemDisplay input = part(PaperQuernEffects.Part.INPUT);
        assertThat(input.getLocation().getY()).isCloseTo(70 + QuernVisuals.INPUT_Y_WITH_HANDSTONE, within(1e-9));
        assertThat(input.getItemStack().getAmount()).as("one item drawn").isEqualTo(1);
        ItemDisplay output = part(PaperQuernEffects.Part.OUTPUT);
        assertThat(output.getLocation().getX()).isCloseTo(3 + QuernVisuals.OUTPUT_X, within(1e-9));
        assertThat(quern.getType()).as("has_handstone=true written").isEqualTo(Material.SNOW);

        // the handstone is taken: its display goes, the input sits lower, nothing is duplicated
        effects.show(quern, new QuernEffects.View(ItemStack.empty(), new ItemStack(Material.WHEAT, 5), ItemStack.empty(),
            false));
        assertThat(all()).hasSize(1);
        assertThat(part(PaperQuernEffects.Part.INPUT).getLocation().getY()).isCloseTo(70 + QuernVisuals.INPUT_Y, within(1e-9));

        effects.hide(quern);
        assertThat(all()).isEmpty();
    }

    @Test
    void spinStepsTheHandstoneAndCallsBackAtTheFinishTick() {
        effects.show(quern, new QuernEffects.View(new ItemStack(Material.FLINT), new ItemStack(Material.WHEAT), ItemStack.empty(),
            true));
        effects.spin(quern);
        assertThat(scheduler.delayed).hasSize(QuernVisuals.SPIN_STEPS + 1);
        assertThat(scheduler.delayed.getLast().ticks()).isEqualTo(QuernRules.TICKS_PER_GRIND);
        // run the first step only
        PaperQuernEffectsMockBukkitTest.QueueScheduler.Delayed first = scheduler.delayed.getFirst();
        first.task().run();
        ItemDisplay handstone = part(PaperQuernEffects.Part.HANDSTONE);
        QuernVisuals.Step step = QuernVisuals.spin().getFirst();
        assertThat(handstone.getInterpolationDuration()).isEqualTo(step.duration());
        // the step's transformation turns +x the way TFC's angle does (about +y)
        org.joml.Vector3f expected = new Quaternionf().rotateY((float) step.angle()).transform(new org.joml.Vector3f(1, 0, 0));
        org.joml.Vector3f sent = PaperQuernEffects.transformation(PaperQuernEffects.Part.HANDSTONE, step.angle())
            .getLeftRotation().transform(new org.joml.Vector3f(1, 0, 0));
        assertThat(sent.distance(expected)).isLessThan(1e-5f);
        // and it is what the display holds (MockBukkit stores a matrix: compare through the same round trip)
        ItemDisplay probe = world.spawn(new Location(world, 0, 0, 0), ItemDisplay.class,
            d -> d.setTransformation(PaperQuernEffects.transformation(PaperQuernEffects.Part.HANDSTONE, step.angle())));
        assertThat(handstone.getTransformation()).isEqualTo(probe.getTransformation());
        probe.remove();
        scheduler.runDelayed();
        assertThat(finished).containsExactly(quern);
    }

    @Test
    void oneFinishCallbackAtATime() {
        effects.finishAt(quern, 30);
        effects.finishAt(quern, 20); // a neighbour update during the grind
        assertThat(scheduler.delayed).as("no duplicate while one is queued").hasSize(1);
        scheduler.runDelayed();
        assertThat(finished).containsExactly(quern);
        effects.finishAt(quern, 10);
        assertThat(scheduler.delayed).as("queued again once the callback ran").hasSize(1);
    }

    @Test
    void damageBreaksAtMaxDamage() {
        // a vanilla damageable item stands in for the handstone's max_damage 250
        ItemStack stone = new ItemStack(Material.WOODEN_SWORD);
        int max = Material.WOODEN_SWORD.getMaxDurability();
        stone.editMeta(Damageable.class, d -> d.setDamage(max - 2));
        ItemStack once = PaperQuernEffects.damageOnce(stone, 0);
        assertThat(((Damageable) once.getItemMeta()).getDamage()).isEqualTo(max - 1);
        assertThat(PaperQuernEffects.damageOnce(once, 0).isEmpty()).as("damage max of max breaks it").isTrue();
        ItemStack unbreaking = stone.clone();
        unbreaking.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 3);
        assertThat(PaperQuernEffects.damageOnce(unbreaking, 1)).as("unbreaking skips 3 rolls in 4").isEqualTo(unbreaking);
        assertThat(((Damageable) PaperQuernEffects.damageOnce(unbreaking, 4).getItemMeta()).getDamage()).isEqualTo(max - 1);
        ItemStack plain = new ItemStack(Material.WHEAT);
        assertThat(PaperQuernEffects.damageOnce(plain, 0)).as("an undamageable stack is kept").isEqualTo(plain);
    }
}
