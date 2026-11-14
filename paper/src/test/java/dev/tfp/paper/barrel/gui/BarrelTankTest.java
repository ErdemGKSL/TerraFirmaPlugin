/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** PAR-PROC-09: the tank frame per level (TFC {@code BarrelScreen.renderBg}), the tooltip and the layout. */
class BarrelTankTest {
    private static final int CAP = BarrelRules.DEFAULT_CAPACITY;

    @Test
    void tankFramePerLevelIsTfcsCeilOfFiftyTimesTheFraction() {
        assertThat(BarrelTank.fillPixels(0, CAP)).isZero();
        assertThat(BarrelTank.fillPixels(1, CAP)).as("one mB already shows a pixel (ceil)").isEqualTo(1);
        assertThat(BarrelTank.fillPixels(200, CAP)).isEqualTo(1);
        assertThat(BarrelTank.fillPixels(201, CAP)).isEqualTo(2);
        assertThat(BarrelTank.fillPixels(5000, CAP)).as("half full").isEqualTo(25);
        assertThat(BarrelTank.fillPixels(9999, CAP)).isEqualTo(50);
        assertThat(BarrelTank.fillPixels(CAP, CAP)).isEqualTo(50);
        assertThat(BarrelTank.fillPixels(CAP * 2, CAP)).as("clamped").isEqualTo(50);
        // every level: TFC's (int) Math.ceil((float) 50 * amount / (float) capacity), monotonic, 0..50
        int last = 0;
        for (int mb = 0; mb <= CAP; mb++) {
            int f = BarrelTank.fillPixels(mb, CAP);
            assertThat(f).isEqualTo((int) Math.ceil((float) 50 * mb / (float) CAP));
            assertThat(f).isBetween(last, 50);
            last = f;
        }
        // a different capacity (TFC barrelCapacity is configurable)
        assertThat(BarrelTank.fillPixels(333, 1000)).isEqualTo(17);
    }

    @Test
    void theTooltipIsTfcsFluidUnitsOfWithATranslatableName() {
        assertThat(BarrelTank.tooltip(FluidAmount.EMPTY, "{0} mB of {1}")).isEmpty();
        Optional<Component> t = BarrelTank.tooltip(new FluidAmount("tfp:brine", 5000), "{0} mB of {1}");
        assertThat(t).isPresent();
        String plain = PlainTextComponentSerializer.plainText().serialize(t.get());
        assertThat(plain).startsWith("5000 mB of ");
        assertThat(t.get().children()).anySatisfy(c -> {
            assertThat(c).isInstanceOf(TranslatableComponent.class);
            assertThat(((TranslatableComponent) c).key()).isEqualTo("fluid.tfc.brine");
            assertThat(((TranslatableComponent) c).fallback()).isEqualTo("Brine");
        });
        // the order follows the pattern (languages that put the name first)
        String swapped = PlainTextComponentSerializer.plainText().serialize(
            BarrelTank.tooltip(new FluidAmount("minecraft:water", 7), "{1}: {0} mB").orElseThrow());
        assertThat(swapped).endsWith(": 7 mB");
    }

    @Test
    void theTintIsTfcsClientColour() {
        assertThat(BarrelTank.tint("minecraft:water")).isEqualTo(0x3F76E4);
        assertThat(BarrelTank.tint("tfp:tannin")).isEqualTo(0x63594E);
        assertThat(BarrelTank.tint("tfp:no_such_fluid")).isEqualTo(0xFFFFFF);
    }

    @Test
    void theLayoutPutsTfcsSlotsOnTheChestGrid() {
        ChestGeometry chest = BarrelScreenLayout.CHEST;
        // TFC (35, 20), (35, 54), (89, 37) moved 9 right and up 2, 0, 1
        assertThat(BarrelScreenLayout.CHEST_SLOTS).containsExactly(chest.slotAt(44, 18), chest.slotAt(44, 54),
            chest.slotAt(98, 36));
        assertThat(BarrelScreenLayout.tfcSlot(14)).isEqualTo(BarrelScreenLayout.TFC_ITEM);
        assertThat(BarrelScreenLayout.tfcSlot(0)).isEqualTo(-1);
        // the seal button art (TFC (123, 35) + (9, -1)) is centred on its slot
        assertThat(chest.slotX(BarrelScreenLayout.SEAL_SLOT)).isEqualTo(BarrelScreenLayout.SEAL.x() + 2);
        assertThat(chest.slotY(BarrelScreenLayout.SEAL_SLOT)).isEqualTo(BarrelScreenLayout.SEAL.y() + 2);
        // the tank column: x 8, rows 0..2, the drawing item in the middle
        for (int slot : BarrelScreenLayout.TANK_SLOTS) {
            assertThat(chest.slotX(slot)).isEqualTo(8);
        }
        assertThat(chest.slotY(BarrelScreenLayout.TANK_SLOT)).isEqualTo(36);
        // TFC's disabled highlight shows only while sealed
        assertThat(BarrelScreenLayout.frames(true)).isEqualTo(Map.of("seal", 0, "disabled", 0));
        assertThat(BarrelScreenLayout.frames(false)).isEqualTo(Map.of("seal", 1, "disabled", -1));
        assertThat(BarrelScreenLayout.LAYERS.compose(BarrelScreenLayout.frames(false)).tags())
            .isNotEqualTo(BarrelScreenLayout.LAYERS.compose(BarrelScreenLayout.frames(true)).tags());
    }
}
