/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The feature modules of this plugin (PAR-INFRA-01): discovered with {@link ServiceLoader} from
 * {@code META-INF/services/dev.tfp.paper.module.FeatureModule}, in file order. The bootstrapper's command tree
 * ({@code TfpCommands}) and {@code TfpPlugin} share one instance ({@link #shared()}), so the debug commands reach the
 * modules that were enabled. Lifecycle calls run on the server's startup/shutdown thread.
 */
public final class FeatureModules {
    /** The service file a new module appends its class name to. */
    public static final String SERVICE_FILE = "META-INF/services/" + FeatureModule.class.getName();
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    /** Upper bound on service entries read (stops a loader that keeps failing on the same entry). */
    private static final int MAX_ENTRIES = 10_000;
    private static volatile FeatureModules shared;

    private final List<FeatureModule> modules;
    private final Set<String> enabled = Collections.synchronizedSet(new HashSet<>());
    /** Enabled modules, in enable order (disabled in reverse). */
    private final List<FeatureModule> enableOrder = new CopyOnWriteArrayList<>();
    private final List<String> problems = new CopyOnWriteArrayList<>();

    private FeatureModules(List<FeatureModule> modules, List<String> problems) {
        this.modules = List.copyOf(modules);
        this.problems.addAll(problems);
    }

    /** The modules of this plugin's class loader, loaded once. */
    public static FeatureModules shared() {
        FeatureModules m = shared;
        if (m == null) {
            synchronized (FeatureModules.class) {
                m = shared;
                if (m == null) {
                    m = load(FeatureModules.class.getClassLoader());
                    shared = m;
                }
            }
        }
        return m;
    }

    /** Discovers the modules listed in {@link #SERVICE_FILE} on {@code loader}. Broken entries become problems. */
    public static FeatureModules load(ClassLoader loader) {
        List<FeatureModule> found = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        // The iterator goes on after a broken entry ("best effort", ServiceLoader), so one bad line costs one module.
        Iterator<FeatureModule> it = ServiceLoader.load(FeatureModule.class, loader).iterator();
        for (int guard = 0; guard < MAX_ENTRIES; guard++) {
            try {
                if (!it.hasNext()) {
                    break;
                }
                found.add(it.next());
            } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
                problems.add("a module cannot be loaded: " + e.getMessage());
            }
        }
        return of(found, problems);
    }

    /** The given modules (tests); invalid or duplicate ids are skipped and reported. */
    public static FeatureModules of(List<FeatureModule> candidates) {
        return of(candidates, List.of());
    }

    private static FeatureModules of(List<FeatureModule> candidates, List<String> problems) {
        List<String> issues = new ArrayList<>(problems);
        Map<String, FeatureModule> byId = new LinkedHashMap<>();
        for (FeatureModule m : candidates) {
            String id;
            try {
                id = m.id();
            } catch (RuntimeException e) {
                issues.add("module " + m.getClass().getName() + " has no id: " + e);
                continue;
            }
            if (id == null || !ID.matcher(id).matches()) {
                issues.add("module " + m.getClass().getName() + " has an invalid id '" + id + "'; skipped");
            } else if (byId.putIfAbsent(id, m) != null) {
                issues.add("module id '" + id + "' of " + m.getClass().getName() + " is already used by "
                        + byId.get(id).getClass().getName() + "; skipped");
            }
        }
        return new FeatureModules(new ArrayList<>(byId.values()), issues);
    }

    /** Every module, in service-file order. */
    public List<FeatureModule> modules() {
        return modules;
    }

    /** What went wrong while loading, building debug commands or enabling (logged by the plugin). */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    public boolean isEnabled(String id) {
        return enabled.contains(id);
    }

    /** Ids of the enabled modules, in enable order. */
    public List<String> enabledIds() {
        return enableOrder.stream().map(FeatureModule::id).toList();
    }

    /**
     * The modules' {@code /tfp debug} literals. {@code builtIn} are the names the command tree already has; a module
     * literal reusing one (or another module's) is skipped and reported in {@link #problems()}.
     */
    public List<LiteralArgumentBuilder<CommandSourceStack>> debugLiterals(Set<String> builtIn, DebugTree.Runner runner) {
        Set<String> taken = new HashSet<>(builtIn);
        List<LiteralArgumentBuilder<CommandSourceStack>> out = new ArrayList<>();
        for (FeatureModule m : modules) {
            String id = m.id();
            DebugTree tree = new DebugTree(id, () -> isEnabled(id), runner, taken, problems);
            try {
                m.debugCommands(tree);
            } catch (RuntimeException | LinkageError e) {
                problems.add("module " + id + ": debug commands failed: " + e);
            }
            out.addAll(tree.literals());
        }
        return out;
    }

    /** Enables every module in order; one that throws is logged and stays disabled. Returns the enabled ids. */
    public List<String> enableAll(ModuleContext context, Logger log) {
        for (FeatureModule m : modules) {
            if (enabled.contains(m.id())) {
                continue;
            }
            try {
                m.enable(context);
                enabled.add(m.id());
                enableOrder.add(m);
            } catch (Exception | LinkageError e) {
                problems.add("module " + m.id() + " failed to enable: " + e);
                log.log(Level.SEVERE, "Feature module " + m.id() + " failed to enable; it stays off", e);
            }
        }
        return enabledIds();
    }

    /** Disables the enabled modules in reverse enable order; failures are logged and do not stop the others. */
    public void disableAll(Logger log) {
        List<FeatureModule> order = new ArrayList<>(enableOrder);
        Collections.reverse(order);
        for (FeatureModule m : order) {
            enabled.remove(m.id());
            enableOrder.remove(m);
            try {
                m.disable();
            } catch (RuntimeException | LinkageError e) {
                log.log(Level.WARNING, "Feature module " + m.id() + " failed to disable", e);
            }
        }
    }
}
