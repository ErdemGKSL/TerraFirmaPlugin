/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.oreoverlay.OreLook;
import dev.tfp.datagen.oreoverlay.OreOverlays;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VanillaSupply;
import dev.tfp.datagen.visuals.VisualDemand;
import dev.tfp.datagen.visuals.VersionFamily;
import dev.tfp.datagen.visuals.VisualAllocator;
import dev.tfp.datagen.visuals.VisualOutputs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * X-VIS-01 visual budget watch. The budget the project set for vanilla visual states:
 * <ul>
 *   <li>each explicit pool (identity, nether_end, absent_clean, absent_minor) may use up to its supply;</li>
 *   <li>the CE {@code auto_state: solid} pool may use all but {@link Profile#SOLID_RESERVE_WARNING} slots, which stay
 *       free for other CraftEngine packs;</li>
 *   <li>the total may use the sum of those budgets.</li>
 * </ul>
 * The real checkout is checked for the default profiles (level-2 on 1.21 and 26, PAR-INFRA-15) with Nether/End on and
 * off, in the shipped ore look and in the target {@code HOST_ROCK} look (ores take their host rock's visual).
 */
class VisualBudgetTest {
    private static final String SOLID = "solid";

    /** Budget per pool: explicit pools get their supply, the solid pool gets its size minus the reserve. */
    static Map<String, Integer> budgetOf(VersionFamily family, Map<String, Integer> capacity) {
        Map<String, Integer> budget = new LinkedHashMap<>();
        for (String pool : VisualAllocator.POOLS) {
            budget.put(pool, pool.equals(SOLID)
                ? family.solidPool() - Profile.SOLID_RESERVE_WARNING
                : capacity.getOrDefault(pool, 0));
        }
        return budget;
    }

    /** One message per pool (and one for the total) whose usage is above its budget. */
    static List<String> violations(Map<String, Integer> used, Map<String, Integer> budget) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : budget.entrySet()) {
            int u = used.getOrDefault(e.getKey(), 0);
            if (u > e.getValue()) {
                out.add("pool " + e.getKey() + ": used " + u + " > budget " + e.getValue());
            }
        }
        int total = sum(used.values());
        int totalBudget = sum(budget.values());
        if (total > totalBudget) {
            out.add("total: used " + total + " > budget " + totalBudget);
        }
        return out;
    }

    static void assertWithinBudget(String key, Map<String, Integer> used, Map<String, Integer> budget) {
        assertThat(violations(used, budget)).as("visual budget exceeded for %s", key).isEmpty();
    }

    private static int sum(Iterable<Integer> values) {
        int s = 0;
        for (int v : values) {
            s += v;
        }
        return s;
    }

    @Test
    void defaultProfilesStayWithinTheBudgetOnTheRealCheckout() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)),
            "TFC checkout is not at the pinned commit");

        Datagen.Result r = Datagen.generate(checkout, TfcCheckout.PINNED_COMMIT);
        JsonObject index = JsonParser.parseString(new String(r.tree().files().get(VisualOutputs.INDEX),
            StandardCharsets.UTF_8)).getAsJsonObject();

        Map<String, Map<String, Integer>> used = new TreeMap<>();
        Map<String, Map<String, Integer>> capacity = new TreeMap<>();
        StringBuilder table = new StringBuilder("visual usage (used/capacity per pool):\n");
        for (var e : index.getAsJsonArray("allocations")) {
            JsonObject a = e.getAsJsonObject();
            String key = a.get("family").getAsString() + "/" + a.get("profile").getAsString() + "/ne="
                + a.get("nether_end").getAsBoolean();
            Map<String, Integer> u = new LinkedHashMap<>();
            Map<String, Integer> c = new LinkedHashMap<>();
            table.append(key);
            for (String pool : VisualAllocator.POOLS) {
                JsonObject p = a.getAsJsonObject("pools").getAsJsonObject(pool);
                u.put(pool, p.get("used").getAsInt());
                c.put(pool, p.get("capacity").getAsInt());
                table.append(' ').append(pool).append(' ').append(u.get(pool)).append('/').append(c.get(pool));
            }
            table.append(" total ").append(sum(u.values())).append('/').append(sum(c.values())).append('\n');
            used.put(key, u);
            capacity.put(key, c);
        }
        System.out.print(table);

        int checked = 0;
        for (VersionFamily f : VersionFamily.values()) {
            for (boolean netherEnd : new boolean[] {false, true}) {
                String key = f.id() + "/" + Profile.defaultFor(f).id() + "/ne=" + netherEnd;
                assertThat(used).as("allocation %s", key).containsKey(key);
                assertWithinBudget(key, used.get(key), budgetOf(f, capacity.get(key)));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(4);
    }

    /**
     * PAR-INFRA-15 done-criterion: with HOST_ROCK the ore demand is 0 and every profile needs the 261 visuals of the
     * families emitted today (rock, soil, sand), all from explicit states: the solid pool stays free and the minor-caveat
     * states are not touched, so the optional "solid pool before absent_minor" tweak would change nothing (left off).
     * Every ore of the runtime data uses its host rock's unit, and every block the runtime treats as occluding has a
     * full-cube visual in the allocation.
     */
    @Test
    void hostRockLookNeeds261VisualsInEveryProfileAndOresUseTheirHostRock() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(checkout)),
            "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, TfcCheckout.PINNED_COMMIT);
        long fullCubeBytes;
        int fullCubeFiles;
        {
            OutputTree full = new OutputTree();
            AssetPipeline.run(tfc, full, new Report(), OreLook.FULL_CUBE);
            fullCubeBytes = packBytes(full);
            fullCubeFiles = full.files().size();
        }
        OutputTree out = new OutputTree();
        List<VisualDemand> demand = AssetPipeline.run(tfc, out, new Report(), OreLook.HOST_ROCK);
        long hostRockBytes = packBytes(out);
        System.out.println("ore look pack delta (rock/ore/soil/sand assets): FULL_CUBE " + fullCubeFiles + " files "
            + fullCubeBytes + " B, HOST_ROCK " + out.files().size() + " files " + hostRockBytes + " B, saved "
            + (fullCubeBytes - hostRockBytes) + " B");
        assertThat(fullCubeFiles - out.files().size()).as("flattened PNGs (1113 + 106 columnar ends) and 1113 ore block models")
            .isEqualTo(1113 + 106 + 1113);
        assertThat(out.files().keySet()).noneMatch(k -> k.matches(".*/textures/block/ore/[a-z_]+/[a-z_]+\\.png")
            || k.matches(".*/models/block/ore/[a-z_]+/[a-z_]+\\.json"));

        VisualOutputs.run(out, demand, m -> Datagen.modelContent(out, m), OreLook.HOST_ROCK);
        OreOverlays.run(tfc, out, demand);
        VanillaSupply supply = VanillaSupply.load();
        JsonObject ores = JsonParser.parseString(new String(out.get(OreOverlays.ORES), StandardCharsets.UTF_8))
            .getAsJsonObject().getAsJsonObject("ores");
        JsonObject occ = JsonParser.parseString(new String(out.get(OreOverlays.OCCLUDING), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(ores.keySet()).hasSize(1113);
        for (VersionFamily f : VersionFamily.values()) {
            for (Profile p : Profile.values()) {
                for (boolean ne : new boolean[] {false, true}) {
                    String key = f.id() + "/" + p.id() + "/ne=" + ne;
                    VisualAllocator.Allocation a = VisualAllocator.allocate(demand, m -> Datagen.modelContent(out, m), p, f,
                        ne, supply, OreLook.HOST_ROCK);
                    assertThat(a.units()).as(key).hasSize(261);
                    assertThat(a.solidUsed()).as(key).isZero();
                    assertThat(a.used().get("absent_minor")).as(key).isZero();
                    assertThat(a.units()).as(key).noneMatch(u -> u.id().startsWith("ore/"));
                    assertWithinBudget(key, a.used(), budgetOf(f, a.capacity()));
                    if (p == Profile.defaultFor(f)) {
                        for (Map.Entry<String, com.google.gson.JsonElement> e : ores.entrySet()) {
                            String host = e.getValue().getAsJsonObject().get("host_rock").getAsString().substring(4);
                            assertThat(a.unitByDemand().get(e.getKey().substring(4))).as(e.getKey())
                                .isSameAs(a.unitByDemand().get(host + "#axis_y"));
                        }
                        occ.getAsJsonArray("occluding").forEach(id -> assertThat(a.demand())
                            .anyMatch(d -> ("tfp:" + d.blockId()).equals(id.getAsString())
                                && a.unitByDemand().get(d.key()) != null));
                    }
                }
            }
        }
    }

    private static long packBytes(OutputTree t) {
        return t.files().entrySet().stream().filter(e -> e.getKey().startsWith(AssetPipeline.RP))
            .mapToLong(e -> e.getValue().length).sum();
    }

    @Test
    void withinBudgetUsagePasses() {
        Map<String, Integer> capacity = Map.of("identity", 126, "nether_end", 67, "absent_clean", 400,
            "absent_minor", 10, SOLID, VersionFamily.V1_21.solidPool());
        Map<String, Integer> budget = budgetOf(VersionFamily.V1_21, capacity);
        // exactly at every limit is still within budget
        Map<String, Integer> used = new LinkedHashMap<>(Map.of("identity", 126, "nether_end", 67, "absent_clean", 400,
            "absent_minor", 10, SOLID, VersionFamily.V1_21.solidPool() - Profile.SOLID_RESERVE_WARNING));
        assertWithinBudget("fixture at limit", used, budget);
    }

    @Test
    void overBudgetUsageFailsNamingThePool() {
        Map<String, Integer> capacity = Map.of("identity", 126, "nether_end", 67, "absent_clean", 400,
            "absent_minor", 10, SOLID, VersionFamily.V1_21.solidPool());
        Map<String, Integer> budget = budgetOf(VersionFamily.V1_21, capacity);
        // solid pool: one slot into the reserve; absent_minor: one state more than the supply
        Map<String, Integer> used = new LinkedHashMap<>(Map.of("identity", 126, "nether_end", 67, "absent_clean", 400,
            "absent_minor", 11, SOLID, VersionFamily.V1_21.solidPool() - Profile.SOLID_RESERVE_WARNING + 1));
        assertThat(violations(used, budget)).containsExactly(
            "pool absent_minor: used 11 > budget 10",
            "pool solid: used 790 > budget 789",
            "total: used 1394 > budget 1392");
    }
}
