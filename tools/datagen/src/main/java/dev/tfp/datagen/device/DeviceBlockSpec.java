/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import com.google.gson.JsonElement;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything datagen needs to write one TFC device block as a CraftEngine entry (PAR-INFRA-08): the state properties,
 * the appearances (with their hitbox carriers and display entities), one variant per server state with its appearance
 * and light, the block settings, the loot, whether it carries the {@code tfp:device} behaviour, and the items the
 * entry declares (the placing block item and the display-only render items).
 *
 * <p>The constructor checks the spec the way CraftEngine would only complain about in its log, or not at all: every
 * variant names every property with a valid value (CE silently turns a bad key into a wildcard), every server state has
 * exactly one variant (no overlaps, whose winner CE picks by YAML order), every variant names a declared appearance,
 * every appearance is used, every display item is declared, and light is 0 to 15. {@link DeviceYaml} writes it.
 *
 * @param id               the block id, {@code tfp:...}
 * @param properties       the state properties in TFC's {@code builder.add} order (variant keys follow it)
 * @param appearances      the appearances; the first is CraftEngine's fallback
 * @param variants         one per server state, in property-product order
 * @param settings         hardness, resistance, sounds and mining tool tag (TFC's block properties and mineable tag), or
 *                         null for none (placeholder entries that CraftEngine does not load yet)
 * @param loot             what the block drops (TFC's loot table), or null for nothing (placeholders)
 * @param behaviour        whether the block carries the {@code tfp:device} behaviour (PAR-INFRA-02)
 * @param blockItemLangKey the lang key of the item that places the block (same id), or null when there is none
 * @param renderItems      display-only item id to the model it shows ({@code tfp:item/render}), in order
 * @param stateMappings    {@code block_state_mappings} the entry needs (real vanilla states sent as another state)
 */
public record DeviceBlockSpec(String id, List<DeviceProperty> properties, List<DeviceAppearance> appearances,
        List<DeviceVariant> variants, Settings settings, DeviceLoot loot, boolean behaviour, String blockItemLangKey,
        Map<String, String> renderItems, Map<String, String> stateMappings) {

    /**
     * The block settings TFC gives a device: {@code strength(hardness, resistance)}, {@code sound(...)} and the
     * {@code minecraft:mineable/<tool>} tag the block is in (mining speed only: TFC devices do not require the tool).
     */
    public record Settings(BigDecimal hardness, BigDecimal resistance, DeviceSound sound, String toolTag) {
        static final String TFC_BLOCKS = "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java";
        static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
        static final List<String> TOOLS = List.of("axe", "hoe", "pickaxe", "shovel");
        private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]?(?:,\\s*([0-9.]+)[fF]?)?\\)");
        private static final Pattern SOUND = Pattern.compile("\\.sound\\(((?:SoundType|TFCSounds)\\.[A-Z_]+)\\)");

        public Settings {
            if (hardness == null || resistance == null || hardness.signum() < 0 || resistance.signum() < 0) {
                throw new DatagenException("device hardness and resistance must be given and not negative");
            }
            if (sound == null) {
                throw new DatagenException("device settings need a sound");
            }
            if (toolTag == null || !toolTag.matches("minecraft:mineable/[a-z_]+")) {
                throw new DatagenException("device tool tag " + toolTag + " is not minecraft:mineable/<tool>");
            }
        }

        /** {@code strength(h)} sets both. */
        public static Settings of(String hardness, String resistance, DeviceSound sound, String tool) {
            return new Settings(new BigDecimal(hardness), new BigDecimal(resistance), sound, "minecraft:mineable/" + tool);
        }

        /**
         * Reads TFC's settings of {@code tfc:<block>}: strength and sound from its registration line in
         * {@code TFCBlocks.java}, the tool from the generated {@code minecraft:mineable/*} tags (exactly one must hold it).
         */
        public static Settings fromTfc(TfcCheckout tfc, String block) {
            String line = null;
            for (String l : tfc.readString(TFC_BLOCKS).split("\n")) {
                if (l.matches(".*\\bregister(NoItem)?\\(\"" + Pattern.quote(block) + "\",.*")) {
                    if (line != null) {
                        throw new DatagenException(TFC_BLOCKS + ": tfc:" + block + " is registered twice");
                    }
                    line = l;
                }
            }
            if (line == null) {
                throw new DatagenException(TFC_BLOCKS + ": no registration of tfc:" + block);
            }
            Matcher st = STRENGTH.matcher(line);
            Matcher so = SOUND.matcher(line);
            if (!st.find() || !so.find()) {
                throw new DatagenException(TFC_BLOCKS + ": tfc:" + block + " has no strength(...) or sound(...)");
            }
            String hardness = st.group(1);
            String resistance = st.group(2) == null ? hardness : st.group(2);
            String tool = null;
            for (String t : TOOLS) {
                String src = MINEABLE + t + ".json";
                JsonElement tag = Json.parse(tfc.readString(src), src);
                for (JsonElement v : tag.getAsJsonObject().getAsJsonArray("values")) {
                    if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + block)) {
                        if (tool != null) {
                            throw new DatagenException("tfc:" + block + " is in two mineable tags: " + tool + ", " + t);
                        }
                        tool = t;
                    }
                }
            }
            if (tool == null) {
                throw new DatagenException("tfc:" + block + " is in no minecraft:mineable tag");
            }
            return of(hardness, resistance, DeviceSound.ofJava(so.group(1)), tool);
        }

        /** CraftEngine {@code settings}: hardness, resistance, sounds, the mineable tag. */
        Map<String, Object> yaml() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hardness", hardness);
            m.put("resistance", resistance);
            m.put("sounds", sound.yaml());
            m.put("tags", List.of(toolTag));
            return m;
        }
    }

    public DeviceBlockSpec {
        if (id == null || !id.matches("tfp:[a-z0-9_/]+")) {
            throw new DatagenException("device block id " + id + " is not tfp:<path>");
        }
        properties = List.copyOf(properties);
        appearances = List.copyOf(appearances);
        variants = List.copyOf(variants);
        renderItems = Collections.unmodifiableMap(new LinkedHashMap<>(renderItems));
        stateMappings = Collections.unmodifiableMap(new LinkedHashMap<>(stateMappings));
        check(id, properties, appearances, variants, renderItems);
    }

    private static void check(String id, List<DeviceProperty> properties, List<DeviceAppearance> appearances,
            List<DeviceVariant> variants, Map<String, String> renderItems) {
        Set<String> names = new HashSet<>();
        for (DeviceProperty p : properties) {
            if (!names.add(p.name())) {
                throw new DatagenException(id + ": property " + p.name() + " twice");
            }
        }
        if (appearances.isEmpty()) {
            throw new DatagenException(id + " has no appearance");
        }
        Map<String, DeviceAppearance> byName = new LinkedHashMap<>();
        for (DeviceAppearance a : appearances) {
            if (byName.put(a.name(), a) != null) {
                throw new DatagenException(id + ": appearance " + a.name() + " twice");
            }
            for (DeviceAppearance.Renderer r : a.renderers()) {
                if (!renderItems.containsKey(r.item())) {
                    throw new DatagenException(id + ": appearance " + a.name() + " draws " + r.item()
                        + ", which the entry does not declare as a render item");
                }
            }
        }
        Set<String> used = new HashSet<>();
        Set<Map<String, String>> seen = new HashSet<>();
        for (DeviceVariant v : variants) {
            if (!v.state().keySet().equals(names) || v.state().size() != properties.size()) {
                throw new DatagenException(id + ": variant " + v.key() + " must name exactly the properties " + names);
            }
            for (DeviceProperty p : properties) {
                if (!p.values().contains(v.state().get(p.name()))) {
                    throw new DatagenException(id + ": variant " + v.key() + " has no valid value for " + p.name());
                }
            }
            if (!seen.add(v.state())) {
                throw new DatagenException(id + ": two variants for the state " + v.key());
            }
            if (!byName.containsKey(v.appearance())) {
                throw new DatagenException(id + ": variant " + v.key() + " names the undeclared appearance " + v.appearance());
            }
            used.add(v.appearance());
        }
        long states = stateCount(properties);
        if (seen.size() != states) {
            throw new DatagenException(id + ": " + seen.size() + " variants for " + states + " server states");
        }
        for (String a : byName.keySet()) {
            if (!used.contains(a)) {
                throw new DatagenException(id + ": appearance " + a + " is used by no variant");
            }
        }
    }

    private static long stateCount(List<DeviceProperty> properties) {
        long n = 1;
        for (DeviceProperty p : properties) {
            n *= p.values().size();
        }
        return n;
    }

    /** The number of server states (the product of the property sizes). */
    public long stateCount() {
        return stateCount(properties);
    }

    /** Whether any appearance draws display entities (overlay or the whole look). */
    public boolean rendered() {
        return appearances.stream().anyMatch(a -> !a.renderers().isEmpty());
    }

    /** The distinct appearance strategies, in appearance order. */
    public List<DeviceAppearance.Strategy> strategies() {
        return appearances.stream().map(DeviceAppearance::strategy).distinct().toList();
    }

    /** Every combination of the property values, first property slowest (TFC's and CraftEngine's state order). */
    public static List<Map<String, String>> combinations(List<DeviceProperty> props) {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(new LinkedHashMap<>());
        for (DeviceProperty p : props) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> base : out) {
                for (String v : p.values()) {
                    Map<String, String> c = new LinkedHashMap<>(base);
                    c.put(p.name(), v);
                    next.add(c);
                }
            }
            out = next;
        }
        return out;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Collects a spec; {@link #build()} runs the checks. */
    public static final class Builder {
        private final String id;
        private final List<DeviceProperty> properties = new ArrayList<>();
        private final List<DeviceAppearance> appearances = new ArrayList<>();
        private final List<DeviceVariant> variants = new ArrayList<>();
        private final Map<String, String> renderItems = new LinkedHashMap<>();
        private final Map<String, String> stateMappings = new LinkedHashMap<>();
        private Settings settings;
        private DeviceLoot loot;
        private boolean behaviour;
        private String blockItemLangKey;

        private Builder(String id) {
            this.id = id;
        }

        public Builder property(DeviceProperty p) {
            properties.add(p);
            return this;
        }

        public Builder properties(List<DeviceProperty> ps) {
            properties.addAll(ps);
            return this;
        }

        public Builder appearance(DeviceAppearance a) {
            appearances.add(a);
            return this;
        }

        /** One variant per server state, in property-product order: its appearance and light (null for none). */
        public Builder everyState(Function<Map<String, String>, String> appearance, ToIntFunction<Map<String, String>> light) {
            for (Map<String, String> s : combinations(properties)) {
                variants.add(new DeviceVariant(s, appearance.apply(s), light == null ? null : light.applyAsInt(s)));
            }
            return this;
        }

        public Builder settings(Settings s) {
            settings = s;
            return this;
        }

        public Builder loot(DeviceLoot l) {
            loot = l;
            return this;
        }

        /** The block carries the {@code tfp:device} behaviour (only once PAR-INFRA-18 registers it with CraftEngine). */
        public Builder deviceBehaviour() {
            behaviour = true;
            return this;
        }

        public Builder blockItem(String langKey) {
            blockItemLangKey = langKey;
            return this;
        }

        public Builder renderItem(String item, String model) {
            if (renderItems.put(item, model) != null) {
                throw new DatagenException(id + ": render item " + item + " twice");
            }
            return this;
        }

        public Builder stateMapping(String from, String to) {
            stateMappings.put(from, to);
            return this;
        }

        public DeviceBlockSpec build() {
            return new DeviceBlockSpec(id, properties, appearances, variants, settings, loot, behaviour,
                blockItemLangKey, renderItems, stateMappings);
        }
    }
}
