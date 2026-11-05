/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.knap;

import dev.tfp.core.knap.KnappingDefinitions;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * PAR-PROC-01: knapping on the module SPI. Needs the GUI framework ({@link GuiService}), the tag runtime ({@link Tags})
 * and the recipe runtime ({@link Recipes}, which creates the result stacks); listed after them in the service file.
 */
public final class KnappingModule implements FeatureModule {
    public static final String ID = "knapping";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        GuiService gui = context.services().require(GuiService.class);
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        KnappingMenu.Services services = new KnappingMenu.Services(gui, context.scheduler(), context.messages(),
            new DefinitionsCache(() -> context.registryService().registries()),
            () -> ItemIngredientContext.lookup(tags.snapshot()), tags::itemId,
            template -> recipes.modifiers().create(template));
        context.listen(new KnappingListener(services));
    }

    /**
     * The knapping definitions of the current registry snapshot, rebuilt once per published snapshot (start,
     * {@code /tfp reload data}); empty until the registries have loaded. Thread-safe: a volatile pair per call.
     */
    static final class DefinitionsCache implements Supplier<KnappingDefinitions> {
        private record Entry(long version, KnappingDefinitions definitions) {
        }

        private final Supplier<Registries> registries;
        private volatile Entry entry = new Entry(0, KnappingDefinitions.EMPTY);

        DefinitionsCache(Supplier<Registries> registries) {
            this.registries = Objects.requireNonNull(registries, "registries");
        }

        @Override
        public KnappingDefinitions get() {
            Registries r = registries.get();
            if (r == null) {
                return KnappingDefinitions.EMPTY;
            }
            long version = r.version();
            Entry e = entry;
            if (e.version() == version) {
                return e.definitions();
            }
            KnappingDefinitions defs = version == 0 ? KnappingDefinitions.EMPTY : KnappingDefinitions.fromRegistry(r.get());
            entry = new Entry(version, defs);
            return defs;
        }
    }
}
