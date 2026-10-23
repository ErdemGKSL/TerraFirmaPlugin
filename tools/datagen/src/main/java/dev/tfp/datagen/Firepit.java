/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (createBlockStateDefinition,
 *       registerDefaultState, the LIT / AXIS / SMOKE_LEVEL properties),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java @ c3b4d9536 (SMOKE_LEVEL range),
 *       src/main/resources/assets/tfc/blockstates/firepit.json @ c3b4d9536 (the lit x axis variants),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (strength, sound, lightLevel(litBlockEmission(15))),
 *       src/main/resources/data/tfc/loot_table/blocks/firepit.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceLoot;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceSpecs;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3-HEAT-03 (block data slice): the TFC firepit as one CraftEngine block entry, {@code tfp:firepit}. The states are the
 * product of the three properties that {@code FirepitBlock} adds to its state definition: {@code lit} (boolean),
 * {@code smoke_level} (0 to 4, from {@code TFCBlockStateProperties}) and {@code axis} (the horizontal axis, x or z). That
 * is 2 x 5 x 2 = 20 server states. Every variant key names all three properties, so no key overlaps another.
 *
 * <p>The properties, their defaults and the state definition are read from {@code FirepitBlock.java}, and an unknown
 * property there is an error. The lit and axis combinations must match the TFC blockstate {@code firepit.json}, which
 * names only {@code lit} and {@code axis}: TFC draws the smoke level with particles, not with a model. The same
 * combinations are the only ones the visual looks at, so all the smoke levels of a state share its appearance.
 *
 * <p>{@code axis} is a CraftEngine {@code string} with the values {@code x} and {@code z}, not CraftEngine's {@code axis}
 * type, which also has {@code y} and would double the state count with states TFC never has. Whether CraftEngine's
 * hardcoded rotation behaviour for the name {@code axis} accepts a string property is not verified yet.
 *
 * <p><b>Carrier.</b> Each of the two appearances ({@code lit}, {@code unlit}) sits on the vanilla state the hitbox carrier
 * selector picks for TFC's shape (PAR-INFRA-13, {@link #carrier}): for the firepit {@code daylight_detector} (16x6x16
 * against TFC's 2 px plate plus a 12x4x12 core, IoU 0.71). There is no model of our own yet, so the look is the carrier's
 * vanilla look, a placeholder until the firepit family's models exist (M3-HEAT-03 open items).
 *
 * <p><b>Spec.</b> The entry is a {@link DeviceBlockSpec} built by {@link DeviceSpecs#firepitFamily} and written by
 * {@link DeviceYaml#writeLiteral} (PAR-INFRA-19), with TFC's settings ({@link DeviceBlockSpec.Settings#fromTfc}: strength
 * 0.4/0.4, nether wart sounds, pickaxe), TFC's loot ({@link DeviceLoot#fromTfc}: wood ash) and TFC's lit light
 * ({@code lightLevel(litBlockEmission(15))}, read by {@link #litLight}: 15 when lit, 0 otherwise). The grill and the pot
 * are written the same way ({@link #spec}).
 *
 * <p>The output is {@code craftengine/tfp/devices/firepit.yml}, outside the CraftEngine {@code configuration/} folder, so
 * CraftEngine does not load it yet. The same holds for the family data, see {@link FruitFamilies}. The behaviour
 * (fuel, temperature, the block entity, the placed block) is not in this entry.
 */
final class Firepit {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java";
    static final String PROPERTIES = "src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/firepit.json";
    static final String OUTPUT = AssetPipeline.PACK + "devices/firepit.yml";
    static final String BLOCK_ID = Json.TO_NS + ":firepit";

    /** The properties of the TFC blockstate, which has a variant for each of their combinations. */
    static final Set<String> VISUAL_PROPERTIES = Set.of("lit", "axis");
    static final String LIT_APPEARANCE = "lit";
    static final String UNLIT_APPEARANCE = "unlit";

    static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";

    /** A declared constant of the state definition: {@code LIT = BlockStateProperties.LIT;} (owner and member). */
    private static final Pattern DECL = Pattern.compile("public static final [^=;]+ (\\w+) = (\\w+)\\.(\\w+);");
    private static final Pattern BUILDER = Pattern.compile("builder\\.add\\(([^)]*)\\);");
    private static final Pattern DEFAULTS = Pattern.compile("registerDefaultState\\(getStateDefinition\\(\\)\\.any\\(\\)((?:\\.setValue\\([^)]*\\))*)\\);");
    private static final Pattern SET_VALUE = Pattern.compile("\\.setValue\\((\\w+), ([^)]*)\\)");
    private static final Pattern SMOKE_LEVEL = Pattern.compile("SMOKE_LEVEL = IntegerProperty\\.create\\(\"(\\w+)\", (\\d+), (\\d+)\\)");
    private static final Pattern LIT_EMISSION = Pattern.compile("\\.lightLevel\\(litBlockEmission\\((\\d+)\\)\\)");

    private Firepit() {
    }

    /** Writes the firepit entry. Returns the number of server states. Fails when TFC's sources do not match. */
    static int run(TfcCheckout tfc, OutputTree out) {
        List<DeviceProperty> props = props(tfc);
        List<Map<String, String>> states = DeviceBlockSpec.combinations(props);
        checkAgainstTfc(tfc, states);
        out.put(OUTPUT, DeviceYaml.writeLiteral(spec(tfc, BLOCK_ID, props)));
        return states.size();
    }

    /**
     * The properties of the state definition, in {@code builder.add} order. Fails when the source has no definition or
     * a property has no known type, no default, or no declaration.
     */
    static List<DeviceProperty> props(TfcCheckout tfc) {
        if (!tfc.exists(SOURCE)) {
            throw new DatagenException("TFC checkout has no " + SOURCE);
        }
        if (!tfc.exists(PROPERTIES)) {
            throw new DatagenException("TFC checkout has no " + PROPERTIES);
        }
        String src = tfc.readString(SOURCE);
        Map<String, String[]> decls = new TreeMap<>();
        Matcher d = DECL.matcher(src);
        while (d.find()) {
            decls.put(d.group(1), new String[] {d.group(2), d.group(3)});
        }
        Matcher b = BUILDER.matcher(src);
        if (!b.find()) {
            throw new DatagenException(SOURCE + ": no builder.add(...) of the block state properties");
        }
        Map<String, String> defaults = defaults(src);
        String properties = tfc.readString(PROPERTIES);

        List<DeviceProperty> out = new ArrayList<>();
        for (String part : b.group(1).split(",")) {
            String constant = part.trim();
            String[] decl = decls.get(constant);
            if (decl == null) {
                throw new DatagenException(SOURCE + ": no declaration of " + constant);
            }
            String dflt = defaults.get(constant);
            if (dflt == null) {
                throw new DatagenException(SOURCE + ": " + constant + " has no registerDefaultState value");
            }
            out.add(prop(decl[0] + "." + decl[1], constant, dflt, properties));
        }
        return out;
    }

    /**
     * One property from its declared owner and member, with its default. {@code propertiesText} is the
     * {@code TFCBlockStateProperties} source, where {@code SMOKE_LEVEL} is declared. Unknown owners are an error.
     */
    static DeviceProperty prop(String owner, String constant, String dflt, String propertiesText) {
        return switch (owner) {
            case "BlockStateProperties.LIT" -> DeviceProperty.bool("lit", Boolean.parseBoolean(bool(dflt, constant)));
            case "BlockStateProperties.HORIZONTAL_AXIS" -> DeviceProperty.names("axis", List.of("x", "z"), axis(dflt, constant));
            case "TFCBlockStateProperties.SMOKE_LEVEL" -> smokeLevel(propertiesText, dflt, constant);
            default -> throw new DatagenException(SOURCE + ": " + constant + " is " + owner
                + ", which datagen does not know");
        };
    }

    /** The {@code SMOKE_LEVEL} integer property: its name and range as {@code TFCBlockStateProperties} declares them. */
    static DeviceProperty smokeLevel(String propertiesText, String dflt, String constant) {
        Matcher m = SMOKE_LEVEL.matcher(propertiesText);
        if (!m.find()) {
            throw new DatagenException(PROPERTIES + ": no SMOKE_LEVEL = IntegerProperty.create(...)");
        }
        int lo = Integer.parseInt(m.group(2));
        int hi = Integer.parseInt(m.group(3));
        if (hi < lo) {
            throw new DatagenException(PROPERTIES + ": SMOKE_LEVEL range " + lo + " to " + hi + " is empty");
        }
        int d = number(dflt, constant);
        if (d < lo || d > hi) {
            throw new DatagenException(SOURCE + ": default of " + constant + " is " + d + ", outside " + lo + " to " + hi);
        }
        return DeviceProperty.range(m.group(1), lo, hi, d);
    }

    /** The registerDefaultState values of the source, by constant name. Fails when the call is missing. */
    static Map<String, String> defaults(String src) {
        Matcher m = DEFAULTS.matcher(src);
        if (!m.find()) {
            throw new DatagenException(SOURCE + ": no registerDefaultState(...any().setValue...) call");
        }
        Map<String, String> out = new LinkedHashMap<>();
        Matcher v = SET_VALUE.matcher(m.group(1));
        while (v.find()) {
            out.put(v.group(1), v.group(2).trim());
        }
        return out;
    }

    static String bool(String dflt, String constant) {
        if (!dflt.equals("true") && !dflt.equals("false")) {
            throw new DatagenException(SOURCE + ": default of " + constant + " is " + dflt + ", not a boolean");
        }
        return dflt;
    }

    static String axis(String dflt, String constant) {
        if (dflt.endsWith("Axis.X")) {
            return "x";
        }
        if (dflt.endsWith("Axis.Z")) {
            return "z";
        }
        throw new DatagenException(SOURCE + ": default of " + constant + " is " + dflt + ", not a horizontal axis");
    }

    static int number(String dflt, String constant) {
        try {
            return Integer.parseInt(dflt);
        } catch (NumberFormatException e) {
            throw new DatagenException(SOURCE + ": default of " + constant + " is " + dflt + ", not an int");
        }
    }

    /**
     * The properties of a device class that inherits the firepit's state definition. Fails when the class does not extend
     * {@code FirepitBlock} or declares a state definition of its own, which datagen does not read for devices.
     */
    static List<DeviceProperty> inheritedProps(TfcCheckout tfc, String source, String className) {
        if (!tfc.exists(source)) {
            throw new DatagenException("TFC checkout has no " + source);
        }
        String src = tfc.readString(source);
        if (!Pattern.compile("class\\s+" + className + "\\s+extends\\s+FirepitBlock\\b").matcher(src).find()) {
            throw new DatagenException(source + ": " + className + " does not extend FirepitBlock, so its state is not the firepit's");
        }
        if (src.contains("createBlockStateDefinition") || src.contains("registerDefaultState")) {
            throw new DatagenException(source + " declares its own block state definition, which datagen does not read");
        }
        return props(tfc);
    }

    /**
     * Each state must match at least one part of a TFC multipart blockstate. Parts name only {@link #VISUAL_PROPERTIES}, and
     * every one of those names must be used, so a TFC change shows up here. Fails otherwise.
     */
    static void checkMultipart(TfcCheckout tfc, String blockstate, List<Map<String, String>> states) {
        if (!tfc.exists(blockstate)) {
            throw new DatagenException("TFC checkout has no " + blockstate);
        }
        JsonObject root = JsonParser.parseString(tfc.readString(blockstate)).getAsJsonObject();
        if (!root.has("multipart")) {
            throw new DatagenException(blockstate + " has no multipart list");
        }
        List<JsonObject> whens = new ArrayList<>();
        Set<String> names = new TreeSet<>();
        for (var part : root.getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().has("when") ? part.getAsJsonObject().getAsJsonObject("when") : new JsonObject();
            for (String name : when.keySet()) {
                if (!VISUAL_PROPERTIES.contains(name)) {
                    throw new DatagenException(blockstate + ": part names " + name + ", expected " + VISUAL_PROPERTIES);
                }
                names.add(name);
            }
            whens.add(when);
        }
        if (!names.equals(new TreeSet<>(VISUAL_PROPERTIES))) {
            throw new DatagenException(blockstate + ": parts name " + names + ", expected " + VISUAL_PROPERTIES);
        }
        for (Map<String, String> s : states) {
            boolean matched = false;
            for (JsonObject when : whens) {
                matched |= matches(when, s);
            }
            if (!matched) {
                throw new DatagenException(blockstate + " has no part for the state " + describe(s));
            }
        }
    }

    private static boolean matches(JsonObject when, Map<String, String> state) {
        for (String name : when.keySet()) {
            if (!String.valueOf(when.get(name).getAsString()).equals(state.get(name))) {
                return false;
            }
        }
        return true;
    }

    /** A state as {@code name=value} pairs in property order, as the refusals print it. */
    static String describe(Map<String, String> state) {
        List<String> parts = new ArrayList<>();
        state.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join(",", parts);
    }

    /**
     * Each (lit, axis) pair of the states must be a variant of the TFC blockstate, and the blockstate must name exactly
     * {@link #VISUAL_PROPERTIES}. Fails otherwise, so a TFC change shows up here.
     */
    static void checkAgainstTfc(TfcCheckout tfc, List<Map<String, String>> states) {
        if (!tfc.exists(BLOCKSTATE)) {
            throw new DatagenException("TFC checkout has no " + BLOCKSTATE);
        }
        JsonObject variants = JsonParser.parseString(tfc.readString(BLOCKSTATE)).getAsJsonObject().getAsJsonObject("variants");
        Set<String> keys = new TreeSet<>();
        Set<String> names = new TreeSet<>();
        for (String key : variants.keySet()) {
            keys.add(sortedKey(key));
            for (String kv : key.split(",")) {
                names.add(kv.split("=", 2)[0]);
            }
        }
        if (!names.equals(new TreeSet<>(VISUAL_PROPERTIES))) {
            throw new DatagenException(BLOCKSTATE + ": variant properties " + names + ", expected " + VISUAL_PROPERTIES);
        }
        for (Map<String, String> s : states) {
            String key = "axis=" + s.get("axis") + ",lit=" + s.get("lit");
            if (!keys.contains(key)) {
                throw new DatagenException(BLOCKSTATE + " has no variant " + key + " for the firepit");
            }
        }
    }

    /** A variant key with its properties in alphabetical order, so keys compare regardless of the order written. */
    static String sortedKey(String key) {
        List<String> parts = new ArrayList<>(List.of(key.split(",")));
        parts.sort(null);
        return String.join(",", parts);
    }

    /** The carrier the hitbox selector assigned to one appearance of a firepit-family block (its vanilla look). */
    static String carrier(String blockId, String appearance) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(blockId, appearance);
        if (a.mode() != HitboxAllocation.Mode.VANILLA) {
            throw new DatagenException(blockId + "#" + appearance + ": the carrier is " + a.mode()
                + ", the firepit family writes a plain vanilla look");
        }
        return a.state();
    }

    /**
     * The spec of the firepit-family block {@code blockId} ({@code tfp:firepit}, {@code tfp:grill}, {@code tfp:pot}): the
     * given properties, the carriers the hitbox selector chose (vanilla look: placeholders), and TFC's settings, loot and
     * lit light of the TFC block of the same name.
     */
    static DeviceBlockSpec spec(TfcCheckout tfc, String blockId, List<DeviceProperty> props) {
        String block = blockId.substring(blockId.indexOf(':') + 1);
        return DeviceSpecs.firepitFamily(blockId, props,
            DeviceAppearance.Carrier.state(carrier(blockId, LIT_APPEARANCE)),
            DeviceAppearance.Carrier.state(carrier(blockId, UNLIT_APPEARANCE)),
            DeviceBlockSpec.Settings.fromTfc(tfc, block), DeviceLoot.fromTfc(tfc, block), litLight(tfc, block));
    }

    /**
     * The light the lit TFC block {@code tfc:<block>} emits: its {@code TFCBlocks} registration must set
     * {@code lightLevel(litBlockEmission(N))} (lit: N, unlit: 0); returns N. Fails otherwise.
     */
    static int litLight(TfcCheckout tfc, String block) {
        if (!tfc.exists(TFC_BLOCKS)) {
            throw new DatagenException("TFC checkout has no " + TFC_BLOCKS);
        }
        Pattern registration = Pattern.compile(".*\\bregister(NoItem)?\\(\"" + Pattern.quote(block) + "\",.*");
        Integer light = null;
        for (String line : tfc.readString(TFC_BLOCKS).split("\n")) {
            if (registration.matcher(line).matches()) {
                Matcher m = LIT_EMISSION.matcher(line);
                if (!m.find()) {
                    throw new DatagenException(TFC_BLOCKS + ": tfc:" + block + " has no lightLevel(litBlockEmission(N))");
                }
                light = Integer.parseInt(m.group(1));
            }
        }
        if (light == null) {
            throw new DatagenException(TFC_BLOCKS + ": no registration of tfc:" + block);
        }
        return light;
    }
}
