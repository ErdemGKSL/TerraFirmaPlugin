/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powerloom;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-MECH-22: the power loom breaks without a solid block below (TFC BottomSupportedDeviceBlock). */
class PowerLoomSupportTest {
    private static final BlockStateRef LOOM = BlockStateRef.of(PowerLoomDevice.ID).with("facing", "east");
    private static final PowerLoomSupport RULE = new PowerLoomSupport(id -> id.equals("minecraft:stone"));

    private static Neighbourhood<BlockStateRef> around(BlockStateRef below) {
        return Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? LOOM
            : dx == 0 && dy == -1 && dz == 0 ? below : BlockStateRef.of("minecraft:air"));
    }

    @Test
    void staysOnASolidBlock() {
        assertThat(RULE.decide(around(BlockStateRef.of("minecraft:stone")))).isNull();
    }

    @Test
    void becomesAirWithoutSupport() {
        assertThat(RULE.decide(around(BlockStateRef.of("minecraft:air")))).isEqualTo(PowerLoomSupport.AIR);
        assertThat(RULE.decide(around(BlockStateRef.of("minecraft:water")))).isEqualTo(PowerLoomSupport.AIR);
    }

    @Test
    void unknownFloorKeepsTheLoomAndOtherBlocksAreIgnored() {
        assertThat(RULE.decide(Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? LOOM
            : dy == -1 && dx == 0 && dz == 0 ? null : BlockStateRef.of("minecraft:air")))).isNull();
        Neighbourhood<BlockStateRef> stone = Neighbourhood.of((dx, dy, dz) -> BlockStateRef.of("minecraft:stone"));
        assertThat(RULE.appliesTo(stone.center())).isFalse();
        assertThat(RULE.decide(Neighbourhood.of((dx, dy, dz) -> BlockStateRef.of("minecraft:air")))).isNull();
    }

    @Test
    void ruleIdentity() {
        assertThat(RULE.blockIds()).isEqualTo(Set.of("tfp:power_loom"));
        assertThat(RULE.id()).isEqualTo("tfp:power_loom/support");
        assertThat(Direction.DOWN.dy()).isEqualTo(-1);
    }
}
