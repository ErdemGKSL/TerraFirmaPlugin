/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import dev.tfp.core.compat.CompatGate;
import dev.tfp.core.compat.CompatGate.Environment;
import dev.tfp.core.compat.CompatGate.Finding;
import dev.tfp.core.compat.CompatGate.Report;
import dev.tfp.core.compat.CompatSpec;
import dev.tfp.paper.config.Yamls;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * The Bukkit side of the M1-BOOT-02 gate: reads the shipped {@code compat.yml} and the running
 * versions, runs the pure {@link CompatGate}, and logs the verdict.
 */
public final class CompatCheck {
    /** Resource path of the shipped table (copied from {@code content/compat.yml} at build time). */
    public static final String RESOURCE = "compat.yml";

    private CompatCheck() {
    }

    public static CompatSpec loadSpec() {
        try (InputStream in = CompatCheck.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the plugin jar");
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> root = (Map<String, ?>) Yamls.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return CompatSpec.fromMap(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    /** What is running: Minecraft, Java, and the installed version of each client plugin in the spec. */
    public static Environment environment(Server server, CompatSpec spec) {
        Map<String, String> installed = new HashMap<>();
        for (String name : spec.plugins().keySet()) {
            Plugin p = server.getPluginManager().getPlugin(name);
            if (p != null) {
                installed.put(name, p.getPluginMeta().getVersion());
            }
        }
        return new Environment(server.getMinecraftVersion(), Runtime.version().feature(), installed);
    }

    /** Evaluates and logs: refusals as SEVERE in one banner, warnings as WARNING, the rest as INFO. */
    public static Report run(Server server, Logger log) {
        CompatSpec spec = loadSpec();
        Report report = CompatGate.evaluate(spec, environment(server, spec));
        log(report, log);
        return report;
    }

    static void log(Report report, Logger log) {
        for (Finding f : report.findings()) {
            switch (f.severity()) {
                case INFO -> log.info("[compat] " + f.message());
                case WARN -> log.warning("[compat] " + f.subject() + ": " + f.message());
                case REFUSE -> { }
            }
        }
        if (report.refused()) {
            StringBuilder sb = new StringBuilder("\n")
                    .append("==============================================================\n")
                    .append(" TerraFirmaPlugin will NOT enable: unsupported setup\n");
            for (Finding f : report.at(CompatGate.Severity.REFUSE)) {
                sb.append("  - ").append(f.message()).append('\n');
            }
            sb.append("==============================================================");
            log.severe(sb.toString());
        } else if (!report.missing().isEmpty()) {
            log.warning("[compat] Degraded mode: " + String.join(", ", report.missing())
                    + " not installed; the features they provide are off.");
        }
    }
}
