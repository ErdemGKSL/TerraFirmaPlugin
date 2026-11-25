/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (updateShape: no
 *       sturdy top below -> air)
 */
package dev.tfp.paper.bowl;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.placed.PlacedSupport;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TFC {@code BottomSupportedDeviceBlock.updateShape}: a bowl whose floor is no solid block any more becomes air and the
 * device framework drops its powder ({@code DROP}). Decided async on the neighbour-update runner from block ids only, as
 * {@link PlacedSupport}: the whole block counts as sturdy (TFC asks {@code isFaceSturdy(UP)} or its
 * {@code solid_top_face} tag; ledger).
 */
public record BowlSupport(Predicate<String> solid) implements ShapeRule<BlockStateRef> {
    public static final String RULE_ID = "tfp:bowl/support";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    public BowlSupport {
        Objects.requireNonNull(solid, "solid");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        return Set.of(BowlDevices.CERAMIC, BowlDevices.WOODEN);
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return BowlDevices.isBowl(state.id());
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        if (!appliesTo(n.center()) || !n.known(Direction.DOWN)) {
            return null;
        }
        BlockStateRef below = n.at(Direction.DOWN);
        return below != null && solid.test(below.id()) ? null : AIR;
    }
}
