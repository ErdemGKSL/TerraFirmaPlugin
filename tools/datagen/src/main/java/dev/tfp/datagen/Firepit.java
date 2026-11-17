/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (createBlockStateDefinition,
 *       registerDefaultState, the LIT / AXIS / SMOKE_LEVEL properties),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java @ c3b4d9536 (SMOKE_LEVEL range),
 *       src/main/resources/assets/tfc/blockstates/firepit.json @ c3b4d9536 (the lit x axis variants, axis z turned y 90),
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
import dev.tfp.datagen.hitbox.HitboxDemands;
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
 * <p><b>Carrier.</b> PAR-HEAT-23: one appearance per (lit, axis) pair ({@code lit_x}, {@code lit_z}, {@code unlit_x},
 * {@code unlit_z}), each on its own MODEL carrier from the hitbox selector ({@link #modelCarrier}: four powers of
 * {@code daylight_detector}, 16x6x16 against TFC's 2 px plate plus a 12x4x12 core, IoU 0.71) drawn with TFC's
 * {@code firepit_lit} / {@code firepit_unlit} model; the axis-z pair turns it y 90 as TFC's blockstate does
 * ({@code model: {path, y: 90}}, {@link DeviceSpecs#turnModels}). TFC crafts the daylight detector, so each carrier hides
 * real detectors at its power as the nearest free power ({@code block_state_mappings}; the detector's model depends only
 * on {@code inverted}, so they look the same). The grill and the pot still take the vanilla look of their carriers
 * ({@link #spec}).
 *
 * <p><b>Spec.</b> The entry is a {@link DeviceBlockSpec} built by {@link DeviceSpecs#firepit} and written through
 * {@link DeviceYaml#literal} (PAR-INFRA-19; PAR-HEAT-23 adds the axis-z turn), with TFC's settings ({@link DeviceBlockSpec.Settings#fromTfc}: strength
 * 0.4/0.4, nether wart sounds, pickaxe), TFC's loot ({@link DeviceLoot#fromTfc}: wood ash) and TFC's lit light
 * ({@code lightLevel(litBlockEmission(15))}, read by {@link #litLight}: 15 when lit, 0 otherwise). The grill and the pot
 * are written through {@link DeviceSpecs#firepitFamily} ({@link #spec}).
 *
 * <p><b>Outputs.</b> {@code craftengine/tfp/devices/firepit.yml} is the literal spec without the behaviour, outside
 * {@code configuration/}, so CraftEngine does not load it (the firepit-family fixture {@code DeviceSpecParityTest}
 * checks). {@link #loadedYaml} is the entry CraftEngine is to load at {@link #LOADED_OUTPUT} (PAR-HEAT-06): the same spec
 * through the device template {@code tfp:block/device}, which adds the {@code tfp:device} behaviour, so the placed block
 * routes to the firepit device kind ({@code paper/firepit}); {@link #run} writes it (PAR-HEAT-19).
 *
 * <p><b>Models.</b> TFC's {@code firepit_lit} and {@code firepit_unlit} block models (the variants of the TFC blockstate,
 * {@link #models}) are copied into the pack by {@link AssetPipeline} and drawn on the carriers above. TFC's fuel logs
 * ({@code FirepitBlockEntityRenderer}) are not drawn (ledger divergence).
 */
final class Firepit {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java";
    static final String PROPERTIES = "src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/firepit.json";
    static final String OUTPUT = AssetPipeline.PACK + "devices/firepit.yml";
    /**
     * PAR-HEAT-06: where the loaded entry goes ({@link #loadedYaml}, template {@code tfp:block/device} with the
     * {@code tfp:device} behaviour). Written by {@link #run} since PAR-HEAT-19.
     */
    static final String LOADED_OUTPUT = AssetPipeline.PACK + "configuration/devices/firepit.yml";
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
        DeviceBlockSpec spec = firepitSpec(tfc, props);
        out.put(OUTPUT, literalYaml(spec));
        out.put(LOADED_OUTPUT, templatedYaml(loaded(spec))); // PAR-HEAT-19: the entry CraftEngine loads
        return states.size();
    }

    /**
     * PAR-HEAT-06: the firepit spec with the {@code tfp:device} behaviour, so the block routes to the firepit device kind.
     * Everything else (states, carriers, TFC's settings, loot and lit light) is the spec's.
     */
    static String loadedYaml(TfcCheckout tfc) {
        return templatedYaml(loaded(firepitSpec(tfc, props(tfc))));
    }

    /** PAR-HEAT-23: the literal entry with the axis-z models turned y 90 (TFC's blockstate). */
    static String literalYaml(DeviceBlockSpec spec) {
        return Yaml.write(DeviceYaml.HEADER, DeviceSpecs.turnModels(DeviceYaml.literal(spec), spec.id(), Firepit::rotation));
    }

    /** PAR-HEAT-23: the templated entry with the axis-z models turned y 90 (TFC's blockstate). */
    static String templatedYaml(DeviceBlockSpec spec) {
        return Yaml.write(DeviceYaml.HEADER, DeviceSpecs.turnModels(DeviceYaml.templated(spec), spec.id(), Firepit::rotation));
    }

    /** PAR-HEAT-23: the blockstate y of a firepit appearance ({@code <lit|unlit>_<axis>}). */
    static int rotation(String appearance) {
        return HitboxDemands.firepitRotation(appearance.substring(appearance.lastIndexOf('_') + 1));
    }

    /**
     * PAR-HEAT-23: the firepit drawn with TFC's models: four appearances ({@code lit_x}, {@code lit_z}, {@code unlit_x},
     * {@code unlit_z}), each on the MODEL carrier the hitbox selector agreed on, with the carriers' hide mappings (real
     * daylight detectors at a carrier power are sent with the nearest free power), and TFC's settings, loot and lit light.
     */
    static DeviceBlockSpec firepitSpec(TfcCheckout tfc, List<DeviceProperty> props) {
        Map<String, String> hides = new TreeMap<>();
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : HitboxDemands.FIREPIT_AXES) {
                HitboxAllocation.Assignment a = modelCarrier(lit, axis);
                if (a.hideTarget() != null) {
                    hides.put(a.state(), a.hideTarget());
                }
            }
        }
        return DeviceSpecs.firepit(BLOCK_ID, props, (lit, axis) -> DeviceAppearance.Carrier.state(modelCarrier(lit, axis).state()),
            HitboxDemands::firepitModel, DeviceBlockSpec.Settings.fromTfc(tfc, "firepit"), DeviceLoot.fromTfc(tfc, "firepit"),
            litLight(tfc, "firepit"), hides);
    }

    /** PAR-HEAT-23: the carrier of the firepit's (lit, axis) appearance; must be MODEL mode with TFC's model. */
    static HitboxAllocation.Assignment modelCarrier(boolean lit, String axis) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(BLOCK_ID, HitboxDemands.firepitAppearance(lit, axis));
        if (a.mode() != HitboxAllocation.Mode.MODEL) {
            throw new DatagenException(BLOCK_ID + "#" + HitboxDemands.firepitAppearance(lit, axis) + ": the carrier is "
                + a.mode() + ", the firepit draws TFC's model on it");
        }
        return a;
    }

    static DeviceBlockSpec loaded(DeviceBlockSpec spec) {
        return new DeviceBlockSpec(spec.id(), spec.properties(), spec.appearances(), spec.variants(), spec.settings(),
            spec.loot(), true, spec.blockItemLangKey(), spec.renderItems(), spec.stateMappings());
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

    /**
     * The block models of the TFC blockstate's variants ({@code block/firepit_lit}, {@code block/firepit_unlit}; no
     * namespace), in variant order without duplicates. Fails when a variant is not a TFC model or the blockstate is missing.
     */
    static List<String> models(TfcCheckout tfc) {
        if (!tfc.exists(BLOCKSTATE)) {
            throw new DatagenException("TFC checkout has no " + BLOCKSTATE);
        }
        JsonObject variants = JsonParser.parseString(tfc.readString(BLOCKSTATE)).getAsJsonObject().getAsJsonObject("variants");
        Set<String> models = new java.util.LinkedHashSet<>();
        for (String key : variants.keySet()) {
            var v = variants.get(key);
            if (!v.isJsonObject() || !v.getAsJsonObject().has("model")) {
                throw new DatagenException(BLOCKSTATE + ": variant " + key + " is not one model");
            }
            String model = v.getAsJsonObject().get("model").getAsString();
            if (!model.startsWith(Json.FROM_NS + ":block/")) {
                throw new DatagenException(BLOCKSTATE + ": variant " + key + " model " + model + " is not a TFC block model");
            }
            models.add(model.substring(Json.FROM_NS.length() + 1));
        }
        return new ArrayList<>(models);
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
     * The vanilla-look spec of a firepit-family block {@code blockId} ({@code tfp:grill}, {@code tfp:pot}; the firepit itself
     * is {@link #firepitSpec}): the
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
