/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.shield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ShieldListenerTest extends dev.tfp.paper.testing.MockServerTest {
    private final ShieldListener listener = new ShieldListener(s -> s.getType() == Material.IRON_INGOT
        ? Optional.of("tfp:metal/shield/steel") : Optional.empty());

    @Test
    void metalFromId() {
        assertThat(ShieldListener.metal("tfp:metal/shield/copper")).contains("copper");
        assertThat(ShieldListener.metal("tfp:metal/shield_x/copper")).isEmpty();
        assertThat(ShieldListener.metal(null)).isEmpty();
    }

    @Test
    void factors() {
        assertThat(listener.factor(new ItemStack(Material.IRON_INGOT))).hasValueSatisfying(
            f -> assertThat(f).isCloseTo(0.25f + 0.75f * 5.75f / 12f, within(1e-6f)));
        assertThat(listener.factor(new ItemStack(Material.SHIELD))).contains(0.25f);
        assertThat(listener.factor(new ItemStack(Material.STICK))).isEmpty();
        assertThat(listener.factor(null)).isEmpty();
    }
}
