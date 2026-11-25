/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Pluckable.java @ c3b4d9536
 */
package dev.tfp.paper.pluck;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;

/**
 * Plucking (PAR-FAUNA-20, TFC {@code Pluckable}; the decision is {@link PluckRules}): sneak and right-click a game bird or poultry with an empty hand for 1 to 3
 * feathers, once per calendar hour, at 15% of its health as pluck damage. Livestock must be adults that are not elderly
 * ({@code uses < usesToElderly}) and each plucking is one use. The animal must have more than 15% health left.
 */
public final class PluckModule implements FeatureModule, Listener {
    /** TFC's {@code Pluckable}s: {@code OviparousAnimal} (chicken, duck, quail) and {@code WingedPrey}. */
    static final Set<String> PLUCKABLE = Set.of("tfc:chicken", "tfc:duck", "tfc:quail", "tfc:grouse", "tfc:pheasant",
            "tfc:turkey", "tfc:peafowl");
    private static final NamespacedKey PLUCKED = new NamespacedKey("tfp", "animal_plucked");

    private ModuleContext ctx;

    @Override
    public String id() {
        return "pluck";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        context.listen(this);
    }

    /** The calendar ticks left on the cooldown of a bird last plucked at {@code lastPlucked} (0 when never plucked). */
    static long remaining(long lastPlucked, long calendarTicks) {
        return PluckRules.remaining(lastPlucked, calendarTicks);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking()
                || !player.getInventory().getItemInMainHand().isEmpty()
                || !(event.getRightClicked() instanceof LivingEntity bird)) {
            return;
        }
        AnimalService animals = ((TfpPlugin) ctx.plugin()).animals();
        Optional<AnimalRecord> record = animals.record(bird);
        ClockSnapshot now = ctx.clock().get();
        if (record.isEmpty() || !PLUCKABLE.contains(record.get().species()) || !now.isSet()) {
            return;
        }
        double max = bird.getAttribute(Attribute.MAX_HEALTH).getValue();
        Long last = bird.getPersistentDataContainer().get(PLUCKED, PersistentDataType.LONG);
        Optional<LivestockSpecies> livestock = animals.table().find(record.get().species()).flatMap(Carrier::livestock);
        boolean fresh = livestock.isPresent()
                && AnimalCheck.ageAt(record.get(), now.playerTicks()) == LivestockAge.ADULT
                && record.get().state().uses() < livestock.get().usesToElderly();
        PluckRules.Outcome outcome = PluckRules.decide((float) (bird.getHealth() / max), last == null ? 0L : last,
                now.calendarTicks(), livestock.isPresent(), fresh);
        switch (outcome) {
            case IGNORE -> {
                return;
            }
            case COOLDOWN -> {
                long left = remaining(last, now.calendarTicks());
                hint(player, "pluck.cooldown", Math.max(1, left * 60 / CalendarTicks.CALENDAR_TICKS_IN_HOUR));
                return;
            }
            case WORN_OUT -> {
                hint(player, "pluck.sick");
                return;
            }
            case PLUCK -> {
            }
        }
        event.setCancelled(true);
        AnimalRecord updated = livestock.isPresent()
                ? record.get().withState(record.get().state().withUses(record.get().state().uses() + 1)) : record.get();
        AnimalPdc.write(bird.getPersistentDataContainer(), updated);
        bird.getPersistentDataContainer().set(PLUCKED, PersistentDataType.LONG, now.calendarTicks());
        int count = 1 + ThreadLocalRandom.current().nextInt(3);
        player.getInventory().addItem(new ItemStack(Material.FEATHER, count)).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        DamageType type = Registry.DAMAGE_TYPE.get(NamespacedKey.fromString("tfp:pluck"));
        bird.damage(max * PluckRules.DAMAGE_FRACTION, DamageSource.builder(type != null ? type : org.bukkit.damage.DamageType.GENERIC).build());
    }

    private void hint(Player player, String key, Object... args) {
        String locale = player.locale().toString().toLowerCase(Locale.ROOT);
        player.sendActionBar(Component.text(ctx.messages().get(locale, key, args)));
    }
}
