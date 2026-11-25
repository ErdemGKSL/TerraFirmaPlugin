/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sewing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.craft.CraftRecipes;
import dev.tfp.core.craft.SewingRecipe;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.sewing.SewingScreen;
import dev.tfp.core.sewing.SewingSession.ItemKind;
import dev.tfp.core.sewing.SewingSession.Mode;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

/** PAR-PROC-07: opening the table, sewing a recipe, taking the result and the item rules, on MockBukkit. */
class SewingMockBukkitTest extends MockServerTest {
    private final AtomicLong now = new AtomicLong(100_000);
    private SewScheduler scheduler;
    private PlayerMock player;
    private Block table;
    private SewingMenu.Services services;
    private CustomBlocks blocks;

    private static ItemKind kind(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return ItemKind.OTHER;
        }
        return switch (s.getType()) {
            case WHITE_WOOL -> ItemKind.WOOL;
            case BROWN_WOOL -> ItemKind.BURLAP;
            case STRING -> ItemKind.STRING;
            case SHEARS -> ItemKind.NEEDLE;
            default -> ItemKind.OTHER;
        };
    }

    @BeforeEach
    void setUp() {
        scheduler = new SewScheduler();
        World world = server.addSimpleWorld("sewing");
        table = world.getBlockAt(0, 64, 0);
        table.setType(Material.OAK_LOG);
        blocks = (CustomBlocks) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {CustomBlocks.class},
            (proxy, method, args) -> {
                if (method.getName().equals("stateAt")) {
                    Block b = (Block) args[0];
                    return b.getType() == Material.OAK_LOG ? Optional.of(BlockStateRef.of("tfp:wood/sewing_table/oak")) : Optional.empty();
                }
                throw new UnsupportedOperationException(method.getName());
            });
        GuiService gui = new GuiService(server, scheduler, now::get, true);
        List<SewingMenu.Entry> entries = new ArrayList<>();
        for (SewingRecipe r : CraftRecipes.SEWING) {
            entries.add(new SewingMenu.Entry(r, new ItemStackTemplate(r.result().item(), r.result().count(), Optional.empty())));
        }
        services = new SewingMenu.Services(gui, scheduler, Messages.fromJar(), () -> entries, SewingMockBukkitTest::kind,
            t -> new ItemStack(Material.matchMaterial(t.id()), t.count()), b -> SewingListener.isTable(blocks, b));
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new SewingListener(services, blocks), plugin);
        player = server.addPlayer("Bot");
        player.teleport(new Location(world, 0.5, 65, 2.5));
    }

    private boolean rightClickTable() {
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(),
            table, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        return session() != null;
    }

    private GuiSession session() {
        return GuiService.session(player.getOpenInventory().getTopInventory());
    }

    private Inventory top() {
        return session().getInventory();
    }

    private SewingMenu menu() {
        return SewingMenu.of(session());
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryType.SlotType slotType = raw < 54 ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent e = new InventoryClickEvent(player.getOpenInventory(), slotType, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private void press(int slot) {
        click(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private void supplies(int wool, int burlap, int yarn) {
        top().setItem(SewingScreen.SLOT_INPUT_1, wool == 0 ? null : new ItemStack(Material.WHITE_WOOL, wool));
        top().setItem(SewingScreen.SLOT_INPUT_2, burlap == 0 ? null : new ItemStack(Material.BROWN_WOOL, burlap));
        top().setItem(SewingScreen.SLOT_YARN, yarn == 0 ? null : new ItemStack(Material.STRING, yarn));
        top().setItem(SewingScreen.SLOT_TOOL, new ItemStack(Material.SHEARS));
    }

    private void sew(SewingRecipe recipe) {
        press(SewingScreen.BUTTON_BURLAP);
        for (int i = 0; i < 32; i++) {
            if (recipe.squares().charAt(i) == 'B') {
                press(SewingScreen.squareSlot(i));
            }
        }
        press(SewingScreen.BUTTON_WOOL);
        for (int i = 0; i < 32; i++) {
            if (recipe.squares().charAt(i) == 'W') {
                press(SewingScreen.squareSlot(i));
            }
        }
        press(SewingScreen.BUTTON_NEEDLE);
        for (int i = 0; i < 45; i++) {
            if (recipe.stitches().charAt(i) == '#') {
                press(SewingScreen.stitchSlot(i));
            }
        }
    }

    private static SewingRecipe recipe(String id) {
        return CraftRecipes.SEWING.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void rightClickingTheTableOpensTheSixRowScreenAndAnotherBlockDoesNot() {
        assertThat(rightClickTable()).isTrue();
        assertThat(top().getSize()).isEqualTo(54);
        player.closeInventory();
        table.getWorld().getBlockAt(5, 64, 5).setType(Material.DIRT);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null,
            table.getWorld().getBlockAt(5, 64, 5), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        assertThat(session()).isNull();
    }

    @Test
    void sewingTheGlobeBannerPatternGivesItAndTakingItConsumesTfcsAmounts() {
        SewingRecipe globe = recipe("sewing/globe_banner_pattern");
        rightClickTable();
        supplies(3, 4, 3);
        sew(globe);
        ItemStack out = top().getItem(SewingScreen.SLOT_RESULT);
        assertThat(out).isNotNull();
        assertThat(out.getType()).isEqualTo(Material.GLOBE_BANNER_PATTERN);
        long wool = globe.squares().chars().filter(c -> c == 'W').count();
        long burlap = globe.squares().chars().filter(c -> c == 'B').count();
        long stitches = globe.stitches().chars().filter(c -> c == '#').count();
        InventoryClickEvent take = click(SewingScreen.SLOT_RESULT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(take.isCancelled()).isTrue();
        assertThat(player.getItemOnCursor().getType()).isEqualTo(Material.GLOBE_BANNER_PATTERN);
        assertThat(top().getItem(SewingScreen.SLOT_RESULT)).isNull();
        assertThat(top().getItem(SewingScreen.SLOT_INPUT_1).getAmount()).isEqualTo(3 - (int) Math.ceil(wool / 8.0));
        assertThat(top().getItem(SewingScreen.SLOT_INPUT_2).getAmount()).isEqualTo(4 - (int) Math.ceil(burlap / 8.0));
        assertThat(top().getItem(SewingScreen.SLOT_YARN).getAmount()).isEqualTo(3 - (int) Math.ceil(stitches / 16.0));
        assertThat(menu().rules().board().squares()).isEqualTo(" ".repeat(32));
        assertThat(menu().rules().mode()).isEqualTo(Mode.NONE);
    }

    @Test
    void theGridIconsFollowTheViewAndTheBoard() {
        rightClickTable();
        supplies(1, 1, 1);
        assertThat(top().getItem(0).getType()).isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        assertThat(top().getItem(8)).isNull(); // column 8 holds no square
        press(SewingScreen.BUTTON_BURLAP);
        press(0);
        press(SewingScreen.BUTTON_WOOL);
        press(1);
        assertThat(top().getItem(0).getType()).isEqualTo(Material.BROWN_WOOL);
        assertThat(top().getItem(1).getType()).isEqualTo(Material.WHITE_WOOL);
        press(SewingScreen.BUTTON_NEEDLE);
        assertThat(top().getItem(8).getType()).isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        press(8);
        assertThat(top().getItem(8).getType()).isEqualTo(Material.STRING);
        assertThat(top().getItem(0).getType()).isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void clothIsLimitedToWhatTheSlotsHold() {
        rightClickTable();
        supplies(1, 0, 0);
        press(SewingScreen.BUTTON_WOOL);
        for (int i = 0; i < 10; i++) {
            press(SewingScreen.squareSlot(i));
        }
        assertThat(menu().rules().board().usedWool()).isEqualTo(8);
        assertThat(menu().rules().board().squares().chars().filter(c -> c == 'W').count()).isEqualTo(8);
    }

    @Test
    void usedClothAndYarnCannotBeTakenOutOfTheirSlots() {
        rightClickTable();
        supplies(1, 0, 1);
        press(SewingScreen.BUTTON_WOOL);
        press(0);
        InventoryClickEvent take = click(SewingScreen.SLOT_INPUT_1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(take.isCancelled()).isTrue();
        // the yarn is still free until a stitch is placed
        InventoryClickEvent yarn = click(SewingScreen.SLOT_YARN, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(yarn.isCancelled()).isFalse();
        top().setItem(SewingScreen.SLOT_YARN, new ItemStack(Material.STRING, 1));
        press(SewingScreen.BUTTON_NEEDLE);
        press(0);
        assertThat(click(SewingScreen.SLOT_YARN, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
        assertThat(click(SewingScreen.SLOT_TOOL, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
    }

    @Test
    void theSlotsOnlyTakeTheirOwnItems() {
        rightClickTable();
        var gui = session().gui();
        assertThat(gui.slot(SewingScreen.SLOT_YARN).accepts().accepts(session(), SewingScreen.SLOT_YARN, new ItemStack(Material.STRING))).isTrue();
        assertThat(gui.slot(SewingScreen.SLOT_YARN).accepts().accepts(session(), SewingScreen.SLOT_YARN, new ItemStack(Material.WHITE_WOOL))).isFalse();
        assertThat(gui.slot(SewingScreen.SLOT_TOOL).accepts().accepts(session(), SewingScreen.SLOT_TOOL, new ItemStack(Material.SHEARS))).isTrue();
        assertThat(gui.slot(SewingScreen.SLOT_TOOL).accepts().accepts(session(), SewingScreen.SLOT_TOOL, new ItemStack(Material.STRING))).isFalse();
        assertThat(gui.slot(SewingScreen.SLOT_INPUT_1).accepts().accepts(session(), SewingScreen.SLOT_INPUT_1, new ItemStack(Material.WHITE_WOOL))).isTrue();
        assertThat(gui.slot(SewingScreen.SLOT_INPUT_2).accepts().accepts(session(), SewingScreen.SLOT_INPUT_2, new ItemStack(Material.STRING))).isFalse();
        assertThat(gui.slot(SewingScreen.SLOT_INPUT_2).accepts().accepts(session(), SewingScreen.SLOT_INPUT_2, new ItemStack(Material.SHEARS))).isFalse();
    }

    @Test
    void closingReturnsTheItemsAndDiscardsTheBoard() {
        rightClickTable();
        supplies(1, 1, 1);
        press(SewingScreen.BUTTON_WOOL);
        press(0);
        GuiSession s = session();
        player.closeInventory();
        assertThat(s.closed()).isTrue();
        assertThat(player.getInventory().contains(Material.WHITE_WOOL)).isTrue();
        assertThat(player.getInventory().contains(Material.BROWN_WOOL)).isTrue();
        assertThat(player.getInventory().contains(Material.STRING)).isTrue();
        assertThat(player.getInventory().contains(Material.SHEARS)).isTrue();
    }

    @Test
    void anIncompleteBoardGivesNoResultAndATableThatIsGoneRefusesPresses() {
        rightClickTable();
        supplies(1, 1, 1);
        press(SewingScreen.BUTTON_WOOL);
        press(0);
        assertThat(top().getItem(SewingScreen.SLOT_RESULT)).isNull();
        table.setType(Material.AIR);
        press(1);
        assertThat(menu().rules().board().squares().charAt(1)).isEqualTo(' ');
        GuiSession s = session();
        scheduler.runDelayed();
        assertThat(s.closed()).isTrue();
    }

    @Test
    void takingTheResultWearsTheNeedleAndIsOneUse() {
        rightClickTable();
        supplies(3, 4, 3);
        ItemStack needle = new ItemStack(Material.SHEARS);
        top().setItem(SewingScreen.SLOT_TOOL, needle);
        sew(recipe("sewing/globe_banner_pattern"));
        click(SewingScreen.SLOT_RESULT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(player.getInventory().getItem(8).getType()).isEqualTo(Material.GLOBE_BANNER_PATTERN);
        var damageable = (org.bukkit.inventory.meta.Damageable) top().getItem(SewingScreen.SLOT_TOOL).getItemMeta();
        assertThat(damageable.getDamage()).isEqualTo(1);
    }

    @Test
    void spectatorsGetNoScreen() {
        player.setGameMode(GameMode.SPECTATOR);
        assertThat(rightClickTable()).isFalse();
    }
}
