/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.arrow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

/** PAR-TOOL-10 on MockBukkit: a glow arrow's hit gives 200 ticks of glowing, a plain arrow nothing. */
class GlowArrowMockBukkitTest extends MockServerTest {
    // a spectral arrow item stands in as "tfp:glow_arrow"
    private final GlowArrowListener listener = new GlowArrowListener(stack -> stack.getType() == Material.SPECTRAL_ARROW
        ? Optional.of("tfp:glow_arrow") : Optional.empty());

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> {
            Object r = h.apply(m.getName(), a);
            if (r != null) {
                return r;
            }
            return switch (m.getName()) {
                case "hashCode" -> 1;
                case "equals" -> p == a[0];
                case "getUniqueId" -> java.util.UUID.nameUUIDFromBytes(type.getName().getBytes());
                default -> m.getReturnType() == boolean.class ? (Object) false : null;
            };
        });
    }

    private java.util.List<org.bukkit.potion.PotionEffect> hit(Material arrowItem) {
        java.util.List<org.bukkit.potion.PotionEffect> effects = new java.util.ArrayList<>();
        Arrow arrow = proxy(Arrow.class, (n, a) -> n.equals("getItemStack") ? new ItemStack(arrowItem) : null);
        Cow cow = proxy(Cow.class, (n, a) -> {
            if (n.equals("addPotionEffect")) {
                effects.add((org.bukkit.potion.PotionEffect) a[0]);
                return true;
            }
            return null;
        });
        listener.onHit(new EntityDamageByEntityEvent(arrow, cow, EntityDamageEvent.DamageCause.PROJECTILE, 2.0));
        return effects;
    }

    @Test
    void aGlowArrowGlowsTheTargetFor200Ticks() {
        var effects = hit(Material.SPECTRAL_ARROW);
        assertThat(effects).hasSize(1);
        assertThat(effects.get(0).getType()).isEqualTo(PotionEffectType.GLOWING);
        assertThat(effects.get(0).getDuration()).isEqualTo(200);
    }

    @Test
    void aPlainArrowDoesNot() {
        assertThat(hit(Material.ARROW)).isEmpty();
    }
}
