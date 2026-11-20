/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.mold;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.MoldKind;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.vessel.VesselItems;
import java.io.ByteArrayInputStream;
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
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-POT-05 on MockBukkit: the pour from a molten vessel into a mold (step, at once, screen), the lazy solidify, the cast
 * by sneak-use with its break roll, the tooltip. A decorated pot stands for the vessel, a flower pot for the axe head mold.
 */
class MoldMockBukkitTest extends MockServerTest {
    static final String COPPER = "tfp:metal/copper";
    static final long START = 100_000;

    private final AtomicLong now = new AtomicLong(START);
    private final List<Runnable> repeating = new ArrayList<>();
    private double draw = 0.5;
    private float breakChance = 0.5f;
    private VesselItems vessels;
    private MoldItems molds;
    private MoldPourer pourer;

    private String id(ItemStack stack) {
        return switch (stack.getType()) {
            case DECORATED_POT -> "tfp:ceramic/vessel";
            case FLOWER_POT -> "tfp:ceramic/axe_head_mold";
            case BRICK -> "tfp:ceramic/unfired_axe_head_mold";
            default -> null;
        };
    }

    @BeforeEach
    void setUp() {
        vessels = new VesselItems(new VesselItems.Services(now::get, s -> Optional.ofNullable(id(s)), id -> 0.0,
            s -> Optional.empty(), s -> true), HeatConfig.DEFAULT);
        ItemHeatHooks heat = new ItemHeatHooks(now::get, HeatConfig.DEFAULT, stack -> 1.2f);
        molds = new MoldItems(new MoldItems.Services(now::get, s -> Optional.ofNullable(id(s)), (f, tag) -> f.startsWith("tfp:metal/"),
            heat, mold -> molds().content(mold).amount() >= 100
                ? Optional.of(new MoldItems.Cast(new ItemStack(Material.IRON_AXE), breakChance)) : Optional.empty()));
        pourer = new MoldPourer(vessels, molds);
    }

    private MoldItems molds() {
        return molds;
    }

    private ItemStack vessel(int copper, float temperature) {
        ItemStack v = new ItemStack(Material.DECORATED_POT);
        FluidPdc.write(v, new FluidAmount(COPPER, copper));
        HeatPdc.write(v, new HeatState(temperature, now.get()));
        return v;
    }

