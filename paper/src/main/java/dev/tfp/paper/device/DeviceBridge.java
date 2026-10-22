/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import java.util.Objects;

/**
 * The meeting point of the two halves of the device framework (PAR-INFRA-02), which are created at different moments:
 * the CraftEngine adapter installs its {@link DeviceStore} when it registers the {@code tfp:device} behaviour (our
 * {@code onLoad}), and the device module installs its {@link DeviceRouter} when it is enabled (our {@code onEnable}).
 * CraftEngine behaviour objects are rebuilt on every {@code /ce reload}, so they read the router here on each callback
 * instead of keeping it.
 *
 * <p>One plugin instance per server, so process-wide holders are enough; both are volatile and swapped whole.
 */
public final class DeviceBridge {
    private static volatile DeviceRouter router = DeviceRouter.NONE;
    private static volatile DeviceStore store = DeviceStore.ABSENT;

    private DeviceBridge() {
    }

    public static DeviceRouter router() {
        return router;
    }

    public static DeviceStore store() {
        return store;
    }

    public static void installRouter(DeviceRouter next) {
        router = Objects.requireNonNull(next, "router");
    }

    public static void installStore(DeviceStore next) {
        store = Objects.requireNonNull(next, "store");
    }

    /** Back to the inert router (module disabled). */
    public static void clearRouter(DeviceRouter installed) {
        if (router == installed) {
            router = DeviceRouter.NONE;
        }
    }
}
