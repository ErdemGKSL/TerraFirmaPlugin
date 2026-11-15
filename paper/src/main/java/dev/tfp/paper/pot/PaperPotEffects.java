/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCDamageTypes.java @ c3b4d9536 (pot),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound: volume 1 + nextFloat, pitch
 *       nextFloat + 0.7 + 0.3)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The pot's world effects on Paper. Block changes run as region tasks on the next tick, after the click's record is
 * stored, and only while the block is still a pot. The burn-out callback works as the firepit's: the latest one per block
 * wins.
 */
public final class PaperPotEffects implements PotEffects {
    /** TFC's {@code TFCDamageTypes.POT}, renamed. */
    public static final Key POT_DAMAGE = Key.key("tfp", "pot");

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> callback;
    private final Predicate<Block> toFirepit;
    private final BiConsumer<Player, Block> screen;
    private final Map<Pos, Long> pending = new ConcurrentHashMap<>();
    private final Map<Pos, Boolean> converting = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile DamageType damage;

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /**
     * @param callback  calls the device back (the device router's neighbour update)
     * @param toFirepit the swap back to a firepit ({@link PotConversion#toFirepit}), returning whether it happened
     * @param screen    opens the pot screen for a player on their thread
     */
    public PaperPotEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals,
            Consumer<Block> callback, Predicate<Block> toFirepit, BiConsumer<Player, Block> screen) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.callback = Objects.requireNonNull(callback, "callback");
        this.toFirepit = Objects.requireNonNull(toFirepit, "toFirepit");
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    private boolean isPot(Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(PotDevice.ID)).orElse(false);
    }

    @Override
    public void show(Block block, BlockStateRef state) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(PotDevice.ID) || state.equals(now.get())) {
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
            // TFCSounds.ITEM_COOL is TFC's own sound event; the vanilla lava extinguish hiss stands in (ledger)
            case COOL -> org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH;
        };
        if (sound == PotEffects.Sound.LOG) {
            block.getWorld().playSound(centre(block), s, SoundCategory.BLOCKS, 1f, 0.8f);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().playSound(centre(block), s, SoundCategory.BLOCKS, 1f + r.nextFloat(), r.nextFloat() + 1f);
    }

    @Override
    public void give(Player player, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        if (!StackCodec.isEmpty(stack)) {
            block.getWorld().dropItem(centre(block), stack);
        }
    }

    @Override
    public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        boolean lava = TfcFluids.LAVA.equals(fluid.fluid());
        org.bukkit.Sound sound = intoItem
            ? (lava ? org.bukkit.Sound.ITEM_BUCKET_FILL_LAVA : org.bukkit.Sound.ITEM_BUCKET_FILL)
            : (lava ? org.bukkit.Sound.ITEM_BUCKET_EMPTY_LAVA : org.bukkit.Sound.ITEM_BUCKET_EMPTY);
        block.getWorld().playSound(centre(block), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void hurt(Player player, float amount) {
        player.damage(amount, DamageSource.builder(damageType()).build());
    }

    /** The server's {@code tfp:pot}, or vanilla {@code in_fire} when the registry lacks it. */
    private DamageType damageType() {
        DamageType t = damage;
        if (t == null) {
            try {
                t = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(POT_DAMAGE);
            } catch (RuntimeException e) {
                t = null; // no registry access: the fallback below
            }
            if (t == null) {
                t = DamageType.IN_FIRE;
            }
            damage = t;
        }
        return t;
    }

    @Override
    public void callbackIn(Block block, long ticks) {
        Pos key = Pos.of(block);
        long id = sequence.incrementAndGet();
        pending.put(key, id);
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1, ticks), () -> {
            if (pending.remove(key, id)) {
                callback.accept(block);
            }
        });
    }

    @Override
    public void breakBlock(Block block) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            if (isPot(block)) {
                blocks.remove(block);
            }
        });
    }

    @Override
    public void toFirepit(Block block, Player player, Runnable atBlock, Runnable atPlayer, Runnable playerGone) {
        Pos key = Pos.of(block);
        if (converting.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            boolean converted;
            try {
                converted = isPot(block) && toFirepit.test(block);
            } finally {
                converting.remove(key);
            }
            if (!converted) {
                return; // refused: the pot (or what replaced it) stays, nothing is handed out
            }
            atBlock.run();
            if (!player.isValid() || !scheduler.entity(player, atPlayer,
                    () -> scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, playerGone))) {
                playerGone.run();
            }
        });
    }

    @Override
    public void openScreen(Player player, Block block) {
        scheduler.entityDelayed(player, 1, () -> {
            if (player.isOnline() && isPotOwned(block)) {
                screen.accept(player, block);
            }
        }, () -> { });
    }

    private boolean isPotOwned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4) && isPot(block);
    }

    @Override
    public boolean converting(Block block) {
        return converting.containsKey(Pos.of(block));
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
