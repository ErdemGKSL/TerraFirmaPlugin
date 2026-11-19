/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.javelin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-TOOL-05 on MockBukkit: a trident carrying a javelin item deals TFC's flat 1.5 x tier bonus. */
class JavelinMockBukkitTest extends MockServerTest {
    private final JavelinListener listener = new JavelinListener(stack -> stack.getType() == Material.TRIDENT
        ? Optional.of("tfp:metal/javelin/copper") : Optional.empty());

    private ItemStack javelin(double modifier) {
        ItemStack s = new ItemStack(Material.TRIDENT);
        s.editMeta(m -> m.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
            new NamespacedKey("tfp", "t"), modifier, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)));
        return s;
    }

    @Test
    void idsAreTheJavelinFamilies() {
        assertThat(JavelinListener.isJavelinId("tfp:stone/javelin/sedimentary")).isTrue();
        assertThat(JavelinListener.isJavelinId("tfp:metal/javelin/steel")).isTrue();
        assertThat(JavelinListener.isJavelinId("tfp:obsidian_javelin")).isTrue();
        assertThat(JavelinListener.isJavelinId("tfp:stone/javelin_head/sedimentary")).isFalse();
        assertThat(JavelinListener.isJavelinId("tfp:metal/sword/steel")).isFalse();
    }

    @Test
    void copperJavelinThrowsFor4875() {
        assertThat(listener.thrownDamage(javelin(0.7f * 3.25f))).hasValueSatisfying(d -> assertThat(d).isEqualTo(4.875f));
        assertThat(listener.thrownDamage(new ItemStack(Material.STICK))).isEmpty();
    }

    @Test
    void aThrownJavelinHitReplacesTheTridentDamage() {
        PlayerMock player = server.addPlayer();
        var world = player.getWorld();
        Trident trident = world.spawn(player.getLocation(), Trident.class);
        trident.setItemStack(javelin(0.7f * 3.25f));
        Cow cow = world.spawn(player.getLocation().add(2, 0, 0), Cow.class);
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(trident, cow, EntityDamageEvent.DamageCause.PROJECTILE, 20.0);
        listener.onHit(hit);
        assertThat(hit.getDamage()).isCloseTo(4.875, within(1e-4));
    }
}
