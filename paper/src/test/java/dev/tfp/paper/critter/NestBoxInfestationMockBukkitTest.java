/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.critter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.nestbox.NestBoxLink;
import dev.tfp.paper.nestbox.NestBoxScreens;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.scheduler.Scheduler;
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

/** PAR-FAUNA-32: opening a nest box rolls an infestation at its block with the food stacks in its slots (TFC onContainerOpen). */
class NestBoxInfestationMockBukkitTest extends MockServerTest {
    private World world;
    private NestBoxScreens screens;
    private final List<Integer> foods = new ArrayList<>();
    private final List<Location> boxes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {Scheduler.class}, (proxy, method, args) -> null);
        screens = new NestBoxScreens(new GuiService(server, scheduler, () -> 0L, true), scheduler, Messages.fromJar(), s -> true);
        screens.pests((viewer, box, food) -> {
            boxes.add(box);
            foods.add(food);
        }, s -> s.getType() == Material.EGG || s.getType() == Material.BREAD);
    }

    private NestBoxLink.Key key() {
        return new NestBoxLink.Key(world.getUID(), 3, 64, -2);
    }

    @Test
    void anOpenRollsAtTheBoxWithTheFoodStacksOnly() {
        Player p = server.addPlayer();
        screens.rollInfestation(p, key(), List.of(new ItemStack(Material.EGG, 1), new ItemStack(Material.STONE),
            new ItemStack(Material.BREAD, 4), ItemStack.empty()));
        assertThat(foods).containsExactly(2);
        assertThat(boxes.get(0)).isEqualTo(new Location(world, 3, 64, -2));
    }

    @Test
    void theFoodCountIsCappedAtFive() {
        Player p = server.addPlayer();
        List<ItemStack> eggs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            eggs.add(new ItemStack(Material.EGG));
        }
        screens.rollInfestation(p, key(), eggs);
        assertThat(foods).containsExactly(5);
    }

    @Test
    void anEmptyBoxRollsLevelZero() {
        screens.rollInfestation(server.addPlayer(), key(), List.of(ItemStack.empty(), ItemStack.empty()));
        assertThat(foods).containsExactly(0);
    }
}
