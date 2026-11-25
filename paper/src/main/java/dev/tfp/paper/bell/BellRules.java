/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java @ c3b4d9536 (attemptToRing: pitch, hammer, volume 2)
 */
package dev.tfp.paper.bell;

import dev.tfp.core.world.Direction;
import java.util.Map;
import java.util.Objects;

/**
 * PAR-METAL-18: the pure rules of the bells: TFC's pitch and volume of the ring, and vanilla {@code BellBlock}'s placement
 * ({@code getStateForPlacement}: the attachment and facing from the clicked face). No Bukkit imports; any thread.
 */
public final class BellRules {
    /** {@code level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, ...)}. */
    public static final float VOLUME = 2.0f;
    /** The pitch of a hard hit (a hammer in the main hand) is this much lower. */
    public static final float HARD_PITCH_DROP = 0.1f;
    /** TFC's constructor pitch of each bell (TFCBlocks: BRONZE_BELL 0.8f, BRASS_BELL 0.6f). */
    public static final Map<String, Float> PITCH = Map.of("bronze", 0.8f, "brass", 0.6f);

    private BellRules() {
    }

    /** {@code hard ? pitch - 0.1f : pitch}. */
    public static float pitch(String metal, boolean hard) {
        Float base = PITCH.get(metal);
        if (base == null) {
            throw new IllegalArgumentException("no bell metal " + metal);
        }
        return hard ? base - HARD_PITCH_DROP : base;
    }

    /** The block id of a bell metal. */
    public static String id(String metal) {
        return "tfp:" + metal + "_bell";
    }

    /** The metal of a bell block id, or null for any other id. */
    public static String metal(String blockId) {
        for (String metal : PITCH.keySet()) {
            if (id(metal).equals(blockId)) {
                return metal;
            }
        }
        return null;
    }

    /** The attachment and facing a placed bell takes, as the state property values. */
    public record Placement(String attachment, String facing) {
    }

    /**
     * Vanilla {@code BellBlock.getStateForPlacement}: on the top or bottom face the bell stands or hangs, facing the player's
     * horizontal direction; on a side face it is on a single wall facing away from the clicked face, or between two walls when
     * the blocks on both sides of the placed position hold it (sturdy faces).
     *
     * @param clicked    the face of the block that was clicked (the placed bell sits on this side of it)
     * @param horizontal the player's horizontal direction
     * @param sturdy     whether the neighbour of the placed position in the given direction has a sturdy face toward it
     */
    public static Placement place(Direction clicked, Direction horizontal, java.util.function.Predicate<Direction> sturdy) {
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(horizontal, "horizontal");
        if (clicked == Direction.UP || clicked == Direction.DOWN) {
            return new Placement(clicked == Direction.DOWN ? "ceiling" : "floor", name(horizontal));
        }
        boolean alongX = clicked == Direction.EAST || clicked == Direction.WEST;
        boolean between = alongX
            ? sturdy.test(Direction.WEST) && sturdy.test(Direction.EAST)
            : sturdy.test(Direction.NORTH) && sturdy.test(Direction.SOUTH);
        return new Placement(between ? "double_wall" : "single_wall", name(clicked.opposite()));
    }

    static String name(Direction d) {
        return d.name().toLowerCase(java.util.Locale.ROOT);
    }
}
