/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/damage_type/*.json @ c3b4d9536 (copied, renamed)
 *       src/generated/resources/data/minecraft/tags/damage_type/no_knockback.json @ c3b4d9536 (copied, renamed)
 *       src/main/java/net/dries007/tfc/common/TFCDamageTypes.java @ c3b4d9536 (the registered keys, cross-checked)
 */
package dev.tfp.datagen.damagetypes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-SURV-10: TFC's five damage types ({@code dehydration}, {@code grill}, {@code lobster_trap}, {@code pluck},
 * {@code pot}) as {@code tfp:} entries of the server's {@code damage_type} registry, and TFC's addition to the vanilla
 * {@code minecraft:no_knockback} tag beside them.
 *
 * <p>They go into the bootstrap datapack {@code tfp_seasonal_biomes} (the one generated datapack {@code TfpBootstrap}
 * registers, as PAR-INFRA-30 and PAR-PROC-24 do), so the registry holds them from the first world load. The JSON is TFC's
 * after the usual {@code tfc:} to {@code tfp:} rename; the {@code message_id} ({@code tfc.dehydration} and so on) has no
 * colon and stays as TFC's, so the death messages keep TFC's {@code death.attack.tfc.*} lang keys.
 *
 * <p>A checkout with {@code TFCDamageTypes.java} (the full one) must declare exactly the shipped set; the tag may name
 * only shipped types or vanilla ones, else the datapack would fail to load.
 */
public final class DamageTypes {
    public static final String SOURCE = TfcCheckout.GENERATED + "data/tfc/damage_type/";
    public static final String TAG_SOURCE = TfcCheckout.GENERATED + "data/minecraft/tags/damage_type/no_knockback.json";
    public static final String JAVA = "src/main/java/net/dries007/tfc/common/TFCDamageTypes.java";
    /** The bootstrap datapack ({@code SeasonalBiomes.OUT}, {@code TfpBootstrap.SEASONAL_DATAPACK_RESOURCE}). */
    public static final String DATAPACK = "datapack/tfp_seasonal_biomes/";
    public static final String TARGET = DATAPACK + "data/" + Json.TO_NS + "/damage_type/";
    public static final String TAG_TARGET = DATAPACK + "data/minecraft/tags/damage_type/no_knockback.json";
    private static final Pattern KEY = Pattern.compile(
        "ResourceKey\\.create\\(\\s*Registries\\.DAMAGE_TYPE\\s*,\\s*Helpers\\.identifier\\(\"([a-z0-9_/]+)\"\\)\\s*\\)");
    private static final Set<String> SCALING = Set.of("never", "when_caused_by_living_non_player", "always");

    private DamageTypes() {
    }

    /** The shipped type names (without namespace) and whether the tag was shipped. */
    public record Result(List<String> types, boolean tag) {
    }

    public static Result run(TfcCheckout tfc, OutputTree out) {
        List<String> types = new ArrayList<>();
        for (String src : tfc.list(SOURCE)) {
            if (!src.endsWith(".json")) {
                continue;
            }
            String name = src.substring(SOURCE.length(), src.length() - ".json".length());
            JsonElement type = Json.rename(Json.parse(tfc.readString(src), src));
            check(name, type);
            out.put(TARGET + name + ".json", Json.write(type));
            types.add(name);
        }
        if (tfc.exists(JAVA)) {
            Set<String> declared = new TreeSet<>();
            Matcher m = KEY.matcher(tfc.readString(JAVA));
            while (m.find()) {
                declared.add(m.group(1));
            }
            if (!declared.equals(new TreeSet<>(types))) {
                throw new DatagenException("TFC damage types: TFCDamageTypes declares " + declared + ", the generated JSON is "
                    + types);
            }
        }
        boolean tag = false;
        if (tfc.exists(TAG_SOURCE)) {
            JsonElement t = Json.rename(Json.parse(tfc.readString(TAG_SOURCE), TAG_SOURCE));
            for (JsonElement v : t.getAsJsonObject().getAsJsonArray("values")) {
                String id = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (id.startsWith(Json.TO_NS + ":") && !types.contains(id.substring(Json.TO_NS.length() + 1))) {
                    throw new DatagenException("minecraft:no_knockback names " + id + ", which is not a shipped damage type");
                }
            }
            out.put(TAG_TARGET, Json.write(t));
            tag = true;
        }
        return new Result(List.copyOf(types), tag);
    }

    /** A damage type has a {@code message_id}, a valid {@code scaling} and an {@code exhaustion}. */
    static void check(String name, JsonElement type) {
        if (!type.isJsonObject()) {
            throw new DatagenException("damage type " + name + " is not an object");
        }
        JsonObject o = type.getAsJsonObject();
        if (!o.has("message_id") || !o.has("exhaustion") || !o.has("scaling")
            || !SCALING.contains(o.get("scaling").getAsString())) {
            throw new DatagenException("damage type " + name + " lacks message_id, exhaustion or a valid scaling: " + o);
        }
    }
}
