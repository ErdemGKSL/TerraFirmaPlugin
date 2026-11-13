/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/TFCComposterBlock.java @ c3b4d9536 (animateTick: the
 * particle position and count),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (the sounds, setState),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound, spawnItem)
 */
package dev.tfp.paper.compost;

import dev.tfp.core.compost.CompostBin;
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
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The server side of {@link ComposterEffects}. The block state ({@code stage}, {@code type}) is written on the next tick
 * with {@code setBlockData(data, false)} while the block is still {@code tfp:composter} (CraftEngine keeps the block
 * entity). TFC's {@code COMPOST_READY} / {@code COMPOST_ROTTEN} particles are client particle types the vanilla client
 * lacks: {@link Particle#HAPPY_VILLAGER} and {@link Particle#MYCELIUM} stand in (ledger).
 */
public final class PaperComposterEffects implements ComposterEffects {
    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> callback;
    private final ComposterLang lang;
    /**
     * Composters with a ripening callback queued, with its due time (wall-clock millis). A request due earlier replaces
     * the queued one (the ready ticks got shorter); the replaced task fires but finds a different due time and does
     * nothing. A due time long past means the task was dropped (region or chunk unload) and is replaced as well.
     */
    private final Map<Pending, Long> pending = new ConcurrentHashMap<>();
    /** How far past its due time a queued callback counts as dropped. */
    static final long STALE_MILLIS = 10_000L;

    private record Pending(UUID world, int x, int y, int z) {
        static Pending of(Block block) {
            return new Pending(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** @param callback calls the device back (the device router's neighbour update) */
    public PaperComposterEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals,
            Consumer<Block> callback, ComposterLang lang) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.callback = Objects.requireNonNull(callback, "callback");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    @Override
    public void show(Block block, CompostBin bin) {
        BlockStateRef wanted = ComposterDevice.state(bin);
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(ComposterDevice.ID) || wanted.equals(now.get())) {
                return; // broken or replaced meanwhile, or already right
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    @Override
    public void sound(Block block, Sound sound) {
        org.bukkit.Sound s = switch (sound) {
            case ADD -> org.bukkit.Sound.ITEM_HOE_TILL;
            case EXTRACT -> org.bukkit.Sound.BLOCK_ROOTED_DIRT_BREAK;
        };
        // TFC Helpers.playSound: volume 1 + nextFloat, pitch nextFloat + 0.7 + 0.3
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().playSound(centre(block), s, SoundCategory.BLOCKS, 1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
    }

    @Override
    public void message(Player player, Message message) {
        player.sendActionBar(Component.text(lang.get(player.locale().toString(), message.key())));
    }

    @Override
    public void spawn(Block block, ItemStack stack, boolean above) {
        Location at = above ? centre(block).add(0, 1, 0) : centre(block);
        block.getWorld().dropItem(at, stack, e -> e.setPickupDelay(0));
    }

    @Override
    public void particles(Block block, CompostBin.Type type) {
        Particle p = type == CompostBin.Type.READY ? Particle.HAPPY_VILLAGER : Particle.MYCELIUM;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // TFC animateTick: up to 4 at (x + rand, y + 1 + rand / 5, z + rand); one burst of 4 per interaction here
        for (int i = 0; i < 4; i++) {
            block.getWorld().spawnParticle(p, block.getX() + r.nextDouble(), block.getY() + 1 + r.nextDouble() / 5D,
                block.getZ() + r.nextDouble(), 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void ripenAt(Block block, long ticks) {
        Pending key = Pending.of(block);
        long delay = Math.max(1, ticks);
        long now = System.currentTimeMillis();
        long due = now + delay * 50L;
        boolean[] queued = {false};
        pending.compute(key, (k, old) -> {
            if (old != null && old <= due && now <= old + STALE_MILLIS) {
                return old; // an earlier or equal callback is still queued
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
    public int hopperRoom(Block block, ItemStack stack) {
        Inventory inv = hopperBelow(block);
        if (inv == null) {
            return 0;
        }
        int room = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType() == Material.AIR) {
                room += stack.getMaxStackSize();
            } else if (s.isSimilar(stack)) {
                room += Math.max(0, s.getMaxStackSize() - s.getAmount());
            }
        }
        return Math.min(room, stack.getAmount());
    }

    @Override
    public void toHopper(Block block, ItemStack stack) {
        ItemStack moved = stack.clone();
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Inventory inv = hopperBelow(block);
            Map<Integer, ItemStack> rest = inv == null ? Map.of(0, moved) : inv.addItem(moved);
            Block below = block.getRelative(BlockFace.DOWN);
            for (ItemStack left : rest.values()) {
                block.getWorld().dropItem(centre(below).add(0, 0.5, 0), left, e -> e.setPickupDelay(0));
            }
        });
    }

    /** The inventory of an enabled (unpowered) hopper directly below, or {@code null}. */
    static Inventory hopperBelow(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.HOPPER) {
            return null;
        }
        if (below.getBlockData() instanceof org.bukkit.block.data.type.Hopper data && !data.isEnabled()) {
            return null;
        }
        return below.getState(false) instanceof Hopper hopper ? hopper.getInventory() : null;
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
