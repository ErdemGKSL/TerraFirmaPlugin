/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.effect;

import dev.tfp.core.aquatic.AquaticSpecies;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.Random;

/** PAR-DATA-07: TFC's mob effects the squid and octopoteuthis give (ink, glow ink) on vanilla carriers. */
public final class EffectsModule implements FeatureModule {
    @Override
    public String id() {
        return "effects";
    }

    @Override
    public void enable(ModuleContext context) {
        var plugin = (TfpPlugin) context.plugin();
        context.listen(new InkListener(
            e -> plugin.animals().record(e).flatMap(r -> AquaticSpecies.ofEntityType(r.species())), new Random()));
    }
}
