/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.lifecycle;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PAR-WORLD-63: the {@link PlantStageSource} of the running server. Holds the table read from the jar, the remap resolved
 * against the current CraftEngine load ({@link #resolve}, CPU pool; again after each CraftEngine reload) and the TFC
 * overworld of each online player (join and world change events, player thread). The packet handler reads it on Netty
 * threads: every field is a volatile snapshot or a concurrent map.
 */
public final class PlantStageService implements PlantStageSource, Listener {
    private final GlobalClockHolder clock;
    private final Logger log;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, Viewer> worlds = new ConcurrentHashMap<>();
    private volatile PlantStageTable table;
    private volatile PlantStageRemap remap = PlantStageRemap.EMPTY;

    public PlantStageService(GlobalClockHolder clock, Logger log) {
        this.clock = clock;
        this.log = log;
    }

    @Override
    public PlantStageRemap remap() {
        return remap;
    }

    @Override
    public Viewer viewer(UUID player) {
        return viewers.get(player);
    }

    @Override
    public ClockSnapshot clock() {
        return clock.get();
    }

    /** CPU pool: the table has been read from the jar. */
    public void table(PlantStageTable table) {
        this.table = table;
    }

    /**
     * CPU pool: resolves the table's CE states to server ids against the current CraftEngine load. No-op until both the
     * table and CraftEngine are loaded; called again on every CraftEngine reload.
     */
    public void resolve(BlockVisuals visuals) {
        PlantStageTable t = table;
        if (t == null || !visuals.isLoaded()) {
            return;
        }
        ServerStateIds ids = null;
        for (String state : t.entries().keySet()) {
            Optional<BlockData> data = visuals.blockData(BlockStateRef.parse(state));
            if (data.isPresent()) {
                try {
                    ids = ServerStateIds.forServer(data.get());
                } catch (ReflectiveOperationException | RuntimeException e) {
                    log.warning("Plant stage looks: cannot read server block state ids (" + e + "); plants keep their blooming look");
                    return;
                }
                break;
            }
        }
        if (ids == null) {
            remap = PlantStageRemap.EMPTY;
            log.info("Plant stage looks: none of the " + t.entries().size() + " plant states is loaded in CraftEngine");
            return;
        }
        ServerStateIds resolver = ids;
        ToIntFunction<String> serverId = s -> visuals.blockData(BlockStateRef.parse(s)).map(resolver::id).orElse(-1);
        install(PlantStageRemap.resolve(t, serverId));
    }

    /** Publishes a resolved remap (also the test seam). */
    void install(PlantStageRemap resolved) {
        remap = resolved;
        log.info("Plant stage looks: " + resolved.size() + " plant states switch to their dormant look with the seasons");
    }

    /** Test seam: a viewer without a Bukkit world. */
    void track(UUID player, Viewer viewer) {
        if (viewer == null) {
            viewers.remove(player);
        } else {
            viewers.put(player, viewer);
        }
    }

    private void track(Player player) {
        World world = player.getWorld();
        Viewer v = worlds.computeIfAbsent(world.getUID(), id -> viewerOf(world));
        track(player.getUniqueId(), v.world() == null ? null : v);
    }

    /** The viewer of a world, or a marker with a null world id when it is no TFC overworld. Player or global thread. */
    private static Viewer viewerOf(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL || !(world.getGenerator() instanceof TfcChunkGenerator)) {
            return new Viewer(null, 0, 0f, null);
        }
        long seed = world.getSeed();
        return new Viewer(world.getUID(), world.getMinHeight(), WorldSettingsLookup.climate(seed).hemisphereScale(),
            PlantStageClimate.forSeed(seed));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }
}
