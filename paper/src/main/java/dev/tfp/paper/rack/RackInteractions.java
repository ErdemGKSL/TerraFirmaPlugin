/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java @ c3b4d9536 (useItemOn)
 * vanilla net.minecraft.world.level.block.ChiseledBookShelfBlock (useItemOn, useWithoutItem, addBook, removeBook)
 */
package dev.tfp.paper.rack;

import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.rack.BookshelfRules;
import dev.tfp.core.rack.RackState;
import dev.tfp.core.rack.ToolRackRules;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * PAR-DECO-05: a right click on a tool rack (TFC {@code ToolRackBlock.useItemOn}: the slot of the hit quadrant, a tool in, the
 * slot's tool out) and on a bookshelf (vanilla {@code ChiseledBookShelfBlock}: a book into, or out of, the slot of the front
 * face). The device framework's callbacks carry no hit position, so the click is read from the event, before other
 * listeners (as the placed items), and the record is changed through {@link DeviceService#edit}. The click is denied when
 * handled. Threads: the event's, which owns the clicked block (anything else is ignored).
 */
public final class RackInteractions implements Listener {
    private final DeviceService devices;
    private final DeviceKind<RackState> toolRack;
    private final DeviceKind<RackState> bookshelf;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Predicate<ItemStack> isTool;
    private final Predicate<ItemStack> isBook;
    private final BiConsumer<Block, BlockStateRef> writer;

    /**
     * @param isTool whether an item is on TFC's {@code usable_on_tool_rack} tag
     * @param isBook whether an item is on {@code minecraft:bookshelf_books}
     * @param writer replaces the state of a custom block of the same id without physics (the visuals' block data)
     */
    public RackInteractions(DeviceService devices, DeviceKind<RackState> toolRack, DeviceKind<RackState> bookshelf,
            CustomBlocks blocks, Scheduler scheduler, Predicate<ItemStack> isTool, Predicate<ItemStack> isBook,
            BiConsumer<Block, BlockStateRef> writer) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.toolRack = Objects.requireNonNull(toolRack, "toolRack");
        this.bookshelf = Objects.requireNonNull(bookshelf, "bookshelf");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.isTool = Objects.requireNonNull(isTool, "isTool");
        this.isBook = Objects.requireNonNull(isBook, "isBook");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        Vector relative = event.getClickedPosition();
        Player player = event.getPlayer();
        if (block == null || relative == null || player.getGameMode() == GameMode.SPECTATOR
                || !scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        if (click(player, block, side(event.getBlockFace()), relative)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /**
     * The click of {@code player}'s main hand on {@code block} at {@code relative} (0 to 1 inside the block) on {@code face}
     * (null for the top or the bottom). Region thread.
     *
     * @return whether the click was handled (the caller denies it)
     */
    public boolean click(Player player, Block block, ChestSide face, Vector relative) {
        Optional<RackBlocks.Found> found = blocks.stateAt(block).flatMap(RackBlocks::parse);
        if (found.isEmpty() || !(found.get().is(RackBlocks.TOOL_RACK) || found.get().is(RackBlocks.BOOKSHELF))) {
            return false;
        }
        // vanilla: a sneaking player with an item in either hand uses the item, not the block
        if (player.isSneaking() && (!StackCodec.isEmpty(player.getInventory().getItemInMainHand())
                || !StackCodec.isEmpty(player.getInventory().getItemInOffHand()))) {
            return false;
        }
        return found.get().is(RackBlocks.TOOL_RACK) ? toolRack(player, block, found.get(), relative)
            : bookshelf(player, block, found.get(), face, relative);
    }

    // ---- the tool rack --------------------------------------------------------------------------------------

    private boolean toolRack(Player player, Block block, RackBlocks.Found rack, Vector at) {
        int slot = ToolRackRules.slot(rack.facing(), at.getX(), at.getY(), at.getZ());
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean heldTool = !StackCodec.isEmpty(held) && isTool.test(held);
        AtomicReference<ToolRackRules.Click> click = new AtomicReference<>(ToolRackRules.Click.PASS);
        AtomicReference<ItemStack> extracted = new AtomicReference<>(ItemStack.empty());
        AtomicReference<List<ItemStack>> after = new AtomicReference<>(List.of());
        Optional<?> stored = devices.edit(block, toolRack, (record, now) -> {
            DeviceInventory<RackState> inv = new DeviceInventory<>(toolRack, record, devices.stacks());
            ItemStack current = inv.get(slot);
            ToolRackRules.Click c = ToolRackRules.click(StackCodec.isEmpty(current), heldTool);
            click.set(c);
            after.set(contents(inv, ToolRackRules.SLOTS));
            if (c == ToolRackRules.Click.PASS) {
                return record;
            }
            extracted.set(current);
            DeviceInventory<RackState> next = inv;
            if (c == ToolRackRules.Click.TAKE) {
                next = inv.set(slot, ItemStack.empty());
            } else {
                ItemStack one = held.clone();
                one.setAmount(1);
                next = inv.set(slot, one);
            }
            after.set(contents(next, ToolRackRules.SLOTS));
            return next.record();
        });
        if (stored.isEmpty() || click.get() == ToolRackRules.Click.PASS) {
            return false;
        }
        ToolRackRules.Click c = click.get();
        if (c == ToolRackRules.Click.INSERT || c == ToolRackRules.Click.SWAP) {
            // TFC: stack.split(1), also in creative
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
                player.getInventory().setItemInMainHand(held);
            } else {
                player.getInventory().setItemInMainHand(ItemStack.empty());
            }
            block.getWorld().playSound(center(block), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        }
        ItemStack out = extracted.get();
        if (!StackCodec.isEmpty(out)) {
            if (StackCodec.isEmpty(player.getInventory().getItemInMainHand())) {
                player.getInventory().setItemInMainHand(out);
            } else {
                give(player, out);
            }
        }
        ToolRackDisplays.sync(block, rack.facing(), after.get());
        player.swingMainHand();
        return true;
    }

    // ---- the bookshelf --------------------------------------------------------------------------------------

    private boolean bookshelf(Player player, Block block, RackBlocks.Found shelf, ChestSide face, Vector at) {
        OptionalInt hit = face == null ? OptionalInt.empty()
            : BookshelfRules.slot(shelf.facing(), face, at.getX(), at.getY(), at.getZ());
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean heldBook = !StackCodec.isEmpty(held) && isBook.test(held);
        if (hit.isEmpty()) {
            return BookshelfRules.click(false, heldBook, false) == BookshelfRules.Click.CONSUME;
        }
        int slot = hit.getAsInt();
        AtomicReference<BookshelfRules.Click> click = new AtomicReference<>(BookshelfRules.Click.PASS);
        AtomicReference<ItemStack> book = new AtomicReference<>(ItemStack.empty());
        AtomicReference<boolean[]> occupied = new AtomicReference<>(shelf.occupied());
        Optional<?> stored = devices.edit(block, bookshelf, (record, now) -> {
            DeviceInventory<RackState> inv = new DeviceInventory<>(bookshelf, record, devices.stacks());
            ItemStack current = inv.get(slot);
            BookshelfRules.Click c = BookshelfRules.click(true, heldBook, !StackCodec.isEmpty(current));
            click.set(c);
            DeviceInventory<RackState> next = inv;
            if (c == BookshelfRules.Click.ADD) {
                ItemStack one = held.clone();
                one.setAmount(1);
                book.set(one);
                next = inv.set(slot, one);
            } else if (c == BookshelfRules.Click.REMOVE) {
                book.set(current);
                next = inv.set(slot, ItemStack.empty());
            }
            occupied.set(occupancy(next));
            return next.record();
        });
        if (stored.isEmpty()) {
            return false;
        }
        BookshelfRules.Click c = click.get();
        if (c == BookshelfRules.Click.PASS) {
            return false;
        }
        if (c == BookshelfRules.Click.ADD) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                if (held.getAmount() > 1) {
                    held.setAmount(held.getAmount() - 1);
                    player.getInventory().setItemInMainHand(held);
                } else {
                    player.getInventory().setItemInMainHand(ItemStack.empty());
                }
            }
            sound(block, book.get(), true);
        } else if (c == BookshelfRules.Click.REMOVE) {
            give(player, book.get());
            sound(block, book.get(), false);
        }
        if (c != BookshelfRules.Click.CONSUME) {
            RackBlocks.Found next = shelf.withOccupied(occupied.get());
            if (!next.state().equals(shelf.state())) {
                writer.accept(block, next.state());
            }
            player.swingMainHand();
        }
        return true;
    }

    /** Vanilla's insert and pickup sounds; an enchanted book has its own. */
    private static void sound(Block block, ItemStack book, boolean insert) {
        boolean enchanted = book.getType() == Material.ENCHANTED_BOOK;
        Sound sound = insert ? (enchanted ? Sound.BLOCK_CHISELED_BOOKSHELF_INSERT_ENCHANTED : Sound.BLOCK_CHISELED_BOOKSHELF_INSERT)
            : (enchanted ? Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP_ENCHANTED : Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP);
        block.getWorld().playSound(center(block), sound, SoundCategory.BLOCKS, 1f, 1f);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    /** Which slots hold a book, in slot order. */
    boolean[] occupancy(DeviceInventory<RackState> inv) {
        boolean[] out = new boolean[BookshelfRules.SLOTS];
        for (int i = 0; i < out.length; i++) {
            out[i] = !StackCodec.isEmpty(inv.get(i));
        }
        return out;
    }

    private static List<ItemStack> contents(DeviceInventory<RackState> inv, int slots) {
        List<ItemStack> out = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            out.add(inv.get(i));
        }
        return out;
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest at the player's feet. */
    static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    private static org.bukkit.Location center(Block block) {
        return new org.bukkit.Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }

    /** The horizontal side of a block face, or null for up and down. */
    static ChestSide side(BlockFace face) {
        return switch (face) {
            case NORTH -> ChestSide.NORTH;
            case EAST -> ChestSide.EAST;
            case SOUTH -> ChestSide.SOUTH;
            case WEST -> ChestSide.WEST;
            default -> null;
        };
    }
}
