/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Fuel.java @ c3b4d9536 (duration, temperature, purity)
 */
package dev.tfp.core.fuel;

/**
 * One fuel ({@code Fuel}). A fuel burns for {@link #duration} ticks at {@link #temperature} (the target the fire pulls
 * the device towards). {@link #purity} is how clean it burns: each fuel adds {@code 1 - purity} of dirtiness to a fire
 * (TFC's {@code AbstractFirepitBlockEntity.consumeFuel}, applied by the firepit port).
 *
 * @param id          the TFC data file path under {@code fuel/}, for instance {@code oak_logs}
 * @param duration    ticks one item burns for, {@code >= 0}
 * @param temperature the burn temperature in C, finite and {@code >= 0}
 * @param purity      in {@code [0, 1]}, 1 is a clean burn (TFC default)
 */
public record Fuel(String id, int duration, float temperature, float purity) {
    public Fuel {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be >= 0: " + duration);
        }
        if (!(temperature >= 0) || Float.isInfinite(temperature)) {
            throw new IllegalArgumentException("temperature must be >= 0 and finite: " + temperature);
        }
        if (!(purity >= 0 && purity <= 1)) {
            throw new IllegalArgumentException("purity must be in [0, 1]: " + purity);
        }
    }
}
