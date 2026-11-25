/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (tickBoth with a crank
 *       rotation, doPush: the receivers at the default offsets, push when none or any accepts air; afterPush: the blow sound)
 */
package dev.tfp.paper.crankshaft;

import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.core.charcoalforge.BellowsRules;
import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.crankshaft.CrankshaftRules.Part;
import dev.tfp.core.crankshaft.CrankshaftRules.Piece;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.bellows.BellowsConsumers;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The powered bellows (PAR-MECH-05). Every crankshaft base runs one region task every {@link #PERIOD} ticks instead of TFC's
 * per-tick bellows tick: it steps TFC's {@code tickBoth} over those ticks ({@link CrankshaftRules#run}, pure, with the crank's
 * rotation as the network reports it now) and applies the pushes it found to the bellows two blocks in front of the base: the
 * air goes to the receivers at the bellows' default offsets (when there are none, or any of them accepts air) and the blow sound
 * plays. The crank's angle and the bellows' push fields are kept in memory (a restart starts them again at 0).
 *
 * <p>{@link #start} and {@link #stop}: region thread of the base; the task runs there.
 */
public final class CrankshaftService {
    /** Ticks stepped per run. */
    public static final int PERIOD = 20;
    /** The blow sound, as PAR-METAL-11's hand push (TFC's {@code tfc:block.bellows.blow} has no pack entry yet). */
    static final String SOUND = "minecraft:block.fire.extinguish";

    /** Where a push's effects go; behind an interface for the tests. */
    public interface Effects {
        /** The blow of a powered push (TFC {@code afterPush}: the sound; no particles, see the ledger). */
        void blow(Block bellows);
    }

    /** The live effects. */
    public static final Effects SOUNDS = bellows -> bellows.getWorld().playSound(bellows.getLocation().add(0.5, 0.5, 0.5), SOUND,
        SoundCategory.BLOCKS, 1f, 1f + (float) ((Math.random() - Math.random()) / 16));

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<CrankRotation> rotations;
    private final Supplier<Optional<BellowsConsumers>> consumers;
    private final Effects effects;
    private final Map<Key, Running> running = new ConcurrentHashMap<>();

    record Key(UUID world, BlockPos pos) {
    }

    static final class Running {
        volatile Scheduler.TaskHandle handle;
        long tick;
        CrankshaftRules.Drive drive = CrankshaftRules.Drive.FRESH;
        int pushes;
    }

    public CrankshaftService(Scheduler scheduler, CustomBlocks blocks, Supplier<CrankRotation> rotations,
            Supplier<Optional<BellowsConsumers>> consumers, Effects effects) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.rotations = Objects.requireNonNull(rotations, "rotations");
        this.consumers = Objects.requireNonNull(consumers, "consumers");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /** The rotation of the crank whose base is {@code base}, empty when it is in no network. Any thread. */
    public Optional<Rotation> rotation(Block base) {
        return rotations.get().rotation(base.getWorld().getUID(), CrankshaftBlocks.pos(base));
    }

    /** Starts the base at {@code block} (a second start is ignored). Region thread. */
    public void start(Block block) {
        World world = block.getWorld();
        BlockPos pos = CrankshaftBlocks.pos(block);
        running.computeIfAbsent(new Key(world.getUID(), pos), k -> {
            Running r = new Running();
            r.handle = scheduler.regionRepeating(world, pos.x() >> 4, pos.z() >> 4, PERIOD, PERIOD, () -> run(world, pos, r));
            return r;
        });
    }

    /** Stops the base at {@code pos}. Any thread. */
    public void stop(UUID world, BlockPos pos) {
        Running r = running.remove(new Key(world, pos));
        if (r != null && r.handle != null) {
            r.handle.cancel();
        }
    }

    public int running() {
        return running.size();
    }

    /** The powered pushes of the base at {@code pos} so far (debug and tests); -1 when it is not running. */
    public int pushes(UUID world, BlockPos pos) {
        Running r = running.get(new Key(world, pos));
        return r == null ? -1 : r.pushes;
    }

    /** One run. Region thread of the base. */
    void run(World world, BlockPos pos, Running r) {
        long from = r.tick;
        r.tick += PERIOD;
        Block base = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<Piece> piece = blocks.stateAt(base).flatMap(CrankshaftBlocks::piece);
        if (piece.isEmpty() || piece.get().part() != Part.BASE) {
            stop(world.getUID(), pos);
            return;
        }
        Optional<Rotation> rotation = rotation(base);
        Optional<Block> bellows = bellowsOf(base, piece.get());
        if (rotation.isEmpty() || bellows.isEmpty()) {
            return; // TFC: no crank rotation, no powered push (the angle stays with the source)
        }
        CrankshaftRules.Run step = CrankshaftRules.run(r.drive, from, r.tick, rotation.get().direction(),
            rotation.get().speed(), piece.get().facing());
        r.drive = step.drive();
        for (int i = 0; i < step.pushes().size(); i++) {
            push(bellows.get(), piece.get());
            r.pushes++;
        }
    }

    /**
     * The bellows a crank drives: two blocks in front of the base, facing the same way, with the shaft between them (TFC's
     * {@code getCrankShaftAt} from the bellows, looking along its facing's opposite).
     */
    Optional<Block> bellowsOf(Block base, Piece piece) {
        Block bellows = base.getRelative(2 * piece.facing().dx(), 0, 2 * piece.facing().dz());
        int cx = bellows.getX() >> 4;
        int cz = bellows.getZ() >> 4;
        if (!bellows.getWorld().isChunkLoaded(cx, cz) || !scheduler.isOwner(bellows.getWorld(), cx, cz)) {
            return Optional.empty();
        }
        Optional<BlockStateRef> state = blocks.stateAt(bellows).filter(s -> s.id().equals(BELLOWS_ID));
        if (state.isEmpty() || !CrankshaftBlocks.name(piece.facing()).equals(state.get().properties().get(BELLOWS_FACING))) {
            return Optional.empty();
        }
        return CrankshaftBlocks.crankAt(bellows, piece.facing().opposite(), blocks, scheduler)
            .filter(b -> b.equals(base)).map(b -> bellows);
    }

    static final String BELLOWS_ID = CrankshaftBlocks.BELLOWS;
    static final String BELLOWS_FACING = "facing";

    /** TFC {@code doPush} and {@code afterPush} of a powered bellows. Region thread of the bellows. */
    void push(Block bellows, Piece crank) {
        String facing = CrankshaftBlocks.name(crank.facing());
        boolean anyReceiver = false;
        boolean anyAllowing = false;
        Optional<BellowsConsumers> registry = consumers.get();
        for (BellowsMotion.Offset offset : BellowsMotion.DEFAULT_OFFSETS) {
            int[] d = offset.delta(facing);
            Block at = bellows.getRelative(d[0], d[1], d[2]);
            if (registry.isEmpty() || !scheduler.isOwner(at.getWorld(), at.getX() >> 4, at.getZ() >> 4)) {
                continue; // another region's block: not read from here
            }
            Optional<BellowsConsumers.Consumer> consumer = blocks.stateAt(at).map(BlockStateRef::id).flatMap(registry.get()::find);
            if (consumer.isPresent()) {
                anyReceiver = true;
                if (consumer.get().canAcceptAir(at)) {
                    anyAllowing = true;
                    consumer.get().intakeAir(at, BellowsRules.BELLOWS_AIR);
                }
            }
        }
        if (!anyReceiver || anyAllowing) {
            effects.blow(bellows);
        }
    }
}
