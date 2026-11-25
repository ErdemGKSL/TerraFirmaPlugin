/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plantable;

import dev.tfp.core.plantable.FertilizerInfo;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.paper.hud.HudStylesHolder;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * PAR-FARM-13: plant info and fertilizer lines in the item tooltips. Adds {@link FertilizerTooltipContributor} and
 * {@link PlantableTooltipContributor} to the {@link TooltipPipeline}. Listed after the {@code tags} and {@code tooltip} modules;
 * the fertilizer table is rebuilt when the registries change version (a {@code /tfp reload}).
 */
public final class PlantableModule implements FeatureModule {
    public static final String ID = "plantable_tooltip";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        TooltipPipeline pipeline = context.services().require(TooltipPipeline.class);
        TooltipText text = context.messages()::get;
        Supplier<Registries> registries = () -> context.registryService().registries();
        pipeline.add(new FertilizerTooltipContributor(ItemSizes.lookups(tags::snapshot),
            versioned(registries, FertilizerInfo.EMPTY, FertilizerInfo::fromRegistry), text));
        pipeline.add(new PlantableTooltipContributor(() -> HudStylesHolder.current().climate(), TooltipStyles.TFC_DEFAULTS, text));
    }

    /** A table built from the current registries, rebuilt when their version changes. Thread-safe. */
    static <T> Supplier<T> versioned(Supplier<Registries> registries, T empty, Function<RegistrySnapshot, T> build) {
        Objects.requireNonNull(empty, "empty");
        record Entry<T>(long version, T value) {
        }
        AtomicReference<Entry<T>> cache = new AtomicReference<>(new Entry<>(0, empty));
        return () -> {
            Registries r = registries.get();
            if (r == null) {
                return empty;
            }
            long version = r.version();
            Entry<T> e = cache.get();
            if (e.version() == version) {
                return e.value();
            }
            T value = version == 0 ? empty : build.apply(r.get());
            cache.set(new Entry<>(version, value));
            return value;
        };
    }
}
