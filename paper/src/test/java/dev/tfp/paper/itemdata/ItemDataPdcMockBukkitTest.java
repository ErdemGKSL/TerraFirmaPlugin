/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.itemdata.ItemListLimits;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-04: the fluid and item-list PDC codecs and their callers on MockBukkit item meta. */
class ItemDataPdcMockBukkitTest extends MockServerTest {
    private static final String WATER = "minecraft:water";

    private static void markNewer(ItemStack stack, String key) {
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer inner = pdc.getAdapterContext().newPersistentDataContainer();
        inner.set(new NamespacedKey("tfp", "v"), PersistentDataType.INTEGER, 99);
        inner.set(new NamespacedKey("tfp", "future"), PersistentDataType.STRING, "keep me");
        pdc.set(NamespacedKey.fromString(key), PersistentDataType.TAG_CONTAINER, inner);
        stack.setItemMeta(meta);
    }

    @Test
    void fluidRoundTripKeepsForeignData() {
        ItemStack jug = new ItemStack(Material.FLOWER_POT);
        NamespacedKey foreign = new NamespacedKey("other", "x");
        ItemMeta meta = jug.getItemMeta();
        meta.getPersistentDataContainer().set(foreign, PersistentDataType.INTEGER, 7);
        jug.setItemMeta(meta);

        assertThat(FluidPdc.read(jug)).isEmpty();
        assertThat(FluidPdc.write(jug, new FluidAmount(WATER, 75))).isTrue();
        assertThat(FluidPdc.read(jug.clone())).contains(new FluidAmount(WATER, 75));
        assertThat(jug.getItemMeta().getPersistentDataContainer().get(foreign, PersistentDataType.INTEGER)).isEqualTo(7);
        assertThat(FluidPdc.write(jug, FluidAmount.EMPTY)).isTrue();
        assertThat(FluidPdc.has(jug)).isFalse();
        assertThat(FluidPdc.write(new ItemStack(Material.AIR), new FluidAmount(WATER, 1))).isFalse();
    }

    @Test
    void newerVersionsAreKeptUntouched() {
        ItemStack bucket = new ItemStack(Material.BUCKET);
        markNewer(bucket, FluidPdc.KEY);
        ItemStack before = bucket.clone();
        assertThat(FluidPdc.read(bucket)).isEmpty();
        assertThat(FluidPdc.write(bucket, new FluidAmount(WATER, 1000))).isFalse();
        assertThat(ItemFluids.fill(bucket, new FluidAmount(WATER, 1000), 1000, true)).isZero();
        assertThat(ItemFluids.drain(bucket, 100)).isEqualTo(FluidAmount.EMPTY);
        assertThat(bucket).isEqualTo(before);

        ItemStack vessel = new ItemStack(Material.DECORATED_POT);
        markNewer(vessel, ContentsPdc.KEY);
        ItemStack vBefore = vessel.clone();
        assertThat(ContentsPdc.read(vessel)).isEmpty();
        assertThat(ContentsPdc.write(vessel, List.of(new ItemStack(Material.STONE)))).isFalse();
        ItemStack rest = ItemContents.insert(vessel, 4, 0, new ItemStack(Material.STONE, 3), 64, s -> true);
        assertThat(rest.getAmount()).isEqualTo(3);
        assertThat(vessel).isEqualTo(vBefore);
    }

    @Test
    void contentsRoundTripWithEmptySlots() {
        ItemStack vessel = new ItemStack(Material.DECORATED_POT);
        List<ItemStack> slots = Arrays.asList(new ItemStack(Material.RAW_COPPER, 5), null,
                new ItemStack(Material.AIR), new ItemStack(Material.RAW_GOLD, 2));
        assertThat(ContentsPdc.write(vessel, slots)).isTrue();

        List<ItemStack> read = ContentsPdc.read(vessel.clone()).orElseThrow();
        assertThat(read).hasSize(4);
        assertThat(read.get(0)).isEqualTo(new ItemStack(Material.RAW_COPPER, 5));
        assertThat(read.get(1).isEmpty()).isTrue();
        assertThat(read.get(2).isEmpty()).isTrue();
        assertThat(read.get(3)).isEqualTo(new ItemStack(Material.RAW_GOLD, 2));

        assertThat(ContentsPdc.write(vessel, null)).isTrue();
        assertThat(ContentsPdc.has(vessel)).isFalse();
    }

    @Test
    void contentsOverTheBoundsAreRefused() {
        ItemStack vessel = new ItemStack(Material.DECORATED_POT);
        List<ItemStack> many = new ArrayList<>();
        for (int i = 0; i <= ItemListLimits.MAX_SLOTS; i++) {
            many.add(null);
        }
        assertThat(ContentsPdc.write(vessel, many)).isFalse();
        assertThat(ContentsPdc.has(vessel)).isFalse();
    }

    /**
     * A vessel's slots keep each stack's own data: food dates and heat records read back from the nested stacks.
     * MockBukkit's item serializer cannot write nested PDC containers, so this runs with an in-memory slot codec that
     * keeps the stack as the real serializer does; the real Paper bytes are checked by the follow-up self-test case.
     */
    @Test
    void nestedStacksKeepFoodAndHeat() {
        List<ItemStack> store = new ArrayList<>();
        ContentsPdc.codec = new ContentsPdc.StackCodec() {
            @Override
            public byte[] encode(ItemStack stack) {
                store.add(stack.clone());
                return new byte[] {(byte) (store.size() - 1)};
            }

            @Override
            public ItemStack decode(byte[] bytes) {
                return store.get(bytes[0]).clone();
            }
        };
        try {
            nestedStacksKeepFoodAndHeatWithCodec();
        } finally {
            ContentsPdc.codec = ContentsPdc.PAPER;
        }
    }

