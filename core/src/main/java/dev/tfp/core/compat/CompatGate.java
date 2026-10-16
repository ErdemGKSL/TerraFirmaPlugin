/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.compat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The startup compatibility decision (M1-BOOT-02), as a pure function of the shipped
 * {@link CompatSpec} and the running {@link Environment}. The Paper side only reads the environment
 * and logs the findings.
 *
 * <ul>
 *   <li>Java, Minecraft or an installed client plugin below its minimum: {@link Severity#REFUSE}.</li>
 *   <li>Minecraft or a plugin newer than anything tested, or a version we cannot parse: {@link Severity#WARN}, continue.</li>
 *   <li>A client plugin that is not installed: {@link Severity#WARN}, continue in degraded mode without its features.</li>
 * </ul>
 */
public final class CompatGate {
    private CompatGate() {
    }

    public enum Severity { INFO, WARN, REFUSE }

    /** One line of the verdict. {@code subject} is "Java", "Minecraft" or the plugin name. */
    public record Finding(Severity severity, String subject, String message) {
    }

    /**
     * What is running.
     *
     * @param minecraft   e.g. {@code 1.21.11}
     * @param javaFeature {@code Runtime.version().feature()}
     * @param plugins     installed plugin name → version string; a client plugin absent from the map is not installed
     */
    public record Environment(String minecraft, int javaFeature, Map<String, String> plugins) {
        public Environment {
            plugins = Map.copyOf(plugins);
        }
    }

    /**
     * The verdict: all findings in check order (Java, Minecraft, then plugins in compat.yml order),
     * and the client plugins that are not installed (their features are off: degraded mode).
     */
    public record Report(List<Finding> findings, Set<String> missing) {
        public Report {
            findings = List.copyOf(findings);
            missing = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(missing));
        }

        /** True if we must not enable. */
        public boolean refused() {
            return findings.stream().anyMatch(f -> f.severity() == Severity.REFUSE);
        }

        public List<Finding> at(Severity severity) {
            return findings.stream().filter(f -> f.severity() == severity).toList();
        }
    }

    public static Report evaluate(CompatSpec spec, Environment env) {
        List<Finding> out = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        checkJava(spec, env, out);
        checkMinecraft(spec, env, out);
        for (Map.Entry<String, CompatSpec.PluginRequirement> e : spec.plugins().entrySet()) {
            String installed = env.plugins().get(e.getKey());
            if (installed == null) {
                missing.add(e.getKey());
            }
            checkPlugin(e.getKey(), e.getValue(), installed, out);
        }
        return new Report(out, missing);
    }

    private static void checkJava(CompatSpec spec, Environment env, List<Finding> out) {
        if (env.javaFeature() < spec.java()) {
            out.add(new Finding(Severity.REFUSE, "Java", "Java " + env.javaFeature() + " is too old: TerraFirmaPlugin needs Java "
                    + spec.java() + " or newer (BetterModel and BetterHud need it too). Start the server with Java "
                    + spec.java() + "."));
        } else {
            out.add(new Finding(Severity.INFO, "Java", "Java " + env.javaFeature() + " OK"));
        }
    }

    private static void checkMinecraft(CompatSpec spec, Environment env, List<Finding> out) {
        String supported = spec.minecraft().stream().map(CompatSpec.Range::toString).collect(Collectors.joining(", "));
        String tested = spec.tested().stream().map(Version::toString).collect(Collectors.joining(", "));
        Optional<Version> parsed = Version.tryParse(env.minecraft());
        if (parsed.isEmpty()) {
            out.add(new Finding(Severity.WARN, "Minecraft", "cannot read the Minecraft version '" + env.minecraft()
                    + "'; continuing. Supported: " + supported + "."));
            return;
        }
        Version mc = parsed.get();
        if (mc.isOlderThan(spec.lowestMinecraft())) {
            out.add(new Finding(Severity.REFUSE, "Minecraft", "Minecraft " + mc + " is not supported. Supported: "
                    + supported + " (tested: " + tested + "). Update the server."));
            return;
        }
        boolean inRange = spec.minecraft().stream().anyMatch(r -> r.contains(mc));
        if (!inRange) {
            out.add(new Finding(Severity.WARN, "Minecraft", "Minecraft " + mc + " is newer than this build was made for ("
                    + supported + ", tested: " + tested + "). Continuing, but expect problems; check for a plugin update."));
        } else if (spec.tested().contains(mc)) {
            out.add(new Finding(Severity.INFO, "Minecraft", "Minecraft " + mc + " (tested)"));
        } else {
            out.add(new Finding(Severity.INFO, "Minecraft", "Minecraft " + mc + " is supported but not tested in CI (tested: "
                    + tested + ")"));
        }
    }

    private static void checkPlugin(String name, CompatSpec.PluginRequirement req, String installed, List<Finding> out) {
        if (installed == null) {
            out.add(new Finding(Severity.WARN, name, "not installed: running without " + req.provides() + ". Install "
                    + name + " " + req.min() + " or newer and restart for the full game."));
            return;
        }
        Optional<Version> parsed = Version.tryParse(installed);
        if (parsed.isEmpty()) {
            out.add(new Finding(Severity.WARN, name, "cannot read " + name + " version '" + installed + "'; assuming it is compatible"
                    + " (needs " + req.min() + " or newer)."));
            return;
        }
        Version v = parsed.get();
        if (v.isOlderThan(req.min())) {
            out.add(new Finding(Severity.REFUSE, name, name + " " + v + " is too old: TerraFirmaPlugin needs " + name + " "
                    + req.min() + " or newer. Update " + name + " and restart."));
        } else if (v.isNewerThan(req.newestTested())) {
            out.add(new Finding(Severity.WARN, name, name + " " + v + " is newer than the newest tested version ("
                    + req.newestTested() + "). Continuing; report problems."));
        } else {
            out.add(new Finding(Severity.INFO, name, name + " " + v + " OK"));
        }
    }
}
