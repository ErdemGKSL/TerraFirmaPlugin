/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.cooking.FirepitState;
import dev.tfp.core.cooking.PotRules;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-09: the pot record's codec round trip and its invariants. */
class PotDataTest {
    static final FirepitData LIT = new FirepitData(new FirepitState(1200, 798f, 640f, 30), true, 2, 0.4f, 3, 2250, true);

    @Test
    void everyOutputKindRoundTrips() {
        for (PotOutput output : List.of(PotOutput.NONE, new PotOutput.Soup(ItemBlob.of(new byte[] {1, 2, 3})),
                new PotOutput.Jam(ItemBlob.of(new byte[] {4}), ItemBlob.of(new byte[] {5, 6}), "tfc:block/jar/banana"))) {
            PotData d = new PotData(LIT, new PotRules.PotState(100, 77), output, new FluidAmount("minecraft:water", 600));
            assertThat(PotData.CODEC.decode(PotData.CODEC.encode(d))).isEqualTo(d);
            assertThat(PotData.CODEC.fromBytes(PotData.CODEC.toBytes(d))).isEqualTo(d);
        }
        assertThat(PotData.CODEC.decode(PotData.CODEC.encode(PotData.FRESH))).isEqualTo(PotData.FRESH);
    }

    @Test
    void anEmptyOutputIsNoOutput() {
        PotData d = PotData.FRESH.withOutput(new PotOutput.Soup(ItemBlob.EMPTY));
        assertThat(d.output()).isEqualTo(PotOutput.NONE);
        assertThat(d.hasOutput()).isFalse();
        PotData jam = PotData.FRESH.withOutput(new PotOutput.Jam(ItemBlob.of(new byte[] {1}), ItemBlob.EMPTY, "t"));
        assertThat(jam.hasOutput()).isFalse();
        assertThat(new PotOutput.Soup(ItemBlob.of(new byte[] {1})).fluidColor()).isEqualTo(0xFFA64214);
    }

    @Test
    void theTankHoldsOneBucket() {
        assertThat(PotData.CAPACITY).isEqualTo(1000);
        assertThatThrownBy(() -> PotData.FRESH.withFluid(new FluidAmount("minecraft:water", 1001)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
