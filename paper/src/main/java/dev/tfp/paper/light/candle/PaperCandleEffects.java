/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/CandleBlockItem.java @ c3b4d9536 (useOn: shrink, CAKE_ADD_CANDLE),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java @ c3b4d9536 (useItemOn: PUMPKIN_CARVE,
 *       damageItem, setBlock(..., 11))
 */
package dev.tfp.paper.light.candle;

import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link CandleEffects}. The burnout callback is one delayed region task per block: a newer request
 * replaces the queued one (the old task fires and does nothing); a task dropped by a chunk unload is replaced by the
 * chunk-load catch-up ({@link CandleService#chunkLoaded}). A state change of the same block ({@code lit}) is written as
 * its server block data without physics, so CraftEngine keeps the block and its counter.
 */
public final class PaperCandleEffects implements CandleEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Supplier<CandleService> service;
    private final Map<Pos, Long> pending = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** @param service the candle service (set after this is built: the device and the service need each other) */
    public PaperCandleEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals,
            Supplier<CandleService> service) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public void burnout(Block block) {
        CandleService s = service.get();
        if (s != null) {
            s.burnout(block);
        }
    }

    @Override
    public void burnoutIn(Block block, long ticks) {
        Pos key = Pos.of(block);
        long id = sequence.incrementAndGet();
        pending.put(key, id);
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1L, ticks), () -> {
            if (!pending.remove(key, id)) {
                return;
            }
            CandleService s = service.get();
            if (s == null || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                return;
            }
            long wait = s.callback(block);
            if (wait > 0) {
                burnoutIn(block, wait);
            }
        });
    }

    @Override
    public boolean setState(Block block, BlockStateRef state) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().id().equals(state.id())) {
            return false;
        }
        if (now.get().equals(state)) {
            return true;
        }
        Optional<BlockData> data = visuals.get().blockData(state);
        data.ifPresent(d -> block.setBlockData(d, false));
        return data.isPresent();
    }

    @Override
    public boolean place(Block block, BlockStateRef state) {
        return blocks.place(block, state, true);
    }

    @Override
    public void placeCarvedPumpkin(Block block, String facing) {
        BlockData data = Material.CARVED_PUMPKIN.createBlockData();
        if (data instanceof Directional d) {
            d.setFacing(BlockFace.valueOf(facing.toUpperCase(Locale.ROOT)));
        }
        block.setBlockData(data, true);
    }

    @Override
    public Optional<String> carvedPumpkin(Block block) {
        if (block.getType() != Material.CARVED_PUMPKIN || !(block.getBlockData() instanceof Directional d)) {
            return Optional.empty();
        }
        return Optional.of(d.getFacing().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void sound(Block block, Sound sound) {
        org.bukkit.Sound s = switch (sound) {
            case EXTINGUISH -> org.bukkit.Sound.BLOCK_CANDLE_EXTINGUISH;
            case ADD_CANDLE -> org.bukkit.Sound.BLOCK_CAKE_ADD_CANDLE;
            case CARVE -> org.bukkit.Sound.BLOCK_PUMPKIN_CARVE;
        };
        block.getWorld().playSound(centre(block), s, SoundCategory.BLOCKS, 1f, 1f);
    }

    @Override
    public void takeOne(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }

    @Override
    public void damageHeld(Player player, EquipmentSlot hand) {
        if (!StackCodec.isEmpty(player.getInventory().getItem(hand))) {
            player.damageItemStack(hand, 1);
        }
    }

    @Override
    public void drop(Block block, ItemStack stack) {
        if (!StackCodec.isEmpty(stack)) {
            block.getWorld().dropItemNaturally(centre(block), stack);
        }
    }

    private static Location centre(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
}
