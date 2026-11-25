/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancementsTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    /** TFC's advancement files at the pinned commit: 68 story, 47 world. */
    private static final int TFC_FILES = 115;

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout); skipped when absent. The count is taken from the checkout itself. */
    @Test
    void realCheckoutHasEveryTfcAdvancementAsAClosedTree() throws IOException {
        TfcCheckout tfc = realCheckout();
        long tfcFiles;
        try (Stream<Path> s = Files.walk(tfc.root().resolve(Advancements.SOURCE))) {
            tfcFiles = s.filter(p -> p.toString().endsWith(".json")).count();
        }
        assertThat(tfcFiles).isEqualTo(TFC_FILES);

        OutputTree out = new OutputTree();
        assertThat(Advancements.run(tfc, out)).isEqualTo(tfcFiles);
        JsonArray list = JsonParser.parseString(text(out, Advancements.OUTPUT)).getAsJsonObject().getAsJsonArray("advancements");
        assertThat(list).hasSize((int) tfcFiles);

        Set<String> ids = new TreeSet<>();
        list.forEach(e -> ids.add(e.getAsJsonObject().get("id").getAsString()));
        assertThat(ids).hasSize((int) tfcFiles);
        // The tree is closed: every parent is an advancement of the output.
        for (var e : list) {
            JsonObject o = e.getAsJsonObject();
            assertThat(o.get("id").getAsString()).startsWith("tfp:");
            assertThat(o.get("tfc").getAsString()).startsWith("tfc:");
            if (o.has("parent")) {
                assertThat(ids).as(o.get("id") + " parent").contains(o.get("parent").getAsString());
            }
        }
        List<String> roots = list.asList().stream().map(JsonObject.class::cast)
            .filter(o -> !o.has("parent")).map(o -> o.get("id").getAsString()).sorted().toList();
        assertThat(roots).containsExactly("tfp:story/root", "tfp:world/root");

        JsonObject barrel = list.asList().stream().map(e -> e.getAsJsonObject())
            .filter(o -> o.get("id").getAsString().equals("tfp:story/barrel")).findFirst().orElseThrow();
        assertThat(barrel.get("parent").getAsString()).isEqualTo("tfp:story/saw");
        assertThat(barrel.get("title").getAsString()).isEqualTo("tfc.advancements.story.barrel.title");
        assertThat(barrel.get("description").getAsString()).isEqualTo("tfc.advancements.story.barrel.description");
        assertThat(barrel.get("criteria").getAsJsonArray().get(0).getAsJsonObject().get("trigger").getAsString())
            .isEqualTo("minecraft:inventory_changed");
        assertThat(barrel.get("requirements").toString()).isEqualTo("[[\"barrels\"]]");
    }

    /** A small hand-made tree: ids renamed, parents resolved, requirements and triggers kept, sorted by id. */
    @Test
    void fixtureTreeIsWrittenWithRenamedIdsAndSortedByIdentity() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json", Fixture.advancement(null, "get"));
        write(root, Advancements.SOURCE + "story/saw.json", Fixture.advancement("tfc:story/root", "saw"));
        write(root, Advancements.SOURCE + "world/root.json", Fixture.advancement(null, "get"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);

        List<Advancements.Advancement> all = Advancements.read(tfc);
        assertThat(all.stream().map(Advancements.Advancement::id).toList())
            .containsExactly("tfp:story/root", "tfp:story/saw", "tfp:world/root");
        Advancements.Advancement saw = all.get(1);
        assertThat(saw.parent()).isEqualTo("tfp:story/root");
        assertThat(saw.tfc()).isEqualTo("tfc:story/saw");
        assertThat(saw.title()).isEqualTo("tfc.advancements.test.saw.title");
        assertThat(saw.requirements()).containsExactly(List.of("saw"));
        assertThat(saw.criteria()).containsExactly(new Advancements.Criterion("saw", "minecraft:inventory_changed"));
        assertThat(saw.frame()).isEqualTo("task");
        assertThat(saw.showToast()).isTrue();
        assertThat(all.get(0).parent()).isNull();

        OutputTree out = new OutputTree();
        assertThat(Advancements.run(tfc, out)).isEqualTo(3);
        assertThat(out.contains(Advancements.OUTPUT)).isTrue();
        assertThat(text(out, Advancements.OUTPUT)).contains("\"id\": \"tfp:story/saw\"").doesNotContain("__comment__\": \"x");
    }

    /** PAR-DATA-02: every advancement as a datapack advancement with impossible criteria, plus the CE lang section. */
    @Test
    void realCheckoutIsEmittedAsDatapackAdvancements() {
        TfcCheckout tfc = realCheckout();
        OutputTree out = new OutputTree();
        out.put("craftengine/tfp/configuration/drops.yml", "items:\n  tfp:rock/loose/schist:\n    template: tfp:item/simple\n");
        assertThat(Advancements.writeDatapack(tfc, out)).isEqualTo(TFC_FILES);
        long files = out.files().keySet().stream().filter(k -> k.startsWith(Advancements.DATAPACK)).count();
        assertThat(files).isEqualTo(TFC_FILES);

        JsonObject root = json(out, Advancements.DATAPACK + "story/root.json");
        assertThat(root.has("parent")).isFalse();
        assertThat(root.getAsJsonObject("criteria").toString()).isEqualTo("{\"in_game_condition\":{\"trigger\":\"minecraft:tick\"}}");
        assertThat(root.getAsJsonObject("display").get("background").getAsString()).isEqualTo("tfp:block/rock/mossy_cobble/diorite");

        JsonObject rock = json(out, Advancements.DATAPACK + "story/find_rock.json");
        assertThat(rock.get("parent").getAsString()).isEqualTo("tfp:story/root");
        assertThat(rock.getAsJsonObject("criteria").toString())
            .isEqualTo("{\"rock_knapping\":{\"trigger\":\"minecraft:impossible\"}}");
        assertThat(rock.get("requirements").toString()).isEqualTo("[[\"rock_knapping\"]]");
        JsonObject display = rock.getAsJsonObject("display");
        assertThat(display.get("icon").toString())
            .isEqualTo("{\"id\":\"minecraft:paper\",\"components\":{\"minecraft:item_model\":\"tfp:rock/loose/schist\"}}");
        assertThat(display.get("title").toString())
            .isEqualTo("{\"translate\":\"tfc.advancements.story.find_rock.title\",\"fallback\":\"Just a Rock\"}");
        assertThat(display.get("show_toast").getAsBoolean()).isTrue();
        assertThat(display.get("announce_to_chat").getAsBoolean()).isTrue();
        assertThat(display.get("frame").getAsString()).isEqualTo("task");

        // No criterion is left with a trigger the server would evaluate against TFC's ids, except the two roots' ticks.
        long ticks = 0;
        for (var f : out.files().entrySet()) {
            if (!f.getKey().startsWith(Advancements.DATAPACK)) {
                continue;
            }
            for (var c : json(out, f.getKey()).getAsJsonObject("criteria").entrySet()) {
                String trigger = c.getValue().getAsJsonObject().get("trigger").getAsString();
                assertThat(trigger).as(f.getKey()).isIn("minecraft:impossible", "minecraft:tick");
                ticks += trigger.equals("minecraft:tick") ? 1 : 0;
            }
            assertThat(new String(f.getValue(), StandardCharsets.UTF_8)).as(f.getKey()).doesNotContain("tfc:");
        }
        assertThat(ticks).isEqualTo(2);
        // A vanilla icon stays; a TFC icon whose CE item is not generated falls back to plain paper.
        assertThat(json(out, Advancements.DATAPACK + "story/charcoal.json").getAsJsonObject("display").get("icon").toString())
            .isEqualTo("{\"id\":\"minecraft:charcoal\"}");
        assertThat(json(out, Advancements.DATAPACK + "world/root.json").getAsJsonObject("display").get("icon").toString())
            .isEqualTo("{\"id\":\"minecraft:paper\"}");

        String lang = text(out, Advancements.CE_LANG);
        assertThat(lang).startsWith("# Generated by tools/datagen").contains("\nlang:\n  de_de:\n").contains("  en_us:\n")
            .contains("    tfc.advancements.story.find_rock.title: 'Just a Rock'\n");
        assertThat(lang.lines().filter(l -> l.startsWith("    tfc.advancements.")).count()).isGreaterThan(13L * 200);
    }

    @Test
    void refusesBackgroundOutsideTextures() {
        assertThat(Advancements.background("tfc:textures/block/mud/fluvisol.png", "x")).isEqualTo("tfp:block/mud/fluvisol");
        assertThatThrownBy(() -> Advancements.background("tfc:block/mud/fluvisol", "x"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("background is not");
    }

    @Test
    void fixtureTreeIsEmittedAsDatapack() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json", Fixture.advancement(null, "get"));
        write(root, Advancements.SOURCE + "story/saw.json", Fixture.advancement("tfc:story/root", "saw"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        OutputTree out = new OutputTree();
        assertThat(Advancements.writeDatapack(tfc, out)).isEqualTo(2);
        JsonObject saw = json(out, Advancements.DATAPACK + "story/saw.json");
        assertThat(saw.get("parent").getAsString()).isEqualTo("tfp:story/root");
        // inventory_changed (with conditions) becomes impossible; no English text in the fixture means no fallback.
        assertThat(saw.getAsJsonObject("criteria").toString()).isEqualTo("{\"saw\":{\"trigger\":\"minecraft:impossible\"}}");
        assertThat(saw.getAsJsonObject("display").get("title").toString())
            .isEqualTo("{\"translate\":\"tfc.advancements.test.saw.title\"}");
        assertThat(out.contains(Advancements.CE_LANG)).as("no lang folder, no lang section").isFalse();
    }

    @Test
    void refusesMissingAdvancementFolder() throws IOException {
        Path root = checkout();
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.run(tfc, new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("TFC has no advancements");
    }

    @Test
    void refusesParentThatIsNotAnAdvancement() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json", Fixture.advancement(null, "get"));
        write(root, Advancements.SOURCE + "story/saw.json", Fixture.advancement("tfc:story/missing", "saw"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("tfc:story/missing is not an advancement of TFC");
    }

    @Test
    void refusesMissingTitleTranslationKey() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json",
            Fixture.advancement(null, "get").replace("{\"translate\": \"tfc.advancements.test.get.title\"}", "{\"text\": \"Root\"}"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("missing string 'translate'");
    }

    @Test
    void refusesRequirementNamingNoCriterion() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json",
            Fixture.advancement(null, "get").replace("[[\"get\"]]", "[[\"nope\"]]"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("criterion 'nope' that is not in criteria");
    }

    @Test
    void refusesNonRootWithoutParent() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json", Fixture.advancement(null, "get"));
        write(root, Advancements.SOURCE + "story/saw.json", Fixture.advancement(null, "saw"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("not the root of its tab");
    }

    @Test
    void refusesParentCycle() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json", Fixture.advancement(null, "get"));
        write(root, Advancements.SOURCE + "story/a.json", Fixture.advancement("tfc:story/b", "a"));
        write(root, Advancements.SOURCE + "story/b.json", Fixture.advancement("tfc:story/a", "b"));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("parent chain is a cycle");
    }

    @Test
    void refusesNonJsonFileInFolder() throws IOException {
        Path root = checkout();
        write(root, Advancements.SOURCE + "story/root.json", Fixture.advancement(null, "get"));
        write(root, Advancements.SOURCE + "story/notes.txt", "x");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> Advancements.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("non-JSON advancement file");
    }

    private static TfcCheckout realCheckout() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(checkout, PIN);
    }

    /** A checkout at the pinned commit with no advancement files yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Fixture.write(root, rel, text);
    }

    private static JsonObject json(OutputTree out, String path) {
        return JsonParser.parseString(text(out, path)).getAsJsonObject();
    }

    private static String text(OutputTree out, String path) {
        return new String(out.get(path), StandardCharsets.UTF_8);
    }
}
