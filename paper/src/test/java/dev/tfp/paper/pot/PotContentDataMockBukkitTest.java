/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.cooking.SoupMix;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotOutput;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.firepit.FirepitEffects;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.recipe.ItemModifierHooks;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.salad.SaladPdc;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-09 on the real datagen output: TFC's 214 pot recipes decode; three vegetables in 1000 mB of water on a hot
 * pot boil into the soup fallback, two servings of vegetable soup with the mixed nutrition of {@link SoupMix}; a bowl
 * takes one serving (the bowl stored on it), the second bowl the other, and the pot is empty after. Also the simple
 * recipe (boiled eggs) and a jam with jars; a vanilla water bucket fills the pot; the firepit <-> pot conversion drops
 * the old device's ash as wood ash.
 */
class PotContentDataMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final NamespacedKey ID = new NamespacedKey("test", "id");

    /** CraftEngine items as paper stacks with their id on the PDC (survives clones). */
    static final class PdcItems implements CustomItems {
        @Override
        public boolean exists(String id) {
            return id.startsWith("tfp:");
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            if (!exists(id)) {
                return Optional.empty();
            }
            ItemStack s = new ItemStack(Material.PAPER, amount);
            ItemMeta m = s.getItemMeta();
            m.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
            s.setItemMeta(m);
            return Optional.of(s);
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            if (stack == null || !stack.hasItemMeta()) {
                return Optional.empty();
            }
            return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        }
    }

    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

        @Override
        public ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }

    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    static final class Effects implements PotEffects {
        final List<ItemStack> given = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Player> screens = new ArrayList<>();
        int toFirepit;
        float hurt;

        @Override
        public void show(Block block, BlockStateRef state) {
        }

        @Override
        public void sound(Block block, Sound sound) {
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void hurt(Player player, float amount) {
            hurt += amount;
        }

        @Override
        public void callbackIn(Block block, long ticks) {
        }

        @Override
        public void breakBlock(Block block) {
        }

        @Override
        public void toFirepit(Block block) {
            toFirepit++;
        }

        @Override
        public void openScreen(Player player, Block block) {
            screens.add(player);
        }
    }

    /** The firepit's world effects: only the spawned stacks (its removal's ash) are recorded. */
    static final class PitEffects implements FirepitEffects {
        final List<ItemStack> spawned = new ArrayList<>();

        @Override
        public void show(Block block, BlockStateRef state) {
        }

        @Override
        public void sound(Block block, Sound sound) {
        }

        @Override
        public void smoke(Block block, int smokeLevel) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void give(Player player, ItemStack stack) {
        }

        @Override
        public void useDouse(Player player, EquipmentSlot hand, Douse douse) {
        }

        @Override
        public void callbackIn(Block block, long ticks) {
        }

        @Override
        public void breakBlock(Block block) {
        }
    }

    private final PdcItems items = new PdcItems();
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(PotDevice.ID, PotModule.FIREPIT_ID);
    private final PitEffects pitEffects = new PitEffects();
    private final MemoryStore store = new MemoryStore();
    private final TableCodec codec = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private PotRecipeBook book;
    private FoodDefinitions foods;
    private DeviceService service;
    private PotDevice device;
    private FirepitDevice firepit;
    private PotConversion conversion;
    private Block pot;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        Registries registries = new Registries();
        registries.reload(Runnable::run, () -> {
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).join();
        TagRules rules = TagRules.load(root);
        Tags tags = new Tags(registries, items, new FakeCustomBlocks(), ServerTags.BUKKIT, rules, () -> rules,
            Runnable::run, err -> {
                throw new AssertionError(err);
            });
        ItemFoodHooks food = new ItemFoodHooks(clock::get, FoodConfig.DEFAULT, ItemFoodHooks.DecayModifiers.UNIT);
        ItemHeatHooks heat = new ItemHeatHooks(clock::get, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        foods = FoodDefinitions.fromRegistry(registries.get());
        Recipes recipes = new Recipes(registries, tags, RecipeModule::vanillaIds, Runnable::run, err -> {
            throw new AssertionError(err);
        }, new ItemModifierHooks.Services(items, tags::itemId, food, () -> foods, heat,
            ItemHeatHooks.Capacities.UNIT, clock::get));
        recipes.rebuild().join();
        book = new PotRecipeBook(new PotRecipeBook.Services(recipes,
            stack -> PotModule.food(stack, tags::itemId, () -> foods), food::isRotten,
            stack -> food.stamp(stack, List.of()), tags::itemId,
            stack -> stack.getType() == Material.MILK_BUCKET ? Optional.of(new ItemStack(Material.BUCKET)) : Optional.empty()));

        World world = server.addSimpleWorld("pot");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, items,
            Logger.getLogger("test"));
        BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId, (k, f) -> true,
            id -> items.create(id, 1), items::exists));
        device = new PotDevice(new PotDevice.Services(s -> Optional.empty(), s -> false, book, tanks,
            s -> s.getType() == Material.BOWL, s -> tags.itemId(s).map(PotModule.EMPTY_JAR::equals).orElse(false),
            s -> tags.is(s, PotModule.EMPTY_JARS_WITH_LID), b -> false, b -> true,
            n -> items.create(PotDevice.WOOD_ASH, n).orElseThrow(), () -> items.create(PotDevice.POT_ITEM, 1).orElseThrow(),
            () -> () -> 0L, HeatConfig.DEFAULT, effects), codec);
        service.register(device.kind());
        firepit = new FirepitDevice(new FirepitDevice.Services(st -> Optional.empty(), st -> false, st -> 0.0,
            new FirepitDevice.Cooker() {
                @Override
                public float recipeTemperature(ItemStack stack) {
                    return Float.NaN;
                }

                @Override
                public Optional<ItemStack> convert(ItemStack heated, float temperature) {
                    return Optional.empty();
                }
            }, st -> FirepitEffects.Douse.NONE, b -> false, b -> true,
            n -> items.create(PotDevice.WOOD_ASH, n).orElseThrow(), () -> () -> 0L, HeatConfig.DEFAULT, pitEffects), codec);
        service.register(firepit.kind());
        conversion = new PotConversion(service, blocks, device.kind(), this::place);
        pot = world.getBlockAt(0, 64, 0);
        blocks.place(pot, BlockStateRef.of(PotDevice.ID).with("lit", "true").with("smoke_level", "0").with("axis", "x"),
            true);
        store.data.put(FakeCustomBlocks.at(pot), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 2.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** CraftEngine replacing a custom block: the old block entity's removal, then a fresh record for the new one. */
    private boolean place(Block at, BlockStateRef state) {
        if (!blocks.exists(state.id())) {
            return false;
        }
        Optional<BlockStateRef> old = blocks.stateAt(at);
        if (old.isPresent()) {
            DataMap data = store.data.remove(FakeCustomBlocks.at(at));
            service.removed(at.getWorld(), at.getX(), at.getY(), at.getZ(), old.get().id(), data == null ? new DataMap() : data);
        }
        blocks.place(at, state, true);
        store.data.put(FakeCustomBlocks.at(at), service.created(state.id()).orElse(new DataMap()));
        return true;
    }

    private ItemStack item(String id) {
        return items.create(id, 1).orElseThrow();
    }

    /** A hot, lit pot (burning for longer than any recipe) holding {@code inputs} and {@code fluid}. */
    private void fill(FluidAmount fluid, String... inputs) {
        FirepitData hot = new FirepitData(new FirepitState(100_000, 800f, 800f, 0), true, 0, 0f, 0, 100_000, false);
        service.edit(pot, device.kind(), (record, now) -> {
            DeviceRecord<PotData> r = record;
            for (int i = 0; i < inputs.length; i++) {
                r = r.withSlot(4 + i, codec.encode(item(inputs[i])));
            }
            return r.withState(PotData.FRESH.withFirepit(hot).withFluid(fluid));
        });
    }

    private DeviceRecord<PotData> record() {
        return device.kind().codec().decode(store.read(pot).orElseThrow());
    }

    private void click(ItemStack held) {
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        service.use(pot, player, EquipmentSlot.HAND, held);
    }

    @Test
    void allPotRecipesLoad() {
        List<PotRecipeView> all = book.all();
        assertThat(all.stream().filter(r -> r.kind() == PotRecipeView.Kind.SIMPLE)).hasSize(145);
        assertThat(all.stream().filter(r -> r.kind() == PotRecipeView.Kind.JAM)).hasSize(66);
        assertThat(all.stream().filter(r -> r.kind() == PotRecipeView.Kind.SOUP)).hasSize(3);
        assertThat(all).allSatisfy(r -> assertThat(r.fluidIngredient().amount()).as(r.id()).isPositive());
    }

    @Test
    void threeVegetablesBoilIntoTwoServingsOfSoupThatBowlsTakeOut() {
        fill(new FluidAmount("minecraft:water", 1000), "tfp:food/carrot", "tfp:food/potato", "tfp:food/cabbage");
        Optional<PotRecipeView> recipe = device.recipe(record());
        assertThat(recipe).isPresent();
        assertThat(recipe.get().kind()).isEqualTo(PotRecipeView.Kind.SOUP);

        clock.addAndGet(100 + recipe.get().duration() + 1);
        service.catchUp(pot);
        DeviceRecord<PotData> done = record();
        assertThat(done.state().fluid().isEmpty()).as("the soup clears the fluid").isTrue();
        for (int i = 4; i <= 8; i++) {
            assertThat(done.slot(i).isEmpty()).as("input %d", i).isTrue();
        }
        assertThat(done.state().output()).isInstanceOf(PotOutput.Soup.class);
        ItemStack soup = codec.decode(((PotOutput.Soup) done.state().output()).stack());
        assertThat(items.idOf(soup)).contains(PotRecipeBook.soupItem(Nutrient.VEGETABLES));
        assertThat(soup.getAmount()).isEqualTo(2); // ingredientsToServings(3)

        FoodData expected = SoupMix.mix(List.of(
            new SoupMix.Ingredient(foods.get("tfp:food/carrot").orElseThrow(), false),
            new SoupMix.Ingredient(foods.get("tfp:food/potato").orElseThrow(), false),
            new SoupMix.Ingredient(foods.get("tfp:food/cabbage").orElseThrow(), false))).orElseThrow().food();
        assertThat(SaladPdc.food(soup)).contains(expected);
        assertThat(expected.vegetables()).isPositive();
        assertThat(SaladPdc.ingredients(soup)).containsExactly("tfp:food/carrot", "tfp:food/potato", "tfp:food/cabbage");

        click(new ItemStack(Material.BOWL, 2));
        assertThat(effects.given).hasSize(1);
        ItemStack serving = effects.given.getFirst();
        assertThat(serving.getAmount()).isEqualTo(1);
        assertThat(items.idOf(serving)).contains("tfp:food/vegetables_soup");
        assertThat(SaladPdc.food(serving)).contains(expected);
        assertThat(SaladPdc.bowl(serving)).contains("minecraft:bowl");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);

        click(new ItemStack(Material.BOWL, 1));
        assertThat(effects.given).hasSize(2);
        assertThat(record().state().hasOutput()).as("both servings taken").isFalse();
        click(new ItemStack(Material.BOWL, 1));
        assertThat(effects.given).as("no soup left: the click opens the screen").hasSize(2);
        assertThat(effects.screens).hasSize(1);
    }

    @Test
    void anOutputWaitsWhileThePotIsHotAndNoNewRecipeStarts() {
        fill(new FluidAmount("minecraft:water", 1000), "tfp:food/carrot", "tfp:food/potato", "tfp:food/cabbage");
        clock.addAndGet(100_000);
        service.catchUp(pot);
        assertThat(record().state().hasOutput()).isTrue();
        assertThat(record().state().counters().boilingTicks()).isZero();
    }

    @Test
    void eggsInWaterBoilIntoBoiledEggsAndTheWaterLeftStays() {
        fill(new FluidAmount("minecraft:water", 1000));
        // vanilla eggs (the fake items only make tfp: ids)
        service.edit(pot, device.kind(), (record, now) -> record.withSlot(4, codec.encode(new ItemStack(Material.EGG)))
            .withSlot(5, codec.encode(new ItemStack(Material.EGG))));
        Optional<PotRecipeView> recipe = device.recipe(record());
        assertThat(recipe).isPresent();
        assertThat(recipe.get().kind()).isEqualTo(PotRecipeView.Kind.SIMPLE);
        clock.addAndGet(100 + recipe.get().duration() + 1);
        service.catchUp(pot);
        DeviceRecord<PotData> done = record();
        assertThat(items.idOf(codec.decode(done.slot(4)))).contains("tfp:food/boiled_egg");
        assertThat(items.idOf(codec.decode(done.slot(5)))).contains("tfp:food/boiled_egg");
        assertThat(done.state().hasOutput()).isFalse();
        // uses_all_fluid defaults to true and there is no fluid output: the tank is emptied, as TFC
        assertThat(done.state().fluid()).isEqualTo(FluidAmount.EMPTY);
    }

    @Test
    void sneakingWithAnEmptyHandTakesTheLitPotOffAndBurns() {
        fill(new FluidAmount("minecraft:water", 200));
        player.setSneaking(true);
        click(ItemStack.empty());
        assertThat(effects.toFirepit).isEqualTo(1);
        assertThat(effects.hurt).isEqualTo(PotDevice.BURN_DAMAGE);
        assertThat(effects.given).singleElement().satisfies(s -> assertThat(items.idOf(s)).contains(PotDevice.POT_ITEM));
    }

    /** The soup of three vegetables, waiting in a pot that has cooled (so clicks are not refused for boiling). */
    private void cooledSoup() {
        fill(new FluidAmount("minecraft:water", 1000), "tfp:food/carrot", "tfp:food/potato", "tfp:food/cabbage");
        clock.addAndGet(100_000);
        service.catchUp(pot);
        assertThat(record().state().hasOutput()).isTrue();
        service.edit(pot, device.kind(), (r, now) -> r.withState(r.state().withFirepit(FirepitData.FRESH)));
    }

    @Test
    void aVanillaWaterBucketFillsThePotAndTheEmptyBucketIsReturned() {
        click(new ItemStack(Material.WATER_BUCKET));
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);
    }

    @Test
    void aWaitingOutputRefusesNewFluid() {
        cooledSoup();
        click(new ItemStack(Material.WATER_BUCKET));
        assertThat(record().state().fluid().isEmpty()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.WATER_BUCKET);
    }

    @Test
    void takingSoupInCreativeStillUsesTheBowlAsTfc() {
        cooledSoup();
        player.setGameMode(GameMode.CREATIVE);
        click(new ItemStack(Material.BOWL, 2));
        assertThat(effects.given).hasSize(1);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
    }

    @Test
    void aPotOnAFirepitWithAshDropsTheAshAsWoodAsh() {
        Block pit = pot.getWorld().getBlockAt(4, 64, 0);
        assertThat(place(pit, BlockStateRef.of(PotModule.FIREPIT_ID).with("lit", "false").with("smoke_level", "0")
            .with("axis", "z"))).isTrue();
        service.edit(pit, firepit.kind(), (r, now) -> r.withState(r.state().withAsh(5)));
        assertThat(conversion.toPot(pit)).isTrue();
        assertThat(blocks.stateAt(pit).orElseThrow().id()).isEqualTo(PotDevice.ID);
        assertThat(pitEffects.spawned).singleElement().satisfies(st -> {
            assertThat(items.idOf(st)).contains(PotDevice.WOOD_ASH);
            assertThat(st.getAmount()).isEqualTo(5);
        });
        assertThat(device.kind().codec().decode(store.read(pit).orElseThrow()).state().firepit().ash()).isZero();
    }

    @Test
    void takingALitPotWithAshOffDropsTheAshAsWoodAsh() {
        fill(new FluidAmount("minecraft:water", 200));
        service.edit(pot, device.kind(), (r, now) -> r.withState(r.state().withFirepit(r.state().firepit().withAsh(3))));
        assertThat(conversion.toFirepit(pot)).isTrue();
        assertThat(blocks.stateAt(pot).orElseThrow().id()).isEqualTo(PotModule.FIREPIT_ID);
        assertThat(effects.spawned).singleElement().satisfies(st -> {
            assertThat(items.idOf(st)).contains(PotDevice.WOOD_ASH);
            assertThat(st.getAmount()).isEqualTo(3);
        });
        FirepitData after = firepit.kind().codec().decode(store.read(pot).orElseThrow()).state();
        assertThat(after.ash()).isZero();
        assertThat(after.lit()).isTrue();
    }
}
