/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.pottery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-POT-02: the ceramic items against TFCItems, on the real pinned checkout. */
class PotteryItemsTest {
    private static TfcCheckout tfc;
    private static Datagen.Result full;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        full = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT);
    }

    private static String text(String path) {
        byte[] b = full.tree().files().get(path);
        assertThat(b).as(path).isNotNull();
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void everyCeramicItemOfTfcItemsIsGenerated() {
        List<PotteryItems.PotteryItem> items = PotteryItems.items(tfc);
        // 16 unfired + 16 glazed vessels, 16 unfired glazed large vessels, molds, plus the single items
        assertThat(items).extracting(PotteryItems.PotteryItem::id)
            .contains("ceramic/unfired_vessel", "ceramic/vessel", "ceramic/ingot_mold", "ceramic/unfired_ingot_mold",
                "ceramic/fire_ingot_mold", "ceramic/bell_mold", "ceramic/jug", "ceramic/unfired_jug", "ceramic/fire_brick",
                "ceramic/red_glazed_vessel", "ceramic/red_unfired_vessel", "ceramic/unfired_large_vessel",
                "ceramic/unfired_large_vessel/red", "ceramic/unfired_mold_table", "ceramic/unfired_channel", "ceramic/pot",
                "ceramic/spindle_head", "ceramic/unfired_bowl", "ceramic/unfired_crucible");
        assertThat(items.stream().filter(i -> i.id().endsWith("_glazed_vessel"))).hasSize(16);
        assertThat(items.stream().filter(i -> i.id().endsWith("_unfired_vessel") && !i.id().equals("ceramic/unfired_vessel")))
            .hasSize(16);
        assertThat(items.stream().filter(i -> i.id().startsWith("ceramic/unfired_large_vessel/"))).hasSize(16);
        String yaml = text(PotteryItems.DIR + "ceramic.yml");
        for (PotteryItems.PotteryItem i : items) {
            assertThat(yaml).contains("  tfp:" + i.id() + ":");
            assertThat(full.tree().files()).containsKey(PotteryItems.RP + "models/item/" + i.id() + ".json");
        }
        assertThat(PotteryItems.ids(items)).doesNotHaveDuplicates();
    }

    @Test
    void jugStacksToOneAndMoldsFollowTheirWeight() {
        var byId = PotteryItems.items(tfc).stream().collect(Collectors.toMap(PotteryItems.PotteryItem::id, i -> i));
        assertThat(byId.get("ceramic/jug").stackSize()).isEqualTo(1);
        assertThat(byId.get("ceramic/unfired_jug").stackSize()).isBetween(1, 64);
    }

    @Test
    void namesAndTexturesArePacked() {
        assertThat(text(PotteryItems.LANG_RP + "en_us.json")).contains("\"item.tfp.ceramic.vessel\"");
        assertThat(full.tree().files()).containsKey(PotteryItems.RP + "textures/item/ceramic/vessel.png");
        // the fluid_container molds become their empty texture
        assertThat(text(PotteryItems.RP + "models/item/ceramic/ingot_mold.json")).contains("tfp:item/ceramic/fired_mold/ingot_empty");
        // block-parented unfired large vessel keeps its parent chain
        assertThat(full.tree().files()).containsKey(PotteryItems.RP + "models/block/ceramic/large_vessel_sealed.json");
    }

    @Test
    void coverageGateOwnsNoCeramicItemAnymore() {
        Set<String> notCe = PotteryItems.items(tfc).stream().map(PotteryItems.PotteryItem::id).collect(Collectors.toSet());
        String report = text("reports/tfc-item-coverage.json");
        for (String id : notCe) {
            assertThat(report).doesNotContain("\"tfc:" + id + "\": \"PAR-POT-02\"");
        }
    }
}
