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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FieldGuideTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    /** TFC's field guide at the pinned commit: 7 English categories, 81 English entries. */
    private static final int TFC_CATEGORIES = 7;
    private static final int TFC_ENTRIES = 81;

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout); skipped when absent. The counts are taken from the checkout. */
    @Test
    void realCheckoutHasEveryCategoryAndEntryInReadingOrder() throws IOException {
        TfcCheckout tfc = realCheckout();
        assertThat(countJson(tfc.root().resolve(FieldGuide.SOURCE + "categories"))).isEqualTo(TFC_CATEGORIES);
        assertThat(countJson(tfc.root().resolve(FieldGuide.SOURCE + "entries"))).isEqualTo(TFC_ENTRIES);

        OutputTree out = new OutputTree();
        assertThat(FieldGuide.run(tfc, out)).isEqualTo(TFC_ENTRIES);
        JsonObject root = JsonParser.parseString(text(out, FieldGuide.OUTPUT)).getAsJsonObject();
        JsonArray cats = root.getAsJsonArray("categories");
        JsonArray entries = root.getAsJsonArray("entries");
        assertThat(cats).hasSize(TFC_CATEGORIES);
        assertThat(entries).hasSize(TFC_ENTRIES);

        // Categories are in sortnum order 0..6, and every entry's category resolves to one of them.
        Set<String> catIds = new TreeSet<>();
        for (int i = 0; i < cats.size(); i++) {
            JsonObject c = cats.get(i).getAsJsonObject();
            assertThat(c.get("sortnum").getAsInt()).isEqualTo(i);
            catIds.add(c.get("id").getAsString());
        }
        assertThat(cats.get(0).getAsJsonObject().get("id").getAsString()).isEqualTo("tfp:getting_started");
        int last = -1;
        Set<String> ids = new TreeSet<>();
        for (var e : entries) {
            JsonObject o = e.getAsJsonObject();
            assertThat(o.get("id").getAsString()).startsWith("tfp:");
            assertThat(o.get("tfc").getAsString()).startsWith("tfc:");
            assertThat(catIds).as(o.get("id") + " category").contains(o.get("category").getAsString());
            assertThat(o.get("title_key").getAsString()).startsWith("tfp.field_guide.entry.");
            assertThat(o.get("pages").getAsInt()).isPositive();
            ids.add(o.get("id").getAsString());
            // Reading order: the category index never goes back.
            int catOrder = cats.asList().stream().map(JsonObject.class::cast).map(c -> c.get("id").getAsString()).toList()
                .indexOf(o.get("category").getAsString());
            assertThat(catOrder).isGreaterThanOrEqualTo(last);
            last = catOrder;
        }
        assertThat(ids).hasSize(TFC_ENTRIES);

        JsonObject intro = find(entries, "tfp:getting_started/introduction");
        assertThat(intro.get("pages").getAsInt()).isEqualTo(12);
        assertThat(intro.get("sortnum").getAsInt()).isZero();
        assertThat(intro.get("title").getAsString()).isEqualTo("Introduction");
        assertThat(intro.getAsJsonObject("icon").get("type").getAsString()).isEqualTo("item");
        assertThat(intro.getAsJsonObject("icon").get("id").getAsString()).isEqualTo("tfp:rock/loose/granite");

        JsonObject climate = find(entries, "tfp:core_mechanics/climate");
        assertThat(climate.get("category").getAsString()).isEqualTo("tfp:core_mechanics");
        assertThat(climate.get("pages").getAsInt()).isEqualTo(12);
        assertThat(climate.getAsJsonObject("icon").get("type").getAsString()).isEqualTo("texture");
        assertThat(climate.getAsJsonObject("icon").get("id").getAsString())
            .isEqualTo("tfp:textures/gui/book/icons/thermometer.png");

        // An entry without sortnum sorts as 0, so the mechanics category (no sortnums at all) is by id.
        JsonObject first = null;
        for (var e : entries) {
            if (e.getAsJsonObject().get("category").getAsString().equals("tfp:mechanics")) {
                first = e.getAsJsonObject();
                break;
            }
        }
        assertThat(first).isNotNull();
        assertThat(first.get("id").getAsString()).isEqualTo("tfp:mechanics/advanced_building_materials");

        JsonObject book = root.getAsJsonObject("book");
        assertThat(book.get("name_key").getAsString()).isEqualTo("tfc.field_guide.book_name");
        assertThat(book.get("landing_text_key").getAsString()).isEqualTo("tfc.field_guide.book_landing_text");
    }

    /** A small hand-made guide: reading order by sortnum then id, the three icon kinds, and the structure kept. */
    @Test
    void fixtureGuideIsWrittenInReadingOrder() throws IOException {
        Path root = guideCheckout();
        TfcCheckout tfc = TfcCheckout.open(root, PIN);

        FieldGuide.Guide g = FieldGuide.read(tfc);
        assertThat(g.categories().stream().map(FieldGuide.Category::id).toList())
            .containsExactly("tfp:getting_started", "tfp:mechanics");
        assertThat(g.entries().stream().map(FieldGuide.Entry::id).toList())
            .containsExactly("tfp:getting_started/introduction", "tfp:getting_started/firepit", "tfp:mechanics/channels");
        FieldGuide.Entry channels = g.entries().get(2);
        assertThat(channels.sortnum()).isZero();
        assertThat(channels.pages()).isEqualTo(3);
        assertThat(channels.icon()).isEqualTo(new FieldGuide.Icon("item", "tfp:ceramic/ingot_mold",
            "[tfp:fluid={id:\"tfp:metal/bronze\",amount:100}]"));
        assertThat(g.entries().get(1).icon()).isEqualTo(
            new FieldGuide.Icon("texture", "tfp:textures/gui/book/icons/firepit.png", null));
        assertThat(g.entries().get(0).titleKey()).isEqualTo("tfp.field_guide.entry.getting_started.introduction.title");

        OutputTree out = new OutputTree();
        assertThat(FieldGuide.run(tfc, out)).isEqualTo(3);
        String text = text(out, FieldGuide.OUTPUT);
        assertThat(text).contains("\"id\": \"tfp:getting_started/firepit\"").doesNotContain("__comment__\": \"x");
        assertThat(text).contains("\"name_key\": \"tfc.field_guide.book_name\"");
    }

    @Test
    void refusesMissingBook() throws IOException {
        Path root = checkout();
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("no field guide book.json");
    }

    @Test
    void refusesMissingGuideFolder() throws IOException {
        Path root = checkout();
        Fixture.write(root, FieldGuide.BOOK, "{\"name\": \"a\", \"landing_text\": \"b\"}");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("TFC has no field guide at");
    }

    @Test
    void refusesEntryWhoseCategoryDoesNotResolve() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "entries/getting_started/bad.json", Fixture.guideEntry("nope", "Bad", "tfc:stick", 2, 1));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("category tfc:nope is not the folder category tfc:getting_started");
    }

    @Test
    void refusesEntryFolderWithoutCategoryFile() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "entries/orphan/a.json", Fixture.guideEntry("orphan", "A", "tfc:stick", null, 1));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("entries/orphan has no category file");
    }

    @Test
    void refusesCategoryWithoutEntries() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "categories/lonely.json", "{\"name\": \"Lonely\", \"description\": \"x\", "
            + "\"icon\": \"tfc:stick\", \"sortnum\": 9}");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("category lonely has no entries");
    }

    @Test
    void refusesEntryWithNoPages() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "entries/getting_started/empty.json",
            "{\"name\": \"Empty\", \"category\": \"tfc:getting_started\", \"icon\": \"tfc:stick\", \"pages\": [], \"read_by_default\": true}");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("entry has no pages");
    }

    @Test
    void refusesEntryWithoutCategoryField() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "entries/getting_started/nocat.json",
            "{\"name\": \"No Category\", \"icon\": \"tfc:stick\", \"pages\": [{\"type\": \"patchouli:text\"}], \"read_by_default\": true}");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("missing string 'category'");
    }

    @Test
    void refusesIconThatIsNeitherItemNorTexture() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "entries/getting_started/icon.json",
            Fixture.guideEntry("getting_started", "Icon", "Not An Item", null, 1));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("neither an item id nor a texture");
    }

    @Test
    void refusesDuplicateCategorySortnum() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "categories/clash.json", "{\"name\": \"Clash\", \"description\": \"x\", "
            + "\"icon\": \"tfc:stick\", \"sortnum\": 0}");
        write(root, FieldGuide.SOURCE + "entries/clash/a.json", Fixture.guideEntry("clash", "A", "tfc:stick", null, 1));
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("share the sortnum 0");
    }

    @Test
    void refusesNonJsonEntryFile() throws IOException {
        Path root = guideCheckout();
        write(root, FieldGuide.SOURCE + "entries/getting_started/notes.txt", "x");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        assertThatThrownBy(() -> FieldGuide.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("non-JSON or nested field guide file");
    }

    /** PAR-DATA-04: every page of the real book is converted to text; recipes are text tables; links resolve. */
    @Test
    void realCheckoutConvertsEveryPageToText() throws IOException {
        TfcCheckout tfc = realCheckout();
        OutputTree out = new OutputTree();
        FieldGuide.run(tfc, out);
        JsonObject root = JsonParser.parseString(text(out, FieldGuide.OUTPUT)).getAsJsonObject();
        JsonArray entries = root.getAsJsonArray("entries");
        Set<String> ids = new TreeSet<>();
        entries.forEach(e -> ids.add(e.getAsJsonObject().get("id").getAsString()));
        java.util.regex.Pattern link = java.util.regex.Pattern.compile("\\$\\(l:([^)]+)\\)");
        int pages = 0;
        for (var e : entries) {
            JsonObject o = e.getAsJsonObject();
            JsonArray content = o.getAsJsonArray("content");
            assertThat(content).as(o.get("id") + " content").isNotEmpty();
            for (var p : content) {
                String t = p.getAsJsonObject().get("text").getAsString();
                pages++;
                assertThat(t).as(o.get("id") + " config values").doesNotContain("$(cfg:");
                var m = link.matcher(t);
                while (m.find()) {
                    String target = m.group(1);
                    if (target.startsWith("http")) {
                        continue;
                    }
                    String path = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
                    assertThat(ids).as(o.get("id") + " link " + target).contains("tfp:" + path);
                }
            }
        }
        assertThat(pages).isGreaterThan(800);
        assertThat(root.getAsJsonObject("book").get("name").getAsString()).isEqualTo("Field Guide");
        assertThat(root.getAsJsonObject("book").get("landing_text").getAsString()).startsWith("Welcome traveller!");

        String firepit = find(entries, "tfp:getting_started/firepit").getAsJsonArray("content").toString();
        assertThat(firepit).contains("$(l)Crafting$(): Firestarter$(br)-S$(br)S-$(br)S = Wooden Rods");
        assertThat(firepit).contains("$(l)Heating$(): Wooden Rods -> 2 x Torch$(br)at $(7)Warming$() (60\u00B0C)");
        // TFC's climate table (wild crops) resolves $(cfg:temperature:..) as %.1f°C
        String crops = find(entries, "tfp:the_world/wild_crops").getAsJsonArray("content").toString();
        assertThat(crops).contains("Cassava").contains("10.4\u00B0C - 40.0\u00B0C");
        String knapping = find(entries, "tfp:getting_started/pottery").getAsJsonArray("content").toString();
        assertThat(knapping).contains("Clay Knapping").contains("X = keep, - = remove");

        String html = text(out, FieldGuide.WEB);
        assertThat(html).startsWith("<!doctype html>").contains("id=\"tfp-getting_started-firepit\"")
            .contains("<kbd>Right Click</kbd>");
        for (String id : ids) {
            assertThat(html).contains("id=\"" + id.replace(':', '-').replace('/', '-') + "\"");
        }
        assertThat(text(out, FieldGuide.ITEM_CONFIG)).contains("tfp:field_guide:").contains("item_name: '<!i><lang:item.tfp.field_guide>'")
            .contains("- minecraft:book");
        assertThat(text(out, FieldGuide.LANG_RP + "en_us.json")).contains("\"item.tfp.field_guide\": \"Field Guide\"");
    }

    /** PAR-DATA-04: page conversion on a hand-made checkout: a recipe page, a spotlight, a table, config values. */
    @Test
    void fixturePagesConvertRecipesTablesAndConfigValues() throws IOException {
        Path root = guideCheckout();
        write(root, TfcCheckout.ASSETS + "lang/en_us.json", "{\"item.tfc.firestarter\": \"Firestarter\", "
            + "\"tfc.tab.field_guide\": \"Field Guide\", \"tfc.field_guide.book_landing_text\": \"Hello\"}");
        write(root, TfcCheckout.GENERATED + "data/tfc/recipe/crafting/firestarter.json", "{\"type\": \"minecraft:crafting_shaped\", "
            + "\"key\": {\"S\": {\"tag\": \"c:rods/wooden\"}}, \"pattern\": [\" S\", \"S \"], \"result\": {\"count\": 1, \"id\": \"tfc:firestarter\"}}");
        write(root, TfcCheckout.GENERATED + "data/tfc/tfc/climate_range/crop/wheat.json",
            "{\"max_hydration\": 85, \"max_temperature\": 22.0, \"min_hydration\": 15, \"min_temperature\": -7.0}");
        write(root, FieldGuide.SOURCE + "entries/getting_started/firepit.json", "{\"name\": \"Firepit\", \"category\": \"tfc:getting_started\", "
            + "\"icon\": \"tfc:firepit\", \"read_by_default\": true, \"sortnum\": 1, \"pages\": ["
            + "{\"type\": \"patchouli:crafting\", \"recipe\": \"tfc:crafting/firestarter\", \"text\": \"Two sticks.\"},"
            + "{\"type\": \"patchouli:crafting\", \"recipe\": \"minecraft:book\"},"
            + "{\"type\": \"patchouli:spotlight\", \"item\": \"tfc:firestarter,minecraft:blue_dye\", \"text\": \"x\"},"
            + "{\"type\": \"patchouli:empty\"},"
            + "{\"type\": \"patchouli:text\", \"text\": \"Torch: $(cfg:torchTicks), wheat $(cfg:climate.wheat.temperature) $(cfg:climate.wheat.hydration), $(cfg:heat:930)hot\"},"
            + "{\"type\": \"tfc:table\", \"columns\": 2, \"title\": \"T\", \"legend\": [{\"text\": \"Ok\", \"color\": \"0x3eb340\"}], \"strings\": ["
            + "{\"text\": \"Crop\", \"bold\": true}, {\"text\": \"A\", \"bold\": true}, {\"text\": \"B\", \"bold\": true},"
            + "{\"text\": \"Rice\"}, {\"fill\": \"0x3eb340\"}, {\"text\": \"1\"}]}"
            + "]}");
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        FieldGuide.Guide g = FieldGuide.read(tfc);
        FieldGuide.Entry firepit = g.entries().stream().filter(e -> e.id().equals("tfp:getting_started/firepit")).findFirst().orElseThrow();
        assertThat(firepit.pages()).isEqualTo(6);
        var c = firepit.content();
        assertThat(c).hasSize(5); // the empty page is dropped
        assertThat(c.get(0).text()).isEqualTo("$(l)Crafting$(): Firestarter$(br)-S$(br)S-$(br)S = Wooden Rods$(br2)Two sticks.");
        assertThat(c.get(1).text()).isEqualTo("$(l)Crafting$(): Book");
        assertThat(c.get(2).title()).isEqualTo("Firestarter / Blue Dye");
        // torch: 86400 player ticks * 20/24 = 3 days; heat 930 is Orange (gold)
        assertThat(c.get(3).text()).isEqualTo("Torch: 3 day(s), wheat -7.0\u00B0C - 22.0\u00B0C 15 - 85, $(6)Orangehot");
        assertThat(c.get(4).title()).isEqualTo("T");
        assertThat(c.get(4).text()).isEqualTo("$(l)Crop$(): $(l)A$(), $(l)B$()$(br)$(l)Rice$(): $(#3eb340)\u25A0$() 1$(br)$(#3eb340)\u25A0$() Ok");
        assertThat(g.book().name()).isEqualTo("Field Guide");
        assertThat(g.book().landingText()).isEqualTo("Hello");
    }

    @Test
    void refusesUnknownPageTypeMissingRecipeAndUnknownConfig() throws IOException {
        for (String page : new String[] {"{\"type\": \"patchouli:mystery\"}",
            "{\"type\": \"patchouli:crafting\", \"recipe\": \"tfc:crafting/nope\"}",
            "{\"type\": \"patchouli:text\", \"text\": \"$(cfg:nope)\"}"}) {
            Path root = guideCheckout();
            write(root, FieldGuide.SOURCE + "entries/getting_started/bad.json", "{\"name\": \"Bad\", \"category\": \"tfc:getting_started\", "
                + "\"icon\": \"tfc:stick\", \"read_by_default\": true, \"pages\": [" + page + "]}");
            TfcCheckout tfc = TfcCheckout.open(root, PIN);
            assertThatThrownBy(() -> FieldGuide.read(tfc)).as(page).isInstanceOf(DatagenException.class)
                .hasMessageContaining("bad.json");
        }
    }

    private static JsonObject find(JsonArray entries, String id) {
        return entries.asList().stream().map(JsonObject.class::cast)
            .filter(o -> o.get("id").getAsString().equals(id)).findFirst().orElseThrow();
    }

    private static long countJson(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).count();
        }
    }

    private static TfcCheckout realCheckout() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(checkout, PIN);
    }

    /** A checkout at the pinned commit with a two-category guide: getting_started (sortnums 0 and 1) and mechanics (none). */
    private Path guideCheckout() throws IOException {
        Path root = checkout();
        write(root, FieldGuide.BOOK, "{\"name\": \"tfc.field_guide.book_name\", \"landing_text\": \"tfc.field_guide.book_landing_text\"}");
        write(root, FieldGuide.SOURCE + "categories/getting_started.json",
            "{\"name\": \"Getting Started\", \"description\": \"Start here.\", \"icon\": \"tfc:stone/axe/sedimentary\", \"sortnum\": 0}");
        write(root, FieldGuide.SOURCE + "categories/mechanics.json",
            "{\"name\": \"Miscellaneous\", \"description\": \"Everything else.\", \"icon\": \"tfc:metal/helmet/blue_steel\", \"sortnum\": 1}");
        write(root, FieldGuide.SOURCE + "entries/getting_started/introduction.json",
            Fixture.guideEntry("getting_started", "Introduction", "tfc:rock/loose/granite", 0, 2));
        write(root, FieldGuide.SOURCE + "entries/getting_started/firepit.json",
            Fixture.guideEntry("getting_started", "Firepit", "tfc:textures/gui/book/icons/firepit.png", 1, 1));
        write(root, FieldGuide.SOURCE + "entries/mechanics/channels.json", Fixture.guideEntry("mechanics", "Channels",
            "tfc:ceramic/ingot_mold[tfc:fluid={id:\"tfc:metal/bronze\",amount:100}]", null, 3));
        return root;
    }

    /** A checkout at the pinned commit with no field guide yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Fixture.write(root, rel, text);
    }

    private static String text(OutputTree out, String path) {
        return new String(out.get(path), StandardCharsets.UTF_8);
    }
}
