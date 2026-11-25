/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCMagmaBlock.java @ c3b4d9536 (extends vanilla MagmaBlock: the hot floor)
 */
package dev.tfp.paper.magma;

import org.bukkit.GameMode;

/**
 * PAR-WORLD-45: the hot-floor rule of TFC's magma block, which extends vanilla's {@code MagmaBlock}: an entity standing on it
 * takes 1 damage ({@code hot_floor}) unless it is sneaking, wears Frost Walker boots or is fire immune. Pure.
 */
public final class MagmaRules {
    /** The id prefix of TFC's per-rock magma blocks ({@code tfp:rock/magma/<rock>}). */
    public static final String ID_PREFIX = "tfp:rock/magma/";
    /** Vanilla's {@code MagmaBlock.stepOn} damage. */
    public static final double DAMAGE = 1.0;

    private MagmaRules() {}

    /** @return Whether a CraftEngine block id is one of TFC's magma blocks. */
    public static boolean isMagma(String id) {
        return id != null && id.startsWith(ID_PREFIX) && id.length() > ID_PREFIX.length();
    }

    /**
     * @param mode        the player's game mode: creative and spectator take no environmental damage (PROTOCOL 4.7)
     * @param onGround    whether the player stands on the block
     * @param sneaking    vanilla's {@code isSteppingCarefully}
     * @param frostWalker whether the boots carry Frost Walker
     * @param fireImmune  fire resistance or any other fire immunity
     */
    public static boolean hurts(GameMode mode, boolean onGround, boolean sneaking, boolean frostWalker, boolean fireImmune) {
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        return onGround && !sneaking && !frostWalker && !fireImmune;
    }
}
