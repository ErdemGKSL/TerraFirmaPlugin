/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCSounds.java @ c3b4d9536 (registerBlock: block.<name>.break/step/place/hit/fall)
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The five sound events of a device block (PAR-INFRA-08), as a Minecraft {@code SoundType} names them. Vanilla
 * {@code SoundType}s map to their vanilla events (note {@code NETHER_WART}, TFC's firepit sound, mixes nether wart and
 * stone events); a TFC {@code TFCSounds.X} type maps to {@code tfp:block.x.*}, the renamed TFC events, which the pack
 * must define (ledger divergence until the sound pass ports them).
 */
public record DeviceSound(String breakSound, String step, String place, String hit, String fall) {
    public static final DeviceSound STONE = vanillaGroup("stone");
    public static final DeviceSound WOOD = vanillaGroup("wood");
    public static final DeviceSound METAL = vanillaGroup("metal");
    /** Vanilla {@code SoundType.NETHER_WART}: nether wart break and plant, stone step, hit and fall. */
    public static final DeviceSound NETHER_WART = new DeviceSound("minecraft:block.nether_wart.break",
        "minecraft:block.stone.step", "minecraft:item.nether_wart.plant", "minecraft:block.stone.hit", "minecraft:block.stone.fall");

    private static final Map<String, DeviceSound> VANILLA = Map.of("STONE", STONE, "WOOD", WOOD, "METAL", METAL,
        "NETHER_WART", NETHER_WART);

    public DeviceSound {
        for (String s : new String[] {breakSound, step, place, hit, fall}) {
            if (s == null || !s.matches("[a-z0-9_]+:[a-z0-9_.]+")) {
                throw new DatagenException("device sound event " + s + " is not a namespaced id");
            }
        }
    }

    private static DeviceSound vanillaGroup(String name) {
        String p = "minecraft:block." + name + ".";
        return new DeviceSound(p + "break", p + "step", p + "place", p + "hit", p + "fall");
    }

    /** A TFC {@code TFCSounds.registerBlock(name)} type: {@code tfp:block.<name>.*}. */
    public static DeviceSound tfc(String name) {
        String p = Json.TO_NS + ":block." + name + ".";
        return new DeviceSound(p + "break", p + "step", p + "place", p + "hit", p + "fall");
    }

    /**
     * The sound of a Java reference as TFC's {@code .sound(...)} writes it: {@code SoundType.WOOD} or
     * {@code TFCSounds.CHARCOAL}. An unmapped vanilla type fails.
     */
    public static DeviceSound ofJava(String ref) {
        if (ref.startsWith("SoundType.")) {
            DeviceSound s = VANILLA.get(ref.substring("SoundType.".length()));
            if (s == null) {
                throw new DatagenException("no device sound mapping for " + ref);
            }
            return s;
        }
        if (ref.startsWith("TFCSounds.") && ref.substring("TFCSounds.".length()).matches("[A-Z_]+")) {
            return tfc(ref.substring("TFCSounds.".length()).toLowerCase(java.util.Locale.ROOT));
        }
        throw new DatagenException("unknown sound reference " + ref);
    }

    /** CraftEngine {@code settings.sounds}. */
    Map<String, Object> yaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("break", breakSound);
        m.put("step", step);
        m.put("place", place);
        m.put("hit", hit);
        m.put("fall", fall);
        return m;
    }
}
