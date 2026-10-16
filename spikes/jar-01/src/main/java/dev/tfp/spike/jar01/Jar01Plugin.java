package dev.tfp.spike.jar01;

import dev.tfp.spike.jar01.Jar01Checks.Check;
import dev.tfp.spike.jar01.Jar01Checks.Expectation;
import dev.tfp.spike.jar01.Jar01Checks.Named;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-JAR-01: runs every API check once in onEnable ("enable") and once 5 s after ServerLoadEvent
 * ("load+5s"). Each check is isolated; the report is written to jar01-report.txt in the server's
 * working directory after each pass completes.
 */
public final class Jar01Plugin extends JavaPlugin implements Listener {
    private static final Path REPORT = Path.of("jar01-report.txt");

    private final Object lock = new Object();
    private final List<String> lines = new ArrayList<>();
    private final List<Pass> passes = new ArrayList<>();

    /** One battery run; finishes when every expected check has reported, or on timeout. */
    private final class Pass {
        final String label;
        final Set<String> expected;
        final Set<String> seen = new java.util.HashSet<>();
        boolean finished;

        Pass(String label, Set<String> expected) {
            this.label = label;
            this.expected = expected;
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register("jar01", new Jar01Command()));
        header();
        startPass("enable");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        Bukkit.getAsyncScheduler().runDelayed(this, task -> startPass("load+5s"), 5, TimeUnit.SECONDS);
    }

    private void header() {
        synchronized (lock) {
            lines.add("# jar01-report (M0-JAR-01)");
            lines.add("# server=" + Bukkit.getName() + " " + Bukkit.getVersion());
            lines.add("# minecraft=" + Bukkit.getMinecraftVersion() + " java=" + Runtime.version());
            lines.add("# folia=" + isFolia());
            lines.add("# format: pass | check | PASS|FAIL | detail");
        }
    }

    private void startPass(String label) {
        List<Named> pure = Jar01Checks.pure(this);
        Set<String> expected = new java.util.HashSet<>();
        pure.forEach(n -> expected.add(n.name()));
        expected.addAll(List.of("scheduler.global", "command.lifecycle", "scheduler.region", "chunk.pdc", "scheduler.async"));
        Pass pass = new Pass(label, expected);
        synchronized (lock) {
            passes.add(pass);
        }
        World world = Bukkit.getWorlds().get(0);

        Bukkit.getGlobalRegionScheduler().execute(this, () -> runGlobal(pass, pure));
        Bukkit.getRegionScheduler().execute(this, world, 0, 0, () -> runRegion(pass, world));
        Bukkit.getAsyncScheduler().runNow(this, task -> runAsync(pass, world));
        Bukkit.getAsyncScheduler().runDelayed(this, task -> timeout(pass), 20, TimeUnit.SECONDS);
    }

    private void runGlobal(Pass pass, List<Named> pure) {
        record(pass, "scheduler.global", true,
                "ran on thread=" + Thread.currentThread().getName() + " isPrimaryThread=" + Bukkit.isPrimaryThread());
        for (Named n : pure) {
            run(pass, n);
        }
        run(pass, new Named("command.lifecycle", () -> {
            int before = Jar01Command.RUNS.get();
            boolean handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "jar01 ping");
            Jar01Checks.expect(handled && Jar01Command.RUNS.get() > before,
                    "dispatch returned " + handled + ", runs " + before + "->" + Jar01Command.RUNS.get());
            return "dispatch=true executeRuns=" + Jar01Command.RUNS.get();
        }));
    }

    private void runRegion(Pass pass, World world) {
        run(pass, new Named("scheduler.region", () -> {
            boolean owned = Bukkit.isOwnedByCurrentRegion(world, 0, 0);
            boolean ownedFar = Bukkit.isOwnedByCurrentRegion(world, 4096, 4096);
            Jar01Checks.expect(owned, "isOwnedByCurrentRegion(world,0,0) was false inside region task");
            return "thread=" + Thread.currentThread().getName() + " owned(0,0)=" + owned + " owned(4096,4096)=" + ownedFar;
        }));
        run(pass, new Named("chunk.pdc", () -> {
            Chunk chunk = world.getChunkAt(0, 0);
            NamespacedKey key = new NamespacedKey(this, "chunk-probe");
            chunk.getPersistentDataContainer().set(key, PersistentDataType.STRING, "v-" + pass.label);
            String read = chunk.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            Jar01Checks.expect(("v-" + pass.label).equals(read), "read=" + read);
            return "chunk(0,0) loaded=" + chunk.isLoaded() + " pdc read=" + read;
        }));
    }

    private void runAsync(Pass pass, World world) {
        run(pass, new Named("scheduler.async", () -> {
            String thread = Thread.currentThread().getName();
            boolean owned = Bukkit.isOwnedByCurrentRegion(world, 0, 0);
            Jar01Checks.expect(!"Server thread".equals(thread), "ran on server thread");
            return "thread=" + thread + " isOwnedByCurrentRegion(0,0)=" + owned;
        }));
    }

    private void run(Pass pass, Named check) {
        try {
            record(pass, check.name(), true, check.body().run());
        } catch (Expectation e) {
            record(pass, check.name(), false, "expectation failed: " + e.getMessage());
        } catch (Throwable t) {
            record(pass, check.name(), false, t.getClass().getName() + ": " + t.getMessage());
        }
    }

    private void timeout(Pass pass) {
        synchronized (lock) {
            if (pass.finished) {
                return;
            }
            for (String name : pass.expected) {
                if (!pass.seen.contains(name)) {
                    lines.add(pass.label + " | " + name + " | FAIL | not reported within 20 s");
                }
            }
            finish(pass);
        }
    }

    private void record(Pass pass, String name, boolean ok, String detail) {
        synchronized (lock) {
            if (pass.finished) {
                return;
            }
            lines.add(pass.label + " | " + name + " | " + (ok ? "PASS" : "FAIL") + " | " + detail.replace('\n', ' '));
            pass.seen.add(name);
            if (pass.seen.containsAll(pass.expected)) {
                finish(pass);
            }
        }
    }

    private void finish(Pass pass) {
        pass.finished = true;
        lines.add("# pass " + pass.label + " complete");
        try {
            Files.write(REPORT, lines);
        } catch (IOException e) {
            getLogger().severe("[jar01] cannot write report: " + e);
        }
        getLogger().info("[jar01] pass " + pass.label + " complete, report at " + REPORT.toAbsolutePath());
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
