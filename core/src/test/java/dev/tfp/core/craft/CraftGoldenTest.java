/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.craft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.craft.SewingRules.Board;
import dev.tfp.core.craft.SewingRules.Consumption;
import dev.tfp.core.craft.SewingRules.Material;
import dev.tfp.core.craft.SewingRules.Supply;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * M3-PROC-02 golden values. The recipe tables are checked against {@code tfc-craft-recipes.tsv}, which was transcribed from TFC's
 * recipe JSON at c3b4d9536 (src/generated/resources/data/tfc/recipe/{scraping,loom,sewing}) by a scratch script. The step and
 * result expectations below are TFC's own rules at c3b4d9536 (ScrapingBlockEntity.onClicked, LoomBlockEntity.onRightClick and
 * tick, SewingTableContainer's placement and take logic), worked out by hand from that code.
 */
class CraftGoldenTest {

    static final String TSV = "/dev/tfp/core/craft/tfc-craft-recipes.tsv";

    /** The TFC transcription, parsed from the golden file. */
    static final Map<String, List<String[]>> ROWS = loadRows();

    private static Map<String, List<String[]>> loadRows() {
        try (InputStream in = CraftGoldenTest.class.getResourceAsStream(TSV)) {
            assert in != null : "missing " + TSV;
            final List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            final List<String[]> rows = lines.stream()
                .filter(l -> !l.startsWith("#") && !l.isEmpty())
                .map(l -> l.split("\t", -1))
                .toList();
            return rows.stream().collect(Collectors.groupingBy(r -> r[0]));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<ScrapingRecipe> tfcScraping() {
        final List<ScrapingRecipe> out = new ArrayList<>();
        for (String[] r : ROWS.get("scraping")) {
            out.add(new ScrapingRecipe(r[1], r[2], r[3], r[4], new CraftOutput(r[5], Integer.parseInt(r[6])),
                r[7].equals("-") ? null : new CraftOutput(r[7], Integer.parseInt(r[8]))));
        }
        return out;
    }

    private static List<LoomRecipe> tfcLoom() {
        final List<LoomRecipe> out = new ArrayList<>();
        for (String[] r : ROWS.get("loom")) {
            out.add(new LoomRecipe(r[1], r[2], Integer.parseInt(r[3]), new CraftOutput(r[4], Integer.parseInt(r[5])),
                Integer.parseInt(r[6]), r[7]));
        }
        return out;
    }

    private static List<SewingRecipe> tfcSewing() {
        final List<SewingRecipe> out = new ArrayList<>();
        for (String[] r : ROWS.get("sewing")) {
            out.add(new SewingRecipe(r[1], r[4], r[5], new CraftOutput(r[2], Integer.parseInt(r[3]))));
        }
        return out;
    }

    /** The hit position at the centre of a scraping cell. */
    private static double hit(int cell) {
        return ((cell % 4) + 0.5) / 4;
    }

    private static double hitZ(int cell) {
        return ((cell / 4) + 0.5) / 4;
    }

    // Tables against the TFC transcription

    @Test
    void tablesMatchTfcTranscription() {
        assertThat(CraftRecipes.SCRAPING).isEqualTo(tfcScraping());
        assertThat(CraftRecipes.LOOM).isEqualTo(tfcLoom());
        assertThat(CraftRecipes.SEWING).isEqualTo(tfcSewing());
    }

    @Test
    void tableSizesAreTfcCounts() {
        assertThat(CraftRecipes.SCRAPING).hasSize(7);
        assertThat(CraftRecipes.LOOM).hasSize(5);
        assertThat(CraftRecipes.SEWING).hasSize(25);
    }

    // Scraping

    @Test
    void scrapingLookupsByIngredient() {
        assertThat(ScrapingRules.find(CraftRecipes.SCRAPING, "tfc:medium_soaked_hide")).map(ScrapingRecipe::id)
            .contains("scraping/medium_scraped_hide");
        assertThat(ScrapingRules.find(CraftRecipes.SCRAPING, "tfc:unrefined_paper")).map(ScrapingRecipe::id)
            .contains("scraping/paper");
        assertThat(ScrapingRules.find(CraftRecipes.SCRAPING, "tfc:large_sheepskin_hide")).hasValueSatisfying(r -> {
            assertThat(r.result()).isEqualTo(new CraftOutput("tfc:large_raw_hide", 1));
            assertThat(r.extraDrop()).isEqualTo(new CraftOutput("tfc:wool", 1));
        });
        assertThat(ScrapingRules.find(CraftRecipes.SCRAPING, "minecraft:stick")).isEmpty();
    }

    @Test
    void scrapedHideHasNoExtraDrop() {
        final Optional<ScrapingRecipe> r = ScrapingRules.find(CraftRecipes.SCRAPING, "tfc:small_soaked_hide");
        assertThat(r).hasValueSatisfying(recipe -> {
            assertThat(recipe.result()).isEqualTo(new CraftOutput("tfc:small_scraped_hide", 1));
            assertThat(recipe.extraDrop()).isNull();
        });
    }

    @Test
    void scrapingCompletesOnTheSixteenthDistinctCell() {
        int mask = 0;
        for (int cell = 0; cell < 16; cell++) {
            final ScrapingRules.Step step = ScrapingRules.scrape(mask, hit(cell), hitZ(cell));
            assertThat(step.changed()).isTrue();
            assertThat(step.complete()).isEqualTo(cell == 15);
            mask = step.mask();
            assertThat(ScrapingRules.scrapedCount(mask)).isEqualTo(cell + 1);
        }
        assertThat(mask).isEqualTo(0xFFFF).isEqualTo(ScrapingRules.COMPLETE);
        assertThat((short) mask).isEqualTo((short) -1);
    }

    @Test
    void scrapingRowMajorCellIndex() {
        assertThat(ScrapingRules.cellOf(0.0, 0.0)).isZero();
        assertThat(ScrapingRules.cellOf(0.99, 0.0)).isEqualTo(3);
        assertThat(ScrapingRules.cellOf(0.0, 0.99)).isEqualTo(12);
        assertThat(ScrapingRules.cellOf(0.3, 0.6)).isEqualTo(1 + 2 * 4);
    }

    @Test
    void scrapingEdgeHitIsClampedToLastColumn() {
        final ScrapingRules.Step step = ScrapingRules.scrape(0, 1.0, 1.0);
        assertThat(step.mask()).isEqualTo(1 << 15);
    }

    @Test
    void scrapingRepeatClickChangesNothing() {
        final ScrapingRules.Step first = ScrapingRules.scrape(0, 0.1, 0.1);
        final ScrapingRules.Step again = ScrapingRules.scrape(first.mask(), 0.2, 0.2);
        assertThat(again.changed()).isFalse();
        assertThat(again.mask()).isEqualTo(first.mask());
        assertThat(again.complete()).isFalse();
    }

    @Test
    void scrapingRejectsOutOfRangeInput() {
        assertThatThrownBy(() -> ScrapingRules.scrape(0, -0.01, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrapingRules.scrape(0, 0.5, 1.01)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrapingRules.scrape(0x10000, 0.5, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }

    // Loom

    @Test
    void loomLookupsByIngredient() {
        assertThat(LoomRules.find(CraftRecipes.LOOM, "tfc:jute_fiber")).map(LoomRecipe::id).contains("loom/burlap_cloth");
        assertThat(LoomRules.find(CraftRecipes.LOOM, "minecraft:string")).map(LoomRecipe::id).contains("loom/silk_cloth");
        assertThat(LoomRules.find(CraftRecipes.LOOM, "tfc:wool_cloth")).map(LoomRecipe::id).contains("loom/white_wool");
        assertThat(LoomRules.find(CraftRecipes.LOOM, "tfc:wool_yarn")).map(LoomRecipe::id).contains("loom/wool_cloth");
        assertThat(LoomRules.find(CraftRecipes.LOOM, "minecraft:cobblestone")).isEmpty();
    }

    @Test
    void loomResultsAndCounts() {
        final LoomRecipe white = LoomRules.find(CraftRecipes.LOOM, "tfc:wool_cloth").orElseThrow();
        assertThat(white.ingredientCount()).isEqualTo(4);
        assertThat(white.steps()).isEqualTo(4);
        assertThat(white.result()).isEqualTo(new CraftOutput("minecraft:white_wool", 8));
        final LoomRecipe silk = LoomRules.find(CraftRecipes.LOOM, "minecraft:string").orElseThrow();
        assertThat(silk.ingredientCount()).isEqualTo(24);
        assertThat(silk.steps()).isEqualTo(24);
        assertThat(silk.result()).isEqualTo(new CraftOutput("tfc:silk_cloth", 1));
    }

    @Test
    void loomFirstPushNeedsMoreThanTwentyTicks() {
        final LoomRecipe wool = recipe("loom/wool_cloth");
        assertThat(LoomRules.canPush(wool, LoomRules.NEW, 16, true, 20)).isFalse();
        assertThat(LoomRules.canPush(wool, LoomRules.NEW, 16, true, 21)).isTrue();
    }

    @Test
    void loomPushAppliesAfterTwentyTicks() {
        final LoomRecipe wool = recipe("loom/wool_cloth");
        final LoomRules.State pushed = LoomRules.push(wool, LoomRules.NEW, 16, true, 100).orElseThrow();
        assertThat(pushed).isEqualTo(new LoomRules.State(0, true, 100));
        assertThat(LoomRules.tick(wool, pushed, 119).state()).isEqualTo(pushed);
        final LoomRules.Tick due = LoomRules.tick(wool, pushed, 120);
        assertThat(due.state()).isEqualTo(new LoomRules.State(1, false, 100));
        assertThat(due.finished()).isFalse();
    }

    @Test
    void loomRefusesPushesThatTfcRefuses() {
        final LoomRecipe wool = recipe("loom/wool_cloth");
        assertThat(LoomRules.canPush(wool, LoomRules.NEW, 15, true, 100)).as("ingredient count").isFalse();
        assertThat(LoomRules.canPush(wool, LoomRules.NEW, 16, false, 100)).as("hand holds an item").isFalse();
        assertThat(LoomRules.canPush(null, LoomRules.NEW, 16, true, 100)).as("no recipe").isFalse();
        final LoomRules.State pending = new LoomRules.State(3, true, 100);
        assertThat(LoomRules.canPush(wool, pending, 16, true, 500)).as("push still pending").isFalse();
        final LoomRules.State done = new LoomRules.State(wool.steps(), false, 100);
        assertThat(LoomRules.canPush(wool, done, 16, true, 500)).as("progress at step count").isFalse();
    }

    @Test
    void loomFinishesOnTheTickOfTheLastStep() {
        // Pushes every 21 ticks from game time 100: the last push at 100 + 21 * (steps - 1) is applied 20 ticks later.
        final Map<String, Long> expectedFinishTick = Map.of(
            "loom/burlap_cloth", 351L,
            "loom/silk_cloth", 603L,
            "loom/unrefined_paper", 267L,
            "loom/white_wool", 183L,
            "loom/wool_cloth", 435L);
        for (LoomRecipe recipe : CraftRecipes.LOOM) {
            LoomRules.State state = LoomRules.NEW;
            long time = 100;
            long finishedAt = -1;
            for (int step = 0; step < recipe.steps(); step++) {
                state = LoomRules.push(recipe, state, recipe.ingredientCount(), true, time).orElseThrow();
                final LoomRules.Tick tick = LoomRules.tick(recipe, state, time + 20);
                state = tick.state();
                if (tick.finished()) finishedAt = time + 20;
                time += 21;
            }
            assertThat(finishedAt).as(recipe.id()).isEqualTo(expectedFinishTick.get(recipe.id()));
            assertThat(LoomRules.result(recipe, state)).as(recipe.id()).contains(recipe.result());
        }
    }

    @Test
    void loomStopsAcceptingPushesOnceFinished() {
        final LoomRecipe paper = recipe("loom/unrefined_paper");
        final LoomRules.State done = new LoomRules.State(8, false, 0);
        assertThat(LoomRules.push(paper, done, 4, true, 1000)).isEmpty();
        assertThat(LoomRules.result(paper, new LoomRules.State(7, false, 0))).isEmpty();
        assertThat(LoomRules.result(paper, done)).contains(new CraftOutput("tfc:unrefined_paper", 1));
    }

    // Sewing

    @Test
    void sewingEveryTfcPatternFindsItself() {
        for (SewingRecipe recipe : CraftRecipes.SEWING) {
            final Board board = new Board(recipe.squares(), recipe.stitches(), 0, 0, 0);
            assertThat(SewingRules.find(CraftRecipes.SEWING, board)).as(recipe.id()).map(SewingRecipe::id).contains(recipe.id());
            final long matches = CraftRecipes.SEWING.stream()
                .filter(r -> r.squares().equals(board.squares()) && r.stitches().equals(board.stitches()))
                .count();
            assertThat(matches).as("patterns equal to " + recipe.id()).isEqualTo(1);
        }
    }

    @Test
    void sewingCreeperBannerPlacedByHand() {
        final SewingRecipe creeper = sewing("sewing/creeper_banner_pattern");
        // The creeper pattern: 8 wool squares, 24 burlap squares and 16 stitches, so 1 wool, 3 burlap and 1 yarn of items.
        assertThat(count(creeper.squares(), 'W')).isEqualTo(8);
        assertThat(count(creeper.squares(), 'B')).isEqualTo(24);
        assertThat(count(creeper.stitches(), '#')).isEqualTo(16);

        final Supply supply = new Supply(1, 3, 1, true);
        Board board = Board.EMPTY;
        for (int i = 0; i < SewingRecipe.SQUARES; i++) {
            final char want = creeper.squares().charAt(i);
            final Material material = want == 'W' ? Material.WOOL : Material.BURLAP;
            board = SewingRules.placeSquare(board, supply, i, material).orElseThrow();
        }
        for (int i = 0; i < SewingRecipe.STITCHES; i++) {
            if (creeper.stitches().charAt(i) == '#') board = SewingRules.placeStitch(board, supply, i, true).orElseThrow();
        }
        assertThat(SewingRules.stitchesLeft(board, supply)).isZero();
        assertThat(SewingRules.find(CraftRecipes.SEWING, board)).map(SewingRecipe::id).contains("sewing/creeper_banner_pattern");
        assertThat(SewingRules.consumption(board)).isEqualTo(new Consumption(1, 3, 1));
    }

    @Test
    void sewingClothAndYarnLimits() {
        final Supply oneWool = new Supply(1, 0, 1, true);
        Board board = Board.EMPTY;
        for (int i = 0; i < 8; i++) board = SewingRules.placeSquare(board, oneWool, i, Material.WOOL).orElseThrow();
        assertThat(SewingRules.placeSquare(board, oneWool, 8, Material.WOOL)).as("ninth wool square").isEmpty();
        assertThat(SewingRules.placeSquare(board, oneWool, 9, Material.BURLAP)).as("no burlap").isEmpty();

        final Supply oneYarn = new Supply(0, 0, 1, true);
        Board stitched = Board.EMPTY;
        for (int i = 0; i < 16; i++) stitched = SewingRules.placeStitch(stitched, oneYarn, i, true).orElseThrow();
        assertThat(SewingRules.placeStitch(stitched, oneYarn, 16, true)).as("seventeenth stitch").isEmpty();
    }

    @Test
    void sewingStitchNeedsNeedle() {
        assertThat(SewingRules.placeStitch(Board.EMPTY, new Supply(0, 0, 1, false), 0, true)).isEmpty();
    }

    @Test
    void sewingConsumptionRoundsUpPerItem() {
        assertThat(SewingRules.consumption(new Board(blank(32), blank(45), 9, 1, 17)))
            .isEqualTo(new Consumption(2, 1, 2));
        assertThat(SewingRules.consumption(new Board(blank(32), blank(45), 8, 0, 16)))
            .isEqualTo(new Consumption(1, 0, 1));
        assertThat(SewingRules.consumption(Board.EMPTY)).isEqualTo(new Consumption(0, 0, 0));
    }

    @Test
    void sewingRemoveRefundsNothing() {
        final Supply wool = new Supply(1, 0, 0, false);
        Board board = SewingRules.placeSquare(Board.EMPTY, wool, 0, Material.WOOL).orElseThrow();
        board = SewingRules.placeSquare(board, wool, 0, Material.REMOVE).orElseThrow();
        assertThat(board.squares().charAt(0)).isEqualTo(' ');
        assertThat(board.usedWool()).as("used wool after removal").isEqualTo(1);
        board = SewingRules.placeSquare(board, wool, 0, Material.WOOL).orElseThrow();
        assertThat(board.usedWool()).isEqualTo(2);
    }

    @Test
    void sewingReplacingMaterialSpendsAnotherSquare() {
        final Supply both = new Supply(1, 1, 0, false);
        Board board = SewingRules.placeSquare(Board.EMPTY, both, 5, Material.BURLAP).orElseThrow();
        board = SewingRules.placeSquare(board, both, 5, Material.WOOL).orElseThrow();
        assertThat(board.squares().charAt(5)).isEqualTo('W');
        assertThat(board.usedBurlap()).isEqualTo(1);
        assertThat(board.usedWool()).isEqualTo(1);
    }

    // Helpers

    private static SewingRecipe sewing(String id) {
        return CraftRecipes.SEWING.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static LoomRecipe recipe(String id) {
        return CraftRecipes.LOOM.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static int count(String text, char c) {
        return (int) text.chars().filter(x -> x == c).count();
    }

    private static String blank(int length) {
        return " ".repeat(length);
    }
}
