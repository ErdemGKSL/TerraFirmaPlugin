/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft)
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (collapse* options)
 */
package dev.tfp.paper.collapse;

import dev.tfp.core.collapse.CollapseRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;

/**
 * TFC's server collapse options (PAR-PHYS-07), read from the {@code collapse} section of {@code config.yml}. Every value is
 * clamped to TFC's own range (chances 0..1, {@code minRadius} 1..32, {@code radiusVariance} 1..32) and a bad value takes
 * the default with a warning. {@code enableExplosionCollapsing} is declared in TFC but never read there, so it has no key.
 *
 * @param triggerChance            {@code collapseTriggerChance}
 * @param fakeTriggerChance        {@code collapseFakeTriggerChance}
 * @param propagateChance          {@code collapsePropagateChance}
 * @param explosionPropagateChance {@code collapseExplosionPropagateChance}
 * @param minRadius                {@code collapseMinRadius}
 * @param radiusVariance           {@code collapseRadiusVariance}
 * @param chiselsStartCollapses    {@code enableChiselsStartCollapses}: read by the chisel item (not ported yet) before it
 *                                 calls {@link CollapseService#trigger}
 */
public record CollapseConfig(double triggerChance, double fakeTriggerChance, double propagateChance,
        double explosionPropagateChance, int minRadius, int radiusVariance, boolean chiselsStartCollapses) {
    public static final String SECTION = "collapse";
    public static final CollapseConfig DEFAULT = new CollapseConfig(CollapseRules.DEFAULT_TRIGGER_CHANCE,
            CollapseRules.DEFAULT_FAKE_TRIGGER_CHANCE, CollapseRules.DEFAULT_PROPAGATE_CHANCE, 0.3,
            CollapseRules.DEFAULT_MIN_RADIUS, CollapseRules.DEFAULT_RADIUS_VARIANCE, true);

    public CollapseConfig {
        for (double chance : new double[] {triggerChance, fakeTriggerChance, propagateChance, explosionPropagateChance}) {
            if (!(chance >= 0 && chance <= 1)) {
                throw new IllegalArgumentException("Chances must be in [0, 1]");
            }
        }
        if (minRadius < 1 || minRadius > 32 || radiusVariance < 1 || radiusVariance > 32) {
            throw new IllegalArgumentException("Radius and variance must be in [1, 32]");
        }
    }

    /** Reads the {@code collapse} section ({@code null} = all defaults); problems go to {@code warn}. */
    public static CollapseConfig parse(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) {
            return DEFAULT;
        }
        return new CollapseConfig(
                chance(section, "trigger-chance", DEFAULT.triggerChance, warn),
                chance(section, "fake-trigger-chance", DEFAULT.fakeTriggerChance, warn),
                chance(section, "propagate-chance", DEFAULT.propagateChance, warn),
                chance(section, "explosion-propagate-chance", DEFAULT.explosionPropagateChance, warn),
                radius(section, "min-radius", DEFAULT.minRadius, warn),
                radius(section, "radius-variance", DEFAULT.radiusVariance, warn),
                section.getBoolean("chisels-start-collapses", DEFAULT.chiselsStartCollapses));
    }

    /** The warnings {@link #parse} would give, for tests and the reload report. */
    public static List<String> problems(ConfigurationSection section) {
        List<String> out = new ArrayList<>();
        parse(section, out::add);
        return out;
    }

    private static double chance(ConfigurationSection s, String key, double def, Consumer<String> warn) {
        if (!s.contains(key)) {
            return def;
        }
        if (!(s.get(key) instanceof Number n) || !(n.doubleValue() >= 0 && n.doubleValue() <= 1)) {
            warn.accept(SECTION + "." + key + ": '" + s.get(key) + "' is not a number in [0, 1]; using " + def);
            return def;
        }
        return n.doubleValue();
    }

    private static int radius(ConfigurationSection s, String key, int def, Consumer<String> warn) {
        if (!s.contains(key)) {
            return def;
        }
        if (!(s.get(key) instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.intValue() < 1
                || n.intValue() > 32) {
            warn.accept(SECTION + "." + key + ": '" + s.get(key) + "' is not a whole number in [1, 32]; using " + def);
            return def;
        }
        return n.intValue();
    }
}
