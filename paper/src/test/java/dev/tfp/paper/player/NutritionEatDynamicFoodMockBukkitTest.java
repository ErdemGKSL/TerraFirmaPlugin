/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.EatDecision;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.salad.SaladPdc;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-36: eating a dynamic food (a salad with {@code tfp:dynamic_food}) records the stack's own food data, like
 * TFC's {@code FoodComponent.getData()}, not the item definition's ({@code food/salad.json}: hunger 4, no nutrients).
 */
class NutritionEatDynamicFoodMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000e476");
    static final String SALAD = "tfp:food/vegetables_salad";
    /** TFC data {@code food/salad.json} (as the definitions see it): hunger 4, decay 4.5, nothing else. */
    static final FoodData DEFINITION = new FoodData(4, 0f, 0f, 0, 0f, 0f, 0f, 0f, 0f, 4.5f);
    /** A carrot + red apple salad (TFC SaladContainer: x0.75, hunger 4, decay 4.0). */
    static final FoodData MIX = new FoodData(4, 7.5f, 0.75f, 0, 0f, 0.5625f, 0.75f, 0f, 0f, 4.0f);
    static final FoodDefinitions DEFS = new FoodDefinitions(Map.of(SALAD, DEFINITION));

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final List<Event> fired = new ArrayList<>();
    PlayerDataStore store;
    PlayerMock alex;
    NutritionEatListener listener;

    @BeforeEach
    void setUp() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        ItemFoodHooks hooks = new ItemFoodHooks(() -> 1_000L, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        listener = new NutritionEatListener(store, () -> 1_000L, NutritionEatListenerMockBukkitTest.ITEMS, () -> DEFS,
                hooks, new ServerScheduler(plugin, 1), fired::add, () -> 0.0);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private ItemStack salad() {
        ItemStack stack = NutritionEatListenerMockBukkitTest.ITEMS.create(SALAD, 1).orElseThrow();
        assertThat(SaladPdc.write(stack, MIX, List.of("tfp:food/carrot", "tfp:food/red_apple"), "tfp:ceramic/bowl"))
                .isTrue();
        return stack;
    }

    private void consume(ItemStack held) {
        alex.getInventory().setItemInMainHand(held);
        listener.onConsume(new PlayerItemConsumeEvent(alex, held.clone(), EquipmentSlot.HAND));
    }

    @Test
    void eatingASaladRecordsItsMixedNutrientsAndWater() {
        float thirst = store.get(ALEX).orElseThrow().thirst();
        consume(salad());
        PlayerData after = store.get(ALEX).orElseThrow();
        assertThat(after.nutrition()).hasSize(1);
        PlayerData.FoodRecord record = after.nutrition().getFirst();
        assertThat(record.nutrients().vegetables()).isEqualTo(0.75f);
        assertThat(record.nutrients().fruit()).isEqualTo(0.5625f);
        assertThat(record.saturation()).isEqualTo(0.75f);
        assertThat(record.decayModifier()).isEqualTo(4.0f);
        assertThat(after.thirst()).isEqualTo(Math.min(thirst + 7.5f, 100f));
    }

    @Test
    void aSaladWithoutDynamicDataUsesTheDefinition() {
        consume(NutritionEatListenerMockBukkitTest.ITEMS.create(SALAD, 1).orElseThrow());
        PlayerData.FoodRecord record = store.get(ALEX).orElseThrow().nutrition().getFirst();
        assertThat(record.nutrients().vegetables()).isZero();
        assertThat(record.decayModifier()).isEqualTo(4.5f);
    }

    @Test
    void onlyEatDecisionsTakeTheDynamicFood() {
        ItemStack stack = salad();
        EatDecision eat = new EatDecision(EatDecision.Action.EAT, SALAD, Optional.of(DEFINITION), Optional.empty());
        assertThat(NutritionEatListener.dynamic(eat, stack).food()).contains(MIX);
        EatDecision block = new EatDecision(EatDecision.Action.BLOCK, SALAD, Optional.empty(), Optional.empty());
        assertThat(NutritionEatListener.dynamic(block, stack)).isSameAs(block);
        ItemStack plain = NutritionEatListenerMockBukkitTest.ITEMS.create(SALAD, 1).orElseThrow();
        assertThat(NutritionEatListener.dynamic(eat, plain)).isSameAs(eat);
    }
}
