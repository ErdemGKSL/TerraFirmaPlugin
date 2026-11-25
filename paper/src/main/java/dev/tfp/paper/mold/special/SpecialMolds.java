/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CastingCraftingRecipe.java @ c3b4d9536 (getRemainingItems break roll),
 *       src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (the fire ingot and bell molds),
 *       src/generated/resources/data/tfc/recipe/casting/{bronze_fire_ingot,brass_bell}.json @ c3b4d9536
 */
package dev.tfp.paper.mold.special;

import dev.tfp.core.vessel.MoldKind;
import dev.tfp.core.vessel.MoldRules;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-METAL-02: the fire ingot mold and the bell mold. Both are ordinary {@link MoldKind}s of the mold glue (PAR-POT-05):
 * their fluids, capacity (100 mB) and casting recipes come from the TFC data (a fire ingot mold gives the same ingot as the
 * ingot mold but is reusable: {@code break_chance} 0.01; a bell mold gives a bell and breaks, the default chance 1). This
 * class holds what is specific to them.
 *
 * <p>Until the bell items exist (PAR-METAL-18 adds {@code tfp:bronze_bell} and {@code tfp:brass_bell} as CraftEngine
 * items), {@link #placeholderBell} stands in for a bell result: a vanilla bell named like the real one. As soon as the
 * custom item exists the recipe runtime creates it first and the placeholder is never used.
 *
 * <p>Threads: pure functions on values the caller owns.
 */
public final class SpecialMolds {
    /** The break chance of the fire ingot mold's recipes (TFC data: {@code "break_chance": 0.01}). */
    public static final float FIRE_INGOT_BREAK_CHANCE = 0.01f;
    /** The bell metals of TFC's casting recipes ({@code bronze_bell}, {@code brass_bell}, {@code gold_bell}). */
    private static final Set<String> BELL_METALS = Set.of("bronze", "brass", "gold");
    private static final Pattern BELL = Pattern.compile("(?:tfp|tfc):([a-z_]+)_bell");

    private SpecialMolds() {
    }

    /** Whether a kind is one of the two molds of this item. */
    public static boolean isSpecial(MoldKind kind) {
        return kind == MoldKind.FIRE_INGOT || kind == MoldKind.BELL;
    }

    /** TFC {@code CastingCraftingRecipe.getRemainingItems}: the mold survives when the draw is above the break chance. */
    public static boolean keeps(float draw, float breakChance) {
        return !MoldRules.breaks(draw, breakChance);
    }

    /** The bell id of a metal: {@code tfp:<metal>_bell}. */
    public static String bellId(String metal) {
        return "tfp:" + metal + "_bell";
    }

    /** Whether {@code id} is one of the bells a bell mold casts. */
    public static boolean isBellId(String id) {
        return metalOf(id).isPresent();
    }

    private static Optional<String> metalOf(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Matcher m = BELL.matcher(id);
        return m.matches() && BELL_METALS.contains(m.group(1)) ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** A vanilla bell named "Brass Bell" for the id {@code tfp:brass_bell}; empty for any other id. */
    public static Optional<ItemStack> placeholderBell(String id, int amount) {
        Optional<String> metal = metalOf(id);
        if (metal.isEmpty() || amount < 1) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(Material.BELL, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = metal.get().substring(0, 1).toUpperCase(Locale.ROOT) + metal.get().substring(1) + " Bell";
            meta.itemName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return Optional.of(stack);
    }
}
