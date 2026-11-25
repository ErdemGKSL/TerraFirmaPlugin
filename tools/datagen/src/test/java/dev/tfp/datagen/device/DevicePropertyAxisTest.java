/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-MECH-24: the x, y, z axis property is CraftEngine's axis type; every other names property stays a string. */
class DevicePropertyAxisTest {
    @Test
    void theFullAxisSetIsCraftEnginesAxisType() {
        Map<String, Object> m = DeviceProperty.names("axis", List.of("x", "y", "z"), "y").yaml();
        assertThat(m).containsEntry("type", "axis").containsEntry("default", "y").doesNotContainKey("values");
    }

    @Test
    void horizontalAxisAndOtherNamesStayStrings() {
        Map<String, Object> two = DeviceProperty.names("axis", List.of("x", "z"), "x").yaml();
        assertThat(two).containsEntry("type", "string").containsEntry("values", List.of("x", "z"));
        Map<String, Object> facing = DeviceProperty.names("facing", List.of("x", "y", "z"), "x").yaml();
        assertThat(facing).containsEntry("type", "string");
    }
}
