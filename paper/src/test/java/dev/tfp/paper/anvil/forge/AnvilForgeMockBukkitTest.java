/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil.forge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilRecipes;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.forge.AnvilScreenLayout;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgeSteps;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.anvil.AnvilClicks;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.anvil.plan.AnvilPlanScreens;
import dev.tfp.paper.anvil.plan.ForgingPdc;
import dev.tfp.paper.anvil.plan.PlanBook;
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
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tool.ForgingBonusPdc;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * PAR-METAL-05 on MockBukkit: the forging screen over a copper anvil with fakes of the CraftEngine adapter, the recipe
 * index, the heat and the hammer damage. Stand-ins: the iron ingot is the work, the golden pickaxe is the hammer, the
 * iron pickaxe the result of the one plan {@code HIT_LAST}.
 */
class AnvilForgeMockBukkitTest extends MockServerTest {
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
    private static final String PLAN = "tfc:anvil/forge_test";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(COPPER);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicLong now = new AtomicLong(100_000);
    private final FakeCustomItems items = new FakeCustomItems();
    private final AtomicInteger damaged = new AtomicInteger();
    private final AtomicInteger shown = new AtomicInteger();
    private final AtomicInteger kept = new AtomicInteger();
    private volatile boolean hot = true;
    private TickScheduler scheduler;
    private DeviceService devices;
    private AnvilPlanScreens plans;
    private AnvilForgeScreens screens;
    private World world;
    private Block anvil;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("anvilforge");
        scheduler = new TickScheduler();
        AnvilRecipes recipes = new AnvilRecipes(List.of(new AnvilRecipe(PLAN, new Ingredient.Item("minecraft:iron_ingot"), 1,
            List.of(ForgeRule.HIT_LAST), true, new CraftOutput("minecraft:iron_pickaxe", 1))));
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, items, Logger.getLogger("test"));
        GuiService gui = new GuiService(server, scheduler, now::get, true);
        PlanBook book = new PlanBook(recipes);
        plans = new AnvilPlanScreens(gui, scheduler, devices, book, () -> CTX, items, blocks, p -> "Anvil");
        AnvilEffects effects = new AnvilEffects() {
            @Override
            public void show(Block block, View view) {
                shown.incrementAndGet();
            }

            @Override
            public void hide(Block block) {
            }

            @Override
            public void state(Block block, BlockStateRef wanted) {
            }
        };
        ForgeHeat heat = new ForgeHeat() {
            @Override
            public boolean canWork(ItemStack stack) {
                return hot;
            }

            @Override
            public void keep(ItemStack from, ItemStack to) {
                kept.incrementAndGet();
            }
        };
        AnvilForger.Env env = new AnvilForger.Env(stacks, book::recipes, () -> CTX, s -> s.getType() == Material.GOLDEN_PICKAXE, heat, items,
            () -> ForgingConfig.DEFAULTS, () -> 0, (p, s) -> {
                damaged.incrementAndGet();
                return s;
            });
        screens = new AnvilForgeScreens(gui, scheduler, devices, env, plans, blocks, effects, new Messages(getClass().getClassLoader()),
            p -> "Anvil", s -> s.getType() == Material.GUNPOWDER);
        AnvilDevice.Services services = new AnvilDevice.Services(s -> s.getType() == Material.GOLDEN_PICKAXE,
            s -> s.getType() == Material.GUNPOWDER, effects, new AnvilClicks(), plans);
        devices.register(AnvilDevice.kind(AnvilDevice.METAL_ID, services));
        devices.register(AnvilDevice.kind(AnvilDevice.ROCK_ID, services));
        plans.onPlainClick(screens::openLater);
        plans.onChosen(screens::openLater);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new AnvilForgeListener(screens), plugin);
        anvil = world.getBlockAt(0, 64, 0);
        blocks.place(anvil, BlockStateRef.of(COPPER).with("facing", "east"), true);
        store.data.put(FakeCustomBlocks.at(anvil), new DataMap());
        player = server.addPlayer("Bot");
    }

    @SuppressWarnings("unchecked")
    private DeviceKind<AnvilState> kind() {
        return (DeviceKind<AnvilState>) devices.kinds().find(COPPER).orElseThrow();
    }

    private DeviceRecord<AnvilState> record() {
        return kind().codec().decode(store.read(anvil).orElseThrow());
    }

    private ItemStack slot(int slot) {
        return stacks.decode(record().slot(slot));
    }

    private void set(int slot, ItemStack stack) {
        devices.edit(anvil, kind(), (r, t) -> r.withSlot(slot, stacks.encode(stack)));
    }

    /** An iron ingot with the plan stored (target 13, nothing worked) in the main input. */
    private void ingotWithPlan() {
        ItemStack ingot = new ItemStack(Material.IRON_INGOT);
        ForgingPdc.write(ingot, new ForgingPdc.Forging(PLAN, 13, 0, ForgeSteps.EMPTY));
        set(AnvilSlots.INPUT_MAIN, ingot);
    }

    private GuiSession open() {
        Optional<GuiSession> s = screens.open(player, anvil);
        assertThat(s).isPresent();
        return s.get();
    }

    private void press(GuiSession s, ForgeStep step) {
        screens.press(s, step);
    }

    private InventoryClickEvent click(int raw, InventoryAction action, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    void theScreenShowsTheFourSlotsAndTheEightStepButtons() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        assertThat(s.getInventory().getSize()).isEqualTo(45);
        assertThat(s.getInventory().getItem(AnvilScreenLayout.MAIN).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(s.getInventory().getItem(AnvilScreenLayout.HAMMER).getType()).isEqualTo(Material.GOLDEN_PICKAXE);
        assertThat(s.getInventory().getItem(AnvilScreenLayout.SECOND)).isNull();
        for (ForgeStep step : ForgeStep.VALUES) {
            ItemStack icon = s.getInventory().getItem(AnvilScreenLayout.stepSlot(step));
            assertThat(GuiButtons.isButton(icon)).as(step.name()).isTrue();
        }
        assertThat(GuiButtons.isButton(s.getInventory().getItem(AnvilScreenLayout.WELD))).isTrue();
        // the plan button shows the plan's result with the label
        ItemStack planButton = s.getInventory().getItem(AnvilScreenLayout.PLAN);
        assertThat(planButton.getType()).isEqualTo(Material.IRON_PICKAXE);
        assertThat(GuiButtons.isButton(planButton)).isTrue();
        assertThat(s.gui().frames(s)).containsEntry(AnvilScreenLayout.KEY_PROGRESS, 0).containsEntry(AnvilScreenLayout.KEY_TARGET, 13)
            .containsEntry(AnvilScreenLayout.KEY_PLAN, AnvilScreenLayout.PLAN_RECIPE);
        assertThat(screens.openCount()).isEqualTo(1);
        assertThat(screens.open(server.addPlayer("Other"), anvil)).as("one viewer per anvil").isEmpty();
    }

    @Test
    void aStepButtonMovesTheOffsetDamagesTheHammerAndShowsTheMarker() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        InventoryClickEvent e = click(AnvilScreenLayout.stepSlot(ForgeStep.SHRINK), InventoryAction.PICKUP_ALL, ClickType.LEFT);
        assertThat(e.isCancelled()).isTrue();
        scheduler.runDelayed();
        ForgingPdc.Forging f = ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN));
        assertThat(f.work()).isEqualTo(16);
        assertThat(f.steps().total()).isEqualTo(1);
        assertThat(f.steps().steps()).containsExactly(ForgeStep.SHRINK);
        assertThat(damaged.get()).isEqualTo(1);
        assertThat(s.gui().frames(s)).containsEntry(AnvilScreenLayout.KEY_PROGRESS, 16)
            .containsEntry(AnvilScreenLayout.stepKey(0), AnvilScreenLayout.stepFrame(ForgeStep.SHRINK))
            .containsEntry(AnvilScreenLayout.ruleKey(0), AnvilScreenLayout.ruleFrame(ForgeRule.HIT_LAST, false));
        assertThat(shown.get()).as("the displays are redrawn").isPositive();
    }

    @Test
    void withoutAHammerNothingHappensAndTheMessageIsSent() {
        ingotWithPlan();
        GuiSession s = open();
        press(s, ForgeStep.PUNCH);
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN)).work()).isZero();
        assertThat(damaged.get()).isZero();
        player.assertSaid("A hammer is required to work in the anvil!");
    }

    @Test
    void aHammerInTheMainHandWorksWhenTheSlotHasNone() {
        ingotWithPlan();
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        press(s, ForgeStep.PUNCH);
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN)).work()).isEqualTo(2);
        assertThat(damaged.get()).isEqualTo(1);
    }

    @Test
    void theFirstStepCannotOverworkAnUntouchedItem() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        press(s, ForgeStep.HIT_LIGHT);
        ForgingPdc.Forging f = ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN));
        assertThat(f.work()).isZero();
        assertThat(f.steps().total()).isZero();
        assertThat(damaged.get()).as("a refused step does not cost the hammer").isZero();
    }

    @Test
    void aColdItemCannotBeWorked() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        hot = false;
        GuiSession s = open();
        press(s, ForgeStep.PUNCH);
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN)).work()).isZero();
        player.assertSaid("Not hot enough to work!");
    }

    @Test
    void overworkingDestroysTheItem() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        press(s, ForgeStep.PUNCH); // 2
        press(s, ForgeStep.DRAW); // -13
        assertThat(StackCodec.isEmpty(slot(AnvilSlots.INPUT_MAIN))).isTrue();
        assertThat(s.getInventory().getItem(AnvilScreenLayout.MAIN)).isNull();
        assertThat(damaged.get()).isEqualTo(2);
    }

    @Test
    void finishingGivesTheResultWithThePerfectBonusAndTheInputsHeat() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        press(s, ForgeStep.SHRINK); // 16
        press(s, ForgeStep.HIT_LIGHT); // 13 = the target, the last step a hit
        ItemStack out = slot(AnvilSlots.INPUT_MAIN);
        assertThat(out.getType()).isEqualTo(Material.IRON_PICKAXE);
        assertThat(ForgingBonusPdc.read(out)).isEqualTo(ForgingQuality.PERFECT); // 2 steps / 2 optimal = 1 < 1.5
        assertThat(ForgingBonusPdc.author(out)).isEqualTo(player.getName()); // PAR-METAL-21: the smith is stored
        assertThat(ForgingPdc.read(out)).isNull();
        assertThat(kept.get()).isEqualTo(1);
        assertThat(s.getInventory().getItem(AnvilScreenLayout.MAIN).getType()).isEqualTo(Material.IRON_PICKAXE);
    }

    @Test
    void aSlowerFinishGetsALowerBonus() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        for (int i = 0; i < 8; i++) {
            press(s, ForgeStep.PUNCH); // 16, the last step is no hit: the rule is unmet
        }
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).isEqualTo(Material.IRON_INGOT);
        press(s, ForgeStep.HIT_LIGHT); // 13 with a hit last: finished after 9 steps, optimal 2: ratio 4.5, below the well threshold 5
        ItemStack out = slot(AnvilSlots.INPUT_MAIN);
        assertThat(out.getType()).isEqualTo(Material.IRON_PICKAXE);
        assertThat(ForgingBonusPdc.read(out)).isEqualTo(ForgingQuality.WELL);
    }

    @Test
    void remoteWorkMovesTheCursorByTheMovementAndNeedsNoHammer() {
        ingotWithPlan();
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).isTrue();
        ForgingPdc.Forging f = ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN));
        assertThat(f.work()).isEqualTo(12);
        assertThat(f.steps().steps()).containsExactly(ForgeStep.HIT_LIGHT);
        assertThat(damaged.get()).as("the trip hammer damages its own").isZero();
    }

    @Test
    void remoteWorkWithForceCompletionStopsAtTheTargetAndCompletes() {
        ingotWithPlan();
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).isTrue();
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).isTrue(); // 12 + 1 = 13
        ItemStack out = slot(AnvilSlots.INPUT_MAIN);
        assertThat(out.getType()).isEqualTo(Material.IRON_PICKAXE);
        assertThat(ForgingBonusPdc.read(out)).as("no forging bonus").isEqualTo(ForgingQuality.NONE);
        assertThat(kept.get()).isEqualTo(1);
    }

    @Test
    void remoteWorkTurnsAndClampsTheMovementWhenTheCursorIsPastTheTarget() {
        ItemStack ingot = new ItemStack(Material.IRON_INGOT);
        ForgingPdc.write(ingot, new ForgingPdc.Forging(PLAN, 13, 20, ForgeSteps.EMPTY.withStep(ForgeStep.UPSET)));
        set(AnvilSlots.INPUT_MAIN, ingot);
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).isTrue();
        assertThat(slot(AnvilSlots.INPUT_MAIN).getType()).as("turned to -12, clamped to -7: on the target, completed").isEqualTo(Material.IRON_PICKAXE);
    }

    @Test
    void remoteWorkRefusesColdNoPlanAndTheFirstOverwork() {
        ingotWithPlan();
        hot = false;
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).isFalse();
        hot = true;
        assertThat(screens.workRemotely(anvil, ForgeStep.DRAW, -15, false)).as("first hit cannot overwork").isFalse();
        assertThat(ForgingPdc.read(slot(AnvilSlots.INPUT_MAIN)).work()).isZero();
        set(AnvilSlots.INPUT_MAIN, new ItemStack(Material.IRON_INGOT));
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).as("no forging on the input").isFalse();
        set(AnvilSlots.INPUT_MAIN, ItemStack.empty());
        assertThat(screens.workRemotely(anvil, ForgeStep.HIT_LIGHT, 12, true)).isFalse();
    }

    @Test
    void remoteWorkPastTheLimitDestroysTheInput() {
        ingotWithPlan();
        assertThat(screens.workRemotely(anvil, ForgeStep.UPSET, 200, false)).isTrue();
        assertThat(StackCodec.isEmpty(slot(AnvilSlots.INPUT_MAIN))).isTrue();
    }

    @Test
    void theFluxSlotTakesOnlyFluxAndTheHammerSlotOnlyAHammer() {
        GuiSession s = open();
        var catalyst = s.gui().slot(AnvilScreenLayout.CATALYST);
        var hammer = s.gui().slot(AnvilScreenLayout.HAMMER);
        assertThat(catalyst.accepts().accepts(s, AnvilScreenLayout.CATALYST, new ItemStack(Material.GUNPOWDER))).isTrue();
        assertThat(catalyst.accepts().accepts(s, AnvilScreenLayout.CATALYST, new ItemStack(Material.STICK))).isFalse();
        assertThat(hammer.accepts().accepts(s, AnvilScreenLayout.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE))).isTrue();
        assertThat(hammer.accepts().accepts(s, AnvilScreenLayout.HAMMER, new ItemStack(Material.GUNPOWDER))).isFalse();
        assertThat(s.gui().slot(AnvilScreenLayout.stepSlot(ForgeStep.DRAW))).isNull();
    }

    @Test
    void aStackPutInTheScreenIsCommittedOnTheNextTickAndGetsADefaultPlan() {
        GuiSession s = open();
        s.getInventory().setItem(AnvilScreenLayout.MAIN, new ItemStack(Material.IRON_INGOT));
        screens.touched(s);
        scheduler.runDelayed();
        ItemStack main = slot(AnvilSlots.INPUT_MAIN);
        assertThat(main.getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(ForgingPdc.read(main)).as("the only plan is chosen").isNotNull();
        assertThat(ForgingPdc.read(main).recipe()).isEqualTo(PLAN);
        assertThat(s.gui().frames(s)).containsEntry(AnvilScreenLayout.KEY_PLAN, AnvilScreenLayout.PLAN_RECIPE);
    }

    @Test
    void closingTheScreenCommitsTheCopyAndFreesTheAnvil() {
        GuiSession s = open();
        s.getInventory().setItem(AnvilScreenLayout.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        player.closeInventory();
        assertThat(slot(AnvilSlots.HAMMER).getType()).isEqualTo(Material.GOLDEN_PICKAXE);
        assertThat(screens.openCount()).isZero();
        assertThat(open()).isNotNull();
    }

    @Test
    void anOverflowOnAOneItemSlotGoesBackToThePlayer() {
        GuiSession s = open();
        s.getInventory().setItem(AnvilScreenLayout.SECOND, new ItemStack(Material.IRON_INGOT, 5));
        screens.touched(s);
        scheduler.runDelayed();
        assertThat(slot(AnvilSlots.INPUT_SECOND).getAmount()).isEqualTo(1);
        assertThat(player.getInventory().contains(Material.IRON_INGOT, 4)).isTrue();
    }

    @Test
    void takingAnUnworkedItemOutClearsItsPlan() {
        ingotWithPlan();
        GuiSession s = open();
        InventoryClickEvent e = click(AnvilScreenLayout.MAIN, InventoryAction.PICKUP_ALL, ClickType.LEFT);
        assertThat(e.isCancelled()).isFalse();
        assertThat(ForgingPdc.read(e.getCurrentItem())).as("an unworked plan does not leave with the item").isNull();
    }

    @Test
    void aWorkedItemKeepsItsPlanWhenTakenOut() {
        ingotWithPlan();
        set(AnvilSlots.HAMMER, new ItemStack(Material.GOLDEN_PICKAXE));
        GuiSession s = open();
        press(s, ForgeStep.PUNCH);
        InventoryClickEvent e = click(AnvilScreenLayout.MAIN, InventoryAction.PICKUP_ALL, ClickType.LEFT);
        assertThat(ForgingPdc.read(e.getCurrentItem())).isNotNull();
    }

    @Test
    void theWeldButtonCallsTheWeldingPartAndReloads() {
        ingotWithPlan();
        GuiSession s = open();
        List<Block> welded = new ArrayList<>();
        screens.welding(new AnvilForgeScreens.Welding() {
            @Override
            public boolean canWeld(Block block, ItemStack main, ItemStack second) {
                return true;
            }

            @Override
            public void weld(org.bukkit.entity.Player viewer, Block block) {
                welded.add(block);
                set(AnvilSlots.INPUT_SECOND, new ItemStack(Material.GOLD_INGOT));
            }
        });
        assertThat(s.gui().frames(s)).containsEntry(AnvilScreenLayout.KEY_WELD, 1);
        click(AnvilScreenLayout.WELD, InventoryAction.PICKUP_ALL, ClickType.LEFT);
        scheduler.runDelayed();
        assertThat(welded).containsExactly(anvil);
        assertThat(s.getInventory().getItem(AnvilScreenLayout.SECOND).getType()).isEqualTo(Material.GOLD_INGOT);
    }

    @Test
    void thePlainClickOpensTheForgingScreenAndAChosenPlanReturnsToIt() {
        ingotWithPlan();
        assertThat(plans.open(player, anvil)).isTrue();
        scheduler.runDelayed();
        assertThat(AnvilForgeScreens.isForge(GuiService.session(player.getOpenInventory().getTopInventory()))).isTrue();
        player.closeInventory();
        plans.openNow(player, anvil); // the plan button
        GuiSession plan = GuiService.session(player.getOpenInventory().getTopInventory());
        assertThat(plan).isNotNull();
        assertThat(AnvilForgeScreens.isForge(plan)).isFalse();
        assertThat(plan.getInventory().getSize()).as("the 3-row plan screen").isEqualTo(27);
    }
}
