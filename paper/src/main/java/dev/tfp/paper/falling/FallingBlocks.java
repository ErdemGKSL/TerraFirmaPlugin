/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.falling;

import dev.tfp.core.falling.FallRules;
import dev.tfp.core.falling.Landing;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * PAR-PHYS-01: TFC's {@code TFCFallingBlockEntity} on a vanilla {@link FallingBlock}. The entity carries the server
 * block data of our state (CraftEngine's custom state, so clients see the block and the ore overlay attaches its
 * riders), {@code dropItem} off (vanilla would drop nothing useful for a custom state), and our state and damage in its
 * PDC so it survives chunk unloads and restarts.
 *
 * <p>Vanilla moves it. Every tick, before vanilla ticks it, a task on the entity's thread predicts this tick's move
 * (gravity, then the collision boxes under its footprint): on contact it runs TFC's on-ground branch
 * ({@link FallRules#decide}): break the block it stands in or the one below and keep falling, or place itself (at, or
 * one above), or drop as items, dealing vanilla fall damage to living entities in its box (not creative or spectator
 * players). A landing the prediction missed (landing on an entity, rounding) reaches vanilla's placement, whose
 * {@code EntityChangeBlockEvent} is cancelled and handled by the same decision ({@link #changeBlock}).
 *
 * <p>Threads: every method runs on the owner thread of the entity or block it is given.
 */
public final class FallingBlocks {
    /** PDC: the falling state ({@link BlockStateRef#toString()}). */
    public static final NamespacedKey STATE_KEY = new NamespacedKey("tfp", "falling_state");
    /** PDC: damage per block fallen (float) and the damage cap (int). */
    public static final NamespacedKey DAMAGE_KEY = new NamespacedKey("tfp", "falling_damage");
    public static final NamespacedKey MAX_DAMAGE_KEY = new NamespacedKey("tfp", "falling_max_damage");

    /** Half the width of a falling block's box (0.98). */
    private static final double HALF_WIDTH = 0.49;
    private static final double HEIGHT = 0.98;
    private static final double EPS = 1.0e-7;
    /** How many landings {@link #recent()} keeps. */
    public static final int RECENT = 8;

    /** One landing that ended a fall, for later features (landslide tracking, rock spikes shattering). */
    public record Landed(Block block, BlockStateRef state, Landing landing) {
    }

    private static final class Fall {
        final BlockStateRef state;
        final int toughness;
        final float perBlock;
        final int maxDamage;
        int time;
        boolean failedBreakCheck;
        boolean done;

        Fall(BlockStateRef state, int toughness, float perBlock, int maxDamage) {
            this.state = state;
            this.toughness = toughness;
            this.perBlock = perBlock;
            this.maxDamage = maxDamage;
        }
    }

    private final FallWorld world;
    private final Scheduler scheduler;
    private final Map<UUID, Fall> falls = new ConcurrentHashMap<>();
    private final List<Consumer<Landed>> hooks = new CopyOnWriteArrayList<>();
    private final LongAdder spawned = new LongAdder();
    private final LongAdder broken = new LongAdder();
    private final LongAdder placed = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    /** The last {@link #RECENT} landings, newest first (for {@code /tfp debug fall last}); guarded by itself. */
    private final Deque<Landed> recent = new ArrayDeque<>();

    public FallingBlocks(FallWorld world, Scheduler scheduler) {
        this.world = world;
        this.scheduler = scheduler;
    }

    /** Runs {@code hook} after every fall that ended (placed or dropped), on the landing block's thread. */
    public void onLanded(Consumer<Landed> hook) {
        hooks.add(hook);
    }

    /**
     * Turns the block at {@code block} into a falling block (TFC: the block is replaced by the entity on its first
     * tick). Owner thread of the block. Empty for air or a state without server block data.
     */
    public Optional<FallingBlock> fall(Block block, float damagePerBlock, int maxDamage) {
        if (block.getType().isAir()) {
            return Optional.empty();
        }
        BlockStateRef state = world.stateAt(block);
        if (world.blockData(state).isEmpty()) {
            return Optional.empty();
        }
        world.remove(block);
        return spawn(block.getWorld(), block.getX(), block.getY(), block.getZ(), state, damagePerBlock, maxDamage);
    }

    /**
     * Spawns a falling {@code state} at the bottom centre of {@code x y z}. Owner thread of that block. Empty when the
     * state has no server block data (unknown, or CraftEngine not loaded).
     */
    public Optional<FallingBlock> spawn(World w, int x, int y, int z, BlockStateRef state, float damagePerBlock,
            int maxDamage) {
        Optional<BlockData> data = world.blockData(state);
        if (data.isEmpty()) {
            return Optional.empty();
        }
        Fall fall = new Fall(state, world.toughness(state), damagePerBlock, maxDamage);
        return Optional.of(spawnEntity(new Location(w, x + 0.5, y, z + 0.5), data.get(), fall));
    }

    private FallingBlock spawnEntity(Location at, BlockData data, Fall fall) {
        FallingBlock entity = at.getWorld().spawn(at, FallingBlock.class, e -> {
            e.setBlockData(data);
            e.setDropItem(false);
            e.setHurtEntities(fall.perBlock > 0f);
            e.setDamagePerBlock(fall.perBlock);
            e.setMaxDamage(fall.maxDamage);
            e.setVelocity(new Vector());
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            pdc.set(STATE_KEY, PersistentDataType.STRING, fall.state.toString());
            pdc.set(DAMAGE_KEY, PersistentDataType.FLOAT, fall.perBlock);
            pdc.set(MAX_DAMAGE_KEY, PersistentDataType.INTEGER, fall.maxDamage);
        });
        spawned.increment();
        track(entity, fall);
        return entity;
    }

    private void track(FallingBlock entity, Fall fall) {
        UUID id = entity.getUniqueId();
        falls.put(id, fall);
        Scheduler.TaskHandle handle = scheduler.entityRepeating(entity, 1L, 1L, () -> tick(entity),
                () -> falls.remove(id, fall));
        if (handle == null) {
            falls.remove(id, fall);
        }
    }

    /** Resumes a falling block of ours loaded from a chunk (its PDC). Owner thread of the entity. */
    public boolean resume(Entity entity) {
        if (!(entity instanceof FallingBlock falling) || falls.containsKey(entity.getUniqueId())) {
            return false;
        }
        Fall fall = fromPdc(falling);
        if (fall == null) {
            return false;
        }
        track(falling, fall);
        return true;
    }

    private Fall fromPdc(FallingBlock entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String text = pdc.get(STATE_KEY, PersistentDataType.STRING);
        if (text == null) {
            return null;
        }
        BlockStateRef state;
        try {
            state = BlockStateRef.parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Float perBlock = pdc.get(DAMAGE_KEY, PersistentDataType.FLOAT);
        Integer max = pdc.get(MAX_DAMAGE_KEY, PersistentDataType.INTEGER);
        return new Fall(state, world.toughness(state), perBlock == null ? 0f : perBlock, max == null ? 0 : max);
    }

    /** Whether {@code entity} is one of ours being tracked. */
    public boolean isTracked(Entity entity) {
        return falls.containsKey(entity.getUniqueId());
    }

    /**
     * One tick of a tracked falling block, before vanilla ticks it. Owner thread of the entity. Package-private for
     * tests; the scheduler calls it.
     */
    void tick(FallingBlock entity) {
        Fall fall = falls.get(entity.getUniqueId());
        if (fall == null || fall.done || !entity.isValid()) {
            return;
        }
        fall.time++;
        Location loc = entity.getLocation();
        World w = loc.getWorld();
        double y = loc.getY();
        double dy = FallRules.moveVelocity(entity.getVelocity().getY());
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        OptionalDouble top = restingTop(w, bx, bz, y, dy);
        if (top.isPresent()) {
            double landY = top.getAsDouble();
            Block at = w.getBlockAt(bx, (int) Math.floor(landY + EPS), bz);
            contact(entity, fall, at, landY, false);
            return;
        }
        fall.failedBreakCheck = false;
        if (FallRules.expires(fall.time, (int) Math.floor(y + dy), w.getMinHeight(), w.getMaxHeight())) {
            world.dropAsItem(loc, fall.state);
            finish(entity, fall, loc.getBlock(), Landing.DROP);
        }
    }

    private OptionalDouble restingTop(World w, int bx, int bz, double y, double dy) {
        int from = (int) Math.floor(y + EPS);
        int to = Math.max((int) Math.floor(y + dy) - 1, w.getMinHeight());
        double[] tops = new double[16];
        double[] bottoms = new double[16];
        int n = 0;
        for (int by = Math.min(from, w.getMaxHeight() - 1); by >= to; by--) {
            for (double[] box : world.collisionHeights(w.getBlockAt(bx, by, bz))) {
                if (n == tops.length) {
                    tops = Arrays.copyOf(tops, n * 2);
                    bottoms = Arrays.copyOf(bottoms, n * 2);
                }
                tops[n] = by + box[1];
                bottoms[n] = by + box[0];
                n++;
            }
        }
        return FallRules.restingTop(y, dy, Arrays.copyOf(tops, n), Arrays.copyOf(bottoms, n));
    }

    /**
     * Vanilla is about to place the falling block at {@code at} (its {@code EntityChangeBlockEvent}): a landing the
     * prediction missed. Returns true when the event must be cancelled (ours: handled here; vanilla then discards the
     * entity, so a fall that must go on continues in a new entity).
     */
    public boolean changeBlock(FallingBlock entity, Block at) {
        Fall fall = falls.get(entity.getUniqueId());
        if (fall == null) {
            fall = fromPdc(entity);
            if (fall == null) {
                return false;
            }
        }
        if (fall.done) {
            return true;
        }
        Landing landing = contact(entity, fall, at, entity.getLocation().getY(), true);
        if (landing == Landing.BOUNCE) {
            return false;
        }
        if (!fall.done) {
            // a break: vanilla discards this entity after the cancelled event; go on falling in a fresh one
            fall.done = true;
            falls.remove(entity.getUniqueId());
            Fall next = new Fall(fall.state, fall.toughness, fall.perBlock, fall.maxDamage);
            next.time = fall.time;
            next.failedBreakCheck = true;
            world.blockData(fall.state).ifPresent(d -> spawnEntity(entity.getLocation(), d, next));
        }
        return true;
    }

    /** TFC's on-ground branch at {@code at} (the entity's block position on contact). */
    private Landing contact(FallingBlock entity, Fall fall, Block at, double landY, boolean vanillaLanded) {
        Block below = at.getRelative(BlockFace.DOWN);
        Block above = at.getRelative(BlockFace.UP);
        FallRules.Contact c = new FallRules.Contact(world.cell(at), world.cell(below), world.cell(above),
                world.survives(fall.state, at), world.survives(fall.state, above), true, fall.failedBreakCheck,
                world.isMovingPiston(at));
        Landing landing = FallRules.decide(c, fall.toughness);
        if (landing == Landing.BOUNCE) {
            return landing; // vanilla bounces off the moving piston
        }
        if (!vanillaLanded) {
            hurt(entity, fall, landY);
        }
        switch (landing) {
            case DESTROY_AT, DESTROY_BELOW -> {
                world.destroy(landing == Landing.DESTROY_AT ? at : below);
                broken.increment();
                fall.failedBreakCheck = true;
                Vector v = entity.getVelocity();
                entity.setVelocity(v.setY(0.0)); // the contact stopped it, as TFC's move did
                return landing;
            }
            case PLACE_AT -> placeOrDrop(entity, fall, at);
            case PLACE_ABOVE -> placeOrDrop(entity, fall, above);
            case DESTROY_ABOVE_AND_PLACE_ABOVE -> {
                world.destroy(above);
                broken.increment();
                placeOrDrop(entity, fall, above);
            }
            default -> {
                world.dropAsItem(entity.getLocation(), fall.state);
                dropped.increment();
            }
        }
        finish(entity, fall, landing == Landing.PLACE_AT || landing == Landing.DROP ? at : above, landing);
        return landing;
    }

    private void placeOrDrop(FallingBlock entity, Fall fall, Block target) {
        if (world.place(target, fall.state)) {
            placed.increment();
        } else {
            world.dropAsItem(entity.getLocation(), fall.state);
            dropped.increment();
        }
    }

    private void finish(FallingBlock entity, Fall fall, Block where, Landing landing) {
        fall.done = true;
        falls.remove(entity.getUniqueId());
        entity.remove();
        Landed landed = new Landed(where, fall.state, landing);
        synchronized (recent) {
            recent.addFirst(landed);
            while (recent.size() > RECENT) {
                recent.removeLast();
            }
        }
        for (Consumer<Landed> hook : hooks) {
            hook.accept(landed);
        }
    }

    /**
     * Vanilla {@code FallingBlockEntity.causeFallDamage} on contact: living entities in the block's box at the contact
     * height, not creative or spectator players ({@code EntitySelector.NO_CREATIVE_OR_SPECTATOR}), then the fall
     * distance resets as vanilla's {@code checkFallDamage} does.
     */
    private void hurt(FallingBlock entity, Fall fall, double landY) {
        float amount = FallRules.damage(entity.getFallDistance(), fall.perBlock, fall.maxDamage);
        entity.setFallDistance(0f);
        if (amount <= 0f) {
            return;
        }
        Location loc = entity.getLocation();
        BoundingBox box = new BoundingBox(loc.getX() - HALF_WIDTH, landY, loc.getZ() - HALF_WIDTH,
                loc.getX() + HALF_WIDTH, landY + HEIGHT, loc.getZ() + HALF_WIDTH);
        for (Entity e : loc.getWorld().getNearbyEntities(box)) {
            if (e != entity && e instanceof LivingEntity living && isTarget(living)) {
                world.hurt(living, amount, entity);
            }
        }
    }

    /** {@code NO_CREATIVE_OR_SPECTATOR} and {@code LIVING_ENTITY_STILL_ALIVE}. */
    static boolean isTarget(LivingEntity living) {
        if (living.isDead() || !living.isValid()) {
            return false;
        }
        return !(living instanceof Player p)
                || (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR);
    }

    public long spawned() {
        return spawned.sum();
    }

    public long broken() {
        return broken.sum();
    }

    public long placed() {
        return placed.sum();
    }

    public long dropped() {
        return dropped.sum();
    }

    /** The last {@link #RECENT} falls that ended, newest first. Any thread. */
    public List<Landed> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    /**
     * What is at a landing's block now, in our vocabulary (custom state first), read without changing anything. Owner
     * thread of the block. For {@code /tfp debug fall last}: a landed CE gravel reads {@code tfp:rock/gravel/...}.
     */
    public BlockStateRef stateNow(Landed landed) {
        return world.stateAt(landed.block());
    }

    /** Falling blocks being tracked now. */
    public int tracking() {
        return falls.size();
    }
}
