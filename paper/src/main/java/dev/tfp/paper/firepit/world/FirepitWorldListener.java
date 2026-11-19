/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (serverTick: gatherAndConsumeItems),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (stepOn: hotFloor),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (gatherAndConsumeItems)
 */
package dev.tfp.paper.firepit.world;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitGather;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * PAR-HEAT-30: the firepit, grill and pot world rules without a per-block tick.
 *
 * <ul>
 *   <li>Gathering: TFC's {@code serverTick} gathers fuel item entities in the box (x, y, z)-(x+1, y+0.5, z+1) every 20
 *       ticks into the fuel slots. Here every spawned item entity gets an entity task every {@value #PERIOD} ticks (it
 *       stops once the item is gone or has rested {@value #REST_CHECKS} checks); inside the box of one of these devices,
 *       one device edit inserts what the fuel slots accept ({@link FirepitGather}) and shrinks the entity.</li>
 *   <li>Hot floor: TFC's {@code FirepitBlock.stepOn} hurts a living entity standing on a lit device with
 *       {@code hotFloor} for 1. Players only: a move onto a lit device starts an entity task (every 10 ticks, the hurt
 *       cooldown) that hurts while the player stands on it; creative and spectator take none (PROTOCOL 4.7). Mobs are a
 *       ledger divergence (no per-mob move event).</li>
 * </ul>
 */
public final class FirepitWorldListener implements Listener {
    public static final long PERIOD = 20;
    public static final int REST_CHECKS = 10;
    /** TFC's gather box, block-local. */
    public static final BoundingBox BOX = new BoundingBox(0, 0, 0, 1, 0.5, 1);
    /** The hurt cooldown of a hot floor, in ticks. */
    public static final long HURT_PERIOD = 10;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Set<String> ids;
    private final Map<UUID, Scheduler.TaskHandle> hot = new ConcurrentHashMap<>();
    /** TFC {@code hotFloor} damage of 1; replaced in tests (MockBukkit has no damage sources). */
    private volatile java.util.function.Consumer<Player> damager = p -> p.damage(1.0,
        DamageSource.builder(DamageType.HOT_FLOOR).build());

    /** @param ids the block ids of the devices (firepit, grill, pot) */
    public FirepitWorldListener(Scheduler scheduler, CustomBlocks blocks, DeviceService devices, Set<String> ids) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.devices = devices;
        this.ids = ids;
    }

    private Optional<BlockStateRef> device(Block block) {
        return blocks.stateAt(block).filter(s -> ids.contains(s.id()));
    }

    // ---- gathering -------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        AtomicInteger rested = new AtomicInteger();
        double[] last = new double[3];
        Scheduler.TaskHandle[] handle = new Scheduler.TaskHandle[1];
        handle[0] = scheduler.entityRepeating(item, PERIOD, PERIOD, () -> {
            if (!item.isValid() || item.getItemStack().getType().isAir()) {
                handle[0].cancel();
                return;
            }
            boolean moved = gather(item);
            Location l = item.getLocation();
            boolean still = l.getX() == last[0] && l.getY() == last[1] && l.getZ() == last[2];
            last[0] = l.getX();
            last[1] = l.getY();
            last[2] = l.getZ();
            if (!still || moved) {
                rested.set(0);
            } else if (rested.incrementAndGet() >= REST_CHECKS) {
                handle[0].cancel();
            }
        }, () -> { });
    }

    /** Entity thread of {@code item}: one gather check. Returns whether anything moved. */
    boolean gather(Item item) {
        Block block = item.getLocation().getBlock();
        Optional<BlockStateRef> state = device(block);
        if (state.isEmpty()) {
            return false;
        }
        BoundingBox box = BOX.clone().shift(block.getX(), block.getY(), block.getZ());
        if (!box.overlaps(item.getBoundingBox())
                || !scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Optional<DeviceKind<FirepitData>> kind = devices.kinds().find(state.get().id()).map(k -> (DeviceKind<FirepitData>) k);
        if (kind.isEmpty()) {
            return false;
        }
        ItemStack stack = item.getItemStack();
        if (!kind.get().filter().canInsert(FirepitSim.SLOT_FUEL_INPUT, stack)) {
            return false; // TFC tests isItemValid(maxSlot, stack): fuel only
        }
        int[] taken = new int[1];
        devices.edit(block, kind.get(), (record, now) -> {
            DeviceRecord<FirepitData> next = apply(record, kind.get(), stack, taken);
            return next == null ? record : next;
        });
        return finish(item, stack, taken[0], block);
    }

    /** Fills {@code taken[0]} and returns the record with the items added, or null when nothing fits. */
    private DeviceRecord<FirepitData> apply(DeviceRecord<FirepitData> record, DeviceKind<FirepitData> kind, ItemStack stack,
            int[] taken) {
        int n = FirepitSim.SLOT_FUEL_INPUT - FirepitSim.SLOT_FUEL_CONSUME + 1;
        int[] limits = new int[n];
        int[] counts = new int[n];
        boolean[] same = new boolean[n];
        boolean[] valid = new boolean[n];
        ItemStack[] held = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            int slot = FirepitSim.SLOT_FUEL_CONSUME + i;
            held[i] = devices.stacks().decode(record.slot(slot));
            limits[i] = Math.min(kind.slotLimit(slot), stack.getMaxStackSize());
            counts[i] = StackCodec.isEmpty(held[i]) ? 0 : held[i].getAmount();
            same[i] = counts[i] > 0 && held[i].isSimilar(stack);
            valid[i] = kind.filter().canInsert(slot, stack);
        }
        int[] add = FirepitGather.insert(limits, counts, same, valid, stack.getAmount());
        DeviceRecord<FirepitData> next = record;
        int total = 0;
        for (int i = 0; i < n; i++) {
            if (add[i] > 0) {
                ItemStack merged = counts[i] == 0 ? stack.clone() : held[i].clone();
                merged.setAmount(counts[i] + add[i]);
                next = next.withSlot(FirepitSim.SLOT_FUEL_CONSUME + i, devices.stacks().encode(merged));
                total += add[i];
            }
        }
        taken[0] = total;
        return total == 0 ? null : next;
    }

    private boolean finish(Item item, ItemStack stack, int taken, Block block) {
        if (taken <= 0) {
            return false;
        }
        if (taken >= stack.getAmount()) {
            item.remove();
        } else {
            ItemStack rest = stack.clone();
            rest.setAmount(stack.getAmount() - taken);
            item.setItemStack(rest);
        }
        devices.neighbourChanged(block); // the lit look and the burn-out callback follow the new fuel
        return true;
    }

    // ---- hot floor -------------------------------------------------------------------------------------

    /** Whether {@code player} stands on a lit device now. */
    boolean onHotFloor(Player player) {
        Location l = player.getLocation();
        Block under = l.clone().subtract(0, 0.01, 0).getBlock();
        return device(under).map(s -> "true".equals(s.properties().get("lit"))).orElse(false);
    }

    private static boolean takesDamage(Player player) {
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR && !player.isInvulnerable();
    }

    /** One hurt: TFC {@code entity.hurt(damageSources().hotFloor(), 1)}. Entity thread of {@code player}. */
    void hurt(Player player) {
        if (takesDamage(player)) {
            damager.accept(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || hot.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        Player player = event.getPlayer();
        if (!onHotFloor(player) && !onHotFloorAt(event.getTo())) {
            return;
        }
        Scheduler.TaskHandle[] handle = new Scheduler.TaskHandle[1];
        handle[0] = scheduler.entityRepeating(player, 1, HURT_PERIOD, () -> {
            if (!player.isOnline() || !onHotFloor(player)) {
                handle[0].cancel();
                hot.remove(player.getUniqueId());
                return;
            }
            hurt(player);
        }, () -> hot.remove(player.getUniqueId()));
        hot.put(player.getUniqueId(), handle[0]);
    }

    private boolean onHotFloorAt(Location to) {
        Block under = to.clone().subtract(0, 0.01, 0).getBlock();
        return device(under).map(s -> "true".equals(s.properties().get("lit"))).orElse(false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Scheduler.TaskHandle h = hot.remove(event.getPlayer().getUniqueId());
        if (h != null) {
            h.cancel();
        }
    }

    /** Test seam: one gather check (the scheduler of a unit test cannot run entity tasks). */
    public boolean gatherForTest(Item item) {
        return gather(item);
    }

    /** Test seam. */
    public boolean onHotFloorForTest(Player player) {
        return onHotFloor(player);
    }

    /** Test seam. */
    public void hurtForTest(Player player) {
        hurt(player);
    }

    /** Test seam: replaces the damage call. */
    public void damageWithForTest(java.util.function.Consumer<Player> next) {
        this.damager = next;
    }
}