    private ItemStack mold() {
        return new ItemStack(Material.FLOWER_POT);
    }

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "entityRepeating" -> {
                    repeating.add((Runnable) args[3]);
                    yield new Scheduler.TaskHandle() {
                        @Override
                        public void cancel() {
                            repeating.remove(args[3]);
                        }

                        @Override
                        public boolean isCancelled() {
                            return !repeating.contains(args[3]);
                        }
                    };
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    @Test
    void firedMoldIdsAreTheirKinds() {
        assertThat(MoldItems.kindOf("tfp:ceramic/axe_head_mold")).contains(MoldKind.AXE_HEAD);
        assertThat(MoldItems.kindOf("tfp:ceramic/fire_ingot_mold")).contains(MoldKind.FIRE_INGOT);
        assertThat(MoldItems.kindOf("tfp:ceramic/unfired_axe_head_mold")).isEmpty();
        assertThat(MoldItems.kindOf("tfp:ceramic/unfired_mold_table")).isEmpty();
        assertThat(MoldItems.kindOf("tfp:ceramic/jug")).isEmpty();
    }

    @Test
    void aMoltenVesselPoursIntoTheMoldUpToItsCapacityAndMixesTheHeat() {
        ItemStack v = vessel(500, 1200f);
        ItemStack m = mold();
        int poured = pourer.pour(v, m, Integer.MAX_VALUE);
        assertThat(poured).as("the axe head mold holds 100 mB").isEqualTo(100);
        assertThat(vessels.fluid(v)).isEqualTo(new FluidAmount(COPPER, 400));
        assertThat(molds.content(m)).isEqualTo(new FluidAmount(COPPER, 100));
        assertThat(m.getMaxStackSize()).as("a filled mold never stacks").isEqualTo(1);
        assertThat(molds.temperature(m)).as("the cold mold heats towards the metal").isBetween(100f, 1200f);
        assertThat(molds.isMolten(m)).isEqualTo(molds.temperature(m) >= 1080f);
        assertThat(vessels.temperature(v)).as("the vessel keeps its temperature").isBetween(1190f, 1200f);
    }

    @Test
    void aColdVesselADifferentFluidAndAnUnfiredMoldTakeNothing() {
        assertThat(pourer.pour(vessel(500, 500f), mold(), 100)).as("below 1080").isZero();
        ItemStack bronze = vessel(500, 1200f);
        FluidPdc.write(bronze, new FluidAmount("tfp:metal/bronze", 500));
        ItemStack m = mold();
        assertThat(pourer.pour(vessel(500, 1200f), m, 60)).isEqualTo(60);
        assertThat(pourer.pour(bronze, m, 10)).as("another metal does not mix in").isZero();
        assertThat(pourer.pour(vessel(500, 1200f), new ItemStack(Material.BRICK), 10)).as("unfired").isZero();
        assertThat(pourer.pour(vessel(500, 1200f), new ItemStack(Material.FLOWER_POT, 2), 10)).as("one mold only").isZero();
    }

    @Test
    void theMoldSolidifiesByCoolingWithoutTicking() {
        ItemStack v = vessel(500, 1500f);
        ItemStack m = mold();
        pourer.pour(v, m, 100);
        HeatPdc.write(m, new HeatState(1200f, now.get()));
        assertThat(molds.isMolten(m)).isTrue();
        now.addAndGet(100_000);
        assertThat(molds.isMolten(m)).as("cooled by the calendar").isFalse();
        assertThat(molds.content(m).amount()).as("the content stays as a casting").isEqualTo(100);
    }

    @Test
    void sneakUseOnACooledMoldGivesTheCastingAndTheBreakRollDecidesTheMold() {
        PlayerMock player = server.addPlayer("Bot");
        MoldListener listener = listener();
        for (boolean breaks : new boolean[] {true, false}) {
            player.getInventory().clear();
            draw = breaks ? 0.1 : 0.9;
            ItemStack m = mold();
            FluidPdc.write(m, new FluidAmount(COPPER, 100));
            HeatPdc.write(m, new HeatState(300f, now.get()));
            player.getInventory().setItemInMainHand(m);
            player.setSneaking(true);
            listener.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, m, null, BlockFace.SELF, EquipmentSlot.HAND));
            assertThat(player.getInventory().contains(Material.IRON_AXE)).as("the casting").isTrue();
            ItemStack held = player.getInventory().getItemInMainHand();
            if (breaks) {
                assertThat(held.isEmpty()).as("draw 0.1 < 0.5: the mold breaks").isTrue();
            } else {
                assertThat(molds.content(held).isEmpty()).as("draw 0.9: kept and drained").isTrue();
                assertThat(molds.isMold(held)).isTrue();
            }
        }
    }

    @Test
    void sneakUseOnAMoltenMoldGivesNothing() {
        PlayerMock player = server.addPlayer("Bot");
        ItemStack m = mold();
        FluidPdc.write(m, new FluidAmount(COPPER, 100));
        HeatPdc.write(m, new HeatState(1300f, now.get()));
        player.getInventory().setItemInMainHand(m);
        player.setSneaking(true);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, m, null, BlockFace.SELF, EquipmentSlot.HAND);
        listener().onInteract(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(player.getInventory().contains(Material.IRON_AXE)).isFalse();
        assertThat(molds.content(player.getInventory().getItemInMainHand()).amount()).isEqualTo(100);
    }

    @Test
    void sneakingWithAMoltenVesselAndAMoldInTheOtherHandPoursAtOnce() {
        PlayerMock player = server.addPlayer("Bot");
        player.getInventory().setItemInMainHand(vessel(300, 1200f));
        player.getInventory().setItemInOffHand(mold());
        player.setSneaking(true);
        ItemStack held = player.getInventory().getItemInMainHand();
        listener().onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null, BlockFace.SELF, EquipmentSlot.HAND));
        assertThat(molds.content(player.getInventory().getItemInOffHand()).amount()).isEqualTo(100);
        assertThat(vessels.fluid(player.getInventory().getItemInMainHand()).amount()).isEqualTo(200);
    }

    @Test
    void thePourScreenFillsOneMbPerTickAndGivesTheMoldBackOnClose() {
        PlayerMock player = server.addPlayer("Bot");
        MoldScreens screens = screens();
        player.getInventory().setItemInMainHand(vessel(500, 1300f));
        GuiSession session = screens.open(player, EquipmentSlot.HAND);
        assertThat(session).isNotNull();
        assertThat(screens.isPourScreen(session)).isTrue();
        session.getInventory().setItem(MoldScreens.SLOT, mold());
        for (int i = 0; i < 30; i++) {
            repeating.get(0).run();
        }
        assertThat(molds.content(session.getInventory().getItem(MoldScreens.SLOT)).amount()).as("one mB a tick").isEqualTo(30);
        assertThat(vessels.fluid(player.getInventory().getItemInMainHand()).amount()).isEqualTo(470);
        player.closeInventory();
        assertThat(repeating).as("the tick task stops with the screen").isEmpty();
        ItemStack back = player.getInventory().getItem(player.getInventory().first(Material.FLOWER_POT));
        assertThat(molds.content(back).amount()).isEqualTo(30);
    }

    @Test
    void aColdVesselOpensNoPourScreen() {
        PlayerMock player = server.addPlayer("Bot");
        player.getInventory().setItemInMainHand(vessel(500, 400f));
        assertThat(screens().open(player, EquipmentSlot.HAND)).isNull();
    }

    @Test
    void theTooltipShowsTheFluidAndTheCapacity() {
        FluidLang lang = new FluidLang(file -> file.equals("en_us.json") ? new ByteArrayInputStream(
            "{\"fluid.tfc.metal.copper\": \"Copper\"}".getBytes(StandardCharsets.UTF_8)) : null);
        lang.load("en_us");
        MoldTooltipContributor c = new MoldTooltipContributor(lang);
        Map<String, Object> fluid = Map.of("tfp:v", 1, "tfp:id", COPPER, "tfp:amount", 40);
        List<Component> out = new ArrayList<>();
        c.contribute(new TooltipItem("tfp:ceramic/sword_blade_mold", "minecraft:paper", 1, TooltipData.of(Map.of(FluidPdc.KEY, fluid))),
            new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        c.contribute(new TooltipItem("tfp:ceramic/jug", "minecraft:paper", 1, TooltipData.of(Map.of(FluidPdc.KEY, fluid))),
            new TooltipContext("en_us", ClockSnapshot.UNSET), out::add);
        assertThat(out.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList()).containsExactly("Copper 40 / 200 mB");
    }

    private MoldScreens screens() {
        GuiService gui = new GuiService(server, scheduler(), System::currentTimeMillis, true);
        server.getPluginManager().registerEvents(new dev.tfp.paper.gui.GuiListener(gui), plugin);
        return new MoldScreens(gui, scheduler(), Messages.fromJar(), molds, pourer);
    }

    private MoldListener listener() {
        return new MoldListener(molds, pourer, screens(), Messages.fromJar(), () -> draw);
    }
}
