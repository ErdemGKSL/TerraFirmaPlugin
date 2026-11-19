/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (onWorldLoad forced game rules, onCreateNetherPortal):
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536
 */
package dev.tfp.paper.worldgen;

import java.util.List;
import org.bukkit.GameRule;
import org.bukkit.World;

/**
 * TFC's forced game rules on a world (PAR-WORLD-16). TFC sets, on every level load when {@code enableForcedTFCGameRules}
 * (default true): naturalRegeneration false (health comes from our own regeneration), doInsomnia false (no phantoms),
 * doPatrolSpawning false, doTraderSpawning false. The daylight rule is the calendar's ({@code DaylightSync}), not set here.
 * Rules the running server does not have (they were renamed in 1.21.11) are skipped.
 */
public final class TfcWorldRules {
    /** JVM property that turns the forced rules off (TFC's {@code enableForcedTFCGameRules}). Default on. */
    public static final String FORCED_PROPERTY = "tfp.world.forced-gamerules";
    /** JVM property that allows nether portals (TFC's {@code enableNetherPortals}). Default off. */
    public static final String PORTALS_PROPERTY = "tfp.world.nether-portals";

    /** Rule key (1.21.11+), legacy name. All are forced to false. */
    public static final List<String[]> FORCED_FALSE = List.of(
        new String[] {"natural_health_regeneration", "naturalRegeneration"},
        new String[] {"spawn_phantoms", "doInsomnia"},
        new String[] {"spawn_patrols", "doPatrolSpawning"},
        new String[] {"spawn_wandering_traders", "doTraderSpawning"});

    private TfcWorldRules() {
    }

    public static boolean forcedEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(FORCED_PROPERTY, "true"));
    }

    public static boolean netherPortalsEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(PORTALS_PROPERTY, "false"));
    }

    /** Sets the forced rules on {@code world} (global thread on Folia). Returns how many rules were changed. */
    public static int apply(World world) {
        if (!forcedEnabled()) {
            return 0;
        }
        int changed = 0;
        for (String[] names : FORCED_FALSE) {
            GameRule<Boolean> rule = find(names[0], names[1]);
            if (rule != null && !Boolean.FALSE.equals(world.getGameRuleValue(rule))) {
                world.setGameRule(rule, false);
                changed++;
            }
        }
        return changed;
    }

    /** A boolean game rule by its 1.21.11+ key or its older name; null when the server has neither. */
    @SuppressWarnings({"unchecked", "deprecation"})
    public static GameRule<Boolean> find(String key, String legacyName) {
        for (GameRule<?> r : GameRule.values()) {
            if (r.getType() != Boolean.class) {
                continue;
            }
            if (r.getKey().getKey().equals(key) || r.getName().equals(key) || r.getName().equals(legacyName)
                    || r.getKey().getKey().equalsIgnoreCase(legacyName)) {
                return (GameRule<Boolean>) r;
            }
        }
        return null;
    }
}
