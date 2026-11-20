/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java @ c3b4d9536 (updateShape,
 *       canSurvive), src/main/java/net/dries007/tfc/common/blocks/plant/coral/CoralWallFanBlock.java @ c3b4d9536
 *       (updateShape, canSurvive), src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCSeaPickleBlock.java
 *       @ c3b4d9536 (updateShape, canSurvive, mayPlaceOn)
 */
package dev.tfp.paper.coral;

import dev.tfp.core.coral.CoralRules;
import dev.tfp.core.coral.CoralRules.Coral;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockSupport;

/**
 * TFC's coral and sea pickle {@code updateShape} as a shape rule of the neighbour runner (PAR-INFRA-10): a coral or fan
 * whose block below has no sturdy top, a wall fan whose block behind has no sturdy face toward it, and a sea pickle whose
 * block below has neither a sturdy nor a non-empty top face become air. Decided async from the snapshot, applied by the
 * runner's commit queue; pure and idempotent. A CraftEngine state as support is unknown off the region thread, so the rule
 * keeps the block then (as {@code CharcoalPileSupport}).
 */
public final class CoralSupport implements ShapeRule<BlockStateRef> {
    public static final String RULE_ID = "tfp:coral/support";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    /** The face tests of a support state: sturdy (FULL), and non-empty on top (sea pickle). */
    public interface Faces {
        /** {@code isFaceSturdy(face)}, or empty when unknown off the region thread. */
        Optional<Boolean> sturdy(BlockStateRef state, Direction face);

        /** Whether the top face of the collision shape is non-empty, or empty when unknown. */
        Optional<Boolean> topNonEmpty(BlockStateRef state);
    }

    /** The vanilla faces: each state string parsed once into Bukkit block data (thread-safe; see CharcoalPileSupport). */
    public static final Faces VANILLA = new VanillaFaces((s, f) -> Bukkit.createBlockData(s).isFaceSturdy(f, BlockSupport.FULL),
        s -> {
            final BlockData d = Bukkit.createBlockData(s);
            // non-empty top: any support type holds somewhere on the top face (CENTER covers posts and thin tops)
            return d.isFaceSturdy(BlockFace.UP, BlockSupport.FULL) || d.isFaceSturdy(BlockFace.UP, BlockSupport.CENTER)
                || d.isFaceSturdy(BlockFace.UP, BlockSupport.RIGID);
        });

    private final Faces faces;

    public CoralSupport(Faces faces) {
        this.faces = Objects.requireNonNull(faces, "faces");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        return CoralStates.IDS;
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return CoralStates.IDS.contains(state.id());
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        final BlockStateRef center = n.center();
        if (center == null || !appliesTo(center)) {
            return null;
        }
        if (center.id().equals(CoralRules.SEA_PICKLE)) {
            final BlockStateRef below = n.at(Direction.DOWN);
            if (below == null) {
                return null;
            }
            final Optional<Boolean> sturdy = faces.sturdy(below, Direction.UP);
            final Optional<Boolean> nonEmpty = faces.topNonEmpty(below);
            if (sturdy.isEmpty() || nonEmpty.isEmpty()) {
                return null;
            }
            return CoralRules.seaPickleCanSurvive(nonEmpty.get(), sturdy.get()) ? null : AIR;
        }
        final Coral coral = Coral.parse(center.id()).orElseThrow();
        final Direction facing = CoralStates.facing(center);
        final BlockStateRef support = n.at(CoralRules.support(coral.type(), facing));
        if (support == null) {
            return null;
        }
        final Optional<Boolean> sturdy = faces.sturdy(support, CoralRules.supportFace(coral.type(), facing));
        return sturdy.isEmpty() || CoralRules.canSurvive(sturdy.get()) ? null : AIR;
    }

    /** Cached vanilla lookups; any non-minecraft state is unknown. */
    static final class VanillaFaces implements Faces {
        private final Map<String, Boolean> sturdy = new ConcurrentHashMap<>();
        private final Map<String, Boolean> top = new ConcurrentHashMap<>();
        private final BiFunction<String, BlockFace, Boolean> parseSturdy;
        private final Function<String, Boolean> parseTop;

        VanillaFaces(BiFunction<String, BlockFace, Boolean> parseSturdy, Function<String, Boolean> parseTop) {
            this.parseSturdy = parseSturdy;
            this.parseTop = parseTop;
        }

        @Override
        public Optional<Boolean> sturdy(BlockStateRef state, Direction face) {
            if (!"minecraft".equals(state.namespace())) {
                return Optional.empty();
            }
            final BlockFace f = BlockFace.valueOf(face.name());
            return cached(sturdy, state + "|" + f, () -> parseSturdy.apply(state.toString(), f));
        }

        @Override
        public Optional<Boolean> topNonEmpty(BlockStateRef state) {
            if (!"minecraft".equals(state.namespace())) {
                return Optional.empty();
            }
            return cached(top, state.toString(), () -> parseTop.apply(state.toString()));
        }

        private static Optional<Boolean> cached(Map<String, Boolean> cache, String key,
                Supplier<Boolean> compute) {
            Boolean known = cache.get(key);
            if (known == null) {
                try {
                    known = compute.get();
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
                cache.putIfAbsent(key, known);
            }
            return Optional.of(known);
        }
    }
}
