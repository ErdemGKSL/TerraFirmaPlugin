/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.mold.special;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.MoldKind;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.mold.MoldListener;
import dev.tfp.paper.mold.MoldPourer;
import dev.tfp.paper.mold.MoldScreens;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.vessel.VesselItems;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
 * PAR-METAL-02 on MockBukkit: the fire ingot mold (reusable, break chance 0.01) and the bell mold (breaks, bell result)
 * through the mold glue, and the placeholder bell. A flower pot stands for the fire ingot mold, a brick for the bell mold.
 */
class SpecialMoldsMockBukkitTest extends MockServerTest {
    static final String BRASS = "tfp:metal/brass";
    static final String BRONZE = "tfp:metal/bronze";

    private final AtomicLong now = new AtomicLong(100_000);
    private double draw = 0.5;
    private MoldItems molds;
    private MoldPourer pourer;

    private static String id(ItemStack stack) {
        return switch (stack.getType()) {
            case DECORATED_POT -> "tfp:ceramic/vessel";
            case FLOWER_POT -> "tfp:ceramic/fire_ingot_mold";
            case BRICK -> "tfp:ceramic/bell_mold";
            default -> null;
        };
    }

    @BeforeEach
    void setUp() {
        VesselItems vessels = new VesselItems(new VesselItems.Services(now::get, s -> Optional.ofNullable(id(s)), x -> 0.0,
            s -> Optional.empty(), s -> true), HeatConfig.DEFAULT);
        ItemHeatHooks heat = new ItemHeatHooks(now::get, HeatConfig.DEFAULT, stack -> 1.2f);
        molds = new MoldItems(new MoldItems.Services(now::get, s -> Optional.ofNullable(id(s)),
            (f, tag) -> f.startsWith("tfp:metal/"), heat, this::casting));
        pourer = new MoldPourer(vessels, molds);
    }

    /** The two TFC recipes in short: the fire ingot mold gives the ingot with 0.01, the bell mold the bell with 1. */
    private Optional<MoldItems.Cast> casting(ItemStack mold) {
        FluidAmount content = molds.content(mold);
        if (content.amount() < 100) {
            return Optional.empty();
        }
        String metal = content.fluid().substring("tfp:metal/".length());
        return switch (molds.kind(mold).orElseThrow()) {
            case FIRE_INGOT -> Optional.of(new MoldItems.Cast(new ItemStack(Material.IRON_INGOT), SpecialMolds.FIRE_INGOT_BREAK_CHANCE));
            case BELL -> SpecialMolds.placeholderBell(SpecialMolds.bellId(metal), 1).map(b -> new MoldItems.Cast(b, 1f));
            default -> Optional.empty();
        };
    }

    private ItemStack filled(Material type, String fluid, float temperature) {
        ItemStack m = new ItemStack(type);
        FluidPdc.write(m, new FluidAmount(fluid, 100));
        HeatPdc.write(m, new HeatState(temperature, now.get()));
        return m;
    }

