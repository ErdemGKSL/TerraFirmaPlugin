/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/IHeatView.java @ c3b4d9536 (canWork),
 * src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (setTemperatureIfWarmer)
 */
package dev.tfp.paper.anvil.forge;

import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-05: what the anvil asks of an item's heat: whether it is hot enough to work ({@code IHeatView.canWork}) and
 * passing the heat of the input on to the output ({@code setTemperatureIfWarmer}). Thread of the stack's owner.
 */
public interface ForgeHeat {
    /** An item without heat (no heat definition) is not checked, as TFC checks only items with the heat capability. */
    boolean canWork(ItemStack stack);

    /** Gives {@code to} the temperature of {@code from} when that is warmer than {@code to}'s. */
    void keep(ItemStack from, ItemStack to);

    /** No heat at all: every item can be worked, nothing is passed on (tests). */
    ForgeHeat NONE = new ForgeHeat() {
        @Override
        public boolean canWork(ItemStack stack) {
            return true;
        }

        @Override
        public void keep(ItemStack from, ItemStack to) {
        }
    };

    /**
     * The real one: the temperature now from {@link ItemHeatHooks}, the working temperature from the item's heat definition
     * ({@code forging_temperature}); an item without a definition has no heat capability.
     */
    static ForgeHeat of(ItemHeatHooks hooks, Function<ItemStack, Optional<String>> idOf, Supplier<ItemHeatDefinitions> definitions,
            LongSupplier now) {
        Objects.requireNonNull(hooks, "hooks");
        Objects.requireNonNull(idOf, "idOf");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(now, "now");
        return new ForgeHeat() {
            private Optional<ItemHeatDefinitions.HeatData> data(ItemStack stack) {
                return idOf.apply(stack).flatMap(id -> definitions.get().get(id));
            }

            @Override
            public boolean canWork(ItemStack stack) {
                Optional<ItemHeatDefinitions.HeatData> d = data(stack);
                return d.isEmpty() || hooks.temperature(stack) >= d.get().forgingTemperature();
            }

            @Override
            public void keep(ItemStack from, ItemStack to) {
                if (data(to).isEmpty()) {
                    return; // the output cannot be heated
                }
                float warm = hooks.temperature(from);
                if (warm > hooks.temperature(to)) {
                    HeatPdc.write(to, new HeatState(warm, now.getAsLong()));
                }
            }
        };
    }
}
