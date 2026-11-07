/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java @ c3b4d9536 (the fluid types: descriptionId,
 *       temperature), src/main/java/net/dries007/tfc/client/ClientEventHandler.java @ c3b4d9536 (the tint colours)
 */
package dev.tfp.core.fluid;

import dev.tfp.core.metal.Metal;
import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.Drinkables;
import java.util.Objects;
import java.util.Optional;

/**
 * One fluid a container can hold (PAR-FLUID-01): a TFC source fluid ({@code tfp:} ids, TFC's {@code tfc:} renamed) or one
 * of the vanilla fluids TFC uses ({@code minecraft:water}, {@code minecraft:lava}, NeoForge's {@code minecraft:milk}).
 * Immutable; any thread.
 *
 * @param id            the fluid id, such as {@code tfp:metal/copper}
 * @param category      which family of TFC's registrations it comes from
 * @param colour        the ARGB tint TFC's client gives the fluid ({@code FluidRendererExtension} tint colour)
 * @param temperature   the fluid type's temperature in Kelvin (NeoForge {@code FluidType.Properties.temperature}: 1300
 *                      for lava-like fluids, the default 300 otherwise)
 * @param descriptionId the translation key of the fluid's name (TFC's {@code descriptionId}; vanilla's block key for
 *                      water and lava, NeoForge's {@code fluid_type.minecraft.milk})
 */
public record TfcFluid(String id, Category category, int colour, int temperature, String descriptionId) {

    /** The families of fluid registrations in {@code TFCFluids}, plus the vanilla fluids. */
    public enum Category {
        /** {@code TFCFluids.METALS}: molten metals, lava-like. */
        METAL,
        /** {@code SALT_WATER}, {@code SPRING_WATER} and {@code RIVER_WATER}. */
        WATER,
        /** {@code TFCFluids.SIMPLE_FLUIDS} ({@code SimpleFluid}). */
        SIMPLE,
        /** {@code TFCFluids.COLORED_FLUIDS}: the 16 dye fluids. */
        DYE,
        /** Vanilla water and lava, NeoForge milk (enabled by TFC's {@code NeoForgeMod.enableMilkFluid()}). */
        VANILLA
    }

    public TfcFluid {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(descriptionId, "descriptionId");
    }

    /** The RGB part of {@link #colour()} (for a text colour). */
    public int rgb() {
        return colour & 0xFFFFFF;
    }

    /** Whether this is a molten metal (TFC's {@code lavaLike()} type, {@code tfc:molten_metals}). */
    public boolean molten() {
        return category == Category.METAL;
    }

    /**
     * The metal of a molten-metal fluid; empty for the other fluids and for {@code tfp:metal/unknown}, which is not one
     * of the 27 metals.
     */
    public Optional<Metal> metal() {
        if (category != Category.METAL) {
            return Optional.empty();
        }
        return Metal.fromId("tfc:" + id.substring(id.indexOf(':') + 1));
    }

    /** The drink definition of this fluid ({@code Drinkable.get(fluid)}), empty when it cannot be drunk. */
    public Optional<Drinkable> drinkable(Drinkables drinkables) {
        return drinkables.get(id);
    }
}
