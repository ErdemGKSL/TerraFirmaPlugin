/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (updateShape: a pile that cannot
 * survive becomes air; canSurvive: a full top face below, or an eight-layer pile)
 */
package dev.tfp.paper.charcoal;

import dev.tfp.core.charcoal.CharcoalPile;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

/**
 * TFC {@code CharcoalPileBlock.updateShape} / {@code canSurvive} as a shape rule of the neighbour runner (PAR-INFRA-10):
 * a charcoal pile whose block below has no full top face and is no eight-layer charcoal pile becomes air (no drop: TFC
 * replaces the state, it does not destroy the block). Decided async from the snapshot and applied by the runner's commit
 * queue; pure and idempotent.
 *
 * <p>The top-face test runs off the region thread, so it reads no world: {@link #VANILLA_TOP} parses a vanilla state
 * string once into Bukkit block data and caches whether its top face is sturdy ({@code isFaceSturdy(UP, FULL)}, which reads
 * the block's support shape; TFC tests {@code isFaceFull} of the collision shape, and the two differ for leaves and
 * {@code snow[layers=8]}: a recorded divergence). Both calls are safe off the region thread on Paper and Folia: parsing a
 * state string only reads the immutable block registry, and {@code CraftBlockData.isFaceSturdy} evaluates the state's
 * cached shape with an empty block getter, touching no world or chunk. A CraftEngine state below that is not a charcoal
 * pile is unknown off-thread (its
 * real collision is CraftEngine's), so the rule keeps the pile then; a pile below with fewer than eight layers is TFC's
 * merge case (the charcoal listener's), so the rule keeps it too.
 *
 * <p>{@link Module} registers the rule with the {@link NeighbourService} (its own line in the module service file).
 */
public final class CharcoalPileSupport implements ShapeRule<BlockStateRef> {
    public static final String RULE_ID = "tfp:charcoal/pile_support";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");
    static final String MINECRAFT = "minecraft";

    /** A full (sturdy, {@code FULL}) top face of a vanilla state string, parsed once per string. Thread-safe. */
    public static final Function<BlockStateRef, Optional<Boolean>> VANILLA_TOP = new VanillaTop();

    private final Function<BlockStateRef, Optional<Boolean>> fullTop;

    /**
     * @param fullTop whether a state has a full top face, or empty when that is unknown off the region thread
     *                (thread-safe; CharcoalPileSupport answers the charcoal pile itself)
     */
    public CharcoalPileSupport(Function<BlockStateRef, Optional<Boolean>> fullTop) {
        this.fullTop = Objects.requireNonNull(fullTop, "fullTop");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        return Set.of(CharcoalPitDevice.CHARCOAL_PILE);
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return state.id().equals(CharcoalPitDevice.CHARCOAL_PILE);
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        if (n.center() == null || !appliesTo(n.center()) || !n.known(Direction.DOWN)) {
            return null;
        }
        BlockStateRef below = n.at(Direction.DOWN);
        if (below == null) {
            return null;
        }
        if (below.id().equals(CharcoalPitDevice.CHARCOAL_PILE)) {
            // Eight layers below hold the pile (TFC canSurvive); fewer are TFC's merge case (updateShape merges before
            // canSurvive), which the charcoal listener does, so the rule never removes a pile standing on a pile.
            return null;
        }
        Optional<Boolean> full = fullTop.apply(below);
        if (full.isEmpty()) {
            return null;
        }
        return CharcoalPile.canSurvive(full.get(), 0) ? null : AIR;
    }

    /** The cached vanilla top-face lookup; any CraftEngine (non-minecraft) state is unknown. */
    static final class VanillaTop implements Function<BlockStateRef, Optional<Boolean>> {
        private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
        private final Function<String, Boolean> parse;

        VanillaTop() {
            this(s -> Bukkit.createBlockData(s).isFaceSturdy(BlockFace.UP, BlockSupport.FULL));
        }

        VanillaTop(Function<String, Boolean> parse) {
            this.parse = parse;
        }

        @Override
        public Optional<Boolean> apply(BlockStateRef state) {
            if (!MINECRAFT.equals(state.namespace())) {
                return Optional.empty();
            }
            String key = state.toString();
            Boolean known = cache.get(key);
            if (known == null) {
                try {
                    known = parse.apply(key);
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
                cache.putIfAbsent(key, known);
            }
            return Optional.of(known);
        }

        int size() {
            return cache.size();
        }
    }

    /**
     * Registers the rule with the neighbour runner. Needs the neighbour module before it in the service file; without it
     * (or without the CraftEngine charcoal pile) there is nothing to register.
     */
    public static final class Module implements FeatureModule {
        public static final String ID = "charcoal_pile_support";

        private volatile NeighbourService.Registration registration;

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void enable(ModuleContext context) {
            context.services().get(NeighbourService.class)
                .ifPresent(n -> registration = n.register(new CharcoalPileSupport(VANILLA_TOP)));
        }

        @Override
        public void disable() {
            NeighbourService.Registration r = registration;
            registration = null;
            if (r != null) {
                r.unregister();
            }
        }
    }
}
