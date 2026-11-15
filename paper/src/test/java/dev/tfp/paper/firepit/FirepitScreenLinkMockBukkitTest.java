/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.firepit.gui.FirepitGuiListener;
import dev.tfp.paper.firepit.gui.FirepitScreenLayout;
import dev.tfp.paper.firepit.gui.FirepitScreens;
import dev.tfp.paper.firepit.gui.FirepitSnapshot;
import dev.tfp.paper.grill.TickScheduler;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-20: the firepit block opens the firepit screen ({@link FirepitModule#openLater} over a
 * {@link FirepitScreenLink}): right-click, the screen opens next tick on the viewer's thread; a log put into the top fuel
 * slot is in device slot 0 after the commit (TFC's cascade); a log clicked onto the block while the screen is open shows
 * in the screen ({@code ScreenSync}); the link answers empty from a thread that does not own the block (Folia) and once
 * the firepit is gone. Same fakes and stand-ins as {@link FirepitMockBukkitTest}.
 */
class FirepitScreenLinkMockBukkitTest extends MockServerTest {
    private static final int FUEL_TOP = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_INPUT);
    private static final int FUEL_BOTTOM = FirepitScreenLayout.chestSlot(FirepitSim.SLOT_FUEL_CONSUME);

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(FirepitDevice.ID);
    private final FirepitMockBukkitTest.MemoryStore store = new FirepitMockBukkitTest.MemoryStore();
    private final FirepitMockBukkitTest.TableCodec codec = new FirepitMockBukkitTest.TableCodec();
    private final FirepitMockBukkitTest.Effects effects = new FirepitMockBukkitTest.Effects();
    private final AtomicLong clock = new AtomicLong(FirepitMockBukkitTest.START);
    private final AtomicLong millis = new AtomicLong(100_000);
    private final TickScheduler scheduler = new TickScheduler();
    private DeviceService service;
    private FirepitDevice device;
    private FirepitScreens screens;
    private Block pit;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        effects.world = blocks;
        World world = server.addSimpleWorld("firepit");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, new FakeCustomItems(),
            Logger.getLogger("test"));
        GuiService gui = new GuiService(server, scheduler, millis::get, true);
        screens = new FirepitScreens(gui, scheduler, Messages.fromJar(), HeatDisplayStyle.COLOR);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new FirepitGuiListener(screens), plugin);
        FirepitDevice[] ref = new FirepitDevice[1];
        device = new FirepitDevice(new FirepitDevice.Services(FirepitMockBukkitTest::fuel,
            s -> s.getType() == Material.OAK_LOG || s.getType() == Material.STICK,
            s -> s.getType() == Material.BEEF ? 1.0 : 0.0,
            new FirepitDevice.Cooker() {
                @Override
                public float recipeTemperature(ItemStack stack) {
                    return Float.NaN;
                }

                @Override
                public Optional<ItemStack> convert(ItemStack heated, float before) {
                    return Optional.empty();
                }
            },
            s -> FirepitEffects.Douse.NONE, b -> false, b -> true, n -> new ItemStack(Material.GUNPOWDER, n),
            () -> () -> 0L, HeatConfig.DEFAULT, effects), codec,
            (p, b) -> FirepitModule.openLater(scheduler, () -> Optional.of(screens), blocks, service, ref[0],
                viewer -> "Firepit", p, b));
        ref[0] = device;
        service.register(device.kind());
        service.hook(FirepitDevice.ID, new FirepitModule.ScreenSync(() -> Optional.of(screens), scheduler));
        pit = world.getBlockAt(0, 64, 0);
        blocks.place(pit, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "x"),
            true);
        store.data.put(FakeCustomBlocks.at(pit), new DataMap());
        player = server.addPlayer("Bot");
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private DeviceRecord<FirepitData> record() {
        return device.kind().codec().decode(store.read(pit).orElseThrow());
    }

    private ItemStack slot(int i) {
        return codec.decode(record().slot(i));
    }

    private boolean use(ItemStack held) {
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(pit, player, EquipmentSlot.HAND, held);
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        InventoryType.SlotType st = raw < view.getTopInventory().getSize() ? InventoryType.SlotType.CONTAINER
            : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent e = new InventoryClickEvent(view, st, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private GuiSession openByRightClick() {
        assertThat(use(ItemStack.empty())).isTrue();
        assertThat(screens.viewer(FirepitScreenLink.Key.of(pit))).as("opened next tick, not inline").isEmpty();
        scheduler.runDelayed();
        GuiSession session = screens.viewer(FirepitScreenLink.Key.of(pit)).orElseThrow();
        assertThat(FirepitScreens.isFirepit(session)).isTrue();
        assertThat(player.getOpenInventory().getTopInventory()).isSameAs(session.getInventory());
        return session;
    }

    @Test
    void rightClickingAFirepitOpensItsScreen() {
        GuiSession session = openByRightClick();
        assertThat(session.getInventory().getSize()).isEqualTo(FirepitScreenLayout.CHEST.size());
        assertThat(FirepitScreens.shown(session).orElseThrow().temperature()).isZero();
        assertThat(screens.openCount()).isEqualTo(1);
    }

    @Test
    void aLogPutInTheTopSlotIsInDeviceSlotZeroAfterTheCommit() {
        GuiSession session = openByRightClick();
        player.setItemOnCursor(new ItemStack(Material.OAK_LOG, 3));
        click(FUEL_TOP, ClickType.LEFT, InventoryAction.PLACE_ALL); // the framework places one log (TFC's limit)
        assertThat(session.getInventory().getItem(FUEL_TOP).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(slot(FirepitSim.SLOT_FUEL_INPUT).isEmpty()).as("committed one tick later").isTrue();
        scheduler.runDelayed();
        assertThat(slot(FirepitSim.SLOT_FUEL_CONSUME).getType()).as("TFC cascadeFuelSlots").isEqualTo(Material.OAK_LOG);
        assertThat(slot(FirepitSim.SLOT_FUEL_INPUT).isEmpty()).isTrue();
        assertThat(session.getInventory().getItem(FUEL_BOTTOM).getType()).as("the screen shows the cascade")
            .isEqualTo(Material.OAK_LOG);
        assertThat(session.getInventory().getItem(FUEL_TOP)).isNull();
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(2);
    }

    @Test
    void aLogClickedOntoTheBlockShowsInTheOpenScreen() {
        GuiSession session = openByRightClick();
        PlayerMock other = server.addPlayer("Other");
        other.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.OAK_LOG));
        assertThat(service.use(pit, other, EquipmentSlot.HAND, new ItemStack(Material.OAK_LOG))).isTrue();
        assertThat(slot(FirepitSim.SLOT_FUEL_CONSUME).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(session.getInventory().getItem(FUEL_BOTTOM).getType()).as("ScreenSync reloads the screen")
            .isEqualTo(Material.OAK_LOG);
        // a second viewer: TFC's one-viewer-per-firepit divergence, the screen does not open for them
        other.getInventory().setItem(EquipmentSlot.HAND, null);
        service.use(pit, other, EquipmentSlot.HAND, ItemStack.empty());
        scheduler.runDelayed();
        assertThat(screens.viewer(FirepitScreenLink.Key.of(pit)).orElseThrow().viewer()).isSameAs(player);
    }

    @Test
    void theLinkCommitsCascadesAndAnswersEmptyOffItsRegionOrOnceGone() {
        FirepitScreenLink link = new FirepitScreenLink(service, device, pit, b -> true);
        List<ItemStack> slots = new ArrayList<>(FirepitSnapshot.emptySlots());
        slots.set(2, new ItemStack(Material.OAK_LOG));
        slots.set(3, new ItemStack(Material.STICK));
        slots.set(FirepitSim.SLOT_ITEM_INPUT, new ItemStack(Material.BEEF));
        FirepitSnapshot after = link.commit(slots).orElseThrow();
        assertThat(after.slot(0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(after.slot(1).getType()).isEqualTo(Material.STICK);
        assertThat(after.slot(2).isEmpty()).isTrue();
        assertThat(slot(FirepitSim.SLOT_ITEM_INPUT).getType()).isEqualTo(Material.BEEF);
        assertThat(link.isItemValid(FirepitSim.SLOT_FUEL_INPUT, new ItemStack(Material.OAK_LOG))).isTrue();
        assertThat(link.isItemValid(FirepitSim.SLOT_ITEM_INPUT, new ItemStack(Material.OAK_LOG))).isFalse();
        assertThat(link.isItemValid(FirepitSim.SLOT_ITEM_INPUT, new ItemStack(Material.BEEF))).isTrue();

        FirepitScreenLink foreign = new FirepitScreenLink(service, device, pit, b -> false);
        assertThat(foreign.snapshot()).as("another region (Folia)").isEmpty();
        assertThat(foreign.commit(FirepitSnapshot.emptySlots())).isEmpty();
        assertThat(slot(0).getType()).as("nothing written off-region").isEqualTo(Material.OAK_LOG);

        blocks.remove(pit);
        assertThat(link.snapshot()).as("gone").isEmpty();
    }

    @Test
    void theScreenClosesWhenTheFirepitIsGone() {
        GuiSession session = openByRightClick();
        blocks.remove(pit);
        scheduler.tick(); // the screen's periodic reload
        assertThat(session.closed()).isTrue();
        assertThat(screens.openCount()).isZero();
    }
}
