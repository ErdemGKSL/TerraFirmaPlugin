/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement.criteria;

import dev.tfp.core.tag.TagType;
import dev.tfp.paper.advancement.Advancements;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-DATA-03: wires TFC's advancement criteria to the feature events ({@link CriteriaListener}). Needs
 * {@link Advancements} ({@code AdvancementModule}) and {@link Tags}; listed after both in the service file.
 */
public final class CriteriaModule implements FeatureModule {
    public static final String ID = "advancementcriteria";

    @Override
    public String id() {
        return ID;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Advancements advancements = context.services().require(Advancements.class);
        context.listen(new CriteriaListener(CriteriaMap.DEFAULT, advancements, tags::itemId,
            (id, tag) -> tags.is(TagType.ITEM, id, tag), (id, tag) -> tags.is(TagType.ENTITY_TYPE, id, tag),
            at -> at.getWorld().getBiome(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getKey().toString()));
    }
}
