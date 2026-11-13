/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (startFire)
 */
package dev.tfp.paper.fire;

import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.fuel.FireStartPlan;
import dev.tfp.core.fuel.FirepitPile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.bukkit.Axis;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * TFC's {@code StartFireEvent.startFire} as a service, the one way every fire starter of the plugin lights things.
 * Devices register a {@link FireLightable} (TFC's event listeners); the firepit block installs its
 * {@link FirepitFactory}.
 *
 * <p><b>Decide async, apply sync.</b> {@link #start} snapshots the rain gate of the position above the target on its
 * region thread, draws the random part ({@link FireStartPlan}: the rain-dependent chances, the strength and the firepit
 * roll) on the CPU pool, then applies on the region thread: the fluid check, the lightables, the ground item scan and the
 * firepit or the fire block, all re-read there.
 */
public final class FireStarting {
    /** Where the steps run ({@code Scheduler} on a server, direct in tests). */
    public interface Runner {
        /** Runs {@code task} on the region thread that owns {@code block} (now, when this thread owns it). */
        void region(Block block, Runnable task);

        /** Runs {@code task} on the CPU pool. */
        void cpu(Runnable task);
    }

    /** TFC's {@code level.isRainingAt(pos)}, split into the region-thread gate and the pure climate decision. */
    public interface Rain {
        /** Vanilla's gates of {@code isRainingAt} for {@code pos}; region thread of {@code pos}. */
        boolean reaches(Block pos);

        /** Whether rain falls at the position, given {@link #reaches}; any thread. */
        boolean raining(World world, int x, int y, int z, boolean reaches);
    }

    /** One starter's random part: the plan from the rain at the position above the target. Any thread. */
    @FunctionalInterface
    public interface Starter {
        FireStartPlan plan(boolean raining, RandomGenerator random);

        /** TFC {@code FirestarterItem}: always strong, firepit chance {@code chance x (raining ? 0.3 : 1)}. */
        static Starter firestarter(double chance) {
            return (raining, random) -> FireStartPlan.firestarter(chance, raining, random);
        }

        /** TFC {@code FlintAndPyriteItem}: strong on a {@code chance} draw, firepit base {@code raining ? 0.3 : chance}. */
        static Starter flintAndPyrite(double chance) {
            return (raining, random) -> FireStartPlan.flintAndPyrite(chance, raining, random);
        }

        /** {@code startFire(..., strength, firepitBase)} of other callers ({@link FireStart#NO_FIREPIT} for none). */
        static Starter fixed(FireStart.Strength strength, double firepitBase) {
            return (raining, random) -> FireStartPlan.fixed(strength, firepitBase, random);
        }
    }

    /** What the apply step did. */
    public enum Result {
        /** A fluid is in front of the targeted face: TFC returns before the event. */
        FLUID,
        /** A {@link FireLightable} handled it (TFC: the event was cancelled). */
        LIT,
        /** A weak start that no lightable took: nothing. */
        WEAK,
        /** A firepit was built from the ground items. */
        FIREPIT,
        /** A firepit could have been built but the roll failed. */
        FIREPIT_MISSED,
        /** A fire block was placed. */
        FIRE,
        /** A fire block could not be placed. */
        NO_FIRE;

        /** TFC's return value of {@code startFire}. */
        public boolean started() {
            return this == LIT || this == FIREPIT || this == FIRE;
        }
    }

    private final Runner runner;
    private final Rain rain;
    private final FireWorld world;
    private final Function<ItemStack, FirepitPile.Kind> kinds;
    private final List<FireLightable> lightables = new CopyOnWriteArrayList<>();
    private volatile FirepitFactory firepit;

    /**
     * @param kinds what a ground stack is for a firepit ({@code #tfc:firepit_sticks}, {@code _kindling}, {@code _logs});
     *              any thread
     */
    public FireStarting(Runner runner, Rain rain, FireWorld world, Function<ItemStack, FirepitPile.Kind> kinds) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.rain = Objects.requireNonNull(rain, "rain");
        this.world = Objects.requireNonNull(world, "world");
        this.kinds = Objects.requireNonNull(kinds, "kinds");
    }

    /** Adds a lightable; they are asked in registration order. Any thread, usually at module enable. */
    public void register(FireLightable lightable) {
        lightables.add(Objects.requireNonNull(lightable, "lightable"));
    }

    /** Installs the firepit factory (PAR-HEAT-06). */
    public void firepitFactory(FirepitFactory factory) {
        this.firepit = factory;
    }

    /**
     * Starts a fire at {@code block}'s {@code face}, as TFC's {@code startFire}. Call on the thread of {@code player}
     * when there is one (its facing is read now).
     *
     * @param player the player, or {@code null}
     * @param stack  the starting stack (copied)
     * @param done   told the result on the region thread
     */
    public void start(Block block, BlockFace face, Player player, ItemStack stack, Starter starter,
                      Consumer<Result> done) {
        Axis axis = player == null ? Axis.X : axisOfYaw(player.getLocation().getYaw());
        ItemStack copy = stack == null ? ItemStack.empty() : stack.clone();
        runner.region(block, () -> {
            Block above = block.getRelative(BlockFace.UP);
            boolean reaches = rain.reaches(above);
            World w = block.getWorld();
            runner.cpu(() -> {
                boolean raining = rain.raining(w, above.getX(), above.getY(), above.getZ(), reaches);
                FireStartPlan plan = starter.plan(raining, ThreadLocalRandom.current());
                runner.region(block, () -> done.accept(apply(
                    new FireStartContext(block, face, player, copy, plan.strength()), plan, axis)));
            });
        });
    }

    /** The apply step: TFC's {@code startFire} after the random draws. Region thread of the block. */
    Result apply(FireStartContext ctx, FireStartPlan plan, Axis axis) {
        Block relative = ctx.block().getRelative(ctx.face());
        if (world.hasFluid(relative)) {
            return Result.FLUID;
        }
        for (FireLightable l : lightables) {
            if (l.light(ctx)) {
                return Result.LIT;
            }
        }
        if (!ctx.strong()) {
            return Result.WEAK;
        }
        FirepitFactory factory = firepit;
        boolean canSurvive = factory != null && plan.firepitBase() != FireStart.NO_FIREPIT && factory.canSurvive(relative);
        List<Item> items = canSurvive ? world.groundItems(relative) : List.of();
        List<FirepitPile.Entry> entries = new ArrayList<>(items.size());
        for (Item item : items) {
            ItemStack s = item.getItemStack();
            entries.add(new FirepitPile.Entry(kinds.apply(s), s.getAmount()));
        }
        FirepitPile.Scan scan = FirepitPile.scan(entries);
        FireStart.Outcome outcome = plan.resolve(false, canSurvive, scan.sticks(), scan.kindling(), scan.hasLogs());
        switch (outcome) {
            case FIREPIT -> {
                List<ItemStack> logs = new ArrayList<>(scan.logs().size());
                for (int index : scan.logs()) {
                    ItemStack one = items.get(index).getItemStack().clone();
                    one.setAmount(1);
                    logs.add(one);
                }
                for (int index : scan.used()) {
                    world.remove(items.get(index));
                }
                factory.build(relative, axis, logs, ctx.player());
                return Result.FIREPIT;
            }
            case FIREPIT_MISSED -> {
                return Result.FIREPIT_MISSED;
            }
            case FIRE -> {
                return world.placeFire(ctx.block(), ctx.face(), ctx.player()) ? Result.FIRE : Result.NO_FIRE;
            }
            default -> {
                return Result.WEAK;
            }
        }
    }

    /** TFC {@code player.getDirection().getAxis()}: {@code Direction.fromYRot(yaw)} (0 south, 1 west, 2 north, 3 east). */
    static Axis axisOfYaw(float yaw) {
        int index = (int) Math.floor(yaw / 90.0 + 0.5) & 3;
        return index == 1 || index == 3 ? Axis.X : Axis.Z;
    }

    /** The axis of a horizontal facing. */
    static Axis axisOf(BlockFace facing) {
        return facing == BlockFace.EAST || facing == BlockFace.WEST ? Axis.X : Axis.Z;
    }
}
