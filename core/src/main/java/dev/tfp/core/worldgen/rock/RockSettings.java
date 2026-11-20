/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/settings/RockSettings.java @ c3b4d9536 (record and codec)
 */
package dev.tfp.core.worldgen.rock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * One rock's blocks and flags (TFC's {@code worldgen/rock_settings/<rock>.json}, exported by datagen to
 * {@code data/tfp/tfp/worldgen/rock_settings/}): the block ids are our {@code tfp:} ids. Immutable.
 */
public record RockSettings(String raw, String hardened, String gravel, String cobble, String sand, String sandstone,
                           Optional<String> spike, Optional<String> loose, Optional<String> mossyLoose,
                           Optional<Boolean> karst, Optional<Boolean> mafic) {
    /** The exported directory, below the data root. */
    public static final String DIR = "tfp/tfp/worldgen/rock_settings/";

    /** {@code karst} with TFC's default (false when absent). */
    public boolean isKarst() {
        return karst.orElse(false);
    }

    /** {@code mafic} with TFC's default (false when absent). */
    public boolean isMafic() {
        return mafic.orElse(false);
    }

    /**
     * Reads one file by TFC's codec: the six blocks required, the rest optional.
     *
     * @throws IllegalArgumentException when a required field is missing
     */
    public static RockSettings parse(String json) {
        final JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return new RockSettings(req(o, "raw"), req(o, "hardened"), req(o, "gravel"), req(o, "cobble"), req(o, "sand"),
            req(o, "sandstone"), opt(o, "spike").map(JsonElement::getAsString), opt(o, "loose").map(JsonElement::getAsString),
            opt(o, "mossy_loose").map(JsonElement::getAsString), opt(o, "karst").map(JsonElement::getAsBoolean),
            opt(o, "mafic").map(JsonElement::getAsBoolean));
    }

    /**
     * The settings of each rock id ({@code tfc:granite} reads {@code granite.json}), in the given order.
     *
     * @param read reads a path below the data root
     * @throws IllegalArgumentException when a rock has no file (TFC's registry file codec fails the same way)
     */
    public static Map<String, RockSettings> load(List<String> rockIds, Function<String, Optional<String>> read) {
        final Map<String, RockSettings> out = new LinkedHashMap<>();
        for (String id : rockIds) {
            final String name = id.substring(id.indexOf(':') + 1);
            final String json = read.apply(DIR + name + ".json")
                .orElseThrow(() -> new IllegalArgumentException("No rock settings for " + id + " (" + DIR + name + ".json)"));
            out.put(id, parse(json));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static String req(JsonObject o, String key) {
        final JsonElement e = o.get(key);
        if (e == null) {
            throw new IllegalArgumentException("No key " + key + " in rock settings");
        }
        return e.getAsString();
    }

    private static Optional<JsonElement> opt(JsonObject o, String key) {
        return Optional.ofNullable(o.get(key));
    }
}
