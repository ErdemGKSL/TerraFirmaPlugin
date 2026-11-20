/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.coral;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.HandlerList;

/**
 * PAR-WORLD-25: the coral and sea pickle rules on the server: {@link CoralSupport} on the neighbour runner (needs the
 * neighbour module before it in the service file) and {@link CoralDeath} for living corals out of water. Both only act on
 * the {@code tfp:coral/*} and {@code tfp:sea_pickle} CraftEngine blocks, so without them nothing happens.
 */
public final class CoralModule implements FeatureModule {
    public static final String ID = "coral";

    private volatile NeighbourService.Registration registration;
    private volatile CoralDeath death;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.services().get(NeighbourService.class)
            .ifPresent(n -> registration = n.register(new CoralSupport(CoralSupport.VANILLA)));
        final CoralDeath d = new CoralDeath(context.scheduler(), context.integrations().customBlocks(),
            ThreadLocalRandom::current);
        context.listen(d);
        death = d;
    }

    @Override
    public void disable() {
        final NeighbourService.Registration r = registration;
        registration = null;
        if (r != null) {
            r.unregister();
        }
        final CoralDeath d = death;
        death = null;
        if (d != null) {
            HandlerList.unregisterAll(d);
        }
    }
}
