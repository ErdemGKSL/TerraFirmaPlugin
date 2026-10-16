/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.debug;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.showcase.Showcase.Cell;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/** M1-SVC-05: the showcase grid is placed per chunk, at the layout's cells, refusals reported. */
class ShowcasePlacerMockBukkitTest extends MockServerTest {
    @Test
    void placesTheGridOneTaskPerChunkAndReportsRefusals() throws Exception {
        World world = server.addSimpleWorld("w");
        List<String> ids = new ArrayList<>();
        for (String ore : List.of("poor_hematite", "rich_hematite")) {
            for (int i = 0; i < 12; i++) {
                ids.add("tfp:ore/" + ore + "/rock" + (char) ('a' + i));
            }
        }
        FakeCustomBlocks blocks = new FakeCustomBlocks().known(ids.stream().filter(id -> !id.endsWith("rockl")).toArray(String[]::new));
        Set<Long> chunks = new HashSet<>();
        List<Long> tasks = new ArrayList<>();
        ShowcasePlacer placer = new ShowcasePlacer(blocks, (w, cx, cz, task) -> {
            assertThat(w).isSameAs(world);
            tasks.add(ShowcasePlacer.chunkKey(cx, cz));
            chunks.add(ShowcasePlacer.chunkKey(cx, cz));
            task.run();
        });

        // origin x = 10: 12 columns 2 apart span x 10..32, i.e. chunks 0, 1 and 2
        ShowcasePlacer.Result r = placer.place(world, 10, 70, 0, ids).get();

        assertThat(r.total()).isEqualTo(24);
        assertThat(r.placed()).isEqualTo(22);
        assertThat(r.failed()).containsExactly("tfp:ore/poor_hematite/rockl", "tfp:ore/rich_hematite/rockl");
        assertThat(r.extent()).isEqualTo(new Cell(23, 3));
        assertThat(tasks).hasSameSizeAs(chunks).hasSize(3);
        assertThat(blocks.stateAt(world.getBlockAt(10, 70, 0))).contains(BlockStateRef.of("tfp:ore/poor_hematite/rocka"));
        assertThat(blocks.stateAt(world.getBlockAt(12, 70, 2))).contains(BlockStateRef.of("tfp:ore/rich_hematite/rockb"));
        assertThat(blocks.stateAt(world.getBlockAt(11, 70, 0))).isEmpty();
        assertThat(ShowcasePlacer.details(r)).containsExactly("not placed: tfp:ore/poor_hematite/rockl",
                "not placed: tfp:ore/rich_hematite/rockl");
    }

    @Test
    void negativeCoordinatesMapToTheirChunks() {
        assertThat(ShowcasePlacer.chunkKey(-1, -1)).isNotEqualTo(ShowcasePlacer.chunkKey(-1, 0));
        long k = ShowcasePlacer.chunkKey(-3, 7);
        assertThat((int) (k >> 32)).isEqualTo(-3);
        assertThat((int) k).isEqualTo(7);
    }
}
