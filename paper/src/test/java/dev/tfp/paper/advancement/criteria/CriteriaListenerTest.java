/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.advancement.CriterionRef;
import dev.tfp.paper.advancement.AdvancementGrants;
import dev.tfp.paper.advancement.Advancements;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.player.NutritionFoodEatenEvent;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-03: the listener awards through the trigger API, with a fake advancement store. */
class CriteriaListenerTest extends MockServerTest {
    private final Set<CriterionRef> awarded = new HashSet<>();
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final FakeCustomItems items = new FakeCustomItems()
        .define("tfp:bellows", n -> new ItemStack(Material.PISTON, n))
        .define("tfp:food/blackberry", n -> new ItemStack(Material.SWEET_BERRIES, n));
    private String biome = "tfp:plains";
    private CriteriaListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        AdvancementGrants grants = new AdvancementGrants() {
            @Override
            public boolean exists(CriterionRef c) {
                return CriteriaMap.DEFAULT.kind(c).isPresent();
            }

            @Override
            public boolean isAwarded(Player p, CriterionRef c) {
                return awarded.contains(c);
            }

            @Override
            public boolean award(Player p, CriterionRef c) {
                return awarded.add(c);
            }
        };
        Advancements advancements = new Advancements(grants, (p, task) -> tasks.add(task), s -> { });
        listener = new CriteriaListener(CriteriaMap.DEFAULT, advancements, stack -> {
            Optional<String> custom = items.idOf(stack);
            return custom.isPresent() ? custom : Optional.of("minecraft:" + stack.getType().name().toLowerCase());
        }, (id, tag) -> tag.equals("minecraft:logs") && id.endsWith("_log"), (id, tag) -> false, at -> biome);
        player = server.addPlayer();
    }

    private void drain() {
        Runnable r;
        while ((r = tasks.poll()) != null) {
            r.run();
        }
    }

    @Test
    void inventoryItemsAndTagsAward() {
        listener.onItem(player, items.create("tfp:bellows", 1).orElseThrow());
        listener.onItem(player, new ItemStack(Material.OAK_LOG));
        listener.onItem(player, new ItemStack(Material.DIRT));
        drain();
        assertThat(awarded).contains(CriterionRef.parse("tfp:story/bellows#bellows"));
        assertThat(awarded).contains(CriterionRef.parse("tfp:story/logging#logs"));
        assertThat(awarded).hasSize(2);
    }

    @Test
    void eatingAFruitAndMealsAward() {
        listener.onMeal(new NutritionFoodEatenEvent(player, "tfp:food/blackberry", false, 0.2f));
        listener.onMeal(new NutritionFoodEatenEvent(player, "tfp:food/mutton", true, Float.NaN));
        listener.onMeal(new NutritionFoodEatenEvent(player, "tfp:food/mutton", false, 1f));
        drain();
        assertThat(awarded).containsExactlyInAnyOrder(CriterionRef.parse("tfp:world/fruit#blackberry"),
            CriteriaMap.EAT_ROTTEN_FOOD, CriteriaMap.FULL_NUTRITION);
    }

    @Test
    void biomeAndGlobeTrotterAward() {
        biome = "tfp:badlands";
        listener.onPosition(player, new Location(player.getWorld(), 10, 70, 20));
        listener.onPosition(player, new Location(player.getWorld(), 10, 70, 10000));
        biome = "minecraft:plains";
        listener.onPosition(player, new Location(player.getWorld(), 10, 70, -10000));
        drain();
        assertThat(awarded).containsExactlyInAnyOrder(CriterionRef.parse("tfp:world/adventuring_time#badlands"),
            CriteriaMap.GLOBE_HIGH, CriteriaMap.GLOBE_LOW);
        // Earned: a second visit queues nothing.
        biome = "tfp:badlands";
        listener.onPosition(player, new Location(player.getWorld(), 10, 70, 20));
        assertThat(tasks).isEmpty();
    }

    @Test
    void presentDayAwardsOnlyWhenTheCalendarPassedTheRealDate() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 25);
        long ticksInYear = dev.tfp.core.calendar.CalendarTicks.ticksInYear(8);
        listener.onClock(today, 500 * ticksInYear, 8, java.util.List.of(player));
        drain();
        assertThat(awarded).isEmpty();
        listener.onClock(today, 1100 * ticksInYear, 8, java.util.List.of(player));
        drain();
        assertThat(awarded).containsExactly(CriteriaMap.THE_FUTURE);
        // Earned: nothing more is queued.
        listener.onClock(today, 1100 * ticksInYear, 8, java.util.List.of(player));
        assertThat(tasks).isEmpty();
    }

    @Test
    void stabAndHookRulesFollowTfc() {
        assertThat(CriteriaMap.DEFAULT.stab("tfp:rabbit", 49.9)).isEmpty();
        assertThat(CriteriaMap.DEFAULT.stab("tfp:rabbit", 50)).contains(CriteriaMap.GREATEST_HUNTER);
        assertThat(CriteriaMap.DEFAULT.stab("tfp:hare", 80)).isEmpty();
        assertThat(CriteriaMap.DEFAULT.hooked("tfp:cod", (id, tag) -> tag.equals(CriteriaMap.SMALL_FISH_TAG)))
            .containsExactly(CriteriaMap.FISHING);
        assertThat(CriteriaMap.DEFAULT.hooked("tfp:orca", (id, tag) -> tag.equals(CriteriaMap.LARGE_FISH_TAG)))
            .containsExactly(CriteriaMap.ADVANCED_FISHING);
        assertThat(CriteriaMap.DEFAULT.hooked("tfp:rabbit", (id, tag) -> false)).isEmpty();
    }
}
