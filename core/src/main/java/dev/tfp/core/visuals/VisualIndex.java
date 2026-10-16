/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.config.TfpConfig.Profile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The shipped {@code visuals/index.json} (datagen M1-DGN-04): pool use of every precomputed allocation.
 * Immutable and thread-safe.
 *
 * @param solidReserveWarning the reserve target datagen checked against
 * @param solidPool           family → CE {@code solid} pool size datagen assumed
 * @param allocations         every shipped allocation
 */
public record VisualIndex(int solidReserveWarning, Map<String, Integer> solidPool, List<Allocation> allocations) {
    public VisualIndex {
        solidPool = Map.copyOf(solidPool);
        allocations = List.copyOf(allocations);
    }

    /**
     * One allocation.
     *
     * @param solidUsed CE {@code auto_state: solid} slots our blocks take (our demand on the shared pool)
     * @param solidFree what datagen expected to stay free for other packs
     */
    public record Allocation(String family, Profile profile, boolean netherEnd, String dir, int solidUsed, int solidFree) {
        public VisualSelection selection() {
            return new VisualSelection(family, profile, netherEnd);
        }
    }

    /** @throws IllegalArgumentException if the JSON is not an index of format 1 */
    public static VisualIndex parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int format = root.get("format").getAsInt();
            if (format != 1) {
                throw new IllegalArgumentException("unsupported visuals/index.json format " + format);
            }
            Map<String, Integer> pools = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("families").entrySet()) {
                pools.put(e.getKey(), e.getValue().getAsJsonObject().get("solid_pool").getAsInt());
            }
            List<Allocation> list = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("allocations")) {
                JsonObject a = el.getAsJsonObject();
                String profileId = a.get("profile").getAsString();
                Profile profile = Profile.byId(profileId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown profile " + profileId));
                list.add(new Allocation(a.get("family").getAsString(), profile, a.get("nether_end").getAsBoolean(),
                        a.get("dir").getAsString(), a.get("solid_used").getAsInt(), a.get("solid_free").getAsInt()));
            }
            int reserve = root.has("solid_reserve_warning") ? root.get("solid_reserve_warning").getAsInt() : 500;
            return new VisualIndex(reserve, pools, list);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException iae && !(e instanceof NumberFormatException)) {
                throw iae;
            }
            throw new IllegalArgumentException("malformed visuals/index.json: " + e, e);
        }
    }

    public Optional<Allocation> find(VisualSelection s) {
        for (Allocation a : allocations) {
            if (a.family().equals(s.family()) && a.profile() == s.profile() && a.netherEnd() == s.netherEnd()) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    /** Solid-pool demand of every profile shipped for {@code family} with the same Nether/End setting, in profile order. */
    public Map<Profile, Integer> solidDemand(String family, boolean netherEnd) {
        Map<Profile, Integer> out = new LinkedHashMap<>();
        for (Profile p : Profile.values()) {
            find(new VisualSelection(family, p, netherEnd)).ifPresent(a -> out.put(p, a.solidUsed()));
        }
        return out;
    }
}
