/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerDeath, onPlayerRespawn),
 * src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (tick: extra-peaceful branch)
 */
package dev.tfp.paper.player;

import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.bukkit.Difficulty;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * PAR-SURV-09: death, respawn and peaceful regeneration ({@link RespawnRules}), a feature module.
 * <ul>
 *   <li>{@link PlayerRespawnEvent} after a death (MONITOR, the player's thread): the stored data becomes TFC's fresh
 *       {@code PlayerInfo} (full thirst, clock restarted, sober, default chisel mode) with the nutrition kept per
 *       {@code keepNutritionAfterDeath}, and the {@code tfp:thirst} effect, which lives in the player's PDC and so would
 *       outlive the death that clears vanilla effects, is removed. Vanilla resets hunger and saturation.</li>
 *   <li>With {@code enablePeacefulDifficultyPassiveRegeneration} on (off by default, as in TFC), one global throttle
 *       every {@value RespawnRules#PEACEFUL_PERIOD_TICKS} ticks hands each online player to the player's thread for TFC's
 *       extra-peaceful step.</li>
 * </ul>
 * TFC's {@code naturalRegenerationModifier} is part of the regeneration rule itself ({@code core} {@code HealthRegen},
 * PAR-SURV-05). The settings are read from the JVM properties named in {@link RespawnRules}.
 */
public final class RespawnModule implements FeatureModule, Listener {
    public static final String ID = "respawn";

    private final AtomicLong steps = new AtomicLong();
    private volatile RespawnRules rules = RespawnRules.DEFAULT;
    private volatile PlayerDataStore data;
    private volatile LongSupplier playerTicks;
    private volatile HudSnapshotCache hud;
    private volatile Scheduler.TaskHandle throttle;

    public RespawnModule() {
    }

    /** For tests: the module wired without a {@link ModuleContext}. */
    RespawnModule(RespawnRules rules, PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud) {
        this.rules = rules;
        this.data = data;
        this.playerTicks = playerTicks;
        this.hud = hud;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        rules = RespawnRules.fromProperties(System::getProperty);
        data = ctx.playerData();
        playerTicks = () -> ctx.clock().get().playerTicks();
        hud = ctx.hudCache();
        ctx.listen(this);
        if (rules.enablePeacefulDifficultyPassiveRegeneration()) {
            Scheduler scheduler = ctx.scheduler();
            throttle = scheduler.globalRepeating(RespawnRules.PEACEFUL_PERIOD_TICKS, RespawnRules.PEACEFUL_PERIOD_TICKS,
                    () -> {
                        boolean healTick = steps.incrementAndGet() % 2 == 0;
                        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
                            scheduler.entity(player, () -> peacefulStep(player, healTick), null);
                        }
                    });
            ctx.logger().info("Peaceful passive regeneration is on (" + RespawnRules.PEACEFUL_REGEN_PROPERTY + ")");
        }
        if (!rules.keepNutritionAfterDeath()) {
            ctx.logger().info("Nutrition is reset on death (" + RespawnRules.KEEP_NUTRITION_PROPERTY + "=false)");
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

    RespawnRules rules() {
        return rules;
    }

    /** Player thread: a respawn after a death starts from TFC's fresh player data. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return; // leaving the End: TFC's Clone event with isWasDeath() false keeps everything
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        ThirstEffects.write(player, null);
        if (!data.isLoaded(id)) {
            return;
        }
        long now = playerTicks.getAsLong();
        RespawnRules r = rules;
        try {
            data.update(id, d -> r.afterDeath(d, now));
        } catch (IllegalStateException notLoaded) {
            // quit meanwhile: nothing to reset
        }
    }

    /** Player thread: one step of TFC's extra-peaceful branch. Returns what was applied. */
    RespawnRules.PeacefulStep peacefulStep(Player player, boolean healTick) {
        if (!player.isOnline() || player.isDead()) {
            return RespawnRules.PeacefulStep.NONE;
        }
        UUID id = player.getUniqueId();
        if (!data.isLoaded(id)) {
            return RespawnRules.PeacefulStep.NONE;
        }
        boolean peaceful = player.getWorld().getDifficulty() == Difficulty.PEACEFUL;
        boolean active = GameModeNeeds.needsActive(player.getGameMode());
        double max = maxHealth(player);
        double health = player.getHealth();
        float thirstNow = data.get(id).map(PlayerData::thirst).orElse(PlayerData.MAX_THIRST);
        RespawnRules.PeacefulStep step = rules.peacefulStep(peaceful, active, healTick, health > 0 && health < max,
                player.getFoodLevel(), thirstNow);
        if (step.isNone()) {
            return step;
        }
        if (step.heal() > 0) {
            player.heal(Math.min(step.heal(), max - health), EntityRegainHealthEvent.RegainReason.CUSTOM);
        }
        if (step.food() > 0) {
            player.setFoodLevel(Math.min(RespawnRules.MAX_FOOD, player.getFoodLevel() + step.food()));
        }
        if (step.thirst() > 0) {
            long now = playerTicks.getAsLong();
            float temperature = (float) hud.snapshot(id).ambientTemperature();
            try {
                data.update(id, d -> {
                    PlayerData evaluated = GameModeNeeds.evaluate(d, true, now, temperature);
                    return evaluated.withThirst(evaluated.thirst() + step.thirst());
                });
            } catch (IllegalStateException notLoaded) {
                // quit meanwhile
            }
        }
        return step;
    }

    private static double maxHealth(Player player) {
        AttributeInstance a = player.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }
}
