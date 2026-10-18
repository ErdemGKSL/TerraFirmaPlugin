/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PropickItem.java @ c3b4d9536
 */
package dev.tfp.core.prospect;

import dev.tfp.core.worldgen.Hash;
import dev.tfp.core.worldgen.ore.OreGrade;
import dev.tfp.core.worldgen.ore.VeinBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * The prospector pick rules as pure functions: the scan radius, the false negative chance, the rating thresholds, the representative
 * grouping of graded ores, and the decision of one use. Stateless, so any thread may call them.
 *
 * <p>Ported behaviour: a direct hit on a prospectable block is always found. Otherwise a random draw seeded by the clicked position
 * decides a false negative, then the cube scan counts the prospectable blocks by representative (poor and rich ores count with the
 * normal ore of their rock). The rating is by the count of the chosen representative.
 *
 * <p>Divergence: TFC picks the representative from a fastutil hash map, so its order depends on identity hashes. This port sorts the
 * representatives by id before the pick, so the result is reproducible. See {@code docs/porting/ledger.md}.
 */
public final class ProspectRules {
    /** TFC {@code PropickItem.RADIUS}: the scan is the cube of this half-width around the clicked block. */
    public static final int RADIUS = 12;
    /** TFC {@code PropickItem.COOLDOWN}: ticks of cooldown after each use. */
    public static final int COOLDOWN_TICKS = 10;
    /** TFC {@code Helpers.damageItem(stack, player, hand)}: one point of durability per use. */
    public static final int DAMAGE_PER_USE = 1;

    /** The salt of the per-position draw ({@code PropickItem.useOn}). */
    static final long SEED_SALT = 19827384739241223L;

    private ProspectRules() {}

    /** Whether an offset lies inside the scan cube, bounds included (TFC {@code BlockPos.betweenClosed}). */
    public static boolean inRadius(int dx, int dy, int dz) {
        return Math.abs(dx) <= RADIUS && Math.abs(dy) <= RADIUS && Math.abs(dz) <= RADIUS;
    }

    /**
     * The chance that a pick misses a block that is in range: {@code 0.3f - clamp(level, 0, 5) * (0.3f / 5f)}. Level 0 gives 0.3, level
     * 5 and above give 0.
     */
    public static float falseNegativeChance(int toolLevel) {
        return 0.3f - clamp(toolLevel, 0, 5) * (0.3f / 5f);
    }

    /** The rating of a count of the chosen representative in the scan: below 10 traces, 10 small, 20 medium, 40 large, 80 very large. */
    public static ProspectResult rate(int amount) {
        if (amount < 10) {
            return ProspectResult.TRACES;
        } else if (amount < 20) {
            return ProspectResult.SMALL;
        } else if (amount < 40) {
            return ProspectResult.MEDIUM;
        } else if (amount < 80) {
            return ProspectResult.LARGE;
        }
        return ProspectResult.VERY_LARGE;
    }

    /**
     * The representative block of a block id: a poor or rich graded ore maps to the normal ore of the same mineral and rock; any other
     * block maps to itself. TFC registers the same mapping with {@code PropickItem.registerRepresentative}.
     */
    public static String representative(String blockId) {
        VeinBlock block = VeinBlock.parse(blockId, "");
        if (block.kind() != VeinBlock.Kind.ORE || block.grade() == OreGrade.UNGRADED || block.grade() == OreGrade.NORMAL) {
            return blockId;
        }
        final int colon = blockId.indexOf(':');
        final String[] path = blockId.substring(colon + 1).split("/");
        return blockId.substring(0, colon) + ":ore/" + OreGrade.NORMAL.prefix() + block.mineral() + "/" + path[2];
    }

    /**
     * Decides one pick: a direct hit, a false negative, a miss, or the rating of the chosen representative in the scan cube.
     * Pure: the random draws come from a generator seeded by the clicked position, as in TFC.
     */
    public static ProspectOutcome decide(ProspectRequest request) {
        if (request.clickedProspectable()) {
            return new ProspectOutcome(ProspectResult.FOUND, request.clickedBlock());
        }
        final Random random = new Random(Hash.hash(SEED_SALT, request.x(), request.y(), request.z()));
        if (random.nextFloat() < falseNegativeChance(request.toolLevel())) {
            return new ProspectOutcome(ProspectResult.NOTHING, request.clickedBlock());
        }
        final TreeMap<String, Integer> counts = new TreeMap<>();
        for (ScanFact fact : request.cube()) {
            if (inRadius(fact.dx(), fact.dy(), fact.dz())) {
                counts.merge(representative(fact.block()), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return new ProspectOutcome(ProspectResult.NOTHING, request.clickedBlock());
        }
        final List<String> keys = new ArrayList<>(counts.keySet());
        final String found = keys.get(random.nextInt(keys.size()));
        return new ProspectOutcome(rate(counts.get(found)), found);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
