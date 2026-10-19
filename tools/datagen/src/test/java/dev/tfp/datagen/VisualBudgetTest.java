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
import dev.tfp.datagen.visuals.Profile;
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
 * The real checkout is checked for the default profiles (1.21 → level-3a, 26 → level-2) with Nether/End on and off.
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
