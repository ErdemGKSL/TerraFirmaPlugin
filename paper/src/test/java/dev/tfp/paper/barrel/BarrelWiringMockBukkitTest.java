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
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.gui.BarrelGuiListener;
import dev.tfp.paper.barrel.gui.BarrelScreenLayout;
import dev.tfp.paper.barrel.gui.BarrelScreens;
import dev.tfp.paper.barrel.gui.BarrelSnapshot;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-35 on MockBukkit with TFC's generated barrel recipes: a right click opens the barrel screen over the device
 * record, the screen's slots reach the record (instant recipes on the real recipe book), the seal button, a second viewer
 * refused, the access hook committing and reloading an open screen around another player's sneak seal, the fluid IO slots,
 * the lazy rain fill, the sealed tooltip lines, the fluid surface, and the Folia refusal of a viewer on another region.
 */
class BarrelWiringMockBukkitTest extends MockServerTest {
    static final String OAK = "tfp:wood/barrel/oak";

    @TempDir
    Path dir;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK);
    private final BarrelTestWorld.MemoryStore store = new BarrelTestWorld.MemoryStore();
    private final BarrelTestWorld.TableCodec stacks = new BarrelTestWorld.TableCodec();
    private final AtomicLong clock = new AtomicLong(10_000);
    private final AtomicBoolean raining = new AtomicBoolean();
    private final List<FluidAmount> surfaces = new ArrayList<>();
    private final List<ItemStack> spawned = new ArrayList<>();
    private BarrelTestWorld data;
    private DeviceService service;
    private DeviceKind<BarrelData> kind;
    private BarrelTestScheduler scheduler;
    private BarrelScreens screens;
    private BarrelScreenLink.Env env;
    private Block barrel;
    private PlayerMock player;

    private final BarrelEffects effects = new BarrelEffects() {
        @Override
        public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
        }

        @Override
        public void recipe(Block block, String sound) {
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }

        @Override
        public void surface(Block block, FluidAmount fluid, int capacity) {
            surfaces.add(fluid);
        }
    };

    @BeforeEach
    void setUp() {
        data = new BarrelTestWorld(dir).load(clock::get);
        World world = server.addSimpleWorld("barrel");
        scheduler = new BarrelTestScheduler();
        GuiService gui = new GuiService(server, scheduler, clock::get, true);
        Messages messages = Messages.fromJar();
        screens = new BarrelScreens(gui, scheduler, messages);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new BarrelGuiListener(screens), plugin);
        ModuleServices services = new ModuleServices();
        services.provide(BarrelScreens.class, screens);

        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, data.items,
            Logger.getLogger("test"));
        BarrelRecipeBook book = new BarrelRecipeBook(data.recipes, data.tanks(), BarrelRules.DEFAULT_CAPACITY);
        BarrelScreenLink.Env[] holder = new BarrelScreenLink.Env[1];
        BarrelDevice.Services barrelServices = new BarrelDevice.Services(book, s -> s.getType() != Material.ANVIL,
            s -> false, effects, b -> raining.get(),
            (p, block, blockId) -> BarrelModule.open(holder[0], services, p, block, blockId));
        kind = service.register(BarrelDevice.kind(barrelServices, stacks));
        GlobalClockHolder calendar = new GlobalClockHolder();
        calendar.publish(10_000, 10_000, 8, true);
        env = new BarrelScreenLink.Env(service, kind, barrelServices, stacks, scheduler, messages, calendar);
        holder[0] = env;
        service.hook(BarrelDevice.ID, new BarrelModule.ScreenSync(services, scheduler));

        barrel = world.getBlockAt(0, 64, 0);
        blocks.place(barrel, BarrelDevice.state(OAK, false), true);
        store.data.put(FakeCustomBlocks.at(barrel), new DataMap());
        player = server.addPlayer("Bot");
    }

    private DeviceRecord<BarrelData> record() {
        DataMap m = store.read(barrel).orElseThrow();
        return m.size() == 0 ? kind.fresh() : kind.codec().decode(m);
    }

    private BarrelContents contents() {
        return BarrelDevice.contents(record(), stacks);
    }

    private boolean click(PlayerMock who, ItemStack held) {
        who.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(barrel, who, EquipmentSlot.HAND, held);
    }

    private GuiSession openByClick() {
        assertThat(click(player, new ItemStack(Material.BEEF))).isTrue();
        assertThat(screens.openCount()).as("the screen opens a tick later, after the click's record is stored").isZero();
        scheduler.runDelayed();
        Optional<GuiSession> s = screens.viewer(BarrelScreenLink.Key.of(barrel));
        assertThat(s).isPresent();
        return s.get();
    }

    private ItemStack meat(int count) {
        ItemStack s = new ItemStack(Material.BEEF, count);
        FoodPdc.write(s, new FoodState(clock.get(), List.of()));
        return s;
    }

    private static int slot(int tfc) {
        return BarrelScreenLayout.chestSlot(tfc);
    }

    @Test
    void aRightClickOpensTheScreenAndItsSlotsReachTheRecord() {
        GuiSession session = openByClick();
        assertThat(BarrelScreens.isBarrel(session)).isTrue();
        Inventory top = session.getInventory();
        top.setItem(slot(BarrelScreenLayout.TFC_ITEM), meat(3));
        screens.edit(BarrelScreenLink.Key.of(barrel)); // what the click listener does one tick after a click
        assertThat(contents().main().getAmount()).isEqualTo(3);
        assertThat(contents().main().getType()).isEqualTo(Material.BEEF);
    }

    @Test
    void anInstantRecipeFromTheScreenIsShownBack() {
        click(player, new ItemStack(Material.WATER_BUCKET));
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        GuiSession session = openByClick();
        session.getInventory().setItem(slot(BarrelScreenLayout.TFC_ITEM), data.items.create("tfp:powder/lime", 2).orElseThrow());
        screens.edit(BarrelScreenLink.Key.of(barrel));
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("tfp:limewater", 1000));
        assertThat(session.getInventory().getItem(slot(BarrelScreenLayout.TFC_ITEM))).as("the lime used, shown back").isNull();
        assertThat(surfaces).last().isEqualTo(new FluidAmount("tfp:limewater", 1000));
    }

    @Test
    void aSecondViewerIsRefused() {
        openByClick();
        PlayerMock other = server.addPlayer("Other");
        assertThat(click(other, new ItemStack(Material.BEEF))).isTrue();
        scheduler.runDelayed();
        assertThat(screens.openCount()).isEqualTo(1);
        assertThat(screens.viewer(BarrelScreenLink.Key.of(barrel)).orElseThrow().viewer()).isEqualTo(player);
    }

    @Test
    void anotherPlayersSneakSealCommitsTheOpenScreenFirstAndReloadsItAfter() {
        GuiSession session = openByClick();
        session.getInventory().setItem(slot(BarrelScreenLayout.TFC_ITEM), meat(2));
        session.getInventory().setItem(slot(BarrelScreenLayout.TFC_FLUID_IN), new ItemStack(Material.BUCKET));
        PlayerMock other = server.addPlayer("Other");
        other.setSneaking(true);
        assertThat(click(other, null)).isTrue();
        assertThat(record().state().sealed()).isTrue();
        assertThat(contents().main().getAmount()).as("the screen's uncommitted meat went in before the seal").isEqualTo(2);
        assertThat(spawned).as("onSeal drops the container slots, including the screen's bucket")
            .extracting(ItemStack::getType).containsExactly(Material.BUCKET);
        assertThat(BarrelScreens.shown(session).orElseThrow().sealed()).as("the screen reloaded after").isTrue();
        assertThat(session.getInventory().getItem(slot(BarrelScreenLayout.TFC_FLUID_IN))).isNull();
    }

    @Test
    void theSealButtonSealsAndTheTooltipNamesTheRecipeAndTheDate() {
        click(player, woodenBrine());
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        assertThat(link.commit(List.of(ItemStack.empty(), ItemStack.empty(), meat(2)))).isPresent();
        BarrelSnapshot sealed = link.toggleSeal(player).orElseThrow();
        assertThat(sealed.sealed()).isTrue();
        List<String> lines = link.sealedLines(player, sealed);
        assertThat(lines).hasSize(2);
        assertThat(lines.getFirst()).isEqualTo("Brined");
        assertThat(lines.get(1)).as("TFC getExactTimeAndDate of the seal tick").matches("\\d+:\\d\\d .+ \\d+, \\d{4}");
        assertThat(surfaces).last().as("a sealed barrel hides its surface").isEqualTo(FluidAmount.EMPTY);
        clock.addAndGet(4000);
        BarrelSnapshot later = link.snapshot().orElseThrow();
        assertThat(traits(later.slot(BarrelScreenLayout.TFC_ITEM))).contains(FoodTrait.BRINED);
    }

    private ItemStack woodenBrine() {
        ItemStack s = data.items.create("tfp:wooden_bucket", 1).orElseThrow();
        dev.tfp.paper.itemdata.ItemFluids.fill(s, new FluidAmount("tfp:brine", 1000), 1000, true);
        return s;
    }

    private static List<FoodTrait> traits(ItemStack stack) {
        return FoodPdc.read(stack).orElseThrow().traits();
    }

    @Test
    void aBucketInTheFluidInSlotEmptiesIntoTheTankAndComesOutEmpty() {
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        BarrelSnapshot after = link.commit(List.of(new ItemStack(Material.WATER_BUCKET), ItemStack.empty(),
            ItemStack.empty())).orElseThrow();
        assertThat(after.fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(after.slot(BarrelScreenLayout.TFC_FLUID_IN).isEmpty()).isTrue();
        assertThat(after.slot(BarrelScreenLayout.TFC_FLUID_OUT).getType()).isEqualTo(Material.BUCKET);
        // a stack of empty buckets fills one, which goes out; the rest stays in
        ItemStack two = new ItemStack(Material.BUCKET, 2);
        after = link.commit(List.of(two, ItemStack.empty(), ItemStack.empty())).orElseThrow();
        assertThat(after.fluid().isEmpty()).isTrue();
        assertThat(after.slot(BarrelScreenLayout.TFC_FLUID_IN).getAmount()).isEqualTo(1);
        assertThat(after.slot(BarrelScreenLayout.TFC_FLUID_OUT).getType()).isEqualTo(Material.WATER_BUCKET);
        // the fluid-out slot is full: nothing more moves
        after = link.snapshot().orElseThrow();
        assertThat(after.slot(BarrelScreenLayout.TFC_FLUID_IN).getAmount()).isEqualTo(1);
    }

    @Test
    void rainFillsAnOpenUprightBarrelLazilyBetweenTwoRainyAccesses() {
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        raining.set(true);
        assertThat(link.snapshot().orElseThrow().fluid().isEmpty()).as("the first access starts the rain clock").isTrue();
        clock.addAndGet(400);
        assertThat(link.snapshot().orElseThrow().fluid()).as("400 ticks of rain: 100 mB").isEqualTo(
            new FluidAmount("minecraft:water", 100));
        raining.set(false);
        clock.addAndGet(400);
        assertThat(link.snapshot().orElseThrow().fluid().amount()).as("dry now: the interval is not counted").isEqualTo(100);
        raining.set(true);
        clock.addAndGet(400);
        assertThat(link.snapshot().orElseThrow().fluid().amount()).as("dry at its start").isEqualTo(100);
        clock.addAndGet(40);
        assertThat(click(player, new ItemStack(Material.BEEF))).isTrue();
        assertThat(record().state().fluid().amount()).as("a click settles the rain too").isEqualTo(110);
        link.toggleSeal(player);
        clock.addAndGet(400);
        assertThat(link.snapshot().orElseThrow().fluid().amount()).as("sealed: no rain").isEqualTo(110);
    }

    @Test
    void aViewerOnAnotherRegionIsRefusedAndClosed() {
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        scheduler.owner = false;
        assertThat(link.snapshot()).isEmpty();
        assertThat(link.commit(List.of(ItemStack.empty(), ItemStack.empty(), meat(1)))).isEmpty();
        assertThat(link.toggleSeal(player)).isEmpty();
        assertThat(contents().mainEmpty()).isTrue();
    }

    @Test
    void theScreenAcceptsContainersAndItemsByTfcsSlotRules() {
        BarrelScreenLink link = new BarrelScreenLink(env, barrel, OAK);
        assertThat(link.acceptsContainer(new ItemStack(Material.BUCKET))).isTrue();
        assertThat(link.acceptsContainer(new ItemStack(Material.BEEF))).isFalse();
        assertThat(link.acceptsItem(new ItemStack(Material.BEEF))).isTrue();
        assertThat(link.acceptsItem(new ItemStack(Material.ANVIL))).as("huge and very heavy (here: the fits rule)").isFalse();
    }

    @Test
    void breakingTheBarrelClosesItsScreenAndHidesTheSurface() {
        click(player, new ItemStack(Material.WATER_BUCKET));
        GuiSession session = openByClick();
        DataMap last = store.read(barrel).orElseThrow();
        blocks.remove(barrel);
        store.data.remove(FakeCustomBlocks.at(barrel));
        service.removed(barrel.getWorld(), 0, 64, 0, OAK, last);
        assertThat(surfaces).last().isEqualTo(FluidAmount.EMPTY);
        assertThat(session.closed() || screens.viewer(BarrelScreenLink.Key.of(barrel)).isEmpty()).isTrue();
    }
}
