/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (fuse, isLit, setLit,
 *       serverTick, PowderkegInventory.canModify),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java @ c3b4d9536 (toggleSeal, useItemOn,
 *       onPlace, neighborChanged, onBlockExploded)
 */
package dev.tfp.core.powderkeg;

import dev.tfp.core.persist.VersionedCodec;

/**
 * PAR-PROC-18: one powderkeg's state: TFC's {@code SEALED} and {@code LIT} block state properties and the block entity's
 * fuse. Lazy: TFC counts {@code fuse} down by one every server tick while lit; here the keg stores the tick its fuse
 * runs out ({@link #fuseEnd}, in the device clock) and explodes when a callback finds it due ({@link #due}). Immutable,
 * any thread.
 *
 * @param sealed  TFC {@code SealableDeviceBlock.SEALED}
 * @param lit     TFC {@code PowderkegBlock.LIT} (and {@code isLit})
 * @param fuseEnd the tick the fuse runs out while lit, {@link #NO_FUSE} otherwise (TFC {@code fuse = -1})
 */
public record PowderkegState(boolean sealed, boolean lit, long fuseEnd) {
    public static final long NO_FUSE = -1L;
    /** A new keg: unsealed, unlit (TFC's default state). */
    public static final PowderkegState FRESH = new PowderkegState(false, false, NO_FUSE);

    public static final VersionedCodec<PowderkegState> CODEC = VersionedCodec.<PowderkegState>builder("powderkeg", 1)
        .build((s, out) -> out.putBoolean("sealed", s.sealed()).putBoolean("lit", s.lit()).putLong("fuse", s.fuseEnd()),
            in -> new PowderkegState(in.getBoolean("sealed", false), in.getBoolean("lit", false),
                in.getLong("fuse", NO_FUSE)));

    public PowderkegState {
        if (lit != (fuseEnd != NO_FUSE)) {
            throw new IllegalArgumentException("a lit keg has a fuse end and an unlit one none: lit=" + lit + ", fuse=" + fuseEnd);
        }
    }

    /** TFC {@code PowderkegInventory.canModify}: the slots change only while unsealed. */
    public boolean canModify() {
        return !sealed;
    }

    /** TFC {@code serverTick}: the fuse has run out at {@code now} ({@code --fuse <= 0}). */
    public boolean due(long now) {
        return lit && now >= fuseEnd;
    }

    /**
     * TFC {@code setLit(true, igniter)} at {@code now}: lit, the fuse reset to {@code fuseTicks} (also when already lit,
     * as TFC). TFC's tick that lights the keg does not count it down, so the explosion is {@code fuseTicks} ticks later.
     */
    public PowderkegState lit(long now, int fuseTicks) {
        return new PowderkegState(sealed, true, now + fuseTicks);
    }

    /** TFC {@code setLit(false, ...)}: unlit, {@code fuse = -1}. */
    public PowderkegState unlit() {
        return new PowderkegState(sealed, false, NO_FUSE);
    }

    public PowderkegState withSealed(boolean seal) {
        return new PowderkegState(seal, lit, fuseEnd);
    }

    /** What a right click does, in TFC's {@code useItemOn} order. */
    public enum Click {
        /** Sneaking with an empty hand on a lit keg: {@code setLit(false)} and the extinguish sound. */
        EXTINGUISH,
        /** Sneaking with an empty hand on an unlit keg: {@code toggleSeal} and the place sound. */
        TOGGLE_SEAL,
        /** Anything else: the keg's screen opens. */
        OPEN
    }

    /** TFC {@code useItemOn}: {@code stack.isEmpty() && player.isShiftKeyDown()} first, else the menu. */
    public Click click(boolean emptyHand, boolean sneaking) {
        if (emptyHand && sneaking) {
            return lit ? Click.EXTINGUISH : Click.TOGGLE_SEAL;
        }
        return Click.OPEN;
    }

    /**
     * TFC {@code toggleSeal}: the seal flips; unsealing a lit keg puts it out first ({@link Toggle#extinguished}); sealing
     * runs the keg's {@code neighborChanged} (a redstone signal then lights it, {@link Toggle#checkSignal}).
     */
    public Toggle toggleSeal() {
        if (sealed) {
            return new Toggle(new PowderkegState(false, false, NO_FUSE), lit, false);
        }
        return new Toggle(withSealed(true), false, true);
    }

    /**
     * The result of {@link #toggleSeal}.
     *
     * @param state        the new state
     * @param extinguished a lit keg was put out (TFC plays the extinguish sound and {@code setLit(false)}'s cool sound)
     * @param checkSignal  the keg was sealed: a redstone signal at it lights it now
     */
    public record Toggle(PowderkegState state, boolean extinguished, boolean checkSignal) {
    }

    /** TFC {@code onPlace} / {@code neighborChanged}: a signal lights a sealed, unlit keg. */
    public boolean lightsFromSignal(boolean signal) {
        return signal && !lit && sealed;
    }

    /** TFC {@code onFireStart}'s powderkeg branch: a strong start on a sealed keg lights it (also when lit). */
    public boolean lightsFromFire(boolean strong) {
        return strong && sealed;
    }

    /** TFC {@code onBlockExploded}: another explosion lights an unlit keg (sealed or not) and does not break it. */
    public boolean lightsFromExplosion() {
        return !lit;
    }
}
