/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (animateTick: smoke position
 *       and counts, getParticleHeightOffset 0.35),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound, spawnItem)
 */
package dev.tfp.paper.firepit;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link FirepitEffects}. TFC's smoke is a client particle that ticks on its own; ours is one burst of
 * vanilla {@link Particle#CAMPFIRE_COSY_SMOKE} / {@link Particle#SMOKE} on an interaction only (ledger). The burn-out
 * callback is one delayed region task per firepit: a newer request replaces the queued one (the old task fires and does
 * nothing), and a task dropped by a chunk unload is not replaced until the next callback. A calendar jump past the
 * burn-out fires the callback within a second through {@link BurnOutWatch} (PAR-HEAT-33).
 */
public final class PaperFirepitEffects implements FirepitEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final BurnOutWatch watch;

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /**
     * Effects whose burn-out callback runs in server ticks only (no calendar-jump sweep; the grill's firepit effects).
     *
     * @param callback calls the device back (the device router's neighbour update)
     */
    public PaperFirepitEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals,
            Consumer<Block> callback) {
        this(scheduler, blocks, visuals, () -> 0L, callback);
    }

    /**
     * @param clock    the device clock (the calendar's player ticks, the clock of {@code FirepitSim.ticksUntilOut})
     * @param callback calls the device back (the device router's neighbour update)
     */
    public PaperFirepitEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, LongSupplier clock,
            Consumer<Block> callback) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.watch = new BurnOutWatch(scheduler, clock, callback);
    }

    /**
     * PAR-HEAT-33: the burn-out callback of each lit, loaded firepit, due both in server ticks and on the device clock.
     * The server-tick task turns the block unlit on time while the calendar runs normally; a calendar jump
     * ({@code /tfp time add}, the sleep skip, vanilla {@code /time}) moves the clock past the burn-out without server
     * ticks, so a sweep on the global thread every {@value #SWEEP_PERIOD} ticks compares each watch's due tick with the
     * clock (the decision: a read of a small map) and queues the callback on the firepit's region (the apply), where the
     * device catches up and its {@code finish} shows the unlit state. Each watch fires once, by whichever path comes
     * first; a newer request for the same firepit replaces it. A watch whose chunk unloaded is dropped when it comes due
     * (the catch-up on chunk load takes over).
     */
    public static final class BurnOutWatch {
        /** Server ticks between two sweeps (one calendar step). */
        public static final long SWEEP_PERIOD = 20;

        private record Watch(long id, Block block, long due) {
        }

        private final Scheduler scheduler;
        private final LongSupplier clock;
        private final Consumer<Block> callback;
        private final Map<Pos, Watch> pending = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        public BurnOutWatch(Scheduler scheduler, LongSupplier clock, Consumer<Block> callback) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.clock = Objects.requireNonNull(clock, "clock");
            this.callback = Objects.requireNonNull(callback, "callback");
        }

        /** Region thread of the block: the callback in {@code ticks} (server ticks and device clock ticks alike). */
        public void callbackIn(Block block, long ticks) {
            long delay = Math.max(1, ticks);
            Pos key = Pos.of(block);
            Watch w = new Watch(sequence.incrementAndGet(), block, clock.getAsLong() + delay);
            pending.put(key, w);
            scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, delay, () -> fire(key, w));
        }

        public Scheduler.TaskHandle start() {
            return scheduler.globalRepeating(SWEEP_PERIOD, SWEEP_PERIOD, this::sweep);
        }

        /** Global thread: queues every watch the clock has passed on its region. */
        public void sweep() {
            long now = clock.getAsLong();
            for (Map.Entry<Pos, Watch> e : pending.entrySet()) {
                Watch w = e.getValue();
                if (w.due() <= now) {
                    Block b = w.block();
                    Pos key = e.getKey();
                    scheduler.region(b.getWorld(), b.getX() >> 4, b.getZ() >> 4, () -> fire(key, w));
                }
            }
        }

        /** The number of watches waiting. */
        public int size() {
            return pending.size();
        }

        private void fire(Pos key, Watch w) {
            if (!pending.remove(key, w)) {
                return;
            }
            Block b = w.block();
            if (b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
                callback.accept(b);
            }
        }
    }

    @Override
    public void show(Block block, BlockStateRef state) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !FirepitDevice.isFirepit(now.get().id()) || state.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(state);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void sound(Block block, Sound sound) {
        org.bukkit.Sound s = switch (sound) {
            case EXTINGUISH -> org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH;
            case LOG -> org.bukkit.Sound.BLOCK_WOOD_PLACE;
            case ASH -> org.bukkit.Sound.BLOCK_SAND_BREAK;
        };
        if (sound == Sound.LOG) {
            // TFC tryInsertLog: Helpers.playPlaceSound with SoundType.WOOD, volume (1 + 1) / 2, pitch 1 * 0.8
            block.getWorld().playSound(centre(block), s, SoundCategory.BLOCKS, 1f, 0.8f);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // TFC Helpers.playSound: volume 1 + nextFloat, pitch nextFloat + 0.7 + 0.3
        block.getWorld().playSound(centre(block), s, SoundCategory.BLOCKS, 1f + r.nextFloat(), r.nextFloat() + 1f);
    }

    @Override
    public void smoke(Block block, int smokeLevel) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double x = block.getX() + 0.5;
        double y = block.getY() + 0.35;
        double z = block.getZ() + 0.5;
        // TFC forcedAnimateTick: 1 + nextInt(3) tall smokes; animateTick: nextInt(4 + smoke) small smokes
        for (int i = 0; i < 1 + r.nextInt(3); i++) {
            block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, x + triangle(r) * 0.5, y + r.nextDouble(),
                z + triangle(r) * 0.5, 0, 0, 0.07, 0, 1);
        }
        for (int i = 0; i < r.nextInt(4 + smokeLevel); i++) {
            block.getWorld().spawnParticle(Particle.SMOKE, x + triangle(r) * 0.5, y + r.nextDouble(),
                z + triangle(r) * 0.5, 0, 0, 0.005, 0, 1);
        }
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        block.getWorld().dropItem(centre(block), stack);
    }

    @Override
    public void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }

    @Override
    public void useDouse(Player player, EquipmentSlot hand, Douse douse) {
        ItemStack held = player.getInventory().getItem(hand);
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (douse == Douse.WATER) {
            player.getInventory().setItem(hand, new ItemStack(Material.BUCKET));
        } else if (douse == Douse.SHOVEL) {
            player.getInventory().setItem(hand, held.damage(1, player));
        }
    }

    @Override
    public void callbackIn(Block block, long ticks) {
        watch.callbackIn(block, ticks);
    }

    /** Starts the calendar-jump sweep of {@link BurnOutWatch}; the handle stops it. */
    public Scheduler.TaskHandle start() {
        return watch.start();
    }

    @Override
    public void breakBlock(Block block) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isPresent() && FirepitDevice.isFirepit(now.get().id())) {
                blocks.remove(block);
            }
        });
    }

    /** TFC {@code Helpers.triangle}: {@code nextFloat - nextFloat}. */
    private static double triangle(ThreadLocalRandom r) {
        return r.nextFloat() - r.nextFloat();
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
