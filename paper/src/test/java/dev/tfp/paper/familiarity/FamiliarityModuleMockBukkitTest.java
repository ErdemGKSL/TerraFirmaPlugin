/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.familiarity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.familiarity.FamiliarityReadout;
import dev.tfp.core.familiarity.FamiliarityReadout.Band;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FAUNA-22: the sneak-look action bar: one message per text change, none out of range, tasks stop on unsneak and quit. */
class FamiliarityModuleMockBukkitTest extends MockServerTest {
    private final List<Boolean> cancelled = new ArrayList<>();
    private int started;
    private FamiliarityModule module;
    private PlayerMock player;
    private Cow cow;
    private Entity looked;
    private FamiliarityReadout readout = new FamiliarityReadout(Band.WHITE, 0.5f, false);
    private boolean percent;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("w");
        cow = world.spawn(new Location(world, 2, 64, 0), Cow.class);
        player = server.addPlayer();
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                if (method.getName().equals("entityRepeating")) {
                    started++;
                    int idx = cancelled.size();
                    cancelled.add(false);
                    return new Scheduler.TaskHandle() {
                        @Override
                        public void cancel() {
                            cancelled.set(idx, true);
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled.get(idx);
                        }
                    };
                }
                return null;
            });
        ModuleContext ctx = new ModuleContext.Of((JavaPlugin) plugin, Logger.getLogger("tfp-test"), scheduler, null, null, null, null,
            dev.tfp.paper.i18n.Messages.fromJar(), null, null, null, null, null, new ModuleServices());
        module = new FamiliarityModule();
        module.enable(ctx);
        module.initForTest(p -> looked, e -> e == cow ? Optional.of(readout) : Optional.empty(), () -> percent,
            (locale, name) -> "This " + name + " is pregnant.");
        player.setSneaking(true);
    }

    private String lastBar() {
        Component c = player.nextActionBar();
        return c == null ? null : PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void sneakLookingAtAnAnimalSendsOneActionBarAndRepeatsOnlyOnChange() {
        looked = cow;
        module.show(player);
        assertThat(lastBar()).isEqualTo("❤".repeat(5) + "♡".repeat(5));
        module.show(player);
        assertThat(lastBar()).as("same text, same tick: nothing re-sent").isNull();
        readout = new FamiliarityReadout(Band.WHITE, 0.7f, false);
        module.show(player);
        assertThat(lastBar()).isEqualTo("❤".repeat(7) + "♡".repeat(3));
    }

    @Test
    void percentOptionShowsTheTwoDecimalPercentAndPregnantAddsTheLine() {
        looked = cow;
        percent = true;
        readout = new FamiliarityReadout(Band.RED, 0.8765f, true);
        module.show(player);
        assertThat(lastBar()).isEqualTo("87.65%  This cow is pregnant.");
    }

    @Test
    void nothingIsSentWhenNotLookingAtAnAnimalOrNotSneaking() {
        looked = null;
        module.show(player);
        assertThat(lastBar()).isNull();
        looked = server.addPlayer();
        module.show(player);
        assertThat(lastBar()).isNull();
        looked = cow;
        player.setSneaking(false);
        module.show(player);
        assertThat(lastBar()).isNull();
    }

    @Test
    void theTaskStartsOnSneakAndStopsOnUnsneakAndQuit() {
        module.onSneak(new PlayerToggleSneakEvent(player, true));
        assertThat(started).isEqualTo(1);
        assertThat(cancelled).containsExactly(false);
        module.onSneak(new PlayerToggleSneakEvent(player, false));
        assertThat(cancelled).containsExactly(true);

        module.onSneak(new PlayerToggleSneakEvent(player, true));
        assertThat(cancelled).containsExactly(true, false);
        module.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertThat(cancelled).containsExactly(true, true);
    }
}
