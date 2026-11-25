/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCArmadillo.java @ c3b4d9536 (customServerAiStep scutes)
 */
package dev.tfp.paper.wildraid;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.wildraid.ArmadilloScutes;
import dev.tfp.paper.animal.SpeciesBehaviour;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * The armadillo's scutes on the calendar ({@link ArmadilloScutes}): the vanilla scute timer is suppressed
 * ({@link WildRaidModule}), and on each staggered check an adult whose cooldown has run out drops one scute at its feet with the
 * vanilla sound and starts the next cooldown. The animal's produced tick is drawn the first time it is seen (TFC's spawn draw).
 * Nothing counts: a check reads one stored calendar tick, so a long-unloaded armadillo drops one scute, as TFC's does.
 */
final class ArmadilloScuteBehaviour implements SpeciesBehaviour {
    static final String SPECIES = "tfc:armadillo";

    private final Supplier<ClockSnapshot> clock;
    private final RandomGenerator random;

    ArmadilloScuteBehaviour(Supplier<ClockSnapshot> clock, RandomGenerator random) {
        this.clock = clock;
        this.random = random;
    }

    @Override
    public Set<String> species() {
        return Set.of(SPECIES);
    }

    @Override
    public boolean keepVanillaGoals() {
        return true;
    }

    @Override
    public void onAdd(Entity entity, AnimalRecord record) {
        ClockSnapshot now = clock.get();
        if (now.isSet() && RaidPdc.armadilloProduced(entity.getPersistentDataContainer()).isEmpty()) {
            RaidPdc.armadilloProduced(entity.getPersistentDataContainer(), ArmadilloScutes.spawnProducedTick(
                    now.calendarTicks(), random.nextInt(ArmadilloScutes.SPAWN_DRAW_BOUND)));
        }
    }

    @Override
    public void onCheck(Check check) {
        ClockSnapshot now = clock.get();
        Entity armadillo = check.entity();
        if (!now.isSet() || !armadillo.isValid()) {
            return;
        }
        var pdc = armadillo.getPersistentDataContainer();
        long produced = RaidPdc.armadilloProduced(pdc).orElse(ArmadilloScutes.UNSET);
        if (!ArmadilloScutes.due(check.age() != LivestockAge.CHILD, produced, now.calendarTicks())) {
            return;
        }
        armadillo.getWorld().playSound(armadillo.getLocation(), Sound.ENTITY_ARMADILLO_SCUTE_DROP, 1.0f,
                (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
        armadillo.getWorld().dropItem(armadillo.getLocation(), new ItemStack(Material.ARMADILLO_SCUTE));
        RaidPdc.armadilloProduced(pdc, now.calendarTicks());
    }
}
