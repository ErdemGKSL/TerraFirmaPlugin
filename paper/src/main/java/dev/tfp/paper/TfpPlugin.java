/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper;

import dev.tfp.core.calendar.CalendarFormat;
import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.compat.CompatGate;
import dev.tfp.core.compat.Version;
import dev.tfp.core.install.InstallStatus;
import dev.tfp.core.visuals.Suppression;
import dev.tfp.core.visuals.SuppressionSet;
import dev.tfp.core.visuals.VisualIndex;
import dev.tfp.core.visuals.VisualSelection;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.showcase.Showcase;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.SnowCatchUp;
import dev.tfp.core.weather.SnowRule;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.debug.ShowcasePlacer;
import dev.tfp.paper.blockdata.ChunkDataStore;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.boot.CompatCheck;
import dev.tfp.paper.calendar.CalendarService;
import dev.tfp.paper.climate.PlayerClimateService;
import dev.tfp.paper.climate.SeasonalBiomeService;
import dev.tfp.paper.weather.ChunkSnowTime;
import dev.tfp.paper.weather.PrecipitationService;
import dev.tfp.paper.weather.SnowService;
import dev.tfp.paper.boot.TfpCommands;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.install.Installer;
import dev.tfp.paper.install.PackRebuildTrigger;
import dev.tfp.paper.install.PendingHudEdit;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.packet.CreativeReturnHandler;
import dev.tfp.paper.packet.NoopPacketHandler;
import dev.tfp.paper.packet.PacketHandlerRegistry;
import dev.tfp.paper.packet.PacketLayer;
import dev.tfp.paper.packet.PacketLayers;
import dev.tfp.paper.packet.SeasonalBiomeHandler;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataFiles;
import dev.tfp.paper.player.PlayerDataListener;
import dev.tfp.paper.player.WaterDrinkListener;
import dev.tfp.paper.food.FoodMergeListener;
import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.player.NutritionEatListener;
import dev.tfp.paper.player.PlayerNeedsHudService;
import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.ready.ReadyGate.Signal;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.suppression.SuppressionListener;
import dev.tfp.paper.worldgen.FillTiming;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import dev.tfp.paper.worldgen.TfcFirstJoinSpawn;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point. Phases follow {@code dependency-bootstrap} §2 / ARCHITECTURE.md §12:
 * <ol>
 *   <li>bootstrap ({@link dev.tfp.paper.boot.TfpBootstrap}): datapack, Brigadier commands;</li>
 *   <li>{@link #onLoad()}: compat gate → config → (M1-INST-*) install content into CE/BetterHud,
 *       config edits → adapters' {@code onLoad} (CraftEngine behaviour registration);</li>
 *   <li>{@link #onEnable()}: refuse if the gate said so; scheduler; ready gate (waive what is missing);
 *       adapters' {@code onEnable} (CraftEngine listeners, BetterModel subscriptions, BetterHud
 *       placeholders); registries (first load signals the ready gate); player data store; packet layer.</li>
 * </ol>
 */
public final class TfpPlugin extends JavaPlugin implements TfpCommands.Handler {
    /** Log the pending ready signals if the gate is still closed this long after enable. */
    private static final long READY_WARN_SECONDS = 60;
    /** Player data autosave interval (ticks): 5 minutes. */
    private static final long AUTOSAVE_TICKS = 5 * 60 * 20;
    /** A player loaded at pre-login but not online after this long was refused by another plugin. */
    private static final long ABSENT_GRACE_NANOS = TimeUnit.SECONDS.toNanos(60);
    /** Reach of {@code /tfp debug block}. */
    private static final int DEBUG_REACH = 8;

    private final ReadyGate readyGate = new ReadyGate();
    private volatile Installer.Result install;
    /** Set when a chunk asked for the CraftEngine table before it was loaded in this run (such a chunk is refused). */
    private final AtomicBoolean chunkBeforeCraftEngine = new AtomicBoolean();
    private Installer.Inputs installInputs;
    private Path contentRoot;
    private VisualSelection visualSelection;
    private VisualIndex visualIndex;
    private volatile PackRebuildTrigger packRebuild;
    private CompatGate.Report compat;
    private ConfigService config;
    private volatile Scheduler scheduler;
    private volatile Integrations integrations;
    private final Messages messages = Messages.fromJar();
    private final ChunkDataStore chunkData = new PdcChunkDataStore();
    private volatile PacketHandlerRegistry packets;
    private volatile PacketLayer packetLayer = PacketLayers.none("not enabled");
    private volatile RegistryService registryService;
    private volatile PlayerDataStore playerData;
    /** M2-CAL-01: the published clock (lock-free reads anywhere) and the service that advances it. */
    private final GlobalClockHolder clock = new GlobalClockHolder();
    private volatile CalendarService calendar;
    /** M3-SURV-01: food queries (stage, tooltip line, stack rule) at the current player tick. */
    private volatile ItemFoodHooks food;
    /** M3-HEAT-01: item heat queries (temperature now, level, tooltip line). */
    private volatile ItemHeatHooks heat;
    /** M3-HEAT-01: the item heat definitions of the current registry snapshot (empty until the registries load). */
    private final ItemHeatDefinitionsCache itemHeatDefinitions = new ItemHeatDefinitionsCache(this::registries);
    /** M3-SURV-02: the food definitions of the current registry snapshot (empty until the registries load). */
    private final FoodDefinitionsCache foodDefinitions = new FoodDefinitionsCache(this::registries);
    /** M2-CLIM-03: the player's ambient temperature for the HUD, and the reading for {@code /tfp debug chunk}. */
    private volatile PlayerClimateService climate;
    /** M2-WTH-01: what falls at the player's position (rain or snow), for {@code /tfp debug chunk}. */
    private volatile PrecipitationService precipitation;
    /** M2-WTH-02/04: the snow service, for {@code /tfp debug snowtime} (its next-unload override). */
    private volatile SnowService snow;
    /** M2-SEA-02: the seasonal biome tables behind the chunk packet rewrite. */
    private volatile SeasonalBiomeService seasonalBiomes;
    /** {@code /tfp debug showcase}: our block ids from the shipped id_map.json, read once. */
    private volatile CompletableFuture<List<String>> showcaseIds;
    private final HudSnapshotCache hudCache = new HudSnapshotCache(() -> {
        Integrations i = integrations;
        return i != null && i.activePlugins().contains("BetterHud");
    });

    @Override
    public void onLoad() {
        try {
            compat = CompatCheck.run(getServer(), getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Compatibility check failed; TerraFirmaPlugin will not enable", e);
            compat = null;
            return;
        }
        if (compat.refused()) {
            return;
        }
        config = new ConfigService(getDataPath().resolve("config.yml"), this::defaultConfigText);
        logConfig(config.load());
        // M1-INST-01/02: our content into CraftEngine/BetterHud, then the config edits (before CraftEngine's
        // onEnable parses its configs). M1-INST-03 (visual profile, pack rebuild trigger) builds on the result.
        install = runInstaller();
        // M1-INT-01..04: discover the adapters of the installed plugins; CraftEngine registers its
        // behaviours/functions/conditions here (must happen in onLoad).
        IntegrationContext ctx = new IntegrationContext(this, getLogger(), readyGate, () -> scheduler, hudCache, clock,
                getDataPath().toAbsolutePath().getParent(), foodDefinitions);
        integrations = Integrations.discover(ctx, name -> getServer().getPluginManager().getPlugin(name) != null,
                TfpPlugin.class.getClassLoader());
        integrations.onLoad();
    }

    private Installer.Result runInstaller() {
        Path pluginsDir = getDataPath().toAbsolutePath().getParent();
        try {
            contentRoot = RegistryService.jarDataRoot(TfpPlugin.class.getClassLoader()).getParent();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Cannot open the plugin jar's /content", e);
            contentRoot = null;
        }
        // M1-INST-03: the visual allocation for visuals.profile and the running version family.
        try {
            visualSelection = VisualSelection.of(config.current().visuals(), Version.parse(getServer().getMinecraftVersion()),
                    getServer().getAllowNether(), getServer().getAllowEnd());
            getLogger().info("Visual allocation: " + visualSelection.id());
            if (contentRoot != null) {
                visualIndex = VisualIndex.parse(java.nio.file.Files.readString(contentRoot.resolve("visuals/index.json")));
            }
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Cannot read the shipped visual allocations; no capacity check this run", e);
        }
        Set<String> installed = new HashSet<>();
        for (String name : new String[] {Installer.CRAFTENGINE, "BetterModel", Installer.BETTERHUD}) {
            if (getServer().getPluginManager().getPlugin(name) != null) {
                installed.add(name);
            }
        }
        installInputs = new Installer.Inputs(config.current().autoInstall(), pluginsDir,
                getDataPath().resolve("install"), contentRoot, getPluginMeta().getVersion(), installed,
                Installer.ceStates(contentRoot), visualSelection);
        return Installer.run(installInputs, getLogger());
    }

    @Override
    public void onEnable() {
        if (compat == null || compat.refused()) {
            getLogger().severe("TerraFirmaPlugin is disabled: see the compatibility errors above.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        scheduler = new ServerScheduler(this, Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
        if (compat.missing().contains("CraftEngine")) {
            readyGate.waive(Signal.CRAFTENGINE_RELOADED, "CraftEngine not installed");
        }
        if (compat.missing().contains("BetterModel")) {
            readyGate.waive(Signal.BETTERMODEL_MODELS_READY, "BetterModel not installed");
        }
        // CRAFTENGINE_RELOADED is signalled by the CE adapter (M1-INT-02), BETTERMODEL_MODELS_READY by the
        // BetterModel adapter (M1-INT-03), REGISTRIES_LOADED by the registry loader's first swap.
        integrations.onEnable();
        enableVisuals();
        enableServices();
        getLogger().info("Integrations: " + (integrations.activePlugins().isEmpty() ? "none"
                : String.join(", ", integrations.activePlugins())));
        readyGate.ready().whenComplete((v, err) -> {
            if (err == null) {
                getLogger().info("TerraFirmaPlugin " + readyGate.describe());
            } else {
                getLogger().log(Level.SEVERE, "TerraFirmaPlugin failed to become ready", err);
            }
        });
        CompletableFuture.runAsync(() -> {
            if (!readyGate.isReady()) {
                getLogger().warning("Not ready after " + READY_WARN_SECONDS + " s, " + readyGate.describe());
            }
        }, CompletableFuture.delayedExecutor(READY_WARN_SECONDS, TimeUnit.SECONDS, scheduler.cpuExecutor()));
    }

    /**
     * M2-GEN-07: the TFC overworld for a default world. Paper calls this when the world loads, after STARTUP plugins are
     * enabled. Set it in {@code bukkit.yml} (the same way as the M0-CE-01 spike):
     * <pre>
     * worlds:
     *   world:
     *     generator: TerraFirmaPlugin          # the seed is the world's seed
     *     # generator: TerraFirmaPlugin:12345  # or an explicit seed
     * </pre>
     * Folia cannot create worlds at runtime, so TFC worlds are the server's default worlds. A bad id logs a warning and
     * uses the world's seed.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        OptionalLong seed;
        try {
            seed = TfcChunkGenerator.parseSeed(id);
        } catch (IllegalArgumentException e) {
            getLogger().warning("world " + worldName + ": " + e.getMessage() + "; using the world's seed");
            seed = OptionalLong.empty();
        }
        return new TfcChunkGenerator(seed, this::blockVisuals, getLogger());
    }

    /**
     * The CraftEngine state table, or null before {@link #onLoad()} built the integrations. A chunk that asks while the table is
     * not loaded is refused by the generator and stays empty, so that is recorded: see {@link #restartSettingTookEffect()}.
     */
    private BlockVisuals blockVisuals() {
        Integrations current = integrations;
        BlockVisuals table = current == null ? null : current.blockVisuals();
        if (table == null || !table.isLoaded()) {
            chunkBeforeCraftEngine.set(true);
        }
        return table;
    }

    /**
     * A restart-only CraftEngine setting changed by the installer (ARCHITECTURE.md §12) only applies after a restart, so the
     * first start is degraded. When CraftEngine loaded in this run before any TFC chunk asked for its table, nothing was refused
     * and the chunks generated from now on are complete: the status is cleared for this run.
     */
    private void restartSettingTookEffect() {
        Installer.Result inst = install;
        if (inst == null || chunkBeforeCraftEngine.get()) {
            return;
        }
        InstallStatus cleared = inst.status().restartApplied();
        if (cleared != inst.status()) {
            install = new Installer.Result(cleared, inst.craftEngine(), inst.betterHud(), inst.skipped(), inst.edits(),
                    inst.packPlan(), inst.packFingerprint());
            getLogger().info("CraftEngine loaded before any TFC chunk was generated: the restart-only setting is in effect for this run");
        }
    }

    /**
     * M1-INST-03: the pool check and the one pack rebuild after CraftEngine loaded; M1-INST-04: the
     * vanilla-block suppression listeners for the installed allocation.
     */
    private void enableVisuals() {
        PackRebuildTrigger trigger = new PackRebuildTrigger(install, getDataPath().resolve("install"),
                integrations.packRebuilder(), integrations.hudReloader(), integrations.visualPools(), visualIndex, visualSelection,
                config.current().visuals().solidReserveWarning(), scheduler.ioExecutor(), getLogger());
        packRebuild = trigger;
        integrations.blockVisuals().onReload(trigger::onCraftEngineReload);
        integrations.blockVisuals().onReload(this::restartSettingTookEffect);
        enablePendingHudEdit(trigger);

        if (contentRoot == null || visualSelection == null) {
            getLogger().warning("No visual allocation: vanilla-block suppression is off");
            return;
        }
        try {
            Path dir = contentRoot.resolve(visualSelection.dir());
            SuppressionSet set = SuppressionSet.parse(java.nio.file.Files.readString(dir.resolve("suppressed_blocks.json")),
                    java.nio.file.Files.readString(dir.resolve("visual_mappings.yml")));
            // Until the M2 generator marks TFC worlds, every world counts: the CraftEngine mappings hide these
            // vanilla blocks in every world anyway.
            SuppressionListener listener = new SuppressionListener(new Suppression(set), world -> true, getLogger());
            getServer().getPluginManager().registerEvents(listener, this);
            getLogger().info("Vanilla-block suppression: " + set.blockIds().size() + " repurposed block(s) of "
                    + visualSelection.id());
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Cannot load the vanilla-block suppression list; suppression is off", e);
        }
    }

    /**
     * M1-INST-02: BetterHud enables after us and writes its config.yml then; apply its pending edits once it has
     * enabled ({@link PendingHudEdit}), or at ServerLoad if it already was.
     */
    private void enablePendingHudEdit(PackRebuildTrigger trigger) {
        if (!Installer.pending(install, dev.tfp.core.install.ConfigEdit.Target.BETTERHUD_CONFIG)) {
            return;
        }
        PendingHudEdit pending = new PendingHudEdit(installInputs, install, integrations.hudReloader(),
                integrations.packRebuilder(), trigger, scheduler.ioExecutor(), getLogger());
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPluginEnable(PluginEnableEvent event) {
                pending.betterHudEnabled(event.getPlugin().getName());
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onServerLoad(ServerLoadEvent event) {
                if (getServer().getPluginManager().isPluginEnabled(Installer.BETTERHUD)) {
                    pending.betterHudEnabled(Installer.BETTERHUD);
                }
            }
        }, this);
    }

    /** M1-INST-03: the pack rebuild / pool check state (null until enabled). */
    public PackRebuildTrigger packRebuild() {
        return packRebuild;
    }

    /** M1-SVC-01..03: registries, player data, packet layer. */
    private void enableServices() {
        registryService = new RegistryService(() -> RegistryService.jarDataRoot(TfpPlugin.class.getClassLoader()),
                scheduler.cpuExecutor());
        registryService.reload().whenComplete((r, err) -> {
            if (err == null) {
                getLogger().info("Registries loaded: " + RegistryService.entries(r.snapshot()) + " entries");
                readyGate.signal(Signal.REGISTRIES_LOADED);
            } else {
                getLogger().log(Level.SEVERE, "Cannot load the TerraFirmaPlugin data", err);
                readyGate.fail(Signal.REGISTRIES_LOADED, err);
            }
        });

        PlayerDataFiles files = new PlayerDataFiles(getDataPath().resolve("playerdata"));
        PlayerDataStore store = new PlayerDataStore(files, scheduler.ioExecutor(), getLogger());
        playerData = store;
        scheduler.io(() -> {
            try {
                int n = files.deleteStaleTempFiles();
                if (n > 0) {
                    getLogger().info("Removed " + n + " unfinished player data write(s) from a crash");
                }
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Cannot clean up player data temp files", e);
            }
        });
        getServer().getPluginManager().registerEvents(new PlayerDataListener(store, messages, getLogger()), this);
        // M2-GEN-16: first-joining players start on the TFC spawn (no optional dependency; the generator decides the world).
        getServer().getPluginManager().registerEvents(new TfcFirstJoinSpawn(), this);
        scheduler.globalRepeating(AUTOSAVE_TICKS, AUTOSAVE_TICKS, () -> {
            Set<UUID> online = new HashSet<>();
            for (Player p : getServer().getOnlinePlayers()) {
                online.add(p.getUniqueId());
            }
            scheduler.io(() -> {
                store.unloadAbsent(online, ABSENT_GRACE_NANOS);
                store.saveDirty();
            });
        });

        // M2-CAL-01: the calendar loads from the first world's PDC on its first global tick (no worlds yet here).
        CalendarService cal = new CalendarService(clock, () -> {
            List<World> worlds = getServer().getWorlds();
            return worlds.isEmpty() ? null : worlds.get(0);
        }, () -> !getServer().getOnlinePlayers().isEmpty(), getLogger());
        calendar = cal;
        cal.start(scheduler);

        // M3-SURV-01: TFC's food stack rule in inventory clicks, pickups and hopper moves (lazy: dates in player ticks).
        // M3-SURV-02: each food's decay_modifier from its food definition (1 for items without one).
        ItemFoodHooks foodHooks = new ItemFoodHooks(() -> clock.get().playerTicks(), FoodConfig.DEFAULT,
                ItemFoodHooks.DecayModifiers.byId(stack -> integrations.customItems().idOf(stack), foodDefinitions));
        food = foodHooks;
        // M3-HEAT-01: the temperature of a heated item, lazily from its stored record and its item_heat capacity.
        heat = new ItemHeatHooks(() -> clock.get().playerTicks(), dev.tfp.core.heat.HeatConfig.DEFAULT,
                ItemHeatHooks.Capacities.byId(stack -> integrations.customItems().idOf(stack), itemHeatDefinitions));
        getServer().getPluginManager().registerEvents(new FoodMergeListener(foodHooks), this);
        // M3-SURV-02: eating a known food writes its nutrition record (the core rule), on the player's thread.
        getServer().getPluginManager().registerEvents(new NutritionEatListener(store,
                () -> clock.get().playerTicks(), integrations.customItems(), foodDefinitions, foodHooks, scheduler),
                this);

        // M2-CLIM-03: the player's ambient temperature, throttled on the global thread and computed off-thread.
        PlayerClimateService playerClimate = new PlayerClimateService(clock, scheduler, hudCache);
        climate = playerClimate;
        getServer().getPluginManager().registerEvents(playerClimate, this);
        playerClimate.start(() -> getServer().getOnlinePlayers());

        // M2-WTH-01: rain or snow at each player's position, decided off-thread (no world change yet).
        PrecipitationService playerPrecipitation = new PrecipitationService(clock, scheduler, hudCache);
        precipitation = playerPrecipitation;
        getServer().getPluginManager().registerEvents(playerPrecipitation, this);
        playerPrecipitation.start(() -> getServer().getOnlinePlayers());

        // M3-SURV-06: thirst and hunger for the HUD, read on each player's thread and throttled on the global thread.
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, scheduler, hudCache,
                () -> clock.get().playerTicks());
        getServer().getPluginManager().registerEvents(needs, this);
        needs.start(() -> getServer().getOnlinePlayers());
        // M3-SURV-03: right-click a water source with an empty main hand drinks (TFC's trigger for the drink packet).
        WaterDrinkListener waterDrink = new WaterDrinkListener(store, () -> clock.get().playerTicks(), hudCache);
        getServer().getPluginManager().registerEvents(waterDrink, this);

        // M2-WTH-02: snow layers, ice and melting near the players, decided off-thread and applied per region under budget.
        // M2-WTH-04: the same service catches up the snow of a chunk on load, from the chunk's stamp (lazy, not replayed).
        SnowService snowService = new SnowService(clock, scheduler);
        snow = snowService;
        snowService.start(() -> getServer().getOnlinePlayers());
        getServer().getPluginManager().registerEvents(snowService, this);

        // We load at STARTUP, PacketEvents at POSTWORLD: it is loaded (its API exists after its onLoad) but not yet
        // enabled here. Registering the listener now is what the M0-NET-01 spike did; isPluginEnabled is false here.
        boolean packetEvents = getServer().getPluginManager().getPlugin(PacketLayers.PACKETEVENTS) != null;
        PacketHandlerRegistry packets = new PacketHandlerRegistry(getLogger());
        this.packets = packets;
        packets.register(new NoopPacketHandler());
        if (packetEvents) {
            packets.register(new CreativeReturnHandler());
            // M2-SEA-02: seasonal grass, foliage and water colours through the chunk biome palette.
            SeasonalBiomeService seasonal = new SeasonalBiomeService(clock, scheduler, id -> getServer().getWorld(id));
            seasonalBiomes = seasonal;
            SeasonalBiomeHandler seasonalHandler = new SeasonalBiomeHandler(seasonal, getLogger());
            seasonal.onQuit(seasonalHandler::forget);
            packets.register(seasonalHandler);
            getServer().getPluginManager().registerEvents(seasonal, this);
            seasonal.start(() -> getServer().getWorlds(), () -> getServer().getOnlinePlayers());
        }
        PacketLayer layer = PacketLayers.create(packetEvents);
        try {
            layer.install(packets);
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "Cannot install the packet layer; running without it", e);
            layer = PacketLayers.none("install failed: " + e);
        }
        packetLayer = layer;
        getLogger().info("Packet layer: " + layer.describe());
    }

    @Override
    public void onDisable() {
        packetLayer.uninstall();
        if (integrations != null) {
            integrations.onDisable();
        }
        if (calendar != null) {
            calendar.shutdown(); // before the worlds are saved
        }
        if (playerData != null) {
            playerData.close(10, TimeUnit.SECONDS); // needs the io executor: before the scheduler stops
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * The install step's outcome: {@code status().degraded()} means TFC worlds must not be generated in this
     * run (restart pending or a required CraftEngine setting is not in effect). Null until {@link #onLoad()} passed.
     */
    public Installer.Result install() {
        return install;
    }

    public ReadyGate readyGate() {
        return readyGate;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    /** The client-plugin services (degraded when a plugin is missing). Null until {@link #onLoad()} passed. */
    public Integrations integrations() {
        return integrations;
    }

    /** Null until {@link #onLoad()} passed the compat gate. */
    public ConfigService config() {
        return config;
    }

    /** Our registries (empty until the first load completed). Null until enabled. */
    public Registries registries() {
        RegistryService r = registryService;
        return r == null ? null : r.registries();
    }

    /** Null until enabled. */
    public PlayerDataStore playerData() {
        return playerData;
    }

    /** M2-CAL-01: the global clock; take one snapshot per operation. */
    public GlobalClockHolder clock() {
        return clock;
    }

    /** M3-HEAT-01: the item heat queries (null until enabled). */
    public ItemHeatHooks heat() {
        return heat;
    }

    /** M3-SURV-01: the food queries (null until enabled). */
    public ItemFoodHooks food() {
        return food;
    }

    /** M2-CAL-01: the calendar service (null until enabled). */
    public CalendarService calendar() {
        return calendar;
    }

    public ChunkDataStore chunkData() {
        return chunkData;
    }

    /** Null until enabled. */
    public PacketHandlerRegistry packets() {
        return packets;
    }

    /** M1-SVC-03: the packet library bridge ({@code none} without PacketEvents). */
    public PacketLayer packetLayer() {
        return packetLayer;
    }

    /** M1-INT-04: the per-player HUD snapshots our placeholders read. */
    public HudSnapshotCache hudCache() {
        return hudCache;
    }

    public Messages messages() {
        return messages;
    }

    @Override
    public String version() {
        StringBuilder sb = new StringBuilder("TerraFirmaPlugin ").append(getPluginMeta().getVersion())
                .append(" on ").append(getServer().getName()).append(' ').append(getServer().getMinecraftVersion())
                .append(", Java ").append(Runtime.version().feature());
        for (String name : new String[] {"CraftEngine", "BetterModel", "BetterHud", PacketLayers.PACKETEVENTS}) {
            Plugin p = getServer().getPluginManager().getPlugin(name);
            sb.append("\n  ").append(name).append(": ")
                    .append(p == null ? "not installed" : p.getPluginMeta().getVersion() + (p.isEnabled() ? "" : " (disabled)"));
        }
        sb.append("\n  packet layer: ").append(packetLayer.describe());
        Installer.Result inst = install;
        if (inst != null && inst.status().degraded()) {
            sb.append("\n  install: DEGRADED").append(inst.status().restartRequired() ? " (restart required)" : "");
        }
        sb.append("\n  status: ").append(readyGate.describe());
        return sb.toString();
    }

    @Override
    public CompletableFuture<Reply> reloadSettings() {
        return CompletableFuture.supplyAsync(() -> {
            ConfigService.Outcome o = config.load();
            logConfig(o);
            if (!o.applied()) {
                return Reply.error("reload.config.not_applied", String.join("; ", o.warnings()));
            }
            return o.warnings().isEmpty() ? Reply.ok("reload.config.done")
                    : Reply.ok("reload.config.done_warnings", o.warnings().size());
        }, scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Reply> reloadData() {
        return registryService.reload().handle((r, err) -> {
            if (err != null) {
                getLogger().log(Level.SEVERE, "Data reload failed; keeping the previous data", err);
                Throwable c = err.getCause() != null ? err.getCause() : err;
                return Reply.error("reload.data.failed", c.getMessage() != null ? c.getMessage() : c.toString());
            }
            if (!r.published()) {
                return Reply.ok("reload.data.superseded");
            }
            return Reply.ok("reload.data.done", RegistryService.entries(r.snapshot()), r.snapshot().registries().size(),
                    r.version());
        });
    }

    @Override
    public CompletableFuture<Reply> reloadContent() {
        // dependency-bootstrap §7: installer -> BetterModel reload -> CraftEngine full reload. The installer
        // (M1-INST-*) is not built yet, so there is nothing to reload.
        return CompletableFuture.completedFuture(Reply.error("reload.content.unavailable"));
    }

    @Override
    public CompletableFuture<Reply> debugBlock(Player player) {
        CompletableFuture<Reply> out = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            Block block = player.getTargetBlockExact(DEBUG_REACH);
            if (block == null) {
                out.complete(Reply.error("debug.block.none", DEBUG_REACH));
                return;
            }
            Location loc = block.getLocation();
            scheduler.region(loc, () -> {
                try {
                    List<String> lines = new ArrayList<>();
                    lines.add("vanilla: " + block.getBlockData().getAsString());
                    lines.add("custom: " + integrations.customBlocks().stateAt(block).map(String::valueOf).orElse("none"));
                    out.complete(Reply.ok("debug.block.header", format(loc)).withDetails(lines));
                } catch (RuntimeException e) {
                    out.completeExceptionally(e);
                }
            });
        }, () -> out.complete(Reply.error("command.player_only")));
        if (!scheduled) {
            out.complete(Reply.error("command.player_only"));
        }
        return out;
    }

    @Override
    public CompletableFuture<Reply> debugChunk(Player player) {
        CompletableFuture<Reply> out = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            World world = player.getWorld();
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            scheduler.region(world, cx, cz, () -> {
                try {
                    Chunk chunk = world.getChunkAt(cx, cz);
                    Set<String> ids = chunkData.ids(chunk);
                    List<String> lines = new ArrayList<>(ids.isEmpty()
                            ? List.of(messages.get(player, "debug.chunk.empty"))
                            : ids.stream().map(id -> "tfp:" + id).toList());
                    long snowTick = ChunkSnowTime.read(chunk.getPersistentDataContainer()).lastCalendarTick();
                    lines.add(messages.get(player, "debug.chunk.snow_time", snowTick == SnowCatchUp.NEVER
                            ? messages.get(player, "debug.chunk.snow_time.none") : String.valueOf(snowTick)));
                    out.complete(Reply.ok("debug.chunk.header", cx, cz, world.getName()).withDetails(lines));
                } catch (RuntimeException e) {
                    out.completeExceptionally(e);
                }
            });
        }, () -> out.complete(Reply.error("command.player_only")));
        if (!scheduled) {
            out.complete(Reply.error("command.player_only"));
        }
        return out.thenCompose(base -> withClimate(player, base)).thenCompose(base -> withPrecipitation(player, base));
    }

    /** {@code /tfp debug chunk}, weather part (M2-WTH-01): the precipitation decided now at the player's position. */
    private CompletableFuture<Reply> withPrecipitation(Player player, Reply base) {
        PrecipitationService service = precipitation;
        if (!base.ok() || service == null) {
            return CompletableFuture.completedFuture(base);
        }
        return service.readNow(player).thenApply(now -> {
            List<String> lines = new ArrayList<>(base.details());
            lines.add(now.map(TfpPlugin::precipitationLine).orElse("precipitation here: the player left"));
            return base.withDetails(lines);
        });
    }

    private static String precipitationLine(PrecipitationType type) {
        return "precipitation here: " + type.name();
    }

    /** The climate line of {@code /tfp debug chunk}, the same for a player and for the console. */
    private static String climateLine(ClimateReading r) {
        return r.isKnown()
                ? "climate now: temperature " + ClimateFormat.temperature(r.temperature())
                        + ", average " + ClimateFormat.temperature(r.averageTemperature())
                        + ", rainfall " + ClimateFormat.rainfall(r.rainfall())
                        + " (calendar tick " + r.calendarTicks() + ")"
                : "climate now: unknown (no calendar reading yet, or not the overworld)";
    }

    /** The snow line of the console reading: the rule's event for this temperature and precipitation (M2-WTH-02). */
    private static String snowLine(ClimateReading r, PrecipitationType type) {
        if (!r.isKnown()) {
            return "snow rule: unknown (no climate reading)";
        }
        SnowRule.Event event = SnowRule.event(r.temperature(), type);
        return "snow rule: event " + event + ", would accumulate: " + (event == SnowRule.Event.ACCUMULATE ? "yes" : "no");
    }

    /** A column for {@code /tfp debug chunk <x> <z>}: the position one block above the surface, read on its region. */
    private record ConsoleSpot(int y, boolean sky, Set<String> ids, long snowTick) {
    }

    /** {@code /tfp debug chunk <x> <z>} from the console (no player): the default overworld, the surface at x, z. */
    @Override
    public CompletableFuture<Reply> debugChunkAt(int x, int z) {
        World world = getServer().getWorlds().get(0);
        int cx = x >> 4;
        int cz = z >> 4;
        CompletableFuture<ConsoleSpot> spot = new CompletableFuture<>();
        Runnable read = () -> {
            try {
                Chunk chunk = world.getChunkAt(cx, cz);
                int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                int y = top + 1;
                long snowTick = ChunkSnowTime.read(chunk.getPersistentDataContainer()).lastCalendarTick();
                spot.complete(new ConsoleSpot(y, y > top, chunkData.ids(chunk), snowTick));
            } catch (RuntimeException e) {
                spot.completeExceptionally(e);
            }
        };
        // RCON runs the command on the server thread, which on Paper is also the region thread: read inline
        // then, so the console can wait for the reply (TfpCommands.reply). Elsewhere, hand it to the region.
        if (scheduler.isOwner(world, cx, cz)) {
            read.run();
        } else {
            scheduler.region(world, cx, cz, read);
        }
        PlayerClimateService climateService = climate;
        PrecipitationService precipitationService = precipitation;
        return spot.thenCompose(s -> {
            BlockPos pos = new BlockPos(x, s.y(), z);
            CompletableFuture<ClimateReading> reading = climateService == null
                    ? CompletableFuture.completedFuture(null) : climateService.readAt(world, pos);
            CompletableFuture<PrecipitationType> type = precipitationService == null || !s.sky()
                    ? CompletableFuture.completedFuture(PrecipitationType.NONE)
                    : precipitationService.readAt(world, pos, true);
            return reading.thenCombine(type, (r, t) -> {
                List<String> lines = new ArrayList<>();
                lines.add("position " + x + " " + z + ", y " + s.y() + " (the first block above the surface)");
                lines.addAll(s.ids().isEmpty()
                        ? List.of(messages.get(Messages.FALLBACK, "debug.chunk.empty"))
                        : s.ids().stream().map(id -> "tfp:" + id).toList());
                lines.add(messages.get(Messages.FALLBACK, "debug.chunk.snow_time",
                        s.snowTick() == SnowCatchUp.NEVER
                                ? messages.get(Messages.FALLBACK, "debug.chunk.snow_time.none")
                                : String.valueOf(s.snowTick())));
                if (r != null) {
                    lines.add(climateLine(r));
                }
                lines.add(precipitationLine(t));
                lines.add(r == null ? "snow rule: unknown (no climate service)" : snowLine(r, t));
                return Reply.ok("debug.chunk.header", cx, cz, world.getName()).withDetails(lines);
            });
        });
    }

    /** {@code /tfp debug chunk}, climate part (M2-CLIM-03): the value computed now next to what the HUD shows. */
    private CompletableFuture<Reply> withClimate(Player player, Reply base) {
        PlayerClimateService service = climate;
        if (!base.ok() || service == null) {
            return CompletableFuture.completedFuture(base);
        }
        return service.readNow(player).thenApply(now -> {
            List<String> lines = new ArrayList<>(base.details());
            lines.add(now.map(TfpPlugin::climateLine).orElse("climate now: the player left"));
            String hudValue = hudCache.isActive()
                    ? ClimateFormat.temperature(hudCache.snapshot(player.getUniqueId()).ambientTemperature())
                    : "not shown (no HUD plugin renders it)";
            lines.add("HUD tfp_ambient_temp: " + hudValue);
            lines.add("chunk input: neutral, until chunk data exists (M2-GEN-05)");
            return base.withDetails(lines);
        });
    }

    /** {@code /tfp debug snowtime}: writes the chunk's {@link ChunkSnowTime} on the chunk's region, as {@code debugChunkAt} reads it. */
    @Override
    public CompletableFuture<Reply> debugSnowTime(int x, int z, long tick) {
        World world = getServer().getWorlds().get(0);
        int cx = x >> 4;
        int cz = z >> 4;
        CompletableFuture<Reply> out = new CompletableFuture<>();
        Runnable write = () -> {
            try {
                Chunk chunk = world.getChunkAt(cx, cz);
                new ChunkSnowTime(tick).write(chunk.getPersistentDataContainer());
                SnowService service = snow;
                if (service != null) {
                    service.stampAtNextUnload(world, cx, cz, tick); // the next unload stamps this tick, not now
                }
                out.complete(Reply.ok("debug.snowtime.done", cx, cz, world.getName(), tick));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        };
        // Same as debugChunkAt: on the region's thread (RCON), write inline so the console gets the reply in time.
        if (scheduler.isOwner(world, cx, cz)) {
            write.run();
        } else {
            scheduler.region(world, cx, cz, write);
        }
        return out;
    }

    /**
     * {@code /tfp debug worldgen <n>} (M2-GEN-15): times {@code count} chunks of the TFC fill path in throwaway chunk data, on
     * the command's own thread like the other debug commands (the console then gets the reply directly). The chunks are a
     * contiguous grid at {@link FillTiming#SAMPLE_ORIGIN}, after {@link FillTiming#WARM_UP} untimed warm-up chunks elsewhere.
     * Nothing is loaded into or written to the world.
     */
    @Override
    public CompletableFuture<Reply> debugWorldgen(int count) {
        World world = getServer().getWorlds().get(0);
        if (!(world.getGenerator() instanceof TfcChunkGenerator generator)) {
            return CompletableFuture.completedFuture(Reply.error("debug.worldgen.not_tfc", world.getName()));
        }
        try {
            for (int i = 0; i < FillTiming.WARM_UP; i++) {
                generator.timeFill(world, FillTiming.WARM_UP_ORIGIN + i, FillTiming.WARM_UP_ORIGIN);
            }
            int side = FillTiming.side(count);
            long[] nanos = new long[count];
            for (int i = 0; i < count; i++) {
                nanos[i] = generator.timeFill(world, FillTiming.SAMPLE_ORIGIN + i % side, FillTiming.SAMPLE_ORIGIN + i / side);
            }
            FillTiming.Stats s = FillTiming.of(nanos);
            if (s.count() == 0) {
                return CompletableFuture.completedFuture(Reply.error("debug.worldgen.refused", count));
            }
            String o = String.valueOf(FillTiming.SAMPLE_ORIGIN);
            return CompletableFuture.completedFuture(Reply.ok("debug.worldgen.done", s.count(), world.getName(),
                    FillTiming.WARM_UP, fmt(s.meanMs()), fmt(s.p95Ms()), fmt(s.minMs()), fmt(s.maxMs()), s.refused())
                .withDetails(List.of("grid " + side + " x " + side + " from chunk " + o + ", " + o + ", thread "
                    + Thread.currentThread().getName() + ", " + Runtime.getRuntime().availableProcessors() + " cpus")));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String fmt(double ms) {
        return String.format(java.util.Locale.ROOT, "%.2f", ms);
    }

    @Override
    public CompletableFuture<Reply> debugPlayer(String name) {
        Player target = getServer().getPlayerExact(name);
        if (target == null) {
            return CompletableFuture.completedFuture(Reply.error("command.player_not_found", name));
        }
        return CompletableFuture.completedFuture(playerData.get(target.getUniqueId())
                .map(d -> Reply.ok("debug.player.header", target.getName()).withDetails(describe(d)))
                .orElse(Reply.error("debug.player.not_loaded", target.getName())));
    }

    @Override
    public CompletableFuture<Reply> debugSetThirst(String name, float value) {
        Player target = getServer().getPlayerExact(name);
        if (target == null) {
            return CompletableFuture.completedFuture(Reply.error("command.player_not_found", name));
        }
        PlayerData updated = playerData.update(target.getUniqueId(), d -> d.withThirst(value));
        return CompletableFuture.completedFuture(Reply.ok("debug.player.header", target.getName())
                .withDetails(List.of("thirst set to " + updated.thirst())));
    }

    @Override
    public CompletableFuture<Reply> debugSetHeat(String name, float temperature) {
        Player target = getServer().getPlayerExact(name);
        if (target == null) {
            return CompletableFuture.completedFuture(Reply.error("command.player_not_found", name));
        }
        ItemHeatHooks hooks = heat;
        if (hooks == null) {
            return CompletableFuture.completedFuture(Reply.error("command.not_enabled"));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(target, () -> {
            try {
                org.bukkit.inventory.ItemStack stack = target.getInventory().getItemInMainHand();
                if (stack.getType().isAir()) {
                    out.complete(Reply.error("debug.heat.empty_hand", target.getName()));
                    return;
                }
                if (!HeatPdc.write(stack, new dev.tfp.core.heat.HeatState(temperature, clock.get().playerTicks()))) {
                    out.complete(Reply.error("debug.heat.not_written", target.getName()));
                    return;
                }
                target.getInventory().setItemInMainHand(stack);
                out.complete(Reply.ok("debug.heat.header", target.getName()).withDetails(List.of(
                        "set: " + temperature + " C at tick " + clock.get().playerTicks(),
                        "temperature now: " + hooks.temperature(stack) + " C (0 when the item has no item_heat data)",
                        "level: " + hooks.level(stack).map(Enum::name).orElse("none"))));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", name)));
        return out;
    }

    static List<String> describe(PlayerData d) {
        return List.of("thirst: " + d.thirst(),
                "lastDrinkTick: " + tick(d.lastDrinkTick()),
                "intoxicatedUntil: " + tick(d.intoxicationTick()),
                "chiselMode: " + d.chiselMode(),
                "nutrition records: " + d.nutrition().size());
    }

    private static String tick(long t) {
        return t == Long.MIN_VALUE ? "never" : Long.toString(t);
    }

    @Override
    public CompletableFuture<Reply> skipTime(String key, String label, TimeCommand.Delta delta) {
        CalendarService cal = calendar;
        if (cal == null) {
            return CompletableFuture.completedFuture(Reply.error("command.not_enabled"));
        }
        // The calendar state is written on the global thread only (CalendarService). Console/RCON commands already run
        // there (Paper main thread, Folia global region): apply inline so the reply is sent before the command returns
        // (RCON only returns what is sent during execution). Other senders (Folia player regions) hop there.
        CompletableFuture<Reply> out = new CompletableFuture<>();
        Runnable apply = () -> {
            try {
                CalendarService.Skipped moved = cal.skip(delta);
                out.complete(moved == null ? Reply.error("time.not_loaded")
                        : Reply.ok(key, label, moved.ticks(), CalendarFormat.dateTime(moved.state().calendarTicks(),
                                moved.state().daysInMonth())));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        };
        if (getServer().isGlobalTickThread()) {
            apply.run();
        } else {
            scheduler.global(apply);
        }
        return out;
    }

    @Override
    public CompletableFuture<Reply> debugPackets() {
        List<String> lines = new ArrayList<>();
        lines.add("layer: " + packetLayer.describe());
        SeasonalBiomeService seasonal = seasonalBiomes;
        if (seasonal != null) {
            lines.add("seasonal biomes: " + seasonal.describe());
        }
        PacketHandlerRegistry registry = packets;
        for (PacketHandlerRegistry.HandlerMetrics m : registry == null ? List.<PacketHandlerRegistry.HandlerMetrics>of() : registry.metrics()) {
            lines.add(String.format(java.util.Locale.ROOT, "%s: calls=%d avg=%.1fus max=%.1fus modified=%d cancelled=%d errors=%d",
                    m.name(), m.calls(), m.averageNanos() / 1000.0, m.maxNanos() / 1000.0, m.modified(),
                    m.cancelled(), m.errors()));
        }
        return CompletableFuture.completedFuture(new Reply(true, "debug.packets.header", List.of(), lines));
    }

    @Override
    public CompletableFuture<Reply> debugShowcase(Player player, String familyArg) {
        java.util.Optional<Showcase.Family> family = Showcase.Family.byArgument(familyArg);
        if (family.isEmpty()) {
            return CompletableFuture.completedFuture(Reply.error("debug.showcase.unknown", familyArg,
                    String.join(", ", Showcase.Family.arguments())));
        }
        String name = family.get().argument();
        dev.tfp.paper.integration.CustomBlocks blocks = integrations.customBlocks();
        return showcaseIds().thenCompose(all -> {
            // Only what CraftEngine actually loaded (datagen v1 does not emit every TFC block yet).
            List<String> ids = Showcase.select(all, family.get()).stream().filter(blocks::exists).toList();
            if (ids.isEmpty()) {
                return CompletableFuture.completedFuture(Reply.error("debug.showcase.none", name));
            }
            CompletableFuture<Location> origin = new CompletableFuture<>();
            boolean scheduled = scheduler.entity(player, () -> origin.complete(player.getLocation()),
                    () -> origin.completeExceptionally(new IllegalStateException(player.getName() + " left")));
            if (!scheduled) {
                origin.completeExceptionally(new IllegalStateException(player.getName() + " is not online"));
            }
            ShowcasePlacer placer = new ShowcasePlacer(blocks, scheduler::region);
            return origin.thenCompose(at -> {
                // corner 2 blocks south-east of the player, at their feet
                int ox = at.getBlockX() + 2;
                int oz = at.getBlockZ() + 2;
                return placer.place(at.getWorld(), ox, at.getBlockY(), oz, ids).thenApply(r -> Reply.ok("debug.showcase.done",
                        name, r.placed(), r.total(), r.extent().dx(), r.extent().dz(),
                        at.getWorld().getName() + " " + ox + " " + at.getBlockY() + " " + oz)
                        .withDetails(ShowcasePlacer.details(r)));
            });
        });
    }

    private CompletableFuture<List<String>> showcaseIds() {
        CompletableFuture<List<String>> f = showcaseIds;
        if (f == null || f.isCompletedExceptionally()) {
            f = CompletableFuture.supplyAsync(() -> {
                Path idMap = RegistryService.jarDataRoot(TfpPlugin.class.getClassLoader()).resolve("id_map.json");
                try {
                    return Showcase.blockIds(com.google.gson.JsonParser.parseString(
                            java.nio.file.Files.readString(idMap, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, scheduler.ioExecutor());
            showcaseIds = f;
        }
        return f;
    }

    private static String format(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private void logConfig(ConfigService.Outcome o) {
        if (o.created()) {
            getLogger().info("Wrote default config.yml");
        }
        if (!o.added().isEmpty()) {
            getLogger().info("config.yml: added missing sections with defaults: " + String.join(", ", o.added()));
        }
        for (String w : o.warnings()) {
            getLogger().warning("config.yml: " + w);
        }
    }

    private String defaultConfigText() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                throw new IllegalStateException("config.yml is missing from the plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
