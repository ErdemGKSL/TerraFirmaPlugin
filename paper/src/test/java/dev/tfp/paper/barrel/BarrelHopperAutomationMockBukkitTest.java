/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.TfcSideTables;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.gui.BarrelScreens;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.automation.DeviceAutomation;
import dev.tfp.paper.device.automation.HopperMover;
import dev.tfp.paper.device.automation.ViewerSync;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-66 on the real barrel: a fluid container a hopper puts into the barrel settles at once, and an open barrel
 * screen is committed before and reloaded after a hopper move (no dupe, no loss).
 */
class BarrelHopperAutomationMockBukkitTest extends MockServerTest {
    private static final String OAK = BarrelMockBukkitTest.OAK;

    @TempDir
    Path dir;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK);
    private final BarrelTestWorld.MemoryStore store = new BarrelTestWorld.MemoryStore();
    private final BarrelTestWorld.TableCodec stacks = new BarrelTestWorld.TableCodec();
    private final AtomicLong clock = new AtomicLong(10_000);
    private final BarrelTestScheduler scheduler = new BarrelTestScheduler();
    private BarrelTestWorld data;
    private DeviceService service;
    private DeviceKind<BarrelData> kind;
    private BarrelDevice.Services services;
    private BarrelScreenLink.Env env;
    private Block barrel;
    private DeviceAutomation.Spec spec;
    private HopperMover mover;

    @BeforeEach
    void setUp() {
        data = new BarrelTestWorld(dir).load(clock::get);
        World world = server.addSimpleWorld("barrel-hopper");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, data.items,
            Logger.getLogger("test"));
        BarrelRecipeBook book = new BarrelRecipeBook(data.recipes, data.tanks(), BarrelRules.DEFAULT_CAPACITY);
        services = new BarrelDevice.Services(book, s -> true, s -> false, new NoEffects());
        kind = service.register(BarrelDevice.kind(services, stacks));
        barrel = world.getBlockAt(0, 64, 0);
        blocks.place(barrel, BarrelDevice.state(OAK, false), true);
        store.data.put(FakeCustomBlocks.at(barrel), new dev.tfp.core.persist.DataMap());
        env = new BarrelScreenLink.Env(service, kind, services, stacks, scheduler, Messages.fromJar(),
            new GlobalClockHolder());
        mover = new HopperMover(service);
        spec = DeviceAutomation.Spec.of(BarrelDevice.ID, state -> TfcSideTables.barrel(Face.UP))
            .withSettle(BarrelDevice.hopperSettle(services, stacks));
    }

    private Inventory hopperWith(ItemStack stack) {
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        hopper.setItem(0, stack);
        return hopper;
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<BarrelData> record() {
        return kind.codec().decode(store.read(barrel).orElseThrow());
    }

    @Test
    void aWaterBucketAHopperPutsIntoTheFluidSlotIsEmptiedIntoTheTankAtOnce() {
        Inventory hopper = hopperWith(new ItemStack(Material.WATER_BUCKET));
        assertThat(mover.push(barrel, spec, TfcSideTables.barrel(Face.UP), Face.NORTH, hopper)).isTrue();
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        BarrelContents c = BarrelDevice.contents(record(), stacks);
        assertThat(c.containerIn().isEmpty()).as("the container left the input slot").isTrue();
        assertThat(c.containerOut().getType()).as("the empty bucket is in the output slot").isEqualTo(Material.BUCKET);
        assertThat(hopper.isEmpty()).isTrue();
    }

    @Test
    void withoutTheSettleTheContainerStaysInTheInputSlot() {
        DeviceAutomation.Spec plain = DeviceAutomation.Spec.of(BarrelDevice.ID, state -> TfcSideTables.barrel(Face.UP));
        Inventory hopper = hopperWith(new ItemStack(Material.WATER_BUCKET));
        assertThat(mover.push(barrel, plain, TfcSideTables.barrel(Face.UP), Face.NORTH, hopper)).isTrue();
        assertThat(record().state().fluid().isEmpty()).isTrue();
        assertThat(BarrelDevice.contents(record(), stacks).containerIn().getType()).isEqualTo(Material.WATER_BUCKET);
    }

    @Test
    void anOpenScreenIsCommittedBeforeAndReloadedAfterAHopperMove() {
        PlayerMock player = server.addPlayer("Bot");
        GuiService gui = new GuiService(server, scheduler, clock::get, true);
        BarrelScreens screens = new BarrelScreens(gui, scheduler, Messages.fromJar());
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        BarrelScreens.Opened opened = screens.open(player, link.key(), link, p -> "Oak Barrel");
        assertThat(opened.ok()).isTrue();
        GuiSession session = opened.session();
        int itemSlot = dev.tfp.paper.barrel.gui.BarrelScreenLayout.chestSlot(dev.tfp.paper.barrel.gui.BarrelScreenLayout.TFC_ITEM);
        // the viewer put five sugar into the screen; the click's commit has not run yet
        session.getInventory().setItem(itemSlot, new ItemStack(Material.SUGAR, 5));
        assertThat(BarrelDevice.contents(record(), stacks).mainEmpty()).isTrue();

        DeviceAutomation.ScreenSync sync = new ViewerSync(b -> screens.viewer(link.key()).map(GuiSession::viewer),
            b -> screens.edit(link.key()), b -> screens.reload(link.key()), scheduler);
        DeviceAutomation.Spec withScreen = spec.withScreens(sync);
        assertThat(sync.unsyncable(barrel)).as("the viewer is on this region").isFalse();

        Inventory hopper = hopperWith(new ItemStack(Material.SUGAR, 2));
        assertThat(mover.push(barrel, withScreen, TfcSideTables.barrel(Face.UP), Face.UP, hopper)).isTrue();

        assertThat(BarrelDevice.contents(record(), stacks).main().getAmount()).as("5 from the screen + 1 from the hopper")
            .isEqualTo(6);
        assertThat(hopper.getItem(0).getAmount()).isEqualTo(1);
        ItemStack shown = session.getInventory().getItem(itemSlot);
        assertThat(shown).as("the screen shows the barrel again").isNotNull();
        assertThat(shown.getAmount()).isEqualTo(6);
        assertThat(shown.getType()).isEqualTo(Material.SUGAR);
    }

    @Test
    void aViewerOnAnotherRegionMakesTheMoveWait() {
        PlayerMock player = server.addPlayer("Bot");
        GuiService gui = new GuiService(server, scheduler, clock::get, true);
        BarrelScreens screens = new BarrelScreens(gui, scheduler, Messages.fromJar());
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        assertThat(screens.open(player, link.key(), link, p -> "Oak Barrel").ok()).isTrue();
        DeviceAutomation.ScreenSync sync = new ViewerSync(b -> screens.viewer(link.key()).map(GuiSession::viewer),
            b -> screens.edit(link.key()), b -> screens.reload(link.key()), scheduler);
        scheduler.owner = false;
        assertThat(sync.unsyncable(barrel)).isTrue();
        assertThat(spec.withScreens(sync).waiting(barrel)).isTrue();
        scheduler.owner = true;
        assertThat(spec.withScreens(sync).waiting(barrel)).isFalse();
        assertThat(Optional.<Player>of(player)).isPresent();
    }

    /** No world effects. */
    private static final class NoEffects implements BarrelEffects {
        @Override
        public void sealed(Block block, dev.tfp.paper.integration.BlockStateRef wanted, boolean sealed) {
        }

        @Override
        public void recipe(Block block, String sound) {
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }
    }
}
