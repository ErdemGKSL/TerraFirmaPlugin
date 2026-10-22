/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.SampleScreen;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-INFRA-03: click routing, shift-click predicates, buttons, the throttled title and closing, on MockBukkit. */
class GuiClickRoutingMockBukkitTest extends MockServerTest {
    private final AtomicLong now = new AtomicLong(100_000);
    private EntityScheduler scheduler;
    private GuiService service;
    private Messages messages;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        scheduler = new EntityScheduler();
        service = new GuiService(server, scheduler, now::get, true);
        messages = Messages.fromJar();
        server.getPluginManager().registerEvents(new GuiListener(service), plugin);
        player = server.addPlayer("Bot");
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        InventoryType.SlotType slotType = raw < 0 ? InventoryType.SlotType.OUTSIDE : InventoryType.SlotType.CONTAINER;
        InventoryClickEvent e = new InventoryClickEvent(view, slotType, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    /**
     * Raw slot of player inventory slot {@code index} (9..35 main, 0..8 hotbar) under a 27-slot screen. Items are put and
     * read through the view with the raw slot, so the test does not depend on how MockBukkit maps raw slots.
     */
    private static int rawPlayer(int index) {
        return index >= 9 ? 27 + index - 9 : 27 + 27 + index;
    }

    /** A 3-row screen with two stone-only inputs (slots 4, then 2), an output (5) and nothing else. */
    private TfpGui stoneOnly() {
        SlotPredicate stone = (s, slot, stack) -> stack.getType() == Material.STONE;
        return TfpGui.builder(SampleScreen.CHEST, SampleScreen.LAYERS)
            .label(p -> "Test")
            .input(4, stone)
            .input(2, stone)
            .output(5)
            .frames(s -> java.util.Map.of(SampleScreen.SEAL_LAYER, 1))
            .build();
    }

    @Test
    void theSampleOpensWithTheGlyphTitleTheBlankButtonAndNineInputs() {
        GuiSession session = service.open(player, SampleGui.create(service, messages));
        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getHolder()).isSameAs(session);
        assertThat(top.getSize()).isEqualTo(27);
        assertThat(session.legacyTitle()).isEqualTo("§f" + SampleScreen.glyphs(false).tags() + "§rSample storage");
        ItemStack button = top.getItem(SampleScreen.BUTTON_SLOT);
        assertThat(GuiButtons.isButton(button)).isTrue();
        // MockBukkit drops the item model when it copies a meta, so check the styling on the meta itself
        org.bukkit.inventory.meta.ItemMeta meta = new ItemStack(Material.PAPER).getItemMeta();
        GuiButtons.style(meta, true, "Seal");
        assertThat(meta.getItemModel()).isEqualTo(GuiButtons.BLANK);
        GuiButtons.style(meta = new ItemStack(Material.PAPER).getItemMeta(), false, "Seal");
        assertThat(meta.hasItemModel()).isFalse();
        for (int s : List.of(3, 4, 5, 12, 13, 14, 21, 22, 23)) {
            assertThat(session.gui().slot(s).kind()).isEqualTo(TfpGui.Kind.INPUT);
        }
        assertThat(session.gui().slot(0)).isNull();
    }

    @Test
    void withoutCraftEngineTheTitleIsTheLabelAndTheButtonIsVisible() {
        GuiService plain = new GuiService(server, scheduler, now::get, false);
        GuiSession session = plain.open(player, SampleGui.create(plain, messages));
        assertThat(session.legacyTitle()).isEqualTo("Sample storage");
        assertThat(GuiButtons.isButton(session.getInventory().getItem(SampleScreen.BUTTON_SLOT))).isTrue();
    }

