/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement;

import dev.tfp.core.advancement.ItemCriteria;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-DATA-02: provides the {@link Advancements} trigger API to later modules and listens for the item criteria
 * ({@link ItemCriteriaListener}). Needs the tag runtime ({@link Tags}); listed after it in the service file. The
 * advancements themselves are datagen's {@code data/tfp/advancement/**} in the bootstrap datapack.
 */
public final class AdvancementModule implements FeatureModule {
    public static final String ID = "advancements";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Advancements advancements = new Advancements(AdvancementGrants.BUKKIT,
            (player, task) -> context.scheduler().entity(player, task, () -> { }), context.logger()::warning);
        context.services().provide(Advancements.class, advancements);
        context.listen(new ItemCriteriaListener(ItemCriteria.DEFAULT, tags::is, advancements));
    }
}
