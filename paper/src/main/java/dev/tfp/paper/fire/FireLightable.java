/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (cancelling the event),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart)
 */
package dev.tfp.paper.fire;

/**
 * The SPI that replaces listening to TFC's {@code StartFireEvent}: a device that can be lit by a fire starter (firepit,
 * pot, grill, log pile, pit kiln, charcoal forge, bloomery, blast furnace, firebox, torch, candle, lamp, powder keg...)
 * registers one with {@link FireStarting#register}. Every start asks the lightables in registration order before TFC's
 * own rules (firepit building, fire block), as TFC posts the event first.
 *
 * <p>Called on the region thread that owns {@link FireStartContext#block()}, for weak and strong starts alike: check
 * {@link FireStartContext#strong()} where TFC's handler checks {@code isStrong()}.
 */
@FunctionalInterface
public interface FireLightable {
    /**
     * Lights the targeted device if it is one of this lightable's. Returns {@code true} when the start was handled (TFC
     * cancels the event): no other lightable is asked, no firepit is built and no fire block is placed.
     */
    boolean light(FireStartContext context);
}
