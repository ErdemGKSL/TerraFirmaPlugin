/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.gui.GuiImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-07: the Paper layout ({@link TabLayout}, {@link TabFont}) and the datagen output ({@code tabs/PlayerTabArt})
 * must agree: every image id with its ascent and height, every line font with its ascent, and the accented cells.
 * Reads {@code content/generated} (skipped when datagen has not run).
 */
class PlayerTabLayoutConsistencyTest {
    private static Path pack;
    private static String yaml;

    @BeforeAll
    static void load() throws IOException {
        String data = System.getProperty("tfp.contentData");
        Assumptions.assumeTrue(data != null, "no tfp.contentData");
        pack = Path.of(data).getParent().resolve("craftengine/tfp");
        Path config = pack.resolve("configuration/player_tabs.yml");
        Assumptions.assumeTrue(Files.isRegularFile(config), "datagen has not run: " + config);
        yaml = Files.readString(config);
    }

    /** The {@code height} and {@code ascent} of {@code id} in the YAML (the generator writes one key per line). */
    private static int[] entry(String id) {
        Matcher m = Pattern.compile("(?m)^  '?" + Pattern.quote(id) + "'?:\\n((?:    .*\\n)+)").matcher(yaml);
        assertThat(m.find()).as(id + " in player_tabs.yml").isTrue();
        String body = m.group(1);
        return new int[] {value(body, "height"), value(body, "ascent")};
    }

    private static int value(String body, String key) {
        Matcher m = Pattern.compile("(?m)^    " + key + ": (-?\\d+)$").matcher(body);
        assertThat(m.find()).as(key).isTrue();
        return Integer.parseInt(m.group(1));
    }

    @Test
    void everyImageMatches() {
        for (GuiImage i : TabLayout.images()) {
            assertThat(entry(i.id())).as(i.id()).containsExactly(i.height(), i.ascent());
            assertThat(Files.isRegularFile(pack.resolve("resourcepack/assets/tfp/" + i.packPath()))).as(i.id()).isTrue();
        }
        int[] inv = entry(TabLayout.INVENTORY_TABS);
        assertThat(inv).containsExactly(TabLayout.INVENTORY_TABS_HEIGHT, 13 - TabLayout.INVENTORY_TABS_Y);
        assertThat(yaml).contains("<shift:" + (TabLayout.INVENTORY_TABS_X - TabLayout.INVENTORY_TITLE_X) + "><image:"
            + TabLayout.INVENTORY_TABS + ">");
    }

    @Test
    void everyLineFontMatches() throws IOException {
        try (var files = Files.list(pack.resolve("resourcepack/assets/tfp/font/tabs"))) {
            assertThat(files.map(p -> p.getFileName().toString()).sorted().toList())
                .containsExactlyInAnyOrderElementsOf(TabFont.LINES.stream().map(y -> "line_" + y + ".json").toList());
        }
        for (int y : TabFont.LINES) {
            JsonObject f = JsonParser.parseString(Files.readString(pack.resolve("resourcepack/assets/tfp/font/tabs/line_" + y
                + ".json"))).getAsJsonObject();
            JsonArray p = f.getAsJsonArray("providers");
            assertThat(p.get(1).getAsJsonObject().get("ascent").getAsInt()).isEqualTo(7 - (y - TabFont.TITLE_Y));
            JsonArray acc = p.get(2).getAsJsonObject().getAsJsonArray("chars");
            for (Object[] a : TabFont.ACCENTED) {
                assertThat(acc.get((Integer) a[2]).getAsString().charAt((Integer) a[3])).isEqualTo(a[0]);
            }
            JsonArray ascii = p.get(1).getAsJsonObject().getAsJsonArray("chars");
            StringBuilder all = new StringBuilder();
            ascii.forEach(r -> all.append(r.getAsString()));
            for (char c = ' '; c < '~'; c++) {
                assertThat(TabFont.supported(c)).isTrue();
                assertThat(all.indexOf(String.valueOf(c))).as("'" + c + "'").isNotNegative();
            }
        }
        assertThat(Map.of("°", TabFont.supported('°'))).containsEntry("°", true);
    }
}
