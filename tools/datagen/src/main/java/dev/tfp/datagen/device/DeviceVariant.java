/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.DatagenException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One server state of a device block (PAR-INFRA-08): its property values (in property order), the appearance it shows
 * and the light it emits.
 *
 * @param state      property name to value, in the spec's property order
 * @param appearance the appearance name
 * @param luminance  the emitted light 0 to 15, or null to leave the block's setting
 */
public record DeviceVariant(Map<String, String> state, String appearance, Integer luminance) {
    public DeviceVariant {
        state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
        if (luminance != null && (luminance < 0 || luminance > 15)) {
            throw new DatagenException("device variant " + describe(state) + ": luminance " + luminance + " outside 0 to 15");
        }
    }

    /** The CraftEngine variant key, {@code lit=false,smoke_level=0,axis=x}. */
    public String key() {
        return describe(state);
    }

    private static String describe(Map<String, String> state) {
        List<String> parts = new ArrayList<>();
        state.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join(",", parts);
    }

    Map<String, Object> yaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appearance", appearance);
        if (luminance != null) {
            m.put("settings", Map.of("luminance", luminance));
        }
        return m;
    }
}
