/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.install.Installer;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.yaml.snakeyaml.Yaml;

/**
 * {@code items.tool_build} (PAR-PROC-27): every CraftEngine item of our installed pack whose config declares a
 * {@code minecraft:tool} component (its own {@code data.tool} / {@code components."minecraft:tool"}, or through a
 * template such as {@code tfp:item/tool}), and every other of our items whose built stack carries one, builds through
 * the CE API on the global thread, carries the tool component, and goes into a test inventory. Vanilla's tool codec
 * rejects a rule naming a block tag the server lacks, which made {@code ce item give Bot tfp:gem_saw} fail
 * ("Missing tag: tfp:mineable/glass_saw in minecraft:block") until PAR-PROC-24 shipped the tags. A canary first shows
 * the server still rejects an unshipped {@code tfp:} tag and accepts a shipped one, so this case would catch the crash.
 * The datagen half of the guard is {@code ToolRuleTagsTest}.
 */
public final class ToolItemsBuildCase implements SelfTestCase {
    static final Duration REBUILD = CraftEngineBlockRoundTripCase.REBUILD;
    static final Duration STEP = Duration.ofSeconds(20);
    /** Items built per global task, so one task never holds the tick long. */
    static final int BATCH = 200;

    @Override
    public String name() {
        return "items.tool_build: every item with a minecraft:tool component builds and can be given (PAR-PROC-27)";
    }

    @Override
    public Duration timeout() {
        return REBUILD.plusSeconds(120);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        Installer.Result install = tfp.install();
        if (install == null || install.craftEngine() == null) {
            throw new Skip("our CraftEngine pack was not installed this run (" + (install == null ? "no install step"
                    : String.join("; ", install.skipped())) + "): start the server again");
        }
        Path config = Installer.craftEngineTarget(tfp.getDataPath().toAbsolutePath().getParent()).resolve("configuration");
        CustomItems items = tfp.integrations().customItems();
        Scheduler s = tfp.scheduler();
        return OnThread.script("tool-build", () -> {
            OnThread.awaitPackRebuild(tfp, REBUILD);
            if (!tfp.integrations().blockVisuals().isLoaded()) {
                throw new AssertionError("CraftEngine has not loaded yet (no CraftEngineReloadEvent seen by our adapter)");
            }
            String canary = OnThread.global(s, STEP, ToolItemsBuildCase::canary);
            Map<String, Boolean> declared = declaredItems(config);
            if (!declared.getOrDefault("tfp:gem_saw", false)) {
                throw new AssertionError("tfp:gem_saw is not a tool item in " + config);
            }
            List<String> ids = new ArrayList<>(declared.keySet());
            Set<String> tools = new TreeSet<>();
            List<String> failures = new ArrayList<>();
            int notBuilt = 0;
            for (int i = 0; i < ids.size(); i += BATCH) {
                List<String> batch = ids.subList(i, Math.min(ids.size(), i + BATCH));
                Result r = OnThread.global(s, STEP, () -> build(items, batch, declared));
                tools.addAll(r.tools());
                failures.addAll(r.failures());
                notBuilt += r.notBuilt();
            }
            if (!failures.isEmpty()) {
                throw new AssertionError(failures.size() + " tool item(s) failed to build or give:\n" + String.join("\n", failures));
            }
            Map<String, Integer> families = new TreeMap<>();
            tools.forEach(id -> families.merge(family(id), 1, Integer::sum));
            return tools.size() + " tool items built and given " + families + " of " + ids.size() + " items ("
                    + notBuilt + " non-tool items did not build, not this case's concern); " + canary;
        });
    }

    /** What one batch found. */
    record Result(Set<String> tools, List<String> failures, int notBuilt) {
    }

