/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536 (useItemOn: the order of the four uses),
 *       src/main/java/net/dries007/tfc/common/blockentities/BowlBlockEntity.java @ c3b4d9536 (MAX_POWDER, one slot),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/BowlBlockEntityRenderer.java @ c3b4d9536 (the powder layer:
 *       texture per item, fallback salt, height)
 */
package dev.tfp.core.bowl;

import java.util.Map;

/**
 * The decisions of a bowl block (wooden or ceramic) that holds one stack of up to {@value #MAX_POWDER} powder. Pure: the
 * caller states what the player holds and what the bowl contains.
 *
 * <p>TFC {@code BowlBlock.useItemOn} tries, in this order: (1) a held item with glassworking data takes one powder of the
 * bowl that matches a glass operation; (2) an empty main hand takes the powder (one, sixteen when sneaking); (3) a held
 * bowl powder is put in; (4) a held food that can be salted takes as many salt as it has items. Anything else passes.
 */
public final class BowlRules {
    /** {@code BowlBlockEntity.MAX_POWDER}. */
    public static final int MAX_POWDER = 16;
    /** The salt powder item (the only powder that salts food; also the renderer's fallback texture). */
    public static final String SALT = "tfp:powder/salt";
    /** Powders sneaking takes in one go ({@code isShiftKeyDown() ? 16 : 1}). */
    public static final int TAKE_SNEAKING = 16;
    public static final int TAKE_ONE = 1;

    /** TFC {@code player.getCooldowns().addCooldown(held.getItem(), 10)} after a glass powder was added. */
    public static final int GLASS_COOLDOWN_TICKS = 10;

    /** The renderer's texture of the vanilla powders ({@code BowlBlockEntityRenderer.TEXTURES}). */
    private static final Map<String, String> VANILLA_TEXTURE = Map.of(
        "minecraft:redstone", "redstone",
        "minecraft:glowstone_dust", "glowstone",
        "minecraft:blaze_powder", "blaze_powder",
        "minecraft:gunpowder", "gunpowder");

    private BowlRules() {
    }

    /** What the player holds and does, and what the bowl holds. */
    public record Situation(boolean mainHand, boolean sneaking, boolean heldEmpty, boolean heldGlassWork, boolean heldPowder,
            boolean heldSaltable, boolean contentEmpty, boolean contentSalt, boolean contentGlassOp) {
    }

    /** What a right click does. */
    public sealed interface Action {
        /** Nothing (TFC {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}). */
        record Pass() implements Action {
        }

        /** (1) The bowl's powder goes into the held glass batch: one powder is used up. */
        record ApplyGlass() implements Action {
        }

        /** (2) The player takes up to {@code count} powder out of the bowl (nothing when it is empty, but the click is used). */
        record Take(int count) implements Action {
        }

        /** (3) The held powder goes in as far as the bowl takes it. */
        record Insert() implements Action {
        }

        /** (4) The held food is salted, as many items as the bowl holds salt. */
        record Salt() implements Action {
        }
    }

    public static Action decide(Situation s) {
        if (s.heldGlassWork()) {
            return s.contentGlassOp() ? new Action.ApplyGlass() : new Action.Pass();
        }
        if (s.heldEmpty() && s.mainHand()) {
            return new Action.Take(s.sneaking() ? TAKE_SNEAKING : TAKE_ONE);
        }
        if (s.heldPowder()) {
            return new Action.Insert();
        }
        if (s.heldSaltable() && s.contentSalt() && !s.contentEmpty()) {
            return new Action.Salt();
        }
        return new Action.Pass();
    }

    /**
     * {@code Helpers.insertAllSlots} into the one slot: how many of {@code held} the bowl takes. A slot that holds another
     * powder takes none.
     */
    public static int accepted(int stored, boolean sameItem, int held) {
        if (held <= 0 || (stored > 0 && !sameItem)) {
            return 0;
        }
        return Math.max(0, Math.min(held, MAX_POWDER - stored));
    }

    /** The items a salting uses: {@code Math.min(held.getCount(), current.getCount())}. */
    public static int toSalt(int held, int stored) {
        return Math.max(0, Math.min(held, stored));
    }

    /**
     * The name of the texture {@code block/powder/<name>} that draws {@code itemId} ({@code tfc:} and {@code tfp:} are the
     * same). TFC's TFC powders and ore powders are {@code powder/<name>}; unknown items use the salt texture.
     */
    public static String texture(String itemId) {
        String id = itemId.startsWith("tfc:") ? "tfp:" + itemId.substring(4) : itemId;
        String vanilla = VANILLA_TEXTURE.get(id);
        if (vanilla != null) {
            return vanilla;
        }
        if (id.startsWith("tfp:powder/") && id.length() > "tfp:powder/".length()) {
            return id.substring("tfp:powder/".length());
        }
        return "salt";
    }

    /**
     * The height of the powder layer in 16ths of a block: {@code Mth.map(count, 0, 16, 0.5, 2)} of the renderer (a bowl's
     * floor is 0.5 high, its rim 3).
     */
    public static double layerHeight(int count) {
        int c = Math.max(0, Math.min(MAX_POWDER, count));
        return 0.5 + (2.0 - 0.5) * c / MAX_POWDER;
    }
}
