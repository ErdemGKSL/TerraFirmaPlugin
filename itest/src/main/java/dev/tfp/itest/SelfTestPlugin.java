/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.ready.ReadyGate;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The in-server self-test (M1-TEST-01). Inactive unless the server runs with {@code -Dtfp.selftest=true}
 * ({@code :itest:runSelfTest} sets it), so it can never stop a real server by accident.
 *
 * <ol>
 *   <li>{@code onLoad}: attaches {@link Log4jTap} to the server's root logger (before any plugin is
 *       enabled, so CraftEngine's config load is seen). If that fails, {@code logs/latest.log} is scanned
 *       at the end instead.</li>
 *   <li>{@link ServerLoadEvent} (startup): starts the {@code tfp-selftest} thread, which runs
 *       {@link SelfTestSuite} with {@link CaseRunner} (the ready gate first), adds the two log cases
 *       ({@link LogFindings}), writes {@code selftest-report.xml} (JUnit) in the server folder and shuts the
 *       server down from the global region thread.</li>
 * </ol>
 * Properties: {@code tfp.selftest.readySeconds} (default 180), {@code tfp.selftest.report} (report path).
 */
public final class SelfTestPlugin extends JavaPlugin implements Listener {
    static final String ACTIVE = "tfp.selftest";
    private static final String MAIN_PLUGIN = "TerraFirmaPlugin";

    private final LogFindings findings = new LogFindings(new ThreadAssertMatcher(), new CraftEngineIssueMatcher("tfp"));
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Log4jTap tap;
    private volatile LoggerContext tapContext;

    static boolean active() {
        return Boolean.getBoolean(ACTIVE);
    }

    @Override
    public void onLoad() {
        if (!active()) {
            return;
        }
        try {
            // The server's context (bound to the server class loader), not one for our plugin class loader.
            LoggerContext ctx = (LoggerContext) LogManager.getContext(Bukkit.class.getClassLoader(), false);
            tap = Log4jTap.attach(ctx, findings);
            tapContext = ctx;
            findings.source("live Log4j appender on the root logger");
        } catch (RuntimeException | LinkageError e) {
            findings.source("logs/latest.log scan at the end (no Log4j appender: " + e + ")");
            getLogger().log(Level.WARNING, "Cannot attach the self-test log appender; will scan logs/latest.log", e);
        }
    }

    @Override
    public void onEnable() {
        if (!active()) {
            getLogger().info("Self-test plugin inactive (start the server with -D" + ACTIVE + "=true to run it)");
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Self-test armed; it runs after server startup (log source: " + findings.source() + ")");
    }

    @Override
    public void onDisable() {
        Log4jTap t = tap;
        LoggerContext ctx = tapContext;
        if (t != null && ctx != null) {
            t.detach(ctx);
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() != ServerLoadEvent.LoadType.STARTUP || !started.compareAndSet(false, true)) {
            return;
        }
        Thread.ofPlatform().name("tfp-selftest").daemon().start(this::runAndStop);
    }

    private void runAndStop() {
        Duration readyLimit = Duration.ofSeconds(Long.getLong("tfp.selftest.readySeconds", 180));
        Path report = Path.of(System.getProperty("tfp.selftest.report", "selftest-report.xml"));
        List<CaseResult> results = new ArrayList<>();
        try {
            results.addAll(new CaseRunner(line -> getLogger().info("[selftest] " + line))
                    .runAll(SelfTestSuite.cases(readyLimit), new ServerContext()));
        } catch (Throwable t) {
            results.add(CaseResult.error("self-test runner", t.toString(), CaseRunner.stackTrace(t), Duration.ZERO));
        }
        if (tap == null) {
            try {
                findings.scan(Files.readAllLines(Path.of("logs", "latest.log"), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                results.add(CaseResult.error("read logs/latest.log", e.toString(), "", Duration.ZERO));
            }
        }
        results.addAll(findings.results());
        try {
            JUnitXmlReport.write(report, JUnitXmlReport.toXml(SelfTestSuite.NAME, results, properties(), Instant.now()));
            long bad = results.stream().filter(r -> r.status() == CaseResult.Status.FAILED
                    || r.status() == CaseResult.Status.ERROR).count();
            getLogger().info("[selftest] " + results.size() + " case(s), " + bad + " failing; report: "
                    + report.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "[selftest] cannot write " + report.toAbsolutePath(), e);
        } finally {
            getServer().getGlobalRegionScheduler().execute(this, () -> getServer().shutdown());
        }
    }

    private Map<String, String> properties() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("server", getServer().getName() + " " + getServer().getVersion());
        p.put("minecraft", getServer().getMinecraftVersion());
        p.put("folia", Boolean.toString(isFolia()));
        p.put("java", Runtime.version().toString());
        p.put("tfp.assertThreads", Boolean.toString(Boolean.getBoolean("tfp.assertThreads")));
        p.put("log.source", findings.source());
        for (String name : new String[] {MAIN_PLUGIN, "CraftEngine", "BetterModel", "BetterHud", "packetevents"}) {
            Plugin plugin = getServer().getPluginManager().getPlugin(name);
            p.put("plugin." + name, plugin == null ? "missing"
                    : plugin.getPluginMeta().getVersion() + (plugin.isEnabled() ? "" : " (disabled)"));
        }
        return p;
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Cases see the main plugin through this. */
    private final class ServerContext implements SelfTestContext {
        private final long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();

        @Override
        public TfpPlugin tfp() {
            Plugin p = getServer().getPluginManager().getPlugin(MAIN_PLUGIN);
            if (!(p instanceof TfpPlugin tfp) || !p.isEnabled()) {
                throw new AssertionError(MAIN_PLUGIN + " is " + (p == null ? "not installed" : "not enabled"));
            }
            return tfp;
        }

        @Override
        public ReadyGate readyGate() {
            return tfp().readyGate();
        }

        @Override
        public long jvmStartMillis() {
            return jvmStart;
        }
    }
}
