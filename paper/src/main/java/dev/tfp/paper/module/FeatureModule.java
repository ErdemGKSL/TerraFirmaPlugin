/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

/**
 * One gameplay feature's Paper wiring (PAR-INFRA-01): its listeners, services, scheduled work and
 * {@code /tfp debug} literals, without editing {@code TfpPlugin} or {@code TfpCommands}.
 *
 * <p>To add a feature: write one class implementing this interface (public, with a public no-argument constructor;
 * it normally lives in the feature's own package) and append its binary name as one line to
 * {@code paper/src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule}. {@link FeatureModules}
 * finds it with {@link java.util.ServiceLoader}.
 *
 * <p>Lifecycle, all on the server's startup thread:
 * <ol>
 *   <li>bootstrap: the instance is created and {@link #debugCommands(DebugTree)} adds its literals under
 *       {@code /tfp debug} (before the plugin is enabled; touch no Bukkit API there);</li>
 *   <li>{@code onEnable}: {@link #enable(ModuleContext)} in service-file order, after the plugin's own services and
 *       the packet handler registry exist and before the packet layer is installed;</li>
 *   <li>{@code onDisable}: {@link #disable()} in reverse order, only for modules whose {@code enable} returned,
 *       before the integrations and the scheduler stop.</li>
 * </ol>
 * A module whose {@code enable} throws is logged and stays disabled; its debug commands then answer "not enabled".
 */
public interface FeatureModule {
    /** A stable id ({@code [a-z0-9_]+}), unique among modules; used in logs and for dependency lookups. */
    String id();

    /** Registers listeners, services and scheduled work. Throwing leaves this module disabled. */
    void enable(ModuleContext context) throws Exception;

    /** Releases what {@link #enable} started that the plugin's own shutdown does not (listeners are unregistered anyway). */
    default void disable() {
    }

    /** Adds this module's {@code /tfp debug <literal>} subcommands. Called once, at bootstrap. */
    default void debugCommands(DebugTree tree) {
    }
}
