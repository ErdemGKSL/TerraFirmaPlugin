/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VanillaSupply;
import dev.tfp.datagen.visuals.VersionFamily;
import dev.tfp.datagen.visuals.VisualAllocator;
import dev.tfp.datagen.visuals.VisualOutputs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-REL-12 profile-vs-pool guard. {@code VisualBudgetTest} (X-VIS-01) checks the default profiles against a budget
 * table; this guard checks <b>every shipped allocation</b> (each profile × MC family × Nether/End setting) from the files
 * the plugin actually installs ({@code visuals/index.json} and each allocation's {@code visual_templates.yml}), so a
 * non-default profile that overflows a pool, or a family generator that gives ores a full-cube look again, fails the
 * build. Rules, each violation naming the allocation, the pool and the culprit block ids:
 * <ol>
 *   <li>no pool uses more states than its capacity (used counted from the templates, and the index must agree);</li>
 *   <li>the CE {@code solid} pool keeps at least {@link Profile#SOLID_RESERVE_WARNING} slots free;</li>
 *   <li>no {@code tfp:ore/**} appearance owns a visual of its own: its vanilla state is always one a non-ore appearance
 *       (the host rock) uses too (the always-on ore rule of PAR-INFRA-15);</li>
 *   <li>every allocation folder belongs to a profile of {@link Profile#values()} and a {@link VersionFamily} (no
 *       {@code level-3a}/{@code level-3b} leftovers), and every expected allocation exists;</li>
 *   <li>one vanilla state is never drawn with two different model lists.</li>
 * </ol>
 * The checks run on the in-memory output of the real checkout and, when present, on the committed
 * {@code content/generated} tree. The rule checker itself is exercised on seeded failures (an overflow, a full-cube ore,
 * a leftover profile folder, a shared state) so it is known to fail where it should.
 */
class ProfilePoolGuardTest {
    private static final String VISUALS = "visuals/";
    private static final String ORE_PREFIX = "tfp:visual/ore/";
    private static final String SOLID = "solid";

    private static SortedMap<String, byte[]> realTree;

    @BeforeAll
    static void generate() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        if (Files.isDirectory(checkout.resolve(".git"))
                && TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout))) {
            realTree = visualsOnly(Datagen.generate(checkout, TfcCheckout.PINNED_COMMIT).tree().files());
        }
    }

    private static SortedMap<String, byte[]> visualsOnly(Map<String, byte[]> files) {
        SortedMap<String, byte[]> out = new TreeMap<>();
        files.forEach((k, v) -> {
            if (k.startsWith(VISUALS)) {
                out.put(k, v);
            }
        });
        return out;
    }

    private static SortedMap<String, byte[]> tree() {
        Assumptions.assumeTrue(realTree != null, "no TFC checkout at the pinned commit");
        return new TreeMap<>(realTree);
    }

    // ---------------------------------------------------------------------------------------------- the checker

    /** One appearance of a template file: the CE block id, the appearance name, its visual key and its model list. */
    record Appearance(String block, String name, String visual, String models) {
        boolean ore() {
            return block.startsWith(ORE_PREFIX);
        }

        String label() {
            return block + (name.isEmpty() ? "" : "#" + name);
        }
    }

    /** Every violation of the five rules in a {@code visuals/**} tree (paths relative to the content root). */
    static List<String> violations(Map<String, byte[]> files, VanillaSupply supply) {
        List<String> out = new ArrayList<>();
        byte[] indexBytes = files.get(VisualOutputs.INDEX);
        if (indexBytes == null) {
            out.add("missing " + VisualOutputs.INDEX);
            return out;
        }
        JsonObject index = JsonParser.parseString(new String(indexBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        int reserve = index.has("solid_reserve_warning") ? index.get("solid_reserve_warning").getAsInt()
            : Profile.SOLID_RESERVE_WARNING;
        if (reserve != Profile.SOLID_RESERVE_WARNING) {
            out.add("index: solid_reserve_warning " + reserve + " != Profile.SOLID_RESERVE_WARNING "
                + Profile.SOLID_RESERVE_WARNING);
        }

        // Rule 4: allocation folders and index rows for known profiles and families only, and all of them present.
        Set<String> expected = new TreeSet<>();
        for (VersionFamily f : VersionFamily.values()) {
            for (Profile p : Profile.values()) {
                for (boolean ne : new boolean[] {false, true}) {
                    expected.add(VisualAllocator.Allocation.dir(f, p, ne));
                }
            }
        }
        Set<String> dirs = new TreeSet<>();
        for (String path : files.keySet()) {
            String[] parts = path.split("/");
            // visuals/<family>/<profile>/<nether-end-x>/<file>; anything else below a family folder is a stray folder
            if (parts.length >= 4 && parts[0].equals("visuals") && !parts[1].equals("ore_overlay")) {
                dirs.add(String.join("/", List.of(parts).subList(0, Math.min(parts.length - 1, 4))));
            }
        }
        for (String d : dirs) {
            if (!expected.contains(d)) {
                out.add(d + ": allocation folder for an unknown profile or family (known profiles "
                    + Stream.of(Profile.values()).map(Profile::id).toList() + ")");
            }
        }
        for (String d : expected) {
            if (!dirs.contains(d)) {
                out.add(d + ": expected allocation folder is missing");
            }
        }

        Map<String, JsonObject> rows = new TreeMap<>();
        for (JsonElement e : index.getAsJsonArray("allocations")) {
            JsonObject a = e.getAsJsonObject();
            String dir = a.get("dir").getAsString();
            rows.put(dir, a);
            if (!expected.contains(dir)) {
                out.add(dir + ": index row for an unknown profile or family");
            }
        }

        for (Map.Entry<String, JsonObject> row : rows.entrySet()) {
            String dir = row.getKey();
            JsonObject a = row.getValue();
            if (!expected.contains(dir)) {
                continue;
            }
            byte[] tpl = files.get(dir + "/visual_templates.yml");
            if (tpl == null) {
                out.add(dir + ": missing visual_templates.yml");
                continue;
            }
            VersionFamily family = VersionFamily.byId(a.get("family").getAsString());
            checkAllocation(dir, family, a, appearances(tpl), supply, out);
        }
        for (String d : expected) {
            if (dirs.contains(d) && !rows.containsKey(d)) {
                out.add(d + ": allocation folder has no index row");
            }
        }
        return out;
    }

    private static void checkAllocation(String dir, VersionFamily family, JsonObject row, List<Appearance> apps,
                                        VanillaSupply supply, List<String> out) {
        // visual key -> appearances using it
        Map<String, List<Appearance>> byVisual = new TreeMap<>();
        for (Appearance ap : apps) {
            byVisual.computeIfAbsent(ap.visual(), k -> new ArrayList<>()).add(ap);
        }

        // Rule 1: pool usage counted from the templates.
        Map<String, Set<String>> poolStates = new LinkedHashMap<>();
        Map<String, Set<String>> poolBlocks = new LinkedHashMap<>();
        for (String p : VisualAllocator.POOLS) {
            poolStates.put(p, new TreeSet<>());
            poolBlocks.put(p, new TreeSet<>());
        }
        for (Map.Entry<String, List<Appearance>> e : byVisual.entrySet()) {
            String visual = e.getKey();
            String pool;
            if (visual.startsWith("auto:")) {
                pool = SOLID;
            } else {
                VanillaSupply.State s = supply.get(family, visual);
                if (s == null) {
                    out.add(dir + ": state " + visual + " is not in the researched supply of MC " + family.id()
                        + " (used by " + labels(e.getValue()) + ")");
                    continue;
                }
                pool = s.pool();
            }
            poolStates.get(pool).add(visual);
            e.getValue().forEach(ap -> poolBlocks.get(pool).add(ap.label()));
        }
        JsonObject pools = row.getAsJsonObject("pools");
        for (String p : VisualAllocator.POOLS) {
            int used = poolStates.get(p).size();
            JsonObject pj = pools.getAsJsonObject(p);
            int capacity = pj.get("capacity").getAsInt();
            int indexUsed = pj.get("used").getAsInt();
            if (indexUsed != used) {
                out.add(dir + ": pool " + p + ": index says used " + indexUsed + " but the templates use " + used);
            }
            if (p.equals(SOLID) && capacity != family.solidPool()) {
                out.add(dir + ": pool solid: index capacity " + capacity + " != CE solid pool " + family.solidPool());
            }
            if (used > capacity) {
                out.add(dir + ": pool " + p + ": used " + used + " > capacity " + capacity + "; blocks: "
                    + head(poolBlocks.get(p)));
            }
        }

        // Rule 2: the solid-pool reserve for other CE packs.
        int solidFree = family.solidPool() - poolStates.get(SOLID).size();
        if (solidFree < Profile.SOLID_RESERVE_WARNING) {
            out.add(dir + ": pool solid: " + solidFree + " slots left < reserve " + Profile.SOLID_RESERVE_WARNING
                + "; blocks: " + head(poolBlocks.get(SOLID)));
        }

        // Rule 3: ores never own a visual.
        List<String> ownOre = new ArrayList<>();
        for (Map.Entry<String, List<Appearance>> e : byVisual.entrySet()) {
            if (e.getValue().stream().allMatch(Appearance::ore)) {
                e.getValue().forEach(ap -> ownOre.add(ap.label() + " (" + poolOf(e.getKey(), family, supply) + ")"));
            }
        }
        if (!ownOre.isEmpty()) {
            out.add(dir + ": " + ownOre.size() + " ore appearance(s) own a visual instead of the host rock's: "
                + head(ownOre));
        }

        // Rule 5: one state, one model list.
        for (Map.Entry<String, List<Appearance>> e : byVisual.entrySet()) {
            Map<String, List<String>> byModels = new TreeMap<>();
            e.getValue().forEach(ap -> byModels.computeIfAbsent(ap.models(), k -> new ArrayList<>()).add(ap.label()));
            if (byModels.size() > 1) {
                out.add(dir + ": pool " + poolOf(e.getKey(), family, supply) + ": state " + e.getKey() + " has "
                    + byModels.size() + " different models: " + byModels.values().stream().map(ProfilePoolGuardTest::head)
                        .collect(Collectors.joining(" | ")));
            }
        }
    }

    private static String poolOf(String visual, VersionFamily family, VanillaSupply supply) {
        if (visual.startsWith("auto:")) {
            return SOLID;
        }
        VanillaSupply.State s = supply.get(family, visual);
        return s == null ? "?" : s.pool();
    }

    private static String labels(List<Appearance> apps) {
        return head(apps.stream().map(Appearance::label).toList());
    }

    private static String head(java.util.Collection<String> items) {
        List<String> l = List.copyOf(items);
        if (l.isEmpty()) {
            return "(none)";
        }
        return l.size() <= 8 ? String.join(", ", l)
            : String.join(", ", l.subList(0, 8)) + ", ... (" + l.size() + " in all)";
    }

    /** Parses a {@code visual_templates.yml} into its appearances. */
    @SuppressWarnings("unchecked")
    static List<Appearance> appearances(byte[] yml) {
        Map<String, Object> root = new Yaml().load(new String(yml, StandardCharsets.UTF_8));
        Map<String, Object> templates = (Map<String, Object>) root.get("templates#visuals");
        List<Appearance> out = new ArrayList<>();
        if (templates == null) {
            return out;
        }
        templates.forEach((block, v) -> {
            Map<String, Object> m = (Map<String, Object>) v;
            if (m.containsKey("state") || m.containsKey("auto_state")) {
                out.add(appearance(block, "", m));
            } else {
                m.forEach((name, app) -> out.add(appearance(block, name, (Map<String, Object>) app)));
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Appearance appearance(String block, String name, Map<String, Object> m) {
        String visual;
        if (m.get("state") != null) {
            visual = m.get("state").toString();
        } else {
            Map<String, Object> auto = (Map<String, Object>) m.get("auto_state");
            visual = "auto:" + (auto == null ? "?" : auto.get("type") + "/" + auto.get("id"));
        }
        List<Object> models = new ArrayList<>();
        Object single = m.get("model");
        if (single != null) {
            models.add(single);
        }
        Object list = m.get("models");
        if (list instanceof List<?> l) {
            models.addAll(l);
        }
        List<String> canon = new ArrayList<>();
        for (Object o : models) {
            canon.add(o instanceof Map<?, ?> om ? new TreeMap<>((Map<String, Object>) om).toString()
                : new TreeMap<>(Map.of("path", o.toString())).toString());
        }
        return new Appearance(block, name, visual, canon.toString());
    }

    // ---------------------------------------------------------------------------------------------- real checkout

    @Test
    void everyShippedAllocationStaysWithinItsPoolsOnTheRealCheckout() {
        SortedMap<String, byte[]> files = tree();
        List<String> v = violations(files, VanillaSupply.load());
        JsonObject index = JsonParser.parseString(new String(files.get(VisualOutputs.INDEX), StandardCharsets.UTF_8))
            .getAsJsonObject();
        JsonArray rows = index.getAsJsonArray("allocations");
        StringBuilder table = new StringBuilder("profile-vs-pool guard (used/capacity, solid free):\n");
        for (JsonElement e : rows) {
            JsonObject a = e.getAsJsonObject();
            table.append(a.get("dir").getAsString());
            for (String p : VisualAllocator.POOLS) {
                JsonObject pj = a.getAsJsonObject("pools").getAsJsonObject(p);
                table.append(' ').append(p).append(' ').append(pj.get("used").getAsInt()).append('/')
                    .append(pj.get("capacity").getAsInt());
            }
            table.append(" solid_free ").append(a.get("solid_free").getAsInt()).append('\n');
        }
        System.out.print(table);
        assertThat(rows).as("level-0..2 x 1.21/26 x Nether/End on/off")
            .hasSize(Profile.values().length * VersionFamily.values().length * 2);
        assertThat(v).as("profile-vs-pool violations").isEmpty();
    }

    @Test
    void theCommittedContentTreePassesTheSameGuard() throws IOException {
        Path root = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("content/generated/content");
        Assumptions.assumeTrue(Files.isRegularFile(root.resolve(VisualOutputs.INDEX)), "no generated content at " + root);
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root.resolve(VISUALS))) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        assertThat(violations(files, VanillaSupply.load())).as("profile-vs-pool violations in " + root).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------- seeded failures

    private static String text(SortedMap<String, byte[]> files, String path) {
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    private static void put(SortedMap<String, byte[]> files, String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static final String L2 = "visuals/1.21/level-2/nether-end-off";
    private static final String L0 = "visuals/26/level-0/nether-end-on";

    @Test
    void aSeededPoolOverflowFailsNamingAllocationPoolAndBlocks() {
        SortedMap<String, byte[]> files = tree();
        JsonObject index = JsonParser.parseString(text(files, VisualOutputs.INDEX)).getAsJsonObject();
        String pool = null;
        int used = 0;
        for (JsonElement e : index.getAsJsonArray("allocations")) {
            JsonObject a = e.getAsJsonObject();
            if (a.get("dir").getAsString().equals(L2)) {
                for (String p : List.of("absent_clean", "identity")) {
                    JsonObject pj = a.getAsJsonObject("pools").getAsJsonObject(p);
                    if (pj.get("used").getAsInt() > 0) {
                        pool = p;
                        used = pj.get("used").getAsInt();
                        pj.addProperty("capacity", used - 1);
                        break;
                    }
                }
            }
        }
        assertThat(pool).as("a used explicit pool in " + L2).isNotNull();
        put(files, VisualOutputs.INDEX, index.toString());
        List<String> v = violations(files, VanillaSupply.load());
        assertThat(v).hasSize(1);
        assertThat(v.getFirst()).startsWith(L2 + ": pool " + pool + ": used " + used + " > capacity " + (used - 1))
            .contains("blocks: tfp:visual/");
    }

    @Test
    void aSeededFullCubeOreAppearanceFails() {
        SortedMap<String, byte[]> files = tree();
        String path = L2 + "/visual_templates.yml";
        String yml = text(files, path);
        // Give one ore its own vanilla state (as a family generator emitting a full-cube ore look again would).
        String marker = "  tfp:visual/ore/amethyst/andesite:\n    state: ";
        int at = yml.indexOf(marker);
        assertThat(at).as("ore template in " + path).isPositive();
        int eol = yml.indexOf('\n', at + marker.length());
        String state = yml.substring(at + marker.length(), eol);
        Set<String> usedStates = new TreeSet<>();
        appearances(files.get(path)).forEach(a -> usedStates.add(a.visual()));
        String free = VanillaSupply.load().candidates(VersionFamily.V1_21, false, Set.of()).stream()
            .map(VanillaSupply.State::state).filter(s -> !usedStates.contains(s)).findFirst().orElseThrow();
        put(files, path, yml.substring(0, at + marker.length()) + "'" + free + "'" + yml.substring(eol));
        List<String> v = violations(files, VanillaSupply.load());
        assertThat(state).isNotEqualTo(free);
        assertThat(v).anyMatch(s -> s.startsWith(L2 + ": 1 ore appearance(s) own a visual")
            && s.contains("tfp:visual/ore/amethyst/andesite"));
    }

    @Test
    void aLeftoverLevel3FolderFails() {
        SortedMap<String, byte[]> files = tree();
        files.put("visuals/1.21/level-3a/nether-end-off/visual_templates.yml",
            files.get(L2 + "/visual_templates.yml"));
        assertThat(violations(files, VanillaSupply.load()))
            .containsExactly("visuals/1.21/level-3a/nether-end-off: allocation folder for an unknown profile or family"
                + " (known profiles [level-0, level-1, level-2])");
    }

    @Test
    void aStateDrawnWithTwoModelsFails() {
        SortedMap<String, byte[]> files = tree();
        String path = L0 + "/visual_templates.yml";
        String yml = text(files, path);
        // Point the ore at andesite's state but keep chalk's models: same state, different look.
        String andesite = "  tfp:visual/ore/amethyst/andesite:\n    state: ";
        int a = yml.indexOf(andesite);
        String andesiteState = yml.substring(a + andesite.length(), yml.indexOf('\n', a + andesite.length()));
        String chalk = "  tfp:visual/ore/amethyst/chalk:\n    state: ";
        int c = yml.indexOf(chalk);
        assertThat(a).isPositive();
        assertThat(c).isPositive();
        int ceol = yml.indexOf('\n', c + chalk.length());
        put(files, path, yml.substring(0, c + chalk.length()) + andesiteState + yml.substring(ceol));
        List<String> v = violations(files, VanillaSupply.load());
        assertThat(v).anyMatch(s -> s.startsWith(L0 + ": pool ") && s.contains("state " + unquote(andesiteState)
            + " has 2 different models") && s.contains("tfp:visual/ore/amethyst/chalk"));
    }

    private static String unquote(String s) {
        return s.startsWith("'") && s.endsWith("'") ? s.substring(1, s.length() - 1) : s;
    }

    @Test
    void aSolidPoolBelowTheReserveFails() {
        // Synthetic tree: one allocation whose templates take every solid slot but 10.
        SortedMap<String, byte[]> files = tree();
        String path = L2 + "/visual_templates.yml";
        StringBuilder yml = new StringBuilder(text(files, path));
        int extra = VersionFamily.V1_21.solidPool() - 10;
        for (int i = 0; i < extra; i++) {
            yml.append("  tfp:visual/test/solid_").append(i).append(":\n    auto_state:\n      type: solid\n      id: test_")
                .append(i).append("\n    model: tfp:block/test/solid_").append(i).append('\n');
        }
        put(files, path, yml.toString());
        List<String> v = violations(files, VanillaSupply.load());
        assertThat(v).anyMatch(s -> s.startsWith(L2 + ": pool solid: 10 slots left < reserve "
            + Profile.SOLID_RESERVE_WARNING) && s.contains("tfp:visual/test/solid_0"));
        assertThat(v).anyMatch(s -> s.startsWith(L2 + ": pool solid: index says used 0 but the templates use " + extra));
    }
}
