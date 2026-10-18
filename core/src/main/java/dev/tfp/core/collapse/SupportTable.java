/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Support.java @ c3b4d9536 (isSupported, canSupport, findUnsupportedPositions,
 * the maximum support range), with the data of data/tfc/tfc/support/
 */
package dev.tfp.core.collapse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import dev.tfp.core.world.BlockPos;

/**
 * Which blocks are supported by the support blocks around them. A support of kind {@code k} with reach {@code up}, {@code down}
 * and {@code horizontal} supports every block whose position is within that box relative to the support. The search range is the
 * maximum reach over all supports, as in TFC.
 */
public final class SupportTable {
    /** One support kind and its reach, TFC's {@code support_up}, {@code support_down}, {@code support_horizontal}. */
    public record Support(Set<String> kinds, int up, int down, int horizontal) {
        public Support {
            kinds = Set.copyOf(kinds);
            if (up < 0 || down < 0 || horizontal < 0) {
                throw new IllegalArgumentException("Support reach must not be negative");
            }
        }

        /** TFC {@code Support.canSupport}: {@code testPos} is inside the reach of the support at {@code supportPos}. */
        boolean canSupport(BlockPos supportPos, BlockPos testPos) {
            int dx = supportPos.x() - testPos.x();
            int dy = supportPos.y() - testPos.y();
            int dz = supportPos.z() - testPos.z();
            return Math.abs(dx) <= horizontal && -down <= dy && dy <= up && Math.abs(dz) <= horizontal;
        }
    }

    private static final Comparator<BlockPos> ORDER = Comparator.comparingInt(BlockPos::x)
        .thenComparingInt(BlockPos::y)
        .thenComparingInt(BlockPos::z);

    private final List<Support> supports;
    private final int up;
    private final int down;
    private final int horizontal;

    public SupportTable(List<Support> supports) {
        this.supports = List.copyOf(supports);
        int u = 0;
        int d = 0;
        int h = 0;
        for (Support s : this.supports) {
            u = Math.max(u, s.up());
            d = Math.max(d, s.down());
            h = Math.max(h, s.horizontal());
        }
        this.up = u;
        this.down = d;
        this.horizontal = h;
    }

    /** The maximum reach over all supports (TFC's {@code RANGE}). */
    public int rangeUp() {
        return up;
    }

    public int rangeDown() {
        return down;
    }

    public int rangeHorizontal() {
        return horizontal;
    }

    /** The support of a kind, or empty. The first matching entry wins, as in TFC. */
    public Optional<Support> get(String kind) {
        for (Support s : supports) {
            if (s.kinds().contains(kind)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** TFC {@code Support.isSupported}: some support within range of {@code pos} supports it. */
    public boolean isSupported(CollapseView view, BlockPos pos) {
        for (int x = pos.x() - horizontal; x <= pos.x() + horizontal; x++) {
            for (int y = pos.y() - down; y <= pos.y() + up; y++) {
                for (int z = pos.z() - horizontal; z <= pos.z() + horizontal; z++) {
                    BlockPos sp = new BlockPos(x, y, z);
                    Optional<Support> s = get(view.cellAt(sp).kind());
                    if (s.isPresent() && s.get().canSupport(sp, pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Every position of the closed box {@code from}..{@code to} that no support reaches, sorted by x, y, z. TFC collects the same set
     * with a hash set whose iteration order is not reproducible; the sort is the one deliberate change.
     */
    public List<BlockPos> unsupported(CollapseView view, BlockPos from, BlockPos to) {
        int minX = Math.min(from.x(), to.x());
        int maxX = Math.max(from.x(), to.x());
        int minY = Math.min(from.y(), to.y());
        int maxY = Math.max(from.y(), to.y());
        int minZ = Math.min(from.z(), to.z());
        int maxZ = Math.max(from.z(), to.z());
        List<BlockPos> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isSupported(view, p)) {
                        out.add(p);
                    }
                }
            }
        }
        out.sort(ORDER);
        return out;
    }
}
