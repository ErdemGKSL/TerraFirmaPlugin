/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.effect.InkListener;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-HUD-20: {@code tfp_ink} (TFC {@code renderInk}): effect added, removed, expired, respawn, game modes. */
class InkOverlayServiceTest extends MockServerTest {
    InkOverlayService service;

    @AfterEach
    void clear() {
        if (service != null) {
            try {
                service.disable();
            } catch (org.mockbukkit.mockbukkit.exception.UnimplementedOperationException e) {
                // MockBukkit cannot cancel a global repeating task; the readings are cleared before the cancel.
            }
        }
    }

    private PlayerMock bot() {
        service = new InkOverlayService(new ServerScheduler(plugin, 1));
        return server.addPlayer("Bot");
    }

    private static PotionEffect ink() {
        return new PotionEffect(InkListener.INK_CARRIER, InkListener.INK_TICKS, 0);
    }

    private static PotionEffect glow() {
        return new PotionEffect(InkListener.GLOW_INK_CARRIER, InkListener.INK_TICKS, 0);
    }

    private void add(PlayerMock p, PotionEffect e, Cause cause) {
        service.onEffect(new EntityPotionEffectEvent(p, null, e, cause, Action.ADDED, false));
    }

    @Test
    void valueFollowsTfcRuleInkFirstThenGlowAndNothingInSpectator() {
        assertThat(InkOverlayService.value(0, GameMode.SURVIVAL)).isZero();
        assertThat(InkOverlayService.value(1, GameMode.SURVIVAL)).isEqualTo(InkOverlayService.INK);
        assertThat(InkOverlayService.value(2, GameMode.SURVIVAL)).isEqualTo(InkOverlayService.GLOW_INK);
        assertThat(InkOverlayService.value(3, GameMode.ADVENTURE)).as("ink wins").isEqualTo(InkOverlayService.INK);
        assertThat(InkOverlayService.value(3, GameMode.CREATIVE)).as("TFC checks only the effect").isEqualTo(InkOverlayService.INK);
        assertThat(InkOverlayService.value(3, GameMode.SPECTATOR)).isZero();
    }

    @Test
    void onlyTheEffectInkListenerAppliesCounts() {
        assertThat(InkOverlayService.isOurs(ink(), Cause.PLUGIN)).isTrue();
        assertThat(InkOverlayService.isOurs(glow(), Cause.PLUGIN)).isTrue();
        assertThat(InkOverlayService.isOurs(ink(), Cause.WARDEN)).as("a Warden's darkness").isFalse();
        assertThat(InkOverlayService.isOurs(ink(), Cause.COMMAND)).isFalse();
        assertThat(InkOverlayService.isOurs(new PotionEffect(InkListener.GLOW_INK_CARRIER, 200, 0), Cause.PLUGIN))
            .as("the glow arrow lasts 200 ticks").isFalse();
        assertThat(InkOverlayService.isOurs(new PotionEffect(InkListener.INK_CARRIER, 100, 1), Cause.PLUGIN)).isFalse();
        assertThat(InkOverlayService.isOurs(new PotionEffect(PotionEffectType.SPEED, 100, 0), Cause.PLUGIN)).isFalse();
        assertThat(InkOverlayService.isOurs(null, Cause.PLUGIN)).isFalse();
    }

    @Test
    void addedPublishesAtOnceAndRemovedHides() {
        PlayerMock bot = bot();
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isZero();

        add(bot, ink(), Cause.PLUGIN);
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isEqualTo(1);

        service.onEffect(new EntityPotionEffectEvent(bot, ink(), null, Cause.EXPIRATION, Action.REMOVED, false));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).as("expired").isZero();

        add(bot, glow(), Cause.PLUGIN);
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isEqualTo(2);
        service.onEffect(new EntityPotionEffectEvent(bot, null, null, Cause.COMMAND, Action.CLEARED, false));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).as("cleared").isZero();
    }

    @Test
    void aVanillaCarrierDrawsNothing() {
        PlayerMock bot = bot();
        add(bot, ink(), Cause.WARDEN);
        add(bot, glow(), Cause.COMMAND);
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isZero();
    }

    @Test
    void inkAndGlowInkTogetherShowInkThenGlowAfterInkEnds() {
        PlayerMock bot = bot();
        add(bot, glow(), Cause.PLUGIN);
        add(bot, ink(), Cause.PLUGIN);
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isEqualTo(1);
        service.onEffect(new EntityPotionEffectEvent(bot, ink(), null, Cause.EXPIRATION, Action.REMOVED, false));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isEqualTo(2);
    }

    @Test
    void respawnAndQuitClear() {
        PlayerMock bot = bot();
        add(bot, ink(), Cause.PLUGIN);
        service.onRespawn(new PlayerRespawnEvent(bot, bot.getLocation(), false));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isZero();

        add(bot, ink(), Cause.PLUGIN);
        service.onQuit(new PlayerQuitEvent(bot, net.kyori.adventure.text.Component.empty(),
            PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isZero();
    }

    @Test
    void spectatorHidesAndLeavingItShowsTheRunningInkAgain() {
        PlayerMock bot = bot();
        add(bot, ink(), Cause.PLUGIN);
        service.onGameMode(new PlayerGameModeChangeEvent(bot, GameMode.SPECTATOR,
            PlayerGameModeChangeEvent.Cause.COMMAND, null));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isZero();
        service.onGameMode(new PlayerGameModeChangeEvent(bot, GameMode.CREATIVE,
            PlayerGameModeChangeEvent.Cause.COMMAND, null));
        assertThat(InkOverlayService.reading(bot.getUniqueId())).as("creative still draws it").isEqualTo(1);
    }

    @Test
    void checkDropsAMarkWhoseCarrierIsGone() {
        PlayerMock bot = bot();
        add(bot, ink(), Cause.PLUGIN); // the mark is set, but the player has no real effect
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isEqualTo(1);
        service.reconcile(bot);
        assertThat(InkOverlayService.reading(bot.getUniqueId())).isZero();

        add(bot, ink(), Cause.PLUGIN);
        bot.addPotionEffect(ink());
        service.reconcile(bot);
        assertThat(InkOverlayService.reading(bot.getUniqueId())).as("the effect is there").isEqualTo(1);
    }
}
