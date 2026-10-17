/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.core.diagnostics.CraftEngineIssueMatcher;
import dev.tfp.itest.cases.SuppressionCase;
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
 *       at the end instead. Installs the self-test CraftEngine pack ({@link SelfTestPack}).</li>
 *   <li>{@link ServerLoadEvent} (startup): starts the {@code tfp-selftest} thread, which runs
 *       {@link SelfTestSuite} with {@link CaseRunner} (the ready gate first), adds the two log cases
 *       ({@link LogFindings}), writes {@code selftest-report.xml} (JUnit, skipped cases with their reasons)
 *       in the server folder and shuts the server down from the global region thread; if any case failed the
 *       process exits with status 1 ({@link ExitStatus}), 2 if the report could not be written.</li>
 * </ol>
 * Properties: {@code tfp.selftest.readySeconds} (default 180), {@code tfp.selftest.report} (report path).
 */
public final class SelfTestPlugin extends JavaPlugin implements Listener {
    static final String ACTIVE = "tfp.selftest";
    private static final String MAIN_PLUGIN = "TerraFirmaPlugin";

    private final LogFindings findings = new LogFindings(new ThreadAssertMatcher(), new CraftEngineIssueMatcher("tfp"),
            new CraftEngineIssueMatcher(SelfTestPack.NAMESPACE));

    {
        // inst.suppression (M1-INST-04) asserts these lines; registered before the tap attaches in onLoad.
        findings.keepLinesContaining(SuppressionCase.STARTUP_LINE, SuppressionCase.FORM_LINE);
    }
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Log4jTap tap;
    private volatile LoggerContext tapContext;
    private volatile Thread serverThread;

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
        // M1-INT-02: our test block pack, before CraftEngine enables and reads its packs.
        try {
            getLogger().info("Self-test CraftEngine pack: " + SelfTestPack.install(getDataPath().toAbsolutePath().getParent(),
                    SelfTestPlugin.class.getClassLoader(), getPluginMeta().getVersion()));
        } catch (IOException | RuntimeException | LinkageError e) {
            getLogger().log(Level.WARNING, "Cannot install the self-test CraftEngine pack; ce.block_roundtrip uses our "
                    + "generated blocks only", e);
        }
    }

    @Override
    public void onEnable() {
        if (!active()) {
            getLogger().info("Self-test plugin inactive (start the server with -D" + ACTIVE + "=true to run it)");
            return;
        }
        serverThread = Thread.currentThread();
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
        CaseRunner runner = new CaseRunner(line -> getLogger().info("[selftest] " + line));
        int code = CaseRunner.EXIT_FAILED;
        try {
            CaseRunner.Outcome outcome = runner.runAndReport(SelfTestSuite.cases(readyLimit), new ServerContext(),
                    this::logCases, report, properties(), Instant.now());
            code = outcome.exitCode();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "[selftest] the run itself failed", t);
        } finally {
            // M1-TEST-01: a failing run makes the server process exit non-zero (after its normal stop).
            ExitStatus.arrange(code, serverThread, getLogger());
            getServer().getGlobalRegionScheduler().execute(this, () -> getServer().shutdown());
        }
    }

    /** The two log cases, after the cases ran (scanning logs/latest.log first if no appender was attached). */
    private List<CaseResult> logCases() {
        List<CaseResult> out = new ArrayList<>();
        if (tap == null) {
            try {
                findings.scan(Files.readAllLines(Path.of("logs", "latest.log"), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                out.add(CaseResult.error("read logs/latest.log", e.toString(), "", Duration.ZERO));
            }
        }
        out.addAll(findings.results());
        return out;
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

        @Override
        public LogFindings logFindings() {
            return findings;
        }

        @Override
        public boolean liveLog() {
            return tap != null;
        }

        @Override
        public Path dataDir() {
            return getDataPath();
        }
    }
}
