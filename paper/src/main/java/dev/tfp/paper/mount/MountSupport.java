/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java @ c3b4d9536 (tickAnimalData:
 * old age modifier, rejectPassengers)
 */
package dev.tfp.paper.mount;

import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.mount.MountInteract;
import dev.tfp.core.mount.MountRules;
import dev.tfp.paper.itemsize.BurdenApplier;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * What every horse-family carrier and camel shares (TFC {@code HorseProperties.tickAnimalData}): the old-age speed
 * modifier and throwing off an overburdened rider. Entity thread only.
 */
public final class MountSupport {
    /** TFC {@code OLD_AGE_MODIFIER}'s id {@code tfc:old_age}. */
    public static final org.bukkit.NamespacedKey OLD_AGE = new org.bukkit.NamespacedKey("tfp", "old_age");
    /** The lang key of the notice a thrown rider gets ({@code tfc.tooltip.animal.horse_angry_overburdened}). */
    public static final String OVERBURDENED_KEY = "mount.overburdened";

    private MountSupport() {
    }

    /** True when the animal has a rider or is itself ridden or a passenger (TFC {@code vanillaParentingCheck}'s negation). */
    public static boolean ridden(Entity e) {
        return !e.getPassengers().isEmpty() || e.isInsideVehicle();
    }

    /** TFC adds the -0.5 {@code ADD_MULTIPLIED_TOTAL} modifier once the animal is old and never removes it. */
    public static void applyOldAge(LivingEntity mount, LivestockAge age) {
        if (age != LivestockAge.OLD) {
            return;
        }
        AttributeInstance speed = mount.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(OLD_AGE) == null) {
            speed.addTransientModifier(new AttributeModifier(OLD_AGE, MountRules.OLD_AGE_SPEED_MODIFIER,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    /**
     * TFC {@code tickAnimalData}: a passenger with the overburdened effect is thrown off, the mount rears and the player is
     * told. Returns true when a rider was thrown.
     */
    public static boolean rejectOverburdened(LivingEntity mount, BiFunction<Player, String, String> messages) {
        List<Entity> riders = mount.getPassengers();
        for (Entity rider : riders) {
            if (rider instanceof LivingEntity living
                    && MountInteract.rejectsPassenger(living instanceof Player p && BurdenApplier.isImmobilised(p))) {
                mount.eject();
                angry(mount);
                if (living instanceof Player player) {
                    player.sendActionBar(Component.text(messages.apply(player, OVERBURDENED_KEY)));
                }
                return true;
            }
        }
        return false;
    }

    /** TFC {@code makeMad}: the animal's angry sound (an untamed or overburdened mount). */
    public static void angry(Entity mount) {
        String type = mount.getType().name().toLowerCase(Locale.ROOT);
        Sound sound = switch (type) {
            case "donkey" -> Sound.ENTITY_DONKEY_ANGRY;
            case "mule" -> Sound.ENTITY_MULE_ANGRY;
            case "camel" -> Sound.ENTITY_CAMEL_HURT;
            default -> Sound.ENTITY_HORSE_ANGRY;
        };
        mount.getWorld().playSound(mount.getLocation(), sound, 1.0f, 1.0f);
    }
}
