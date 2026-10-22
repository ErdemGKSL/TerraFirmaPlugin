/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.visuals.VersionFamily;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The vanilla candidate table: per researched Minecraft version the selection and collision shapes of the candidate
 * blocks' states ({@code hitbox/vanilla-shapes-<version>.json}, dumped from each server jar) and the carrier policy
 * ({@code hitbox/carrier-policy.json}: presence in TFC worlds, hide rule, bans, CE pools).
 */
public final class VanillaCandidates {
    public static final String POLICY = "carrier-policy.json";
    public static final String POOL_PREFIX = "auto_state:";

    /** Where a candidate block occurs. Only ABSENT and NATURAL (with a hide mapping) may be chosen. */
    public enum Presence {
        ABSENT, NATURAL, PLAYER, RESERVED, REFERENCE
    }

    /** One vanilla state in one version. Flags: occludes, replaceable, block_entity, fluid, random_offset. */
    public record VState(String state, Shape selection, Shape collision, int light, Set<String> flags) {
        public String block() {
            int i = state.indexOf('[');
            return i < 0 ? state : state.substring(0, i);
        }

        public boolean flag(String f) {
            return flags.contains(f);
        }
    }

    /**
     * The policy of one candidate block. {@code hide}: e.g. {@code nearest_free_value:layers}, or null. {@code onlyClass}:
     * the one TFC shape class allowed to use the block (its class override reserves it), or null for every class.
     */
    public record BlockPolicy(Presence presence, String hide, int statesConsumed, Set<String> replaceableOnly, String note,
            String onlyClass) {
    }

    private final Map<String, Map<String, VState>> byVersion;
    private final Map<String, BlockPolicy> blocks;
    private final Map<String, String> hardBan;
    private final Map<String, String> excludedStates;
    private final Set<String> litParticles;
    private final Set<String> objectBan;
    private final String objectBanReason;
    private final Map<String, VState> pools;

    private VanillaCandidates(Map<String, Map<String, VState>> byVersion, Map<String, BlockPolicy> blocks,
            Map<String, String> hardBan, Map<String, String> excludedStates, Set<String> litParticles, Set<String> objectBan,
            String objectBanReason, Map<String, VState> pools) {
        this.byVersion = byVersion;
        this.blocks = blocks;
        this.hardBan = hardBan;
        this.excludedStates = excludedStates;
        this.litParticles = litParticles;
        this.objectBan = objectBan;
        this.objectBanReason = objectBanReason;
        this.pools = pools;
    }

    private static VanillaCandidates standard;

    /** The committed tables (cached). */
    public static synchronized VanillaCandidates load() {
        if (standard == null) {
            Map<String, Map<String, VState>> versions = new LinkedHashMap<>();
            for (VersionFamily f : VersionFamily.values()) {
                for (String v : f.researchedVersions()) {
                    versions.put(v, parseShapes(resource("vanilla-shapes-" + v + ".json"), v));
                }
            }
            standard = parse(versions, resource(POLICY));
        }
        return standard;
    }

