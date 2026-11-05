/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCSounds.java @ c3b4d9536 (registerBlock: block.<name>.break/step/place/hit/fall)
 * The volume/pitch rule (PAR-INFRA-33) follows CraftEngine 26.9.2's BlockSounds defaults, not TFC code.
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.rendered.RenderedSound;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The five sound events of a device block (PAR-INFRA-08), as a Minecraft {@code SoundType} names them. Vanilla
 * {@code SoundType}s map to their vanilla events (note {@code NETHER_WART}, TFC's firepit sound, mixes nether wart and
 * stone events); a TFC {@code TFCSounds.X} type maps to {@code tfp:block.x.*}, the renamed TFC events, which the pack
 * must define (ledger divergence until the sound pass ports them).
 *
 * <p>Volume and pitch (PAR-INFRA-33): each event plays at the TFC {@code SoundType}'s volume and pitch under the vanilla
 * client's multipliers ({@link RenderedSound}). CraftEngine's {@code BlockSounds} defaults for a plain id (break and
 * place 1.0/0.8, step 0.15/1.0, fall 0.5/0.75; read from the 26.9.2 jar) are exactly those values for a
 * {@code SoundType} of (1.0, 1.0) (vanilla {@code STONE}, {@code WOOD}, {@code NETHER_WART} and every
 * {@code TFCSounds.registerBlock} type), so such a set stays plain ids. Any other {@code SoundType} (vanilla
 * {@code METAL}, 1.0/1.5) writes {@code {id, volume, pitch}} for all five events. The hit event is the exception that
 * does not matter here: CraftEngine's mining loop sends it at a fixed 0.5/0.5 whatever the config says, and the paper
 * packet rewrite ({@code dev.tfp.paper.blocksound}) sets it to {@link #values()}' hit, reading a plain id as (1.0, 1.0).
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

    /** {@code event -> [volume, pitch]} the client plays each event at for this set's TFC {@code SoundType}. */
    public Map<String, List<BigDecimal>> values() {
        return RenderedSound.of(this).table();
    }

    /** True when CraftEngine's defaults for plain ids already play TFC's volume and pitch: a (1.0, 1.0) type. */
    public boolean plainIds() {
        RenderedSound type = RenderedSound.of(this);
        return type.volume().compareTo(BigDecimal.ONE) == 0 && type.pitch().compareTo(BigDecimal.ONE) == 0;
    }

    /**
     * CraftEngine {@code settings.sounds}: plain ids for a (1.0, 1.0) {@code SoundType}, otherwise
     * {@code {id, volume, pitch}} per event ({@link #values()}).
     */
    public Map<String, Object> yaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("break", event("break", breakSound));
        m.put("step", event("step", step));
        m.put("place", event("place", place));
        m.put("hit", event("hit", hit));
        m.put("fall", event("fall", fall));
        return m;
    }

    private Object event(String event, String id) {
        if (plainIds()) {
            return id;
        }
        RenderedSound type = RenderedSound.of(this);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("volume", type.volume(event));
        m.put("pitch", type.pitch(event));
        return m;
    }
}