    /** Builds each id; a declared tool, or any stack carrying a tool component, must build, carry it and be given. */
    static Result build(CustomItems items, List<String> ids, Map<String, Boolean> declared) {
        Set<String> tools = new HashSet<>();
        List<String> failures = new ArrayList<>();
        int notBuilt = 0;
        Inventory inv = Bukkit.createInventory(null, 9);
        for (String id : ids) {
            boolean tool = declared.getOrDefault(id, false);
            ItemStack stack;
            try {
                stack = items.create(id, 1).orElse(null);
            } catch (Throwable e) {
                if (tool) {
                    failures.add(id + ": " + e);
                } else {
                    notBuilt++;
                }
                continue;
            }
            if (stack == null) {
                if (tool) {
                    failures.add(id + ": CraftEngine does not know the item");
                } else {
                    notBuilt++;
                }
                continue;
            }
            boolean hasTool = stack.hasData(DataComponentTypes.TOOL);
            if (!tool && !hasTool) {
                continue;
            }
            if (!hasTool) {
                failures.add(id + ": built without its minecraft:tool component");
                continue;
            }
            tools.add(id);
            try {
                inv.clear();
                if (!inv.addItem(stack).isEmpty() || !stack.isSimilar(inv.getItem(0))) {
                    failures.add(id + ": the test inventory did not take the stack");
                }
            } catch (Throwable e) {
                failures.add(id + ": giving failed: " + e);
            }
        }
        inv.clear();
        return new Result(tools, failures, notBuilt);
    }

    /** The server rejects a tool rule naming an unshipped tfp: tag and accepts the shipped tfp:mineable/knife. */
    static String canary() {
        String rule = "minecraft:stick[minecraft:tool={rules:[{blocks:\"#%s\",speed:2.0f,correct_for_drops:true}]}]";
        try {
            Bukkit.getItemFactory().createItemStack(rule.formatted("tfp:mineable/knife"));
        } catch (RuntimeException e) {
            throw new AssertionError("a tool rule naming the shipped #tfp:mineable/knife does not parse: " + e, e);
        }
        try {
            Bukkit.getItemFactory().createItemStack(rule.formatted("tfp:mineable/tfp_selftest_unshipped"));
            return "canary: this server accepts unshipped tool-rule tags (the guard is not needed here)";
        } catch (RuntimeException e) {
            return "canary: unshipped #tfp: tag rejected, shipped one accepted";
        }
    }

    /**
     * Every item id of our installed CE config, mapped to whether its config declares a tool component (directly or
     * through any template it calls, transitively).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Boolean> declaredItems(Path config) throws IOException {
        Map<String, Object> templates = new HashMap<>();
        Map<String, Object> items = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(config)) {
            files = walk.filter(p -> p.toString().endsWith(".yml")).sorted().toList();
        }
        for (Path f : files) {
            Object root = new Yaml().load(Files.readString(f, StandardCharsets.UTF_8));
            if (!(root instanceof Map<?, ?> sections)) {
                continue;
            }
            for (var e : ((Map<String, Object>) sections).entrySet()) {
                String section = e.getKey().contains("#") ? e.getKey().substring(0, e.getKey().indexOf('#')) : e.getKey();
                if (e.getValue() instanceof Map<?, ?> m) {
                    if (section.equals("templates")) {
                        templates.putAll((Map<String, Object>) m);
                    } else if (section.equals("items")) {
                        items.putAll((Map<String, Object>) m);
                    }
                }
            }
        }
        Map<String, Boolean> out = new TreeMap<>();
        items.forEach((id, node) -> out.put(id, declaresTool(node, templates, new HashSet<>())));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static boolean declaresTool(Object node, Map<String, Object> templates, Set<String> seen) {
        if (node instanceof List<?> list) {
            return list.stream().anyMatch(v -> declaresTool(v, templates, seen));
        }
        if (!(node instanceof Map<?, ?> raw)) {
            return false;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        for (var e : map.entrySet()) {
            String key = e.getKey();
            if ((key.equals("tool") || key.equals("minecraft:tool")) && e.getValue() instanceof Map<?, ?> t
                    && t.containsKey("rules")) {
                return true;
            }
            if (key.equals("template")) {
                List<?> calls = e.getValue() instanceof List<?> l ? l : List.of(e.getValue());
                for (Object c : calls) {
                    String id = String.valueOf(c);
                    if (seen.add(id) && declaresTool(templates.get(id), templates, seen)) {
                        return true;
                    }
                }
            } else if (declaresTool(e.getValue(), templates, seen)) {
                return true;
            }
        }
        return false;
    }

    /** {@code tfp:metal/knife/bronze} -> {@code metal/knife}; {@code tfp:gem_saw} -> {@code gem_saw}. */
    static String family(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        int last = path.lastIndexOf('/');
        return last < 0 ? path : path.substring(0, last);
    }
}
