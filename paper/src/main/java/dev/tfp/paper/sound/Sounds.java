/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCSounds.java @ c3b4d9536
 */
package dev.tfp.paper.sound;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;

/**
 * PAR-DATA-08: TFC's sound events as the resource pack ships them ({@code tfp:<event>}, datagen {@code sound/TfcSounds}).
 * Feature code plays them by key so the client resolves the pack's oggs; a client without the pack plays nothing. The
 * animal events live in {@code tfp_animals} ({@code AnimalSoundsModule}), peat in {@code tfp_soil} and the firestarter in
 * {@code tfp_fire}. Every key here is checked against the generated event list ({@code SoundsTest}).
 */
public final class Sounds {
    public static final String NAMESPACE = "tfp";

    // TFCSounds block.*
    public static final String ANVIL_HIT = key("block.anvil.hit");
    public static final String BELLOWS_BLOW = key("block.bellows.blow");
    public static final String QUERN_DRAG = key("block.quern.drag");
    public static final String LOOM_WEAVE = key("block.loom.weave");
    public static final String TOOL_RACK_PLACE = key("block.tool_rack.place");
    public static final String SCRIBING_TABLE_USE = key("block.scribing_table.use");
    public static final String BARREL_OPEN = key("block.barrel.open");
    public static final String BARREL_CLOSE = key("block.barrel.close");
    public static final String BARREL_DRIP = key("block.barrel.drip");
    public static final String LARGE_VESSEL_OPEN = key("block.large_vessel.open");
    public static final String LARGE_VESSEL_CLOSE = key("block.large_vessel.close");
    public static final String WATTLE_DYED = key("block.wattle.dyed");
    public static final String WATTLE_DAUBED = key("block.wattle.daubed");
    public static final String WATTLE_WOVEN = key("block.wattle.woven");
    public static final String CROP_STICK_ADD = key("block.crop.stick_add");
    public static final String BLOOMERY_CRACKLE = key("block.bloomery.crackle");
    // TFCSounds item.*
    public static final String COOL = key("item.cool");
    public static final String CERAMIC_BREAK = key("item.ceramic.break");
    public static final String JUG_BLOW = key("item.jug.blow");
    public static final String FERTILIZER_USE = key("item.fertilizer.use");
    public static final String PAN_USE = key("item.pan.use");
    public static final String JAVELIN_HIT = key("item.javelin.hit");
    public static final String JAVELIN_HIT_GROUND = key("item.javelin.hit_ground");
    public static final String JAVELIN_THROW = key("item.javelin.throw");
    public static final String KNAPPING_STONE = key("item.knapping.stone");
    public static final String KNAPPING_CLAY = key("item.knapping.clay");
    public static final String KNAPPING_LEATHER = key("item.knapping.leather");
    // TFCSounds random.*
    public static final String DIRT_SLIDE_SHORT = key("random.dirt_slide_short");
    public static final String ROCK_SLIDE_LONG = key("random.rock_slide_long");
    public static final String ROCK_SLIDE_LONG_FAKE = key("random.rock_slide_long_fake");
    public static final String ROCK_SLIDE_SHORT = key("random.rock_slide_short");
    public static final String ROCK_SMASH = key("random.rock_smash");

    private Sounds() {
    }

    /** The full key of a shipped event name. */
    public static String key(String event) {
        return NAMESPACE + ":" + event;
    }

    /** Plays a TFC event at a position, for everyone in range (TFC's {@code level.playSound(null, pos, ...)}). */
    public static void play(World world, Location at, String key, SoundCategory category, float volume, float pitch) {
        world.playSound(at, key, category, volume, pitch);
    }
}
