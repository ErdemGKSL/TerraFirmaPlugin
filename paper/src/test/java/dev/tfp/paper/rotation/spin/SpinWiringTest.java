/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.rotation.CreativeMotor;
import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.rotation.RotationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** PAR-MECH-12: the network actor's shaft reports and the axle's render item. */
class SpinWiringTest {
    static final UUID WORLD = new UUID(4, 4);

    record Report(List<ShaftReport> changed, List<BlockPos> removed) {
    }

    @Test
    void theActorReportsAxlesOnlyWithTheirSpeedAndHandAndWhenTheyGo() {
        List<Report> reports = new ArrayList<>();
        RotationService service = new RotationService(Runnable::run, (w, p) -> { }, Logger.getAnonymousLogger());
        service.shaftHook((w, changed, removed) -> reports.add(new Report(changed, removed)));
        BlockPos motor = new BlockPos(0, 64, 0);
        BlockPos axle = new BlockPos(1, 64, 0);
        BlockPos second = new BlockPos(2, 64, 0);
        service.put(WORLD, axle, MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        assertThat(reports).singleElement().satisfies(r -> assertThat(r.changed()).singleElement().satisfies(s -> {
            assertThat(s.network()).isEqualTo(ShaftReport.NO_NETWORK);
            assertThat(s.speed()).isZero();
        }));
        service.put(WORLD, motor, MechanicalBlock.State.motor(RotationAxis.X, 8));
        service.put(WORLD, second, MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X));
        ShaftReport a = last(reports, axle);
        ShaftReport b = last(reports, second);
        assertThat(a.speed()).isCloseTo(CreativeMotor.speed(8), within(1e-6f));
        assertThat(a.network()).isNotEqualTo(ShaftReport.NO_NETWORK).isEqualTo(b.network());
        assertThat(a.direction()).isEqualTo(b.direction());
        int before = reports.size();
        service.put(WORLD, axle, MechanicalBlock.State.axle(MechanicalBlock.AXLE, RotationAxis.X)); // unchanged: no report
        assertThat(reports).hasSize(before);
        service.put(WORLD, motor, MechanicalBlock.State.motor(RotationAxis.X, 0)); // stop
        assertThat(last(reports, second).speed()).isZero();
        service.remove(WORLD, second);
        assertThat(reports.getLast().removed()).containsExactly(second);
        // the motor is no shaft
        assertThat(reports.stream().flatMap(r -> r.changed().stream()).map(ShaftReport::pos)).doesNotContain(motor);
    }

    private static ShaftReport last(List<Report> reports, BlockPos pos) {
        ShaftReport found = null;
        for (Report r : reports) {
            for (ShaftReport s : r.changed()) {
                if (s.pos().equals(pos)) {
                    found = s;
                }
            }
        }
        assertThat(found).as("report of %s", pos).isNotNull();
        return found;
    }

    @Test
    void theRenderItemOfAnAxleIsItsWoodsShaftModel() {
        assertThat(SpinVisuals.renderItem("tfp:wood/axle/oak")).hasValue("tfp:render/rotation/wood/axle/oak");
        assertThat(SpinVisuals.renderItem("tfp:wood/axle/white_cedar")).hasValue("tfp:render/rotation/wood/axle/white_cedar");
        assertThat(SpinVisuals.renderItem("tfp:wood/encased_axle/oak")).isEmpty();
        assertThat(SpinVisuals.renderItem("tfp:wood/axle/")).isEmpty();
        assertThat(SpinVisuals.renderItem(null)).isEmpty();
    }
}
