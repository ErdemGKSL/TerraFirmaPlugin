/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.EatDecision;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.assertj.core.data.Offset;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-SURV-03: the consume listener on MockBukkit. A TFC food is recorded and fires the eaten hook; vanilla foods TFC
 * has no definition for are refused; vanilla items TFC defines (pumpkin pie, milk) are eaten TFC's way instead of
 * vanilla's; rotten food fires the rotten hook; peaceful changes nothing; the records persist across a reload.
 */
class NutritionEatListenerMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000e475");
    static final NamespacedKey CUSTOM_ID = new NamespacedKey("test", "custom_id");
    static final FoodData MUTTON = new FoodData(4, 0f, 0f, 0, 0f, 0f, 0f, 1.5f, 0f, 3f);
    static final FoodData PIE = new FoodData(4, 5f, 2f, 0, 1f, 0f, 2f, 1f, 0.3f, 2.5f);
    static final FoodDefinitions DEFS = new FoodDefinitions(Map.of(
            "tfp:food/mutton", MUTTON, "minecraft:pumpkin_pie", PIE, "tfp:melon", MUTTON), Set.of("tfp:melon"));

    /** Custom ids read from a test PDC key: Paper's consume event hands out a copy of the stack. */
    static final CustomItems ITEMS = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return true;
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            ItemStack stack = new ItemStack(Material.MUTTON, amount);
            stack.editMeta(m -> m.getPersistentDataContainer().set(CUSTOM_ID, PersistentDataType.STRING, id));
            return Optional.of(stack);
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            if (stack == null || !stack.hasItemMeta()) {
                return Optional.empty();
            }
            return Optional.ofNullable(
                    stack.getItemMeta().getPersistentDataContainer().get(CUSTOM_ID, PersistentDataType.STRING));
        }
    };

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
        listener = new NutritionEatListener(store, () -> 1_000L, ITEMS, () -> DEFS, hooks,
                new ServerScheduler(plugin, 1), fired::add, () -> 0.0);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private PlayerItemConsumeEvent consume(ItemStack held) {
        alex.getInventory().setItemInMainHand(held);
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(alex, held.clone(), EquipmentSlot.HAND);
        listener.onConsume(event);
        return event;
    }

    private NutritionFoodEatenEvent onlyEatenEvent() {
        assertThat(fired).hasSize(1);
        return (NutritionFoodEatenEvent) fired.getFirst();
    }

    @Test
    void aTfcFoodIsRecordedTheVanillaLevelIsSetNextTickAndTheHookFires() {
        alex.setFoodLevel(10);
        alex.setSaturation(0f);
        PlayerItemConsumeEvent event = consume(ITEMS.create("tfp:food/mutton", 1).orElseThrow());

        assertThat(event.isCancelled()).isFalse();
        assertThat(store.get(ALEX).orElseThrow().nutrition()).hasSize(1);
        assertThat(store.get(ALEX).orElseThrow().nutrition().getFirst().nutrients().protein()).isEqualTo(1.5f);
        alex.setFoodLevel(18); // what vanilla's own nutrition would leave after the event
        server.getScheduler().performOneTick();
        assertThat(alex.getFoodLevel()).as("TFC's value: 10 + 4").isEqualTo(14);
        NutritionFoodEatenEvent eaten = onlyEatenEvent();
        assertThat(eaten.itemId()).isEqualTo("tfp:food/mutton");
        assertThat(eaten.rotten()).isFalse();
        assertThat(eaten.averageNutrition())
                .isCloseTo(NutritionEating.averageNutrition(store.get(ALEX).orElseThrow(), 14), Offset.offset(1e-6f));
    }

    /** TFC removes the food component of vanilla food without a TFC definition: bread cannot be eaten. */
    @Test
    void vanillaFoodWithoutATfcDefinitionIsRefused() {
        alex.setFoodLevel(10);
        PlayerItemConsumeEvent event = consume(new ItemStack(Material.BREAD, 3));

        assertThat(event.isCancelled()).isTrue();
        assertThat(store.get(ALEX).orElseThrow().nutrition()).isEmpty();
        assertThat(alex.getInventory().getItemInMainHand().getAmount()).isEqualTo(3);
        assertThat(fired).isEmpty();
    }

    @Test
    void aCustomFoodWhoseDefinitionIsInedibleIsRefused() {
        PlayerItemConsumeEvent event = consume(ITEMS.create("tfp:melon", 1).orElseThrow());
        assertThat(event.isCancelled()).isTrue();
        assertThat(fired).isEmpty();
    }

    @Test
    void aPotionIsNotTfcEating() {
        PlayerItemConsumeEvent event = consume(new ItemStack(Material.POTION));
        assertThat(event.isCancelled()).isFalse();
        assertThat(fired).isEmpty();
    }

    /** TFC data {@code food/pumpkin_pie.json}: vanilla's pie is eaten with TFC's values, not vanilla's 8 food. */
    @Test
    void aVanillaFoodTfcDefinesIsEatenTfcsWayInstead() {
        alex.setFoodLevel(10);
        alex.setSaturation(1f);
        store.update(ALEX, d -> d.withThirst(50f));
        PlayerItemConsumeEvent event = consume(new ItemStack(Material.PUMPKIN_PIE, 2));

        assertThat(event.isCancelled()).as("vanilla's consume is replaced").isTrue();
        assertThat(alex.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(alex.getFoodLevel()).isEqualTo(14);
        assertThat(alex.getSaturation()).isCloseTo(3f, Offset.offset(1e-5f));
        PlayerData after = store.get(ALEX).orElseThrow();
        assertThat(after.nutrition()).hasSize(1);
        assertThat(after.thirst()).as("the pie's 5 water").isEqualTo(55f);
        assertThat(onlyEatenEvent().itemId()).isEqualTo("minecraft:pumpkin_pie");
    }

    /** TFC {@code MilkBucketItemMixin}: dairy is recorded, effects stay, the bucket is left. */
    @Test
    void milkRecordsDairyKeepsEffectsAndLeavesABucket() {
        alex.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
        PlayerItemConsumeEvent event = consume(new ItemStack(Material.MILK_BUCKET));

        assertThat(event.isCancelled()).isTrue();
        assertThat(alex.hasPotionEffect(PotionEffectType.SPEED)).isTrue();
        assertThat(alex.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);
        assertThat(store.get(ALEX).orElseThrow().nutrition().getFirst().nutrients().dairy()).isEqualTo(2f);
        assertThat(onlyEatenEvent().itemId()).isEqualTo(EatDecision.MILK_BUCKET);
    }

    @Test
    void inCreativeTheReplacedConsumeKeepsTheItem() {
        alex.setGameMode(GameMode.CREATIVE);
        consume(new ItemStack(Material.MILK_BUCKET));
        assertThat(alex.getInventory().getItemInMainHand().getType()).isEqualTo(Material.MILK_BUCKET);
        assertThat(store.get(ALEX).orElseThrow().nutrition()).hasSize(1);
    }

    /** TFC {@code PlayerInfo.java} 178-190: no nutrition, Hunger II (and Poison I), then EAT_ROTTEN_FOOD. */
    @Test
    void rottenFoodGivesTheEffectsAndFiresTheRottenHook() {
        alex.setFoodLevel(10);
        ItemStack rotten = ITEMS.create("tfp:food/mutton", 1).orElseThrow();
        FoodPdc.write(rotten, new FoodState(FoodDecay.ROTTEN_FLAG, List.of()));
        PlayerItemConsumeEvent event = consume(rotten);

        assertThat(event.isCancelled()).isFalse();
        assertThat(store.get(ALEX).orElseThrow().nutrition()).isEmpty();
        assertThat(alex.getPotionEffect(PotionEffectType.HUNGER).getAmplifier()).isEqualTo(1);
        assertThat(alex.getPotionEffect(PotionEffectType.POISON).getDuration()).isEqualTo(1800);
        server.getScheduler().performOneTick();
        assertThat(alex.getFoodLevel()).as("rotten: the level is left as it was").isEqualTo(10);
        NutritionFoodEatenEvent eaten = onlyEatenEvent();
        assertThat(eaten.rotten()).isTrue();
        assertThat(eaten.averageNutrition()).isNaN();
    }

    /** TFC's {@code PlayerInfo#eat} does not look at the difficulty: peaceful eats the same way. */
    @Test
    void peacefulEatsTheSameWay() {
        alex.getWorld().setDifficulty(Difficulty.PEACEFUL);
        alex.setFoodLevel(10);
        consume(ITEMS.create("tfp:food/mutton", 1).orElseThrow());
        server.getScheduler().performOneTick();
        assertThat(store.get(ALEX).orElseThrow().nutrition()).hasSize(1);
        assertThat(alex.getFoodLevel()).isEqualTo(14);
        assertThat(onlyEatenEvent().rotten()).isFalse();
    }

    @Test
    void vanillaFoodIsRefusedEvenWhenThePlayerDataIsNotLoaded() {
        store.unload(ALEX).join();
        PlayerItemConsumeEvent bread = consume(new ItemStack(Material.BREAD));
        assertThat(bread.isCancelled()).isTrue();
        PlayerItemConsumeEvent mutton = consume(ITEMS.create("tfp:food/mutton", 1).orElseThrow());
        assertThat(mutton.isCancelled()).isFalse();
        assertThat(fired).isEmpty();
    }

    /** The in-game criterion's persistence half: the eaten record is saved and read back after a restart. */
    @Test
    void theEatenRecordPersistsAcrossARestart() throws Exception {
        consume(ITEMS.create("tfp:food/mutton", 1).orElseThrow());
        PlayerData eaten = store.get(ALEX).orElseThrow();
        store.unload(ALEX).get(10, java.util.concurrent.TimeUnit.SECONDS); // quit: saved to disk

        PlayerDataStore restarted = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        restarted.loadBlocking(ALEX);
        assertThat(restarted.get(ALEX).orElseThrow().nutrition()).isEqualTo(eaten.nutrition());
    }
}
