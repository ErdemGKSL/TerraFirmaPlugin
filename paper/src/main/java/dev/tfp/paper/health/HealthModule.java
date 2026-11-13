/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.health;

import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.player.GameModeNeeds;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * PAR-SURV-05: health and damage on Paper, a feature module.
 * <ul>
 *   <li>{@link PhysicalDamageListener}: TFC's {@code onLivingHurt} (forging bonus, physical damage type and the
 *       entity and armour resistances from the exported {@code entity_damage_resistance} / {@code item_damage_resistance}
 *       data, then the player's nutrition health modifier).</li>
 *   <li>{@link HealthRegenerator}: TFC's passive regeneration replaces vanilla's, one global throttle every
 *       {@value HealthRegenerator#PERIOD_TICKS} ticks handing each online player to the player's own thread, at the
 *       {@code survival.natural-regeneration-modifier} of config.yml (TFC {@code naturalRegenerationModifier},
 *       PAR-SURV-14; a {@code /tfp reload config} applies it at the next step).</li>
 * </ul>
 * The forging damage bonus is read from a {@link PhysicalDamageListener.WeaponDamageBonus} service when a later module
 * provides one; until then it is 1.
 *
 * <p>Debug: {@code /tfp debug damage} toggles a log line per hit (attacker, target, type, resistance, factor, result).
 */
public final class HealthModule implements FeatureModule {
    public static final String ID = "health";

    private volatile Scheduler.TaskHandle throttle;
    private volatile boolean debugLog;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        Tags tags = ctx.services().require(Tags.class);
        DamageResistancesCache resistances = new DamageResistancesCache(() -> ctx.registryService().registries());
        Supplier<Consumer<String>> debug = () -> debugLog ? ctx.logger()::info : null;
        ctx.listen(new PhysicalDamageListener(resistances, ItemSizes.lookups(tags::snapshot), tags::itemId,
                ctx.playerData()::get,
                () -> ctx.services().get(PhysicalDamageListener.WeaponDamageBonus.class)
                        .orElse(PhysicalDamageListener.WeaponDamageBonus.NONE),
                entity -> ctx.plugin().getServer().isOwnedByCurrentRegion(entity), debug));
        PlayerDataStore data = ctx.playerData();
        RegeneratorCache regens = new RegeneratorCache(
                () -> ctx.config().current().survival().naturalRegenerationModifier(),
                config -> new HealthRegenerator(config, player -> evaluated(ctx, data, player),
                        (player, amount) -> player.heal(amount, EntityRegainHealthEvent.RegainReason.CUSTOM)));
        ctx.listen(regens.get()); // the event handlers do not depend on the modifier
        Scheduler scheduler = ctx.scheduler();
        throttle = scheduler.globalRepeating(HealthRegenerator.PERIOD_TICKS, HealthRegenerator.PERIOD_TICKS, () -> {
            HealthRegenerator regen = regens.get();
            for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
                scheduler.entity(player, () -> regen.step(player), null);
            }
        });
    }

    /** {@link HealthConfig#DEFAULT} with TFC's {@code naturalRegenerationModifier} from config.yml (PAR-SURV-14). */
    public static HealthConfig healthConfig(double naturalRegenerationModifier) {
        HealthConfig d = HealthConfig.DEFAULT;
        return new HealthConfig(d.vanillaMaxHealth(), d.displayUnitsPerHp(), d.regenIntervalTicks(), d.regenPerStep(),
                (float) naturalRegenerationModifier, d.minFood(), d.maxHunger(), d.minThirst(), d.maxThirst());
    }

    /**
     * The regenerator for the configured {@code survival.natural-regeneration-modifier}, rebuilt when a
     * {@code /tfp reload config} changes it.
     */
    static final class RegeneratorCache implements Supplier<HealthRegenerator> {
        private record Entry(double modifier, HealthRegenerator regen) {
        }

        private final DoubleSupplier modifier;
        private final Function<HealthConfig, HealthRegenerator> factory;
        private volatile Entry entry;

        RegeneratorCache(DoubleSupplier modifier, Function<HealthConfig, HealthRegenerator> factory) {
            this.modifier = modifier;
            this.factory = factory;
        }

        @Override
        public HealthRegenerator get() {
            double m = modifier.getAsDouble();
            Entry e = entry;
            if (e != null && Double.compare(e.modifier(), m) == 0) {
                return e.regen();
            }
            HealthRegenerator regen = factory.apply(healthConfig(m));
            entry = new Entry(m, regen);
            return regen;
        }
    }

    /** Player thread: the player's data with the thirst clock caught up to now (as the HUD reads it). */
    private static Optional<PlayerData> evaluated(ModuleContext ctx, PlayerDataStore data, Player player) {
        UUID id = player.getUniqueId();
        if (!data.isLoaded(id)) {
            return Optional.empty();
        }
        boolean active = GameModeNeeds.needsActive(player.getGameMode());
        long now = ctx.clock().get().playerTicks();
        float temperature = (float) ctx.hudCache().snapshot(id).ambientTemperature();
        try {
            return Optional.of(data.update(id, d -> GameModeNeeds.evaluate(d, active, now, temperature)));
        } catch (IllegalStateException e) {
            return Optional.empty(); // unloaded between the check and the update (quit)
        }
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = throttle;
        throttle = null;
        if (t != null) {
            t.cancel();
        }
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("damage", node -> node.executes(tree.run(ctx -> {
            debugLog = !debugLog;
            return CompletableFuture.completedFuture(Reply.ok(debugLog ? "debug.damage.on" : "debug.damage.off"));
        })));
    }

    /** The resistance tables of the current registries, rebuilt when their version changes. */
    static final class DamageResistancesCache implements Supplier<DamageResistances> {
        private record Entry(long version, DamageResistances tables) {
        }

        private final Supplier<Registries> registries;
        private volatile Entry entry = new Entry(0, DamageResistances.EMPTY);

        DamageResistancesCache(Supplier<Registries> registries) {
            this.registries = registries;
        }

        @Override
        public DamageResistances get() {
            Registries r = registries.get();
            if (r == null) {
                return DamageResistances.EMPTY;
            }
            long version = r.version();
            Entry e = entry;
            if (e.version() == version) {
                return e.tables();
            }
            DamageResistances tables = version == 0 ? DamageResistances.EMPTY : DamageResistances.fromRegistry(r.get());
            entry = new Entry(version, tables);
            return tables;
        }
    }
}
