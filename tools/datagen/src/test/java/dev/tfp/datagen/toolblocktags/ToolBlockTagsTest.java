/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.toolblocktags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.crafting.CraftingRecipes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-PROC-24: the tfp:mineable/* block tags as server tags in the bootstrap datapack. */
class ToolBlockTagsTest {

    private static List<String> entries(OutputTree out, String tag) {
        byte[] bytes = out.get(ToolBlockTags.serverTagPath(tag));
        assertThat(bytes).as(tag).isNotNull();
        JsonObject file = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        assertThat(file.keySet()).containsExactly("values");
        List<String> ids = new ArrayList<>();
        for (JsonElement e : file.getAsJsonArray("values")) {
            JsonObject o = e.getAsJsonObject();
            assertThat(o.get("required").getAsBoolean()).as(tag + " " + o).isFalse();
            ids.add(o.get("id").getAsString());
        }
        return ids;
    }

    @Test
    void pathsAndServerTags() {
        assertThat(ToolBlockTags.serverTagPath("tfp:mineable/knife"))
            .isEqualTo("datapack/tfp_seasonal_biomes/data/tfp/tags/block/mineable/knife.json");
        OutputTree out = new OutputTree();
        assertThat(ToolBlockTags.isServerTag(out, "minecraft:mineable/axe")).isTrue();
        assertThat(ToolBlockTags.isServerTag(out, "tfp:mineable/knife")).isFalse();
        out.put("data/tfp/tags/block/mineable/knife.json", "{\"values\":[\"#minecraft:mineable/hoe\"]}");
        ToolBlockTags.run(out);
        assertThat(ToolBlockTags.isServerTag(out, "tfp:mineable/knife")).isTrue();
        assertThat(entries(out, "tfp:mineable/knife")).containsExactly("#minecraft:mineable/hoe");
    }

    /** c: layers (NeoForge base + TFC addition, replace honoured), tfp: tags inlined, CE blocks left out. */
    @Test
    void resolvesLayers() {
        OutputTree out = new OutputTree();
        out.put("data/neoforge_common_tags.json", """
            {"block": {"c:glass_blocks": ["#c:glass_blocks/cheap", "minecraft:tinted_glass"],
                       "c:glass_blocks/cheap": ["minecraft:glass"], "c:panes": ["minecraft:glass_pane"]}}""");
        out.put("data/c/tags/block/glass_blocks/cheap.json", "{\"values\":[\"tfp:poured_glass\",\"minecraft:white_stained_glass\"]}");
        out.put("data/c/tags/block/panes.json", "{\"replace\":true,\"values\":[{\"id\":\"minecraft:iron_bars\",\"required\":false}]}");
        out.put("data/tfp/tags/block/other.json", "{\"values\":[\"minecraft:stone\",\"#c:unknown\"]}");
        out.put("data/tfp/tags/block/mineable/glass_saw.json",
            "{\"values\":[\"#c:glass_blocks\",\"#c:panes\",\"#tfp:other\",\"tfp:black_poured_glass\"]}");
        List<ToolBlockTags.Shipped> shipped = ToolBlockTags.run(out);
        assertThat(shipped).hasSize(1);
        assertThat(entries(out, "tfp:mineable/glass_saw")).containsExactly(
            "minecraft:glass", "minecraft:white_stained_glass", "minecraft:tinted_glass", "minecraft:iron_bars", "minecraft:stone");
        assertThat(shipped.get(0).dropped()).containsExactlyInAnyOrder("tfp:poured_glass", "#c:unknown", "tfp:black_poured_glass");
        JsonObject report = JsonParser.parseString(new String(out.get(ToolBlockTags.REPORT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertThat(report.getAsJsonObject("tfp:mineable/glass_saw").getAsJsonArray("left_out")).hasSize(3);
        // only the mineable folder is shipped
        assertThat(out.contains(ToolBlockTags.serverTagPath("tfp:other"))).isFalse();
    }

    @Test
    void cycleFails() {
        OutputTree out = new OutputTree();
        out.put("data/tfp/tags/block/a.json", "{\"values\":[\"#tfp:mineable/x\"]}");
        out.put("data/tfp/tags/block/mineable/x.json", "{\"values\":[\"#tfp:a\"]}");
        assertThatThrownBy(() -> ToolBlockTags.run(out)).isInstanceOf(DatagenException.class).hasMessageContaining("cycle");
    }

    /** The item's test on the real checkout: every tool's mineable_tag is a server tag; the gem saw recipe is emitted. */
    @Test
    void realCheckout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        OutputTree tree = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT).tree();
        assertThat(CraftingRecipes.unbuildable(tree)).isEmpty();
        Pattern tag = Pattern.compile("^ +mineable_tag: ([a-z0-9_]+:[a-z0-9_/]+)\\s*$", Pattern.MULTILINE);
        List<String> named = new ArrayList<>();
        tree.files().forEach((p, bytes) -> {
            if (p.startsWith("craftengine/") && p.endsWith(".yml")) {
                Matcher m = tag.matcher(new String(bytes, StandardCharsets.UTF_8));
                while (m.find()) {
                    named.add(m.group(1));
                }
            }
        });
        assertThat(named).contains("tfp:mineable/glass_saw", "tfp:mineable/knife", "tfp:mineable/hammer", "tfp:mineable/scythe");
        named.forEach(t -> assertThat(ToolBlockTags.isServerTag(tree, t)).as(t).isTrue());
        // TFC: knife and scythe = #minecraft:mineable/hoe, hammer = #minecraft:logs
        assertThat(entries(tree, "tfp:mineable/knife")).containsExactly("#minecraft:mineable/hoe");
        assertThat(entries(tree, "tfp:mineable/scythe")).containsExactly("#minecraft:mineable/hoe");
        assertThat(entries(tree, "tfp:mineable/hammer")).containsExactly("#minecraft:logs");
        assertThat(entries(tree, "tfp:mineable/chisel")).isEmpty();
        List<String> saw = entries(tree, "tfp:mineable/glass_saw");
        assertThat(saw).allMatch(id -> id.startsWith("minecraft:") || id.startsWith("#minecraft:"));
        if (tree.contains("data/neoforge_common_tags.json")) { // NeoForge's c:glass_blocks / c:glass_panes
            assertThat(saw).contains("minecraft:glass", "minecraft:tinted_glass", "minecraft:white_stained_glass",
                "minecraft:glass_pane", "minecraft:black_stained_glass_pane");
        }
        JsonObject report = JsonParser.parseString(new String(tree.get(ToolBlockTags.REPORT), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray left = report.getAsJsonObject("tfp:mineable/glass_saw").getAsJsonArray("left_out");
        assertThat(left.toString()).contains("tfp:poured_glass", "tfp:black_poured_glass");
        // the CE recipes now hold the gem saw
        String recipes = new String(tree.get(CraftingRecipes.DIR + "general_recipes.yml"), StandardCharsets.UTF_8);
        assertThat(recipes).contains("tfp:crafting/gem_saw:");
        byte[] meta = tree.get(ToolBlockTags.DATAPACK + "pack.mcmeta");
        assertThat(meta).as("bootstrap datapack pack.mcmeta").isNotNull();
    }
}
