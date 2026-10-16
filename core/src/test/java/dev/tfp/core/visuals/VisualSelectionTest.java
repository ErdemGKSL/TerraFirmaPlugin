/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.compat.Version;
import dev.tfp.core.config.TfpConfig.NetherEndBlocks;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.config.TfpConfig.Visuals;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M1-INST-03: picking the shipped allocation for the running version and config. */
class VisualSelectionTest {
    static final String INDEX = """
            {"format": 1, "solid_reserve_warning": 500,
             "families": {"1.21": {"default_profile": "level-3a", "solid_pool": 1289},
                          "26": {"default_profile": "level-2", "solid_pool": 1489}},
             "allocations": [
               {"family": "1.21", "profile": "level-2", "nether_end": false, "dir": "visuals/1.21/level-2/nether-end-off",
                "solid_used": 591, "solid_free": 698},
               {"family": "1.21", "profile": "level-3a", "nether_end": false, "dir": "visuals/1.21/level-3a/nether-end-off",
                "solid_used": 339, "solid_free": 950},
               {"family": "1.21", "profile": "level-3a", "nether_end": true, "dir": "visuals/1.21/level-3a/nether-end-on",
                "solid_used": 272, "solid_free": 1017}
             ]}
            """;

    static Path contentRoot() {
        String configured = System.getProperty("tfp.contentData");
        Path data = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        return data.getParent();
    }

    @Test
    void defaultsPerFamily() {
        VisualSelection s121 = VisualSelection.of(Visuals.DEFAULTS, Version.parse("1.21.11"), true, true);
        assertThat(s121).isEqualTo(new VisualSelection("1.21", Profile.LEVEL_3A, false));
        assertThat(s121.dir()).isEqualTo("visuals/1.21/level-3a/nether-end-off");
        VisualSelection s26 = VisualSelection.of(Visuals.DEFAULTS, Version.parse("26.3"), true, true);
        assertThat(s26.dir()).isEqualTo("visuals/26/level-2/nether-end-off");
        assertThat(s26.id()).isEqualTo("26/level-2/nether-end-off");
    }

    @Test
    void configuredProfileWins() {
        Visuals v = new Visuals(Map.of("1.21", Profile.LEVEL_0, "26", Profile.LEVEL_3B), 500, NetherEndBlocks.AUTO);
        assertThat(VisualSelection.of(v, Version.parse("1.21.4"), true, true).profile()).isEqualTo(Profile.LEVEL_0);
        assertThat(VisualSelection.of(v, Version.parse("26.2"), true, true).profile()).isEqualTo(Profile.LEVEL_3B);
    }

    @Test
    void netherEndAutoOnlyWithoutBothDimensions() {
        assertThat(VisualSelection.netherEnd(NetherEndBlocks.AUTO, false, false)).isTrue();
        assertThat(VisualSelection.netherEnd(NetherEndBlocks.AUTO, true, false)).isFalse();
        assertThat(VisualSelection.netherEnd(NetherEndBlocks.AUTO, false, true)).isFalse();
        assertThat(VisualSelection.netherEnd(NetherEndBlocks.TRUE, true, true)).isTrue();
        assertThat(VisualSelection.netherEnd(NetherEndBlocks.FALSE, false, false)).isFalse();
    }

    @Test
    void indexFindsAllocationsAndDemand() {
        VisualIndex idx = VisualIndex.parse(INDEX);
        assertThat(idx.solidPool()).containsEntry("1.21", 1289).containsEntry("26", 1489);
        assertThat(idx.find(new VisualSelection("1.21", Profile.LEVEL_3A, true))).get()
                .extracting(VisualIndex.Allocation::solidUsed).isEqualTo(272);
        assertThat(idx.find(new VisualSelection("26", Profile.LEVEL_3A, true))).isEmpty();
        assertThat(idx.solidDemand("1.21", false)).containsExactly(Map.entry(Profile.LEVEL_2, 591), Map.entry(Profile.LEVEL_3A, 339));
        assertThat(idx.find(new VisualSelection("1.21", Profile.LEVEL_2, false)).orElseThrow().dir())
                .isEqualTo(new VisualSelection("1.21", Profile.LEVEL_2, false).dir());
    }

    @Test
    void malformedIndexIsRejected() {
        assertThatThrownBy(() -> VisualIndex.parse("{\"format\": 2}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisualIndex.parse("[]")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everySelectionHasAShippedAllocation() throws IOException {
        Path root = contentRoot();
        Path index = root.resolve("visuals/index.json");
        assumeTrue(Files.isRegularFile(index), "no generated content at " + index.toAbsolutePath());
        VisualIndex idx = VisualIndex.parse(Files.readString(index));
        for (String family : new String[] {"1.21", "26"}) {
            for (Profile p : Profile.values()) {
                for (boolean ne : new boolean[] {false, true}) {
                    VisualSelection s = new VisualSelection(family, p, ne);
                    VisualIndex.Allocation a = idx.find(s).orElseThrow(() -> new AssertionError("no allocation for " + s.id()));
                    assertThat(a.dir()).isEqualTo(s.dir());
                    for (String f : new String[] {"visual_templates.yml", "visual_mappings.yml", "suppressed_blocks.json"}) {
                        assertThat(root.resolve(s.dir()).resolve(f)).isRegularFile();
                    }
                }
            }
        }
    }
}
