/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java @ c3b4d9536 (renderBg: fillHeight; renderTooltip:
 * the tank's fluid units)
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.gui.GuiButtons;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/**
 * PAR-PROC-09: TFC's barrel tank. The level is TFC's fill height, {@code ceil(50 * amount / capacity)} pixels, which the
 * tank item ({@link BarrelScreenLayout#TANK_ITEM_MODEL}) carries as custom model data float 0 (its item model definition
 * picks the model of that height) with the fluid's tint as custom model data colour 0. The three tank slots show TFC's
 * tooltip, {@code "<amount> mB of <fluid>"}, and nothing when the tank is empty.
 */
public final class BarrelTank {
    private BarrelTank() {
    }

    /** TFC {@code BarrelScreen.renderBg}: the fluid's height in pixels, 0 to 50. */
    public static int fillPixels(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        int fill = (int) Math.ceil((float) BarrelScreenLayout.TANK_PIXELS * amount / (float) capacity);
        return Math.max(0, Math.min(BarrelScreenLayout.TANK_PIXELS, fill));
    }

    /** The tint of {@code fluidId} (TFC's client tint; opaque white for an unknown fluid). */
    public static int tint(String fluidId) {
        return TfcFluids.get(fluidId).map(TfcFluid::rgb).orElse(0xFFFFFF);
    }

    /**
     * The item a tank slot holds.
     *
     * @param glyphs   true with CraftEngine (the pack has the tank models); false gives a plain named paper
     * @param drawn    true for the slot that draws the fluid ({@link BarrelScreenLayout#TANK_SLOT}); the others are blank
     * @param fluid    the tank
     * @param capacity the tank's capacity
     * @param tooltip  the tooltip ({@link #tooltip}); null hides it (an empty tank)
     */
    public static ItemStack icon(boolean glyphs, boolean drawn, FluidAmount fluid, int capacity, Component tooltip) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        GuiButtons.style(meta, glyphs, "");
        if (tooltip != null) {
            meta.customName(tooltip.decoration(TextDecoration.ITALIC, false));
        } else {
            meta.setHideTooltip(true);
        }
        if (glyphs && drawn) {
            meta.setItemModel(NamespacedKey.fromString(BarrelScreenLayout.TANK_ITEM_MODEL));
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) fillPixels(fluid.amount(), capacity)));
            cmd.setColors(List.of(Color.fromRGB(tint(fluid.fluid()))));
            meta.setCustomModelDataComponent(cmd);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * TFC {@code Tooltips.fluidUnitsOf}: {@code pattern} is the viewer's {@code "{0} mB of {1}"} (any order); the fluid's
     * name is a translatable component of TFC's description id so the client names it in its own language. Empty for an
     * empty tank (TFC shows no tooltip then).
     */
    public static Optional<Component> tooltip(FluidAmount fluid, String pattern) {
        if (fluid.isEmpty()) {
            return Optional.empty();
        }
        Component name = TfcFluids.get(fluid.fluid())
            .<Component>map(f -> Component.translatable(f.descriptionId(), fallbackName(f.id())))
            .orElse(Component.text(fluid.fluid()));
        Component amount = Component.text(fluid.amount());
        net.kyori.adventure.text.TextComponent.Builder out = Component.text();
        int i = 0;
        while (i < pattern.length()) {
            int a = pattern.indexOf('{', i);
            if (a < 0 || a + 2 >= pattern.length() || pattern.charAt(a + 2) != '}'
                || (pattern.charAt(a + 1) != '0' && pattern.charAt(a + 1) != '1')) {
                int end = a < 0 ? pattern.length() : a + 1;
                out.append(Component.text(pattern.substring(i, end)));
                i = end;
                continue;
            }
            if (a > i) {
                out.append(Component.text(pattern.substring(i, a)));
            }
            out.append(pattern.charAt(a + 1) == '0' ? amount : name);
            i = a + 3;
        }
        return Optional.of(out.build());
    }

    /** The English-looking fallback a client without the name shows: the id's path, words capitalised. */
    static String fallbackName(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder b = new StringBuilder();
        for (String w : path.split("_")) {
            if (!w.isEmpty()) {
                if (!b.isEmpty()) {
                    b.append(' ');
                }
                b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return b.toString();
    }
}
