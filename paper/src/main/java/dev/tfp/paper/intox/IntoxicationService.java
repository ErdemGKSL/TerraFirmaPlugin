/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (getIntoxication, addIntoxication,
 * eat: addIntoxication(food.intoxication())), src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (onDrink)
 */
package dev.tfp.paper.intox;

import dev.tfp.core.intox.Intoxicants;
import dev.tfp.core.intox.Intoxication;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataStore;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A player's intoxication (PAR-SURV-08), provided by {@link IntoxicationModule} as a module service
 * ({@code context.services().require(IntoxicationService.class)}). The state is TFC's single timestamp,
 * {@link PlayerData#intoxicationTick()} (the player tick the player is intoxicated until), so nothing ticks: the level is
 * read from it and falls linearly to 0.
 *
 * <p>Callers: the jug and glass bottle drink (PAR-POT-07) calls {@link #drink} with the drained fluid after its
 * {@code Drinkable.onDrink} thirst part, as TFC's {@code JugItem.finishUsingItem} does through {@code onDrink}; any
 * future eat path of a food with {@code intoxication} calls {@link #add}. No TFC food has any.
 *
 * <p>Threads: reads ({@link #level}, {@link #remaining}) on any thread (the store's lock-free read and one clock
 * snapshot); writes on the player's own thread, the store's convention.
 */
public final class IntoxicationService {
    private final PlayerDataStore data;
    private final LongSupplier playerTicks;
    private final Supplier<Intoxicants> intoxicants;
    private final int maxTicks;

    /**
     * @param playerTicks the calendar's player tick ({@code calendar().getTicks()} in TFC's {@code PlayerInfo})
     * @param maxTicks    TFC {@code maxIntoxicationTicks}
     */
    public IntoxicationService(PlayerDataStore data, LongSupplier playerTicks, Supplier<Intoxicants> intoxicants,
            int maxTicks) {
        if (maxTicks < Intoxication.MIN_MAX_TICKS) {
            throw new IllegalArgumentException("maxIntoxicationTicks below TFC's minimum: " + maxTicks);
        }
        this.data = data;
        this.playerTicks = playerTicks;
        this.intoxicants = intoxicants;
        this.maxTicks = maxTicks;
    }

    public int maxTicks() {
        return maxTicks;
    }

    /** Any thread: the loaded drinkables' intoxication. */
    public Intoxicants intoxicants() {
        return intoxicants.get();
    }

    /** Any thread: TFC {@code getIntoxication}, 0 when sober or not loaded. */
    public float level(UUID player) {
        return level(player, playerTicks.getAsLong());
    }

    /** Any thread: the level at player tick {@code now}. */
    public float level(UUID player, long now) {
        Optional<PlayerData> d = data.get(player);
        return d.map(p -> Intoxication.level(p.intoxicationTick(), now, maxTicks)).orElse(0f);
    }

    /** Any thread: the ticks until sober. */
    public long remaining(UUID player) {
        long now = playerTicks.getAsLong();
        return data.get(player).map(p -> Intoxication.remaining(p.intoxicationTick(), now)).orElse(0L);
    }

    /**
     * Player thread: TFC {@code addIntoxication(ticks)} at the current player tick. Returns the stored tick after it
     * ({@link Intoxication#SOBER} when the player is not loaded, which changes nothing).
     */
    public long add(UUID player, long ticks) {
        long now = playerTicks.getAsLong();
        try {
            return data.update(player, d -> d.withIntoxicationTick(
                    Intoxication.add(d.intoxicationTick(), now, ticks, maxTicks))).intoxicationTick();
        } catch (IllegalStateException notLoaded) {
            return Intoxication.SOBER;
        }
    }

    /**
     * Player thread: the intoxication part of TFC {@code Drinkable.onDrink(player, mB)} for {@code fluidId}: the
     * drinkable's {@code intoxication} ticks, whatever the amount ({@code FoodData.mul} keeps it). TFC adds it even when
     * it is 0 (a no-op), so nothing is written then. Returns the ticks added.
     */
    public int drink(UUID player, String fluidId) {
        int ticks = intoxicants().ticksPerDrink(fluidId);
        if (ticks != 0) {
            add(player, ticks);
        }
        return ticks;
    }

    /** Player thread: back to sober (TFC: a death gives the new player a fresh {@code PlayerInfo}). */
    public void clear(UUID player) {
        try {
            data.update(player, d -> d.intoxicationTick() == Intoxication.SOBER ? d
                    : d.withIntoxicationTick(Intoxication.SOBER));
        } catch (IllegalStateException notLoaded) {
            // quit meanwhile: nothing to reset
        }
    }
}
