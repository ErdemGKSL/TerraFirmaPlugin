/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.DatagenException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-INFRA-08: spec to YAML goldens, one device per appearance strategy, and the spec checks. */
class DeviceYamlGoldenTest {
    private static final String HEADER = "# " + DeviceYaml.HEADER + "\n";
    private static final BigDecimal ONE = new BigDecimal("1");
    private static final DeviceProperty FACING = DeviceProperty.names("facing", List.of("north", "south"), "north");
    private static final DeviceProperty LIT = DeviceProperty.bool("lit", false);

    /** Repurposed vanilla states, one per facing, placed by its own item. */
    static DeviceBlockSpec repurposed() {
        return DeviceBlockSpec.builder("tfp:test/repurposed")
            .property(FACING)
            .appearance(DeviceAppearance.modelled("north", DeviceAppearance.Carrier.state("minecraft:smoker[facing=north,lit=false]"), "tfp:block/test"))
            .appearance(DeviceAppearance.vanilla("south", DeviceAppearance.Carrier.state("minecraft:smoker[facing=south,lit=false]")))
            .everyState(s -> s.get("facing"), null)
            .settings(ONE, new BigDecimal("2.5"))
            .lootSelf()
            .deviceBehaviour()
            .blockItem("block.tfp.test.repurposed")
            .build();
    }

    /** auto_state pool appearances, one shared by key; the lit state emits light. */
    static DeviceBlockSpec autoState() {
        return DeviceBlockSpec.builder("tfp:test/auto")
            .property(LIT)
            .property(DeviceProperty.range("count", 1, 2, 1))
            .appearance(DeviceAppearance.modelled("idle", DeviceAppearance.Carrier.pool("solid"), "tfp:block/test_idle"))
            .appearance(DeviceAppearance.modelled("burning", DeviceAppearance.Carrier.pool("solid", "tfp:test/auto_burning"), "tfp:block/test_burning"))
            .everyState(s -> "true".equals(s.get("lit")) ? "burning" : "idle", s -> "true".equals(s.get("lit")) ? 15 : 0)
            .settings(ONE, ONE)
            .lootSelf()
            .deviceBehaviour()
            .build();
    }

    /** A transparent carrier, the look made of display entities. */
    static DeviceBlockSpec rendered() {
        return DeviceBlockSpec.builder("tfp:test/rendered")
            .property(LIT)
            .appearance(DeviceAppearance.rendered("unlit", DeviceAppearance.Carrier.state("minecraft:daylight_detector[inverted=false,power=0]"),
                List.of(DeviceAppearance.Renderer.item("tfp:render/test/unlit"))))
            .appearance(DeviceAppearance.rendered("lit", DeviceAppearance.Carrier.state("minecraft:daylight_detector[inverted=false,power=0]"),
                List.of(DeviceAppearance.Renderer.item("tfp:render/test/lit").scaled(new BigDecimal("0.5"))
                    .translated("0,0.25,0").rotated("0,90,0").lit(15, 15))))
            .everyState(s -> "true".equals(s.get("lit")) ? "lit" : "unlit", null)
            .settings(new BigDecimal("2"), new BigDecimal("2"))
            .lootSelf()
            .deviceBehaviour()
            .renderItem("tfp:render/test/unlit", "tfp:block/test/unlit")
            .renderItem("tfp:render/test/lit", "tfp:block/test/lit")
            .build();
    }

    @Test
    void repurposedStateGolden() {
        DeviceBlockSpec spec = repurposed();
        assertThat(spec.strategies()).containsExactly(DeviceAppearance.Strategy.REPURPOSED_STATE);
        assertThat(DeviceYaml.writeLiteral(spec)).isEqualTo(HEADER + """
            blocks:
              tfp:test/repurposed:
                settings:
                  template: tfp:settings/device
                  arguments:
                    device_hardness: 1.0
                    device_resistance: 2.5
                loot:
                  template: tfp:loot_table/self
                behavior:
                  type: tfp:device
                states:
                  properties:
                    facing:
                      type: string
                      values:
                        - north
                        - south
                      default: north
                  appearances:
                    north:
                      state: 'minecraft:smoker[facing=north,lit=false]'
                      model: tfp:block/test
                    south:
                      state: 'minecraft:smoker[facing=south,lit=false]'
                  variants:
                    facing=north:
                      appearance: north
                    facing=south:
                      appearance: south
            items:
              tfp:test/repurposed:
                template: tfp:item/block_item
                arguments:
                  item_lang_key: block.tfp.test.repurposed
            """);
        assertThat(DeviceYaml.writeTemplated(spec)).isEqualTo(HEADER + """
            blocks:
              tfp:test/repurposed:
                template: tfp:block/device
                arguments:
                  device_hardness: 1.0
                  device_resistance: 2.5
                states:
                  properties:
                    facing:
                      type: string
                      values:
                        - north
                        - south
                      default: north
                  appearances:
                    north:
                      state: 'minecraft:smoker[facing=north,lit=false]'
                      model: tfp:block/test
                    south:
                      state: 'minecraft:smoker[facing=south,lit=false]'
                  variants:
                    facing=north:
                      appearance: north
                    facing=south:
                      appearance: south
            items:
              tfp:test/repurposed:
                template: tfp:item/block_item
                arguments:
                  item_lang_key: block.tfp.test.repurposed
            """);
    }

