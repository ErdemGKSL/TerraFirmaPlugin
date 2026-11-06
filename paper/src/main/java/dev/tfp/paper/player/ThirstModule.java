/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (tick: lack-of-thirst effects),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerTick: drinking in the rain),
 * src/main/java/net/dries007/tfc/common/effect/ThirstEffect.java @ c3b4d9536 (the thirst effect's drain),
 * and src/main/java/net/dries007/tfc/common/TFCDamageTypes.java @ c3b4d9536 (dehydration)
 */
package dev.tfp.paper.player;

import dev.tfp.core.registry.Registries;
import dev.tfp.core.thirst.Drinkables;
import dev.tfp.core.thirst.RainDrinking;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstEffect;
import dev.tfp.core.thirst.ThirstStage;
import dev.tfp.core.thirst.ThirstState;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.packet.SeasonalBiomeHandler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Thirst's effects on the player (PAR-SURV-04), a feature module. Nothing ticks per player: a global throttle every
 * {@value #PERIOD_TICKS} ticks sends one task to each online player's own thread, which
 * <ol>
 *   <li>evaluates the lazy thirst clock ({@link GameModeNeeds#evaluate}) when anything below needs the current value;</li>
 *   <li>drains the {@code tfp:thirst} effect for the {@value #PERIOD_TICKS} ticks since the last reading
 *       ({@link ThirstEffect#advance}, TFC {@code ThirstEffect.applyEffectTick});</li>
 *   <li>adds {@value #PERIOD_TICKS} ticks of rain drinking when the player looks up in the rain the client draws
 *       ({@link RainDrinking}, TFC {@code onPlayerTick}; the biome the client renders there, see {@link #clientBiome}),
 *       sampled once per reading;</li>
 *   <li>every {@value #EFFECT_PERIOD_TICKS} ticks (TFC {@code tickCount % 100 == 0}), outside peaceful, applies the
 *       stage's Slowness and Mining Fatigue (160 ticks, no particles, no icon, as TFC) and, at 0 thirst, 1 point of
 *       dehydration damage.</li>
 * </ol>
 * Creative and spectator do nothing here (TFC: {@code abilities.invulnerable}; the needs are off, PAR-HUD-12); a dead or
 * not loaded player is skipped.
 *
 * <p>Dehydration damage is vanilla {@code minecraft:starve} (no {@code tfp:dehydration} damage type is registered yet),
 * with TFC's death message ({@code death.attack.tfc.dehydration}) put back on the death it causes. The changes are
 * written through {@link PlayerDataStore#update} and the HUD shows them at its next reading (at most one second).
 *
 * <p>Debug: {@code /tfp debug rain [name]} prints the rain-drinking gates of a player (pitch, storm, open sky, the
 * client's biome precipitation and temperature, the TFC ambient value, and the verdict).
 */
public final class ThirstModule implements FeatureModule, Listener {
    /** One reading per player per second. */
    public static final long PERIOD_TICKS = 20;
    /** TFC's lack-of-thirst check interval. */
    public static final long EFFECT_PERIOD_TICKS = 100;
    /** TFC's effect duration for the lack-of-thirst slowdown. */
    public static final int SLOWDOWN_TICKS = 160;
    /** TFC's dehydration damage per check at 0 thirst. */
    public static final double DEHYDRATION_DAMAGE = 1.0;
    /** TFC's death message key ({@code message_id} {@code tfc.dehydration}). */
    public static final String DEATH_KEY = "death.attack.tfc.dehydration";

    private static final ThirstConfig THIRST = ThirstConfig.DEFAULT;
    private static final DrinkablesCache NO_DRINKABLES = new DrinkablesCache(() -> null);
    private static volatile DrinkablesCache drinkables = NO_DRINKABLES;

    /** How a dehydration hit is dealt (the real server: {@link #STARVE}; MockBukkit has no damage with a source). */
    @FunctionalInterface
    interface Hurt {
        void hurt(Player player, double amount);
    }

    /** Vanilla {@code minecraft:starve} damage with no attacker. */
    static final Hurt STARVE = (player, amount) -> player.damage(amount,
            DamageSource.builder(DamageType.STARVE).build());

    /** The biome a vanilla client renders at a position: what decides whether it draws rain or snow there. */
    record ClientBiome(boolean hasPrecipitation, float temperature, String source) {
    }

    /** Player thread: the client's biome at the block above the player's feet. */
    @FunctionalInterface
    interface ClientSky {
        ClientBiome at(Player player);
    }

    /** Players being hurt by dehydration right now (the death event fires inside the damage call). */
    private final Set<UUID> dehydrating = ConcurrentHashMap.newKeySet();

    private volatile PlayerDataStore data;
    private volatile LongSupplier playerTicks;
    private volatile HudSnapshotCache hud;
    private volatile Scheduler scheduler;
    private volatile Scheduler.TaskHandle throttle;
    private volatile Hurt hurt = STARVE;
    private volatile ClientSky sky = player -> clientBiome(player, false);
    /** Global thread only (the throttle), so no synchronization. */
    private long runs;

    /** For the service file. */
    public ThirstModule() {
    }

    /** With explicit services and no throttle (tests). */
    ThirstModule(PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud, Scheduler scheduler, Hurt hurt,
            ClientSky sky) {
        this.data = data;
        this.playerTicks = playerTicks;
        this.hud = hud;
        this.scheduler = scheduler;
        this.hurt = hurt;
        this.sky = sky;
    }

    /**
     * Any thread: the loaded drinkable definitions (empty before the module is enabled or the registries are loaded).
     * {@link WaterDrinkListener} reads them from here because the plugin builds it before the modules exist.
     */
    public static Drinkables drinkables() {
        return drinkables.get();
    }

    @Override
    public String id() {
        return "thirst";
    }

    @Override
    public void enable(ModuleContext context) {
        data = context.playerData();
        playerTicks = () -> context.clock().get().playerTicks();
        hud = context.hudCache();
        scheduler = context.scheduler();
        drinkables = new DrinkablesCache(() -> context.registryService().registries());
        // The seasonal handler exists only with the packet layer; without it the client sees the server's biomes.
        boolean seasonal = context.packets().handlers().contains(SeasonalBiomeHandler.NAME);
        BooleanSupplier remapped = () -> seasonal && context.clock().get().isSet();
        sky = player -> clientBiome(player, remapped.getAsBoolean());
        context.listen(this);
        Supplier<Collection<? extends Player>> online = () -> context.plugin().getServer().getOnlinePlayers();
        throttle = scheduler.globalRepeating(PERIOD_TICKS, PERIOD_TICKS, () -> {
            boolean effectsDue = (++runs * PERIOD_TICKS) % EFFECT_PERIOD_TICKS == 0;
            for (Player player : online.get()) {
                scheduler.entity(player, () -> read(player, effectsDue), null);
            }
        });
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = throttle;
        throttle = null;
        if (t != null) {
            t.cancel();
        }
        drinkables = NO_DRINKABLES;
        dehydrating.clear();
    }

    /** Player thread: one reading (see the class comment). */
    void read(Player player, boolean effectsDue) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        if (!player.isOnline() || player.isDead() || !data.isLoaded(id)) {
            return;
        }
        Optional<ThirstEffect> effect = ThirstEffects.read(player);
        if (!GameModeNeeds.needsActive(player.getGameMode())) {
            // The needs are off: the effect runs out as a vanilla effect does, but takes no thirst.
            effect.ifPresent(e -> ThirstEffects.write(player, e.advance(0f, PERIOD_TICKS).left()));
            return;
        }
        float temperature = (float) hud.snapshot(id).ambientTemperature();
        boolean rain = rainGates(player).drinks();
        if (effect.isEmpty() && !rain && !effectsDue) {
            return; // nothing reads or changes the thirst: the HUD reading keeps the clock
        }
        long now = playerTicks.getAsLong();
        ThirstEffect[] left = {null};
        PlayerData after = data.update(id, d -> {
            PlayerData evaluated = GameModeNeeds.evaluate(d, true, now, temperature);
            float thirst = evaluated.thirst();
            if (effect.isPresent()) {
                ThirstEffect.Drain drain = effect.get().advance(thirst, PERIOD_TICKS);
                thirst = drain.thirst();
                left[0] = drain.left();
            }
            if (rain) {
                thirst = new ThirstState(thirst, evaluated.thirstTick()).drinkRain(PERIOD_TICKS, THIRST).thirst();
            }
            return thirst == evaluated.thirst() ? evaluated : evaluated.withThirst(thirst);
        });
        if (effect.isPresent()) {
            ThirstEffects.write(player, left[0]);
        }
        if (effectsDue) {
            applyStage(player, ThirstStage.of(after.thirst(), THIRST));
        }
    }

    /**
     * Player thread: TFC {@code PlayerInfo.tick}'s lack-of-thirst branch for {@code stage}, except in peaceful. The
     * effects are TFC's {@code new MobEffectInstance(effect, 160, amplifier, false, false)}: not ambient, no particles
     * and (vanilla's {@code showIcon = visible}) no icon.
     */
    void applyStage(Player player, ThirstStage stage) {
        if (stage == ThirstStage.NONE || player.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        int amplifier = stage.slowdownAmplifier();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOWDOWN_TICKS, amplifier, false, false,
                false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, SLOWDOWN_TICKS, amplifier, false,
                false, false));
        if (stage.damages()) {
            UUID id = player.getUniqueId();
            dehydrating.add(id);
            try {
                hurt.hurt(player, DEHYDRATION_DAMAGE);
            } finally {
                dehydrating.remove(id);
            }
        }
    }

    /** The inputs of the rain-drinking decision for one player, as read on the player's thread. */
    record RainGates(float pitch, boolean storm, boolean openToSky, ClientBiome biome) {
        boolean drinks() {
            return RainDrinking.drinks(pitch, storm, openToSky, biome.hasPrecipitation(), biome.temperature());
        }
    }

    /** Player thread: the rain-drinking gates now. */
    RainGates rainGates(Player player) {
        boolean open = openToSky(player);
        return new RainGates(player.getLocation().getPitch(), player.getWorld().hasStorm(), open, sky.at(player));
    }

    /**
     * Player thread: the biome the client renders at the block above the feet. In a TFC world whose chunks go through the
     * seasonal biome rewrite (packet layer present, calendar running) the client gets a seasonal variant, which keeps the
     * TFC biome's climate ({@link RainDrinking#TFC_BIOME_TEMPERATURE}, precipitation); everywhere else it gets the
     * server's biome, whose height-adjusted temperature the world gives, with "has precipitation" read as a downfall above 0 (the
     * vanilla overworld biomes without precipitation, desert, badlands and savanna, are exactly those with downfall 0).
     */
    static ClientBiome clientBiome(Player player, boolean seasonalRemap) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int y = at.getBlockY() + 1;
        if (seasonalRemap && world.getEnvironment() == World.Environment.NORMAL
                && world.getGenerator() instanceof TfcChunkGenerator) {
            return seasonalVariant(y, world.getSeaLevel());
        }
        Block block = world.getBlockAt(at.getBlockX(), y, at.getBlockZ());
        return new ClientBiome(block.getHumidity() > 0, (float) block.getTemperature(), "server biome");
    }

    /** The seasonal variant's climate at a height (TFC biome temperature, height-adjusted as vanilla does). */
    static ClientBiome seasonalVariant(int y, int seaLevel) {
        float t = RainDrinking.heightAdjustedTemperature(RainDrinking.TFC_BIOME_TEMPERATURE, y, seaLevel);
        return new ClientBiome(true, t, "seasonal variant");
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("rain", node -> node
                .executes(tree.player((player, ctx) -> debugRain(player)))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(tree.run(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            Player target = Bukkit.getPlayerExact(name);
                            return target == null
                                    ? CompletableFuture.completedFuture(Reply.error("command.player_not_found", name))
                                    : debugRain(target);
                        }))));
    }

    /** Any thread: the rain gates of a player, read on the player's thread. */
    CompletableFuture<Reply> debugRain(Player player) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            // Paper's main thread (or the player's region): answer at once, so the console and RCON see the reply.
            return CompletableFuture.completedFuture(rainReply(player));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(player, () -> {
            try {
                out.complete(rainReply(player));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
        return out;
    }

    /** Player thread: the debug reply of the rain gates. */
    private Reply rainReply(Player player) {
        ThreadAsserts.owned(player);
        return Reply.ok("debug.rain.header", player.getName()).withDetails(describe(rainGates(player),
                hud.snapshot(player.getUniqueId()).ambientTemperature()));
    }

    /** The debug lines of a reading. */
    static List<String> describe(RainGates g, double ambient) {
        return List.of(
                String.format(Locale.ROOT, "pitch %.1f (looks up: %s, needs <= %.0f)", g.pitch(),
                        RainDrinking.looksUp(g.pitch()), RainDrinking.MAX_PITCH),
                "storm: " + g.storm(),
                "open to sky: " + g.openToSky(),
                String.format(Locale.ROOT, "client biome (%s): precipitation %s, temperature %.3f (rain needs >= %.2f)",
                        g.biome().source(), g.biome().hasPrecipitation(), g.biome().temperature(),
                        RainDrinking.RAIN_MIN_TEMPERATURE),
                String.format(Locale.ROOT, "TFC ambient %.1f C (not a rain gate here)", ambient),
                "drinks rain: " + g.drinks());
    }

    /**
     * Vanilla {@code isRainingAt}'s position gates at the block above the feet: it sees the sky (above the world-surface
     * heightmap) and no motion-blocking block is above it.
     */
    static boolean openToSky(Player player) {
        Location at = player.getLocation();
        World world = player.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY() + 1;
        int z = at.getBlockZ();
        return y > world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE)
                && y > world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
    }

    /** TFC's death message for a death by dehydration ({@code tfc.dehydration}, with the vanilla-style fallback). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (dehydrating.contains(player.getUniqueId())) {
            event.deathMessage(Component.translatable(DEATH_KEY, "%1$s dehydrated to death", player.displayName()));
        }
    }

    /** The drinkable definitions of the current registry version. */
    static final class DrinkablesCache implements Supplier<Drinkables> {
        private record Entry(long version, Drinkables drinkables) {
        }

        private final Supplier<Registries> registries;
        private volatile Entry entry = new Entry(0, Drinkables.EMPTY);

        DrinkablesCache(Supplier<Registries> registries) {
            this.registries = registries;
        }

        @Override
        public Drinkables get() {
            Registries r = registries.get();
            if (r == null) {
                return Drinkables.EMPTY;
            }
            long version = r.version();
            Entry e = entry;
            if (e.version() == version) {
                return e.drinkables();
            }
            Drinkables d = version == 0 ? Drinkables.EMPTY : Drinkables.fromRegistry(r.get());
            entry = new Entry(version, d);
            return d;
        }
    }
}
