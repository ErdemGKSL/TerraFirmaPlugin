/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-33: the volume and pitch of every device sound event, the vanilla client's multipliers on the TFC
 * {@code SoundType} (STONE, WOOD, NETHER_WART and TFC's own types are (1.0, 1.0), METAL is (1.0, 1.5)), and the YAML
 * form: plain ids where CraftEngine's defaults already are those values, {@code {id, volume, pitch}} otherwise.
 */
class DeviceSoundVolumeTest {
    /** break/place (v+1)/2, p*0.8; step v*0.15, p; hit (v+1)/8, p*0.5; fall v*0.5, p*0.75 at (1.0, 1.0). */
    private static final Map<String, List<BigDecimal>> UNIT = table(
        "1.0", "0.8", "0.15", "1.0", "1.0", "0.8", "0.25", "0.5", "0.5", "0.75");
    /** The same at (1.0, 1.5). */
    private static final Map<String, List<BigDecimal>> METAL = table(
        "1.0", "1.2", "0.15", "1.5", "1.0", "1.2", "0.25", "0.75", "0.5", "1.125");

    private static Map<String, List<BigDecimal>> table(String... values) {
        Map<String, List<BigDecimal>> m = new LinkedHashMap<>();
        String[] events = {"break", "step", "place", "hit", "fall"};
        for (int i = 0; i < events.length; i++) {
            m.put(events[i], List.of(new BigDecimal(values[2 * i]), new BigDecimal(values[2 * i + 1])));
        }
        return m;
    }

    @Test
    void stoneWoodNetherWartAndTfcTypesPlayAtTheUnitSoundType() {
        assertThat(DeviceSound.STONE.values()).isEqualTo(UNIT);
        assertThat(DeviceSound.WOOD.values()).isEqualTo(UNIT);
        assertThat(DeviceSound.NETHER_WART.values()).isEqualTo(UNIT);
        assertThat(DeviceSound.tfc("charcoal").values()).isEqualTo(UNIT);
        assertThat(DeviceSound.ofJava("TFCSounds.CHARCOAL").values()).isEqualTo(UNIT);
    }

    @Test
    void metalPlaysAtItsOwnPitch() {
        assertThat(DeviceSound.METAL.values()).isEqualTo(METAL);
    }

    @Test
    void unitTypesStayPlainIdsBecauseCraftEngineDefaultsMatch() {
        // CraftEngine 26.9.2 BlockSounds defaults for a plain id: break/place 1.0/0.8, step 0.15/1.0, fall 0.5/0.75.
        for (DeviceSound s : List.of(DeviceSound.STONE, DeviceSound.WOOD, DeviceSound.NETHER_WART, DeviceSound.tfc("charcoal"))) {
            assertThat(s.plainIds()).as(s.breakSound()).isTrue();
            assertThat(s.yaml().values()).allSatisfy(v -> assertThat(v).isInstanceOf(String.class));
        }
        assertThat(DeviceSound.NETHER_WART.yaml()).containsExactly(
            Map.entry("break", "minecraft:block.nether_wart.break"), Map.entry("step", "minecraft:block.stone.step"),
            Map.entry("place", "minecraft:item.nether_wart.plant"), Map.entry("hit", "minecraft:block.stone.hit"),
            Map.entry("fall", "minecraft:block.stone.fall"));
    }

    @Test
    void metalWritesIdVolumeAndPitchPerEvent() {
        assertThat(DeviceSound.METAL.plainIds()).isFalse();
        DeviceBlockSpec spec = DeviceBlockSpec.builder("tfp:test/metal")
            .appearance(DeviceAppearance.vanilla("only", DeviceAppearance.Carrier.state("minecraft:iron_block")))
            .everyState(s -> "only", null)
            .settings(DeviceBlockSpec.Settings.of("5", "6", DeviceSound.METAL, "pickaxe"))
            .build();
        assertThat(DeviceYaml.writeLiteral(spec)).contains("""
                  sounds:
                    break:
                      id: minecraft:block.metal.break
                      volume: 1.0
                      pitch: 1.2
                    step:
                      id: minecraft:block.metal.step
                      volume: 0.15
                      pitch: 1.5
                    place:
                      id: minecraft:block.metal.place
                      volume: 1.0
                      pitch: 1.2
                    hit:
                      id: minecraft:block.metal.hit
                      volume: 0.25
                      pitch: 0.75
                    fall:
                      id: minecraft:block.metal.fall
                      volume: 0.5
                      pitch: 1.125
            """);
    }
}