    private MoldListener listener() {
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });
        GuiService gui = new GuiService(server, scheduler, System::currentTimeMillis, true);
        return new MoldListener(molds, pourer, new MoldScreens(gui, scheduler, Messages.fromJar(), molds, pourer),
            Messages.fromJar(), () -> draw);
    }

    private void sneakUse(PlayerMock player, ItemStack held) {
        player.getInventory().setItemInMainHand(held);
        player.setSneaking(true);
        listener().onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, held, null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    @Test
    void theTwoMoldsAreKnownKindsWithTheirFluidTagsAndCapacity() {
        assertThat(MoldItems.kindOf("tfp:ceramic/fire_ingot_mold")).contains(MoldKind.FIRE_INGOT);
        assertThat(MoldItems.kindOf("tfp:ceramic/bell_mold")).contains(MoldKind.BELL);
        assertThat(MoldItems.kindOf("tfp:ceramic/unfired_bell_mold")).isEmpty();
        assertThat(MoldKind.FIRE_INGOT.defaultCapacity()).isEqualTo(100);
        assertThat(MoldKind.BELL.defaultCapacity()).isEqualTo(100);
        assertThat(SpecialMolds.isSpecial(MoldKind.BELL) && SpecialMolds.isSpecial(MoldKind.FIRE_INGOT)).isTrue();
        assertThat(SpecialMolds.isSpecial(MoldKind.INGOT)).isFalse();
    }

    @Test
    void theFireIngotMoldBreaksOnlyBelowOnePercent() {
        assertThat(SpecialMolds.keeps(0.005f, SpecialMolds.FIRE_INGOT_BREAK_CHANCE)).as("0.005 < 0.01: breaks").isFalse();
        assertThat(SpecialMolds.keeps(0.0099f, SpecialMolds.FIRE_INGOT_BREAK_CHANCE)).isFalse();
        assertThat(SpecialMolds.keeps(0.01f, SpecialMolds.FIRE_INGOT_BREAK_CHANCE)).as("TFC breaks only when draw < chance").isTrue();
        assertThat(SpecialMolds.keeps(0.9f, SpecialMolds.FIRE_INGOT_BREAK_CHANCE)).isTrue();
        int breaks = 0;
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < 100_000; i++) {
            if (!SpecialMolds.keeps(random.nextFloat(), SpecialMolds.FIRE_INGOT_BREAK_CHANCE)) {
                breaks++;
            }
        }
        assertThat(breaks).as("about 1000 of 100000").isBetween(850, 1150);
    }

    @Test
    void aCooledFireIngotMoldGivesTheIngotAndIsKeptEmpty() {
        PlayerMock player = server.addPlayer("Bot");
        draw = 0.5;
        sneakUse(player, filled(Material.FLOWER_POT, BRONZE, 300f));
        assertThat(player.getInventory().contains(Material.IRON_INGOT)).isTrue();
        ItemStack held = player.getInventory().getItemInMainHand();
        assertThat(molds.isMold(held)).as("reusable").isTrue();
        assertThat(molds.content(held).isEmpty()).as("drained").isTrue();
    }

    @Test
    void aFireIngotMoldBreaksOnTheRareRoll() {
        PlayerMock player = server.addPlayer("Bot");
        draw = 0.001;
        sneakUse(player, filled(Material.FLOWER_POT, BRONZE, 300f));
        assertThat(player.getInventory().contains(Material.IRON_INGOT)).as("the ingot is still given").isTrue();
        assertThat(player.getInventory().getItemInMainHand().isEmpty()).as("the mold broke").isTrue();
    }

    @Test
    void aBellMoldGivesTheBrassBellAndBreaks() {
        PlayerMock player = server.addPlayer("Bot");
        draw = 0.99; // the default chance 1: even the highest draw breaks the mold
        sneakUse(player, filled(Material.BRICK, BRASS, 300f));
        ItemStack bell = player.getInventory().getItem(player.getInventory().first(Material.BELL));
        assertThat(bell).isNotNull();
        assertThat(PlainTextComponentSerializer.plainText().serialize(bell.getItemMeta().itemName())).isEqualTo("Brass Bell");
        assertThat(player.getInventory().getItemInMainHand().isEmpty()).as("a bell mold is single use").isTrue();
    }

    @Test
    void aMoltenBellMoldGivesNothingYet() {
        PlayerMock player = server.addPlayer("Bot");
        sneakUse(player, filled(Material.BRICK, BRASS, 1300f));
        assertThat(player.getInventory().contains(Material.BELL)).isFalse();
        assertThat(molds.isMolten(player.getInventory().getItemInMainHand())).isTrue();
    }

    @Test
    void thePlaceholderBellIsOnlyForTheBellIds() {
        assertThat(SpecialMolds.placeholderBell("tfp:bronze_bell", 1)).isPresent();
        assertThat(SpecialMolds.placeholderBell("tfp:gold_bell", 2).orElseThrow().getAmount()).isEqualTo(2);
        assertThat(SpecialMolds.placeholderBell("tfp:steel_bell", 1)).isEmpty();
        assertThat(SpecialMolds.placeholderBell("tfp:metal/ingot/brass", 1)).isEmpty();
        assertThat(SpecialMolds.placeholderBell(null, 1)).isEmpty();
        assertThat(SpecialMolds.isBellId("tfp:brass_bell")).isTrue();
        assertThat(SpecialMolds.isBellId("tfp:bell")).isFalse();
    }
}
