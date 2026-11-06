/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.oreoverlay.OreOverlaySelfCheck;
import dev.tfp.paper.oreoverlay.OreOverlayService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/**
 * {@code ore.overlay} (PAR-INFRA-16): the ore-overlay runtime on a real server (Paper and Folia). The body is
 * {@link OreOverlaySelfCheck#run}: place/break around a CraftEngine ore converges to the full build, a clear and an
 * explosion leave no overlay id behind, every world read on its owner region.
 */
public final class OreOverlayCase implements SelfTestCase {
    @Override
    public String name() {
        return "ore.overlay: exposed-face overlays converge after place/break, clear and explosion (PAR-INFRA-16)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(240);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        OreOverlayService service = tfp.moduleServices().get(OreOverlayService.class)
                .orElseThrow(() -> new Skip("the ore overlay module is off (CraftEngine or PacketEvents missing)"));
        return OreOverlaySelfCheck.run(service, tfp.integrations().customBlocks(), tfp.scheduler(),
                Bukkit.getWorlds().getFirst());
    }
}
