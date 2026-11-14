/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-PROC-09: the barrel screen over a link: slots, tank, seal button, commits, reloads, one viewer, gone barrels. */
class BarrelScreensMockBukkitTest extends MockServerTest {
    private static final int IN = BarrelScreenLayout.chestSlot(BarrelScreenLayout.TFC_FLUID_IN);
    private static final int OUT = BarrelScreenLayout.chestSlot(BarrelScreenLayout.TFC_FLUID_OUT);
    private static final int ITEM = BarrelScreenLayout.chestSlot(BarrelScreenLayout.TFC_ITEM);

    private final AtomicLong now = new AtomicLong(100_000);
    private TickScheduler scheduler;
    private GuiService gui;
    private BarrelScreens screens;
    private PlayerMock player;

    /** A barrel in memory that records the calls; {@code gone} makes it answer empty. */
    static final class FakeLink implements BarrelLink {
        BarrelSnapshot barrel;
        boolean gone;
        final List<List<ItemStack>> commits = new ArrayList<>();
        int seals;
        int closed;
        /** What a commit turns the item slot into (an instant recipe), or null. */
        ItemStack instantResult;

        FakeLink(BarrelSnapshot barrel) {
            this.barrel = barrel;
        }

        @Override
        public Optional<BarrelSnapshot> snapshot() {
            return gone ? Optional.empty() : Optional.of(barrel);
        }

        @Override
        public Optional<BarrelSnapshot> commit(List<ItemStack> slots) {
            commits.add(slots);
            if (gone) {
                return Optional.empty();
            }
            List<ItemStack> next = new ArrayList<>(slots);
            if (instantResult != null && !next.get(BarrelScreenLayout.TFC_ITEM).isEmpty()) {
                next.set(BarrelScreenLayout.TFC_ITEM, instantResult.clone());
            }
            barrel = barrel.withSlots(next);
            return snapshot();
        }

        @Override
        public Optional<BarrelSnapshot> toggleSeal(org.bukkit.entity.Player viewer) {
            seals++;
            barrel = barrel.withSealed(!barrel.sealed());
            return snapshot();
        }

        @Override
        public boolean acceptsContainer(ItemStack stack) {
            return stack.getType() == Material.BUCKET || stack.getType() == Material.WATER_BUCKET;
        }

        @Override
        public boolean acceptsItem(ItemStack stack) {
            return stack.getType() != Material.CHEST;
        }

        @Override
        public List<String> sealedLines(org.bukkit.entity.Player viewer, BarrelSnapshot shown) {
            return List.of("Brining");
        }

        @Override
        public void closed(org.bukkit.entity.Player viewer) {
            closed++;
        }
    }

