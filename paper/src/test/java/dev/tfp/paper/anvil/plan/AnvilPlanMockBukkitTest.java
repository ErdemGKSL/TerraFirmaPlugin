/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil.plan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilRecipes;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.anvil.AnvilClicks;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.grill.TickScheduler;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-04 on MockBukkit: the plan screen over a copper anvil with fakes of the CraftEngine adapter and the recipe index.
 * Stand-ins: iron ingot = the copper ingot (nineteen plans, to page), gold ingot = an item with exactly one plan, diamond = an
 * item with none; the plans' result items are other vanilla materials.
 */
class AnvilPlanMockBukkitTest extends MockServerTest {
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

    static final class Effects implements AnvilEffects {
        @Override
        public void show(Block block, View view) {
        }

        @Override
        public void hide(Block block) {
        }

        @Override
        public void state(Block block, BlockStateRef wanted) {
        }
    }

    /** Item ids of the vanilla materials, no tags, no food or heat or fluid. */
    static final IngredientContext<ItemStack> CTX = new IngredientContext<>() {
        @Override
        public String itemId(ItemStack stack) {
            return "minecraft:" + stack.getType().name().toLowerCase();
        }

        @Override
        public TagLookup tags() {
            return new TagLookup() {
                @Override
                public boolean contains(TagType type, String tag, String id) {
                    return false;
                }

                @Override
                public boolean exists(TagType type, String tag) {
                    return false;
                }
            };
        }

        @Override
        public FoodHooks<ItemStack> food() {
            return new FoodHooks<>() {
                @Override
                public boolean isRotten(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean hasTrait(ItemStack stack, String trait) {
                    return false;
                }
            };
        }

        @Override
        public HeatHooks<ItemStack> heat() {
            return stack -> 0f;
        }

        @Override
        public FluidHooks<ItemStack> fluids() {
            return stack -> Optional.empty();
        }
    };

    private static final String COPPER = "tfp:metal/anvil/copper";
    private static final String GRANITE = "tfp:rock/anvil/granite";
    /** The recipes: nineteen on iron ingots (the first is "pick"), one on gold, one on iron that needs tier 2. */
    static final Material[] RESULTS = {Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE,
        Material.STONE_SWORD, Material.WOODEN_PICKAXE, Material.WOODEN_AXE, Material.WOODEN_SHOVEL, Material.WOODEN_HOE,
        Material.WOODEN_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE,
        Material.IRON_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE};

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(COPPER, GRANITE);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicLong now = new AtomicLong(100_000);
    private final FakeCustomItems items = new FakeCustomItems();
    private TickScheduler scheduler;
    private DeviceService devices;
    private AnvilPlanScreens screens;
    private AnvilRecipes recipes;
    private World world;
    private Block anvil;
    private PlayerMock player;
    private final List<Block> chosenAt = new ArrayList<>();

    private static AnvilRecipe plan(String name, String input, int tier, Material result, int count) {
        return new AnvilRecipe("tfc:anvil/" + name, new Ingredient.Item(input), tier, List.of(ForgeRule.HIT_LAST),
            false, new CraftOutput("minecraft:" + result.name().toLowerCase(), count));
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("anvilplan");
        scheduler = new TickScheduler();
        List<AnvilRecipe> list = new ArrayList<>();
        for (int i = 0; i < RESULTS.length; i++) {
            list.add(plan("iron_" + i, "minecraft:iron_ingot", 1, RESULTS[i], 1 + i % 3));
        }
        list.add(plan("gold_only", "minecraft:gold_ingot", 1, Material.GOLDEN_SWORD, 1));
        list.add(plan("iron_tier2", "minecraft:iron_ingot", 2, Material.NETHERITE_PICKAXE, 1));
        recipes = new AnvilRecipes(list);
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, items, Logger.getLogger("test"));
        GuiService gui = new GuiService(server, scheduler, now::get, true);
        screens = new AnvilPlanScreens(gui, scheduler, devices, new PlanBook(recipes), () -> CTX, items, blocks, p -> "Anvil");
        screens.onChosen((viewer, block) -> chosenAt.add(block));
        AnvilDevice.Services services = new AnvilDevice.Services(s -> s.getType() == Material.IRON_PICKAXE,
            s -> s.getType() == Material.GUNPOWDER, new Effects(), new AnvilClicks(), screens);
        devices.register(AnvilDevice.kind(AnvilDevice.METAL_ID, services));
        devices.register(AnvilDevice.kind(AnvilDevice.ROCK_ID, services));
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        anvil = world.getBlockAt(0, 64, 0);
        blocks.place(anvil, BlockStateRef.of(COPPER).with("facing", "east"), true);
        store.data.put(FakeCustomBlocks.at(anvil), new DataMap());
        player = server.addPlayer("Bot");
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<AnvilState> record() {
        DeviceKind<AnvilState> kind = (DeviceKind<AnvilState>) devices.kinds().find(COPPER).orElseThrow();
        return kind.codec().decode(store.read(anvil).orElseThrow());
    }

    private ItemStack slot(int slot) {
        return stacks.decode(record().slot(slot));
    }

    /** A sneaking click with {@code type} in the hand: puts one into the anvil. */
    private void insert(Material type) {
        player.setSneaking(true);
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(type, 2));
        assertThat(devices.use(anvil, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND))).isTrue();
        player.setSneaking(false);
    }

    private GuiSession open() {
        Optional<GuiSession> s = screens.openNow(player, anvil);
        assertThat(s).isPresent();
        return s.get();
    }

    private InventoryClickEvent click(int raw) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, raw, ClickType.LEFT,
            InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    void anItemWithManyPlansIsNotAssignedOneAndListsThemOnThisTiersOnly() {
        insert(Material.IRON_INGOT);
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN))).as("19 plans, none chosen yet").isNull();
        GuiSession s = open();
        AnvilPlanScreens.Plans p = AnvilPlanScreens.plans(s);
        assertThat(p.tier()).isEqualTo(1);
        assertThat(p.recipes()).hasSize(19); // the tier 2 recipe is left out on a copper anvil
        assertThat(p.recipes()).extracting(AnvilRecipe::id).doesNotContain("tfc:anvil/iron_tier2");
    }

    @Test
    void theScreenShowsTheFirstPageOfEighteenWithTheResultItemsAndAnArrow() {
        insert(Material.IRON_INGOT);
        GuiSession s = open();
        for (int cell = 0; cell < 18; cell++) {
            ItemStack icon = s.getInventory().getItem(cell);
            assertThat(icon).as("cell " + cell).isNotNull();
            assertThat(icon.getType()).isEqualTo(RESULTS[cell]);
            assertThat(GuiButtons.isButton(icon)).isTrue();
        }
        assertThat(s.getInventory().getItem(0).getAmount()).isEqualTo(1);
        assertThat(s.getInventory().getItem(1).getAmount()).as("TFC draws the result's count").isEqualTo(2);
        assertThat(s.gui().frames(s)).containsEntry("buttons", 18).containsEntry("arrows", 2); // next only
        assertThat(s.getInventory().getSize()).isEqualTo(27);
    }

    @Test
    void thePageArrowsTurnPagesAndTheLastPageHasOneButton() {
        insert(Material.IRON_INGOT);
        GuiSession s = open();
        assertThat(click(PlanLayout.RIGHT_SLOT).isCancelled()).isTrue();
        scheduler.runDelayed();
        assertThat(AnvilPlanScreens.page(s)).isEqualTo(1);
        assertThat(s.getInventory().getItem(0).getType()).isEqualTo(RESULTS[18]);
        assertThat(s.getInventory().getItem(1)).as("only one plan on page 2").isNull();
        assertThat(s.gui().frames(s)).containsEntry("buttons", 1).containsEntry("arrows", 1); // previous only
        click(PlanLayout.RIGHT_SLOT);
        scheduler.runDelayed();
        assertThat(AnvilPlanScreens.page(s)).as("no page after the last").isEqualTo(1);
        click(PlanLayout.LEFT_SLOT);
        scheduler.runDelayed();
        assertThat(AnvilPlanScreens.page(s)).isZero();
        click(PlanLayout.LEFT_SLOT);
        scheduler.runDelayed();
        assertThat(AnvilPlanScreens.page(s)).isZero();
    }

    @Test
    void choosingAPlanSeedsTheTargetAndRemembersItAndClosesTheScreen() {
        insert(Material.IRON_INGOT);
        GuiSession shown = open();
        assertThat(click(3).isCancelled()).as("a plan button never moves an item").isTrue();
        scheduler.runDelayed();
        ForgingPdc.Forging f = ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN));
        assertThat(f).isNotNull();
        assertThat(f.recipe()).isEqualTo("tfc:anvil/iron_3");
        AnvilRecipe chosen = recipes.byId("tfc:anvil/iron_3").orElseThrow();
        assertThat(f.target()).isEqualTo(chosen.computeTarget(world.getSeed())).isBetween(40, 113);
        assertThat(f.work()).isZero();
        assertThat(f.worked()).isFalse();
        assertThat(record().state().lastRecipe()).isEqualTo("tfc:anvil/iron_3");
        assertThat(chosenAt).containsExactly(anvil);
        assertThat(shown.closed()).as("the screen closed").isTrue();
    }

    @Test
    void anItemWithExactlyOnePlanGetsItWhenPlacedAndAnEmptyCellDoesNothing() {
        insert(Material.GOLD_INGOT);
        ForgingPdc.Forging f = ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN));
        assertThat(f).as("TFC setAndUpdateSlots: a single candidate is selected").isNotNull();
        assertThat(f.recipe()).isEqualTo("tfc:anvil/gold_only");
        assertThat(f.target()).isEqualTo(recipes.byId("tfc:anvil/gold_only").orElseThrow().computeTarget(world.getSeed()));
        GuiSession s = open();
        assertThat(s.getInventory().getItem(1)).isNull();
        assertThat(click(1).isCancelled()).as("an empty cell is decoration").isTrue();
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN)).recipe()).isEqualTo("tfc:anvil/gold_only");
    }

    @Test
    void theRememberedPlanIsSelectedForTheNextItemWithSeveralPlans() {
        insert(Material.IRON_INGOT);
        open();
        click(5);
        scheduler.runDelayed();
        // take the ingot back out: its plan is not worked, so it is cleared (TFC clearRecipeIfNotWorked)
        player.setSneaking(true);
        player.getInventory().setItem(EquipmentSlot.HAND, null);
        assertThat(devices.use(anvil, player, EquipmentSlot.HAND, null)).isTrue();
        player.setSneaking(false);
        ItemStack back = player.getInventory().getItem(EquipmentSlot.HAND);
        assertThat(back.getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(ForgingPdc.read(back)).as("an unworked plan leaves with the item cleared").isNull();
        // a new ingot: the anvil's last recipe is picked among its candidates
        insert(Material.IRON_INGOT);
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN)).recipe()).isEqualTo("tfc:anvil/iron_5");
    }

    @Test
    void aWorkedPlanStaysOnTheItemWhenItIsTaken() {
        insert(Material.IRON_INGOT);
        open();
        click(0);
        scheduler.runDelayed();
        // work the item once (the forging screen of PAR-METAL-05 does this)
        @SuppressWarnings("unchecked")
        DeviceKind<AnvilState> kind = (DeviceKind<AnvilState>) devices.kinds().find(COPPER).orElseThrow();
        devices.edit(anvil, kind, (r, t) -> {
            ItemStack main = stacks.decode(r.slot(AnvilSlots.INPUT_MAIN));
            ForgingPdc.write(main, ForgingPdc.read(main).withStep(ForgeStep.PUNCH, 2));
            return r.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(main));
        });
        player.setSneaking(true);
        player.getInventory().setItem(EquipmentSlot.HAND, null);
        devices.use(anvil, player, EquipmentSlot.HAND, null);
        ForgingPdc.Forging f = ForgingPdc.read(player.getInventory().getItem(EquipmentSlot.HAND));
        assertThat(f).isNotNull();
        assertThat(f.worked()).isTrue();
        assertThat(f.work()).isEqualTo(2);
        assertThat(f.steps().steps()).containsExactly(ForgeStep.PUNCH);
    }

    @Test
    void choosingAnotherPlanKeepsTheWorkAndTheSteps() {
        ForgingPdc.Forging worked = new ForgingPdc.Forging("tfc:anvil/iron_0", 60, 9,
            new dev.tfp.core.forge.ForgeSteps(List.of(ForgeStep.PUNCH, ForgeStep.BEND), 2));
        assertThat(worked.withPlan("tfc:anvil/iron_1", 70)).isEqualTo(new ForgingPdc.Forging("tfc:anvil/iron_1", 70, 9, worked.steps()));
    }

    @Test
    void anItemWithNoPlanOpensNothing() {
        insert(Material.DIAMOND);
        assertThat(screens.openNow(player, anvil)).isEmpty();
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN))).isNull();
    }

    @Test
    void anEmptyAnvilOpensNothingAndAStaleInputIsNotChosen() {
        assertThat(screens.openNow(player, anvil)).isEmpty();
        insert(Material.IRON_INGOT);
        GuiSession s = open();
        // the input is replaced after the screen opened: pressing a plan for the old item chooses nothing
        player.setSneaking(true);
        player.getInventory().setItem(EquipmentSlot.HAND, null);
        devices.use(anvil, player, EquipmentSlot.HAND, null);
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND, 1));
        devices.use(anvil, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND));
        player.setSneaking(false);
        assertThat(screens.choose(anvil, 1, "tfc:anvil/iron_0", 50)).isEmpty();
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN))).isNull();
        assertThat(s.closed()).isFalse();
    }

    @Test
    void thePlainClickOpensTheScreenOneTickLater() {
        insert(Material.IRON_INGOT);
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.STICK, 1));
        assertThat(devices.use(anvil, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND)))
            .as("the click is consumed").isTrue();
        scheduler.runDelayed();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(GuiSession.class);
    }

    @Test
    void forgingPdcRoundTripsAndRejectsGarbage() {
        ForgingPdc.Forging f = new ForgingPdc.Forging("tfc:anvil/metal/pickaxe_head/copper", 77, -3,
            new dev.tfp.core.forge.ForgeSteps(List.of(ForgeStep.HIT_LIGHT, ForgeStep.DRAW, ForgeStep.UPSET), 5));
        ItemStack stack = new ItemStack(Material.IRON_INGOT);
        ForgingPdc.write(stack, f);
        assertThat(ForgingPdc.read(stack)).isEqualTo(f);
        assertThat(ForgingPdc.clearIfNotWorked(stack)).as("worked").isFalse();
        ForgingPdc.write(stack, new ForgingPdc.Forging("tfc:anvil/x", 50, 0, dev.tfp.core.forge.ForgeSteps.EMPTY));
        assertThat(ForgingPdc.clearIfNotWorked(stack)).isTrue();
        assertThat(ForgingPdc.read(stack)).isNull();
        assertThat(ForgingPdc.parse("nonsense")).isNull();
        assertThat(ForgingPdc.parse("a;1;2;3;NOT_A_STEP")).isNull();
    }
}
