/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCRabbit.java @ c3b4d9536 (RaidGardenGoal extends MoveToBlockGoal),
 *       src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java @ c3b4d9536 (TFCFoxEatBerriesGoal)
 * and net.minecraft.world.entity.ai.goal.MoveToBlockGoal.findNearestBlock (vanilla 1.21.1)
 */
package dev.tfp.core.wildraid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The order in which vanilla's {@code MoveToBlockGoal.findNearestBlock} tries the blocks around an animal: for each vertical
 * step {@code k} (0, 1, -1, 2, -2, ... up to the vertical range) every ring {@code l} of Chebyshev radius 0 to
 * {@code range - 1}, the offset {@code (i, k - 1, j)}. The first block that is a valid target is the goal's target, so the
 * list is nearest-first within a level.
 */
public final class RaidSearch {
    /** One offset from the animal's block position. */
    public record Offset(int dx, int dy, int dz) {
    }

    private static final ConcurrentMap<Long, List<Offset>> CACHE = new ConcurrentHashMap<>();

    private RaidSearch() {
    }

    /**
     * The offsets of a goal with the given horizontal {@code range} and {@code vertical} range, in the order the vanilla
     * search tries them. Each cell appears once. Immutable and cached.
     */
    public static List<Offset> offsets(int range, int vertical) {
        if (range < 1 || vertical < 0) {
            throw new IllegalArgumentException("range " + range + ", vertical " + vertical);
        }
        return CACHE.computeIfAbsent(((long) range << 32) | vertical, k -> List.copyOf(build(range, vertical)));
    }

    private static List<Offset> build(int range, int vertical) {
        List<Offset> out = new ArrayList<>();
        for (int k = 0; k <= vertical; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < range; l++) {
                for (int i = 0; i <= l; i = i > 0 ? -i : 1 - i) {
                    for (int j = i < l && i > -l ? l : 0; j <= l; j = j > 0 ? -j : 1 - j) {
                        out.add(new Offset(i, k - 1, j));
                    }
                }
            }
        }
        return out;
    }
}
