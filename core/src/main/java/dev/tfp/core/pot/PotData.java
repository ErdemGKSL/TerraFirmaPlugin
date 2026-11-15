/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (the saved fields: output,
 *       boilingTicks, preBoilingTicks; the tank of FluidHelpers.BUCKET_VOLUME in PotInventory)
 */
package dev.tfp.core.pot;

import dev.tfp.core.cooking.PotRules;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;

/**
 * Everything a pot keeps besides its nine slots: the firepit underneath (TFC's pot is an {@code AbstractFirepitBlockEntity}:
 * burn, temperature, ash, smoke), the boil counters, the output waiting to be taken and the fluid tank. The calendar tick of
 * the last update is the device record's.
 *
 * @param firepit  the firepit part (TFC's inherited fields and the {@code lit}, {@code smoke_level} state)
 * @param counters TFC {@code preBoilingTicks} and {@code boilingTicks}
 * @param output   TFC {@code output}, {@link PotOutput#NONE} for {@code null}
 * @param fluid    the tank ({@link #CAPACITY} mB)
 */
public record PotData(FirepitData firepit, PotRules.PotState counters, PotOutput output, FluidAmount fluid) {
    /** TFC {@code FluidHelpers.BUCKET_VOLUME}: the pot's tank. */
    public static final int CAPACITY = 1000;

    /** A new pot: a cold, unlit firepit, idle counters, no output, an empty tank. */
    public static final PotData FRESH = new PotData(FirepitData.FRESH, PotRules.PotState.IDLE, PotOutput.NONE,
        FluidAmount.EMPTY);

    public static final VersionedCodec<PotData> CODEC = VersionedCodec.<PotData>builder("pot", 1)
        .build((d, out) -> {
            out.putMap("firepit", FirepitData.CODEC.encode(d.firepit()))
                .putInt("preBoilingTicks", d.counters().preBoilingTicks())
                .putInt("boilingTicks", d.counters().boilingTicks())
                .putString("fluid", d.fluid().fluid())
                .putInt("fluidAmount", d.fluid().amount())
                .putString("output", d.output().type());
            switch (d.output()) {
                case PotOutput.Soup s -> out.putBytes("soup", s.stack().bytes());
                case PotOutput.Jam j -> out.putBytes("unsealed", j.unsealed().bytes())
                    .putBytes("sealed", j.sealed().bytes()).putString("texture", j.texture());
                case PotOutput.None n -> {
                }
            }
        }, in -> new PotData(FirepitData.CODEC.decode(in.requireMap("firepit")),
            new PotRules.PotState(in.getInt("preBoilingTicks", 0), in.getInt("boilingTicks", 0)),
            output(in),
            new FluidAmount(in.getString("fluid", ""), in.getInt("fluidAmount", 0))));

    public PotData {
        Objects.requireNonNull(firepit, "firepit");
        Objects.requireNonNull(counters, "counters");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(fluid, "fluid");
        if (fluid.amount() > CAPACITY) {
            throw new IllegalArgumentException("fluid above the pot's capacity: " + fluid);
        }
        if (output.isEmpty()) {
            output = PotOutput.NONE; // TFC: an empty output is dropped (output = null)
        }
    }

    private static PotOutput output(DataMap in) {
        String type = in.getString("output", "empty");
        return switch (type) {
            case "empty" -> PotOutput.NONE;
            case "soup" -> new PotOutput.Soup(ItemBlob.of(in.getBytes("soup", new byte[0])));
            case "jam" -> new PotOutput.Jam(ItemBlob.of(in.getBytes("unsealed", new byte[0])),
                ItemBlob.of(in.getBytes("sealed", new byte[0])), in.getString("texture", ""));
            default -> throw new PersistException("unknown pot output " + type);
        };
    }

    public PotData withFirepit(FirepitData value) {
        return new PotData(value, counters, output, fluid);
    }

    public PotData withCounters(PotRules.PotState value) {
        return new PotData(firepit, value, output, fluid);
    }

    public PotData withOutput(PotOutput value) {
        return new PotData(firepit, counters, value, fluid);
    }

    public PotData withFluid(FluidAmount value) {
        return new PotData(firepit, counters, output, value);
    }

    /** Whether an output waits to be taken (TFC {@code output != null}). */
    public boolean hasOutput() {
        return !output.isEmpty();
    }

    /** The device temperature in C. */
    public float temperature() {
        return firepit.temperature();
    }

    /** TFC {@code shouldRenderAsBoiling}: {@code boilingTicks > 0}. */
    public boolean rendersAsBoiling() {
        return counters.boilingTicks() > 0;
    }
}
