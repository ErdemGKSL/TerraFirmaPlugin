/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCSounds.java @ c3b4d9536 (registerBlock: DeferredSoundType(1.0f, 1.0f, ...))
 * The per-event multipliers are vanilla client behaviour (LevelRenderer, BlockItem, Entity, MultiPlayerGameMode,
 * LivingEntity), not TFC code.
 */
package dev.tfp.datagen.rendered;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.device.DeviceSound;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The volume and pitch of each sound event of a renderer cube, as the vanilla client plays a block's {@code SoundType}
 * (PAR-INFRA-17). CraftEngine replays the events of a CE block itself; without an explicit volume and pitch it uses
 * its own {@code BlockSounds} defaults, which equal a {@code SoundType} of (1.0, 1.0) and so play {@code METAL}
 * (1.0, 1.5) a third too low. The client applies these multipliers to the {@code SoundType} volume {@code v} and pitch
 * {@code p}:
 *
 * <ul>
 *   <li>break ({@code LevelRenderer} level event 2001) and place ({@code BlockItem.place}): {@code (v + 1) / 2},
 *       {@code p * 0.8}</li>
 *   <li>step ({@code Entity.playStepSound}): {@code v * 0.15}, {@code p}</li>
 *   <li>hit ({@code MultiPlayerGameMode.continueDestroyBlock}): {@code (v + 1) / 8}, {@code p * 0.5}</li>
 *   <li>fall ({@code LivingEntity.playBlockFallSound}): {@code v * 0.5}, {@code p * 0.75}</li>
 * </ul>
 *
 * <p>The {@code SoundType} volume and pitch: vanilla {@code STONE}, {@code WOOD} and {@code NETHER_WART} are (1.0, 1.0),
 * {@code METAL} is (1.0, 1.5) (read from the 26.3 client's {@code SoundType.<clinit>}); TFC's {@code TFCSounds.registerBlock}
 * types are (1.0, 1.0).
 *
 * <p>Only these vanilla types are mapped; {@link #of} fails fast for any other. A family that draws a renderer cube with
 * another vanilla {@code SoundType} (e.g. {@code ANVIL}, {@code CHAIN}, {@code LANTERN}, {@code COPPER}, {@code COPPER_GRATE},
 * {@code GRAVEL}; PAR-DECO-09, PAR-DECO-12, PAR-MECH-01) adds its volume and pitch to {@code VANILLA} first.
 *
 * @param volume the {@code SoundType} volume
 * @param pitch the {@code SoundType} pitch
 */
public record RenderedSound(BigDecimal volume, BigDecimal pitch) {
    public static final RenderedSound UNIT = new RenderedSound(BigDecimal.ONE, BigDecimal.ONE);
    public static final RenderedSound METAL = new RenderedSound(BigDecimal.ONE, new BigDecimal("1.5"));
    private static final Map<DeviceSound, RenderedSound> VANILLA = Map.of(DeviceSound.STONE, UNIT, DeviceSound.WOOD, UNIT,
        DeviceSound.NETHER_WART, UNIT, DeviceSound.METAL, METAL);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal EIGHT = BigDecimal.valueOf(8);

    /** The {@code SoundType} volume and pitch of a device sound set; TFC's own types are (1.0, 1.0). */
    public static RenderedSound of(DeviceSound sound) {
        RenderedSound s = VANILLA.get(sound);
        if (s != null) {
            return s;
        }
        // Every tfp:block.* sound is a TFC type: at the pinned commit all TFC block SoundTypes come from
        // TFCSounds.registerBlock (TFCSounds.java:142-151), which builds DeferredSoundType(1.0f, 1.0f, ...).
        if (sound.breakSound().startsWith(Json.TO_NS + ":block.")) {
            return UNIT;
        }
        throw new DatagenException("no SoundType volume/pitch for " + sound);
    }

    /** The volume the client plays {@code event} at ({@link RenderedTemplates#SOUND_KEYS}). */
    public BigDecimal volume(String event) {
        return scale(switch (event) {
            case "break", "place" -> volume.add(BigDecimal.ONE).divide(TWO);
            case "step" -> volume.multiply(new BigDecimal("0.15"));
            case "hit" -> volume.add(BigDecimal.ONE).divide(EIGHT);
            case "fall" -> volume.multiply(new BigDecimal("0.5"));
            default -> throw new IllegalArgumentException("sound event " + event);
        });
    }

    /** The pitch the client plays {@code event} at. */
    public BigDecimal pitch(String event) {
        return scale(switch (event) {
            case "break", "place" -> pitch.multiply(new BigDecimal("0.8"));
            case "step" -> pitch;
            case "hit" -> pitch.multiply(new BigDecimal("0.5"));
            case "fall" -> pitch.multiply(new BigDecimal("0.75"));
            default -> throw new IllegalArgumentException("sound event " + event);
        });
    }

    /** {@code event -> [volume, pitch]} for every event. */
    public Map<String, List<BigDecimal>> table() {
        Map<String, List<BigDecimal>> m = new LinkedHashMap<>();
        for (String e : RenderedTemplates.SOUND_KEYS) {
            m.put(e, List.of(volume(e), pitch(e)));
        }
        return m;
    }

    /** At least one decimal, no trailing zeros beyond it ({@code 1.0}, {@code 1.125}). */
    private static BigDecimal scale(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 1 ? s.setScale(1, RoundingMode.UNNECESSARY) : s;
    }
}
