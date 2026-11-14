/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.piston.PistonFluidSelfCheck;
import dev.tfp.paper.piston.PistonFluidService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/**
 * {@code piston.no_water_dupe} (PAR-DATA-17): the piston fluid rule (PAR-DATA-16, TFC's MC-130183 fix) on a real
 * server. The body is {@link PistonFluidSelfCheck#run}: a real sticky piston given a 1-tick pulse spits a waterlogged
 * slab, which ends dry with one commit and no water source left; a held pulse lands it dry with no commit.
 */
public final class PistonNoWaterDupeCase implements SelfTestCase {
    @Override
    public String name() {
        return "piston.no_water_dupe: a real 1-tick piston pulse moves no water source (PAR-DATA-17)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(180);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        PistonFluidService service = tfp.moduleServices().get(PistonFluidService.class)
                .orElseThrow(() -> new AssertionError("no PistonFluidService: the piston_fluid module is not enabled"));
        return PistonFluidSelfCheck.run(service, tfp.scheduler(), Bukkit.getWorlds().getFirst());
    }
}
