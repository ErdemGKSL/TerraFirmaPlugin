/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.particle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** PAR-DATA-09: the facade is well formed and docs/particles.md lists every entry. */
class TfcParticleTest {
    @Test
    void everyEntryHasAPositiveCountAndANamedVanillaParticle() {
        for (TfcParticle p : TfcParticle.values()) {
            assertThat(p.vanilla()).as(p.name()).isNotNull();
            assertThat(p.count()).as(p.name()).isPositive();
            assertThat(p.spread()).as(p.name()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void noAmbientParticleIsOffered() {
        for (String ambient : new String[] {"BUTTERFLY", "MOTH", "WIND", "LEAF", "SNOWFLAKE", "FEATHER", "SLEEP", "SMOKE_"}) {
            assertThat(TfcParticle.values()).extracting(Enum::name).noneMatch(n -> n.startsWith(ambient));
        }
    }

    @Test
    void theDocumentListsEveryEntry() throws Exception {
        Path doc = Path.of(System.getProperty("tfp.repoRoot", "..")).resolve("docs/particles.md");
        if (!Files.isRegularFile(doc)) {
            doc = Path.of("../docs/particles.md");
        }
        String text = Files.readString(doc);
        for (TfcParticle p : TfcParticle.values()) {
            assertThat(text).as(p.name()).contains("`" + p.name() + "`");
        }
    }
}
