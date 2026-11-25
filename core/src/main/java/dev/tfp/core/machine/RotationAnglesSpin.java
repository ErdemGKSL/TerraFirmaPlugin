/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Rotation.java @ c3b4d9536 (angle(rotation, partialTick): the axis step of the
 *       direction times the source angle),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/AxleBlockEntityRenderer.java @ c3b4d9536 (applyRotation: the
 *       shaft turns by minus that angle about its positive axis)
 */
package dev.tfp.core.machine;

import dev.tfp.core.world.Direction;

/**
 * The spin of a rotating block as a client can show it without a packet per tick (PAR-MECH-02). TFC turns the shaft in its block
 * entity renderer from the source's angle each frame; a vanilla client has only a display entity whose rotation it interpolates
 * between two server-sent values. So a spin is a run of <em>segments</em>: each packet carries the rotation the display reaches
 * {@code duration} ticks later, and the client turns steadily there by itself.
 *
 * <p>A display's transformation is {@code left * scale * right}, and the client interpolates the left and the right rotation
 * separately along their shortest arcs (under half a turn each). Both rotations are about the shaft's axis and each carries half
 * of the angle, so one segment can sweep almost two thirds of a turn ({@link #MAX_SWEEP}), twice what one rotation alone could.
 * At TFC's creative-motor top speed (30 rpm, 0.157 rad per tick) that is one packet every 37 ticks, and none while the speed stays
 * the same and the block is not refreshed.
 *
 * <p>All angles are in radians and unbounded (never wrapped): wrapping would make the client interpolate the wrong way round.
 */
public final class RotationAnglesSpin {

    /** The most one segment may sweep: two shortest arcs of 170 degrees (a margin under 180 so the arc direction is never ambiguous). */
    public static final double MAX_SWEEP = 2 * Math.toRadians(170);

    /** The longest a segment may run in ticks, so a very slow shaft still refreshes now and then (a viewer's lost packet heals). */
    public static final int MAX_SEGMENT_TICKS = 200;

    private RotationAnglesSpin() {
    }

    /**
     * The rate a block turns at about the positive direction of its axis: TFC's {@code Rotation.angle} multiplies the source angle
     * by the axis step of the block's direction and the renderer turns by the negative of it.
     *
     * @param direction the block's rotation direction (the network node's handedness)
     * @param speed     the source's signed speed in radians per tick
     * @return radians per tick about the positive axis
     */
    public static double rate(Direction direction, float speed) {
        return -RotationAngles.axisStep(direction) * (double) speed;
    }

    /**
     * The shaft angle as a straight line in time: {@code phi(t) = phi + rate * (t - tick)}. Lazy: nothing ticks, the angle at any
     * tick is computed on demand (TFC steps a float angle each tick; this closed form agrees with it to float precision).
     *
     * @param tick the game tick the line is anchored at
     * @param phi  the angle at that tick, unbounded
     * @param rate radians per tick about the positive axis
     */
    public record Track(long tick, double phi, double rate) {

        /** A shaft at rest at {@code phi}. */
        public static Track rest(long tick, double phi) {
            return new Track(tick, phi, 0);
        }

        public double phiAt(long at) {
            return phi + rate * (at - tick);
        }

        /** The same shaft with a new rate from {@code at}: continuous, the angle at {@code at} is kept. */
        public Track retarget(long at, double newRate) {
            return new Track(at, phiAt(at), newRate);
        }
    }

    /**
     * One packet: the display's left and right rotation both turn {@code halfAngle} about the axis (so the shaft shows
     * {@code 2 * halfAngle}), reached after {@code duration} ticks of interpolation (0 snaps).
     *
     * @param tick     the tick the packet is sent
     * @param halfAngle each rotation's angle about the positive axis
     * @param duration the transformation interpolation duration in ticks
     * @param nextTick the tick the next segment must be sent, or -1 when the shaft rests
     */
    public record Update(long tick, double halfAngle, int duration, long nextTick) {
        /** The shaft angle the display reaches. */
        public double phi() {
            return 2 * halfAngle;
        }
    }

    /**
     * The segment starting at {@code tick}. The client is assumed to show {@code track.phiAt(tick)} then (the previous segment
     * ended there, or the shaft rests there).
     */
    public static Update plan(Track track, long tick) {
        double rate = track.rate();
        if (rate == 0) {
            return new Update(tick, track.phiAt(tick) / 2, 0, -1);
        }
        int duration = (int) Math.max(1, Math.min(MAX_SEGMENT_TICKS, Math.floor(MAX_SWEEP / Math.abs(rate))));
        return new Update(tick, track.phiAt(tick + duration) / 2, duration, tick + duration);
    }

    /** How many packets keep a shaft turning at {@code rate} for {@code ticks} ticks, the first one included. */
    public static int packetsFor(double rate, int ticks) {
        if (rate == 0) {
            return 1;
        }
        Track t = Track.rest(0, 0).retarget(0, rate);
        int n = 0;
        for (Update u = plan(t, 0); ; u = plan(t, u.nextTick())) {
            n++;
            if (u.nextTick() < 0 || u.nextTick() >= ticks) {
                return n;
            }
        }
    }
}
