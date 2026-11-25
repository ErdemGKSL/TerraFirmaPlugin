/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java @ c3b4d9536 (createGenes keys)
 */
package dev.tfp.paper.mount;

import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.mount.HorseColour;
import dev.tfp.core.mount.MountGenes;
import dev.tfp.core.mount.MountStats;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The mount data on a horse-family carrier, next to the animal record: {@code tfp:mount_stats} (the base health, jump and
 * speed the animal was rolled with, so a later reload does not redraw them) and {@code tfp:mount_genes} (TFC's
 * {@code genes} tag a fertilized female carries until she gives birth: both parents' species, stats and colour).
 * Both are single strings, readable with {@code /data get entity}.
 */
public final class MountPdc {
    public static final NamespacedKey STATS = new NamespacedKey("tfp", "mount_stats");
    public static final NamespacedKey GENES = new NamespacedKey("tfp", "mount_genes");

    private MountPdc() {
    }

    public static boolean hasStats(PersistentDataContainer pdc) {
        return pdc.has(STATS, PersistentDataType.STRING);
    }

    public static Optional<MountStats> stats(PersistentDataContainer pdc) {
        String s = pdc.get(STATS, PersistentDataType.STRING);
        if (s == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseStats(s));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static void writeStats(PersistentDataContainer pdc, MountStats stats) {
        pdc.set(STATS, PersistentDataType.STRING, formatStats(stats));
    }

    static String formatStats(MountStats s) {
        return s.maxHealth() + "," + s.jumpStrength() + "," + s.movementSpeed();
    }

    static MountStats parseStats(String s) {
        String[] p = s.split(",");
        return new MountStats(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
    }

    public static Optional<MountGenes> genes(PersistentDataContainer pdc) {
        String s = pdc.get(GENES, PersistentDataType.STRING);
        if (s == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseGenes(s));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static void writeGenes(PersistentDataContainer pdc, MountGenes genes) {
        pdc.set(GENES, PersistentDataType.STRING, formatGenes(genes));
    }

    public static void clearGenes(PersistentDataContainer pdc) {
        pdc.remove(GENES);
    }

    static String formatGenes(MountGenes g) {
        return formatParent(g.mother()) + "|" + formatParent(g.father());
    }

    static MountGenes parseGenes(String s) {
        String[] p = s.split("\\|");
        return new MountGenes(parseParent(p[0]), parseParent(p[1]));
    }

    private static String formatParent(MountGenes.Parent p) {
        String colour = p.colour().map(c -> c.variant() + ":" + c.markings()).orElse("-");
        return p.species().name().toLowerCase(Locale.ROOT) + ";" + formatStats(p.stats()) + ";" + colour;
    }

    private static MountGenes.Parent parseParent(String s) {
        String[] p = s.split(";");
        Optional<HorseColour> colour = Optional.empty();
        if (!p[2].equals("-")) {
            String[] c = p[2].split(":");
            colour = Optional.of(new HorseColour(Integer.parseInt(c[0]), Integer.parseInt(c[1])));
        }
        return new MountGenes.Parent(LivestockSpecies.valueOf(p[0].toUpperCase(Locale.ROOT)), parseStats(p[1]), colour);
    }
}
