/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.packet.PacketHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-63: the module side of the plant stage looks (no PacketEvents types, the viewer's view, the service). */
class PlantStageModuleTest {
    @Test
    void theReflectiveHandlerNameResolvesInThePacketLayer() throws Exception {
        Class<?> handler = Class.forName(PlantStageModule.PACKET_HANDLER);
        assertThat(handler.getPackageName()).isEqualTo("dev.tfp.paper.packet.plantstage");
        assertThat(PacketHandler.class).isAssignableFrom(handler);
        handler.getConstructor(PlantStageSource.class, Logger.class);
    }

    @Test
    void featureSideClassesNameNoPacketEventsTypes() {
        for (Class<?> type : new Class<?>[] {PlantStageModule.class, PlantStageService.class, PlantStageSource.class,
            PlantStageSource.Viewer.class, ServerStateIds.class}) {
            for (Method m : type.getDeclaredMethods()) {
                assertThat(m.toGenericString()).as(type.getSimpleName()).doesNotContain("retrooper");
            }
            for (Field f : type.getDeclaredFields()) {
                assertThat(f.getType().getName()).as(type.getSimpleName()).doesNotContain("retrooper");
            }
        }
    }

    @Test
    void viewReadsTheCalendarAndTheHemisphereAtTheColumn() {
        PlantStageSource.Viewer viewer = new PlantStageSource.Viewer(UUID.randomUUID(), -64, 20_000f, null);
        assertThat(viewer.view(ClockSnapshot.UNSET, 0)).isNull();
        long tick = CalendarTicks.ticksInYear(8) / 4 + 6000;
        PlantStageRemap.View north = viewer.view(new ClockSnapshot(1, 0, tick, 8, true), -100);
        PlantStageRemap.View south = viewer.view(new ClockSnapshot(1, 0, tick, 8, true), 100_000);
        assertThat(north.fractionOfYear()).isEqualTo((float) CalendarTicks.fractionOfYear(tick, 8));
        assertThat(north.dayTime()).isEqualTo(6000);
        assertThat(north.northernHemisphere()).isNotEqualTo(south.northernHemisphere());
    }

    @Test
    void serviceTracksViewersAndPublishesTheRemap() {
        GlobalClockHolder clock = new GlobalClockHolder();
        PlantStageService service = new PlantStageService(clock, Logger.getLogger("test"));
        UUID player = UUID.randomUUID();
        assertThat(service.remap().size()).isZero();
        assertThat(service.viewer(player)).isNull();
        PlantStageSource.Viewer viewer = new PlantStageSource.Viewer(UUID.randomUUID(), 0, 20_000f, null);
        service.track(player, viewer);
        assertThat(service.viewer(player)).isSameAs(viewer);
        service.track(player, null);
        assertThat(service.viewer(player)).isNull();
        assertThat(service.clock()).isSameAs(clock.get());
    }
}
