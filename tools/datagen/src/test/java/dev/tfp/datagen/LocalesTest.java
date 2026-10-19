/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class LocalesTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    /** en_us translation keys at the pinned commit (the file has 10076 entries, one of them mcresources' __comment__). */
    private static final int TFC_EN_KEYS = 10075;
    /** TFC's language files at the pinned commit: one per locale, including tr_tr. */
    private static final int TFC_LOCALES = 13;

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout); skipped when absent. The counts are taken from the checkout. */
    @Test
    void realCheckoutHasEveryTfcLocaleWithCoverageAgainstEnUs() throws IOException {
        TfcCheckout tfc = realCheckout();
        long files = countJson(tfc.root().resolve(Locales.SOURCE));
        assertThat(files).isEqualTo(TFC_LOCALES);

        OutputTree out = new OutputTree();
        assertThat(Locales.run(tfc, out)).isEqualTo(TFC_LOCALES);

        JsonObject en = parse(out, "lang/en_us.json");
        assertThat(en.keySet()).hasSize(TFC_EN_KEYS);
        assertThat(en.get("tfc.field_guide.book_name").getAsString()).isNotBlank();
        assertThat(en.has("__comment__")).isFalse();

        Set<String> enKeys = new TreeSet<>(en.keySet());
        JsonObject index = parse(out, Locales.INDEX);
        assertThat(index.get("en_us_keys").getAsInt()).isEqualTo(TFC_EN_KEYS);
        var rows = index.getAsJsonArray("locales");
        assertThat(rows).hasSize(TFC_LOCALES);
        assertThat(rows.get(0).getAsJsonObject().get("locale").getAsString()).isEqualTo("en_us");
        Set<String> ids = new TreeSet<>();
        for (var r : rows) {
            JsonObject row = r.getAsJsonObject();
            String id = row.get("locale").getAsString();
            ids.add(id);
            JsonObject locale = parse(out, "lang/" + id + ".json");
            // Each locale's keys are a subset of en_us; anything else is reported as extra and must be none at the pin.
            assertThat(enKeys).as(id + " keys outside en_us").containsAll(locale.keySet());
            assertThat(row.get("keys").getAsInt()).isEqualTo(locale.keySet().size());
            assertThat(row.get("extra").getAsInt()).isZero();
            assertThat(row.get("missing").getAsInt()).isEqualTo(TFC_EN_KEYS - locale.keySet().size());
            assertThat(locale.keySet()).noneMatch(k -> k.startsWith("__comment"));
        }
        assertThat(ids).contains("tr_tr", "de_de", "zh_cn");
        assertThat(ids).hasSize(TFC_LOCALES);
    }

    /** A small hand-made set: comments dropped, keys sorted, English first, an extra key reported and kept. */
    @Test
    void fixtureLocalesKeepKeysVerbatimAndReportCoverage() throws IOException {
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        write("assets/tfc/lang/en_us.json", "{\"__comment__\": \"x\", \"tfc.b\": \"B\", \"block.tfc.a\": \"A\", \"tfc.c\": \"C\"}");
        write("assets/tfc/lang/tr_tr.json",
            "{\"__comment__\": \"x\", \"__comment1__\": \"y\", \"tfc.b\": \"Bee\", \"tfc.gone\": \"extra\"}");
        write("assets/tfc/lang/de_de.json", "{\"block.tfc.a\": \"Aa\", \"tfc.b\": \"Bb\", \"tfc.c\": \"Cc\"}");

        assertThat(Locales.read(tfc)).extracting(Locales.Locale::id).containsExactly("en_us", "de_de", "tr_tr");

        OutputTree out = new OutputTree();
        assertThat(Locales.run(tfc, out)).isEqualTo(3);
        JsonObject tr = parse(out, "lang/tr_tr.json");
        assertThat(tr.keySet()).containsExactly("tfc.b", "tfc.gone");
        assertThat(tr.get("tfc.b").getAsString()).isEqualTo("Bee");

        JsonObject en = parse(out, "lang/en_us.json");
        assertThat(en.keySet()).containsExactly("block.tfc.a", "tfc.b", "tfc.c");

        JsonObject index = parse(out, Locales.INDEX);
        assertThat(index.get("en_us_keys").getAsInt()).isEqualTo(3);
        JsonObject trRow = index.getAsJsonArray("locales").get(2).getAsJsonObject();
        assertThat(trRow.get("locale").getAsString()).isEqualTo("tr_tr");
        assertThat(trRow.get("keys").getAsInt()).isEqualTo(2);
        assertThat(trRow.get("missing").getAsInt()).isEqualTo(2);
        assertThat(trRow.get("extra").getAsInt()).isEqualTo(1);
        JsonObject deRow = index.getAsJsonArray("locales").get(1).getAsJsonObject();
        assertThat(deRow.get("missing").getAsInt()).isZero();
        assertThat(deRow.get("extra").getAsInt()).isZero();
    }

    @Test
    void refusesMissingLangFolder() throws IOException {
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("TFC has no language files");
    }

    @Test
    void refusesMissingEnglishFile() throws IOException {
        write("assets/tfc/lang/de_de.json", "{\"tfc.b\": \"Bb\"}");
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("TFC has no en_us.json");
    }

    @Test
    void refusesEmptyEnglishFile() throws IOException {
        write("assets/tfc/lang/en_us.json", "{\"__comment__\": \"only a comment\"}");
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("has no translation keys");
    }

    @Test
    void refusesNonStringTranslation() throws IOException {
        write("assets/tfc/lang/en_us.json", "{\"tfc.b\": \"B\"}");
        write("assets/tfc/lang/de_de.json", "{\"tfc.b\": [\"Bb\"]}");
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("translation tfc.b is not a string");
    }

    @Test
    void refusesFileThatIsNotALocale() throws IOException {
        write("assets/tfc/lang/en_us.json", "{\"tfc.b\": \"B\"}");
        write("assets/tfc/lang/notes.txt", "x");
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("unexpected language file");
    }

    @Test
    void refusesNestedLanguageFile() throws IOException {
        write("assets/tfc/lang/en_us.json", "{\"tfc.b\": \"B\"}");
        write("assets/tfc/lang/extra/de_de.json", "{\"tfc.b\": \"Bb\"}");
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("unexpected language file");
    }

    @Test
    void refusesLanguageFileThatIsNotAnObject() throws IOException {
        write("assets/tfc/lang/en_us.json", "[\"B\"]");
        TfcCheckout tfc = TfcCheckout.open(checkout(), PIN);
        assertThatThrownBy(() -> Locales.read(tfc))
            .isInstanceOf(DatagenException.class).hasMessageContaining("is not a JSON object");
    }

    private static JsonObject parse(OutputTree out, String path) {
        byte[] bytes = out.get(path);
        assertThat(bytes).as(path).isNotNull();
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static long countJson(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).count();
        }
    }

    private static TfcCheckout realCheckout() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(checkout, PIN);
    }

    /** A checkout at the pinned commit with no language files yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }

    private void write(String rel, String text) throws IOException {
        Fixture.write(tmp.resolve("tfc"), TfcCheckout.MAIN + rel, text);
    }
}
