/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java @ c3b4d9536 (bait, loadAdditional, saveAdditional)
 */
package dev.tfp.core.lobstertrap;

import dev.tfp.core.fishing.LobsterTrap;
import dev.tfp.core.persist.VersionedCodec;

/**
 * The persisted state of one lobster trap (PAR-FAUNA-19): the bait units left (TFC's {@code bait}). The catch lives in the device's
 * six slots; {@link #contents} joins the two for the rules in {@link LobsterTrap}. Immutable, any thread.
 *
 * @param bait the bait units left, 0 to {@link LobsterTrap#BAIT_PER_ITEM}
 */
public record LobsterTrapState(int bait) {
    /** A new trap: no bait. */
    public static final LobsterTrapState FRESH = new LobsterTrapState(0);

    public static final VersionedCodec<LobsterTrapState> CODEC = VersionedCodec.<LobsterTrapState>builder("lobstertrap", 1)
        .build((s, out) -> out.putInt("bait", s.bait()), in -> new LobsterTrapState(Math.max(0, in.getInt("bait", 0))));

    public LobsterTrapState {
        if (bait < 0) {
            throw new IllegalArgumentException("bait " + bait);
        }
    }

    /** The contents the rules read, with {@code occupied} catch slots. */
    public LobsterTrap.Contents contents(int occupied) {
        return new LobsterTrap.Contents(Math.min(bait, Integer.MAX_VALUE), occupied);
    }

    /** The state after the rules changed the contents. */
    public static LobsterTrapState of(LobsterTrap.Contents contents) {
        return new LobsterTrapState(contents.bait());
    }
}
