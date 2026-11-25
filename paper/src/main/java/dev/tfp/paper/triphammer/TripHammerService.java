/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java @ c3b4d9536 (serverTick: the
 *       rotation of the bladed axle above, the anvil in front, the hit sound, workRemotely(HIT_LIGHT, 12, true), damageItem, the
 *       break sound of a used-up hammer, destroyBlock on the wrong hand)
 */
package dev.tfp.paper.triphammer;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.machine.TripHammerRules;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.triphammer.TripHammerRun;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The trip hammers at work (PAR-MECH-07). Every hammer runs one region task every {@link #PERIOD} ticks instead of TFC's per-tick
 * {@code serverTick}: it reads the rotation of the bladed axle above (the network actor's published rotation,
 * {@link RotationService#rotation}), steps TFC's tick over those ticks ({@link TripHammerRun}, with the hammer's own angle) and
 * applies what it found right away: each strike plays the hit and asks the metal anvil in front for its remote light hit
 * ({@link AnvilRemote}); a hit the anvil took damages the hammer's item, which is stored back in the device record; a rotation
 * that runs the wrong way for the facing breaks the hammer (its drops and its item). The angle and cooldown live in memory (a
 * restart starts them again).
 *
 * <p>{@link #start} and {@link #stop}: region thread of the hammer; the task runs there. The anvil is the next block in front,
 * read only when this thread owns it.
 */
public final class TripHammerService {
    /** Ticks stepped per run. */
    public static final int PERIOD = 20;

    /** The world effects; behind an interface for the tests. */
    public interface Effects {
        /** TFC {@code playSound(pos, TFCSounds.ANVIL_HIT, BLOCKS, 0.4f, 0.2f)} at the hammer. */
        void hit(Block hammer);

        /** TFC {@code playSound(pos, SoundEvents.ITEM_BREAK, BLOCKS)} when the hammer's item is used up. */
        void itemBroke(Block hammer);

        /** TFC {@code Helpers.damageItem(item, level)}: one point (Unbreaking applies); empty when it breaks. */
        ItemStack damage(ItemStack stack);
    }

    /** The live effects. */
    public static final Effects LIVE = new Effects() {
        @Override
        public void hit(Block hammer) {
            hammer.getWorld().playSound(hammer.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 0.4f,
                0.2f);
        }

        @Override
        public void itemBroke(Block hammer) {
            hammer.getWorld().playSound(hammer.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1f,
                1f);
        }

        @Override
        public ItemStack damage(ItemStack stack) {
            return HammerDamage.damageOnce(stack, java.util.concurrent.ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
        }
    };

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<Optional<RotationService>> rotations;
    private final Supplier<AnvilRemote> anvils;
    private final Effects effects;
    private final Map<Key, Running> running = new ConcurrentHashMap<>();
    private volatile DeviceService devices;
    private volatile DeviceKind<TripHammerDevice.Nothing> kind;

    record Key(UUID world, BlockPos pos) {
    }

    static final class Running {
        volatile Scheduler.TaskHandle handle;
        TripHammerRun.Drive drive = TripHammerRun.Drive.FRESH;
        int strikes;
    }

    public TripHammerService(Scheduler scheduler, CustomBlocks blocks, Supplier<Optional<RotationService>> rotations,
            Supplier<AnvilRemote> anvils, Effects effects) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.rotations = Objects.requireNonNull(rotations, "rotations");
        this.anvils = Objects.requireNonNull(anvils, "anvils");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    /** Wires the device framework and the hammer's kind (built after this service). */
    public void bind(DeviceService devices, DeviceKind<TripHammerDevice.Nothing> kind) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /** Starts the hammer at {@code block} (a second start is ignored). Region thread. */
    public void start(Block block) {
        World world = block.getWorld();
        BlockPos pos = pos(block);
        running.computeIfAbsent(new Key(world.getUID(), pos), k -> {
            Running r = new Running();
            r.handle = scheduler.regionRepeating(world, pos.x() >> 4, pos.z() >> 4, PERIOD, PERIOD, () -> run(world, pos, r));
            return r;
        });
    }

    /** Stops the hammer at {@code pos}. Any thread. */
    public void stop(UUID world, BlockPos pos) {
        Running r = running.remove(new Key(world, pos));
        if (r != null && r.handle != null) {
            r.handle.cancel();
        }
    }

    public int running() {
        return running.size();
    }

    /** The strikes of the hammer at {@code pos} so far (debug and tests); -1 when it is not running. */
    public int strikes(UUID world, BlockPos pos) {
        Running r = running.get(new Key(world, pos));
        return r == null ? -1 : r.strikes;
    }

    /** TFC {@code getRotation}: the rotation of the bladed axle above, null without one in a network. Region thread. */
    Rotation rotationAbove(Block hammer) {
        Block above = hammer.getRelative(0, 1, 0);
        if (blocks.stateAt(above).filter(TripHammerBlocks::isBladedAxle).isEmpty()) {
            return null;
        }
        return rotations.get().flatMap(s -> s.rotation(hammer.getWorld().getUID(), pos(above))).orElse(null);
    }

    /** One run. Region thread of the hammer. */
    void run(World world, BlockPos pos, Running r) {
        Block hammer = world.getBlockAt(pos.x(), pos.y(), pos.z());
        Optional<Direction> facing = blocks.stateAt(hammer).flatMap(TripHammerBlocks::facing);
        DeviceService d = devices;
        DeviceKind<TripHammerDevice.Nothing> k = kind;
        if (facing.isEmpty() || d == null || k == null) {
            if (facing.isEmpty()) {
                stop(world.getUID(), pos);
            }
            return;
        }
        Rotation rotation = rotationAbove(hammer);
        StackCodec stacks = d.stacks();
        ItemStack stored = d.inspect(hammer).flatMap(DeviceService.Inspection::record).map(rec -> stacks.decode(rec.slot(0)))
            .orElse(ItemStack.empty());
        ItemStack[] held = {stored};
        Block anvil = hammer.getRelative(facing.get().dx(), 0, facing.get().dz());
        TripHammerRun.Result result = TripHammerRun.run(r.drive, PERIOD, rotation, facing.get(), new TripHammerRun.World() {
            @Override
            public boolean hasItem() {
                return !StackCodec.isEmpty(held[0]);
            }

            @Override
            public Optional<BooleanSupplier> anvil() {
                if (!scheduler.isOwner(anvil.getWorld(), anvil.getX() >> 4, anvil.getZ() >> 4)
                    || blocks.stateAt(anvil).filter(TripHammerBlocks::isMetalAnvil).isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(() -> {
                    effects.hit(hammer);
                    return anvils.get().workRemotely(anvil, ForgeStep.HIT_LIGHT, TripHammerRules.ANVIL_LIGHT_HIT_MOVEMENT, true);
                });
            }

            @Override
            public void damageItem() {
                held[0] = effects.damage(held[0]);
                if (StackCodec.isEmpty(held[0])) {
                    effects.itemBroke(hammer);
                }
            }
        });
        r.drive = result.drive();
        r.strikes += result.strikes();
        if (held[0] != stored) {
            ItemStack next = StackCodec.isEmpty(held[0]) ? ItemStack.empty() : held[0];
            d.edit(hammer, k, (DeviceRecord<TripHammerDevice.Nothing> rec, long now) -> rec.withSlot(0, stacks.encode(next)));
        }
        if (result.broke()) {
            // TFC level.destroyBlock(pos, true): the block's drops and, through the device removal, its item
            stop(world.getUID(), pos);
            blocks.remove(hammer);
        }
    }
}
