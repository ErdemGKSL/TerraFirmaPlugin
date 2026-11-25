/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sapling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.worldgen.tree.Wood;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SaplingRulesTest {
    @Test
    void daysMatchTfcWoodConstants() throws IOException {
        final Path file = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"),
            "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java");
        assumeTrue(Files.isRegularFile(file), "no TFC checkout");
        final Pattern row = Pattern.compile("^\\s*([A-Z_]+)\\((?:true|false), [^,]+, [^,]+, [^,]+, (\\d+), \\d+\\)[,;]", Pattern.MULTILINE);
        final Matcher m = row.matcher(Files.readString(file));
        int rows = 0;
        while (m.find()) {
            final Wood wood = Wood.valueOf(m.group(1));
            assertThat(SaplingRules.daysToGrow(wood)).as(m.group(1)).isEqualTo(Integer.parseInt(m.group(2)));
            rows++;
        }
        assertThat(rows).isEqualTo(Wood.values().length);
    }

    @Test
    void ticksAreCalendarTicksTimesModifier() {
        assertThat(SaplingRules.defaultTicksToGrow(Wood.OAK)).isEqualTo(240_000);
        assertThat(SaplingRules.ticksToGrow(Wood.SEQUOIA, 1.0)).isEqualTo(432_000L);
        assertThat(SaplingRules.ticksToGrow(Wood.ASH, 0.5)).isEqualTo(84_000L);
        assertThat(SaplingRules.ticksToGrow(Wood.ASH, 1.5)).isEqualTo(252_000L);
        assertThat(SaplingRules.ticksToGrow(Wood.ASH, 0)).isZero();
    }

    @Test
    void growsOnlyStrictlyAfterTheRequiredTicks() {
        assertThat(SaplingRules.elapsed(240_000, 240_000)).isFalse();
        assertThat(SaplingRules.elapsed(240_001, 240_000)).isTrue();
        assertThat(SaplingRules.elapsed(0, 0)).isFalse();
    }

    @Test
    void needsLightNine() {
        assertThat(SaplingRules.lightOk(8)).isFalse();
        assertThat(SaplingRules.lightOk(9)).isTrue();
    }

    @Test
    void blockIdsRoundTrip() {
        for (Wood wood : Wood.values()) {
            assertThat(SaplingRules.woodOf(SaplingRules.blockId(wood))).isEqualTo(wood);
        }
        assertThat(SaplingRules.woodOf("tfp:wood/log/oak")).isNull();
        assertThat(SaplingRules.woodOf("tfp:wood/sapling/nope")).isNull();
    }

    @Test
    void stateCodecRoundTripsAndCountsTicks() {
        final SaplingState s = new SaplingState(12_345L);
        final DataMap out = SaplingState.CODEC.encode(s);
        assertThat(SaplingState.CODEC.decode(out)).isEqualTo(s);
        assertThat(s.ticksSince(20_000L)).isEqualTo(7_655L);
    }
}
