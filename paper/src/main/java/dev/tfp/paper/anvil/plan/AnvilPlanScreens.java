/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/AnvilPlanContainer.java @ c3b4d9536 (onButtonPress),
 * src/main/java/net/dries007/tfc/client/screen/AnvilPlanScreen.java @ c3b4d9536 (the buttons and the pages),
 * src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (chooseRecipe, setAndUpdateSlots,
 * ejectInventory)
 */
package dev.tfp.paper.anvil.plan;

import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilRecipePage;
import dev.tfp.core.anvil.AnvilRecipes;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.AnvilTiers;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-METAL-04: the anvil's plan selection screen (TFC {@code AnvilPlanContainer} and {@code AnvilPlanScreen}) and the plan
 * rules of the anvil's main input, as the anvil's {@link AnvilDevice.Plans}.
 *
 * <p>The screen lists the recipes the main input can start on this anvil's tier ({@link AnvilRecipes#candidates}), 18 to a page
 * ({@link AnvilRecipePage}), each as a button showing the recipe's result item with its name as the tooltip, and two arrows for
 * the pages. Pressing a button selects the plan: the main input gets the forging state {@link ForgingPdc} with the recipe and the
 * work target seeded from the world ({@link AnvilRecipe#computeTarget}), the anvil remembers the recipe as its last one, and the
 * screen closes (TFC then reopens the anvil's forging screen; PAR-METAL-05 reopens it through {@link #onChosen}).
 *
 * <p>Threads: {@link #open} and every screen callback run on the viewer's thread; the record work needs the block's region.
 * When the viewer's thread does not own the block the screen closes instead of working on it. The recipe book is an immutable
 * snapshot.
 */
public final class AnvilPlanScreens implements AnvilDevice.Plans {
    static final String STATE_PLANS = "plan.plans";
    static final String STATE_PAGE = "plan.page";
    static final String STATE_BLOCK = "plan.block";
    static final NamespacedKey PLAN_KEY = new NamespacedKey("tfp", "anvil_plan");

    /** What a screen was opened for: the plans and where they apply. */
    record Plans(Block block, int tier, ItemStack input, List<AnvilRecipe> recipes) {
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final DeviceService devices;
    private final PlanBook book;
    private final Recipes recipes;
    private final CustomItems items;
    private final CustomBlocks blocks;
    private final Function<Player, String> title;
    private volatile AnvilDevice.Chosen chosen = (player, block) -> { };

    public AnvilPlanScreens(GuiService gui, Scheduler scheduler, DeviceService devices, PlanBook book, Recipes recipes,
            CustomItems items, CustomBlocks blocks, Function<Player, String> title) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.book = Objects.requireNonNull(book, "book");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.items = Objects.requireNonNull(items, "items");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.title = Objects.requireNonNull(title, "title");
    }

    /** What runs (on the viewer's thread) after a plan was chosen, in place of just closing the screen. */
    public void onChosen(AnvilDevice.Chosen chosen) {
        this.chosen = Objects.requireNonNull(chosen, "chosen");
    }

    // ---- the plan rules of the main input -----------------------------------------------------------------

    @Override
    public DeviceRecord<AnvilState> slotsChanged(Block block, int tier, DeviceRecord<AnvilState> record, StackCodec stacks) {
        ItemStack input = stacks.decode(record.slot(AnvilSlots.INPUT_MAIN));
        if (input == null || input.isEmpty()) {
            return record;
        }
        ForgingPdc.Forging forging = ForgingPdc.read(input);
        AnvilRecipes all = book.recipes();
        if (forging != null && all.byId(forging.recipe()).isPresent()) {
            return record; // TFC: a recipe the item already holds stays
        }
        List<AnvilRecipe> candidates = all.candidates(input, tier, ingredients());
        Optional<AnvilRecipe> pick = AnvilRecipes.defaultFor(candidates, record.state().lastRecipe().isEmpty() ? null
            : record.state().lastRecipe());
        if (pick.isEmpty()) {
            return record;
        }
        ItemStack next = input.clone();
        ForgingPdc.write(next, withPlan(forging, pick.get(), block));
        return record.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(next));
    }

    @Override
    public ItemStack taken(ItemStack stack) {
        ForgingPdc.clearIfNotWorked(stack);
        return stack;
    }

    private ItemIngredientContext ingredients() {
        return recipes.ingredients();
    }

    private static ForgingPdc.Forging withPlan(ForgingPdc.Forging old, AnvilRecipe recipe, Block block) {
        int target = recipe.computeTarget(block.getWorld().getSeed());
        return old == null ? new ForgingPdc.Forging(recipe.id(), target, 0, dev.tfp.core.forge.ForgeSteps.EMPTY)
            : old.withPlan(recipe.id(), target);
    }

    // ---- opening ---------------------------------------------------------------------------------------

    /** Opens the plan screen of the anvil at {@code block} for {@code viewer}, one tick later (off the click). */
    @Override
    public void open(Player viewer, Block block) {
        scheduler.entityDelayed(viewer, 1, () -> {
            if (viewer.isOnline()) {
                openNow(viewer, block);
            }
        }, () -> { });
    }

    /**
     * Opens the screen now.
     *
     * @return the session, or empty when the anvil has no main input, the input has no plan on this tier, this thread does not own
     *         the block, or the screen was refused
     */
    public Optional<GuiSession> openNow(Player viewer, Block block) {
        if (!owned(block)) {
            return Optional.empty();
        }
        Optional<DeviceKind<AnvilState>> kind = kind(block);
        int tier = blocks.stateAt(block).map(BlockStateRef::id).flatMap(id -> {
            var t = AnvilTiers.tier(id);
            return t.isPresent() ? Optional.of(t.getAsInt()) : Optional.<Integer>empty();
        }).orElse(-1);
        if (kind.isEmpty() || tier < 0) {
            return Optional.empty();
        }
        Optional<DeviceRecord<AnvilState>> record = devices.edit(block, kind.get(), (r, now) -> r);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        ItemStack input = devices.stacks().decode(record.get().slot(AnvilSlots.INPUT_MAIN));
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        List<AnvilRecipe> plans = book.recipes().candidates(input, tier, ingredients());
        GuiSession session = gui.open(viewer, screen());
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE_PLANS, new Plans(block, tier, input, plans));
        session.put(STATE_PAGE, 0);
        session.refresh();
        return Optional.of(session);
    }

    private TfpGui screen() {
        TfpGui.Builder b = TfpGui.builder(PlanLayout.CHEST, PlanLayout.LAYERS).label(title);
        for (int cell = 0; cell < AnvilRecipePage.PER_PAGE; cell++) {
            int c = cell;
            b.button(cell, s -> planIcon(s, c), (s, click) -> press(s, c));
        }
        b.button(PlanLayout.LEFT_SLOT, s -> arrowIcon(), (s, click) -> turn(s, -1));
        b.button(PlanLayout.RIGHT_SLOT, s -> arrowIcon(), (s, click) -> turn(s, 1));
        b.frames(s -> {
            Plans p = plans(s);
            int total = p == null ? 0 : p.recipes().size();
            int page = page(s);
            return PlanLayout.frames(AnvilRecipePage.pageItems(p == null ? List.<AnvilRecipe>of() : p.recipes(), page).size(),
                page, total);
        });
        return b.build();
    }

    static Plans plans(GuiSession session) {
        Object p = session.get(STATE_PLANS, null);
        return p instanceof Plans plans ? plans : null;
    }

    static int page(GuiSession session) {
        return session.get(STATE_PAGE, 0);
    }

    /** The plan on cell {@code cell} of the current page, or null. */
    static AnvilRecipe planAt(GuiSession session, int cell) {
        Plans p = plans(session);
        if (p == null) {
            return null;
        }
        List<AnvilRecipe> onPage = AnvilRecipePage.pageItems(p.recipes(), page(session));
        return cell < onPage.size() ? onPage.get(cell) : null;
    }

    // ---- buttons ---------------------------------------------------------------------------------------

    private ItemStack planIcon(GuiSession session, int cell) {
        AnvilRecipe recipe = planAt(session, cell);
        if (recipe == null) {
            return null;
        }
        ItemStack stack = result(recipe);
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(GuiButtons.MARKER, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(PLAN_KEY, PersistentDataType.STRING, recipe.id());
        });
        return stack;
    }

    /** The recipe's result as the button's item: TFC draws {@code getResultItem} with its count. */
    ItemStack result(AnvilRecipe recipe) {
        String id = recipe.output().item();
        int count = Math.min(recipe.output().count(), 64);
        Optional<ItemStack> custom = items.create(id, count);
        if (custom.isPresent()) {
            return custom.get();
        }
        Material material = id.startsWith("minecraft:") ? Material.matchMaterial(id) : null;
        if (material != null && material.isItem()) {
            return new ItemStack(material, count);
        }
        ItemStack paper = GuiButtons.icon(gui.glyphs(), id); // an unknown result id: a named paper so the plan still shows
        return paper;
    }

    private ItemStack arrowIcon() {
        ItemStack icon = GuiButtons.icon(gui.glyphs(), "");
        icon.editMeta(meta -> meta.setHideTooltip(true));
        return icon;
    }

    private void turn(GuiSession session, int by) {
        Plans p = plans(session);
        if (p == null) {
            return;
        }
        int now = page(session);
        int next = now + by;
        if (by < 0 ? !AnvilRecipePage.hasPrevious(now) : !AnvilRecipePage.hasNext(now, p.recipes().size())) {
            return;
        }
        session.put(STATE_PAGE, AnvilRecipePage.clamp(next, p.recipes().size()));
        session.refresh();
    }

    /** TFC {@code AnvilPlanContainer.onButtonPress}: choose the recipe, then back to the anvil. */
    private void press(GuiSession session, int cell) {
        AnvilRecipe recipe = planAt(session, cell);
        Plans p = plans(session);
        Player viewer = session.viewer();
        if (recipe == null || p == null) {
            return;
        }
        if (!owned(p.block())) {
            viewer.closeInventory();
            return;
        }
        boolean done = choose(p.block(), p.tier(), recipe.id(), recipe.computeTarget(p.block().getWorld().getSeed())).isPresent();
        viewer.closeInventory();
        if (done) {
            chosen.chosen(viewer, p.block());
        }
    }

    /**
     * TFC {@code chooseRecipe}: sets {@code recipeId} on the main input (if it is still there and the recipe still lists for it
     * on this tier), remembers it as the anvil's last recipe. Region thread of {@code block}.
     *
     * @return the forging state stored, empty when nothing was chosen
     */
    Optional<ForgingPdc.Forging> choose(Block block, int tier, String recipeId, int target) {
        Optional<DeviceKind<AnvilState>> kind = kind(block);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        ForgingPdc.Forging[] out = new ForgingPdc.Forging[1];
        devices.edit(block, kind.get(), (record, now) -> {
            ItemStack input = devices.stacks().decode(record.slot(AnvilSlots.INPUT_MAIN));
            Optional<AnvilRecipe> recipe = book.recipes().byId(recipeId);
            if (input == null || input.isEmpty() || recipe.isEmpty()
                || !recipe.get().matches(input, tier, ingredients())) {
                return record;
            }
            ItemStack next = input.clone();
            ForgingPdc.Forging old = ForgingPdc.read(next);
            ForgingPdc.Forging forging = old == null
                ? new ForgingPdc.Forging(recipeId, target, 0, dev.tfp.core.forge.ForgeSteps.EMPTY) : old.withPlan(recipeId, target);
            ForgingPdc.write(next, forging);
            out[0] = forging;
            return record.withSlot(AnvilSlots.INPUT_MAIN, devices.stacks().encode(next))
                .withState(record.state().withLastRecipe(recipeId));
        });
        return Optional.ofNullable(out[0]);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private boolean owned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    @SuppressWarnings("unchecked")
    private Optional<DeviceKind<AnvilState>> kind(Block block) {
        return blocks.stateAt(block).map(BlockStateRef::id).flatMap(devices.kinds()::find).map(k -> (DeviceKind<AnvilState>) k);
    }
}
