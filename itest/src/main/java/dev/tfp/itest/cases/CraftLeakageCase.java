/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AdvancedShapedRecipe.java @ c3b4d9536 (TFC's recipes replace the
 *       vanilla ones: a vanilla recipe must not match items that are only vanilla by their base material)
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * {@code craft.leakage} (PAR-PROC-28): our CraftEngine items must not craft vanilla recipes through their base material.
 * For every vanilla crafting recipe of the running server whose ingredients are all material choices, a crafter block
 * is filled with a stand-in for our item per ingredient: the stack of a CraftEngine item that is not TFC lumber (a loose
 * rock) turned into the choice's first material with {@link ItemStack#withType} (it keeps the CraftEngine id, so
 * {@code CustomItems#idOf} still names it; the server, which sees only the base material, matches the recipe). A real
 * {@link CrafterCraftEvent} is fired through the plugin manager: the event must come back cancelled
 * ({@code CraftListener#onCrafter}) for every recipe, the kept ones included, because a rock is not in
 * {@code #minecraft:planks}. For the vanilla recipes TFC keeps that accept TFC planks through the tag ({@link #PLANKS_ONLY}: two
 * TFC planks make sticks) the same grid made of {@code tfp:wood/planks/oak} stand-ins must NOT be cancelled. The crafter path
 * is the one that fires a real event for a server match without a player.
 *
 * <p>The case reports how many recipes it checked.
 */
public final class CraftLeakageCase implements SelfTestCase {
    /** Chunks east of spawn: away from the other CE block cases (40 to 76). */
    static final int CHUNK_OFFSET = 80;
    private static final Duration STEP = Duration.ofSeconds(20);
    /** The CraftEngine item whose stack is turned into every base material: a loose rock, in no vanilla item tag. */
    static final String LEAK_ITEM = "tfp:rock/loose/granite";
    /** TFC planks, in #minecraft:planks through TFC's tag data. */
    static final String PLANKS_ITEM = "tfp:wood/planks/oak";
    /**
     * The vanilla recipes TFC keeps (KeptVanillaRecipes) that are made of planks alone: a grid of TFC planks stand-ins
     * matches them through #minecraft:planks (the other kept recipes need a second vanilla item).
     */
    static final Set<String> PLANKS_ONLY = Set.of("minecraft:stick");

    @Override
    public String name() {
        return "craft.leakage: no vanilla crafting recipe crafts our items through their base material (PAR-PROC-28)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60 + 40 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        return OnThread.script("craft-leakage", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            return check(tfp, tfp.integrations().customItems());
        });
    }

    private static String check(TfpPlugin tfp, CustomItems items) throws Exception {
        ItemStack rock = items.create(LEAK_ITEM, 1)
                .orElseThrow(() -> new AssertionError(LEAK_ITEM + " is not a CraftEngine item"));
        ItemStack planks = items.create(PLANKS_ITEM, 1)
                .orElseThrow(() -> new AssertionError(PLANKS_ITEM + " is not a CraftEngine item"));
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        int cx = (world.getSpawnLocation().getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = world.getSpawnLocation().getBlockZ() >> 4;
        int x = (cx << 4) + 8;
        int z = (cz << 4) + 8;
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        try {
            return OnThread.region(s, world, cx, cz, Duration.ofSeconds(120), () -> {
                int y = world.getHighestBlockYAt(x, z) + 2;
                Block crafter = world.getBlockAt(x, y, z);
                crafter.setType(Material.CRAFTER, false);
                int checked = 0;
                int keptChecked = 0;
                int vanillaRecipes = 0;
                Map<String, Integer> skippedByChoice = new java.util.TreeMap<>();
                List<String> leaks = new ArrayList<>();
                List<String> refused = new ArrayList<>();
                Iterator<Recipe> it = Bukkit.recipeIterator();
                while (it.hasNext()) {
                    Recipe r = it.next();
                    if (!(r instanceof CraftingRecipe recipe) || !recipe.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) {
                        continue;
                    }
                    vanillaRecipes++;
                    ItemStack[] grid = fill(recipe, rock);
                    if (grid == null) {
                        skippedByChoice.merge(recipe instanceof ShapedRecipe ? "shaped" : recipe instanceof ShapelessRecipe
                                ? "shapeless" : recipe.getClass().getSimpleName(), 1, Integer::sum);
                        continue;
                    }
                    String id = recipe.getKey().toString();
                    if (!fire(crafter, recipe, grid)) {
                        leaks.add(id);
                    }
                    checked++;
                    if (PLANKS_ONLY.contains(id)) {
                        ItemStack[] lumber = fill(recipe, planks);
                        if (lumber != null) {
                            keptChecked++;
                            if (fire(crafter, recipe, lumber)) {
                                refused.add(id);
                            }
                        }
                    }
                }
                crafter.setType(Material.AIR, false);
                if (checked == 0) {
                    throw new AssertionError("no vanilla crafting recipe could be filled");
                }
                if (!leaks.isEmpty()) {
                    throw new AssertionError(leaks.size() + " of " + checked + " vanilla recipes crafted our items: "
                            + leaks.subList(0, Math.min(10, leaks.size())));
                }
                if (!refused.isEmpty()) {
                    throw new AssertionError("TFC planks were refused by the kept recipe(s) " + refused);
                }
                return checked + " of " + vanillaRecipes + " vanilla crafting recipes of the server filled with stand-ins of "
                        + LEAK_ITEM + " (not fillable: " + skippedByChoice + "): all cancelled; " + keptChecked
                        + " kept tag recipes accept " + PLANKS_ITEM;
            });
        } finally {
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    /** Fires a real crafter craft of {@code recipe} over {@code grid}; true when the listener cancelled it. */
    private static boolean fire(Block crafter, CraftingRecipe recipe, ItemStack[] grid) {
        Container c = (Container) crafter.getState(false);
        Inventory inv = c.getInventory();
        inv.clear();
        inv.setContents(grid);
        CrafterCraftEvent event = new CrafterCraftEvent(crafter, recipe, recipe.getResult().clone());
        Bukkit.getPluginManager().callEvent(event);
        inv.clear();
        return event.isCancelled();
    }

    /** The 9 crafter slots of {@code recipe} with a stand-in of {@code proto} per ingredient, or null when none can be made. */
    private static ItemStack[] fill(CraftingRecipe recipe, ItemStack proto) {
        ItemStack[] grid = new ItemStack[9];
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (int row = 0; row < shape.length; row++) {
                for (int col = 0; col < shape[row].length(); col++) {
                    RecipeChoice choice = choices.get(shape[row].charAt(col));
                    if (choice == null) {
                        continue;
                    }
                    ItemStack ours = standIn(choice, proto);
                    if (ours == null) {
                        return null;
                    }
                    grid[row * 3 + col] = ours;
                }
            }
            return grid;
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> choices = shapeless.getChoiceList();
            if (choices.size() > 9) {
                return null;
            }
            for (int i = 0; i < choices.size(); i++) {
                ItemStack ours = standIn(choices.get(i), proto);
                if (ours == null) {
                    return null;
                }
                grid[i] = ours;
            }
            return grid;
        }
        return null;
    }

    private static ItemStack standIn(RecipeChoice choice, ItemStack proto) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            for (Material m : mc.getChoices()) {
                if (m.isItem() && !m.isAir()) {
                    return proto.withType(m);
                }
            }
        } else if (choice instanceof RecipeChoice.ExactChoice exact) {
            // an ingredient tag TFC's data extends with our own items reaches the server as exact stacks: their base
            // material is what the server's match sees
            for (ItemStack candidate : exact.getChoices()) {
                if (candidate != null && candidate.getType().isItem() && !candidate.getType().isAir()) {
                    return proto.withType(candidate.getType());
                }
            }
        }
        return null;
    }
}
