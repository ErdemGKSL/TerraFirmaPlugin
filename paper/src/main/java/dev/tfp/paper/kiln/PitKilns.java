/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (useItemOn: straw, logs,
 *       empty-hand take; updateShape: no sturdy base -> air, fire above an unlit kiln -> tryLight),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (useItemOn: straw on a placed
 *       item with a valid structure),
 *       src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536
 *       (convertPlacedItemToPitKiln),
 *       src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (serverTick, tryLight,
 *       light, DIAGONALS, emptyFuelContents, convertPitKilnToPlacedItem, cookContents)
 */
package dev.tfp.paper.kiln;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.kiln.PitKilnFiring;
import dev.tfp.core.kiln.PitKilnLayers;
import dev.tfp.core.kiln.PitKilnRules;
import dev.tfp.core.kiln.PitKilnState;
import dev.tfp.core.placed.PlacedRules;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.placed.PlacedItemDevice;
import dev.tfp.paper.placed.PlacedItemEffects;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The pit kiln on its region thread: building it from a placed item, adding and taking straw and logs, lighting it and
 * the lazy firing. Each entry point reads the block, decides with the core rules ({@link PitKilnLayers},
 * {@link PitKilnRules}, {@link PitKilnFiring}; O(1) per kiln) and writes the record and the block inline, as the placed
 * items do. Nothing ticks: a lit kiln is evaluated on a neighbour update, by the {@link KilnClock} when its burn time is
 * up, and on chunk load; {@link #evaluate} catches it up to the calendar tick of that moment.
 */
public final class PitKilns {
    /** TFC {@code PitKilnBlockEntity.DIAGONALS}: lighting a kiln tries the four diagonal neighbours. */
    static final int[][] DIAGONALS = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    /** The world checks the kiln makes (region thread of the block). */
    public interface Structure {
        /** TFC {@code isValid}: four sturdy non-flammable sides and a sturdy base. */
        boolean valid(Block kiln);

        /** {@code tryLight}'s second check: all four sides sturdy (no flammability check). */
        boolean sidesSturdy(Block kiln);

        /** {@code updateShape(DOWN)}: the block below has a sturdy top face. */
        boolean baseSturdy(Block kiln);
    }

    /**
     * What the kiln reads from the rest of the plugin.
     *
     * @param blocks     the custom block adapter
     * @param devices    the device service
     * @param placedKind the placed item device kind, empty while placed items are off
     * @param strawValue TFC {@code PitKilnBlock.strawValue(item)} of a stack (4, 1 or 0)
     * @param log        whether a stack is in {@code #tfc:pit_kiln_logs}
     * @param items      the stack operations of the firing (heat and heating recipes)
     * @param structure  the world checks
     * @param config     {@code pitKilnTicks} and {@code pitKilnTemperature}
     * @param now        the calendar (player) tick, TFC {@code Calendars.SERVER.getTicks()}
     * @param effects    the block stage, sounds, drops
     * @param contents   the content displays (the placed item renderer)
     * @param clock      the lit-kiln clock
     * @param scheduler  to reach a diagonal kiln on another region
     */
    public record Services(CustomBlocks blocks, DeviceService devices, Supplier<Optional<DeviceKind<PlacedState>>> placedKind,
            ToIntFunction<ItemStack> strawValue, Predicate<ItemStack> log, PitKilnFiring.Items<ItemStack> items,
            Structure structure, PitKilnRules.Config config, LongSupplier now, KilnEffects effects,
            PlacedItemEffects contents, KilnClock clock, Scheduler scheduler) {
        public Services {
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(placedKind, "placedKind");
            Objects.requireNonNull(strawValue, "strawValue");
            Objects.requireNonNull(log, "log");
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(contents, "contents");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(scheduler, "scheduler");
        }
    }

    private final Services s;
    private DeviceKind<PitKilnState> kind;

    public PitKilns(Services services) {
        this.s = Objects.requireNonNull(services, "services");
    }

    /** Sets the registered kind (it needs this object's {@link #later} first). */
    public void kind(DeviceKind<PitKilnState> registered) {
        this.kind = Objects.requireNonNull(registered, "registered");
    }

    public DeviceKind<PitKilnState> kind() {
        return kind;
    }

    /** The stage of the kiln at {@code block}, or {@code -1} when it is no kiln. */
    public int stageAt(Block block) {
        return s.blocks().stateAt(block).map(PitKilnDevice::stage).orElse(-1);
    }

    /** Hands {@code block} to {@link #evaluate} on its region thread, after the current callback. */
    public void later(Block block) {
        s.scheduler().region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> evaluate(block));
    }

    // ---- building -----------------------------------------------------------------------------------------------

    /**
     * TFC {@code PlacedItemBlock.useItemOn}: straw on a placed item block whose structure is valid turns it into a kiln
     * holding the items and that straw ({@code convertPlacedItemToPitKiln}).
     *
     * @return whether the click was used
     */
    public boolean fromPlaced(Player player, EquipmentSlot hand, Block block, ItemStack held) {
        int value = StackCodec.isEmpty(held) ? 0 : s.strawValue().applyAsInt(held);
        Optional<DeviceKind<PlacedState>> pk = s.placedKind().get();
        if (value == 0 || pk.isEmpty() || kind == null || !s.blocks().exists(PitKilnDevice.ID)
                || !s.blocks().stateAt(block).map(st -> st.id().equals(PlacedItemDevice.ID)).orElse(false)
                || !s.structure().valid(block)) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>();
        PlacedState[] placed = {PlacedState.EMPTY};
        Optional<DeviceRecord<PlacedState>> taken = s.devices().edit(block, pk.get(), (record, now) -> {
            DeviceInventory<PlacedState> inv = new DeviceInventory<>(pk.get(), record, s.devices().stacks());
            for (int i = 0; i < PlacedRules.SLOTS; i++) {
                items.add(inv.get(i));
            }
            placed[0] = record.state();
            return record.cleared(); // copyToAndClear: the removal of the placed block drops nothing
        });
        if (taken.isEmpty()) {
            return false;
        }
        int stage = PitKilnLayers.stageFromPlaced(value);
        if (!s.blocks().place(block, PitKilnDevice.state(stage), true)) {
            restorePlaced(block, pk.get(), items, placed[0]);
            return false;
        }
        ItemStack straw = one(held);
        Optional<DeviceRecord<PitKilnState>> made = s.devices().edit(block, kind, (record, now) -> {
            DeviceInventory<PitKilnState> inv = new DeviceInventory<>(kind, record, s.devices().stacks());
            for (int i = 0; i < PlacedRules.SLOTS; i++) {
                inv = inv.set(i, items.get(i));
            }
            inv = inv.set(PitKilnState.strawSlot(stage), straw);
            return inv.record().withState(PitKilnState.fromPlaced(placed[0]));
        });
        if (made.isEmpty()) {
            // no kiln record: give the items back as a placed item block
            toPlaced(block, items, placed[0]);
            return false;
        }
        shrink(player, hand, held);
        s.effects().sound(block, KilnEffects.Sound.CONVERT);
        showContents(block, stage, placed[0], items);
        return true;
    }

    /**
     * TFC {@code PitKilnBlock.useItemOn} with the main hand: straw onto the straw layers, a log onto full straw, or an
     * empty hand taking the top layer back (the last straw turns the kiln back into a placed item block).
     *
     * @return whether the click was used
     */
    public boolean use(Player player, EquipmentSlot hand, Block block, ItemStack held) {
        int stage = stageAt(block);
        if (stage < 0 || kind == null || hand != EquipmentSlot.HAND) {
            return false;
        }
        boolean empty = StackCodec.isEmpty(held);
        int value = empty ? 0 : s.strawValue().applyAsInt(held);
        if (value > 0 && PitKilnLayers.canAddStraw(stage, value)) {
            int next = PitKilnLayers.stageAfterStraw(stage, value);
            return add(player, hand, block, held, stage, next, PitKilnState.strawSlot(PitKilnLayers.strawSlotAfterAdd(stage,
                value)), KilnEffects.Sound.STRAW);
        }
        if (!empty && PitKilnLayers.canAddLog(stage) && s.log().test(held)) {
            return add(player, hand, block, held, stage, PitKilnLayers.stageAfterLog(stage),
                PitKilnState.logSlot(PitKilnLayers.logSlotAfterAdd(stage)), KilnEffects.Sound.LOG);
        }
        if (empty) {
            return take(player, block, stage);
        }
        return false;
    }

    private boolean add(Player player, EquipmentSlot hand, Block block, ItemStack held, int stage, int next, int slot,
            KilnEffects.Sound sound) {
        ItemStack one = one(held);
        Optional<DeviceRecord<PitKilnState>> stored = s.devices().edit(block, kind, (record, now) ->
            record.state().lit() ? record : new DeviceInventory<>(kind, record, s.devices().stacks()).set(slot, one).record());
        if (stored.isEmpty() || stored.get().state().lit()) {
            return false;
        }
        s.effects().stage(block, next, false); // TFC flag 10: clients only
        shrink(player, hand, held);
        s.effects().sound(block, sound);
        showContents(block, next, stored.get());
        return true;
    }

    private boolean take(Player player, Block block, int stage) {
        Optional<PitKilnLayers.Take> t = PitKilnLayers.take(stage);
        if (t.isEmpty()) {
            return false;
        }
        int slot = t.get().log() ? PitKilnState.logSlot(t.get().slot()) : PitKilnState.strawSlot(t.get().slot());
        ItemStack[] taken = {ItemStack.empty()};
        Optional<DeviceRecord<PitKilnState>> stored = s.devices().edit(block, kind, (record, now) -> {
            if (record.state().lit()) {
                return record;
            }
            DeviceInventory<PitKilnState> inv = new DeviceInventory<>(kind, record, s.devices().stacks());
            taken[0] = inv.get(slot);
            return inv.set(slot, ItemStack.empty()).record();
        });
        if (stored.isEmpty() || stored.get().state().lit()) {
            return false;
        }
        ItemStack drop = taken[0];
        int remove = t.get().log() ? 1 : StackCodec.isEmpty(drop) ? 0 : drop.getAmount() * s.strawValue().applyAsInt(drop);
        if (!StackCodec.isEmpty(drop)) {
            s.effects().give(player, drop);
        }
        int next = PitKilnLayers.stageAfterTake(stage, remove);
        if (next < 0) {
            convertToPlaced(block, false, null);
        } else if (next != stage) {
            s.effects().stage(block, next, false);
            showContents(block, next, stored.get());
        }
        return true;
    }

    // ---- lighting ----------------------------------------------------------------------------------------------

    /**
     * TFC {@code tryLight}: a kiln with its eight logs, a valid structure, room for fire above and four sturdy sides is
     * lit (straw and logs burn, the block turns to the lit stage, fire is set above) and then tries its diagonal kilns.
     *
     * @return whether this kiln was lit
     */
    public boolean tryLight(Block block) {
        int stage = stageAt(block);
        if (!PitKilnLayers.hasFuel(stage) || kind == null) {
            return false;
        }
        Optional<PitKilnState> state = read(block);
        Block above = block.getRelative(BlockFace.UP);
        // BaseFireBlock.canBePlacedAt(above, UP): the space is empty; a kiln with its logs is a full block fire stands on
        if (state.isEmpty() || state.get().lit() || !s.structure().valid(block) || !above.getType().isAir()
                || !s.structure().sidesSturdy(block)) {
            return false;
        }
        long[] litTick = {0L};
        Optional<DeviceRecord<PitKilnState>> stored = s.devices().edit(block, kind, (record, now) -> {
            litTick[0] = now;
            DeviceRecord<PitKilnState> next = record.withState(record.state().light(now));
            DeviceInventory<PitKilnState> inv = new DeviceInventory<>(kind, next, s.devices().stacks());
            for (int i = PitKilnState.FIRST_STRAW; i < PitKilnState.SLOTS; i++) {
                inv = inv.set(i, ItemStack.empty()); // the straw and logs burn (never dropped while lit)
            }
            return inv.record();
        });
        if (stored.isEmpty()) {
            return false;
        }
        s.effects().stage(block, PitKilnLayers.LIT, true);
        s.contents().hide(block);
        above.setType(Material.FIRE, true);
        s.clock().track(block, litTick[0] + s.config().durationTicks());
        for (int[] d : DIAGONALS) {
            Block other = block.getRelative(d[0], 0, d[1]);
            if (s.scheduler().isOwner(other.getWorld(), other.getX() >> 4, other.getZ() >> 4)) {
                tryLight(other);
            } else {
                s.scheduler().region(other.getWorld(), other.getX() >> 4, other.getZ() >> 4, () -> tryLight(other));
            }
        }
        return true;
    }

    // ---- lazy firing -------------------------------------------------------------------------------------------

    /**
     * The kiln at {@code block} at the current calendar tick: TFC's {@code updateShape} and, for a lit kiln, the checks of
     * {@code serverTick} in its order (fire above, structure, burn time). A lit kiln that fired or failed becomes a placed
     * item block with its contents; one still burning is listed on the {@link KilnClock}.
     */
    public void evaluate(Block block) {
        int stage = stageAt(block);
        if (stage < 0 || kind == null) {
            s.clock().untrack(block);
            return;
        }
        if (!s.structure().baseSturdy(block)) {
            s.clock().untrack(block);
            s.blocks().remove(block); // updateShape: air; the contents (and an unlit kiln's fuel) drop
            return;
        }
        Optional<PitKilnState> state = read(block);
        if (state.isEmpty()) {
            return;
        }
        Block above = block.getRelative(BlockFace.UP);
        if (!state.get().lit()) {
            if (above.getType() == Material.FIRE) {
                above.setType(Material.AIR, true); // updateShape(UP, fire): the fire goes, then tryLight
                tryLight(block);
            }
            return;
        }
        long now = s.now().getAsLong();
        long litTick = state.get().litTick();
        long end = litTick + s.config().durationTicks();
        boolean failed = false;
        if (above.getType().isAir()) {
            if (now < end) {
                above.setType(Material.FIRE, true);
            }
        } else if (above.getType() != Material.FIRE) {
            failed = true;
        }
        if (!failed && !s.structure().valid(block)) {
            failed = true;
        }
        long nowClamped = Math.max(now, litTick);
        PitKilnRules.Outcome outcome = PitKilnRules.evaluate(true, litTick, nowClamped, s.config(),
            failed ? nowClamped : PitKilnRules.NEVER);
        if (outcome.phase() == PitKilnRules.Phase.LIT) {
            s.clock().track(block, end);
            return;
        }
        s.clock().untrack(block);
        if (outcome.phase() == PitKilnRules.Phase.FIRED && above.getType() == Material.FIRE) {
            above.setType(Material.AIR, true);
        }
        convertToPlaced(block, true, outcome);
    }

    /**
     * TFC {@code convertPitKilnToPlacedItem}: the contents (fired by {@code outcome} when given) go into a placed item
     * block at the kiln's place; the fuel is gone.
     */
    private void convertToPlaced(Block block, boolean sound, PitKilnRules.Outcome outcome) {
        List<ItemStack> items = new ArrayList<>();
        PlacedState[] placed = {PlacedState.EMPTY};
        Optional<DeviceRecord<PitKilnState>> cleared = s.devices().edit(block, kind, (record, now) -> {
            DeviceInventory<PitKilnState> inv = new DeviceInventory<>(kind, record, s.devices().stacks());
            for (int i = 0; i < PitKilnState.CONTENT_SLOTS; i++) {
                ItemStack stack = inv.get(i);
                items.add(outcome == null ? stack
                    : PitKilnFiring.resolve(stack, record.state().litTick(), outcome, s.config(), s.items()));
            }
            placed[0] = record.state().placed();
            return record.cleared(); // copyToAndClear (and emptyFuelContents): the kiln's removal drops nothing
        });
        if (cleared.isEmpty()) {
            return;
        }
        if (sound) {
            s.effects().sound(block, KilnEffects.Sound.EXTINGUISH);
        }
        toPlaced(block, items, placed[0]);
    }

    /**
     * The items as a placed item block at {@code block}. A kiln whose contents are all gone (melted) becomes air instead
     * of an empty placed item block (ledger divergence); without the placed item kind the items drop.
     */
    private void toPlaced(Block block, List<ItemStack> items, PlacedState placed) {
        boolean any = items.stream().anyMatch(i -> !StackCodec.isEmpty(i));
        Optional<DeviceKind<PlacedState>> pk = s.placedKind().get();
        if (!any || pk.isEmpty() || !s.blocks().place(block, BlockStateRef.of(PlacedItemDevice.ID), true)) {
            if (s.blocks().stateAt(block).isPresent()) {
                s.blocks().remove(block);
            }
            for (ItemStack i : items) {
                if (!StackCodec.isEmpty(i)) {
                    s.effects().spawn(block, i);
                }
            }
            return;
        }
        restorePlaced(block, pk.get(), items, placed);
    }

    private void restorePlaced(Block block, DeviceKind<PlacedState> pk, List<ItemStack> items, PlacedState placed) {
        Optional<DeviceRecord<PlacedState>> stored = s.devices().edit(block, pk, (record, now) -> {
            DeviceInventory<PlacedState> inv = new DeviceInventory<>(pk, record, s.devices().stacks());
            for (int i = 0; i < PlacedRules.SLOTS; i++) {
                inv = inv.set(i, items.get(i));
            }
            return inv.record().withState(placed);
        });
        if (stored.isEmpty()) {
            for (ItemStack i : items) {
                if (!StackCodec.isEmpty(i)) {
                    s.effects().spawn(block, i);
                }
            }
            return;
        }
        s.contents().show(block, placed, items);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private Optional<PitKilnState> read(Block block) {
        return s.devices().inspect(block).flatMap(DeviceService.Inspection::record).map(DeviceRecord::state)
            .filter(PitKilnState.class::isInstance).map(PitKilnState.class::cast);
    }

    private void showContents(Block block, int stage, DeviceRecord<PitKilnState> record) {
        DeviceInventory<PitKilnState> inv = new DeviceInventory<>(kind, record, s.devices().stacks());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < PitKilnState.CONTENT_SLOTS; i++) {
            items.add(inv.get(i));
        }
        showContents(block, stage, record.state().placed(), items);
    }

    private void showContents(Block block, int stage, PlacedState placed, List<ItemStack> items) {
        if (PitKilnLayers.contentsVisible(stage)) {
            s.contents().show(block, placed, items);
        } else {
            s.contents().hide(block);
        }
    }

    private static ItemStack one(ItemStack held) {
        ItemStack one = held.clone();
        one.setAmount(1);
        return one;
    }

    /** {@code held.split(1)}; creative keeps the stack (vanilla restores the count after {@code useItemOn}). */
    private static void shrink(Player player, EquipmentSlot hand, ItemStack held) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, rest.getAmount() <= 0 ? null : rest);
    }
}
