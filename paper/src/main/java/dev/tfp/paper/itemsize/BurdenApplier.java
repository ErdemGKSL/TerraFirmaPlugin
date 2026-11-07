/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerTick: overburdening, onLivingJump),
 * src/main/java/net/dries007/tfc/common/effect/ExhaustedEffect.java @ c3b4d9536,
 * src/main/java/net/dries007/tfc/common/effect/TFCEffects.java @ c3b4d9536 (OVERBURDENED)
 */
package dev.tfp.paper.itemsize;

import dev.tfp.core.item.Burden;
import dev.tfp.core.item.BurdenEffects;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.paper.player.GameModeNeeds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

/**
 * TFC's exhausted and overburdened effects on a player (PAR-SURV-06), applied once per
 * {@value BurdenEffects#CHECK_PERIOD_TICKS}-tick reading on the player's own thread:
 * <ul>
 *   <li>the carry count of the whole inventory (main, armour, offhand) of huge, very heavy items ({@link Burden});</li>
 *   <li>{@link Burden#ONE}, exhausted: the food exhaustion TFC's effect causes in those 20 ticks
 *       ({@link BurdenEffects#exhaustedFoodExhaustion}) is added to the vanilla exhaustion;</li>
 *   <li>{@link Burden#MORE_THAN_ONE}, overburdened: transient attribute modifiers take the movement speed to 0
 *       ({@code -7.5} multiplied total, TFC's modifier) and the jump strength to 0 (TFC cancels every jump);
 *       otherwise they are removed;</li>
 *   <li>a change of state is told once on the action bar (a vanilla client cannot list a custom effect).</li>
 * </ul>
 * Creative and spectator carry no burden (TFC: {@code abilities.invulnerable}); adventure does.
 */
public final class BurdenApplier {
    /** The id of our transient attribute modifiers. */
    public static final NamespacedKey MODIFIER = new NamespacedKey("tfp", "overburdened");
    /** The lang keys of the action bar notices, {@code effect.overburdened} and {@code effect.exhausted}. */
    public static final String OVERBURDENED_KEY = "effect.overburdened";
    public static final String EXHAUSTED_KEY = "effect.exhausted";

    private final ItemSizes sizes;
    private final float exhaustion;
    private final BiConsumer<Player, Burden> notice;
    private final Map<UUID, Burden> last = new ConcurrentHashMap<>();

    /**
     * @param sizes  the size resolver
     * @param notice told on the player's thread when a player's burden changes to a non-zero state
     */
    public BurdenApplier(ItemSizes sizes, float passiveExhaustionModifier, BiConsumer<Player, Burden> notice) {
        this.sizes = Objects.requireNonNull(sizes, "sizes");
        this.exhaustion = BurdenEffects.exhaustedFoodExhaustion(passiveExhaustionModifier);
        this.notice = Objects.requireNonNull(notice, "notice");
    }

    /** The carry count of the player's inventory (TFC {@code Helpers.getCarryCount(player.getInventory())}). */
    public Burden burden(Player player) {
        List<ItemSizing> carried = new ArrayList<>();
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && !s.getType().isAir()) {
                carried.add(sizes.sizing(s));
            }
        }
        return Burden.of(carried);
    }

    /** Player thread: one reading. Returns the burden that was applied. */
    public Burden apply(Player player) {
        Burden burden = player.isDead() || !GameModeNeeds.needsActive(player.getGameMode()) ? Burden.NONE
                : burden(player);
        setImmobilised(player, BurdenEffects.immobilises(burden));
        if (BurdenEffects.exhausts(burden)) {
            player.setExhaustion(player.getExhaustion() + exhaustion);
        }
        Burden before = last.put(player.getUniqueId(), burden);
        if (burden != before && burden.isNonZero()) {
            notice.accept(player, burden);
        }
        return burden;
    }

    /** Removes our modifiers and forgets the player (quit, plugin disable). */
    public void clear(Player player) {
        setImmobilised(player, false);
        last.remove(player.getUniqueId());
    }

    /** Whether our overburdened modifiers are on the player now. */
    public static boolean isImmobilised(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(MODIFIER) != null;
    }

    private static void setImmobilised(Player player, boolean on) {
        modifier(player.getAttribute(Attribute.MOVEMENT_SPEED), on, BurdenEffects.OVERBURDENED_SPEED_MODIFIER);
        modifier(player.getAttribute(Attribute.JUMP_STRENGTH), on, -1.0);
    }

    private static void modifier(AttributeInstance attribute, boolean on, double amount) {
        if (attribute == null) {
            return;
        }
        boolean present = attribute.getModifier(MODIFIER) != null;
        if (on && !present) {
            attribute.addTransientModifier(new AttributeModifier(MODIFIER, amount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        } else if (!on && present) {
            attribute.removeModifier(MODIFIER);
        }
    }
}
