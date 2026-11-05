/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.neighbour.NeighbourSelfCheck;
import dev.tfp.paper.neighbour.NeighbourService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/**
 * {@code neighbour.converge} (PAR-INFRA-26): the neighbour-update runner (PAR-INFRA-10) on a real server. The body is
 * {@link NeighbourSelfCheck#run}: ten bone blocks placed provisionally converge to {@code axis=x} with one commit per
 * changed block, then breaking the second one re-decides the first through {@code BlockPhysicsEvent} with one more
 * commit.
 */
public final class NeighbourConvergeCase implements SelfTestCase {
    @Override
    public String name() {
        return "neighbour.converge: the bone-axis test rule converges with one commit per changed block "
                + "(PAR-INFRA-26)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(180);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        NeighbourService service = tfp.moduleServices().get(NeighbourService.class)
                .orElseThrow(() -> new AssertionError("no NeighbourService: the neighbour module is not enabled"));
        return NeighbourSelfCheck.run(service, tfp.scheduler(), Bukkit.getWorlds().getFirst());
    }
}
