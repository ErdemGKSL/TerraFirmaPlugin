/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.ClearWorldCommand;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.command.CountBlockCommand;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-DATA-01: {@code /tfp count} and {@code /tfp clearworld} on a MockBukkit world. */
class WorldCommandsMockBukkitTest extends MockServerTest {
    private final QueueScheduler scheduler = new QueueScheduler();
    private final FakeCustomBlocks custom = new FakeCustomBlocks().known("tfp:rock/raw/granite", "tfp:rock/hardened/granite",
            "tfp:ore/rich_native_copper/granite", "tfp:dirt/silt", "tfp:grass/silt");
    private final AtomicLong clock = new AtomicLong();
    private WorldCommands commands;
    private World world;

    @BeforeEach
    void setUpWorld() {
        world = server.addSimpleWorld("data");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz); // MockBukkit loads on demand; the commands only walk loaded chunks
            }
        }
        commands = new WorldCommands(scheduler, custom,
                (state, tag) -> tag.equals("tfc:rock") && state.id().startsWith("tfp:rock/"),
                (type, tag) -> tag.equals("minecraft:logs") && type == Material.OAK_LOG, clock::get);
    }

    private void place(int x, int y, int z, String id) {
        custom.place(world.getBlockAt(x, y, z), BlockStateRef.of(id), false);
        world.getBlockAt(x, y, z).setType(Material.STONE, false);
    }

    private static CountBlockCommand.Request count(int radius, String block) {
        return ((Parsed.Ok<CountBlockCommand.Request>) CountBlockCommand.parse(java.util.List.of(Integer.toString(radius), block)))
                .value();
    }

    private static ClearWorldCommand.Request clear(int radius, String preset) {
        return ((Parsed.Ok<ClearWorldCommand.Request>) ClearWorldCommand.parse(java.util.List.of(Integer.toString(radius), preset)))
                .value();
    }

    private Reply run(java.util.concurrent.CompletableFuture<Reply> future) {
        scheduler.drain();
        assertThat(future).isDone();
        return future.join();
    }

    @Test
    void countsVanillaBlocksInTheSquareOverTheWholeHeight() {
        world.getBlockAt(0, 10, 0).setType(Material.STONE);
        world.getBlockAt(1, world.getMinHeight(), -1).setType(Material.STONE);
        world.getBlockAt(2, 70, 0).setType(Material.STONE); // column 2: outside radius 1
        world.getBlockAt(1, 5, 1).setType(Material.DIRT);
        Reply reply = run(commands.count(new Location(world, 0.5, 64, 0.5), count(1, "stone")));
        assertThat(reply).isEqualTo(Reply.ok("command.data.count.done", 2L));
    }

    @Test
    void countsCustomBlocksByIdPropertiesAndTag() {
        place(0, 60, 0, "tfp:rock/raw/granite");
        place(1, 60, 0, "tfp:rock/hardened/granite");
        place(0, 61, 1, "tfp:rock/raw/granite");
        Location at = new Location(world, 0, 64, 0);
        assertThat(run(commands.count(at, count(2, "tfp:rock/raw/granite"))).args()).containsExactly(2L);
        assertThat(run(commands.count(at, count(2, "#tfc:rock"))).args()).containsExactly(3L);
        assertThat(run(commands.count(at, count(2, "tfp:rock/raw/granite[x=y]"))).args()).containsExactly(0L);
        world.getBlockAt(0, 70, 0).setType(Material.OAK_LOG);
        assertThat(run(commands.count(at, count(2, "#minecraft:logs"))).args()).containsExactly(1L);
    }

    @Test
    void aVanillaBlockStatePropertyIsMatchedThroughItsData() {
        world.getBlockAt(0, 70, 0).setType(Material.OAK_LOG);
        Location at = new Location(world, 0, 64, 0);
        assertThat(run(commands.count(at, count(1, "oak_log"))).args()).containsExactly(1L);
        assertThat(run(commands.count(at, count(1, "oak_log[axis=y]"))).args()).containsExactly(1L);
        assertThat(run(commands.count(at, count(1, "oak_log[axis=x]"))).args()).containsExactly(0L);
    }

    @Test
    void theCountIsTheSameWhenEveryColumnIsItsOwnSlice() {
        for (int x = -3; x <= 3; x++) {
            world.getBlockAt(x, 40, x).setType(Material.STONE);
        }
        Location at = new Location(world, 0, 64, 0);
        Reply whole = run(commands.count(at, count(5, "stone")));
        scheduler.regionRuns = 0;
        // slice length is a constant, so make the clock jump by more than a slice per read
        WorldCommands jumping = new WorldCommands(scheduler, custom, (s, t) -> false, (t, g) -> false,
                new java.util.function.LongSupplier() {
                    long t;

                    @Override
                    public long getAsLong() {
                        return t += ChunkJob.SLICE_NANOS;
                    }
                });
        Reply split = run(jumping.count(at, count(5, "stone")));
        assertThat(split).isEqualTo(whole);
        assertThat(split.args()).containsExactly(7L);
        assertThat(scheduler.regionRuns).as("one task per column of the chunks").isGreaterThan(100);
    }

    @Test
    void anUnloadedChunkIsSkippedAndSaid() {
        world.getBlockAt(0, 10, 0).setType(Material.STONE);
        Location at = new Location(world, 0, 64, 0);
        // the square reaches chunk -1; far chunks (radius 40 reaches chunk 2 and -3) were never loaded
        Reply reply = run(commands.count(at, count(40, "stone")));
        assertThat(reply.args()).containsExactly(1L);
        assertThat(reply.details()).singleElement().asString().contains("not loaded");
        assertThat(run(commands.count(at, count(1, "stone"))).details()).isEmpty();
    }

    @Test
    void clearWorldRemovesFromTheBottomUpToEachSurfaceByPreset() {
        long ground = 0; // the flat world's own blocks in the 3 x 3 columns: vanilla, so only ALL and NOT_ORE take them
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    ground += world.getBlockAt(x, y, z).getType().isAir() ? 0 : 1;
                }
            }
        }
        // column (0,0): raw rock at 5, ore at 6, dirt at 7, vanilla stone at 8 (surface is the highest block)
        place(0, 5, 0, "tfp:rock/raw/granite");
        place(0, 6, 0, "tfp:ore/rich_native_copper/granite");
        place(0, 7, 0, "tfp:dirt/silt");
        world.getBlockAt(0, 8, 0).setType(Material.STONE);
        Location at = new Location(world, 0, 64, 0);
        Reply reply = run(commands.clear(at, clear(1, "RAW_ROCK")));
        assertThat(reply).isEqualTo(Reply.ok("command.data.clear.done", 1L));
        assertThat(custom.stateAt(world.getBlockAt(0, 5, 0))).isEmpty();
        assertThat(custom.stateAt(world.getBlockAt(0, 6, 0))).isPresent();

        reply = run(commands.clear(at, clear(1, "EARTH")));
        assertThat(reply.args()).as("dirt only: the ore and vanilla stone stay").containsExactly(1L);
        assertThat(custom.stateAt(world.getBlockAt(0, 7, 0))).isEmpty();

        reply = run(commands.clear(at, clear(1, "NOT_ORE")));
        assertThat(reply.args()).as("the ground, the vanilla stone and the stone under each removed block; the ore stays")
                .containsExactly(ground + 3);
        assertThat(world.getBlockAt(0, 8, 0).getType()).isEqualTo(Material.AIR);
        assertThat(custom.stateAt(world.getBlockAt(0, 6, 0))).isPresent();

        reply = run(commands.clear(at, clear(1, "ALL")));
        assertThat(reply.args()).containsExactly(1L);
        assertThat(custom.stateAt(world.getBlockAt(0, 6, 0))).as("a custom block goes through the adapter").isEmpty();
    }

    @Test
    void clearWorldAgreesWithTheCoreSweep() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y < 4 + (x + z + 4) % 3; y++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE);
                }
            }
        }
        int expected = ClearWorldCommand.sweep(2, ClearWorldCommand.Preset.ALL, world.getMinHeight(),
                (dx, dz) -> world.getHighestBlockYAt(dx, dz, org.bukkit.HeightMap.WORLD_SURFACE) + 1,
                (dx, y, dz) -> world.getBlockAt(dx, y, dz).getType().isAir() ? ClearWorldCommand.Kind.AIR
                        : ClearWorldCommand.Kind.OTHER,
                (dx, y, dz) -> { });
        // vanilla blocks are OTHER: only ALL removes them
        Reply reply = run(commands.clear(new Location(world, 0, 64, 0), clear(2, "ALL")));
        assertThat(reply.args()).containsExactly((long) expected);
        assertThat(expected).isPositive();
    }
}
