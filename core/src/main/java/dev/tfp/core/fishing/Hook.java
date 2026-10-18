/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/TFCFishingHook.java @ c3b4d9536
 */
package dev.tfp.core.fishing;

/**
 * The state of a fishing hook that the pull rules keep: the pull exhaustion and the game time of the last pull. Immutable; every step
 * returns the next state.
 *
 * <p>TFC calls the retrieve on both sides and keeps its exhaustion and last-pull time in the hook's saved data. The time is game
 * ticks, not calendar ticks, so the rule is a pure function of the two stored numbers and the game time the caller passes.
 *
 * @param exhaustion the pull exhaustion, 0 to 100 while the hook lives
 * @param lastPulled the game time of the last retrieve
 */
public record Hook(int exhaustion, long lastPulled) {

    /** The window, in game ticks, after a pull in which a new pull adds exhaustion. */
    public static final int PULL_WINDOW = 25;
    /** The exhaustion above which the hook breaks the bait and is discarded. */
    public static final int MAX_EXHAUSTION = 100;

    /** Which entity, if any, the hook holds. */
    public enum Hooked {
        /** No entity is hooked. */
        NONE,
        /** A hooked entity that is still alive. */
        LIVE,
        /** A hooked entity that was removed. */
        REMOVED
    }

    /** A hook that was just thrown: no exhaustion, no pull. */
    public static Hook fresh() {
        return new Hook(0, 0L);
    }

    /**
     * One hook tick, TFC {@code tick}: the exhaustion falls by one while positive.
     *
     * @return the next state
     */
    public Hook tick() {
        return exhaustion > 0 ? new Hook(exhaustion - 1, lastPulled) : this;
    }

    /**
     * One retrieve, TFC {@code retrieve}. A pull within {@value #PULL_WINDOW} ticks of the last one adds {@code (window - diff) * 2}
     * exhaustion; past {@value #MAX_EXHAUSTION} the bait is eaten, the hook is discarded and the rod takes 1 damage. Otherwise the
     * owner pulls: a hooked entity is pulled, the hook is discarded when no live entity is held, and the rod takes 2 damage on the ground
     * or 1 in the air. With no owner ready the rod takes no damage.
     *
     * @param gameTime   the game time of the retrieve
     * @param hooked     what the hook holds
     * @param ownerReady whether the owner is a player who is not stopped from fishing (TFC {@code player != null && !shouldStopFishing})
     * @param onGround   whether the hook is on the ground
     * @return the outcome, with the next state of the hook
     */
    public Retrieve retrieve(long gameTime, Hooked hooked, boolean ownerReady, boolean onGround) {
        long diff = gameTime - lastPulled;
        int next = exhaustion;
        boolean warning = false;
        if (diff < PULL_WINDOW) {
            next = exhaustion + (int) ((PULL_WINDOW - diff) * 2);
            if (next > MAX_EXHAUSTION) {
                return new Retrieve(new Hook(next, lastPulled), 1, true, true, false, false, true);
            }
            warning = true;
        }
        Hook after = new Hook(next, gameTime);
        if (!ownerReady) {
            return new Retrieve(after, 0, false, false, warning, false, false);
        }
        boolean pulled = hooked != Hooked.NONE;
        boolean discarded = hooked != Hooked.LIVE;
        return new Retrieve(after, onGround ? 2 : 1, false, discarded, warning, pulled, false);
    }

    /**
     * The velocity that pulls a hooked entity toward its owner, TFC {@code pullEntity}: the owner minus the hook, with the vertical
     * component set to 4 when the owner is above the hook and within 5 blocks on x and z, normalised and scaled by the strength.
     * A zero vector normalises to zero, as vanilla {@code Vec3.normalize} does.
     *
     * @param ownerX the owner's x
     * @param ownerY the owner's y
     * @param ownerZ the owner's z
     * @param hookX  the hook's x
     * @param hookY  the hook's y
     * @param hookZ  the hook's z
     * @param strength the pull strength of the rod, see {@link FishingRod#strength}
     * @return the velocity to set on the hooked entity
     */
    public static Velocity pullVelocity(double ownerX, double ownerY, double ownerZ, double hookX, double hookY, double hookZ, float strength) {
        double dx = ownerX - hookX;
        double dy = ownerY - hookY;
        double dz = ownerZ - hookZ;
        if (dy > 0 && dx < 5 && dz < 5) {
            dy = 4;
        }
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4) {
            return new Velocity(0, 0, 0);
        }
        double scale = strength / length;
        return new Velocity(dx * scale, dy * scale, dz * scale);
    }

    /**
     * The outcome of one retrieve.
     *
     * @param next       the next state of the hook
     * @param damage     the damage the rod takes: 2 on the ground, 1 in the air or when the pull is too hard, 0 without an owner pull
     * @param baitEaten  whether the bait is removed from the rod
     * @param discarded  whether the hook is removed
     * @param warning    whether the warning sound plays (a pull inside the window)
     * @param pulled     whether a hooked entity is pulled
     * @param tooHard    whether the pull exhausted the hook
     */
    public record Retrieve(Hook next, int damage, boolean baitEaten, boolean discarded, boolean warning, boolean pulled, boolean tooHard) {}

    /**
     * A velocity in blocks per tick.
     *
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    public record Velocity(double x, double y, double z) {}
}
