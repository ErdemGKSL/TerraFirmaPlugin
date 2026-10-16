/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import org.junit.jupiter.api.Test;

/** M1-INT-04: the probe evaluates our placeholders the way BetterHud invokes them, from the snapshot only. */
class PlaceholderProbeTest {
    @Test
    void evaluatesOurPlaceholderFromTheSnapshotByUuid() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        Map<String, HudPlaceholder<?>> registry = new LinkedHashMap<>();
        HudValues.NUMBERS.forEach((name, read) -> registry.put(name, BetterHudIntegration.numberPlaceholder(cache, read)));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cache.put(a, new HudSnapshot(37.5, 50, 80, 12, 300));

        assertThat(PlaceholderProbe.evaluate(registry, "tfp_health", a)).hasValue(37.5);
        assertThat(PlaceholderProbe.evaluate(registry, "tfp_thirst", a)).hasValue(80);
        assertThat(PlaceholderProbe.evaluate(registry, "tfp_health", b)).hasValue(HudSnapshot.DEFAULT.health());
        assertThat(PlaceholderProbe.evaluate(registry, "unknown", a)).isEmpty();
    }

    @Test
    void aPlaceholderReadingMoreThanTheUuidFailsLoudly() {
        Map<String, HudPlaceholder<?>> registry = Map.of("bad",
                HudPlaceholder.<Number>of((args, reason) -> player -> player.location().hashCode()));
        assertThatThrownBy(() -> PlaceholderProbe.evaluate(registry, "bad", UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("location");
        Map<String, HudPlaceholder<?>> text = Map.of("text", HudPlaceholder.<String>of((args, reason) -> player -> "x"));
        assertThatThrownBy(() -> PlaceholderProbe.evaluate(text, "text", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(PlaceholderProbe.player(new UUID(1, 2)).uuid()).isEqualTo(new UUID(1, 2));
    }
}
