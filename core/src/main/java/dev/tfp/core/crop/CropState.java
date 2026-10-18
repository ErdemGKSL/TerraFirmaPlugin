/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CropBlockEntity.java @ c3b4d9536 (growth, expiry, yield, lastGrowthTick)
 */
package dev.tfp.core.crop;

/**
 * The stored state of one crop, as TFC's {@code CropBlockEntity} keeps it. The nutrients absorbed so far are the soil side and are
 * not here. {@code lastTick} is the calendar tick the state was last advanced to; it is the planting tick for a new crop.
 *
 * @param growth   growth from 0 to 1 (the limit), a fully grown crop is at 1
 * @param expiry   the expiry accumulator, rescaled to TFC's constant limit
 * @param yield    the yield from 0 to 1, from the nutrients satisfied while growing
 * @param lastTick the calendar tick this state is current to
 */
public record CropState(float growth, float expiry, float yield, long lastTick) {

    /** A crop planted at {@code tick}: no growth, no expiry, no yield yet. */
    public static CropState planted(long tick) {
        return new CropState(0f, 0f, 0f, tick);
    }
}