    @Test
    void placingIsCheckedDecorationAndButtonsAreRefused() {
        GuiSession session = service.open(player, stoneOnly());
        player.setItemOnCursor(new ItemStack(Material.DIRT, 3));
        assertThat(click(4, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("dirt into a stone slot").isTrue();
        assertThat(click(0, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("decoration").isTrue();
        assertThat(click(5, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("output").isTrue();
        player.setItemOnCursor(new ItemStack(Material.STONE, 3));
        assertThat(click(4, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("stone").isFalse();
        player.setItemOnCursor(null);
        session.getInventory().setItem(5, new ItemStack(Material.IRON_INGOT));
        assertThat(click(5, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("taking an output").isFalse();
        assertThat(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
        assertThat(click(4, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled()).isTrue();
        assertThat(click(-999, ClickType.LEFT, InventoryAction.NOTHING).isCancelled()).as("outside").isFalse();
    }

    @Test
    void numberKeySwapsAreCheckedLikePlacing() {
        service.open(player, stoneOnly());
        player.getInventory().setItem(0, new ItemStack(Material.DIRT));
        InventoryClickEvent dirt = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 4,
            ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(dirt);
        assertThat(dirt.isCancelled()).isTrue();
        player.getInventory().setItem(1, new ItemStack(Material.STONE));
        InventoryClickEvent stone = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 4,
            ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 1);
        server.getPluginManager().callEvent(stone);
        assertThat(stone.isCancelled()).isFalse();
    }

    @Test
    void shiftClickFillsAcceptingInputsInOrderMergingFirst() {
        GuiSession session = service.open(player, stoneOnly());
        Inventory top = session.getInventory();
        top.setItem(2, new ItemStack(Material.STONE, 60));
        player.getOpenInventory().setItem(rawPlayer(9), new ItemStack(Material.DIRT, 10));
        InventoryClickEvent dirt = click(rawPlayer(9), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(dirt.isCancelled()).isTrue();
        assertThat(player.getOpenInventory().getItem(rawPlayer(9))).isEqualTo(new ItemStack(Material.DIRT, 10));
        assertThat(top.getItem(4)).isNull();

        player.getOpenInventory().setItem(rawPlayer(10), new ItemStack(Material.STONE, 64));
        click(rawPlayer(10), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        // merge into slot 2 (60 -> 64) first, then the first empty accepting input in order: slot 4 takes 60
        assertThat(top.getItem(2).getAmount()).isEqualTo(64);
        assertThat(top.getItem(4)).isEqualTo(new ItemStack(Material.STONE, 60));
        assertThat(player.getOpenInventory().getItem(rawPlayer(10))).isNull();
        assertThat(top.getItem(5)).as("never into the output").isNull();

        player.getOpenInventory().setItem(rawPlayer(11), new ItemStack(Material.STONE, 64));
        click(rawPlayer(11), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(top.getItem(4).getAmount()).isEqualTo(64);
        assertThat(player.getOpenInventory().getItem(rawPlayer(11))).as("what does not fit stays").isEqualTo(new ItemStack(Material.STONE, 60));
    }

    @Test
    void dragsOnlyCoverAcceptingInputs() {
        service.open(player, stoneOnly());
        InventoryView view = player.getOpenInventory();
        InventoryDragEvent onInputs = new InventoryDragEvent(view, null, new ItemStack(Material.STONE, 2), false,
            java.util.Map.of(2, new ItemStack(Material.STONE), 4, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(onInputs);
        assertThat(onInputs.isCancelled()).isFalse();
        InventoryDragEvent overDecor = new InventoryDragEvent(view, null, new ItemStack(Material.STONE, 2), false,
            java.util.Map.of(4, new ItemStack(Material.STONE), 7, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(overDecor);
        assertThat(overDecor.isCancelled()).isTrue();
        InventoryDragEvent wrongItem = new InventoryDragEvent(view, null, new ItemStack(Material.DIRT, 2), false,
            java.util.Map.of(4, new ItemStack(Material.DIRT), rawPlayer(9), new ItemStack(Material.DIRT)));
        server.getPluginManager().callEvent(wrongItem);
        assertThat(wrongItem.isCancelled()).isTrue();
    }

    @Test
    void theSealButtonRunsOnTheEntitySchedulerLocksTheSlotsAndThrottlesTheTitle() {
        GuiSession session = service.open(player, SampleGui.create(service, messages));
        session.getInventory().setItem(3, new ItemStack(Material.STONE, 5));
        now.addAndGet(1000);

        InventoryClickEvent press = click(SampleScreen.BUTTON_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(press.isCancelled()).isTrue();
        assertThat(scheduler.entityTasks).isEqualTo(1);
        assertThat(SampleGui.sealed(session)).isTrue();
        assertThat(session.titleSends()).as("long after the open: sent at once").isEqualTo(1);
        assertThat(player.getOpenInventory().getTitle()).contains("<image:tfp:gui/large_vessel_seal:0:0>");
        assertThat(session.getInventory().getItem(SampleScreen.BUTTON_SLOT).getItemMeta().customName())
            .isEqualTo(net.kyori.adventure.text.Component.text("Unseal")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        // sealed: the slots refuse clicks and shift-clicks (TFC LargeVesselContainer.clicked / moveStack)
        assertThat(click(3, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
        player.getOpenInventory().setItem(rawPlayer(9), new ItemStack(Material.STONE, 5));
        assertThat(click(rawPlayer(9), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled()).isTrue();
        assertThat(player.getOpenInventory().getItem(rawPlayer(9))).isEqualTo(new ItemStack(Material.STONE, 5));

        // two more presses inside 250 ms: one flush, with the latest state (unsealed, then sealed again)
        now.addAndGet(50);
        click(SampleScreen.BUTTON_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        now.addAndGet(50);
        click(SampleScreen.BUTTON_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(session.titleSends()).isEqualTo(1);
        assertThat(scheduler.delayed).hasSize(1);
        assertThat(scheduler.delayed.getFirst().ticks()).isEqualTo(4); // 150 ms left, rounded up to ticks
        now.addAndGet(150);
        scheduler.runDelayed();
        assertThat(session.titleSends()).as("the screen already shows 'sealed': nothing to send").isEqualTo(1);

        now.addAndGet(300);
        click(SampleScreen.BUTTON_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(session.titleSends()).isEqualTo(2);
        assertThat(click(3, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("unsealed again").isFalse();
    }

    @Test
    void closingGivesTheContentsBackAndTakesTheButtonsAway() {
        GuiSession session = service.open(player, SampleGui.create(service, messages));
        session.getInventory().setItem(13, new ItemStack(Material.STONE, 7));
        player.closeInventory();
        assertThat(session.closed()).isTrue();
        assertThat(player.getInventory().contains(Material.STONE, 7)).isTrue();
        assertThat(session.getInventory().getItem(13)).isNull();
        assertThat(session.getInventory().getItem(SampleScreen.BUTTON_SLOT)).isNull();
        assertThat(java.util.Arrays.stream(player.getInventory().getContents()).anyMatch(GuiButtons::isButton)).isFalse();
        session.refresh(); // a late refresh after closing does nothing
        assertThat(session.titleSends()).isZero();
    }
}
