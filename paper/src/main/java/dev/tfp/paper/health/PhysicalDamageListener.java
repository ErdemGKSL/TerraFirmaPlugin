/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onLivingHurt)
 */
package dev.tfp.paper.health;

import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionLevels;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.health.IncomingDamage;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.player.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-SURV-05: TFC's {@code onLivingHurt} on Paper. On every {@link EntityDamageEvent} of a living entity (the target's
 * thread) the hit is snapshotted into an {@link IncomingDamage.Hit} (damage type, causing entity, its main-hand weapon,
 * the target's armour, the player's health modifier), the pure rule computes the new damage and
 * {@link EntityDamageEvent#setDamage(double)} sets it as the base damage. Bukkit then rescales the vanilla armour,
 * enchantment and absorption modifiers from that base, which is where TFC's incoming-damage event sits in vanilla's
 * order (before armour).
 *
 * <p>Priority {@code HIGH}, ignoring cancelled events: other plugins' cancellations and base-damage changes at lower
 * priorities are seen, and {@code MONITOR} listeners see the final value.
 *
 * <p>Folia: the causing entity of a projectile can be in another region; its main hand is then not read (the typing
 * falls through to the entity type tags, as for an empty hand).
 */
public final class PhysicalDamageListener implements Listener {
    /** The damage multiplier of a weapon's forging bonus; PAR-METAL items provide it, 1 until then. */
    @FunctionalInterface
    public interface WeaponDamageBonus {
        float damage(ItemStack weapon);

        WeaponDamageBonus NONE = weapon -> 1f;
    }

    private final Supplier<DamageResistances> resistances;
    private final Supplier<TagLookup> tags;
    private final Function<ItemStack, Optional<String>> itemIds;
    private final Function<UUID, Optional<PlayerData>> playerData;
    private final Supplier<WeaponDamageBonus> bonus;
    private final Predicate<Entity> owned;
    private final Supplier<Consumer<String>> debug;

    /**
     * @param resistances the loaded resistance tables
     * @param tags        the tag lookup (our tags plus the server's vanilla tags)
     * @param itemIds     the item id of a stack (CraftEngine id, else the vanilla key)
     * @param playerData  the loaded player data, for the nutrition health modifier
     * @param bonus       the forging bonus source of the weapon
     * @param owned       whether the current thread owns an entity (Folia region check)
     * @param debug       where to write one line per hit, or {@code null} when the debug log is off
     */
    public PhysicalDamageListener(Supplier<DamageResistances> resistances, Supplier<TagLookup> tags,
            Function<ItemStack, Optional<String>> itemIds, Function<UUID, Optional<PlayerData>> playerData,
            Supplier<WeaponDamageBonus> bonus, Predicate<Entity> owned, Supplier<Consumer<String>> debug) {
        this.resistances = Objects.requireNonNull(resistances, "resistances");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.itemIds = Objects.requireNonNull(itemIds, "itemIds");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.bonus = Objects.requireNonNull(bonus, "bonus");
        this.owned = Objects.requireNonNull(owned, "owned");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        float amount = (float) event.getDamage();
        IncomingDamage.Hit hit = snapshot(target, event.getDamageSource(), amount);
        IncomingDamage.Result result = IncomingDamage.apply(hit, resistances.get(), tags.get());
        if (result.unchanged()) {
            return;
        }
        event.setDamage(result.amount());
        Consumer<String> log = debug.get();
        if (log != null) {
            log.accept(String.format(Locale.ROOT,
                    "damage %s -> %s: %s %.3f x bonus %.3f, type %s, resistance %.2f, factor %.4f, health modifier %s = %.3f",
                    hit.attackerType().orElse("-"), hit.targetType(), hit.damageTypeId(), amount, hit.forgingBonus(),
                    result.type().map(t -> t.serializedName()).orElse("none"), result.resistance(),
                    result.multiplier(), hit.healthModifier().map(m -> String.format(Locale.ROOT, "%.3f", m))
                            .orElse("-"), result.amount()));
        }
    }

    /** Target thread: the hit as the pure rule reads it. */
    IncomingDamage.Hit snapshot(LivingEntity target, DamageSource source, float amount) {
        String damageType = source.getDamageType().getKey().toString();
        Entity causing = source.getCausingEntity();
        Optional<String> attackerType = Optional.empty();
        Optional<String> weapon = Optional.empty();
        float forging = 1f;
        if (causing != null && causing.getType() != EntityType.UNKNOWN) {
            attackerType = Optional.of(causing.getType().getKey().toString());
            if (causing instanceof LivingEntity living && owned.test(living)) {
                EntityEquipment equipment = living.getEquipment();
                ItemStack hand = equipment == null ? null : equipment.getItemInMainHand();
                if (hand != null && !hand.getType().isAir()) {
                    weapon = itemIds.apply(hand);
                    forging = bonus.get().damage(hand);
                }
            }
        }
        List<String> armor = new ArrayList<>(4);
        EntityEquipment equipment = target.getEquipment();
        if (equipment != null) {
            for (ItemStack piece : equipment.getArmorContents()) {
                if (piece != null && !piece.getType().isAir()) {
                    itemIds.apply(piece).ifPresent(armor::add);
                }
            }
        }
        Optional<Float> modifier = target instanceof Player player
                ? Optional.of(healthModifier(playerData.apply(player.getUniqueId()), player.getFoodLevel()))
                : Optional.empty();
        return new IncomingDamage.Hit(amount, damageType, attackerType, weapon, forging,
                target.getType().getKey().toString(), armor, modifier);
    }

    /**
     * TFC {@code getHealthModifier} of the stored nutrition at the vanilla food level, the same value the health bar
     * uses; without stored data, the modifier of TFC's average nutrition. TFC always has the player's data; here it can be
     * missing only before the data has loaded (or for a player the store never saw), and the average is TFC's own
     * starting value, so the fallback matches a fresh TFC player.
     */
    static float healthModifier(Optional<PlayerData> stored, int foodLevel) {
        NutritionConfig config = NutritionConfig.DEFAULT;
        if (stored.isEmpty()) {
            return NutritionLevels.healthModifier(NutritionLevels.DEFAULT_AVERAGE_NUTRITION, config);
        }
        List<FoodData> foods = new ArrayList<>(stored.get().nutrition().size());
        for (PlayerData.FoodRecord r : stored.get().nutrition()) {
            PlayerData.Nutrients n = r.nutrients();
            foods.add(new FoodData(r.hunger(), r.water(), r.saturation(), r.intoxication(), n.grain(), n.fruit(),
                    n.vegetables(), n.protein(), n.dairy(), r.decayModifier()));
        }
        int hunger = Math.clamp(foodLevel, 0, NutritionState.MAX_HUNGER);
        return new NutritionState(foods).levels(hunger, config).healthModifier(config);
    }
}
