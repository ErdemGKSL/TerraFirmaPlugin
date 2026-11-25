/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java @ c3b4d9536 (toggleSeal, neighborChanged),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (SEALED, POWERED,
 *       handleNeighborChanged, getStateForPlacement)
 */
package dev.tfp.core.largevessel;

import dev.tfp.core.persist.VersionedCodec;

/**
 * PAR-POT-06: one large vessel's state, TFC's {@code SEALED} and {@code POWERED} block state properties. The vessel has
 * no tick and no timer: sealing is instant ({@code LargeVesselRules}). Immutable, any thread.
 *
 * @param sealed  {@code SealableDeviceBlock.SEALED}
 * @param powered {@code SealableDeviceBlock.POWERED}: the redstone signal the last neighbour update saw
 */
public record LargeVesselState(boolean sealed, boolean powered) {
    /** A new vessel: open, unpowered (TFC's default state). */
    public static final LargeVesselState FRESH = new LargeVesselState(false, false);

    public static final VersionedCodec<LargeVesselState> CODEC = VersionedCodec.<LargeVesselState>builder("large_vessel", 1)
        .build((s, out) -> out.putBoolean("sealed", s.sealed()).putBoolean("powered", s.powered()),
            in -> new LargeVesselState(in.getBoolean("sealed", false), in.getBoolean("powered", false)));

    /** TFC {@code VesselInventory.canModify}: the slots change only while open. */
    public boolean canModify() {
        return !sealed;
    }

    /** What a seal change asks of the contents (TFC {@code onSeal} / {@code onUnseal}). */
    public enum Seal {
        NONE, SEAL, UNSEAL
    }

    /**
     * One change of state and the seal action it carries.
     *
     * @param state the new state
     * @param seal  {@link Seal#SEAL} when the vessel was just sealed, {@link Seal#UNSEAL} when just opened
     */
    public record Change(LargeVesselState state, Seal seal) {
    }

    /** TFC {@code LargeVesselBlock.toggleSeal}: the seal flips ({@code POWERED} stays). */
    public Change toggleSeal() {
        return new Change(new LargeVesselState(!sealed, powered), sealed ? Seal.UNSEAL : Seal.SEAL);
    }

    /**
     * TFC {@code SealableDeviceBlock.handleNeighborChanged} for a signal of {@code signal}: when it differs from
     * {@code POWERED}, a signal that differs from {@code SEALED} too seals (or opens) the vessel as well; one that agrees
     * only records {@code POWERED}. No change at all when the signal equals {@code POWERED}.
     *
     * @return the change, or {@code null} when nothing changes
     */
    public Change neighbourSignal(boolean signal) {
        if (signal == powered) {
            return null;
        }
        if (signal != sealed) {
            return new Change(new LargeVesselState(signal, signal), signal ? Seal.SEAL : Seal.UNSEAL);
        }
        return new Change(new LargeVesselState(sealed, signal), Seal.NONE);
    }

    /**
     * TFC {@code SealableDeviceBlock.getStateForPlacement}: {@code POWERED} follows the signal at the position; a stack
     * that carries {@code tfc:contents} places sealed.
     */
    public static LargeVesselState placed(boolean sealedItem, boolean signal) {
        return new LargeVesselState(sealedItem, signal);
    }
}
