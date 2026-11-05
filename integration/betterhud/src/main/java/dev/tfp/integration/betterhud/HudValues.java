/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.core.hud.HudGauge;
import dev.tfp.paper.integration.HudSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * What our BetterHud placeholders and listeners read from a {@link HudSnapshot}. Pure field reads: they
 * run on BetterHud's async timer ~20×/s per player and must never touch the world (on Folia that throws).
 * Names are the ids used in our generated BetterHud YAML ({@code [tfp_health]}, {@code class: tfp_thirst}).
 */
final class HudValues {
    private HudValues() {
    }

    /**
     * Number placeholders. Thirst and hunger are published by {@code PlayerNeedsHudService}; the placeholders read the
     * player's latest snapshot and fall back to {@link HudSnapshot#DEFAULT} before the first publish. Health and max
     * health are published by {@code PlayerNeedsHudService} on TFC's scale. {@code tfp_held_temp} evaluates the held
     * stack's published heat record at the calendar tick of the read ({@link HudSnapshot#heldTemperature()},
     * PAR-HUD-11), so it follows the cooling between publishes without touching the item. {@code tfp_armor} is the vanilla armour attribute (0..30), its listener 0..1 on
     * vanilla's 20-point scale (BetterHud's own {@code armor} listener would need its built-in placeholder; ours reads the snapshot).
     *
     * <p>PAR-HUD-12: in creative and spectator ({@link HudSnapshot#needsActive()} false) {@code tfp_max_health} reads 0
     * ({@link #NEEDS_GATE}) and {@code tfp_held_temp} reads 0. Every generated bar layout of the bars HUD carries the
     * condition {@code tfp_max_health > 0} and the held-temperature popup already shows only while {@code tfp_held_temp
     * > 0}, so the bars and the popup hide in those modes without a further placeholder (a max health is never 0 in
     * survival or adventure).
     */
    static final Map<String, ToDoubleFunction<HudSnapshot>> NUMBERS;

    /**
     * The placeholder the bars' game-mode condition reads (PAR-HUD-12): the max health, 0 while the player's game mode
     * has no needs. Must match {@code HudLayouts.NEEDS_ACTIVE} in tools/datagen.
     */
    static final String NEEDS_GATE = "tfp_max_health";

    /** Bar listeners, 0..1. */
    static final Map<String, ToDoubleFunction<HudSnapshot>> LISTENERS;

    static {
        Map<String, ToDoubleFunction<HudSnapshot>> n = new LinkedHashMap<>();
        n.put("tfp_health", HudSnapshot::health);
        n.put(NEEDS_GATE, s -> s.needsActive() ? s.maxHealth() : 0);
        n.put("tfp_thirst", HudSnapshot::thirst);
        n.put("tfp_hunger", HudSnapshot::hunger);
        n.put("tfp_held_temp", s -> s.needsActive() ? s.heldTemperature() : 0);
        n.put("tfp_air", HudSnapshot::air);
        n.put("tfp_armor", HudSnapshot::armor);
        NUMBERS = Collections.unmodifiableMap(n);

        Map<String, ToDoubleFunction<HudSnapshot>> l = new LinkedHashMap<>();
        l.put("tfp_health", s -> ratio(s.health(), s.maxHealth()));
        l.put("tfp_thirst", s -> ratio(s.thirst(), HudSnapshot.MAX_THIRST));
        l.put("tfp_hunger", s -> ratio(s.hunger(), HudSnapshot.MAX_HUNGER));
        l.put("tfp_air", s -> ratio(s.air(), HudSnapshot.MAX_AIR));
        l.put("tfp_armor", s -> ratio(s.armor(), HudSnapshot.MAX_ARMOR));
        l.put("tfp_ambient_temp", s -> thermometer(s.ambientTemperature()));
        LISTENERS = Collections.unmodifiableMap(l);
    }

    /** The thermometer bar's range: {@link #THERMOMETER_MIN} °C (empty) to {@link #THERMOMETER_MIN} + {@link #THERMOMETER_SPAN} °C (full). */
    static final double THERMOMETER_MIN = -20;
    static final double THERMOMETER_SPAN = 60;

    /** The ambient temperature as the thermometer fill, 0..1; 0 while no reading exists (NaN). */
    static double thermometer(double celsius) {
        return Double.isNaN(celsius) ? 0 : ratio(celsius - THERMOMETER_MIN, THERMOMETER_SPAN);
    }

    static double ratio(double value, double max) {
        return HudGauge.fraction(value, max);
    }
}
