/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rock;

import dev.tfp.core.rock.MossSpread;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;

/**
 * PAR-GATHER-06: mossy rock spreading, lazily. Mossy cobble/brick blocks are indexed under {@value MossService#KIND} when
 * placed (or converted) and caught up on chunk load, interaction and the visible-area sweep by the catch-up service
 * ({@link MossService}). Must come after {@code CatchUpModule} in the service file.
 */
public final class MossModule implements FeatureModule {
    public static final String ID = "moss";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        CatchUpService catchUp = context.services().require(CatchUpService.class);
        int rate = Integer.getInteger("tfp.moss.rate", MossSpread.DEFAULT_RATE);
        boolean enabled = Boolean.parseBoolean(System.getProperty("tfp.moss.enabled", "true"));
        MossService service = new MossService(context.integrations().customBlocks(), catchUp::track,
                context.chunkData(), () -> context.clock().get().playerTicks(), enabled, Math.max(1, rate));
        catchUp.kinds().registerDirect(MossService.KIND, service::catchUp);
        context.services().provide(MossService.class, service);
        context.listen(new MossListener(service, context.scheduler()));
    }
}