    private static void nestedStacksKeepFoodAndHeatWithCodec() {
        ItemStack bread = new ItemStack(Material.BREAD, 3);
        FoodState food = new FoodState(48_000L, List.of(FoodTrait.SALTED));
        FoodPdc.write(bread, food);
        ItemStack ore = new ItemStack(Material.RAW_COPPER, 2);
        HeatState heat = new HeatState(812.5f, 12_345L);
        HeatPdc.write(ore, heat);

        ItemStack vessel = new ItemStack(Material.DECORATED_POT);
        assertThat(ContentsPdc.write(vessel, List.of(bread, ore))).isTrue();

        List<ItemStack> read = ContentsPdc.contents(vessel.clone());
        assertThat(FoodPdc.read(read.get(0))).contains(food);
        assertThat(HeatPdc.read(read.get(1))).contains(heat);
    }

    /** Caller: a jug filled and drained through ItemFluids with TFC's container rules and the jug's capacity. */
    @Test
    void jugFillAndDrain() {
        ItemStack jug = new ItemStack(Material.FLOWER_POT);
        int jugCap = FluidContainerKind.JUG.defaultCapacity();

        assertThat(ItemFluids.fill(jug, new FluidAmount(WATER, 5000), FluidContainerKind.JUG, WATER::equals)).isEqualTo(jugCap);
        assertThat(ItemFluids.content(jug)).isEqualTo(new FluidAmount(WATER, jugCap));
        assertThat(ItemFluids.fill(jug, new FluidAmount("tfc:salt_water", 10), jugCap, true)).isZero();
        assertThat(ItemFluids.fill(new ItemStack(Material.FLOWER_POT), new FluidAmount("minecraft:lava", 10),
                FluidContainerKind.JUG, WATER::equals)).isZero();

        assertThat(ItemFluids.drain(jug, new FluidAmount("minecraft:lava", 10))).isEqualTo(FluidAmount.EMPTY);
        assertThat(ItemFluids.drain(jug, 30)).isEqualTo(new FluidAmount(WATER, 30));
        assertThat(ItemFluids.content(jug)).isEqualTo(new FluidAmount(WATER, jugCap - 30));
        assertThat(ItemFluids.drain(jug, 10_000)).isEqualTo(new FluidAmount(WATER, jugCap - 30));
        assertThat(FluidPdc.has(jug)).isFalse();
    }

    /** Caller: a 4-slot vessel takes items through ItemContents, with TFC's slot rules. */
    @Test
    void vesselInsertAndExtract() {
        ItemStack vessel = new ItemStack(Material.DECORATED_POT);
        ItemStack rest = ItemContents.insert(vessel, 4, 1, new ItemStack(Material.RAW_COPPER, 40), 16, s -> true);
        assertThat(rest).isEqualTo(new ItemStack(Material.RAW_COPPER, 24));
        assertThat(ItemContents.slots(vessel, 4).get(1)).isEqualTo(new ItemStack(Material.RAW_COPPER, 16));

        rest = ItemContents.insert(vessel, 4, 1, new ItemStack(Material.RAW_GOLD, 1), 16, s -> true);
        assertThat(rest).isEqualTo(new ItemStack(Material.RAW_GOLD, 1));
        rest = ItemContents.insert(vessel, 4, 0, new ItemStack(Material.STICK, 1), 16, s -> s.getType() != Material.STICK);
        assertThat(rest).isEqualTo(new ItemStack(Material.STICK, 1));

        assertThat(ItemContents.extract(vessel, 4, 1, 5)).isEqualTo(new ItemStack(Material.RAW_COPPER, 5));
        assertThat(ItemContents.extract(vessel, 4, 1, 64)).isEqualTo(new ItemStack(Material.RAW_COPPER, 11));
        assertThat(ItemContents.extract(vessel, 4, 1, 1).isEmpty()).isTrue();
        assertThat(ItemContents.slots(vessel, 4)).allMatch(ItemStack::isEmpty);
    }

    /** No merge when the fluid or the item list differs. */
    @Test
    void mergeNeedsEqualContents() {
        ItemStack a = new ItemStack(Material.BUCKET);
        ItemStack b = new ItemStack(Material.BUCKET);
        assertThat(ItemContents.canMerge(a, b)).isTrue();
        FluidPdc.write(a, new FluidAmount(WATER, 1000));
        assertThat(ItemContents.canMerge(a, b)).isFalse();
        FluidPdc.write(b, new FluidAmount(WATER, 1000));
        assertThat(ItemContents.canMerge(a, b)).isTrue();

        ContentsPdc.write(a, List.of(new ItemStack(Material.RAW_COPPER, 2)));
        ContentsPdc.write(b, List.of(new ItemStack(Material.RAW_COPPER, 3)));
        assertThat(ItemContents.canMerge(a, b)).isFalse();
        ContentsPdc.write(b, List.of(new ItemStack(Material.RAW_COPPER, 2)));
        assertThat(ItemContents.canMerge(a, b)).isTrue();
    }
}
