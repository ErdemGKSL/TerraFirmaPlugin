/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.knap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.knap.KnappingDefinitions;
import dev.tfp.core.knap.KnappingIngredient;
import dev.tfp.core.knap.KnappingPattern;
import dev.tfp.core.knap.KnappingRecipe;
import dev.tfp.core.knap.KnappingResult;
import dev.tfp.core.knap.KnappingRules;
import dev.tfp.core.knap.KnappingScreen;
import dev.tfp.core.knap.KnappingType;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-PROC-01: opening, knapping, the output, taking it and the consumption rules, on MockBukkit. */
class KnappingMockBukkitTest extends MockServerTest {
    /** TFC's stone axe head pattern. */
    private static final List<String> AXE = List.of(" #   ", "#### ", "#####", "#### ", " #   ");
    private static final List<String> NOTCH = List.of(" ####", "#####", "#####", "#####", "#####");

    private static final KnappingType ROCK = new KnappingType("tfp:rock", new KnappingIngredient("minecraft:flint", false), 2, 1,
        false, false, true, "tfp:item.knapping.stone", "tfp:rock/loose/granite");
    private static final KnappingType CLAY = new KnappingType("tfp:clay", new KnappingIngredient("minecraft:clay_ball", false), 5, 5,
        true, true, false, "tfp:item.knapping.clay", "minecraft:clay_ball");
    private static final KnappingType LEATHER = new KnappingType("tfp:leather", new KnappingIngredient("minecraft:leather", false),
        1, 1, false, false, false, "tfp:item.knapping.leather", "minecraft:leather");

    private final AtomicLong now = new AtomicLong(100_000);
    private KnapScheduler scheduler;
    private PlayerMock player;
    private KnappingMenu.Services services;

