/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536
 */
package dev.tfp.core.metal;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The 27 metals of TFC, in TFC's order. Immutable: every field is final and set in the constructor.
 *
 * <p>Each metal has its fluid heat (the melt temperature and the specific heat capacity of the molten fluid, the
 * {@code fluid_heat/} data) and its ingot heat (the heat capacity, forging and welding temperatures of the ingot, the
 * {@code item_heat/} data). Metals with a tool tier also have a {@link MetalTier}. The {@code UNKNOWN} fallback metal of TFC
 * is not here: it is the registry's fallback, see {@link #UNKNOWN_ID}.
 *
 * <p>The colour, map colour, rarity, textures and the item parts of each metal are presentation or item data, and are not
 * in this table.
 */
public enum Metal {
    BISMUTH(-1.0f, 270.0f, 0.021428572f, 7.142857f, 162.0f, 216.0f),
    BISMUTH_BRONZE(MetalTier.BISMUTH_BRONZE, -1.0f, 985.0f, 0.008571429f, 2.857143f, 591.0f, 788.0f),
    BLACK_BRONZE(MetalTier.BLACK_BRONZE, -1.0f, 1070.0f, 0.008571429f, 2.857143f, 642.0f, 856.0f),
    BRONZE(MetalTier.BRONZE, 0.7f, 950.0f, 0.008571429f, 2.857143f, 570.0f, 760.0f),
    BRASS(0.7f, 930.0f, 0.008571429f, 2.857143f, 558.0f, 744.0f),
    COPPER(MetalTier.COPPER, 0.0f, 1080.0f, 0.008571429f, 2.857143f, 648.0f, 864.0f),
    GOLD(-1.0f, 1060.0f, 0.005f, 1.6666666f, 636.0f, 848.0f),
    NICKEL(-1.0f, 1453.0f, 0.00625f, 2.0833333f, 871.80005f, 1162.4f),
    ROSE_GOLD(-1.0f, 960.0f, 0.008571429f, 2.857143f, 576.0f, 768.0f),
    SILVER(0.95f, 961.0f, 0.00625f, 2.0833333f, 576.60004f, 768.8f),
    TIN(-1.0f, 230.0f, 0.021428572f, 7.142857f, 138.0f, 184.0f),
    ZINC(-1.0f, 420.0f, 0.014285715f, 4.761905f, 252.00002f, 336.0f),
    STERLING_SILVER(0.95f, 950.0f, 0.008571429f, 2.857143f, 570.0f, 760.0f),
    WROUGHT_IRON(MetalTier.WROUGHT_IRON, 0.0f, 1535.0f, 0.008571429f, 2.857143f, 921.00006f, 1228.0f),
    CAST_IRON(-1.0f, 1535.0f, 0.008571429f, 2.857143f, 921.00006f, 1228.0f),
    PIG_IRON(-1.0f, 1535.0f, 0.008571429f, 2.857143f, 921.00006f, 1228.0f),
    STEEL(MetalTier.STEEL, 0.9f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    BLACK_STEEL(MetalTier.BLACK_STEEL, -1.0f, 1485.0f, 0.008571429f, 2.857143f, 891.00006f, 1188.0f),
    BLUE_STEEL(MetalTier.BLUE_STEEL, -1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    RED_STEEL(MetalTier.RED_STEEL, -1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    WEAK_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    WEAK_BLUE_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    WEAK_RED_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    HIGH_CARBON_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    HIGH_CARBON_BLACK_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    HIGH_CARBON_BLUE_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    HIGH_CARBON_RED_STEEL(-1.0f, 1540.0f, 0.008571429f, 2.857143f, 924.00006f, 1232.0f),
    ;

    /** The weathering resistance value of a metal that does not weather (TFC {@code -1}). */
    public static final float NO_WEATHERING = -1f;
    /** The id of TFC's fallback metal, used when a fluid is not a known metal. Not one of the 27 metals. */
    public static final String UNKNOWN_ID = "tfc:metal/unknown";

    private static final Map<String, Metal> BY_ID = indexById();

    /** The metal's melt temperature in the TFC fluid heat, in degrees. */
    private final float meltTemperature;
    /** The specific heat capacity of the molten metal, in energy per degree and millibucket. */
    private final float specificHeatCapacity;
    /** The heat capacity of an ingot of this metal (TFC {@code item_heat} {@code heat_capacity}). */
    private final float ingotHeatCapacity;
    /** The forging temperature of an ingot of this metal. */
    private final float ingotForgingTemperature;
    /** The welding temperature of an ingot of this metal. */
    private final float ingotWeldingTemperature;
    /** The weathering resistance, or {@link #NO_WEATHERING} when the metal does not weather. */
    private final float weatheringResistance;
    /** The tool tier of this metal, or {@code null} when it has none. */
    private final MetalTier toolTier;
    private final String id;

    Metal(float weatheringResistance, float meltTemperature, float specificHeatCapacity, float ingotHeatCapacity,
          float ingotForgingTemperature, float ingotWeldingTemperature) {
        this(null, weatheringResistance, meltTemperature, specificHeatCapacity, ingotHeatCapacity, ingotForgingTemperature,
            ingotWeldingTemperature);
    }

    Metal(MetalTier toolTier, float weatheringResistance, float meltTemperature, float specificHeatCapacity, float ingotHeatCapacity,
          float ingotForgingTemperature, float ingotWeldingTemperature) {
        this.toolTier = toolTier;
        this.weatheringResistance = weatheringResistance;
        this.meltTemperature = meltTemperature;
        this.specificHeatCapacity = specificHeatCapacity;
        this.ingotHeatCapacity = ingotHeatCapacity;
        this.ingotForgingTemperature = ingotForgingTemperature;
        this.ingotWeldingTemperature = ingotWeldingTemperature;
        this.id = "tfc:metal/" + name().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Metal> indexById() {
        Map<String, Metal> index = new HashMap<>();
        for (Metal metal : values()) {
            index.put(metal.id, metal);
        }
        return Map.copyOf(index);
    }

    /** The fluid and item id of this metal, such as {@code tfc:metal/copper}. */
    public String id() {
        return id;
    }

    /**
     * The metal with this id, or empty when the id is not one of the 27 metals (including {@link #UNKNOWN_ID}).
     *
     * @param id the id, such as {@code tfc:metal/copper}
     */
    public static Optional<Metal> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** The melt temperature of this metal, in degrees (TFC fluid heat {@code melt_temperature}). */
    public float meltTemperature() {
        return meltTemperature;
    }

    /** The specific heat capacity of the molten metal (TFC fluid heat {@code specific_heat_capacity}). */
    public float specificHeatCapacity() {
        return specificHeatCapacity;
    }

    /** The heat capacity of an ingot of this metal (TFC item heat {@code heat_capacity}). */
    public float ingotHeatCapacity() {
        return ingotHeatCapacity;
    }

    /** The forging temperature of an ingot of this metal (TFC item heat {@code forging_temperature}). */
    public float ingotForgingTemperature() {
        return ingotForgingTemperature;
    }

    /** The welding temperature of an ingot of this metal (TFC item heat {@code welding_temperature}). */
    public float ingotWeldingTemperature() {
        return ingotWeldingTemperature;
    }

    /** Whether this metal weathers. Metals that do not weather have {@link #NO_WEATHERING} as their resistance. */
    public boolean weathers() {
        return weatheringResistance >= 0f;
    }

    /** The weathering resistance, or {@link #NO_WEATHERING} when the metal does not weather. */
    public float weatheringResistance() {
        return weatheringResistance;
    }

    /** The tool and armor tier of this metal, or empty when the metal has none. */
    public Optional<MetalTier> toolTier() {
        return Optional.ofNullable(toolTier);
    }

    /** The tier level of this metal, as TFC's {@code Metal#tier()}: the tool tier level, or 0 when there is none. */
    public int tier() {
        return toolTier == null ? 0 : toolTier.level();
    }
}
