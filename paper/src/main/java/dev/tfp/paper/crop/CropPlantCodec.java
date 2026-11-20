/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CropBlockEntity.java @ c3b4d9536 (saveAdditional, loadAdditional)
 */
package dev.tfp.paper.crop;

import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.crop.CropState;
import dev.tfp.core.farm.NutrientGrowth;
import dev.tfp.core.persist.VersionedCodec;

/** The stored crop: growth, yield, expiry, the three absorbed nutrients and the calendar tick of the last growth. */
final class CropPlantCodec {
    static final VersionedCodec<CropPlant> CODEC = VersionedCodec.<CropPlant>builder("crop", 1)
        .build((c, out) -> out.putFloat("growth", c.state().growth())
                .putFloat("expiry", c.state().expiry())
                .putFloat("yield", c.state().yield())
                .putLong("tick", c.state().lastTick())
                .putFloat("n", c.absorbed().nitrogen())
                .putFloat("p", c.absorbed().phosphorus())
                .putFloat("k", c.absorbed().potassium()),
            in -> new CropPlant(new CropState(in.getFloat("growth", 0f), in.getFloat("expiry", 0f), in.getFloat("yield", 0f),
                in.getLong("tick", 0L)), new NutrientGrowth.Demand(in.getFloat("n", 0f), in.getFloat("p", 0f), in.getFloat("k", 0f))));

    private CropPlantCodec() {
    }
}
