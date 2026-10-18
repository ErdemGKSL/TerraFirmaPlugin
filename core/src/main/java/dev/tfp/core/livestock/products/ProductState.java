/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/ProducingAnimal.java and
 * src/main/java/net/dries007/tfc/common/entities/livestock/ProducingMammal.java (the {@code produced} field) @ c3b4d9536
 */
package dev.tfp.core.livestock.products;

/**
 * The one stored product value: the player tick of the last harvest or lay ({@code DATA_PRODUCED}, NBT
 * {@code produced}). The cooldown is never stored. It is derived from this tick and the species' produce ticks by
 * {@link ProductRules#cooldownRemaining}.
 *
 * @param producedTick the tick of the last harvest, or {@link #NEVER}
 */
public record ProductState(long producedTick) {
    /** TFC default {@code DATA_PRODUCED == 0L}. The {@code <= 0} checks treat it as "never produced". */
    public static final long NEVER = 0L;

    /** A fresh animal: never harvested. */
    public static ProductState fresh() {
        return new ProductState(NEVER);
    }
}
