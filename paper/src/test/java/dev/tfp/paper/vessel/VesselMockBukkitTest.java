/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.alloy.AlloyRange;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.Melt;
import dev.tfp.paper.alloy.AlloyPdc;
import dev.tfp.paper.alloy.AlloyTooltipContributor;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-POT-04 on MockBukkit: the vessel's insert size limits, the lazy melt into the alloy, the item data round trip, the
 * screen (open, commit, the vessel stays put) and the tooltip line. A decorated pot stands for the vessel, copper ingots for
 * a meltable item (1080 degrees, 100 mB each), an anvil for an item too large for the vessel.
 */
class VesselMockBukkitTest extends MockServerTest {
    static final String COPPER = "tfp:metal/copper";
    static final String TIN = "tfp:metal/tin";
    static final long START = 100_000;
    static final List<AlloyRecipe> BRONZE = List.of(new AlloyRecipe("tfp:metal/bronze",
            List.of(new AlloyRange(COPPER, 0.88, 0.92), new AlloyRange(TIN, 0.08, 0.12))));

    private final AtomicLong now = new AtomicLong(START);
    private VesselItems items;
    private Object savedCodec;
    private Field codecField;

    /** MockBukkit's item serializer cannot write nested PDC containers, so the slot codec keeps the stacks in memory. */
    @BeforeEach
    void memoryCodec() throws Exception {
        Class<?> iface = Class.forName("dev.tfp.paper.itemdata.ContentsPdc$StackCodec");
        codecField = Class.forName("dev.tfp.paper.itemdata.ContentsPdc").getDeclaredField("codec");
        codecField.setAccessible(true);
        savedCodec = codecField.get(null);
        List<ItemStack> store = new ArrayList<>();
        Object memory = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (proxy, method, args) -> {
            if (method.getName().equals("encode")) {
                store.add(((ItemStack) args[0]).clone());
                return new byte[] {(byte) (store.size() - 1)};
            }
            return store.get(((byte[]) args[0])[0]).clone();
        });
        codecField.set(null, memory);
        items = new VesselItems(new VesselItems.Services(now::get,
                stack -> Optional.ofNullable(switch (stack.getType()) {
                    case DECORATED_POT -> "tfp:ceramic/vessel";
                    case COPPER_INGOT -> "test:copper_ingot";
                    case IRON_INGOT -> "test:tin_ingot";
                    default -> null;
                }),
                id -> id.equals("test:copper_ingot") ? 1.0 : 0.0,
                stack -> stack.getType() == Material.COPPER_INGOT
                        ? Optional.of(new Melt(1080f, new FluidAmount(COPPER, 100), 0.008571429f, 1080f))
                        : stack.getType() == Material.IRON_INGOT
                        ? Optional.of(new Melt(230f, new FluidAmount(TIN, 100), 0.021428572f, 230f)) : Optional.empty(),
                stack -> stack.getType() != Material.ANVIL, () -> BRONZE), HeatConfig.DEFAULT);
    }

    @AfterEach
    void restoreCodec() throws Exception {
        codecField.set(null, savedCodec);
    }

    private final List<Runnable> delayed = new ArrayList<>();

    /** Entity tasks run at once (the test thread is the player's thread); delayed ones wait in {@link #delayed}. */
    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "entity" -> {
                        ((Runnable) args[1]).run();
                        yield true;
                    }
                    case "entityDelayed" -> {
                        delayed.add((Runnable) args[2]);
                        yield true;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ItemStack vessel() {
        return new ItemStack(Material.DECORATED_POT);
    }

    private void heat(ItemStack vessel, float temperature) {
        HeatPdc.write(vessel, new HeatState(temperature, now.get()));
    }

    @Test
    void insertObeysTheSlotCapacityTheSizeLimitAndTheSlotsItem() {
        ItemStack v = vessel();
        ItemStack rest = items.insert(v, 0, new ItemStack(Material.COPPER_INGOT, 64));
        assertThat(rest.getAmount()).as("slot capacity 16 of a 64 stack").isEqualTo(48);
        assertThat(items.slots(v).get(0).getAmount()).isEqualTo(16);

        ItemStack anvil = new ItemStack(Material.ANVIL, 1);
        assertThat(items.insert(v, 1, anvil).getAmount()).as("too large for a small vessel").isEqualTo(1);
        assertThat(items.slots(v).get(1).isEmpty()).isTrue();

        assertThat(items.insert(v, 0, new ItemStack(Material.STONE, 3)).getAmount()).as("another item in a used slot").isEqualTo(3);
        assertThat(items.insert(v, 1, new ItemStack(Material.STONE, 3)).isEmpty()).isTrue();
        assertThat(items.insert(v, 2, vessel()).getAmount()).as("no vessel in a vessel").isEqualTo(1);
    }

    @Test
    void aWarmVesselIsNoInventory() {
        ItemStack v = vessel();
        assertThat(items.isInventory(v)).isTrue();
        heat(v, 300f);
        assertThat(items.isInventory(v)).isFalse();
        assertThat(items.insert(v, 0, new ItemStack(Material.COPPER_INGOT, 2)).getAmount()).isEqualTo(2);
        assertThat(items.slots(v).get(0).isEmpty()).isTrue();
    }

    @Test
    void contentsMeltIntoTheAlloyAtTheirTemperature() {
        ItemStack v = vessel();
        assertThat(items.insert(v, 0, new ItemStack(Material.COPPER_INGOT, 3)).isEmpty()).isTrue();
        heat(v, 1000f);
        assertThat(items.settle(v)).as("1000 is below the 1080 recipe").isFalse();
        assertThat(items.slots(v).get(0).getAmount()).isEqualTo(3);

        heat(v, 1100f);
        assertThat(items.settle(v)).isTrue();
        assertThat(items.slots(v).get(0).isEmpty()).isTrue();
        assertThat(items.fluid(v)).isEqualTo(new FluidAmount(COPPER, 300));
        assertThat(items.isMolten(v)).as("above the copper melt point").isTrue();
        assertThat(items.isInventory(v)).isFalse();
        assertThat(v.getMaxStackSize()).as("a filled vessel never stacks").isEqualTo(1);
        assertThat(items.settle(v)).as("nothing left to melt").isFalse();
    }

    @Test
    void theAlloyAndHeatSurviveACopyAndCoolDownLazily() {
        ItemStack v = vessel();
        items.insert(v, 0, new ItemStack(Material.COPPER_INGOT, 1));
        heat(v, 1200f);
        items.settle(v);
        ItemStack copy = v.clone();
        assertThat(items.fluid(copy)).isEqualTo(new FluidAmount(COPPER, 100));
        assertThat(items.temperature(copy)).isBetween(1190f, 1200f);

        now.addAndGet(400_000);
        assertThat(items.temperature(copy)).as("cooled by the calendar, no ticking").isLessThan(1000f);
        assertThat(items.isMolten(copy)).isFalse();
        assertThat(items.alloyMeltTemperature(copy)).contains(1080f);
    }

    @Test
    void theAlloyCapsAtTheVesselCapacity() {
        ItemStack v = vessel();
        for (int slot = 0; slot < 4; slot++) {
            items.insert(v, slot, new ItemStack(Material.COPPER_INGOT, 16));
        }
        heat(v, 1500f);
        assertThat(items.settle(v)).isTrue();
        assertThat(items.fluid(v).amount()).as("4 x 16 x 100 mB, capped at 3000").isEqualTo(3000);
    }

    @Test
    void theScreenOpensCommitsAndKeepsTheVesselInItsSlot() {
        PlayerMock player = server.addPlayer("Bot");
        GuiService gui = new GuiService(server, scheduler(), System::currentTimeMillis, true);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        VesselScreens screens = new VesselScreens(gui, scheduler(),
                Messages.fromJar(), items);
        ItemStack v = vessel();
        player.getInventory().setItemInMainHand(v);

        GuiSession session = screens.use(player, EquipmentSlot.HAND);
        assertThat(session).isNotNull();
        assertThat(screens.isVesselScreen(session)).isTrue();
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(27);

        session.getInventory().setItem(VesselScreens.SLOTS[1], new ItemStack(Material.COPPER_INGOT, 20));
        session.getInventory().setItem(VesselScreens.SLOTS[2], new ItemStack(Material.ANVIL, 1));
        screens.commit(session, player, true);

        ItemStack held = player.getInventory().getItemInMainHand();
        assertThat(items.slots(held).get(1).getAmount()).isEqualTo(16);
        assertThat(items.slots(held).get(2).isEmpty()).as("the anvil goes back").isTrue();
        assertThat(player.getInventory().contains(Material.ANVIL)).isTrue();
        assertThat(player.getInventory().contains(Material.COPPER_INGOT)).as("the 4 over the slot capacity").isTrue();

        // the open vessel's slot cannot be clicked
        PlayerMock other = server.addPlayer("Other");
        other.getInventory().setItemInMainHand(vessel());
        GuiSession s2 = screens.use(other, EquipmentSlot.HAND);
        int raw = 27 + 27 + other.getInventory().getHeldItemSlot();
        InventoryClickEvent e = new InventoryClickEvent(other.getOpenInventory(), InventoryType.SlotType.QUICKBAR, raw,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
        assertThat(s2).isNotNull();
        assertThat(e.getRawSlot()).isEqualTo(raw);
    }

    @Test
    void aHotVesselOrOneWithAnAlloyDoesNotOpen() {
        PlayerMock player = server.addPlayer("Bot");
        GuiService gui = new GuiService(server, scheduler(), System::currentTimeMillis, true);
        VesselScreens screens = new VesselScreens(gui, scheduler(),
                Messages.fromJar(), items);
        ItemStack v = vessel();
        heat(v, 400f);
        player.getInventory().setItemInMainHand(v);
        assertThat(screens.use(player, EquipmentSlot.HAND)).isNull();
    }

    @Test
    void theTooltipShowsTheAlloy() {
        FluidLang lang = new FluidLang(file -> file.equals("en_us.json") ? new ByteArrayInputStream(
                "{\"fluid.tfc.metal.copper\": \"Copper\"}".getBytes(StandardCharsets.UTF_8)) : null);
        lang.load("en_us");
        VesselTooltipContributor c = new VesselTooltipContributor(lang);
        Map<String, Object> fluid = Map.of("tfp:v", 1, "tfp:id", COPPER, "tfp:amount", 100);
        List<Component> out = new ArrayList<>();
        c.contribute(new TooltipItem("tfp:ceramic/vessel", "minecraft:paper", 1, TooltipData.of(Map.of(FluidPdc.KEY, fluid))),
                new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertThat(out.stream().map(plain::serialize).toList()).containsExactly("Contents:", "Copper 100 mB");

        out.clear();
        c.contribute(new TooltipItem("tfp:ceramic/jug", "minecraft:paper", 1, TooltipData.of(Map.of(FluidPdc.KEY, fluid))),
                new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        c.contribute(new TooltipItem("tfp:ceramic/vessel", "minecraft:paper", 1, TooltipData.of(Map.of())),
                new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        assertThat(out).isEmpty();
    }

    @Test
    void copperAndTinMeltIntoABronzeMixAtTheBronzeHeat() {
        ItemStack v = vessel();
        items.insert(v, 0, new ItemStack(Material.COPPER_INGOT, 9));
        items.insert(v, 1, new ItemStack(Material.IRON_INGOT, 1));
        heat(v, 1100f);
        assertThat(items.settle(v)).isTrue();
        FluidAlloy mix = AlloyPdc.read(v);
        assertThat(mix.contents()).containsEntry(COPPER, 900d).containsEntry(TIN, 100d);
        assertThat(FluidPdc.content(v).isEmpty()).as("the single tfp:fluid is gone").isTrue();
        assertThat(items.fluid(v)).isEqualTo(new FluidAmount("tfp:metal/bronze", 1000));
        assertThat(items.alloyMeltTemperature(v)).contains(950f);
        assertThat(items.isMolten(v)).as("1100 is above the bronze melt point").isTrue();
        assertThat(items.state(v).alloy().specificHeat()).isEqualTo(0.008571429f);
        assertThat(v.getMaxStackSize()).isEqualTo(1);

        ItemStack copy = v.clone();
        assertThat(AlloyPdc.read(copy).contents()).containsEntry(TIN, 100d);
        heat(v, 900f);
        assertThat(items.isMolten(v)).as("below 950 the bronze is solid; copper alone would still be").isFalse();
    }

    @Test
    void aFillOfAnotherMetalAddsAMixAndStopsAtTheCapacity() {
        ItemStack v = vessel();
        assertThat(items.fill(v, COPPER, 900)).isEqualTo(900);
        assertThat(items.fill(v, TIN, 100)).isEqualTo(100);
        assertThat(items.fluid(v)).isEqualTo(new FluidAmount("tfp:metal/bronze", 1000));
        assertThat(items.fill(v, TIN, 5000)).as("room for 2000").isEqualTo(2000);
        assertThat(items.fill(v, TIN, 1)).as("full").isZero();
        assertThat(items.fill(v, "minecraft:water", 10)).as("no melt temperature").isZero();
        assertThat(items.fill(new ItemStack(Material.STONE), COPPER, 10)).isZero();
        assertThat(items.fluid(v).fluid()).as("copper 900, tin 2100: no recipe").isEqualTo("tfp:metal/unknown");
        assertThat(items.alloyMeltTemperature(v)).contains(400f);
    }

    @Test
    void aDrainTakesEachMetalInProportion() {
        ItemStack v = vessel();
        items.fill(v, COPPER, 900);
        items.fill(v, TIN, 100);
        assertThat(items.drain(v, 500)).isEqualTo(500);
        FluidAlloy mix = AlloyPdc.read(v);
        assertThat(mix.amount()).isEqualTo(500);
        assertThat(mix.contents().get(COPPER)).isEqualTo(450d);
        assertThat(mix.contents().get(TIN)).isEqualTo(50d);
        assertThat(items.drain(v, 9999)).isEqualTo(500);
        assertThat(items.fluid(v).isEmpty()).isTrue();
        assertThat(AlloyPdc.read(v).isEmpty()).isTrue();
    }

    @Test
    void anOldVesselWithOneFluidStillReadsAndIsRewrittenAsAnAlloy() {
        ItemStack v = vessel();
        FluidPdc.write(v, new FluidAmount(COPPER, 300));
        assertThat(items.fluid(v)).isEqualTo(new FluidAmount(COPPER, 300));
        assertThat(items.alloyMeltTemperature(v)).contains(1080f);
        assertThat(items.fill(v, TIN, 100)).isEqualTo(100);
        assertThat(FluidPdc.content(v).isEmpty()).isTrue();
        assertThat(AlloyPdc.read(v).contents()).containsEntry(COPPER, 300d).containsEntry(TIN, 100d);
    }

    @Test
    void theTooltipShowsTheMixOnceWithBothMetals() {
        FluidLang lang = new FluidLang(file -> file.equals("en_us.json") ? new ByteArrayInputStream(
                ("{\"fluid.tfc.metal.copper\": \"Copper\", \"fluid.tfc.metal.tin\": \"Tin\","
                        + " \"fluid.tfc.metal.bronze\": \"Bronze\"}").getBytes(StandardCharsets.UTF_8)) : null);
        lang.load("en_us");
        ItemStack v = vessel();
        items.fill(v, COPPER, 900);
        items.fill(v, TIN, 100);
        FluidAlloy mix = AlloyPdc.read(v);
        Map<String, Object> alloy = Map.of("tfp:v", 1, "tfp:amount", mix.amount(),
                "tfp:s", dev.tfp.core.alloy.AlloyCodec.encode(mix));
        TooltipItem item = new TooltipItem("tfp:ceramic/vessel", "minecraft:paper", 1, TooltipData.of(Map.of(AlloyPdc.KEY, alloy)));
        List<Component> out = new ArrayList<>();
        TooltipContext ctx = new TooltipContext("en_us", ClockSnapshot.UNSET);
        new VesselTooltipContributor(lang).contribute(item, ctx, out::add);
        assertThat(out).as("the vessel contributor defers").isEmpty();
        new AlloyTooltipContributor(lang, () -> BRONZE).contribute(item, ctx, out::add);
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertThat(out.stream().map(plain::serialize).toList()).containsExactly("Contents:", "Bronze 1000 mB",
                "  Copper 900 mB (90.0%)", "  Tin 100 mB (10.0%)");
    }

    @Test
    void vesselIdsAreTheFiredOnes() {
        assertThat(VesselItems.isVesselId("tfp:ceramic/vessel")).isTrue();
        assertThat(VesselItems.isVesselId("tfp:ceramic/red_glazed_vessel")).isTrue();
        assertThat(VesselItems.isVesselId("tfp:ceramic/unfired_vessel")).isFalse();
        assertThat(VesselItems.isVesselId("tfp:ceramic/unfired_red_glazed_vessel")).isFalse();
        assertThat(VesselItems.isVesselId("tfp:ceramic/jug")).isFalse();
    }
}
