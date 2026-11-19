/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;

/**
 * {@code tool.metal_armor} (PAR-TOOL-03): the four steel and copper armour pieces and the steel sword load as CraftEngine items
 * whose attribute modifiers are TFC's: steel set armour 15 and toughness 4, copper 9 and 0, the steel sword's attack damage 5.75
 * and attack speed -2.4 (TFCArmorMaterials, TFCTiers, Metal.ItemType).
 */
public final class MetalArmorCase implements SelfTestCase {
    @Override
    public String name() {
        return "tool.metal_armor: metal armour and weapon attribute values equal TFC's (PAR-TOOL-03)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return OnThread.script("metal-armor", () -> check(tfp));
    }

    private static double sum(ItemStack stack, Attribute attribute) {
        double total = 0;
        var mods = stack.getItemMeta().getAttributeModifiers(attribute);
        if (mods != null) {
            for (AttributeModifier m : mods) {
                total += m.getAmount();
            }
        }
        return total;
    }

    private static ItemStack item(TfpPlugin tfp, String id) {
        return tfp.integrations().customItems().create(id, 1).orElseThrow(() -> new AssertionError("no CE item " + id));
    }

    private static void expect(String what, double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-4) {
            throw new AssertionError(what + ": " + actual + ", TFC " + expected);
        }
    }

    private static String check(TfpPlugin tfp) {
        for (Object[] row : new Object[][] {{"steel", 15.0, 4.0}, {"copper", 9.0, 0.0}}) {
            double armor = 0;
            double toughness = 0;
            for (String piece : new String[] {"helmet", "chestplate", "greaves", "boots"}) {
                ItemStack s = item(tfp, "tfp:metal/" + piece + "/" + row[0]);
                armor += sum(s, Attribute.ARMOR);
                toughness += sum(s, Attribute.ARMOR_TOUGHNESS);
            }
            expect(row[0] + " set armor", armor, (double) row[1]);
            expect(row[0] + " set toughness", toughness, (double) row[2]);
        }
        ItemStack sword = item(tfp, "tfp:metal/sword/steel");
        expect("steel sword attack damage", sum(sword, Attribute.ATTACK_DAMAGE), 5.75);
        expect("steel sword attack speed", sum(sword, Attribute.ATTACK_SPEED), -2.4);
        return "steel 15/4, copper 9/0, steel sword 5.75 / -2.4";
    }
}
