/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.coral;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.HandlerList;

/**
 * PAR-WORLD-25: the coral and sea pickle rules on the server: {@link CoralSupportQueue} (PAR-WORLD-50: the
 * {@link CoralSupport} rule with drops, instead of the neighbour runner, which writes air without drops) and
 * {@link CoralDeath} for living corals out of water. Both only act on the {@code tfp:coral/*} and {@code tfp:sea_pickle}
 * CraftEngine blocks, so without them nothing happens.
 */
public final class CoralModule implements FeatureModule {
    public static final String ID = "coral";

    private volatile CoralSupportQueue support;
    private volatile CoralDeath death;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        final CoralSupportQueue s = new CoralSupportQueue(context.scheduler(), context.integrations().customBlocks());
        context.listen(s);
        support = s;
        final CoralDeath d = new CoralDeath(context.scheduler(), context.integrations().customBlocks(),
            ThreadLocalRandom::current);
        context.listen(d);
        death = d;
    }

    @Override
    public void disable() {
        final CoralSupportQueue s = support;
        support = null;
        if (s != null) {
            HandlerList.unregisterAll(s);
        }
        final CoralDeath d = death;
        death = null;
        if (d != null) {
            HandlerList.unregisterAll(d);
        }
    }
}
