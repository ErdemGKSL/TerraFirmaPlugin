/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgingBonusComponent.java @ c3b4d9536 (addTooltipInfo),
 *       src/main/java/net/dries007/tfc/common/component/forge/ForgingCapability.java @ c3b4d9536 (addTooltipInfo),
 *       src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java @ c3b4d9536 (author)
 */
package dev.tfp.paper.anvil.tooltip;

import dev.tfp.core.forge.ForgeSteps;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * TFC's two forging tooltip lines: the forging bonus ({@code ForgingBonusComponent.addTooltipInfo}: the tier's name, followed
 * by the smith through {@code tfc.tooltip.author} "§a%s§7 by %s" when one is stored) and "Worked" for an item whose forging
 * steps were taken ({@code ForgingCapability.addTooltipInfo}). Reads the PDC records {@code tfp:forging_bonus},
 * {@code tfp:forging_author} and {@code tfp:forging} of the packet copy of the stack, on Netty threads.
 */
public final class ForgingTooltipContributor implements TooltipContributor {
    public static final String ID = "forging";
    static final String BONUS_KEY = "tfp:forging_bonus";
    static final String AUTHOR_KEY = "tfp:forging_author";
    static final String FORGING_KEY = "tfp:forging";
    static final String WORKED_TEXT = "tooltip.forging.worked";

    private final TooltipText text;

    public ForgingTooltipContributor(TooltipText text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_FORGING;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        TooltipData values = item.values();
        String bonus = values.string(BONUS_KEY);
        if (bonus != null) {
            String tier = bonus.toLowerCase(Locale.ROOT);
            if (!tier.equals("none") && List.of("modest", "well", "expert", "perfect").contains(tier)) {
                String name = text.get(context.locale(), "tooltip.forging.bonus." + tier);
                String author = values.string(AUTHOR_KEY);
                Component line = Component.text(name); // no colour without a smith, as TFC's plain display name
                if (author != null && !author.isEmpty()) {
                    line = Component.text().append(Component.text(name, NamedTextColor.GREEN))
                        .append(Component.text(" " + text.get(context.locale(), "tooltip.forging.author", author), NamedTextColor.GRAY)).build();
                }
                lines.accept(line);
            }
        }
        if (worked(values.string(FORGING_KEY))) {
            lines.accept(Component.text(text.get(context.locale(), WORKED_TEXT)));
        }
    }

    /**
     * TFC {@code ForgeSteps.isWorked}: some step was taken. The record is {@code recipe;target;work;total;STEP,...} (see
     * {@code ForgingPdc}); {@code total} is the number of steps taken.
     */
    static boolean worked(String record) {
        if (record == null) {
            return false;
        }
        String[] p = record.split(";", -1);
        if (p.length != 5) {
            return false;
        }
        try {
            return new ForgeSteps(List.of(), Integer.parseInt(p[3])).isWorked();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