    @Test
    void autoStateGolden() {
        DeviceBlockSpec spec = autoState();
        assertThat(spec.strategies()).containsExactly(DeviceAppearance.Strategy.AUTO_STATE);
        assertThat(spec.stateCount()).isEqualTo(4);
        assertThat(DeviceYaml.writeTemplated(spec)).isEqualTo(HEADER + """
            blocks:
              tfp:test/auto:
                template: tfp:block/device
                arguments:
                  device_hardness: 1.0
                  device_resistance: 1.0
                states:
                  properties:
                    lit:
                      type: boolean
                      default: false
                    count:
                      type: int
                      range: '1~2'
                      default: 1
                  appearances:
                    idle:
                      auto_state: solid
                      model: tfp:block/test_idle
                    burning:
                      auto_state:
                        type: solid
                        id: tfp:test/auto_burning
                      model: tfp:block/test_burning
                  variants:
                    'lit=false,count=1':
                      appearance: idle
                      settings:
                        luminance: 0
                    'lit=false,count=2':
                      appearance: idle
                      settings:
                        luminance: 0
                    'lit=true,count=1':
                      appearance: burning
                      settings:
                        luminance: 15
                    'lit=true,count=2':
                      appearance: burning
                      settings:
                        luminance: 15
            """);
    }

    @Test
    void entityRendererGolden() {
        DeviceBlockSpec spec = rendered();
        assertThat(spec.strategies()).containsExactly(DeviceAppearance.Strategy.ENTITY_RENDERER);
        assertThat(DeviceYaml.writeTemplated(spec)).isEqualTo(HEADER + """
            blocks:
              tfp:test/rendered:
                template: tfp:block/device_rendered
                arguments:
                  device_hardness: 2.0
                  device_resistance: 2.0
                states:
                  properties:
                    lit:
                      type: boolean
                      default: false
                  appearances:
                    unlit:
                      state: 'minecraft:daylight_detector[inverted=false,power=0]'
                      transparent: true
                      entity_renderer:
                        - item: tfp:render/test/unlit
                    lit:
                      state: 'minecraft:daylight_detector[inverted=false,power=0]'
                      transparent: true
                      entity_renderer:
                        - item: tfp:render/test/lit
                          scale: 0.5
                          translation: '0,0.25,0'
                          rotation: '0,90,0'
                          brightness:
                            block_light: 15
                            sky_light: 15
                  variants:
                    lit=false:
                      appearance: unlit
                    lit=true:
                      appearance: lit
            items:
              tfp:render/test/unlit:
                template: tfp:item/render
                arguments:
                  render_model: tfp:block/test/unlit
              tfp:render/test/lit:
                template: tfp:item/render
                arguments:
                  render_model: tfp:block/test/lit
            """);
    }

    @Test
    void everyGoldenIsStrictYaml() {
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        for (DeviceBlockSpec spec : List.of(repurposed(), autoState(), rendered())) {
            for (String text : List.of(DeviceYaml.writeLiteral(spec), DeviceYaml.writeTemplated(spec))) {
                assertThat(text).doesNotContain("\t");
                Map<?, ?> root = new Yaml(opts).load(text);
                assertThat(List.copyOf(((Map<?, ?>) root.get("blocks")).keySet())).isEqualTo(List.of(spec.id()));
            }
        }
    }

