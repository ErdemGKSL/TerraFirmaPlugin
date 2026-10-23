/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.core.catchup.CatchUpAdvance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;

/**
 * A fake lazy block for tests and the self-test (not registered in production): vanilla wheat whose age is a pure
 * function of the calendar, {@code min(7, now / }{@value #TICKS_PER_STAGE}{@code )}. It changes only through the
 * catch-up service, so a test can move the calendar, trigger a chunk load or a sweep, and watch the age follow.
 */
public final class ProbeCatchUp {
    public static final String ID = "tfp:catchup/probe";
    /** Player ticks per growth stage. */
    public static final long TICKS_PER_STAGE = 1_000;

    private ProbeCatchUp() {
    }

    /** The age the probe has at {@code now}. */
    public static int ageAt(long now) {
        return (int) Math.max(0, Math.min(7, now / TICKS_PER_STAGE));
    }

    /** The probe kind (state = the wheat age). */
    public static CatchUpKind<Integer> kind() {
        CatchUpAdvance<Integer> advance = (age, now) -> Math.max(age, ageAt(now));
        return CatchUpKind.of(ID, (world, pos) -> {
            Block b = world.getBlockAt(pos.x(), pos.y(), pos.z());
            return b.getType() == Material.WHEAT && b.getBlockData() instanceof Ageable a ? a.getAge() : null;
        }, (world, pos, age) -> {
            Block b = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Ageable a = (Ageable) b.getBlockData();
            a.setAge(age);
            b.setBlockData(a, false);
        }, advance);
    }
}
