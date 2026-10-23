/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.Optional;

/**
 * PAR-INFRA-11: the catch-up trigger service on the module SPI. Provides {@link CatchUpService} to later modules
 * ({@code services().require(CatchUpService.class)}, then {@code kinds().register(...)} and {@code track(block, kind)}
 * on placement), listens for chunk load/unload, interactions and breaks, and runs the visible-area sweep. When the
 * device module is enabled it registers the direct kind {@value #DEVICE_KIND} ({@code DeviceService.catchUp}), so a
 * device indexed under that kind is caught up on chunk load. Must be listed before every module that registers kinds.
 */
public final class CatchUpModule implements FeatureModule {
    public static final String ID = "catchup";
    /** The kind id devices are indexed under. */
    public static final String DEVICE_KIND = "tfp:device";

    private volatile CatchUpSweep sweep;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        CatchUpSettings settings = CatchUpSettings.fromProperties(System::getProperty);
        CatchUpService service = new CatchUpService(new CatchUpKinds(), context.chunkData(), context::regions,
                context.scheduler().cpuExecutor(), () -> context.clock().get().playerTicks(), System::nanoTime,
                settings, context.logger());
        Optional<DeviceService> devices = context.services().get(DeviceService.class);
        devices.ifPresent(d -> service.kinds().registerDirect(DEVICE_KIND,
                (world, pos) -> d.catchUp(world.getBlockAt(pos.x(), pos.y(), pos.z())).isPresent()));
        context.services().provide(CatchUpService.class, service);
        context.listen(new CatchUpListener(service));
        CatchUpSweep s = new CatchUpSweep(service, context.scheduler(),
                () -> context.plugin().getServer().getOnlinePlayers());
        s.start();
        sweep = s;
    }

    @Override
    public void disable() {
        CatchUpSweep s = sweep;
        sweep = null;
        if (s != null) {
            s.stop();
        }
    }
}
