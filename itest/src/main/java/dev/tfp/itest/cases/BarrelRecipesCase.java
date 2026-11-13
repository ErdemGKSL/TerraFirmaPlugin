/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.tag.TagType;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.barrel.BarrelDevice;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.barrel.BarrelRecipeBook;
import dev.tfp.paper.barrel.BarrelRecipeView;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code barrel.recipes} (PAR-PROC-32): on the running server all 331 TFC barrel recipes (309 sealed, 20 instant, 2 instant
 * fluid) decode through {@link BarrelRecipeBook} from the recipe runtime the barrel module uses, TFC's brining recipe has
 * its 3600-tick duration and 125 mB of brine, and every wood's {@code tfp:wood/barrel/<wood>} block and item are known to
 * CraftEngine (the MockBukkit {@code BarrelRecipesContentDataMockBukkitTest} checks the same recipes on the datagen output).
 */
public final class BarrelRecipesCase implements SelfTestCase {
    static final Duration STEP = Duration.ofSeconds(20);
    /** TFC's woods (datagen's {@code HitboxDemands.BARREL_WOODS}). */
    static final List<String> WOODS = List.of("acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir",
        "hickory", "kapok", "mangrove", "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore",
        "white_cedar", "willow");

    @Override
    public String name() {
        return "barrel.recipes: the 331 barrel recipes decode and every barrel block is loaded (PAR-PROC-32)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (tfp.moduleServices() == null) {
            throw new Skip("the feature modules are not enabled");
        }
        Optional<Recipes> recipes = tfp.moduleServices().get(Recipes.class);
        Optional<Tags> tagsService = tfp.moduleServices().get(Tags.class);
        if (recipes.isEmpty() || tagsService.isEmpty()) {
            throw new Skip("the recipe or tag module is not enabled");
        }
        Tags tags = tagsService.get();
        boolean ce = tfp.integrations().activePlugins().contains("CraftEngine");
        CustomItems items = tfp.integrations().customItems();
        CustomBlocks blocks = tfp.integrations().customBlocks();
        Scheduler s = tfp.scheduler();
        return OnThread.script("barrel-recipes", () -> {
            BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
                (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
                id -> items.create(id, 1), items::exists));
            BarrelRecipeBook book = new BarrelRecipeBook(recipes.get(), tanks, BarrelRules.DEFAULT_CAPACITY);
            List<String> problems = new ArrayList<>();
            // The index is built lazily (the first index() call starts a build and returns the previous one), as the
            // first barrel click does in game: build it for the current data before counting.
            recipes.get().rebuild().get(STEP.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            int[] counts = OnThread.global(s, STEP, () -> new int[] {
                book.all(BarrelRecipeView.Kind.SEALED).size(),
                book.all(BarrelRecipeView.Kind.INSTANT).size(),
                book.all(BarrelRecipeView.Kind.INSTANT_FLUID).size()});
            if (counts[0] != 309 || counts[1] != 20 || counts[2] != 2) {
                problems.add("decoded " + counts[0] + " sealed, " + counts[1] + " instant, " + counts[2]
                    + " instant fluid barrel recipes; TFC has 309, 20, 2");
            }
            BarrelRecipeView brined = book.all(BarrelRecipeView.Kind.SEALED).stream()
                .filter(r -> r.id().endsWith("barrel/brined")).findFirst().orElse(null);
            if (brined == null) {
                problems.add("no sealed recipe barrel/brined");
            } else if (brined.duration() != 3600 || brined.inputFluid().amount() != 125) {
                problems.add("barrel/brined: " + brined.duration() + " ticks, " + brined.inputFluid().amount()
                    + " mB (TFC: 3600, 125)");
            }
            String blockDetail;
            if (ce) {
                OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
                List<String> missing = OnThread.global(s, STEP, () -> {
                    List<String> m = new ArrayList<>();
                    for (String wood : WOODS) {
                        String id = BarrelDevice.ID + "/" + wood;
                        if (!blocks.exists(id)) {
                            m.add(id + " (block)");
                        }
                        if (!items.exists(id)) {
                            m.add(id + " (item)");
                        }
                    }
                    return m;
                });
                if (!missing.isEmpty()) {
                    problems.add("CraftEngine does not know " + missing);
                }
                blockDetail = WOODS.size() + " barrel blocks and items loaded";
            } else {
                blockDetail = "CraftEngine absent: barrel blocks not checked";
            }
            if (!problems.isEmpty()) {
                throw new AssertionError(String.join("; ", problems));
            }
            return counts[0] + " sealed, " + counts[1] + " instant, " + counts[2] + " instant fluid recipes; " + blockDetail;
        });
    }
}
