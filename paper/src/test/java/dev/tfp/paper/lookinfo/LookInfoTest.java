/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.lookinfo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.crop.CropState;
import dev.tfp.core.farm.NutrientGrowth;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-12: the readout text per kind, the lang keys, and the overlay's game-mode and sneak gates. */
class LookInfoTest extends MockServerTest {
    private static CropPlant plant(float growth, float yield) {
        return new CropPlant(new CropState(growth, 0f, yield, 0L), new NutrientGrowth.Demand(0f, 0f, 0f));
    }

    @Test
    void cropShowsGrowthAndYieldPercent() {
        List<LookInfoText.Line> lines = LookInfoText.crop(plant(0.456f, 0.755f));
        assertThat(lines).extracting(LookInfoText.Line::key).containsExactly("lookinfo.crop.growth", "lookinfo.crop.yield");
        assertThat(lines.get(0).args()).containsExactly(45);
        assertThat(lines.get(1).args()).containsExactly(76);
    }

    @Test
    void growthIsClamped() {
        assertThat(LookInfoText.crop(plant(1.7f, 1f)).get(0).args()).containsExactly(100);
        assertThat(LookInfoText.crop(plant(-0.2f, 0f)).get(0).args()).containsExactly(0);
    }

    @Test
    void langKeysFormat() {
        Messages messages = Messages.fromJar();
        assertThat(messages.get("en_us", "lookinfo.crop.growth", 45)).contains("Growth").contains("45%");
        assertThat(messages.get("en_us", "lookinfo.crop.yield", 76)).contains("76%");
        assertThat(messages.get("tr_tr", "lookinfo.crop.growth", 45)).contains("45%");
    }

    @Test
    void moduleIsRegistered() throws Exception {
        try (var in = LookInfoTest.class.getResourceAsStream("/META-INF/services/dev.tfp.paper.module.FeatureModule")) {
            assertThat(new String(in.readAllBytes())).contains("dev.tfp.paper.lookinfo.LookInfoModule");
        }
    }

    @Test
    void spectatorAndNonSneakingPlayersTraceNothing() {
        server.addSimpleWorld("w");
        List<String> calls = new ArrayList<>();
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                calls.add(method.getName());
                return null;
            });
        LookInfoOverlay overlay = new LookInfoOverlay(scheduler, null, null, Messages.fromJar(), () -> 0L);
        PlayerMock player = server.addPlayer();
        overlay.check(player); // not sneaking
        player.setSneaking(true);
        player.setGameMode(GameMode.SPECTATOR);
        overlay.check(player);
        assertThat(calls).isEmpty();
    }
}
