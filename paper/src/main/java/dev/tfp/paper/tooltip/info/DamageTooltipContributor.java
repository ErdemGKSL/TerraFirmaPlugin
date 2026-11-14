/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/PhysicalDamageType.java @ c3b4d9536 (addTooltipInfo)
 */
package dev.tfp.paper.tooltip.info;

import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.item.info.DamageInfo;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/**
 * TFC's damage lines: {@code "Deals Slashing Damage"} on a weapon of a {@code deals_*_damage} tag and
 * {@code "Resistances: Slashing x, Piercing y, Crushing z"} on an armour piece of an {@code item_damage_resistance}
 * definition, after the size line and before forging ({@link #ORDER_DAMAGE}). Netty thread; reads immutable snapshots.
 */
public final class DamageTooltipContributor implements TooltipContributor {
    public static final String ID = "damage_type";
    /** TFC: after {@code ItemSizeManager.addTooltipInfo}, before {@code ForgingBonusComponent.addTooltipInfo}. */
    public static final int ORDER_DAMAGE = 150;

    private final Supplier<TagLookup> tags;
    private final Supplier<DamageResistances> resistances;
    private final TooltipText text;

    public DamageTooltipContributor(Supplier<TagLookup> tags, Supplier<DamageResistances> resistances,
            TooltipText text) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.resistances = Objects.requireNonNull(resistances, "resistances");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_DAMAGE;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        String id = item.id();
        TagLookup lookup = tags.get();
        String locale = context.locale();
        DamageInfo.dealsKey(id, lookup).ifPresent(key -> lines.accept(LegacyLines.translate(text, locale, key)));
        Optional<List<DamageInfo.Share>> shares = DamageInfo.resistances(id, resistances.get(), lookup);
        if (shares.isPresent()) {
            Component[] args = shares.get().stream()
                    .map(s -> Component.text(s.immune() ? text.get(locale, DamageInfo.IMMUNE_KEY) : s.percent()))
                    .toArray(Component[]::new);
            lines.accept(LegacyLines.translate(text, locale, DamageInfo.RESISTS_KEY, args));
        }
    }
}
