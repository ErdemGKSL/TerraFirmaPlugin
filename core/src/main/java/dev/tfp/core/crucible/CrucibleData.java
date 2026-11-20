/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (the saved state:
 *       temperature, targetTemperature, targetTemperatureStabilityTicks, the alloy)
 */
package dev.tfp.core.crucible;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PAR-METAL-12: the state of one crucible besides its nine input slots: the temperature (with the target and its stability
 * window) and the fluid alloy. Immutable, any thread.
 *
 * @param temperature TFC {@code temperature}, {@code targetTemperature} and {@code targetTemperatureStabilityTicks}
 * @param alloy       TFC {@code CrucibleInventory.alloy}
 */
public record CrucibleData(CrucibleTemperature temperature, FluidAlloy alloy) {
    /** A new crucible: cold and empty. */
    public static final CrucibleData FRESH = new CrucibleData(CrucibleTemperature.COLD, FluidAlloy.empty());

    public static final VersionedCodec<CrucibleData> CODEC = VersionedCodec.<CrucibleData>builder("crucible", 1)
        .build((d, out) -> {
            List<DataMap> entries = new ArrayList<>();
            for (Map.Entry<String, Double> e : d.alloy().contents().entrySet()) {
                entries.add(new DataMap().putString("fluid", e.getKey()).putDouble("amount", e.getValue()));
            }
            out.putFloat("temperature", d.temperature().temperature())
                .putFloat("target", d.temperature().target())
                .putInt("stability", d.temperature().stabilityTicks())
                .putInt("alloyAmount", d.alloy().amount())
                .putList("alloy", entries);
        }, in -> {
            Map<String, Double> content = new LinkedHashMap<>();
            for (DataMap e : in.getList("alloy")) {
                content.merge(e.getString("fluid", ""), e.getDouble("amount", 0d), Double::sum);
            }
            return new CrucibleData(new CrucibleTemperature(in.getFloat("temperature", 0f), in.getFloat("target", 0f),
                Math.max(0, in.getInt("stability", 0))), FluidAlloy.of(in.getInt("alloyAmount", 0), content));
        });

    public CrucibleData {
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(alloy, "alloy");
    }

    public CrucibleData withTemperature(CrucibleTemperature value) {
        return new CrucibleData(value, alloy);
    }

    public CrucibleData withAlloy(FluidAlloy value) {
        return new CrucibleData(temperature, value);
    }

    /** The crucible's temperature now. */
    public float degrees() {
        return temperature.temperature();
    }
}
