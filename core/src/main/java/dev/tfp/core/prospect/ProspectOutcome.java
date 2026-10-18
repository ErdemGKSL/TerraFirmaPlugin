/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PropickItem.java @ c3b4d9536
 */
package dev.tfp.core.prospect;

/**
 * The decided result of one pick. Immutable.
 *
 * @param result the rating or the hit/miss result
 * @param block  the block the message names: the clicked block for {@link ProspectResult#FOUND} and {@link ProspectResult#NOTHING}
 *               (TFC keeps the clicked block as its default), or the representative block of the rated amount for a traces, small,
 *               medium, large or very large result
 */
public record ProspectOutcome(ProspectResult result, String block) {}
