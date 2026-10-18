/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java @ c3b4d9536 (useItemOn: pickup gives the drops to a
 * non-creative player), with the loot tables of src/main/resources/data/tfc/loot_table/blocks/{groundcover,rock/loose,rock/mossy_loose}/
 */
package dev.tfp.core.gather;

import java.util.List;
import java.util.Objects;

/**
 * The pickup and break yields of groundcover pieces and loose rocks. Pure and thread-safe.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>No tool is required. Neither {@code GroundcoverBlock} nor {@code LooseRockBlock} (nor the {@code LOOSE} and
 *       {@code MOSSY_LOOSE} rock types) declares {@code requiresCorrectToolForDrops}, and their loot tables have no tool
 *       condition. A tool never gates the drop; {@link #requiresCorrectTool} is false for every piece.</li>
 *   <li>A groundcover piece yields its item once. A loose rock yields its own item (plain or mossy) with the count of its
 *       {@code count} property, 1 to 3.</li>
 *   <li>Right-click pickup ({@code GroundcoverBlock.useItemOn}) gives the drops to the player unless the player is in creative.
 *       The held item does not matter, not even an empty hand, because no tool gates the drop.</li>
 * </ul>
 */
public final class GatherRules {
    private GatherRules() {}

    /** TFC declares no correct-tool requirement on any groundcover or loose rock block. */
    public static boolean requiresCorrectTool(GatherPiece piece) {
        Objects.requireNonNull(piece, "piece");
        return false;
    }

    /**
     * The items a piece yields when it breaks, given whether the held tool matches the piece's required tool. Because
     * {@link #requiresCorrectTool} is false, {@code toolMatches} never changes the result: the piece's loot table applies in
     * every tool state.
     */
    public static List<GatherYield> drops(GatherPiece piece, boolean toolMatches) {
        if (requiresCorrectTool(piece) && !toolMatches) {
            return List.of();
        }
        return List.of(entry(piece));
    }

    /**
     * The items right-click pickup gives the player (TFC {@code GroundcoverBlock.useItemOn}): the drops, unless the player is in
     * creative mode. There is no tool, so the pickup always yields the drops of the piece otherwise.
     */
    public static List<GatherYield> pickup(GatherPiece piece, boolean creative) {
        return creative ? List.of() : drops(piece, false);
    }

    /**
     * The items a piece yields when a player breaks it with the given tool state. In creative mode the vanilla break drops
     * nothing ({@code Block.playerWillDestroy}), so this returns none; otherwise it is {@link #drops}.
     */
    public static List<GatherYield> breakDrops(GatherPiece piece, boolean toolMatches, boolean creative) {
        if (creative) {
            return List.of();
        }
        return drops(piece, toolMatches);
    }

    private static GatherYield entry(GatherPiece piece) {
        return switch (piece) {
            case GatherPiece.Groundcover groundcover -> new GatherYield(groundcover.type().itemId(), 1);
            case GatherPiece.LooseRock loose -> new GatherYield(
                loose.mossy() ? loose.rock().mossyItemId() : loose.rock().itemId(), loose.count());
        };
    }
}
