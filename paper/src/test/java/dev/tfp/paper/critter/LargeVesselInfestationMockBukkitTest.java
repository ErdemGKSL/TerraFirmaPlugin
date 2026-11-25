/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.critter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.largevessel.LargeVesselLink;
import dev.tfp.paper.largevessel.LargeVesselScreens;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-38: opening an unsealed large vessel rolls an infestation at its block with the food stacks of its slots (TFC
 * onContainerOpen, LargeVesselContainer.canBeInfested); a sealed one rolls nothing.
 */
class LargeVesselInfestationMockBukkitTest extends MockServerTest {
    private World world;
    private LargeVesselScreens screens;
    private final List<Integer> foods = new ArrayList<>();
    private final List<Location> vessels = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {Scheduler.class}, (proxy, method, args) -> null);
        screens = new LargeVesselScreens(new GuiService(server, scheduler, () -> 0L, true), scheduler, Messages.fromJar(), s -> true);
        screens.pests((viewer, at, food) -> {
            vessels.add(at);
            foods.add(food);
        }, s -> s.getType() == Material.BREAD || s.getType() == Material.APPLE);
    }

    private LargeVesselLink.Key key() {
        return new LargeVesselLink.Key(world.getUID(), -7, 70, 12);
    }

    private static List<ItemStack> slots(ItemStack... stacks) {
        List<ItemStack> nine = new ArrayList<>(List.of(stacks));
        while (nine.size() < 9) {
            nine.add(ItemStack.empty());
        }
        return nine;
    }

    @Test
    void anUnsealedVesselRollsAtItsBlockWithTheFoodStacks() {
        Player p = server.addPlayer();
        screens.rollInfestation(p, key(), new LargeVesselLink.Snapshot(false,
            slots(new ItemStack(Material.BREAD, 3), new ItemStack(Material.STONE, 64), new ItemStack(Material.APPLE))));
        assertThat(foods).containsExactly(2);
        assertThat(vessels).containsExactly(new Location(world, -7, 70, 12));
    }

    @Test
    void theFoodCountIsCappedAtFive() {
        List<ItemStack> bread = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            bread.add(new ItemStack(Material.BREAD));
        }
        screens.rollInfestation(server.addPlayer(), key(), new LargeVesselLink.Snapshot(false, bread));
        assertThat(foods).containsExactly(5);
    }

    @Test
    void aSealedVesselDoesNotRoll() {
        screens.rollInfestation(server.addPlayer(), key(), new LargeVesselLink.Snapshot(true, slots(new ItemStack(Material.BREAD))));
        assertThat(foods).isEmpty();
    }

    @Test
    void anEmptyUnsealedVesselRollsLevelZero() {
        screens.rollInfestation(server.addPlayer(), key(), new LargeVesselLink.Snapshot(false, slots()));
        assertThat(foods).containsExactly(0);
    }
}