    @BeforeEach
    void setUp() {
        scheduler = new TickScheduler();
        gui = new GuiService(server, scheduler, now::get, true);
        screens = new BarrelScreens(gui, scheduler, Messages.fromJar());
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new BarrelGuiListener(screens), plugin);
        player = server.addPlayer("Bot");
    }

    private static BarrelSnapshot barrel(boolean sealed, int mb, ItemStack... slots) {
        List<ItemStack> s = new ArrayList<>(List.of(ItemStack.empty(), ItemStack.empty(), ItemStack.empty()));
        for (int i = 0; i < slots.length; i++) {
            s.set(i, slots[i]);
        }
        return new BarrelSnapshot(sealed, mb == 0 ? FluidAmount.EMPTY : new FluidAmount("minecraft:water", mb), 10_000, s);
    }

    private GuiSession open(FakeLink link) {
        BarrelScreens.Opened o = screens.open(player, "barrel@0,64,0", link, p -> "Oak Barrel");
        assertThat(o.ok()).isTrue();
        return o.session();
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    void opensWithTheBarrelsSlotsTheTankAndTheSealButton() {
        FakeLink link = new FakeLink(barrel(false, 5000, new ItemStack(Material.BUCKET), ItemStack.empty(),
            new ItemStack(Material.BEEF, 3)));
        GuiSession session = open(link);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getSize()).isEqualTo(27);
        assertThat(top.getItem(IN).getType()).isEqualTo(Material.BUCKET);
        assertThat(top.getItem(OUT)).isNull();
        assertThat(top.getItem(ITEM).getAmount()).isEqualTo(3);
        for (int slot : BarrelScreenLayout.TANK_SLOTS) {
            assertThat(GuiButtons.isButton(top.getItem(slot))).as("tank slot " + slot).isTrue();
        }
        assertThat(top.getItem(BarrelScreenLayout.SEAL_SLOT).getItemMeta().customName())
            .isEqualTo(net.kyori.adventure.text.Component.text("Seal")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        assertThat(session.glyphs().tags()).isEqualTo(BarrelScreenLayout.LAYERS.compose(BarrelScreenLayout.frames(false)).tags());
        assertThat(screens.openCount()).isEqualTo(1);
    }

    @Test
    void theTankItemCarriesTheFrameAndTheTint() {
        // MockBukkit drops item models and custom model data when it copies metas, so check the icon itself
        ItemStack icon = BarrelTank.icon(true, true, new FluidAmount("minecraft:water", 5000), 10_000, null);
        ItemMeta meta = icon.getItemMeta();
        assertThat(GuiButtons.isButton(icon)).isTrue();
        if (meta.hasCustomModelDataComponent()) {
            assertThat(meta.getCustomModelDataComponent().getFloats()).containsExactly(25f);
            assertThat(meta.getCustomModelDataComponent().getColors().getFirst().asRGB()).isEqualTo(0x3F76E4);
        }
        assertThat(meta.isHideTooltip()).as("an empty tank shows no tooltip").isTrue();
        ItemMeta plain = BarrelTank.icon(false, true, FluidAmount.EMPTY, 10_000, null).getItemMeta();
        assertThat(plain.hasItemModel()).as("no pack: a plain paper").isFalse();
    }

    @Test
    void aClickIsCommittedNextTickAndTheBarrelsAnswerIsShown() {
        FakeLink link = new FakeLink(barrel(false, 0));
        link.instantResult = new ItemStack(Material.LEATHER);
        GuiSession session = open(link);
        player.setItemOnCursor(new ItemStack(Material.BEEF, 2));
        InventoryClickEvent e = click(ITEM, ClickType.LEFT, InventoryAction.PLACE_ALL);
        assertThat(e.isCancelled()).isFalse();
        session.getInventory().setItem(ITEM, new ItemStack(Material.BEEF, 2)); // what the server applies after the event
        assertThat(link.commits).as("not before the click is applied").isEmpty();
        scheduler.runDelayed();
        assertThat(link.commits).hasSize(1);
        assertThat(link.commits.getFirst().get(BarrelScreenLayout.TFC_ITEM).getAmount()).isEqualTo(2);
        assertThat(session.getInventory().getItem(ITEM).getType()).as("the instant recipe's result").isEqualTo(Material.LEATHER);
        scheduler.runDelayed();
        assertThat(link.commits).as("nothing new to commit").hasSize(1);
    }

    @Test
    void slotsFollowTfcsPredicatesAndTheOutputSlotOnlyGivesBack() {
        FakeLink link = new FakeLink(barrel(false, 0));
        open(link);
        player.setItemOnCursor(new ItemStack(Material.CHEST));
        assertThat(click(ITEM, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("too big").isTrue();
        player.setItemOnCursor(new ItemStack(Material.STONE));
        assertThat(click(IN, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("not a container").isTrue();
        assertThat(click(OUT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).as("output").isTrue();
        assertThat(click(BarrelScreenLayout.TANK_SLOT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue();
        player.setItemOnCursor(new ItemStack(Material.BUCKET));
        assertThat(click(IN, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isFalse();
    }

    @Test
    void theSealButtonCommitsThenTogglesAndLocksTheSlots() {
        FakeLink link = new FakeLink(barrel(false, 3000));
        GuiSession session = open(link);
        session.getInventory().setItem(ITEM, new ItemStack(Material.BEEF, 4)); // placed, not yet committed
        click(BarrelScreenLayout.SEAL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(link.commits).as("the copy goes in before sealing").hasSize(1);
        assertThat(link.seals).isEqualTo(1);
        assertThat(BarrelScreens.shown(session).orElseThrow().sealed()).isTrue();
        assertThat(session.gui().locked(session)).isTrue();
        assertThat(session.glyphs().tags()).isEqualTo(BarrelScreenLayout.LAYERS.compose(BarrelScreenLayout.frames(true)).tags());
        ItemStack button = session.getInventory().getItem(BarrelScreenLayout.SEAL_SLOT);
        assertThat(button.getItemMeta().customName()).isEqualTo(net.kyori.adventure.text.Component.text("Unseal")
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        assertThat(button.getItemMeta().lore()).hasSize(1);
        player.setItemOnCursor(null);
        assertThat(click(ITEM, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("sealed").isTrue();
        click(BarrelScreenLayout.SEAL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(link.seals).isEqualTo(2);
        assertThat(session.gui().locked(session)).isFalse();
    }

    @Test
    void theScreenReloadsChangesFromOutside() {
        FakeLink link = new FakeLink(barrel(false, 1000));
        GuiSession session = open(link);
        link.barrel = link.barrel.withFluid(new FluidAmount("minecraft:water", 9000))
            .withSlots(List.of(ItemStack.empty(), new ItemStack(Material.WATER_BUCKET), ItemStack.empty()));
        scheduler.tick();
        assertThat(BarrelScreens.shown(session).orElseThrow().fluid().amount()).isEqualTo(9000);
        assertThat(session.getInventory().getItem(OUT).getType()).isEqualTo(Material.WATER_BUCKET);
    }

    @Test
    void oneViewerPerBarrelAndClosingCommitsAndFrees() {
        FakeLink link = new FakeLink(barrel(false, 0));
        GuiSession session = open(link);
        PlayerMock other = server.addPlayer("Other");
        BarrelScreens.Opened second = screens.open(other, "barrel@0,64,0", link, p -> "Oak Barrel");
        assertThat(second.ok()).isFalse();
        assertThat(second.refusal()).isEqualTo(BarrelScreens.Refusal.BUSY);
        session.getInventory().setItem(IN, new ItemStack(Material.BUCKET));
        player.closeInventory();
        assertThat(link.commits).hasSize(1);
        assertThat(link.barrel.slot(BarrelScreenLayout.TFC_FLUID_IN).getType()).isEqualTo(Material.BUCKET);
        assertThat(session.getInventory().getItem(IN)).as("the closed copy is emptied").isNull();
        assertThat(link.closed).isEqualTo(1);
        assertThat(scheduler.repeating.getFirst().isCancelled()).isTrue();
        assertThat(screens.openCount()).isZero();
        assertThat(screens.open(other, "barrel@0,64,0", link, p -> "Oak Barrel").ok()).isTrue();
    }

    @Test
    void aGoneBarrelClosesTheScreenAndReturnsOnlyWhatTheViewerAdded() {
        FakeLink link = new FakeLink(barrel(false, 0, ItemStack.empty(), ItemStack.empty(), new ItemStack(Material.BEEF)));
        GuiSession session = open(link);
        session.getInventory().setItem(IN, new ItemStack(Material.BUCKET)); // added, then the barrel breaks
        link.gone = true;
        scheduler.tick();
        assertThat(session.closed()).isTrue();
        assertThat(player.getInventory().contains(Material.BUCKET)).as("the viewer's own bucket comes back").isTrue();
        assertThat(player.getInventory().contains(Material.BEEF)).as("the barrel's beef dropped with the block").isFalse();
        assertThat(screens.openCount()).isZero();
        FakeLink broken = new FakeLink(barrel(false, 0));
        broken.gone = true;
        assertThat(screens.open(player, "other", broken, p -> "x").refusal()).isEqualTo(BarrelScreens.Refusal.GONE);
    }

    @Test
    void theDemoLinkGivesEverythingBackOnClose() {
        DemoBarrelLink demo = new DemoBarrelLink(new FluidAmount("minecraft:water", 5000), 10_000);
        BarrelScreens.Opened o = screens.open(player, new Object(), demo, p -> "Barrel");
        o.session().getInventory().setItem(IN, new ItemStack(Material.BUCKET));
        o.session().getInventory().setItem(ITEM, new ItemStack(Material.BEEF, 5));
        click(BarrelScreenLayout.SEAL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(player.getInventory().contains(Material.BUCKET)).as("sealing drops the containers").isTrue();
        assertThat(o.session().getInventory().getItem(IN)).isNull();
        player.closeInventory();
        assertThat(player.getInventory().containsAtLeast(new ItemStack(Material.BEEF), 5)).isTrue();
    }
}
