/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class TfcSpawnWorldRulesTest extends MockServerTest {
    @Test
    void ruleNamesResolve() {
        assertNotNull(TfcWorldRules.find("natural_health_regeneration", "naturalRegeneration"));
        assertNotNull(TfcWorldRules.find("spawn_phantoms", "doInsomnia"));
    }

    @Test
    void forcedRulesAppliedOnce() {
        World w = server.addSimpleWorld("w");
        int first = TfcWorldRules.apply(w);
        assertTrue(first > 0);
        assertEquals(0, TfcWorldRules.apply(w));
        assertEquals(Boolean.FALSE, w.getGameRuleValue(TfcWorldRules.find("spawn_phantoms", "doInsomnia")));
    }

    @Test
    void portalsOffByDefault() {
        assertTrue(!TfcWorldRules.netherPortalsEnabled());
        assertTrue(TfcWorldRules.forcedEnabled());
    }
}
