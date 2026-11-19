/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prospect;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.prospect.ProspectOutcome;
import dev.tfp.core.prospect.ProspectRequest;
import dev.tfp.core.prospect.ProspectResult;
import dev.tfp.core.prospect.ProspectRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/** PAR-TOOL-08 on MockBukkit: the scan cube, the decision and TFC's message for a fixture world. */
class PropickMockBukkitTest extends MockServerTest {
    /** Gold ore (vanilla ore blocks) stands in for the prospectable CE ores. */
    private final PropickListener listener = new PropickListener(stack -> Optional.empty(),
        block -> block.getType() == Material.GOLD_ORE ? Optional.of(BlockStateRef.of("tfp:ore/normal_native_gold/granite"))
            : Optional.empty(), (state, tag) -> tag.equals("tfc:prospectable"));

    @Test
    void theScanFindsEveryOreInTheCubeAndRatesTheCount() {
        var world = server.addSimpleWorld("prospect");
        Block clicked = world.getBlockAt(0, 64, 0);
        clicked.setType(Material.STONE);
        for (int i = 0; i < 25; i++) {
            world.getBlockAt(i % 5 - 2, 60 + i / 5, 3).setType(Material.GOLD_ORE);
        }
        world.getBlockAt(30, 64, 0).setType(Material.GOLD_ORE); // outside the radius
        ProspectRequest request = listener.request(clicked, 4);
        assertThat(request.clickedProspectable()).isFalse();
        assertThat(request.cube()).hasSize(25);
        ProspectOutcome outcome = ProspectRules.decide(request);
        assertThat(outcome.result()).isIn(ProspectResult.MEDIUM, ProspectResult.NOTHING); // 25 blocks: medium unless a false negative
        if (outcome.result() == ProspectResult.MEDIUM) {
            assertThat(outcome.block()).isEqualTo("tfp:ore/normal_native_gold/granite");
        }
    }

    @Test
    void aDirectHitIsFoundWithoutAScan() {
        var world = server.addSimpleWorld("prospect2");
        Block ore = world.getBlockAt(0, 64, 0);
        ore.setType(Material.GOLD_ORE);
        ProspectRequest request = listener.request(ore, 0);
        assertThat(request.clickedProspectable()).isTrue();
        assertThat(request.cube()).isEmpty();
        assertThat(ProspectRules.decide(request).result()).isEqualTo(ProspectResult.FOUND);
    }

    @Test
    void messagesAreTfcsKeys() {
        var found = (TranslatableComponent) PropickListener.message(new ProspectOutcome(ProspectResult.SMALL,
            "tfp:ore/normal_native_gold/granite"));
        assertThat(found.key()).isEqualTo("tfc.tooltip.propick.found_small");
        assertThat(((TranslatableComponent) found.arguments().get(0).asComponent()).key())
            .isEqualTo("block.tfc.ore.normal_native_gold.granite.prospected");
        assertThat(((TranslatableComponent) PropickListener.message(new ProspectOutcome(ProspectResult.NOTHING, "x:y"))).key())
            .isEqualTo("tfc.tooltip.propick.nothing");
    }
}
