/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (addExhaustion, tick)
 */
package dev.tfp.paper.player.exhaustion;

import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.player.GameModeNeeds;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.player.ThirstDecay;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;

/**
 * PAR-SURV-15, TFC {@code PlayerInfo.addExhaustion}: every vanilla exhaustion source of a player (sprint, jump, mining,
 * attack, damage, swim...) is multiplied by 0.4 and counted in the same pool as the passive exhaustion, whose completed
 * points cost thirst and one saturation or food point each. The vanilla event is cancelled, so the vanilla
 * {@code FoodData} never spends it a second time; the {@code REGEN} reason is left to {@code HealthRegenerator}. No
 * exhaustion in creative and spectator (needs paused, PROTOCOL 4.7).
 */
public final class ExhaustionModule implements FeatureModule, Listener {
    public static final String ID = "exhaustion";

    private volatile PlayerDataStore data;
    private volatile LongSupplier playerTicks;
    private volatile HudSnapshotCache hud;

    public ExhaustionModule() {
    }

    /** Tests: wired without a context. */
    public ExhaustionModule(PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud) {
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
        data = ctx.playerData();
        playerTicks = () -> ctx.clock().get().playerTicks();
        hud = ctx.hudCache();
        ctx.listen(this);
    }

    /** Player thread (the event is fired on the owning thread). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getExhaustionReason() == EntityExhaustionEvent.ExhaustionReason.REGEN) {
            return;
        }
        PlayerDataStore store = data;
        if (store == null || !store.isLoaded(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        boolean active = GameModeNeeds.needsActive(player.getGameMode()) && !player.isInvulnerable();
        if (!active) {
            return;
        }
        float amount = event.getExhaustion();
        long now = playerTicks.getAsLong();
        float temperature = (float) hud.snapshot(player.getUniqueId()).ambientTemperature();
        store.update(player.getUniqueId(), d -> ThirstDecay.addExhaustion(d, amount, now, temperature));
        FoodSpend.drain(player, store, true);
    }
}
