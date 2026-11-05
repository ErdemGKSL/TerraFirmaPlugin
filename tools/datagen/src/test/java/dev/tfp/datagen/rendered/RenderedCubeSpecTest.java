/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.rendered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceSound;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-17: renderer cube templates, destroy stages, the spec golden and the spike S2 config. */
class RenderedCubeSpecTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z_]+)(:-[^}]*)?}");

    static RenderedCubeSpec copper() {
        return RenderedCubeSpec.of("tfp:test/copper", "tfp:block/test/copper",
            DeviceBlockSpec.Settings.of("5.0", "6.0", DeviceSound.METAL, "pickaxe"), true, "block.tfp.test.copper");
    }

    @Test
    void cubeGolden() {
        assertThat(copper().write("h")).isEqualTo("""
            # h
            blocks:
              tfp:test/copper:
                template: tfp:block/rendered_cube
                arguments:
                  rendered_hardness: 5.0
                  rendered_resistance: 6.0
                  rendered_sound_break: minecraft:block.metal.break
                  rendered_sound_break_volume: 1.0
                  rendered_sound_break_pitch: 1.2
                  rendered_sound_step: minecraft:block.metal.step
                  rendered_sound_step_volume: 0.15
                  rendered_sound_step_pitch: 1.5
                  rendered_sound_place: minecraft:block.metal.place
                  rendered_sound_place_volume: 1.0
                  rendered_sound_place_pitch: 1.2
                  rendered_sound_hit: minecraft:block.metal.hit
                  rendered_sound_hit_volume: 0.25
                  rendered_sound_hit_pitch: 0.75
                  rendered_sound_fall: minecraft:block.metal.fall
                  rendered_sound_fall_volume: 0.5
                  rendered_sound_fall_pitch: 1.125
                  rendered_tool_tag: minecraft:mineable/pickaxe
                  rendered_item: tfp:render/test/copper
                  rendered_particle_item: tfp:render/test/copper
                settings:
                  correct_tools:
                    - '#minecraft:pickaxes'
                    - '#tfp:tools/pickaxe'
                loot:
                  template: tfp:loot_table/self
            items:
              tfp:test/copper:
                template: tfp:item/block_item
                arguments:
                  item_lang_key: block.tfp.test.copper
              tfp:render/test/copper:
                template: tfp:item/render
                arguments:
                  render_model: tfp:block/test/copper
            """);
        RenderedCubeSpec crate = RenderedCubeSpec.of("tfp:test/crate", "tfp:block/test/crate",
            DeviceBlockSpec.Settings.of("2", "2", DeviceSound.WOOD, "axe"), false, "block.tfp.test.crate");
        assertThat(crate.yaml().get("blocks").toString()).doesNotContain("correct_tools");
    }

    @Test
    void templateGolden() {
        assertThat(Yaml.write("h", RenderedTemplates.yaml())).isEqualTo("""
            # h
            'templates#rendered_block':
              tfp:settings/destroy_stages:
                destroy_stages:
                  items:
                    - tfp:render/destroy_stage/0
                    - tfp:render/destroy_stage/1
                    - tfp:render/destroy_stage/2
                    - tfp:render/destroy_stage/3
                    - tfp:render/destroy_stage/4
                    - tfp:render/destroy_stage/5
                    - tfp:render/destroy_stage/6
                    - tfp:render/destroy_stage/7
                    - tfp:render/destroy_stage/8
                    - tfp:render/destroy_stage/9
                  scale: 1.01
                  view_range: 0.75
              tfp:block/rendered_cube:
                settings:
                  hardness: '${rendered_hardness}'
                  resistance: '${rendered_resistance}'
                  sounds:
                    break:
                      id: '${rendered_sound_break}'
                      volume: '${rendered_sound_break_volume}'
                      pitch: '${rendered_sound_break_pitch}'
                    step:
                      id: '${rendered_sound_step}'
                      volume: '${rendered_sound_step_volume}'
                      pitch: '${rendered_sound_step_pitch}'
                    place:
                      id: '${rendered_sound_place}'
                      volume: '${rendered_sound_place_volume}'
                      pitch: '${rendered_sound_place_pitch}'
                    hit:
                      id: '${rendered_sound_hit}'
                      volume: '${rendered_sound_hit_volume}'
                      pitch: '${rendered_sound_hit_pitch}'
                    fall:
                      id: '${rendered_sound_fall}'
                      volume: '${rendered_sound_fall_volume}'
                      pitch: '${rendered_sound_fall_pitch}'
                  tags:
                    - '${rendered_tool_tag}'
                  destroy_stages:
                    items:
                      - tfp:render/destroy_stage/0
                      - tfp:render/destroy_stage/1
                      - tfp:render/destroy_stage/2
                      - tfp:render/destroy_stage/3
                      - tfp:render/destroy_stage/4
                      - tfp:render/destroy_stage/5
                      - tfp:render/destroy_stage/6
                      - tfp:render/destroy_stage/7
                      - tfp:render/destroy_stage/8
                      - tfp:render/destroy_stage/9
                    scale: 1.01
                    view_range: 0.75
                state:
                  state: minecraft:barrier
                  entity_renderer:
                    - item: '${rendered_item}'
                      view_range: '${rendered_view_range:-4.0}'
                events:
                  - 'on': break
                    functions:
                      - type: particle
                        particle: minecraft:item
                        item: '${rendered_particle_item}'
                        count: 64
                        offset_x: 0.25
                        offset_y: 0.25
                        offset_z: 0.25
                        speed: 0.05
            """);
    }

    @Test
    void destroyStageItemsUseTheVanillaCrackTexturesInOrder() {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> items = (Map<String, Map<String, Object>>) RenderedTemplates.itemsYaml().get("items");
        assertThat(items.keySet()).containsExactly(stageIds().toArray(String[]::new));
        int n = 0;
        for (var item : items.values()) {
            assertThat(item.toString()).contains("all=minecraft:block/destroy_stage_" + n + "}")
                .contains("parent=minecraft:block/cube_all");
            n++;
        }
        assertThatThrownBy(() -> RenderedTemplates.crackTexture(10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedFilesReferenceTheCrackTexturesAndCopyNoPng() {
        OutputTree out = new OutputTree();
        RenderedTemplates.run(out);
        var files = out.files();
        assertThat(files.keySet()).allMatch(p -> !p.endsWith(".png"));
        assertThat(files).containsKeys(RenderedTemplates.OUTPUT, RenderedTemplates.ITEMS_OUTPUT);
        String items = new String(files.get(RenderedTemplates.ITEMS_OUTPUT), StandardCharsets.UTF_8);
        for (int n = 0; n < 10; n++) {
            assertThat(items).contains("all: minecraft:block/destroy_stage_" + n + "\n");
            String quad = new String(files.get(RenderedTemplates.MODELS + "item/render/overlay_crack_" + n + ".json"),
                StandardCharsets.UTF_8);
            assertThat(quad).contains("\"crack\": \"minecraft:block/destroy_stage_" + n + "\"");
            com.google.gson.JsonParser.parseString(quad);
        }
        assertThat(new String(files.get(RenderedTemplates.OUTPUT), StandardCharsets.UTF_8)).startsWith("# Generated by tools/datagen");
    }

    @Test
    void callsGiveEveryArgumentTheTemplateNeeds() {
        String body = Yaml.write("h", RenderedTemplates.yaml());
        Set<String> required = new HashSet<>();
        Matcher m = PLACEHOLDER.matcher(body);
        while (m.find()) {
            if (m.group(2) == null) {
                required.add(m.group(1));
            }
        }
        assertThat(required).hasSize(20);
        assertThat(copper().arguments().keySet()).containsExactlyInAnyOrderElementsOf(required);
    }

    /** Vanilla's client multipliers over the SoundType volume/pitch; METAL is (1.0, 1.5), TFC's own types (1.0, 1.0). */
    @Test
    void soundVolumeAndPitchAreTheVanillaClients() {
        assertThat(RenderedSound.of(DeviceSound.METAL).table().toString())
            .isEqualTo("{break=[1.0, 1.2], step=[0.15, 1.5], place=[1.0, 1.2], hit=[0.25, 0.75], fall=[0.5, 1.125]}");
        for (DeviceSound s : List.of(DeviceSound.STONE, DeviceSound.WOOD, DeviceSound.NETHER_WART, DeviceSound.tfc("charcoal"))) {
            assertThat(RenderedSound.of(s).table().toString())
                .isEqualTo("{break=[1.0, 0.8], step=[0.15, 1.0], place=[1.0, 0.8], hit=[0.25, 0.5], fall=[0.5, 0.75]}");
        }
        assertThatThrownBy(() -> RenderedSound.of(new DeviceSound("minecraft:block.glass.break", "minecraft:block.glass.step",
            "minecraft:block.glass.place", "minecraft:block.glass.hit", "minecraft:block.glass.fall")))
            .isInstanceOf(DatagenException.class);
    }

    @Test
    void rejectsBadIds() {
        assertThatThrownBy(() -> RenderedCubeSpec.of("copper", "tfp:block/x",
            DeviceBlockSpec.Settings.of("1", "1", DeviceSound.METAL, "pickaxe"), true, "k")).isInstanceOf(DatagenException.class);
        assertThatThrownBy(() -> new RenderedCubeSpec("tfp:x", "tfp:render/x", "tfp:block/x",
            DeviceBlockSpec.Settings.of("1", "1", DeviceSound.METAL, "pickaxe"), true, "tfp:render/x", " "))
            .isInstanceOf(DatagenException.class);
    }

    @Test
    void crackedOverlayVariantsComposeTheOverlayWithEachStage() {
        Map<String, Object> v = CrackedOverlays.variants("tfp:ore_overlay/normal_native_gold", "tfp:item/ore_overlay/normal_native_gold");
        assertThat(v).hasSize(10);
        int n = 0;
        for (var e : v.entrySet()) {
            assertThat(e.getKey()).isEqualTo("tfp:ore_overlay/normal_native_gold/crack_" + n);
            assertThat(e.getValue().toString()).contains("type=minecraft:composite",
                "path=tfp:item/ore_overlay/normal_native_gold}", "path=tfp:item/render/overlay_crack_" + n + "}");
            n++;
        }
        assertThatThrownBy(() -> CrackedOverlays.variantItem("tfc:x", 0)).isInstanceOf(DatagenException.class);
    }

    /** Real checkout: TFC's metal block settings, the 53 overlays with 10 cracked variants each, the spike config. */
    @Test
    void realCheckout() throws IOException {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("not at the pinned commit: " + e.getMessage());
            return;
        }
        // Metal.BlockType.blockProperties: strength(5.0F, 6.0F).sound(SoundType.METAL), mineable/pickaxe.
        assertThat(RenderedCubeSpec.tfcMetalBlock(tfc, "copper"))
            .isEqualTo(DeviceBlockSpec.Settings.of("5.0", "6.0", DeviceSound.METAL, "pickaxe"));
        List<String> overlays = CrackedOverlays.tfcOverlays(tfc);
        assertThat(overlays).hasSize(53).contains("normal_native_gold", "rich_malachite", "amethyst");
        List<String> variants = new ArrayList<>();
        for (String o : overlays) {
            variants.addAll(CrackedOverlays.variants("tfp:ore_overlay/" + o, "tfp:item/ore_overlay/" + o).keySet());
        }
        assertThat(variants).hasSize(530).doesNotHaveDuplicates();

        String expected = S2Spike.text(tfc);
        Path repo = Path.of(System.getProperty("tfp.repoRoot", "../.."));
        Path file = repo.resolve(S2Spike.FILE);
        String actual = Files.isRegularFile(file) ? Files.readString(file) : "";
        if (!actual.equals(expected)) {
            Path build = repo.resolve("tools/datagen/build/s2/s2.yml");
            Files.createDirectories(build.getParent());
            Files.writeString(build, expected);
            throw new AssertionError(S2Spike.FILE + " is not the generator's output; the expected text is in " + build
                + " (copy it over)");
        }
        assertThat(expected).contains("rendered_hardness: 5.0", "minecraft:block.metal.break", "rendered_sound_break_pitch: 1.2",
            "rendered_sound_hit_volume: 0.25", "'#minecraft:pickaxes'");
    }

    private static List<String> stageIds() {
        List<String> ids = new ArrayList<>();
        for (int n = 0; n < 10; n++) {
            ids.add("tfp:render/destroy_stage/" + n);
        }
        return ids;
    }
}
