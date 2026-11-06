/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/effect/ThirstEffect.java @ c3b4d9536 (the effect's state),
 * and src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (onDrink: addEffect)
 */
package dev.tfp.paper.player;

import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.ThirstEffect;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The player's {@code tfp:thirst} effect (TFC's {@code tfc:thirst} mob effect), kept in the player's persistent data
 * container as {@code [amplifier, remaining ticks]} so it survives a relog and a restart like a vanilla effect. A vanilla
 * client cannot show a custom effect, so it has no icon; the drain is applied by {@link ThirstModule}. Player thread
 * only.
 */
public final class ThirstEffects {
    /** The effect id in the drinkable data. */
    public static final String THIRST_EFFECT = "tfp:thirst";

    static final NamespacedKey KEY = new NamespacedKey("tfp", "thirst_effect");

    private ThirstEffects() {
    }

    /** The player's thirst effect, if one is running. A malformed record reads as none. */
    public static Optional<ThirstEffect> read(Player player) {
        ThreadAsserts.owned(player);
        int[] v = player.getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        if (v == null || v.length != 2 || v[0] < 0 || v[0] > 255 || v[1] < 1) {
            return Optional.empty();
        }
        return Optional.of(new ThirstEffect(v[0], v[1]));
    }

    /** Stores the effect, or removes it when {@code effect} is null. */
    public static void write(Player player, ThirstEffect effect) {
        ThreadAsserts.owned(player);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (effect == null) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.INTEGER_ARRAY, new int[] {effect.amplifier(), effect.remaining()});
        }
    }

    /** Adds a thirst effect with vanilla's {@code addEffect} combination rule ({@link ThirstEffect#combine}). */
    public static void add(Player player, ThirstEffect effect) {
        write(player, read(player).map(e -> e.combine(effect)).orElse(effect));
    }

    /**
     * Applies one drinkable effect that came up (TFC {@code onDrink}: {@code new MobEffectInstance(type, duration,
     * amplifier, false, false, true)}): {@code tfp:thirst} becomes our thirst effect, a vanilla effect id a vanilla
     * effect without particles. An unknown id or a duration below 1 is skipped.
     *
     * @return whether an effect was applied
     */
    public static boolean apply(Player player, Drinkable.Effect effect) {
        if (effect.duration() < 1 || effect.amplifier() < 0 || effect.amplifier() > 255) {
            return false;
        }
        if (THIRST_EFFECT.equals(effect.effect())) {
            add(player, new ThirstEffect(effect.amplifier(), effect.duration()));
            return true;
        }
        NamespacedKey key = NamespacedKey.fromString(effect.effect());
        PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
        if (type == null) {
            return false;
        }
        player.addPotionEffect(new PotionEffect(type, effect.duration(), effect.amplifier(), false, false, true));
        return true;
    }
}
