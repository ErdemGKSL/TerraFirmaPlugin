/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import java.util.ArrayList;
import java.util.List;

/**
 * The versioned codec of {@link PlayerData}. Field names follow TFC's NBT ({@code PlayerInfo
 * #addAdditionalSaveData}, {@code FoodData.MAP_CODEC}) so the layout stays recognisable.
 *
 * <p><b>Adding a field</b> (a feature needs new player state): add it to {@link PlayerData}, bump
 * {@link #VERSION}, register {@code .migrate(old, d -> d.putX("field", default))}, write and read it below,
 * and add a migration test that decodes a map of the previous version.
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>thirst, lastDrinkTick, intoxication, chiselMode, nutrition (M1-SVC-02)</li>
 * </ol>
 */
public final class PlayerDataCodec {
    public static final int VERSION = 1;

    public static final VersionedCodec<PlayerData> CODEC = VersionedCodec.<PlayerData>builder("player_data", VERSION)
            // .migrate(1, d -> ...) when VERSION becomes 2
            .build(PlayerDataCodec::write, PlayerDataCodec::read);

    private PlayerDataCodec() {
    }

    private static void write(PlayerData p, DataMap out) {
        out.putFloat("thirst", p.thirst());
        out.putLong("lastDrinkTick", p.lastDrinkTick());
        out.putLong("intoxication", p.intoxicationTick());
        out.putString("chiselMode", p.chiselMode());
        List<DataMap> records = new ArrayList<>(p.nutrition().size());
        for (PlayerData.FoodRecord r : p.nutrition()) {
            PlayerData.Nutrients n = r.nutrients();
            records.add(new DataMap()
                    .putInt("hunger", r.hunger())
                    .putFloat("water", r.water())
                    .putFloat("saturation", r.saturation())
                    .putInt("intoxication", r.intoxication())
                    .putFloat("grain", n.grain())
                    .putFloat("fruit", n.fruit())
                    .putFloat("vegetables", n.vegetables())
                    .putFloat("protein", n.protein())
                    .putFloat("dairy", n.dairy())
                    .putFloat("decay_modifier", r.decayModifier()));
        }
        out.putList("nutrition", records);
    }

    private static PlayerData read(DataMap in) {
        List<PlayerData.FoodRecord> records = new ArrayList<>();
        for (DataMap r : in.getList("nutrition")) {
            // TFC's FoodData codec: every field optional, default 0.
            records.add(new PlayerData.FoodRecord(
                    r.getInt("hunger", 0), r.getFloat("water", 0f), r.getFloat("saturation", 0f),
                    r.getInt("intoxication", 0),
                    new PlayerData.Nutrients(r.getFloat("grain", 0f), r.getFloat("fruit", 0f),
                            r.getFloat("vegetables", 0f), r.getFloat("protein", 0f), r.getFloat("dairy", 0f)),
                    r.getFloat("decay_modifier", 0f)));
        }
        return new PlayerData(in.requireFloat("thirst"), in.requireLong("lastDrinkTick"),
                in.requireLong("intoxication"), in.requireString("chiselMode"), records);
    }
}
