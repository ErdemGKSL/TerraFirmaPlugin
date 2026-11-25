/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/Seat.java @ c3b4d9536 (sit, getSittingEntity, stopRiding)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestock.products.ProductSpec;
import dev.tfp.core.livestock.products.ProductState;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * PAR-FAUNA-07: the nest box's view of the hen that sits in it (TFC {@code Seat.getSittingEntity}), so the device rules
 * run without a live entity in tests. All calls on the region thread that owns the nest block.
 */
public interface NestHens {
    /**
     * A sitting hen as the lay rules need her.
     *
     * @param id      the hen's entity id
     * @param species the carrier id ({@code tfc:chicken}); stored on the egg so the chick is the same species
     * @param product the hen's product state
     */
    record Hen(String id, String species, LivestockSpecies livestock, ProductSpec spec, AnimalState state,
            ProductState product) {
    }

    /** The hen {@code id} if she is still at {@code nest}: alive, loaded and within the seat's reach. */
    Optional<Hen> find(Block nest, String id);

    /** The hen laid: her state and product cooldown are stored and she stands up (TFC {@code setFertilized}, {@code setProductsCooldown}, {@code stopRiding}). */
    void laid(Block nest, Hen hen, AnimalState after, ProductState product);

    /** The hen stands up and goes on (TFC {@code stopRiding}). */
    void release(Block nest, String id);
}
