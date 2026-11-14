/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (setLit: the sounds and
 *       the disabled message), src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound: volume 1 + rand,
 *       pitch rand + 0.7 + 0.3)
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.powderkeg.PowderkegState;
import dev.tfp.paper.i18n.Messages;
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
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * PAR-PROC-18: {@link PowderkegEffects} on Paper. State writes and the explosion run as region tasks of the block; the
 * fuse callback is a delayed region task, one per keg and due time.
 */
public final class PaperPowderkegEffects implements PowderkegEffects {
    /** How far past its due time a queued callback counts as dropped (region or chunk unload) and is replaced. */
    static final long STALE_MILLIS = 10_000L;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> callback;
    private final Supplier<PowderkegBlasts> blasts;
    private final Messages messages;
    private final LongSupplier clock;
    private final Map<Key, Long> pending = new ConcurrentHashMap<>();

    private record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /**
     * @param callback the device router's neighbour update (the fuse's end)
     * @param blasts   the blast listener that runs the explosion
     */
    public PaperPowderkegEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals,
            Consumer<Block> callback, Supplier<PowderkegBlasts> blasts, Messages messages) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.callback = Objects.requireNonNull(callback, "callback");
        this.blasts = Objects.requireNonNull(blasts, "blasts");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = System::currentTimeMillis;
    }

    @Override
    public void show(Block block, PowderkegState state) {
        BlockStateRef wanted = PowderkegDevice.state(state);
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(PowderkegDevice.ID) || wanted.equals(now.get())) {
                return;
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void sound(Block block, Sound sound) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (sound) {
            case PRIMED -> block.getWorld().playSound(at, org.bukkit.Sound.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS,
                1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
            // TFCSounds.ITEM_COOL is TFC's own sound event; the vanilla lava extinguish hiss stands in (ledger)
            case COOL -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS,
                1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
            case EXTINGUISH -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS,
                1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
            // Helpers.playPlaceSound: the block's place sound (wood), volume (1 + 1) / 2, pitch 0.8
            case PLACE -> block.getWorld().playSound(at, org.bukkit.Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
            default -> throw new IllegalStateException("unknown sound " + sound);
        }
    }

    @Override
    public void disabled(Player player) {
        player.sendActionBar(Component.text(messages.get(player, "powderkeg.disabled")));
    }

    @Override
    public void fuse(Block block, long ticks) {
        Key key = Key.of(block);
        long delay = Math.max(1, ticks);
        long now = clock.getAsLong();
        long due = now + delay * 50L;
        boolean[] queued = {false};
        pending.compute(key, (k, old) -> {
            if (old != null && old <= due && now <= old + STALE_MILLIS) {
                return old; // an earlier or equal callback is still queued; it re-queues the rest
            }
            queued[0] = true;
            return due;
        });
        if (!queued[0]) {
            return;
        }
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, delay, () -> {
            if (pending.remove(key, due)) {
                callback.accept(block);
            }
        });
    }

    @Override
    public void explode(Block block, int strength) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> blasts.get().explode(block, strength));
    }
}
