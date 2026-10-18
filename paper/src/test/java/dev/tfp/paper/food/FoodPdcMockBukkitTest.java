/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.food;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** M3-SURV-01: the food item PDC codec and the stack merge helper on MockBukkit item meta. */
class FoodPdcMockBukkitTest extends MockServerTest {
    private static final FoodConfig CFG = FoodConfig.DEFAULT;
    private static final long NOW = 100_000L;

    private static ItemStack food(FoodState state) {
        ItemStack stack = new ItemStack(Material.BREAD, 4);
        if (state != null) {
            assertThat(FoodPdc.write(stack, state)).isTrue();
        }
        return stack;
    }

    @Test
    void roundTripKeepsStateAndForeignData() {
        ItemStack stack = new ItemStack(Material.BREAD);
        NamespacedKey foreign = new NamespacedKey("other", "x");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(foreign, PersistentDataType.INTEGER, 7);
        stack.setItemMeta(meta);
        FoodState state = new FoodState(48_000L, List.of(FoodTrait.SALTED, new FoodTrait("other:smoky", 1f)));

        FoodPdc.write(stack, state);

        assertThat(FoodPdc.read(stack.clone())).contains(state);
        assertThat(stack.getItemMeta().getPersistentDataContainer().get(foreign, PersistentDataType.INTEGER)).isEqualTo(7);
        FoodState rotten = new FoodState(FoodDecay.ROTTEN_FLAG, List.of());
        FoodPdc.write(stack, rotten);
        assertThat(FoodPdc.read(stack)).contains(rotten);
        FoodPdc.write(stack, null);
        assertThat(FoodPdc.has(stack)).isFalse();
    }

    @Test
    void itemWithoutKeyReadsEmpty() {
        assertThat(FoodPdc.read(new ItemStack(Material.BREAD))).isEmpty();
        assertThat(FoodPdc.read(new ItemStack(Material.STONE, 3))).isEmpty();
    }

    @Test
    void mergeFollowsTheCoreStackRule() {
        FoodState a = FoodState.createdAt(NOW, CFG);
        FoodState close = new FoodState(a.creationDate() + CFG.stackTicks(), List.of());
        FoodState far = new FoodState(a.creationDate() + CFG.stackTicks() + 1, List.of());
        FoodState salted = new FoodState(a.creationDate(), List.of(FoodTrait.SALTED));

        assertThat(FoodStacks.canMerge(food(a), food(close), 1f, NOW, CFG)).isTrue();
        assertThat(FoodStacks.canMerge(food(a), food(far), 1f, NOW, CFG)).isFalse();
        assertThat(FoodStacks.canMerge(food(a), food(salted), 1f, NOW, CFG)).isFalse();
        assertThat(FoodStacks.canMerge(food(a), food(null), 1f, NOW, CFG)).isFalse();
        assertThat(FoodStacks.canMerge(food(null), food(null), 1f, NOW, CFG)).isTrue();
        ItemStack other = new ItemStack(Material.APPLE, 4);
        FoodPdc.write(other, a);
        assertThat(FoodStacks.canMerge(food(a), other, 1f, NOW, CFG)).isFalse();

        ItemStack target = food(close);
        FoodStacks.mergeInto(target, food(a));
        assertThat(FoodPdc.read(target)).contains(a);
    }
}
