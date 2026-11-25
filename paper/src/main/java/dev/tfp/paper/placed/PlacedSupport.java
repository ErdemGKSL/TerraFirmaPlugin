/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java @ c3b4d9536 (updateShape: no supported
 *       slot left -> air)
 */
package dev.tfp.paper.placed;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.neighbour.ShapeRule;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TFC {@code PlacedItemBlock.updateShape}: when the block below no longer supports the placed items, the block becomes air
 * and the device framework drops the items ({@code DROP}). Decided async on the neighbour-update runner (PAR-INFRA-10) from
 * block ids only, so the rule is the whole block: a below that is no solid block (air, fluid, plants) removes it. TFC's
 * per-quadrant ejection when the shape below changes partly (a full block replaced by stairs) is not ported (ledger).
 */
public record PlacedSupport(Predicate<String> solid) implements ShapeRule<BlockStateRef> {
    public static final String RULE_ID = "tfp:placed_item/support";
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    /** @param solid whether a block id supports placed items (thread-safe) */
    public PlacedSupport {
        Objects.requireNonNull(solid, "solid");
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> blockIds() {
        return Set.of(PlacedItemDevice.ID);
    }

    @Override
    public boolean appliesTo(BlockStateRef state) {
        return state.id().equals(PlacedItemDevice.ID);
    }

    @Override
    public BlockStateRef decide(Neighbourhood<BlockStateRef> n) {
        if (!appliesTo(n.center()) || !n.known(Direction.DOWN)) {
            return null;
        }
        BlockStateRef below = n.at(Direction.DOWN);
        return below != null && solid.test(below.id()) ? null : AIR;
    }

    /**
     * The default support test: a vanilla id whose material is solid; any custom block id (CraftEngine terrain and devices
     * are real solid server blocks unless they are placed items themselves). Static data only, any thread.
     */
    public static boolean solidId(String id) {
        if (id.equals(PlacedItemDevice.ID)) {
            return false;
        }
        if (!id.startsWith("minecraft:")) {
            return true;
        }
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(id);
        return m != null && m.isBlock() && m.isSolid();
    }
}
