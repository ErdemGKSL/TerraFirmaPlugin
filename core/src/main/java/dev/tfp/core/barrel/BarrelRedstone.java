/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (handleNeighborChanged)
 */
package dev.tfp.core.barrel;

/**
 * PAR-PROC-42: TFC's redstone rule for sealable devices ({@code SealableDeviceBlock.handleNeighborChanged}). Only an
 * edge of the signal against the recorded {@code POWERED} acts: when the signal then differs from the seal, the seal
 * follows the signal (on seals, off unseals); otherwise only the recorded signal changes.
 */
public final class BarrelRedstone {
    /** What to do after a signal reading. */
    public enum Action {
        /** Nothing: the signal equals the recorded one. */
        NONE,
        /** Record the signal only. */
        RECORD,
        /** Record the signal and seal ({@code onSeal}). */
        SEAL,
        /** Record the signal and unseal ({@code onUnseal}). */
        UNSEAL
    }

    private BarrelRedstone() {
    }

    /**
     * @param powered the recorded signal ({@code POWERED})
     * @param sealed  whether the barrel is sealed
     * @param signal  {@code level.hasNeighborSignal(pos)} now
     */
    public static Action decide(boolean powered, boolean sealed, boolean signal) {
        if (signal == powered) {
            return Action.NONE;
        }
        if (signal != sealed) {
            return signal ? Action.SEAL : Action.UNSEAL;
        }
        return Action.RECORD;
    }
}
