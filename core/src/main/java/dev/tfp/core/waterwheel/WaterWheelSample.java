/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WaterWheelBlockEntity.java @ c3b4d9536 (serverTick lines
 *       40-60: clientTick first, then the sample every 40 ticks; clientTick lines 62-83; the saved fields targetSpeed and
 *       obstructed, saveAdditional/loadAdditional)
 */
package dev.tfp.core.waterwheel;

import dev.tfp.core.power.WaterWheel;
import dev.tfp.core.rotation.SourceSpeed;
import java.util.Optional;

/**
 * The state of one water wheel between two flow samples, and its 40-tick step. TFC ticks the wheel every tick: the speed
 * ramps toward the target by {@link WaterWheel#LERP_SPEED} (or is held at 0 while obstructed), and every
 * {@link WaterWheel#SAMPLE_INTERVAL_TICKS} ticks the window is sampled after that tick's ramp. The target and the
 * obstructed flag only change at a sample, so the ramp over the ticks between two samples is one closed-form step
 * ({@link #advance}); nothing here ticks.
 *
 * @param speed      the wheel's speed, radians per tick (TFC {@code node.rotation().speed()}); negative turns the other way
 * @param target     the speed the wheel ramps toward (TFC {@code targetSpeed})
 * @param obstructed whether the last sample found a solid block in the window (TFC {@code obstructed})
 */
public record WaterWheelSample(float speed, float target, boolean obstructed) {
    /** A newly placed wheel: at rest, no target, not obstructed (TFC constructor). */
    public static final WaterWheelSample FRESH = new WaterWheelSample(0f, 0f, false);

    /**
     * The state after {@code ticks} ticks of TFC {@code clientTick} with this target: held at 0 while obstructed, otherwise
     * ramped toward the target by at most {@code ticks * LERP_SPEED}. Equal to {@code ticks} single ramps because the target
     * does not move between samples.
     */
    public WaterWheelSample advance(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks < 0: " + ticks);
        }
        if (obstructed) {
            return new WaterWheelSample(0f, target, true);
        }
        if (ticks == 0) {
            return this;
        }
        return new WaterWheelSample(SourceSpeed.ramp(speed, target, WaterWheel.LERP_SPEED * ticks), target, false);
    }

    /**
     * One sample: the ramp of the {@code elapsed} ticks since the last one, then TFC's sample of the window. An obstructed
     * window (empty {@code netFlow}) keeps the target and sets the flag; otherwise the target becomes
     * {@code netFlow * MAX_SPEED / MAX_FLOW} and the flag clears.
     *
     * @param elapsed ticks since the last sample (normally {@link WaterWheel#SAMPLE_INTERVAL_TICKS})
     * @param netFlow {@link WaterWheel#netFlow} of the window
     */
    public WaterWheelSample sample(long elapsed, Optional<Float> netFlow) {
        WaterWheelSample after = advance(elapsed);
        if (netFlow.isEmpty()) {
            return new WaterWheelSample(after.speed, after.target, true);
        }
        return new WaterWheelSample(after.speed, WaterWheel.targetSpeed(netFlow.get()), false);
    }

    /**
     * A sample whose window could not be read completely (a cell in a chunk that is not loaded, or whose river flow is not
     * known yet): only the ramp runs, the target and the flag stay (ledger divergence: TFC always reads the whole window).
     */
    public WaterWheelSample skipped(long elapsed) {
        return advance(elapsed);
    }
}
