/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.piston;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.piston.PistonFluidRule.State;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * PAR-DATA-16: TFC's {@code emptyFluidFrom} applied to piston arrivals. The rule is the same for extend and retract and
 * for a 1-tick and a normal pulse (TFC redirects both {@code finalTick} and {@code tick}), so the table covers the
 * states; the path only decides when the Paper side asks.
 */
class PistonFluidRuleTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            // waterlogged vanilla blocks arrive dry, other properties kept
            "minecraft:oak_slab[type=bottom,waterlogged=true]|minecraft:oak_slab[type=bottom,waterlogged=false]",
            "minecraft:stone_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]"
                    + "|minecraft:stone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:oak_fence[east=false,north=true,south=false,waterlogged=true,west=false]"
                    + "|minecraft:oak_fence[east=false,north=true,south=false,waterlogged=false,west=false]",
            // TFC fluid-logged blocks (fluid property) arrive empty, whatever fluid they held
            "tfp:wood/planks/oak_fence[fluid=water,north=true]|tfp:wood/planks/oak_fence[fluid=empty,north=true]",
            "tfp:plant/cattail[fluid=salt_water,part=lower]|tfp:plant/cattail[fluid=empty,part=lower]",
            // a block with both properties loses both
            "tfp:test/both[fluid=spring_water,waterlogged=true]|tfp:test/both[fluid=empty,waterlogged=false]",
            // meltable ice and liquid blocks arrive as air
            "minecraft:ice|minecraft:air",
            "tfp:sea_ice|minecraft:air",
            "tfp:ice_pile|minecraft:air",
            "minecraft:water[level=0]|minecraft:air",
            "tfp:fluid/salt_water[level=0]|minecraft:air",
    })
    void changedArrivals(String moved, String arrives) {
        State in = parse(moved);
        assertThat(PistonFluidRule.changesOnArrival(in)).isTrue();
        assertThat(PistonFluidRule.arrive(in)).contains(parse(arrives));
        // idempotent: the dry state lands unchanged
        assertThat(PistonFluidRule.arrive(parse(arrives))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "minecraft:oak_slab[type=bottom,waterlogged=false]",
            "tfp:wood/planks/oak_fence[fluid=empty]",
            "minecraft:stone",
            "minecraft:packed_ice",
            "minecraft:blue_ice",
            "minecraft:air",
    })
    void unchangedArrivals(String moved) {
        assertThat(PistonFluidRule.changesOnArrival(parse(moved))).isFalse();
        assertThat(PistonFluidRule.arrive(parse(moved))).isEmpty();
    }

    @Test
    void decideOnlyDriesTheMovedBlock() {
        State wet = parse("minecraft:oak_slab[type=bottom,waterlogged=true]");
        // 1-tick pulse: finalTick placed the slab with its water; it is dried
        assertThat(PistonFluidRule.decide("minecraft:oak_slab", wet))
                .contains(parse("minecraft:oak_slab[type=bottom,waterlogged=false]"));
        // normal pulse: vanilla already cleared waterlogged; nothing to do
        assertThat(PistonFluidRule.decide("minecraft:oak_slab", parse("minecraft:oak_slab[type=bottom,waterlogged=false]")))
                .isEmpty();
        // another block took the place (or the move was undone): left alone
        assertThat(PistonFluidRule.decide("minecraft:oak_stairs", wet)).isEmpty();
        assertThat(PistonFluidRule.decide("minecraft:oak_slab", null)).isEmpty();
        assertThat(PistonFluidRule.decide("minecraft:oak_slab", State.of(PistonFluidRule.MOVING_PISTON))).isEmpty();
    }

    @Test
    void stateEqualityIgnoresPropertyOrder() {
        assertThat(State.of("a:b", Map.of("x", "1", "y", "2"))).isEqualTo(State.of("a:b", Map.of("y", "2", "x", "1")));
        assertThat(PistonFluidRule.arrive(State.of("a:b", Map.of("waterlogged", "false")))).isEqualTo(Optional.empty());
    }

    static State parse(String text) {
        int open = text.indexOf('[');
        if (open < 0) {
            return State.of(text);
        }
        java.util.TreeMap<String, String> props = new java.util.TreeMap<>();
        for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
            String[] kv = pair.split("=");
            props.put(kv[0], kv[1]);
        }
        return new State(text.substring(0, open), props);
    }
}
