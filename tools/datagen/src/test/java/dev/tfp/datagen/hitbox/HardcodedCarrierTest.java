/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-13 guard: no generator chooses a vanilla carrier itself. Outside {@code hitbox/} (the selector) and
 * {@code visuals/} (the full-cube allocator and HideTargets) no main source may contain a vanilla block-state literal
 * ({@code "minecraft:<block>[}) or write an {@code auto_state}; {@code device/DeviceAppearance.java} only serialises a
 * carrier it is given.
 */
class HardcodedCarrierTest {
    private static final Pattern STATE_LITERAL = Pattern.compile("\"minecraft:[a-z0-9_]+\\[");
    private static final Pattern AUTO_STATE = Pattern.compile("\"auto_state\"");
    // PAR-WORLD-59: the plant pools are the selector's (hitbox/PlantPools), so the plant generators are no exemption.
    // PAR-WORLD-42 (repair): SoilExtraBlocks only serialises the pool the selector hands the grass path
    // (HitboxAllocation.Assignment#pool, VanillaCandidates.poolName), as DeviceAppearance does for the devices.
    private static final Set<String> AUTO_STATE_SERIALISERS = Set.of("device/DeviceAppearance.java", "soil/SoilExtraBlocks.java");

    @Test
    void noVanillaStateLiteralOutsideTheSelector() throws IOException {
        Path main = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("tools/datagen/src/main/java/dev/tfp/datagen");
        List<String> hits = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(main)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String rel = main.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("hitbox/") || rel.startsWith("visuals/")) {
                    continue;
                }
                scanned++;
                String code = TfcShapeExpressions.stripComments(Files.readString(p));
                Matcher m = STATE_LITERAL.matcher(code);
                while (m.find()) {
                    hits.add(rel + ": " + m.group());
                }
                if (!AUTO_STATE_SERIALISERS.contains(rel)) {
                    Matcher a = AUTO_STATE.matcher(code);
                    while (a.find()) {
                        hits.add(rel + ": auto_state chosen outside the selector");
                    }
                }
            }
        }
        assertThat(scanned).isGreaterThan(20);
        assertThat(hits).as("take carriers from dev.tfp.datagen.hitbox.HitboxAllocation").isEmpty();
    }

    @Test
    void theGuardSeesALiteral() {
        assertThat(STATE_LITERAL.matcher("put(\"state\", \"minecraft:campfire[lit=true]\")").find()).isTrue();
        assertThat(STATE_LITERAL.matcher("\"minecraft:[a-z0-9_]+(\\\\[\"").find()).isFalse();
    }
}