    @Test
    void variantsMustNameEveryPropertyWithAValidValue() {
        var b = DeviceBlockSpec.builder("tfp:test/bad").property(LIT).property(FACING)
            .appearance(DeviceAppearance.vanilla("a", DeviceAppearance.Carrier.state("minecraft:stone")));
        // Only lit is named: CraftEngine would read facing as a wildcard.
        assertThatThrownBy(() -> new DeviceBlockSpec("tfp:test/bad", List.of(LIT, FACING),
            List.of(DeviceAppearance.vanilla("a", DeviceAppearance.Carrier.state("minecraft:stone"))),
            List.of(new DeviceVariant(Map.of("lit", "false"), "a", null)), null, false, false, null, Map.of(), Map.of()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("must name exactly the properties");
        assertThatThrownBy(() -> new DeviceBlockSpec("tfp:test/bad", List.of(LIT),
            List.of(DeviceAppearance.vanilla("a", DeviceAppearance.Carrier.state("minecraft:stone"))),
            List.of(new DeviceVariant(Map.of("lit", "maybe"), "a", null)), null, false, false, null, Map.of(), Map.of()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("no valid value for lit");
        assertThat(b.everyState(s -> "a", null).build().stateCount()).isEqualTo(4);
    }

    @Test
    void everyStateHasExactlyOneVariant() {
        DeviceAppearance a = DeviceAppearance.vanilla("a", DeviceAppearance.Carrier.state("minecraft:stone"));
        assertThatThrownBy(() -> new DeviceBlockSpec("tfp:test/bad", List.of(LIT), List.of(a),
            List.of(new DeviceVariant(Map.of("lit", "false"), "a", null)), null, false, false, null, Map.of(), Map.of()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("1 variants for 2 server states");
        assertThatThrownBy(() -> new DeviceBlockSpec("tfp:test/bad", List.of(LIT), List.of(a),
            List.of(new DeviceVariant(Map.of("lit", "false"), "a", null), new DeviceVariant(Map.of("lit", "false"), "a", null)),
            null, false, false, null, Map.of(), Map.of()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("two variants");
    }

    @Test
    void appearancesAndRenderItemsAreChecked() {
        var stone = DeviceAppearance.Carrier.state("minecraft:stone");
        assertThatThrownBy(() -> DeviceBlockSpec.builder("tfp:test/bad").property(LIT)
            .appearance(DeviceAppearance.vanilla("a", stone)).everyState(s -> "b", null).build())
            .isInstanceOf(DatagenException.class).hasMessageContaining("undeclared appearance b");
        assertThatThrownBy(() -> DeviceBlockSpec.builder("tfp:test/bad").property(LIT)
            .appearance(DeviceAppearance.vanilla("a", stone)).appearance(DeviceAppearance.vanilla("b", stone))
            .everyState(s -> "a", null).build())
            .isInstanceOf(DatagenException.class).hasMessageContaining("appearance b is used by no variant");
        assertThatThrownBy(() -> DeviceBlockSpec.builder("tfp:test/bad").property(LIT)
            .appearance(DeviceAppearance.rendered("a", stone, List.of(DeviceAppearance.Renderer.item("tfp:render/x"))))
            .everyState(s -> "a", null).build())
            .isInstanceOf(DatagenException.class).hasMessageContaining("does not declare as a render item");
        assertThatThrownBy(() -> DeviceAppearance.rendered("a", stone, List.of()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("draws nothing");
        assertThatThrownBy(() -> DeviceAppearance.vanilla("a", DeviceAppearance.Carrier.pool("solid")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("auto_state without a model");
        assertThatThrownBy(() -> DeviceAppearance.Carrier.state("minecraft:smoker[facing=north"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("not a full vanilla block state");
        assertThatThrownBy(() -> new DeviceVariant(Map.of("lit", "true"), "a", 16))
            .isInstanceOf(DatagenException.class).hasMessageContaining("outside 0 to 15");
    }

    @Test
    void propertiesAreChecked() {
        assertThat(DeviceProperty.range("smoke_level", 0, 4, 0).values()).containsExactly("0", "1", "2", "3", "4");
        assertThatThrownBy(() -> DeviceProperty.range("x", 0, 4, 5)).isInstanceOf(DatagenException.class);
        assertThatThrownBy(() -> DeviceProperty.range("x", 3, 1, 3)).isInstanceOf(DatagenException.class);
        assertThatThrownBy(() -> DeviceProperty.names("x", List.of("a", "a"), "a")).isInstanceOf(DatagenException.class);
        assertThatThrownBy(() -> DeviceProperty.names("x", List.of("A"), "A")).isInstanceOf(DatagenException.class);
    }

    @Test
    void theTemplatesOnlyFitSpecsWithSettingsLootAndBehaviour() {
        DeviceBlockSpec placeholder = DeviceSpecs.firepitFamily("tfp:test/firepit", List.of(LIT),
            DeviceAppearance.Carrier.state("minecraft:campfire[lit=true]"), DeviceAppearance.Carrier.state("minecraft:campfire[lit=false]"));
        assertThatThrownBy(() -> DeviceYaml.templated(placeholder)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("write it literally");
        assertThat(DeviceYaml.literal(placeholder).toString()).doesNotContain("settings", "loot", "behavior");
    }
}
