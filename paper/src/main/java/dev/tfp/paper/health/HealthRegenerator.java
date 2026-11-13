/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (tick: custom TFC regeneration),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onServerStarting: naturalRegeneration false)
 */
package dev.tfp.paper.health;

import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.health.HealthRegen;
import dev.tfp.paper.player.PlayerData;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * PAR-SURV-05: TFC's passive regeneration in place of vanilla's. TFC turns the {@code naturalRegeneration} game rule
 * off, so neither vanilla's food regeneration ({@code SATIATED}) nor peaceful regeneration ({@code REGEN}) happens;
 * here those two regain reasons are cancelled for players instead of changing the world's game rule. Vanilla's
 * {@code FoodData.tick} adds its regeneration exhaustion ({@code ExhaustionReason.REGEN}) after the heal whatever the
 * regain event decided, so that exhaustion is cancelled too: with the game rule off TFC adds none. Every
 * {@value #PERIOD_TICKS} ticks ({@code tickCount % 10 == 0}) a hurt player with food {@code >= 4} and thirst {@code > 20}
 * heals {@link HealthRegen#healPerStep} vanilla HP (reason {@code CUSTOM}, as TFC's {@code heal} fires the heal event).
 *
 * <p>The health itself is the vanilla health, which the server saves, so no extra state is persisted.
 */
public final class HealthRegenerator implements Listener {
    /** TFC's regeneration interval. */
    public static final long PERIOD_TICKS = 10;

    private final HealthConfig config;
    /** The player's current (lazily evaluated) data; called only for a hurt player. */
    private final Function<Player, Optional<PlayerData>> current;
    private final BiConsumer<Player, Double> heal;

    /**
     * @param current the player's data evaluated now (the thirst clock caught up), empty when not loaded
     * @param heal    how health is added ({@code player.heal(amount, CUSTOM)} on the server)
     */
    public HealthRegenerator(HealthConfig config, Function<Player, Optional<PlayerData>> current,
            BiConsumer<Player, Double> heal) {
        this.config = config;
        this.current = current;
        this.heal = heal;
    }

    /** Vanilla's natural regeneration is off in TFC (the game rule); TFC's own heal below replaces it. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player && suppressed(event.getRegainReason())) {
            event.setCancelled(true);
        }
    }

    /**
     * The exhaustion vanilla's food regeneration adds after each {@code SATIATED} heal; {@code FoodData.tick} adds it
     * even when the regain event was cancelled. TFC's game rule off means it never happens.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getEntity() instanceof Player
                && event.getExhaustionReason() == EntityExhaustionEvent.ExhaustionReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /** The regain reasons the {@code naturalRegeneration} game rule controls. */
    static boolean suppressed(EntityRegainHealthEvent.RegainReason reason) {
        return reason == EntityRegainHealthEvent.RegainReason.SATIATED
                || reason == EntityRegainHealthEvent.RegainReason.REGEN;
    }

    /** Player thread: one regeneration step. Returns the HP healed (0 when nothing happened). */
    public double step(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return 0;
        }
        double health = player.getHealth();
        double max = maxHealth(player);
        if (health <= 0 || health >= max) {
            return 0; // TFC isHurt()
        }
        Optional<PlayerData> data = current.apply(player);
        if (data.isEmpty()) {
            return 0;
        }
        float food = player.getFoodLevel();
        float thirst = data.get().thirst();
        if (!HealthRegen.canRegenerate(food, thirst, config)) {
            return 0;
        }
        double amount = Math.min(HealthRegen.healPerStep(food, thirst, config), max - health);
        heal.accept(player, amount);
        return amount;
    }

    private static double maxHealth(Player player) {
        AttributeInstance a = player.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }
}
