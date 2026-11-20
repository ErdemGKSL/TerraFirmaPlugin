/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/settings/RockLayerSettings.java @ c3b4d9536 (the codec's data, Data record)
 * and src/generated/resources/data/tfc/worldgen/world_preset/overworld.json @ c3b4d9536 (the preset values)
 */
package dev.tfp.core.worldgen.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code rock_layer_settings} of TFC's world settings, as the codec reads them (TFC's {@code RockLayerSettings.Data}):
 * the rock names and their rock ids, the layer graph and the five layer lists. This is data only; the layer walk and its
 * validation are {@code core/worldgen/rock} (PAR-WORLD-23). Immutable; the maps keep their insertion order.
 *
 * @param rocks      rock name to rock id ({@code tfc:granite})
 * @param bottom     the rocks of the bottom layer
 * @param layers     the named layers: each maps a rock to the id of the layer below it ({@code bottom} ends the walk)
 * @param oceanFloor the layer ids of ocean regions
 * @param volcanic   the layer ids of volcanic regions
 * @param land       the layer ids of land regions
 * @param uplift     the layer ids of uplift regions
 */
public record RockLayerConfig(
    Map<String, String> rocks,
    List<String> bottom,
    List<Layer> layers,
    List<String> oceanFloor,
    List<String> volcanic,
    List<String> land,
    List<String> uplift
) {
    /** One named layer: {@code id} and the rock to next-layer map, in file order. */
    public record Layer(String id, Map<String, String> layers) {
        public Layer {
            layers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(layers));
        }
    }

    public RockLayerConfig {
        rocks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rocks));
        bottom = List.copyOf(bottom);
        layers = List.copyOf(layers);
        oceanFloor = List.copyOf(oceanFloor);
        volcanic = List.copyOf(volcanic);
        land = List.copyOf(land);
        uplift = List.copyOf(uplift);
    }

    /** TFC's overworld preset at c3b4d9536. */
    public static final RockLayerConfig PRESET = preset();

    private static RockLayerConfig preset() {
        final Map<String, String> rocks = new LinkedHashMap<>();
        for (String r : List.of("andesite", "basalt", "chalk", "chert", "claystone", "conglomerate", "dacite", "diorite", "dolomite",
                "gabbro", "gneiss", "granite", "limestone", "marble", "phyllite", "quartzite", "rhyolite", "schist", "shale", "slate", "tuff")) {
            rocks.put(r, "tfc:" + r);
        }
        final List<Layer> layers = new ArrayList<>();
        layers.add(new Layer("felsic", map("granite", "bottom")));
        layers.add(new Layer("intermediate", map("diorite", "bottom")));
        layers.add(new Layer("mafic", map("gabbro", "bottom")));
        layers.add(new Layer("igneous_extrusive", map("andesite", "intermediate", "basalt", "mafic", "dacite", "intermediate",
            "rhyolite", "felsic")));
        layers.add(new Layer("igneous_extrusive_x2", map("andesite", "igneous_extrusive", "basalt", "igneous_extrusive",
            "dacite", "igneous_extrusive", "rhyolite", "igneous_extrusive")));
        layers.add(new Layer("igneous_intrusive", map("diorite", "intermediate", "gabbro", "mafic", "granite", "felsic")));
        layers.add(new Layer("high_grade", map("gneiss", "bottom", "schist", "bottom")));
        layers.add(new Layer("low_grade", map("phyllite", "high_grade", "slate", "high_grade")));
        layers.add(new Layer("marble", map("marble", "bottom")));
        layers.add(new Layer("quartzite", map("quartzite", "bottom")));
        layers.add(new Layer("sedimentary", map("chalk", "marble", "chert", "quartzite", "claystone", "low_grade",
            "conglomerate", "low_grade", "dolomite", "marble", "limestone", "marble", "shale", "low_grade")));
        layers.add(new Layer("uplift", map("diorite", "low_grade", "gabbro", "low_grade", "gneiss", "high_grade", "granite",
            "low_grade", "marble", "bottom", "phyllite", "high_grade", "quartzite", "bottom", "schist", "high_grade", "slate",
            "high_grade")));
        return new RockLayerConfig(rocks, List.of("gneiss", "schist", "diorite", "granite", "gabbro"), layers,
            List.of("igneous_extrusive"), List.of("igneous_extrusive", "igneous_extrusive_x2", "igneous_intrusive"),
            List.of("sedimentary", "sedimentary", "sedimentary", "igneous_extrusive"),
            List.of("uplift", "uplift", "uplift", "sedimentary"));
    }

    private static Map<String, String> map(String... kv) {
        final Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** The codec form ({@code rocks}, {@code bottom}, {@code layers}, {@code ocean_floor}, {@code volcanic}, {@code land}, {@code uplift}). */
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("rocks", new LinkedHashMap<>(rocks));
        out.put("bottom", new ArrayList<>(bottom));
        final List<Object> ls = new ArrayList<>();
        for (Layer layer : layers) {
            final Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", layer.id());
            l.put("layers", new LinkedHashMap<>(layer.layers()));
            ls.add(l);
        }
        out.put("layers", ls);
        out.put("ocean_floor", new ArrayList<>(oceanFloor));
        out.put("volcanic", new ArrayList<>(volcanic));
        out.put("land", new ArrayList<>(land));
        out.put("uplift", new ArrayList<>(uplift));
        return out;
    }

    /**
     * Reads the codec form; every field is required, as in TFC's codec.
     *
     * @throws IllegalArgumentException on a missing field, an unknown field or a wrong type
     */
    public static RockLayerConfig fromMap(Map<?, ?> in) {
        for (Object key : in.keySet()) {
            if (!List.of("rocks", "bottom", "layers", "ocean_floor", "volcanic", "land", "uplift").contains(String.valueOf(key))) {
                throw new IllegalArgumentException("rock_layer_settings: unknown field '" + key + "'");
            }
        }
        final List<Layer> layers = new ArrayList<>();
        for (Object o : list(in, "layers")) {
            if (!(o instanceof Map<?, ?> l)) {
                throw new IllegalArgumentException("rock_layer_settings.layers: each entry needs 'id' and 'layers'");
            }
            final Object id = l.get("id");
            if (id == null) {
                throw new IllegalArgumentException("rock_layer_settings.layers: an entry has no 'id'");
            }
            layers.add(new Layer(id.toString(), stringMap(l, "layers")));
        }
        return new RockLayerConfig(stringMap(in, "rocks"), strings(in, "bottom"), layers, strings(in, "ocean_floor"),
            strings(in, "volcanic"), strings(in, "land"), strings(in, "uplift"));
    }

    private static List<?> list(Map<?, ?> in, String key) {
        final Object v = in.get(key);
        if (!(v instanceof List<?> l)) {
            throw new IllegalArgumentException("rock_layer_settings: '" + key + "' must be a list");
        }
        return l;
    }

    private static List<String> strings(Map<?, ?> in, String key) {
        final List<String> out = new ArrayList<>();
        for (Object o : list(in, key)) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    private static Map<String, String> stringMap(Map<?, ?> in, String key) {
        final Object v = in.get(key);
        if (!(v instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("rock_layer_settings: '" + key + "' must be a map");
        }
        final Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, val) -> out.put(String.valueOf(k), String.valueOf(val)));
        return out;
    }
}
