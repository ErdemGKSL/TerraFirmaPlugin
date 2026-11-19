/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgingBonusComponent.java @ c3b4d9536 (get, applyLikeUnbreaking),
 *       src/main/java/net/dries007/tfc/common/component/forge/ForgingBonus.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onBreakSpeed, damage multiplier)
 */
package dev.tfp.paper.tool;

import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The forging bonus of a tool or weapon (TFC {@code ForgingBonusComponent}, PAR-TOOL-02) as item PDC
 * {@code tfp:forging_bonus} = the {@link ForgingQuality} name; no entry is {@link ForgingQuality#NONE}. The anvil
 * (PAR-METAL) writes it with {@link #write}; the durability and attack damage multipliers are applied by
 * {@link ToolListener}.
 *
 * <p>TFC multiplies the mining speed on {@code BreakSpeed}. Paper has no such event, so {@link #write} also puts the
 * difference into the item's {@code mining_efficiency} attribute: vanilla adds that attribute to the tool's speed when
 * the speed is above 1, so a tool of speed s and efficiency e mines at s + s (e - 1) = s e on its mineable blocks. TFC
 * multiplies on every block, including those the tool is no faster on; here those stay at 1 (divergence in the ledger).
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack.
 */
public final class ForgingBonusPdc {
    public static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("tfp:forging_bonus"));
    /** The id of the attribute modifier that carries the mining efficiency. */
    public static final NamespacedKey MODIFIER = Objects.requireNonNull(NamespacedKey.fromString("tfp:forging_bonus_efficiency"));

    private ForgingBonusPdc() {
    }

    /** The stack's bonus; {@link ForgingQuality#NONE} for no entry or an unknown value. */
    public static ForgingQuality read(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return ForgingQuality.NONE;
        }
        String name = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (name == null) {
            return ForgingQuality.NONE;
        }
        try {
            return ForgingQuality.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ForgingQuality.NONE;
        }
    }

    /**
     * Sets {@code quality} on {@code stack} (replacing an earlier one; {@link ForgingQuality#NONE} removes it) and the
     * efficiency modifier for the stack's fastest tool rule.
     */
    public static void write(ItemStack stack, ForgingQuality quality, ForgingConfig config) {
        Objects.requireNonNull(quality, "quality");
        stack.editMeta(meta -> {
            meta.removeAttributeModifier(Attribute.MINING_EFFICIENCY, new AttributeModifier(MODIFIER, 0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            if (quality == ForgingQuality.NONE) {
                meta.getPersistentDataContainer().remove(KEY);
                return;
            }
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, quality.name());
            double extra = bestSpeed(meta) * (quality.efficiency(config) - 1.0);
            if (extra > 0) {
                meta.addAttributeModifier(Attribute.MINING_EFFICIENCY, new AttributeModifier(MODIFIER, extra,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            }
        });
    }

    /** Copies the bonus of {@code from} to {@code to} ({@code tfc:copy_forging_bonus}); none on {@code from} copies nothing. */
    public static ItemStack copy(ItemStack to, ItemStack from, ForgingConfig config) {
        ForgingQuality q = read(from);
        if (q != ForgingQuality.NONE) {
            write(to, q, config);
        }
        return to;
    }

    /** The highest speed among the tool rules of the item (1 without a tool component). */
    static double bestSpeed(ItemMeta meta) {
        ToolComponent tool = meta.hasTool() ? meta.getTool() : null;
        double best = 1.0;
        if (tool != null) {
            for (ToolComponent.ToolRule rule : tool.getRules()) {
                if (rule.getSpeed() != null && rule.getSpeed() > best) {
                    best = rule.getSpeed();
                }
            }
        }
        return best;
    }
}
