/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Where the CraftEngine adapter publishes its {@link MiningDriver} (from its {@code onEnable}) and withdraws it (from
 * its {@code onDisable}). Read per dig start, so the order of adapter and module enabling does not matter.
 */
public final class MiningDrivers {
    private static final AtomicReference<MiningDriver> CURRENT = new AtomicReference<>(MiningDriver.NONE);

    private MiningDrivers() {
    }

    public static void install(MiningDriver driver) {
        CURRENT.set(Objects.requireNonNull(driver, "driver"));
    }

    /** Withdraws {@code driver} if it is the current one. */
    public static void uninstall(MiningDriver driver) {
        CURRENT.compareAndSet(driver, MiningDriver.NONE);
    }

    /** The installed driver, or {@link MiningDriver#NONE}. Any thread. */
    public static MiningDriver current() {
        return CURRENT.get();
    }
}