    static String resource(String name) {
        try (InputStream in = VanillaCandidates.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new DatagenException("missing datagen resource hitbox/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, VState> parseShapes(String text, String version) {
        JsonObject root = Json.parse(text, "vanilla-shapes-" + version + ".json").getAsJsonObject();
        if (!version.equals(root.get("version").getAsString())) {
            throw new DatagenException("vanilla-shapes-" + version + ".json holds version " + root.get("version"));
        }
        Map<String, VState> out = new TreeMap<>();
        for (JsonElement e : root.getAsJsonArray("states")) {
            JsonArray a = e.getAsJsonArray();
            Set<String> flags = new LinkedHashSet<>();
            a.get(4).getAsJsonArray().forEach(f -> flags.add(f.getAsString()));
            VState s = new VState(a.get(0).getAsString(), Shape.fromJson(a.get(1)), Shape.fromJson(a.get(2)),
                a.get(3).getAsInt(), Set.copyOf(flags));
            out.put(s.state(), s);
        }
        return out;
    }

    static VanillaCandidates parse(Map<String, Map<String, VState>> versions, String policyText) {
        JsonObject p = Json.parse(policyText, POLICY).getAsJsonObject();
        Map<String, BlockPolicy> blocks = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("blocks").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            blocks.put(e.getKey(), new BlockPolicy(Presence.valueOf(o.get("presence").getAsString().toUpperCase(Locale.ROOT)),
                o.has("hide") ? o.get("hide").getAsString() : null,
                o.has("states_consumed") ? o.get("states_consumed").getAsInt() : 1, replaceableOnly(o),
                o.get("note").getAsString(), o.has("only_class") ? o.get("only_class").getAsString() : null));
        }
        Map<String, String> hardBan = new TreeMap<>();
        p.getAsJsonObject("hard_ban").entrySet().forEach(e -> hardBan.put(e.getKey(), e.getValue().getAsString()));
        Map<String, String> excluded = new TreeMap<>();
        p.getAsJsonObject("excluded_states").entrySet().forEach(e -> excluded.put(e.getKey(), e.getValue().getAsString()));
        Set<String> lit = new LinkedHashSet<>();
        p.getAsJsonArray("lit_particles").forEach(e -> lit.add(e.getAsString()));
        JsonObject ob = p.getAsJsonObject("object_ban");
        Set<String> objectBan = new LinkedHashSet<>();
        ob.getAsJsonArray("blocks").forEach(e -> objectBan.add(e.getAsString()));
        Map<String, VState> pools = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("pools").entrySet()) {
            boolean occludes = e.getValue().getAsJsonObject().get("occludes").getAsBoolean();
            pools.put(POOL_PREFIX + e.getKey(), new VState(POOL_PREFIX + e.getKey(), Shape.FULL, Shape.FULL, 0,
                occludes ? Set.of("occludes") : Set.of()));
        }
        for (Map.Entry<String, Map<String, VState>> v : versions.entrySet()) {
            for (VState s : v.getValue().values()) {
                if (!blocks.containsKey(s.block()) && !hardBan.containsKey(s.block())) {
                    throw new DatagenException(POLICY + " has no policy for " + s.block() + " (dumped for " + v.getKey() + ")");
                }
            }
        }
        for (String b : blocks.keySet()) {
            BlockPolicy bp = blocks.get(b);
            if (bp.presence() == Presence.NATURAL && bp.hide() == null) {
                throw new DatagenException(POLICY + ": natural block " + b + " has no hide rule");
            }
        }
        return new VanillaCandidates(versions, blocks, hardBan, excluded, lit, objectBan, ob.get("reason").getAsString(), pools);
    }

    private static Set<String> replaceableOnly(JsonObject o) {
        if (!o.has("replaceable_only")) {
            return null;
        }
        Set<String> s = new LinkedHashSet<>();
        o.getAsJsonArray("replaceable_only").forEach(e -> s.add(e.getAsString()));
        return Set.copyOf(s);
    }

    /** Whether a state is replaceable (the client predicts placing another block into it). */
    public boolean replaceable(VState s) {
        BlockPolicy p = blocks.get(s.block());
        if (p != null && p.replaceableOnly() != null) {
            return p.replaceableOnly().contains(s.state());
        }
        return s.flag("replaceable");
    }

    /** The researched versions in table order. */
    public List<String> versions() {
        return new ArrayList<>(byVersion.keySet());
    }

    /** The state in {@code version}, or null when the version has no such state. Pools exist everywhere. */
    public VState get(String version, String state) {
        if (state.startsWith(POOL_PREFIX)) {
            return pools.get(state);
        }
        Map<String, VState> m = byVersion.get(version);
        if (m == null) {
            throw new DatagenException("no vanilla shape table for " + version);
        }
        return m.get(state);
    }

    /** Every dumped state of every version plus the pools, sorted. */
    public Set<String> allStates() {
        Set<String> all = new java.util.TreeSet<>(pools.keySet());
        byVersion.values().forEach(m -> all.addAll(m.keySet()));
        return all;
    }

    /** True when every researched version of {@code family} has the state. */
    public boolean inFamily(String state, VersionFamily family) {
        for (String v : family.researchedVersions()) {
            if (get(v, state) == null) {
                return false;
            }
        }
        return true;
    }

    public BlockPolicy policy(String block) {
        return blocks.get(block);
    }

    public Map<String, BlockPolicy> policies() {
        return blocks;
    }

    /** The hard-ban reason of a block, or null. */
    public String hardBan(String block) {
        return hardBan.get(block);
    }

    /** The reason one state (not its whole block) is never a carrier, or null. */
    public String excludedState(String state) {
        return excludedStates.get(state);
    }

    public boolean litParticles(String block) {
        return litParticles.contains(block);
    }

    public boolean objectBanned(String block) {
        return objectBan.contains(block);
    }

    public String objectBanReason() {
        return objectBanReason;
    }

    public boolean isPool(String state) {
        return pools.containsKey(state);
    }

    /** The CE auto_state pool name of a pool candidate ({@code solid}, {@code leaves}). */
    public static String poolName(String state) {
        return state.substring(POOL_PREFIX.length());
    }
}
