/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.food;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodStage;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * M3-SURV-01: the stamp, the tooltip stage and {@link FoodMergeListener} registered on MockBukkit's plugin
 * manager. Dates are player ticks; the default stack window is {@link FoodConfig#DEFAULT} (7200 ticks).
 */
class FoodMergeListenerMockBukkitTest extends MockServerTest {
    private static final long STEP = FoodConfig.DEFAULT.stackTicks();
    private final AtomicLong now = new AtomicLong(10 * STEP);
    private ItemFoodHooks hooks;

    @BeforeEach
    void register() {
        hooks = new ItemFoodHooks(now::get, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        server.getPluginManager().registerEvents(new FoodMergeListener(hooks), plugin);
    }

    private ItemStack bread(int amount, Long created) {
        ItemStack s = new ItemStack(Material.BREAD, amount);
        if (created != null) {
            assertThat(FoodPdc.write(s, new FoodState(created, List.of()))).isTrue();
        }
        return s;
    }

    private static long created(ItemStack s) {
        return FoodPdc.read(s).orElseThrow().creationDate();
    }

    @Test
    void stampDatesNewFoodOnceWithRoundedTickAndTraits() {
        ItemStack s = new ItemStack(Material.BREAD);
        now.set(10 * STEP + 5);
        assertThat(hooks.stamp(s, List.of(FoodTrait.WILD))).isTrue();
        FoodState st = FoodPdc.read(s).orElseThrow();
        assertThat(st.traits()).containsExactly(FoodTrait.WILD);
        assertThat(st).isEqualTo(FoodStamp.created(10 * STEP + 5, List.of(FoodTrait.WILD), 1f, FoodConfig.DEFAULT));
        now.addAndGet(STEP);
        assertThat(hooks.stamp(s, List.of())).as("an existing date is kept").isFalse();
        assertThat(FoodPdc.read(s)).contains(st);
    }

    @Test
    void decayModifierComesFromTheFoodDefinitionByItemId() {
        // M3-SURV-02: blueberry 4.9 (TFC food data); an item without an id or a definition gets 1.
        dev.tfp.core.food.FoodDefinitions defs = new dev.tfp.core.food.FoodDefinitions(java.util.Map.of(
                "tfp:food/blueberry", new dev.tfp.core.recipe.food.FoodData(4, 5f, 0.5f, 0, 0, 0.9f, 0, 0, 0, 4.9f)));
        ItemStack berry = new ItemStack(Material.SWEET_BERRIES);
        ItemStack bread = new ItemStack(Material.BREAD);
        ItemFoodHooks.DecayModifiers m = ItemFoodHooks.DecayModifiers.byId(
                s -> s.getType() == Material.SWEET_BERRIES ? java.util.Optional.of("tfp:food/blueberry")
                        : s.getType() == Material.BREAD ? java.util.Optional.of("tfp:food/unknown") : java.util.Optional.empty(),
                () -> defs);
        assertThat(m.of(berry)).isEqualTo(4.9f);
        assertThat(m.of(bread)).isEqualTo(1f);
        assertThat(m.of(new ItemStack(Material.STONE))).isEqualTo(1f);
        assertThat(m.of(null)).isEqualTo(1f);
        // the berry rots 4.9 times sooner than a modifier-1 food
        ItemFoodHooks byId = new ItemFoodHooks(now::get, FoodConfig.DEFAULT, m);
        long created = 11 * STEP;
        assertThat(FoodPdc.write(berry, new FoodState(created, List.of()))).isTrue();
        now.set(created + dev.tfp.core.food.FoodDecay.lifeTime(1f / 4.9f));
        assertThat(byId.isRotten(berry)).isTrue();
        assertThat(hooks.isRotten(berry)).as("with UNIT the berry would still be fresh").isFalse();
    }

    @Test
    void tooltipLineFollowsTheStageAtTheCurrentTick() {
        ItemStack s = bread(1, 11 * STEP);
        Messages m = Messages.fromJar();
        assertThat(hooks.stage(s)).contains(FoodStage.FRESH);
        assertThat(hooks.tooltipLine(s, m, Locale.US)).contains("Fresh");
        now.set(11 * STEP + dev.tfp.core.food.FoodDecay.lifeTime(1f));
        assertThat(hooks.isRotten(s)).isTrue();
        assertThat(hooks.tooltipKey(s)).contains(ItemFoodHooks.KEY_ROTTEN);
        assertThat(hooks.tooltipLine(bread(1, dev.tfp.core.food.FoodDecay.NEVER_DECAY_FLAG), m, Locale.US))
                .contains("Never expires");
        assertThat(hooks.tooltipLine(bread(1, null), m, Locale.US)).isEmpty();
    }

    private InventoryClickEvent leftClick(PlayerMock player, Inventory chest, ItemStack slot, ItemStack cursor) {
        chest.setItem(0, slot);
        InventoryView view = player.openInventory(chest);
        view.setCursor(cursor);
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT,
                InventoryAction.SWAP_WITH_CURSOR);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    void stampedAndUnstampedFoodDoNotMerge() {
        PlayerMock player = server.addPlayer();
        Inventory chest = server.createInventory(null, 9);
        InventoryClickEvent e = leftClick(player, chest, bread(3, 11 * STEP), bread(2, null));

        assertThat(e.isCancelled()).as("left to vanilla (a swap)").isFalse();
        assertThat(chest.getItem(0).getAmount()).isEqualTo(3);
        assertThat(FoodPdc.has(e.getView().getCursor())).isFalse();

        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        hopper.setItem(0, bread(3, 11 * STEP));
        ItemStack undated = bread(1, null);
        InventoryMoveItemEvent move = new InventoryMoveItemEvent(server.createInventory(null, InventoryType.HOPPER),
                undated, hopper, true);
        server.getPluginManager().callEvent(move);
        assertThat(FoodPdc.has(move.getItem())).isFalse();
        assertThat(created(hopper.getItem(0))).isEqualTo(11 * STEP);
    }

    @Test
    void stampedFoodsWithTheSameOrCloseDatesMerge() {
        assertThat(hooks.canMerge(bread(1, 11 * STEP), bread(5, 11 * STEP))).as("same date").isTrue();

        // Close dates (one stack window apart): vanilla would swap; the listener merges and keeps the earlier date.
        PlayerMock player = server.addPlayer();
        Inventory chest = server.createInventory(null, 9);
        InventoryClickEvent e = leftClick(player, chest, bread(3, 12 * STEP), bread(2, 11 * STEP));

        assertThat(e.isCancelled()).isTrue();
        assertThat(chest.getItem(0).getAmount()).isEqualTo(5);
        assertThat(created(chest.getItem(0))).isEqualTo(11 * STEP);
        ItemStack cursor = e.getView().getCursor();
        assertThat(cursor == null || cursor.isEmpty()).isTrue();

        // Hopper: destination slot and moved item get the same (earlier) date, so vanilla stacks them.
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        hopper.setItem(0, bread(3, 11 * STEP));
        InventoryMoveItemEvent move = new InventoryMoveItemEvent(server.createInventory(null, InventoryType.HOPPER),
                bread(1, 12 * STEP), hopper, true);
        server.getPluginManager().callEvent(move);
        assertThat(move.getItem().isSimilar(hopper.getItem(0))).isTrue();
        assertThat(created(move.getItem())).isEqualTo(11 * STEP);

        // Too far apart: no merge.
        assertThat(hooks.canMerge(bread(1, 11 * STEP), bread(1, 13 * STEP))).isFalse();
    }
}
