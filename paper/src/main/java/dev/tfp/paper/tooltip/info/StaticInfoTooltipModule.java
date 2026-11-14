/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.info;

import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.item.info.DepositItems;
import dev.tfp.core.item.info.FuelInfo;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
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
 * PAR-INFRA-46: TFC's static info tooltip lines that no item feature owns. Adds four contributors to the
 * {@link TooltipPipeline}: the registration lore ({@link StaticLoreContributor}: rock display category, stone head rock
 * category, glass type; first, under the name), the damage type and resistance lines ({@link DamageTooltipContributor},
 * after size), the fuel line ({@link FuelTooltipContributor}, after heat) and the sluice/pan line
 * ({@link DepositTooltipContributor}, after metal content). Listed after the {@code tags} and {@code tooltip} modules
 * in the service file. The data tables are rebuilt when the registries change version (a {@code /tfp reload}).
 */
public final class StaticInfoTooltipModule implements FeatureModule {
    public static final String ID = "static_info_tooltip";

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
        var lookups = ItemSizes.lookups(tags::snapshot);
        pipeline.add(new StaticLoreContributor(text));
        pipeline.add(new DamageTooltipContributor(lookups,
                versioned(registries, DamageResistances.EMPTY, DamageResistances::fromRegistry), text));
        pipeline.add(new FuelTooltipContributor(lookups,
                versioned(registries, FuelInfo.EMPTY, FuelInfo::fromRegistry), TooltipStyles.TFC_DEFAULTS, text));
        pipeline.add(new DepositTooltipContributor(lookups,
                versioned(registries, DepositItems.EMPTY, DepositItems::fromRegistry), text));
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
