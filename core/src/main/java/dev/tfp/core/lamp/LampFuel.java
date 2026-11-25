/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/LampFuel.java @ c3b4d9536 (burn rate, the lamps a fuel works in),
 *       src/generated/resources/data/tfc/tfc/lamp_fuel/{canola_oil,lava,olive_oil,tallow}.json @ c3b4d9536
 */
package dev.tfp.core.lamp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One lamp fuel: TFC's {@code lamp_fuel} data entry.
 *
 * @param fluid    the fluid id
 * @param burnRate ticks per mB; {@code -1} burns for ever (lava)
 * @param metals   the metals of the lamps it works in; empty means every lamp (TFC {@code #tfc:lamps})
 */
public record LampFuel(String fluid, int burnRate, Set<String> metals) {
    public LampFuel {
        Objects.requireNonNull(fluid, "fluid");
        metals = Set.copyOf(metals);
        if (burnRate == 0 || burnRate < -1) {
            throw new IllegalArgumentException("burn rate must be positive or -1: " + burnRate);
        }
    }

    /** TFC {@code burnRate() == -1}: the fuel is never used up, and a lamp filled with it lights itself. */
    public boolean endless() {
        return burnRate == -1;
    }

    /** TFC {@code LampFuel.get(fluid, state)} for the lamp of {@code metal}. */
    public boolean works(String metal) {
        return metals.isEmpty() || metals.contains(metal);
    }

    /** The fuels of TFC at the pinned commit, in file-name order. */
    public static final List<LampFuel> TFC = List.of(
        new LampFuel("tfp:canola_oil", 6000, Set.of()),
        new LampFuel("minecraft:lava", -1, Set.of("blue_steel", "red_steel")),
        new LampFuel("tfp:olive_oil", 7200, Set.of()),
        new LampFuel("tfp:tallow", 2400, Set.of()));

    /** The fuel of {@code fluid} that works in the lamp of {@code metal}, TFC {@code LampFuel.get}. */
    public static Optional<LampFuel> get(String fluid, String metal) {
        for (LampFuel f : TFC) {
            if (f.fluid.equals(fluid) && f.works(metal)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
