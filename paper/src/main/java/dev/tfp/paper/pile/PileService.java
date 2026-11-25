/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (doIngotPiling),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java @ c3b4d9536 (useItemOn, tick, canSurvive,
 *       onRemove, onDestroyedByPlayer),
 *       src/main/java/net/dries007/tfc/common/blockentities/IngotPileBlockEntity.java @ c3b4d9536 (addIngot, removeIngot,
 *       removeAllIngots)
 */
package dev.tfp.paper.pile;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.pile.AddDecision;
import dev.tfp.core.pile.IngotPileRules;
import dev.tfp.core.pile.PileCell;
import dev.tfp.core.pile.PileKind;
import dev.tfp.core.pile.PileVisual;
import dev.tfp.core.pile.RemoveDecision;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * The ingot piles of a world (PAR-METAL-16): reading a column as {@link PileCell}s, taking and adding ingots, placing a new
 * pile, and keeping the block state's {@code count}, the displays and the support in step with the record's slots. The
 * decisions are {@link IngotPileRules}; this class applies them. Every method runs on the region thread of the block it names
 * (a column is one chunk, so one region).
 *
 * <p>The slots are the truth: slot {@code i} holds ingot {@code i} (bottom to top), and the count of the block state is the
 * number of filled slots ({@link #sync}). A change of slots schedules a {@link #sync} one task later, so the state change never
 * runs inside the device callback that stores the record.
 */
public final class PileService {
    static final String COUNT = "count";
    /** TFC {@code Helpers.playPlaceSound} of {@code SoundType.METAL}: volume (1 + 1) / 2, pitch 1 x 0.8. */
    static final String PLACE_SOUND = "block.metal.place";
    /** A creative break mark older than this is stale (the break was cancelled afterwards). */
    static final long VOID_MARK_NANOS = 2_000_000_000L;
    /** How far above a pile the light for its displays is looked for (a stack of piles is at most 64 / 8 piles high per metre). */
    static final int LIGHT_CLIMB = 16;

    /** Writes a new state of a pile block that is already there (CraftEngine keeps the block entity: the record stays). */
    @FunctionalInterface
    interface StateWriter {
        boolean write(Block block, BlockStateRef state);
    }

    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Supplier<DeviceService> devices;
    private final Scheduler scheduler;
    private final StateWriter writer;
    private final StackCodec stacks;
    private final Map<PileKind, DeviceKind<Boolean>> kinds = new EnumMap<>(PileKind.class);
    private final Map<Long, Long> voided = new ConcurrentHashMap<>();
    private final java.util.function.LongSupplier nanos;

    PileService(CustomBlocks blocks, CustomItems items, Supplier<DeviceService> devices, Scheduler scheduler,
            StateWriter writer, StackCodec stacks, java.util.function.LongSupplier nanos) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.nanos = Objects.requireNonNull(nanos, "nanos");
    }

    void bind(PileKind kind, DeviceKind<Boolean> device) {
        kinds.put(kind, device);
    }

    DeviceKind<Boolean> device(PileKind kind) {
        return kinds.get(kind);
    }

    /** The block id of a pile kind. */
    static String id(PileKind kind) {
        return kind == PileKind.INGOT ? "tfp:ingot_pile" : "tfp:double_ingot_pile";
    }

    // ---- reading -------------------------------------------------------------------------------------

    /** The kind of pile {@code block} is, empty for any other block. */
    Optional<PileKind> kindAt(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        for (PileKind kind : PileKind.values()) {
            if (state.get().id().equals(id(kind))) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** The count of the pile block state (1 when the property is missing). */
    int countOf(Block block) {
        return blocks.stateAt(block).map(s -> parse(s.properties().get(COUNT))).orElse(1);
    }

    private static int parse(String value) {
        try {
            return value == null ? 1 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** {@code block} as a rules cell. Whatever is not air and not a pile is {@link PileCell#OTHER}. */
    PileCell cell(Block block) {
        Optional<PileKind> kind = kindAt(block);
        if (kind.isPresent()) {
            return new PileCell.Filled(kind.get(), Math.max(1, Math.min(kind.get().capacity(), countOf(block))));
        }
        return block.getType().isAir() && blocks.stateAt(block).isEmpty() ? PileCell.AIR : PileCell.OTHER;
    }

    /**
     * The cells above {@code block}, nearest first, up to and including the first cell that ends the column for the rules: any
     * cell that is not a pile of {@code kind}, or, when {@code stopAtNonFull}, a pile that is not full.
     */
    List<PileCell> above(Block block, PileKind kind, boolean stopAtNonFull) {
        List<PileCell> cells = new ArrayList<>();
        int top = block.getWorld().getMaxHeight();
        for (int y = block.getY() + 1; y < top; y++) {
            PileCell cell = cell(block.getWorld().getBlockAt(block.getX(), y, block.getZ()));
            cells.add(cell);
            boolean pile = cell instanceof PileCell.Filled f && f.kind() == kind;
            if (!pile || stopAtNonFull && ((PileCell.Filled) cell).count() < kind.capacity()) {
                break;
            }
        }
        return cells;
    }

    /** The kind of pile an ingot item makes ({@code c:ingots} and {@code c:double_ingots}), empty for any other item. */
    Optional<PileKind> kindOf(ItemStack stack, java.util.function.BiPredicate<ItemStack, String> tagged) {
        if (StackCodec.isEmpty(stack)) {
            return Optional.empty();
        }
        for (PileKind kind : PileKind.values()) {
            if (tagged.test(stack, kind.itemTag())) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    // ---- the record ----------------------------------------------------------------------------------

    /** The index of the last filled slot of {@code record}, or -1. */
    int top(DeviceRecord<Boolean> record) {
        for (int i = record.slots().size() - 1; i >= 0; i--) {
            if (!record.slot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    int filled(DeviceRecord<Boolean> record) {
        return top(record) + 1;
    }

    /** TFC {@code removeIngot}: the record without its last ingot, and that ingot. */
    record Taken(DeviceRecord<Boolean> record, ItemStack ingot) {
    }

    Taken take(DeviceRecord<Boolean> record) {
        int i = top(record);
        if (i < 0) {
            return new Taken(record, ItemStack.empty());
        }
        return new Taken(record.withSlot(i, ItemBlob.EMPTY), stacks.decode(record.slot(i)));
    }

    /** TFC {@code addIngot}: the record with {@code ingot} on top. */
    DeviceRecord<Boolean> add(DeviceRecord<Boolean> record, ItemStack ingot) {
        int next = filled(record);
        if (next >= record.slots().size()) {
            throw new IllegalStateException("pile is full");
        }
        ItemStack one = ingot.clone();
        one.setAmount(1);
        return record.withSlot(next, stacks.encode(one));
    }

    /** The metals of the ingots of {@code record}, bottom to top. */
    List<String> metals(DeviceRecord<Boolean> record) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < filled(record); i++) {
            ItemStack stack = stacks.decode(record.slot(i));
            out.add(StackCodec.isEmpty(stack) ? PileVisual.UNKNOWN : PileVisual.metalOf(items.idOf(stack).orElse(null)));
        }
        return out;
    }

    // ---- removing (a plain use) ----------------------------------------------------------------------

    /**
     * TFC {@code useItemOn} without sneaking: climbs the column of piles of {@code clicked}'s block, takes the top ingot of the
     * top pile and hands it to the player (not in creative). The block is changed one task later ({@link #sync}).
     *
     * @param record the caught-up record of {@code clicked}
     * @return the record to store for {@code clicked}, or null when the use is not for the pile
     */
    DeviceRecord<Boolean> remove(Block clicked, DeviceRecord<Boolean> record, Player player) {
        Optional<PileKind> kind = kindAt(clicked);
        if (kind.isEmpty()) {
            return null;
        }
        RemoveDecision d = IngotPileRules.decideRemove(kind.get(), player.isSneaking(), cell(clicked),
            above(clicked, kind.get(), false));
        if (d.action() != RemoveDecision.Action.REMOVE) {
            return null;
        }
        Block top = clicked.getRelative(0, d.depth(), 0);
        ItemStack ingot;
        DeviceRecord<Boolean> next = record;
        if (d.depth() == 0) {
            Taken taken = take(record);
            ingot = taken.ingot();
            next = taken.record();
        } else {
            ItemStack[] got = {ItemStack.empty()};
            Optional<DeviceRecord<Boolean>> edited = devices.get().edit(top, kinds.get(kind.get()), (r, now) -> {
                Taken taken = take(r);
                got[0] = taken.ingot();
                return taken.record();
            });
            if (edited.isEmpty()) {
                return null;
            }
            ingot = got[0];
        }
        if (player.getGameMode() != GameMode.CREATIVE && !StackCodec.isEmpty(ingot)) {
            give(player, ingot);
        }
        later(top, () -> sync(top));
        return next;
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest at the player's feet. */
    static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack.clone()).values()
            .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    // ---- adding (a sneak use of an ingot) --------------------------------------------------------------

    /** What an add did, for the listener. */
    enum Outcome {
        /** Not for the piles: the click goes on. */
        PASS,
        /** The use fails (TFC {@code InteractionResult.FAIL}): nothing happens and nothing else does. */
        FAIL,
        /** An ingot went into a pile or a new pile was placed. */
        DONE
    }

    /**
     * TFC {@code doIngotPiling}: a sneak use of an ingot of {@code kind} on {@code clicked}.
     *
     * @param face the clicked face, which decides where a new pile goes
     * @return what happened; the stack is shrunk here
     */
    Outcome add(Player player, EquipmentSlot hand, ItemStack held, PileKind kind, Block clicked, BlockFace face) {
        AddDecision d = IngotPileRules.decideAdd(kind, true, cell(clicked), above(clicked, kind, true));
        switch (d.action()) {
            case PASS:
                return Outcome.PASS;
            case FAIL:
                return Outcome.FAIL;
            case ADD_TO_PILE: {
                Block target = clicked.getRelative(0, d.depth(), 0);
                if (!insert(target, kind, held)) {
                    return Outcome.FAIL;
                }
                shrink(player, hand, held); // TFC stack.split(1): also in creative
                sound(target);
                return Outcome.DONE;
            }
            case PLACE_ON_TOP: {
                Block target = clicked.getRelative(0, d.depth(), 0);
                if (!place(target, kind, held)) {
                    return Outcome.FAIL;
                }
                consume(player, hand, held);
                sound(target);
                return Outcome.DONE;
            }
            case PLACE_BY_ITEM: {
                Block target = placeTarget(clicked, face);
                if (target == null || !canPlace(target, kind) || !place(target, kind, held)) {
                    return Outcome.FAIL;
                }
                consume(player, hand, held);
                sound(target);
                return Outcome.DONE;
            }
            default:
                return Outcome.PASS;
        }
    }

    /** TFC {@code BlockPlaceContext}: the clicked block when it can be replaced, else the block on the clicked face. */
    Block placeTarget(Block clicked, BlockFace face) {
        if (blocks.stateAt(clicked).isEmpty() && clicked.isReplaceable()) {
            return clicked;
        }
        return clicked.getRelative(face);
    }

    /** TFC {@code BlockItemPlacement.canPlace}: replaceable, a solid top or a pile of this kind below, nothing standing in it. */
    boolean canPlace(Block target, PileKind kind) {
        boolean free = blocks.stateAt(target).isEmpty() && (target.getType().isAir() || target.isReplaceable());
        return free && supported(target, kind) && unobstructed(target);
    }

    /** TFC {@code canSurvive}: the block below has a sturdy top, or is a pile of the same block. */
    boolean supported(Block block, PileKind kind) {
        Block below = block.getRelative(BlockFace.DOWN);
        Optional<PileKind> belowKind = kindAt(below);
        if (belowKind.isPresent()) {
            return belowKind.get() == kind;
        }
        return sturdyTop(below);
    }

    /** {@code isFaceSturdy(UP)}: a custom block reads as its carrier's shape; falls back to solidity where the API cannot tell. */
    static boolean sturdyTop(Block below) {
        try {
            return below.getBlockData().isFaceSturdy(BlockFace.UP, org.bukkit.block.BlockSupport.FULL);
        } catch (RuntimeException e) {
            return below.getType().isSolid();
        }
    }

    private static boolean unobstructed(Block target) {
        BoundingBox box = BoundingBox.of(target);
        return target.getWorld().getNearbyEntities(box, e -> e instanceof LivingEntity le && !le.isDead()
            && (!(le instanceof Player p) || p.getGameMode() != GameMode.SPECTATOR)).isEmpty();
    }

    /** One ingot into the existing pile at {@code target}. */
    private boolean insert(Block target, PileKind kind, ItemStack held) {
        Optional<DeviceRecord<Boolean>> r = devices.get().edit(target, kinds.get(kind), (record, now) ->
            filled(record) >= kind.capacity() ? record : add(record, held));
        if (r.isEmpty()) {
            return false;
        }
        later(target, () -> sync(target));
        return true;
    }

    /** A new pile of one ingot at {@code target}. */
    private boolean place(Block target, PileKind kind, ItemStack held) {
        if (!blocks.place(target, BlockStateRef.of(id(kind)).with(COUNT, "1"), true)) {
            return false;
        }
        Optional<DeviceRecord<Boolean>> r = devices.get().edit(target, kinds.get(kind), (record, now) -> add(record, held));
        if (r.isEmpty()) {
            blocks.remove(target);
            return false;
        }
        later(target, () -> sync(target));
        return true;
    }

    private static void shrink(Player player, EquipmentSlot hand, ItemStack held) {
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, rest.getAmount() <= 0 ? null : rest);
    }

    private static void consume(Player player, EquipmentSlot hand, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            shrink(player, hand, held);
        }
    }

    private static void sound(Block block) {
        block.getWorld().playSound(block.getLocation().toCenterLocation(), PLACE_SOUND, SoundCategory.BLOCKS, 1f, 0.8f);
    }

    /** The custom blocks of the world, for the module's debug fill. */
    CustomBlocks blocks() {
        return blocks;
    }

    /** Debug: stores {@code ingots} as the contents of the pile at {@code block} (the block state is {@link #sync}ed after). */
    Optional<DeviceRecord<Boolean>> fill(Block block, PileKind kind, List<ItemStack> ingots) {
        return devices.get().edit(block, kinds.get(kind), (record, now) -> {
            DeviceRecord<Boolean> next = record;
            for (int i = 0; i < record.slots().size(); i++) {
                next = next.withSlot(i, i < ingots.size() ? stacks.encode(ingots.get(i)) : ItemBlob.EMPTY);
            }
            return next;
        });
    }

    // ---- keeping the block in step with the slots -----------------------------------------------------

    /** Runs {@code task} on the region of {@code block}, after the current callback. */
    void later(Block block, Runnable task) {
        scheduler.region(block.getLocation(), task);
    }

    /**
     * Makes the block state's count, the displays and the existence of the pile match its slots: no ingot left removes the block
     * (TFC removes it with the last ingot); a count that differs from the filled slots is set (TFC
     * {@code setBlock(count - 1)} and {@code removeBlock}).
     */
    void sync(Block block) {
        Optional<PileKind> kind = kindAt(block);
        if (kind.isEmpty()) {
            return;
        }
        Optional<DeviceRecord<Boolean>> record = devices.get().edit(block, kinds.get(kind.get()), (r, now) -> r);
        if (record.isEmpty()) {
            return;
        }
        int filled = filled(record.get());
        if (filled == 0) {
            blocks.remove(block);
            PileDisplays.clear(block);
            return;
        }
        int count = Math.min(filled, kind.get().capacity());
        if (countOf(block) != count) {
            BlockStateRef next = blocks.stateAt(block).orElseThrow().with(COUNT, Integer.toString(count));
            writer.write(block, next);
        }
        List<PileVisual.Part> parts = PileVisual.plan(kind.get(), metals(record.get()));
        int[] light = light(block, kind.get());
        PileDisplays.sync(block, kind.get(), parts, light[0], light[1]);
    }

    /** The (block, sky) light above the column of piles the block is in: the displays stand inside a transparent carrier. */
    int[] light(Block block, PileKind kind) {
        Block above = block.getRelative(BlockFace.UP);
        for (int i = 0; i < LIGHT_CLIMB && kindAt(above).isPresent(); i++) {
            above = above.getRelative(BlockFace.UP);
        }
        try {
            return new int[] {Math.max(block.getLightFromBlocks(), above.getLightFromBlocks()), above.getLightFromSky()};
        } catch (RuntimeException e) {
            return new int[] {0, 15};
        }
    }

    // ---- support and removal --------------------------------------------------------------------------

    /** TFC {@code updateShape} then {@code tick}: a pile whose support went pops off, dropping its ingots. */
    void checkSupport(Block block) {
        Optional<PileKind> kind = kindAt(block);
        if (kind.isEmpty() || supported(block, kind.get())) {
            return;
        }
        later(block, () -> {
            Optional<PileKind> still = kindAt(block);
            if (still.isPresent() && !supported(block, still.get())) {
                blocks.remove(block);
            }
        });
    }

    /** A creative player is breaking {@code block}: its ingots are voided (TFC {@code onDestroyedByPlayer}). */
    void markVoid(Block block) {
        voided.put(packed(block), nanos.getAsLong());
    }

    private static long packed(Block block) {
        return ((long) block.getX() & 0x3FFFFFFL) << 38 | ((long) block.getZ() & 0x3FFFFFFL) << 12 | ((long) block.getY() & 0xFFFL);
    }

    /** TFC {@code onRemove}: every ingot is popped at the block, unless a creative player broke it. */
    void removed(Block block, List<ItemStack> contents) {
        Long mark = voided.remove(packed(block));
        boolean voidIt = mark != null && nanos.getAsLong() - mark < VOID_MARK_NANOS;
        if (!voidIt) {
            Location at = block.getLocation();
            for (ItemStack ingot : contents) {
                if (!StackCodec.isEmpty(ingot)) {
                    block.getWorld().dropItemNaturally(at, ingot);
                }
            }
        }
        later(block, () -> {
            if (kindAt(block).isEmpty()) {
                PileDisplays.clear(block);
            }
        });
    }
}
