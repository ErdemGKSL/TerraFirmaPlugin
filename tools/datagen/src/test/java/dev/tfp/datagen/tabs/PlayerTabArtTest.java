/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.tabs;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-SURV-07: the player tab art, fonts and lang override from the real TFC checkout. */
class PlayerTabArtTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int count;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        out = new OutputTree();
        assertThat(PlayerTabArt.available(tfc)).isTrue();
        count = PlayerTabArt.run(tfc, out);
    }

    private static BufferedImage png(GuiImage image) {
        byte[] b = out.get(PlayerTabArt.RP + image.packPath());
        assertThat(b).as(image.id()).isNotNull();
        try {
            return ImageIO.read(new ByteArrayInputStream(b));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> config() {
        return new Yaml().load(new String(out.get(PlayerTabArt.CONFIG), StandardCharsets.UTF_8));
    }

    @Test
    void everyImageIsPresentAtItsSizeWithItsYamlEntry() {
        assertThat(count).isEqualTo(PlayerTabArt.images().size() + 1); // + the inventory tab column
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> images = (Map<String, Map<String, Object>>) config().get("images");
        for (GuiImage i : PlayerTabArt.images()) {
            BufferedImage img = png(i);
            assertThat(img.getWidth()).as(i.id()).isEqualTo(i.pngWidth());
            assertThat(img.getHeight()).as(i.id()).isEqualTo(i.pngHeight());
            assertThat(images.get(i.id())).as(i.id()).containsEntry("ascent", i.ascent()).containsEntry("height", i.height())
                .containsEntry("font", GuiImage.FONT);
        }
        assertThat(images.get(PlayerTabArt.INVENTORY_TABS)).containsEntry("font", "minecraft:default")
            .containsEntry("ascent", 9).containsEntry("height", 91);
        assertThat(png(PlayerTabArt.inventoryTabs()).getWidth()).isEqualTo(25);
    }

    @Test
    void backgroundsKeepTfcArtAboveAndMoveTheInventoryDown19() throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(tfc.read(PlayerTabArt.GUI + "player_nutrition.png")));
        BufferedImage bg = png(PlayerTabArt.background("nutrition"));
        assertThat(bg.getHeight()).isEqualTo(186);
        for (int y = 4; y < 70; y++) { // the bars and icons stay
            for (int x = 0; x < 176; x++) {
                assertThat(bg.getRGB(x, y)).as("(%d,%d)", x, y).isEqualTo(src.getRGB(x, y));
            }
        }
        for (int y = 83; y < 162; y++) { // the player slot frames move to the chest's (84 -> 103)
            assertThat(bg.getRGB(30, y + 19)).as("y %d", y).isEqualTo(src.getRGB(30, y));
        }
        int panel = src.getRGB(88, 75);
        for (int y = 71; y < 90; y++) { // the tab row is plain panel under the button art
            assertThat(bg.getRGB(88, y)).isEqualTo(panel);
        }
    }

    @Test
    void buttonsHaveTwoFramesWithTheTfcIcons() throws IOException {
        BufferedImage icons = ImageIO.read(new ByteArrayInputStream(tfc.read(PlayerTabArt.ICONS)));
        for (int t = 0; t < 4; t++) {
            BufferedImage b = png(PlayerTabArt.button(t));
            assertThat(b.getHeight()).isEqualTo(36);
            for (int frame = 0; frame < 2; frame++) {
                assertThat(b.getRGB(4, frame * 18) >>> 24).as("closed top edge").isNotZero();
                assertThat(b.getRGB(0, frame * 18 + 9) >>> 24).as("closed left edge").isNotZero();
                assertThat(b.getRGB(9, frame * 18 + 17) >>> 24).as("closed bottom edge").isNotZero();
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int c = icons.getRGB(16 * t + x, y);
                        if ((c >>> 24) == 255) {
                            assertThat(b.getRGB(1 + x, frame * 18 + 1 + y)).isEqualTo(c);
                        }
                    }
                }
            }
            int differ = 0;
            for (int y = 0; y < 18; y++) {
                for (int x = 0; x < 18; x++) {
                    differ += b.getRGB(x, y) != b.getRGB(x, y + 18) ? 1 : 0;
                }
            }
            assertThat(differ).as("inactive and active frames differ").isGreaterThan(20);
        }
    }

    @Test
    void everyFrameAdvancesItsFullWidth() {
        java.util.List<GuiImage> all = new java.util.ArrayList<>(PlayerTabArt.images());
        all.add(PlayerTabArt.inventoryTabs());
        for (GuiImage i : all) {
            BufferedImage img = png(i);
            for (int f = 0; f < i.frames(); f++) {
                int x = i.frameCol(f) * i.width() + i.width() - 1;
                boolean any = false;
                for (int y = i.frameRow(f) * i.height(); y < (i.frameRow(f) + 1) * i.height(); y++) {
                    any |= (img.getRGB(x, y) >>> 24) != 0;
                }
                assertThat(any).as(i.id() + " frame " + f + ": the client would measure a narrower glyph").isTrue();
            }
        }
    }

    @Test
    void barFrameKIsKPixelsWide() {
        BufferedImage b = png(PlayerTabArt.bar(2));
        assertThat(b.getHeight()).isEqualTo(94 * 5);
        for (int k : new int[] {0, 1, 50, 93}) {
            int opaque = 0;
            for (int x = 0; x < 93; x++) {
                if ((b.getRGB(x, k * 5 + 2) >>> 24) != 0) {
                    opaque++;
                }
            }
            assertThat(opaque).as("frame %d", k).isEqualTo(k);
        }
    }

    @Test
    void lineFontsLowerTheVanillaSheets() {
        for (int y : PlayerTabArt.LINES) {
            JsonObject font = Json.parse(new String(out.get(PlayerTabArt.FONTS + "line_" + y + ".json"), StandardCharsets.UTF_8),
                "font").getAsJsonObject();
            JsonArray p = font.getAsJsonArray("providers");
            assertThat(p.get(0).getAsJsonObject().get("type").getAsString()).isEqualTo("space");
            JsonObject ascii = p.get(1).getAsJsonObject();
            assertThat(ascii.get("ascent").getAsInt()).isEqualTo(7 - (y - 6));
            assertThat(ascii.getAsJsonArray("chars")).hasSize(16);
            ascii.getAsJsonArray("chars").forEach(r -> assertThat(r.getAsString()).hasSize(16));
            JsonObject acc = p.get(2).getAsJsonObject();
            assertThat(acc.get("ascent").getAsInt()).isEqualTo(10 - (y - 6));
            assertThat(acc.getAsJsonArray("chars")).hasSize(75);
            assertThat(acc.getAsJsonArray("chars").get(9).getAsString()).startsWith("Şş");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theInventoryTitleDrawsTheTabColumnAtX173() {
        Map<String, Object> lang = (Map<String, Object>) ((Map<String, Object>) config().get("lang")).get("all");
        assertThat(lang).containsEntry("container.crafting", "<white><shift:76><image:tfp:gui/inventory_tabs>");
    }
}
