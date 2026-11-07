/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java @ c3b4d9536 (registration order and types),
 *       src/main/java/net/dries007/tfc/common/fluids/SimpleFluid.java @ c3b4d9536 (ids and colours),
 *       src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (metal colours),
 *       src/main/java/net/dries007/tfc/client/ClientEventHandler.java @ c3b4d9536 (registerClientExtensions tints)
 */
package dev.tfp.core.fluid;

import dev.tfp.core.fluid.TfcFluid.Category;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fluid registry containers use (PAR-FLUID-01): every source fluid TFC registers in {@code TFCFluids} (the 28 molten
 * metals including {@code metal/unknown}, salt, spring and river water, the 20 {@code SimpleFluid}s and the 16 dye
 * fluids, in TFC's registration order), then the three vanilla fluids TFC uses. TFC's flowing variants are not here: a
 * container only ever holds a source fluid. Ids use our namespace ({@code tfc:} → {@code tfp:}, as datagen renames the
 * data). Immutable; any thread.
 *
 * <p>No fluid exists as a world block (NOT-PARITY NP-FLUID-01): these are container contents only.
 */
public final class TfcFluids {
    /** TFC {@code TFCFluids.ALPHA_MASK}. */
    public static final int ALPHA_MASK = 0xFF000000;
    /** The number of source fluids TFC registers in {@code TFCFluids} (28 + 3 + 20 + 16). */
    public static final int TFC_SOURCE_COUNT = 67;

    public static final String WATER = "minecraft:water";
    public static final String LAVA = "minecraft:lava";
    public static final String MILK = "minecraft:milk";

    /** TFC {@code Metal} colours, in {@code Metal} order (the 27 metals, then {@code UNKNOWN}). */
    private static final Object[][] METALS = {
        {"bismuth", 0xFF486B72}, {"bismuth_bronze", 0xFF418E4F}, {"black_bronze", 0xFF3B2636},
        {"bronze", 0xFF96892E}, {"brass", 0xFF7C5E33}, {"copper", 0xFFB64027}, {"gold", 0xFFDCBF1B},
        {"nickel", 0xFF4E4E3C}, {"rose_gold", 0xFFEB7137}, {"silver", 0xFF949495}, {"tin", 0xFF90A4BB},
        {"zinc", 0xFFBBB9C4}, {"sterling_silver", 0xFFAC927B}, {"wrought_iron", 0xFF989897},
        {"cast_iron", 0xFF989897}, {"pig_iron", 0xFF6A595C}, {"steel", 0xFF5F5F5F}, {"black_steel", 0xFF111111},
        {"blue_steel", 0xFF2D5596}, {"red_steel", 0xFF700503}, {"weak_steel", 0xFF111111},
        {"weak_blue_steel", 0xFF2D5596}, {"weak_red_steel", 0xFF700503}, {"high_carbon_steel", 0xFF5F5F5F},
        {"high_carbon_black_steel", 0xFF111111}, {"high_carbon_blue_steel", 0xFF2D5596},
        {"high_carbon_red_steel", 0xFF700503}, {"unknown", 0xFF2F2B27},
    };

    /** TFC {@code SimpleFluid}, in enum order, with its colour. */
    private static final Object[][] SIMPLE = {
        {"brine", 0xFFDCD3C9}, {"curdled_milk", 0xFFFFFBE8}, {"limewater", 0xFFB4B4B4}, {"lye", 0xFFFEFFDE},
        {"milk_vinegar", 0xFFFFFBE8}, {"olive_oil", 0xFF6A7537}, {"olive_oil_water", 0xFF4A4702},
        {"canola_oil", 0xFFBA8507}, {"canola_oil_water", 0xFF9F6B07}, {"tannin", 0xFF63594E}, {"tallow", 0xFFEDE9CF},
        {"vinegar", 0xFFC7C2AA}, {"beer", 0xFFC39E37}, {"cider", 0xFFB0AE32}, {"rum", 0xFF6E0123},
        {"sake", 0xFFB7D9BC}, {"vodka", 0xFFDCDCDC}, {"whiskey", 0xFF583719}, {"corn_whiskey", 0xFFD9C7B7},
        {"rye_whiskey", 0xFFC77D51},
    };

    /**
     * Vanilla 1.21.1 {@code DyeColor}, in enum order, with its {@code getTextureDiffuseColor()} (opaque ARGB), the tint TFC
     * gives its dye fluids.
     */
    private static final Object[][] DYES = {
        {"white", 0xFFF9FFFE}, {"orange", 0xFFF9801D}, {"magenta", 0xFFC74EBD}, {"light_blue", 0xFF3AB3DA},
        {"yellow", 0xFFFED83D}, {"lime", 0xFF80C71F}, {"pink", 0xFFF38BAA}, {"gray", 0xFF474F52},
        {"light_gray", 0xFF9D9D97}, {"cyan", 0xFF169C9C}, {"purple", 0xFF8932B8}, {"blue", 0xFF3C44AA},
        {"brown", 0xFF835432}, {"green", 0xFF5E7C16}, {"red", 0xFFB02E26}, {"black", 0xFF1D1D21},
    };

    /** NeoForge's water tint (also the default of TFC's salt water, whose real tint is the biome water colour). */
    private static final int WATER_TINT = ALPHA_MASK | 0x3F76E4;
    /** Lava and milk render their own texture untinted. */
    private static final int NO_TINT = 0xFFFFFFFF;
    private static final int LAVA_TEMPERATURE = 1300;
    private static final int DEFAULT_TEMPERATURE = 300;

    private static final List<TfcFluid> ALL;
    private static final Map<String, TfcFluid> BY_ID;

    static {
        List<TfcFluid> all = new ArrayList<>();
        for (Object[] m : METALS) {
            String name = (String) m[0];
            all.add(new TfcFluid("tfp:metal/" + name, Category.METAL, ALPHA_MASK | (int) m[1], LAVA_TEMPERATURE,
                    "fluid.tfc.metal." + name));
        }
        all.add(new TfcFluid("tfp:salt_water", Category.WATER, WATER_TINT, DEFAULT_TEMPERATURE,
                "fluid.tfc.salt_water"));
        all.add(new TfcFluid("tfp:spring_water", Category.WATER, ALPHA_MASK | 0x4ECBD7, DEFAULT_TEMPERATURE,
                "fluid.tfc.spring_water"));
        // RiverWaterFluid.getFluidType() is NeoForgeMod.WATER_TYPE: vanilla water's name, tint and temperature
        all.add(new TfcFluid("tfp:river_water", Category.WATER, WATER_TINT, DEFAULT_TEMPERATURE,
                "block.minecraft.water"));
        for (Object[] s : SIMPLE) {
            String name = (String) s[0];
            // ClientEventHandler: ALPHA_MASK | colour for transparent ones, the colour itself otherwise (all are opaque)
            all.add(new TfcFluid("tfp:" + name, Category.SIMPLE, ALPHA_MASK | (int) s[1], DEFAULT_TEMPERATURE,
                    "fluid.tfc." + name));
        }
        for (Object[] d : DYES) {
            String name = d[0] + "_dye";
            all.add(new TfcFluid("tfp:" + name, Category.DYE, (int) d[1], DEFAULT_TEMPERATURE, "fluid.tfc." + name));
        }
        all.add(new TfcFluid(WATER, Category.VANILLA, WATER_TINT, DEFAULT_TEMPERATURE, "block.minecraft.water"));
        all.add(new TfcFluid(LAVA, Category.VANILLA, NO_TINT, LAVA_TEMPERATURE, "block.minecraft.lava"));
        all.add(new TfcFluid(MILK, Category.VANILLA, NO_TINT, DEFAULT_TEMPERATURE, "fluid_type.minecraft.milk"));
        ALL = List.copyOf(all);
        Map<String, TfcFluid> byId = new LinkedHashMap<>();
        for (TfcFluid f : ALL) {
            if (byId.put(f.id(), f) != null) {
                throw new IllegalStateException("duplicate fluid " + f.id());
            }
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private TfcFluids() {
    }

    /** Every fluid, TFC's sources in registration order, then water, lava and milk. */
    public static List<TfcFluid> all() {
        return ALL;
    }

    /** The fluid with this id; TFC's own {@code tfc:} namespace is accepted for ours. */
    public static Optional<TfcFluid> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.startsWith("tfc:") ? "tfp:" + id.substring(4) : id;
        return Optional.ofNullable(BY_ID.get(key));
    }

    /**
     * A fluid named by a person (a debug command): an exact id ({@code tfc:} accepted), a bare path tried as {@code tfp:}
     * then {@code minecraft:}, or a {@code tfp:}/{@code tfc:} path that is a vanilla fluid ({@code tfp:milk} → milk).
     */
    public static Optional<TfcFluid> resolve(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String id = input.trim().toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon < 0) {
            return get("tfp:" + id).or(() -> get("minecraft:" + id));
        }
        Optional<TfcFluid> exact = get(id);
        String ns = id.substring(0, colon);
        if (exact.isPresent() || !(ns.equals("tfp") || ns.equals("tfc"))) {
            return exact;
        }
        return get("minecraft:" + id.substring(colon + 1));
    }

    /** The TFC source fluids only (no vanilla fluids). */
    public static List<TfcFluid> tfcSources() {
        return ALL.stream().filter(f -> f.category() != Category.VANILLA).toList();
    }
}
