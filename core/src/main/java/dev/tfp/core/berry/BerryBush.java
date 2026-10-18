/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BerryBushBlockEntity.java @ c3b4d9536 (growthsRemaining),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickingPlantBlockEntity.java @ c3b4d9536 (lastPickedTick),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (lastUpdateTick)
 */
package dev.tfp.core.berry;

/**
 * The stored state of one stationary berry bush. The bush is never ticked: the state is the values TFC keeps on the block and its block
 * entity, and {@link BerryRules} brings it up to any later tick.
 *
 * @param lifecycle        the stored lifecycle stage
 * @param stage            the growth stage, {@code 0..2} (2 is fully grown)
 * @param growthsRemaining the growths left before the bush stops growing and spreading
 * @param lastUpdateTick   the player tick the growth counter counts from (TFC {@code lastUpdateTick}, may be banked into the past)
 * @param lastPickedTick   the player tick of the last pick, or of the placement
 */
public record BerryBush(Lifecycle lifecycle, int stage, int growthsRemaining, long lastUpdateTick, long lastPickedTick) {
    public BerryBush {
        if (lifecycle == null) {
            throw new IllegalArgumentException("lifecycle");
        }
        if (stage < 0 || stage > BerryRules.MAX_STAGE) {
            throw new IllegalArgumentException("stage must be in [0, " + BerryRules.MAX_STAGE + "]: " + stage);
        }
        if (growthsRemaining < 0) {
            throw new IllegalArgumentException("growthsRemaining must be >= 0: " + growthsRemaining);
        }
    }
}
