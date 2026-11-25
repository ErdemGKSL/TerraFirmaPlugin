/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/camel/DromedaryCamel.java @ c3b4d9536 (createAttributes),
 * src/main/java/net/dries007/tfc/common/entities/livestock/camel/AbstractCamel.java (mobInteract, vanillaParentingCheck),
 * src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java (tickAnimalData)
 */
package dev.tfp.paper.camel;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.mount.MountRules;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.mount.MountSupport;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Camels (PAR-FAUNA-16): the dromedary and the bactrian camel on the vanilla camel carrier. Both get TFC's attributes
 * ({@code DromedaryCamel.createAttributes}, which TFC registers for both: 32 health, speed 0.1, jump 0.42, step height 1.5).
 * Riding is vanilla's (a camel needs no taming in TFC: its {@code mobInteract} only tries the feed step and then vanilla);
 * an old camel moves at half speed and a rider with the overburdened modifier is thrown off, as for the horse family
 * ({@link MountSupport}). Breeding of two camels of one kind, with the livestock rules, is the livestock check
 * (PAR-FAUNA-05), which pairs only one species.
 *
 * <p>Threads: add and check run on the camel's owning thread.
 */
public final class CamelModule implements FeatureModule {
    /** The two camel species. */
    public static final Set<String> SPECIES = Set.of("tfc:dromedary_camel", "tfc:bactrian_camel");

    private BiFunction<Player, String, String> messages;

    @Override
    public String id() {
        return "camel";
    }

    @Override
    public void enable(ModuleContext context) {
        this.messages = (p, key) -> context.messages().get(p.locale().toString().toLowerCase(Locale.ROOT), key);
        ((TfpPlugin) context.plugin()).animals().behaviours().register(behaviour());
    }

    /** Test seam. */
    void initForTest(BiFunction<Player, String, String> messages) {
        this.messages = messages;
    }

    SpeciesBehaviour behaviour() {
        return new SpeciesBehaviour() {
            @Override
            public Set<String> species() {
                return SPECIES;
            }

            @Override
            public boolean keepVanillaGoals() {
                return true;
            }

            @Override
            public void onAdd(Entity entity, AnimalRecord record) {
                if (entity instanceof LivingEntity camel) {
                    applyAttributes(camel);
                }
            }

            @Override
            public void onCheck(Check check) {
                if (check.entity() instanceof LivingEntity camel) {
                    MountSupport.applyOldAge(camel, check.age());
                    MountSupport.rejectOverburdened(camel, messages);
                }
            }
        };
    }

    /** TFC's camel attributes as the carrier's base values. */
    static void applyAttributes(LivingEntity camel) {
        base(camel, Attribute.MAX_HEALTH, MountRules.DROMEDARY_MAX_HEALTH);
        base(camel, Attribute.MOVEMENT_SPEED, MountRules.DROMEDARY_MOVEMENT_SPEED);
        base(camel, Attribute.JUMP_STRENGTH, MountRules.DROMEDARY_JUMP_STRENGTH);
        base(camel, Attribute.STEP_HEIGHT, MountRules.DROMEDARY_STEP_HEIGHT);
    }

    private static void base(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance a = e.getAttribute(attribute);
        if (a != null && a.getBaseValue() != value) {
            a.setBaseValue(value);
        }
    }
}
