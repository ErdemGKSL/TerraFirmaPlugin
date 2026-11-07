/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.damagetypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-SURV-10: TFC's damage types and the no_knockback tag in the bootstrap datapack. */
class DamageTypesTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;

    @TempDir
    Path tmp;

    private static TfcCheckout pinned() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(checkout, PIN);
        } catch (DatagenException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit");
            throw e;
        }
        return tfc;
    }

    private static JsonObject json(SortedMap<String, byte[]> files, String path) {
        byte[] b = files.get(path);
        assertThat(b).as(path).isNotNull();
        return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Pinned checkout: the five types and the tag, equal to TFC's renamed, in the bootstrap datapack. */
    @Test
    void pinnedCheckoutShipsTheFiveTypesAndTheTag() {
        TfcCheckout tfc = pinned();
        OutputTree out = new OutputTree();
        DamageTypes.Result result = DamageTypes.run(tfc, out);
        assertThat(result.types()).containsExactly("dehydration", "grill", "lobster_trap", "pluck", "pot");
        assertThat(result.tag()).isTrue();
        SortedMap<String, byte[]> files = out.files();
        assertThat(files.keySet()).hasSize(6).allMatch(p -> p.startsWith("datapack/tfp_seasonal_biomes/data/"));
        assertThat(DamageTypes.TARGET).isEqualTo("datapack/tfp_seasonal_biomes/data/tfp/damage_type/");
        for (String name : result.types()) {
            String src = DamageTypes.SOURCE + name + ".json";
            JsonElement expected = Json.rename(Json.parse(tfc.readString(src), src));
            assertThat(new String(files.get(DamageTypes.TARGET + name + ".json"), StandardCharsets.UTF_8))
                .isEqualTo(Json.write(expected));
        }
        JsonObject dehydration = json(files, DamageTypes.TARGET + "dehydration.json");
        assertThat(dehydration.get("message_id").getAsString()).isEqualTo("tfc.dehydration");
        assertThat(dehydration.get("scaling").getAsString()).isEqualTo("when_caused_by_living_non_player");
        assertThat(dehydration.get("exhaustion").getAsFloat()).isZero();
        assertThat(dehydration.has("effects")).isFalse();
        assertThat(json(files, DamageTypes.TARGET + "grill.json").get("effects").getAsString()).isEqualTo("burning");
        JsonObject tag = json(files, DamageTypes.TAG_TARGET);
        assertThat(tag.getAsJsonArray("values").asList().stream().map(JsonElement::getAsString))
            .containsExactly("tfp:dehydration", "tfp:grill", "tfp:pluck", "tfp:pot", "tfp:lobster_trap");
        assertThat(tag.has("replace")).isFalse(); // added to vanilla's no_knockback, not replacing it
    }

    /** A checkout without the sources ships nothing; a tag naming an unshipped tfp: type fails. */
    @Test
    void fixtureChecks() throws IOException {
        Path root = tmp.resolve("tfc");
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(root.resolve(".git/HEAD"), PIN + "\n");
        OutputTree empty = new OutputTree();
        DamageTypes.Result none = DamageTypes.run(TfcCheckout.open(root, PIN), empty);
        assertThat(none.types()).isEmpty();
        assertThat(none.tag()).isFalse();
        assertThat(empty.files()).isEmpty();

        write(root, DamageTypes.SOURCE + "pot.json",
            "{\"exhaustion\": 0.1, \"message_id\": \"tfc.pot\", \"scaling\": \"when_caused_by_living_non_player\"}");
        write(root, DamageTypes.TAG_SOURCE, "{\"values\": [\"tfc:pot\", \"tfc:grill\"]}");
        assertThatThrownBy(() -> DamageTypes.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("tfp:grill");

        write(root, DamageTypes.JAVA, "ResourceKey.create(Registries.DAMAGE_TYPE, Helpers.identifier(\"pot\"));\n"
            + "ResourceKey.create(Registries.DAMAGE_TYPE, Helpers.identifier(\"grill\"));");
        assertThatThrownBy(() -> DamageTypes.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("TFCDamageTypes declares");
    }

    @Test
    void rejectsInvalidTypes() {
        assertThatThrownBy(() -> DamageTypes.check("x", Json.parse("{\"message_id\": \"a\", \"exhaustion\": 0}", "x")))
            .isInstanceOf(DatagenException.class);
        assertThatThrownBy(() -> DamageTypes.check("x", Json.parse(
            "{\"message_id\": \"a\", \"exhaustion\": 0, \"scaling\": \"sometimes\"}", "x"))).isInstanceOf(DatagenException.class);
        DamageTypes.check("x", Json.parse("{\"message_id\": \"a\", \"exhaustion\": 0, \"scaling\": \"never\"}", "x"));
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }
}
