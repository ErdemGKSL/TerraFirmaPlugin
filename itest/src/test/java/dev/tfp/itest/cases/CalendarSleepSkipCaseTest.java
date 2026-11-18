/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.bukkit.World;
import org.bukkit.event.world.TimeSkipEvent;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-24: the reflective NIGHT_SKIP builder of {@code calendar.sleep_skip}, on the 1.21.11 compile API. */
class CalendarSleepSkipCaseTest {
    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "test-world";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void buildsANightSkipEventForTheWorldAndAmount() {
        World world = world();
        TimeSkipEvent event = CalendarSleepSkipCase.nightSkip(world, 3_000L);
        assertThat(event.getWorld()).isSameAs(world);
        assertThat(event.getSkipAmount()).isEqualTo(3_000L);
        assertThat(((Enum<?>) (Object) event.getSkipReason()).name()).isEqualTo("NIGHT_SKIP");
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void eachCallBuildsAFreshEvent() {
        World world = world();
        TimeSkipEvent a = CalendarSleepSkipCase.nightSkip(world, 1L);
        TimeSkipEvent b = CalendarSleepSkipCase.nightSkip(world, 23_999L);
        assertThat(a).isNotSameAs(b);
        assertThat(a.getSkipAmount()).isEqualTo(1L);
        assertThat(b.getSkipAmount()).isEqualTo(23_999L);
    }

    @Test
    void theCaseNamesNoReasonEnumInItsBytecode() throws Exception {
        try (InputStream in = CalendarSleepSkipCase.class.getResourceAsStream("CalendarSleepSkipCase.class")) {
            String bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertThat(bytes).doesNotContain("TimeSkipEvent$SkipReason");
        }
    }
}
