package dev.tfp.spike.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-ENV-01: boots next to CraftEngine, BetterModel, BetterHud and PacketEvents and writes a
 * report of what it found. With {@code -Dtfp.spike.autostop=<seconds>} it shuts the server down
 * that many seconds after {@link ServerLoadEvent}, so the matrix script can run unattended.
 */
public final class HarnessPlugin extends JavaPlugin implements Listener {
    private static final List<String> DEPENDENCIES =
            List.of("CraftEngine", "BetterModel", "BetterHud", "packetevents");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("[spike] enabled on thread " + Thread.currentThread().getName());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        long stopAfter = Long.getLong("tfp.spike.autostop", -1);
        // Give the dependencies (CE pack build, BM model load) time to settle before reporting.
        long delay = stopAfter > 0 ? stopAfter : 15;
        Bukkit.getAsyncScheduler().runDelayed(this, task -> {
            writeReport();
            if (stopAfter > 0) {
                Bukkit.getGlobalRegionScheduler().execute(this, Bukkit::shutdown);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void writeReport() {
        List<String> lines = new ArrayList<>();
        lines.add("server=" + Bukkit.getName() + " " + Bukkit.getVersion());
        lines.add("minecraft=" + Bukkit.getMinecraftVersion());
        lines.add("java=" + Runtime.version());
        lines.add("folia=" + isFolia());
        boolean ok = true;
        for (String name : DEPENDENCIES) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            String state = p == null ? "MISSING" : (p.isEnabled() ? "ENABLED" : "DISABLED");
            if (!"ENABLED".equals(state)) {
                ok = false;
            }
            lines.add("plugin." + name + "=" + state + (p == null ? "" : " " + p.getPluginMeta().getVersion()));
        }
        lines.add("result=" + (ok ? "PASS" : "FAIL"));
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.write(getDataFolder().toPath().resolve("report.txt"), lines);
            Files.write(Path.of("spike-report.txt"), lines);
        } catch (IOException e) {
            getLogger().severe("[spike] cannot write report: " + e);
        }
        lines.forEach(l -> getLogger().info("[spike] " + l));
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
