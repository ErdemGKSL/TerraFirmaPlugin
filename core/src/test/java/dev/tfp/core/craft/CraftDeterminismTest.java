/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.craft;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.craft.SewingRules.Board;
import dev.tfp.core.craft.SewingRules.Material;
import dev.tfp.core.craft.SewingRules.Supply;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Determinism of the craft rules: lookups do not depend on table order, scraping completes at the same click whatever the
 * order of distinct cells, and a loom evaluated only when touched reaches the same state as one evaluated every tick.
 */
class CraftDeterminismTest {

    private static final int SEEDS = 50;

    @Test
    void lookupsIgnoreTableOrder() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Random random = new Random(seed);
            final List<ScrapingRecipe> scraping = shuffled(CraftRecipes.SCRAPING, random);
            final List<LoomRecipe> loom = shuffled(CraftRecipes.LOOM, random);
            final List<SewingRecipe> sewing = shuffled(CraftRecipes.SEWING, random);
            for (ScrapingRecipe r : CraftRecipes.SCRAPING) {
                assertThat(ScrapingRules.find(scraping, r.ingredient())).contains(r);
            }
            for (LoomRecipe r : CraftRecipes.LOOM) {
                assertThat(LoomRules.find(loom, r.ingredient())).contains(r);
            }
            for (SewingRecipe r : CraftRecipes.SEWING) {
                assertThat(SewingRules.find(sewing, new Board(r.squares(), r.stitches(), 0, 0, 0))).contains(r);
            }
        }
    }

    @Test
    void scrapingCompletesAtTheSameClickForAnyCellOrder() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final List<Integer> cells = shuffled(IntStream.range(0, 16).boxed().toList(), new Random(seed));
            int mask = 0;
            int changes = 0;
            for (int i = 0; i < cells.size(); i++) {
                final int cell = cells.get(i);
                final ScrapingRules.Step step = ScrapingRules.scrape(mask, ((cell % 4) + 0.5) / 4, ((cell / 4) + 0.5) / 4);
                if (step.changed()) changes++;
                mask = step.mask();
                assertThat(step.complete()).as("seed %d click %d", seed, i).isEqualTo(i == 15);
            }
            assertThat(changes).isEqualTo(16);
            assertThat(mask).isEqualTo(ScrapingRules.COMPLETE);
        }
    }

    @Test
    void sewingSameInputsGiveSameBoard() {
        final SewingRecipe recipe = CraftRecipes.SEWING.get(0);
        final Supply supply = new Supply(4, 4, 4, true);
        for (int run = 0; run < 3; run++) {
            Board board = Board.EMPTY;
            for (int i = 0; i < 32; i++) {
                final Material m = recipe.squares().charAt(i) == 'W' ? Material.WOOL : Material.BURLAP;
                board = SewingRules.placeSquare(board, supply, i, m).orElseThrow();
            }
            assertThat(board.usedBurlap() + board.usedWool()).isEqualTo(32);
            assertThat(board.squares()).isEqualTo(recipe.squares());
            assertThat(board.usedWool()).isEqualTo(count(recipe.squares(), 'W'));
            assertThat(board.usedBurlap()).isEqualTo(count(recipe.squares(), 'B'));
        }
    }

    @Test
    void loomLazyEvaluationMatchesEveryTickEvaluation() {
        for (LoomRecipe recipe : CraftRecipes.LOOM) {
            for (long seed = 0; seed < SEEDS; seed++) {
                final Random random = new Random(seed * 31 + recipe.steps());
                final long end = 2000;
                final List<Long> attempts = new ArrayList<>();
                long t = 0;
                while (t < end) {
                    attempts.add(t);
                    t += 1 + random.nextInt(30);
                }

                // Every tick: applies the due step at each tick, then tries a push at the attempt times.
                LoomRules.State stepped = LoomRules.NEW;
                int attemptIndex = 0;
                for (long now = 0; now <= end; now++) {
                    stepped = LoomRules.tick(recipe, stepped, now).state();
                    if (attemptIndex < attempts.size() && attempts.get(attemptIndex) == now) {
                        stepped = LoomRules.push(recipe, stepped, recipe.ingredientCount(), true, now).orElse(stepped);
                        attemptIndex++;
                    }
                }

                // Only when touched: at each attempt, first apply whatever is due, then try the push.
                LoomRules.State lazy = LoomRules.NEW;
                for (long at : attempts) {
                    lazy = LoomRules.tick(recipe, lazy, at).state();
                    lazy = LoomRules.push(recipe, lazy, recipe.ingredientCount(), true, at).orElse(lazy);
                }
                lazy = LoomRules.tick(recipe, lazy, end).state();

                assertThat(lazy).as("%s seed %d", recipe.id(), seed).isEqualTo(stepped);
            }
        }
    }

    private static <T> List<T> shuffled(List<T> source, Random random) {
        final List<T> copy = new ArrayList<>(source);
        Collections.shuffle(copy, random);
        return copy;
    }

    private static int count(String text, char c) {
        return (int) text.chars().filter(x -> x == c).count();
    }
}
