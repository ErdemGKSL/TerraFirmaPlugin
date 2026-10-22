/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.itemdata.ContentsPdc;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@code itemdata.pdc_roundtrip} (PAR-INFRA-12): item data written through our PDC codecs survives the real server's
 * item serializers. PAR-INFRA-04 tests {@code tfp:contents} on MockBukkit with an in-memory slot codec, because
 * MockBukkit's serializer cannot write nested PDC containers; here every slot goes through Paper's own
 * {@code ItemStack.serializeAsBytes}.
 *
 * <p>Built: a vessel ({@code tfp:contents}, TFC {@code ItemListComponent}) holding a salted bread with a creation date
 * ({@code tfp:food}), an empty slot and raw copper with a heat record ({@code tfp:heat}); a jug filled with water
 * through {@link ItemFluids} ({@code tfp:fluid}). Both carry a foreign PDC key. Each is copied through:
 * <ul>
 *   <li>{@code serializeAsBytes}/{@code deserializeBytes} (the codec of the slots themselves);</li>
 *   <li>{@code serializeItemsAsBytes}/{@code deserializeItemsFromBytes} (Paper's inventory batch format);</li>
 *   <li>a YAML configuration ({@code ConfigurationSerializable}, what plugins save to disk);</li>
 *   <li>Paper's item JSON ({@code UnsafeValues.serializeItemAsJson});</li>
 *   <li>a server inventory ({@code setItem}/{@code getItem}: the conversion to and from the vanilla stack that every
 *       container and player inventory save uses), on the global region thread;</li>
 *   <li>all of the above in sequence.</li>
 * </ul>
 * After each copy the nested food and heat states are read back with {@link FoodPdc}/{@link HeatPdc} from the vessel's
 * slots, the jug's fluid with {@link ItemFluids}, and the foreign keys must be kept. Any difference fails the case.
 */
public final class ItemDataRoundTripCase implements SelfTestCase {
    static final FoodState FOOD = new FoodState(48_000L, List.of(FoodTrait.SALTED));
    static final HeatState HEAT = new HeatState(812.5f, 12_345L);
    static final String WATER = "minecraft:water";
    static final NamespacedKey FOREIGN = new NamespacedKey("tfp_selftest", "foreign");
    private static final Duration STEP = Duration.ofSeconds(10);

    @Override
    public String name() {
        return "itemdata.pdc_roundtrip: nested tfp:food/tfp:heat inside tfp:contents and a tfp:fluid jug survive Paper's"
                + " item serializers and an inventory copy (PAR-INFRA-12)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        Scheduler s = ctx.tfp().scheduler();
        return OnThread.script("itemdata", () -> check(s));
    }

    private static String check(Scheduler s) throws Exception {
        ItemStack vessel = vessel();
        ItemStack jug = jug();
        // The originals must already read back, or the copies below prove nothing.
        verify("original", vessel, jug);

        Map<String, UnaryOperator<ItemStack>> copies = new LinkedHashMap<>();
        copies.put("bytes", st -> ItemStack.deserializeBytes(st.serializeAsBytes()));
        copies.put("items-bytes", st -> ItemStack.deserializeItemsFromBytes(ItemStack.serializeItemsAsBytes(List.of(st)))[0]);
        copies.put("yaml", ItemDataRoundTripCase::viaYaml);
        copies.put("json", ItemDataRoundTripCase::viaJson);
        copies.put("inventory", st -> viaInventory(s, st));

        List<String> done = new ArrayList<>();
        ItemStack chainedVessel = vessel;
        ItemStack chainedJug = jug;
        for (Map.Entry<String, UnaryOperator<ItemStack>> c : copies.entrySet()) {
            verify(c.getKey(), c.getValue().apply(vessel), c.getValue().apply(jug));
            chainedVessel = c.getValue().apply(chainedVessel);
            chainedJug = c.getValue().apply(chainedJug);
            done.add(c.getKey());
        }
        verify("chained", chainedVessel, chainedJug);
        done.add("chained");
        int vesselBytes = vessel.serializeAsBytes().length;
        return "food " + FOOD.creationDate() + " and heat " + HEAT.temperature() + " C read back from the vessel slots,"
                + " jug " + ItemFluids.content(jug) + ", via " + String.join(", ", done) + " (vessel " + vesselBytes + " bytes)";
    }

    static ItemStack vessel() {
        ItemStack bread = new ItemStack(Material.BREAD, 3);
        expect("write tfp:food on the bread", true, FoodPdc.write(bread, FOOD));
        ItemStack ore = new ItemStack(Material.RAW_COPPER, 2);
        expect("write tfp:heat on the ore", true, HeatPdc.write(ore, HEAT));
        ItemStack vessel = new ItemStack(Material.DECORATED_POT);
        expect("write tfp:contents on the vessel", true, ContentsPdc.write(vessel, Arrays.asList(bread, null, ore)));
        tagForeign(vessel, 7);
        return vessel;
    }

    static ItemStack jug() {
        ItemStack jug = new ItemStack(Material.FLOWER_POT);
        int cap = FluidContainerKind.JUG.defaultCapacity();
        expect("fill the jug", cap, ItemFluids.fill(jug, new FluidAmount(WATER, cap * 2), FluidContainerKind.JUG, WATER::equals));
        tagForeign(jug, 9);
        return jug;
    }

    static void verify(String path, ItemStack vessel, ItemStack jug) {
        if (vessel == null || jug == null) {
            throw new AssertionError(path + ": the copy returned no stack");
        }
        expect(path + ": vessel type", Material.DECORATED_POT, vessel.getType());
        expect(path + ": vessel has tfp:contents", true, ContentsPdc.has(vessel));
        List<ItemStack> slots = ContentsPdc.read(vessel)
                .orElseThrow(() -> new AssertionError(path + ": tfp:contents does not read back"));
        expect(path + ": slot count", 3, slots.size());
        ItemStack bread = slots.get(0);
        expect(path + ": slot 0", Material.BREAD + " x3", bread.getType() + " x" + bread.getAmount());
        expect(path + ": slot 1 empty", true, slots.get(1).isEmpty());
        ItemStack ore = slots.get(2);
        expect(path + ": slot 2", Material.RAW_COPPER + " x2", ore.getType() + " x" + ore.getAmount());
        expect(path + ": nested tfp:food", FOOD, FoodPdc.read(bread).orElse(null));
        expect(path + ": nested tfp:heat", HEAT, HeatPdc.read(ore).orElse(null));
        expect(path + ": bread has no heat", false, HeatPdc.has(bread));
        expect(path + ": vessel foreign key", 7, foreign(vessel));

        expect(path + ": jug type", Material.FLOWER_POT, jug.getType());
        int cap = FluidContainerKind.JUG.defaultCapacity();
        expect(path + ": jug fluid", new FluidAmount(WATER, cap), ItemFluids.content(jug));
        expect(path + ": jug max stack size", 1, jug.getMaxStackSize());
        expect(path + ": jug foreign key", 9, foreign(jug));
    }

    private static ItemStack viaYaml(ItemStack stack) {
        YamlConfiguration out = new YamlConfiguration();
        out.set("item", stack);
        YamlConfiguration in = new YamlConfiguration();
        try {
            in.loadFromString(out.saveToString());
        } catch (InvalidConfigurationException e) {
            throw new AssertionError("yaml: the saved item does not load: " + e.getMessage(), e);
        }
        return in.getItemStack("item");
    }

    @SuppressWarnings("deprecation") // UnsafeValues is Paper's only item JSON codec
    private static ItemStack viaJson(ItemStack stack) {
        return Bukkit.getUnsafe().deserializeItemFromJson(Bukkit.getUnsafe().serializeItemAsJson(stack));
    }

    private static ItemStack viaInventory(Scheduler s, ItemStack stack) {
        try {
            return OnThread.global(s, STEP, () -> {
                Inventory inv = Bukkit.createInventory(null, 9);
                inv.setItem(4, stack);
                return inv.getItem(4);
            });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void tagForeign(ItemStack stack, int value) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(FOREIGN, PersistentDataType.INTEGER, value);
        stack.setItemMeta(meta);
    }

    private static Integer foreign(ItemStack stack) {
        return stack.hasItemMeta() ? stack.getItemMeta().getPersistentDataContainer().get(FOREIGN, PersistentDataType.INTEGER) : null;
    }

    private static void expect(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
