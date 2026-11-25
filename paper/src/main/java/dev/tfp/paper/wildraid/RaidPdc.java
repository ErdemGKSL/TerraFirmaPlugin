/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.wildraid;

import dev.tfp.core.wildraid.RabbitRaid;
import java.util.OptionalLong;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The two ticks the raid module keeps on an animal (the lazy state; nothing counts down): the game tick of a rabbit's last raid
 * and the calendar tick of an armadillo's last scute. Both are plain longs on the animal's PDC; an animal without them is
 * hungry (rabbit) or new (armadillo).
 */
final class RaidPdc {
    /** TFC's {@code TFCMoreCarrotTicks} as the tick of the raid. */
    static final NamespacedKey RABBIT_RAID = new NamespacedKey("tfp", "rabbit_raid");
    /** TFC's {@code produced} tag (the calendar tick of the last scute). */
    static final NamespacedKey ARMADILLO_PRODUCED = new NamespacedKey("tfp", "armadillo_produced");

    private RaidPdc() {
    }

    static long rabbitRaid(Entity rabbit) {
        Long v = rabbit.getPersistentDataContainer().get(RABBIT_RAID, PersistentDataType.LONG);
        return v == null ? RabbitRaid.NEVER : v;
    }

    static void rabbitRaid(Entity rabbit, long gameTick) {
        rabbit.getPersistentDataContainer().set(RABBIT_RAID, PersistentDataType.LONG, gameTick);
    }

    static OptionalLong armadilloProduced(PersistentDataContainer pdc) {
        Long v = pdc.get(ARMADILLO_PRODUCED, PersistentDataType.LONG);
        return v == null ? OptionalLong.empty() : OptionalLong.of(v);
    }

    static void armadilloProduced(PersistentDataContainer pdc, long calendarTick) {
        pdc.set(ARMADILLO_PRODUCED, PersistentDataType.LONG, calendarTick);
    }
}