    @BeforeEach
    void setUp() {
        scheduler = new KnapScheduler();
        GuiService gui = new GuiService(server, scheduler, now::get, true);
        KnappingDefinitions defs = new KnappingDefinitions(List.of(ROCK, CLAY, LEATHER), List.of(
            new KnappingRecipe("tfp:knapping/axe", "tfp:rock", KnappingPattern.of(AXE, Optional.empty()), null,
                new KnappingResult("minecraft:stone_axe", 1, Map.of())),
            new KnappingRecipe("tfp:knapping/brick", "tfp:clay", KnappingPattern.of(NOTCH, Optional.empty()), null,
                new KnappingResult("minecraft:brick", 2, Map.of())),
            new KnappingRecipe("tfp:knapping/boots", "tfp:leather", KnappingPattern.of(NOTCH, Optional.empty()), null,
                new KnappingResult("minecraft:leather_boots", 1, Map.of()))));
        services = new KnappingMenu.Services(gui, scheduler, Messages.fromJar(), () -> defs, () -> TagLookup.EMPTY,
            s -> s == null || s.getType().isAir() ? Optional.empty() : Optional.of(s.getType().getKey().toString()),
            t -> new ItemStack(Material.matchMaterial(t.id()), t.count()));
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new KnappingListener(services), plugin);
        player = server.addPlayer("Bot");
        player.getInventory().setHeldItemSlot(0);
    }

    private boolean rightClickAir() {
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, player.getInventory().getItemInMainHand(),
            null, BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        return GuiService.session(player.getOpenInventory().getTopInventory()) != null;
    }

    private GuiSession session() {
        return GuiService.session(player.getOpenInventory().getTopInventory());
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryType.SlotType slotType = raw < 54 ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent e = new InventoryClickEvent(player.getOpenInventory(), slotType, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    /** Clicks every cell that is off in {@code pattern}. */
    private void knap(List<String> pattern) {
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if (pattern.get(y).charAt(x) == ' ') {
                    click(KnappingScreen.cellSlot(KnappingRules.buttonIndex(x, y)), ClickType.LEFT, InventoryAction.PICKUP_ALL);
                }
            }
        }
    }

    /** Off cells hold nothing, on cells a button with a hidden tooltip (TFC's knapping buttons have no tooltip). */
    private void assertCellSlots(KnappingMenu menu) {
        Inventory top = session().getInventory();
        for (int cell = 0; cell < 25; cell++) {
            ItemStack item = top.getItem(KnappingScreen.cellSlot(cell));
            if (menu.rules().grid().get(cell)) {
                assertThat(item).as("on cell %d", cell).isNotNull();
                assertThat(item.getItemMeta().isHideTooltip()).as("on cell %d hides its tooltip", cell).isTrue();
            } else {
                assertThat(item == null || item.isEmpty()).as("off cell %d is empty", cell).isTrue();
            }
        }
    }

    private ItemStack output() {
        return session().getInventory().getItem(KnappingScreen.OUTPUT_SLOT);
    }

    @Test
    void tooFewRocksOrANonKnappableItemOpenNothing() {
        player.getInventory().setItem(0, new ItemStack(Material.FLINT, 1));
        assertThat(rightClickAir()).isFalse();
        player.getInventory().setItem(0, new ItemStack(Material.DIRT, 64));
        assertThat(rightClickAir()).isFalse();
    }

    @Test
    void knappingAnAxeHeadFromTwoRocks() {
        player.getInventory().setItem(0, new ItemStack(Material.FLINT, 2));
        assertThat(rightClickAir()).isTrue();
        Inventory top = session().getInventory();
        assertThat(top.getSize()).isEqualTo(54);
        KnappingMenu menu = KnappingMenu.of(session());
        assertThat(menu.texture()).isEqualTo("rock/loose/granite"); // flint has no texture: the type icon's
        assertCellSlots(menu);
        // the first click removes one rock (TFC: not creative, not consume-after-complete)
        click(KnappingScreen.cellSlot(0), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(1);
        assertThat(output()).isNull();
        // a right click or a click on an off cell does nothing
        click(KnappingScreen.cellSlot(0), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        click(KnappingScreen.cellSlot(2), ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        assertThat(menu.rules().grid().get(2)).isTrue();
        knap(AXE);
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(1);
        assertThat(output().getType()).isEqualTo(Material.STONE_AXE);
        assertCellSlots(menu);
        assertThat(java.util.stream.IntStream.range(0, 25)
            .filter(c -> top.getItem(KnappingScreen.cellSlot(c)) != null).count()).isEqualTo(15);
        // the title shows the cells that are left (background + the axe's 15 on cells)
        assertThat(session().glyphs().tokens().stream().filter(t -> t instanceof dev.tfp.core.gui.TitleGlyphs.Glyph).count())
            .isEqualTo(1 + 15);
        // take it: on the cursor, the grid resets to all off, nothing more is consumed
        InventoryClickEvent take = click(KnappingScreen.OUTPUT_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(take.isCancelled()).isTrue();
        assertThat(player.getItemOnCursor().getType()).isEqualTo(Material.STONE_AXE);
        assertThat(output()).isNull();
        assertThat(menu.rules().requiresReset()).isTrue();
        for (int i = 0; i < 25; i++) {
            assertThat(menu.rules().grid().get(i)).isFalse();
        }
        assertCellSlots(menu);
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(1);
    }

    @Test
    void shiftClickTakesIntoTheLastHotbarSlotFirst() {
        player.getInventory().setItem(0, new ItemStack(Material.FLINT, 2));
        rightClickAir();
        knap(AXE);
        click(KnappingScreen.OUTPUT_SLOT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(player.getInventory().getItem(8).getType()).isEqualTo(Material.STONE_AXE);
        assertThat(output()).isNull();
    }

    @Test
    void clayConsumesFiveWhenTheResultIsTakenAndTheWindowThenCloses() {
        player.getInventory().setItem(0, new ItemStack(Material.CLAY_BALL, 5));
        rightClickAir();
        knap(NOTCH);
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(5);
        assertThat(output().getType()).isEqualTo(Material.BRICK);
        // clay draws the disabled texture on the off cell
        assertThat(session().gui().frames(session())).containsEntry("cell0", 1).containsEntry("cell1", 0);
        click(KnappingScreen.OUTPUT_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(2);
        assertThat(player.getInventory().getItem(0)).isNull();
        GuiSession s = session();
        scheduler.runDelayed(); // TFC stillValid: the clay is gone, the window closes
        assertThat(s.closed()).isTrue();
    }

    @Test
    void closingWithAResultOnTheOutputGivesItAndConsumes() {
        player.getInventory().setItem(0, new ItemStack(Material.CLAY_BALL, 7));
        rightClickAir();
        knap(NOTCH);
        player.closeInventory();
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(2);
        assertThat(player.getInventory().contains(Material.BRICK, 2)).isTrue();
    }

    @Test
    void closingWithoutAResultConsumesNoClay() {
        player.getInventory().setItem(0, new ItemStack(Material.CLAY_BALL, 5));
        rightClickAir();
        click(KnappingScreen.cellSlot(7), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        player.closeInventory();
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(5);
    }

    @Test
    void leatherIsConsumedOnTheFirstClickAndTheWindowStaysOpen() {
        player.getInventory().setItem(0, new ItemStack(Material.LEATHER, 1));
        rightClickAir();
        click(KnappingScreen.cellSlot(0), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(player.getInventory().getItem(0)).isNull();
        scheduler.runDelayed();
        assertThat(session()).isNotNull();
        assertThat(output().getType()).isEqualTo(Material.LEATHER_BOOTS);
        // leather has no off texture: the off cell is hidden
        assertThat(session().gui().frames(session())).containsEntry("cell0", -1);
    }

    @Test
    void creativeKnapsWithoutConsumingOnTheFirstClick() {
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(Material.LEATHER, 1));
        rightClickAir();
        knap(NOTCH);
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(1);
        assertThat(output().getType()).isEqualTo(Material.LEATHER_BOOTS);
    }

    @Test
    void spectatorsCannotKnap() {
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().setItem(0, new ItemStack(Material.FLINT, 2));
        assertThat(rightClickAir()).isFalse();
    }

    @Test
    void theInputStackCannotBeMovedOrSwapped() {
        player.getInventory().setItem(0, new ItemStack(Material.FLINT, 2));
        rightClickAir();
        int heldRaw = 54 + 27; // hotbar slot 0 under a 54-slot screen
        assertThat(click(heldRaw, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
        InventoryClickEvent swap = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 60,
            ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(swap);
        assertThat(swap.isCancelled()).isTrue();
        assertThat(click(heldRaw + 1, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("another slot").isFalse();
    }
}
