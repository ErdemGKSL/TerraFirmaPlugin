/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java @ c3b4d9536 (updateShape: no scraping
 * surface below turns the block into air)
 */
package dev.tfp.paper.scraping;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TFC {@code ScrapingBlock.updateShape}: when the block below is no scraping surface any more (the log was broken or
 * replaced), the scraping block becomes air, and the device framework drops its item ({@code DROP}). Decided async on the
 * neighbour-update runner (PAR-INFRA-10) and applied by its commit queue; pure and idempotent.
 */
public record ScrapingSupport(Predicate<String> surface) implements ShapeRule<BlockStateRef> {
    public static final String RULE_ID = "tfp:scraping/support";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    /** @param surface whether a block id is in {@code tfc:scraping_surface} (thread-safe) */
    public ScrapingSupport {
        Objects.requireNonNull(surface, "surface");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        return Set.of(ScrapingDevice.ID);
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return state.id().equals(ScrapingDevice.ID);
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        if (!appliesTo(n.center()) || !n.known(Direction.DOWN)) {
            return null;
        }
        BlockStateRef below = n.at(Direction.DOWN);
        return below != null && surface.test(below.id()) ? null : AIR;
    }
}
