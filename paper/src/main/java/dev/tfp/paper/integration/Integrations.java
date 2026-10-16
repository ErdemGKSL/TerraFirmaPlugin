/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The adapters found on the classpath and the services they provide (M1-INT-01). Feature code asks this
 * class for {@link BlockVisuals}, {@link CustomBlocks}, {@link CustomItems}, {@link EntityModels} and
 * {@link HudBridge} and never sees an adapter class; a missing or failed adapter yields the degraded
 * (absent) implementation.
 *
 * <p>Adapters are discovered with {@link ServiceLoader} because {@code paper} cannot depend on the
 * {@code integration/*} modules (they depend on it); the shaded jar merges their service files.
 * Lifecycle methods are called by {@code TfpPlugin} on the server thread.
 */
public final class Integrations {
    private final IntegrationContext context;
    private final List<Integration> active = new ArrayList<>();
    private final Map<String, String> failed = new ConcurrentHashMap<>();

    private Integrations(IntegrationContext context) {
        this.context = context;
    }

    /** Discovers providers with {@link ServiceLoader} on {@code loader}. */
    public static Integrations discover(IntegrationContext context, Predicate<String> pluginInstalled,
            ClassLoader loader) {
        List<IntegrationProvider> providers = new ArrayList<>();
        ServiceLoader.load(IntegrationProvider.class, loader).forEach(providers::add);
        return create(context, pluginInstalled, providers);
    }

    /** Instantiates the adapters of {@code providers} whose plugin is installed. */
    public static Integrations create(IntegrationContext context, Predicate<String> pluginInstalled,
            List<IntegrationProvider> providers) {
        Integrations result = new Integrations(context);
        for (IntegrationProvider p : providers) {
            if (!pluginInstalled.test(p.pluginName())) {
                continue;
            }
            try {
                result.active.add(p.create(context));
            } catch (RuntimeException | LinkageError e) {
                result.disable(p.pluginName(), null, "adapter could not be created", e);
            }
        }
        return result;
    }

    public void onLoad() {
        run("onLoad", Integration::onLoad);
    }

    public void onEnable() {
        run("onEnable", Integration::onEnable);
    }

    public void onDisable() {
        for (Integration i : List.copyOf(active)) {
            try {
                i.onDisable();
            } catch (RuntimeException | LinkageError e) {
                context.logger().log(Level.WARNING, i.pluginName() + " adapter failed to disable cleanly", e);
            }
        }
    }

    private void run(String phase, java.util.function.Consumer<Integration> step) {
        for (Integration i : List.copyOf(active)) {
            try {
                step.accept(i);
            } catch (RuntimeException | LinkageError e) {
                active.remove(i);
                disable(i.pluginName(), i, phase + " failed", e);
            }
        }
    }

    private void disable(String plugin, Integration integration, String what, Throwable e) {
        String reason = plugin + " adapter disabled: " + what + " (" + e + ")";
        failed.put(plugin, reason);
        context.logger().log(Level.SEVERE, reason + "; running without " + plugin + " features", e);
        if (integration != null) {
            integration.readySignal().ifPresent(s -> context.readyGate().waive(s, reason));
        }
    }

    /** Plugin names of the adapters that are running. */
    public List<String> activePlugins() {
        return active.stream().map(Integration::pluginName).toList();
    }

    /** Plugin name → reason, for adapters that failed. */
    public Map<String, String> failures() {
        return Map.copyOf(failed);
    }

    private <T> Optional<T> find(Class<T> type) {
        for (Integration i : active) {
            Optional<T> s = i.service(type);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    public BlockVisuals blockVisuals() {
        return find(BlockVisuals.class).orElse(Absent.BLOCK_VISUALS);
    }

    public CustomBlocks customBlocks() {
        return find(CustomBlocks.class).orElse(Absent.CUSTOM_BLOCKS);
    }

    public CustomItems customItems() {
        return find(CustomItems.class).orElse(Absent.CUSTOM_ITEMS);
    }

    /** M1-INST-03: CraftEngine pack rebuild (absent: {@link PackRebuilder#available()} is false). */
    public PackRebuilder packRebuilder() {
        return find(PackRebuilder.class).orElse(Absent.PACK_REBUILDER);
    }

    /** M1-INST-03: CraftEngine pool counts (absent: always empty). */
    public VisualPools visualPools() {
        return find(VisualPools.class).orElse(Absent.VISUAL_POOLS);
    }

    public EntityModels entityModels() {
        return find(EntityModels.class).orElse(Absent.ENTITY_MODELS);
    }

    /** M1-INT-04: our placeholders as registered in BetterHud (absent: none registered). */
    public HudPlaceholders hudPlaceholders() {
        return find(HudPlaceholders.class).orElse(Absent.HUD_PLACEHOLDERS);
    }

    /** Always the shared snapshot cache; {@link HudBridge#isActive()} says whether anything renders it. */
    public HudBridge hud() {
        return context.hudCache();
    }
}
