/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/sounds.json (entity.*) @ c3b4d9536
 */
package dev.tfp.paper.animal.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Animal sounds (PAR-FAUNA-20): the carriers are silent, so the plugin plays an animal's voice itself on a throttled
 * ambient timer (about once in 50 s per animal, from the staggered check), when it is hurt and when it dies. A species with
 * a TFC sound event ({@code tfp_animals:entity.<name>.ambient|hurt|death}, from TFC's {@code sounds.json}) plays that; any
 * other plays its carrier's vanilla sound.
 */
public final class AnimalSoundsModule implements FeatureModule, Listener {
    /** Chance per 20-tick check of an ambient sound: about one in 50 seconds. */
    static final double AMBIENT_CHANCE = 0.02;
    private ModuleContext ctx;
    private volatile Set<String> events = Set.of();

    @Override
    public String id() {
        return "animalsounds";
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        this.ctx = context;
        var file = RegistryService.jarDataRoot(AnimalSoundsModule.class.getClassLoader()).resolve("tfp/data_maps/animal_sounds.json");
        Set<String> names = new HashSet<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray()) {
            names.add(e.getAsString());
        }
        events = Set.copyOf(names);
        ((TfpPlugin) context.plugin()).animals().behaviours().register(new Ambient());
        context.listen(this);
    }

    /** The TFC sound event for {@code species} and {@code kind}, if TFC has one ({@code entity.bear.ambient} for all bears). */
    static Optional<String> event(Set<String> events, String species, String kind) {
        String name = species.substring(species.indexOf(':') + 1);
        for (String candidate : new String[] {name, name.replaceAll("^(black|grizzly|polar)_", ""),
                name.replaceAll("_camel$", "")}) {
            String key = "entity." + candidate + "." + kind;
            if (events.contains(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    void play(Entity e, String kind) {
        Optional<AnimalRecord> record = AnimalPdc.read(e.getPersistentDataContainer());
        if (record.isEmpty()) {
            return;
        }
        Optional<String> event = event(events, record.get().species(), kind);
        if (event.isPresent()) {
            e.getWorld().playSound(e.getLocation(), "tfp_animals:" + event.get(), SoundCategory.NEUTRAL, 1f, 1f);
            return;
        }
        if (e instanceof LivingEntity living) {
            Sound vanilla = switch (kind) {
                case "hurt" -> living.getHurtSound();
                case "death" -> living.getDeathSound();
                default -> living instanceof Mob mob ? mob.getAmbientSound() : null;
            };
            if (vanilla != null) {
                e.getWorld().playSound(e.getLocation(), vanilla, SoundCategory.NEUTRAL, 1f, 1f);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity living && !living.isDead() && event.getFinalDamage() > 0
                && living.getHealth() - event.getFinalDamage() > 0) {
            play(living, "hurt");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        play(event.getEntity(), "death");
    }

    private final class Ambient implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return dev.tfp.core.animal.CarrierTable.get().ids().stream().collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public void onCheck(Check check) {
            if (ThreadLocalRandom.current().nextDouble() < AMBIENT_CHANCE) {
                play(check.entity(), "ambient");
            }
        }
    }
}
