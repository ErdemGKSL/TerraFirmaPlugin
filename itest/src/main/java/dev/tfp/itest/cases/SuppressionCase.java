/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.LogFindings;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * {@code inst.suppression} (M1-INST-04): lava next to water in the world, through the server's own fluid
 * logic, does not create the repurposed block; our listener replaces it and logs
 * {@code suppress: BlockFormEvent at <world> <x>,<y>,<z>: ...} for the lava position.
 *
 * <p>Setup, {@value #CHUNK_OFFSET} chunks east of spawn (force-loaded while it runs), in the air above the
 * terrain inside a stone box (floor and walls, so nothing flows out): a water source (placed without physics,
 * it stays put), then a lava source next to it and a flowing lava block next to the water, both with physics,
 * so the lava's own placement check runs ({@code LiquidBlock.shouldSpreadLiquid} fires {@code BlockFormEvent}).
 * The case waits up to {@value #WAIT_SECONDS} s (fluid ticks) for the log line through the live log tap, then
 * clears the box. Skipped without the live log tap or when the server logged no suppression list at startup.
 */
public final class SuppressionCase implements SelfTestCase {
    /** {@code TfpPlugin.enableVisuals}' startup line when the suppression list loaded. */
    public static final String STARTUP_LINE = "Vanilla-block suppression: ";
    /** {@code SuppressionListener.suppressLine} for a fluid formation. */
    public static final String FORM_LINE = "suppress: BlockFormEvent at ";
    static final int CHUNK_OFFSET = 44;
    static final int WAIT_SECONDS = 10;
    private static final Duration STEP = Duration.ofSeconds(20);
    /** Box: interior 3 x 1 x 3 at (x..x+2, y, z..z+2); floor at y-1, walls one block around, two high. */
    private static final int LOW = -1;
    private static final int HIGH = 3;

    /** True if {@code line} is our suppression log line for a block formation at exactly this position. */
    static boolean formLineFor(String line, String world, int x, int y, int z) {
        return line.contains(FORM_LINE + world + " " + x + "," + y + "," + z + ": ");
    }

    /** The startup line, if the server logged that the suppression list loaded. */
    static Optional<String> startupLine(List<String> lines) {
        return lines.stream().filter(l -> l.contains(STARTUP_LINE)).findFirst();
    }

    @Override
    public String name() {
        return "inst.suppression: lava next to water forms no repurposed block and logs the suppression (M1-INST-04)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(4 * STEP.toSeconds() + WAIT_SECONDS + 30);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        LogFindings log = ctx.logFindings();
        if (log == null || !ctx.liveLog()) {
            throw new Skip("no live log tap: the suppression log line cannot be observed during the run");
        }
        String loaded = startupLine(log.linesContaining(STARTUP_LINE)).orElseThrow(() -> new Skip(
                "the server logged no suppression list at startup (no \"" + STARTUP_LINE.trim() + "\" line)"));
        Scheduler s = tfp.scheduler();
        return OnThread.script("suppression", () -> {
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            int cz = spawn.getBlockZ() >> 4;
            int x = (cx << 4) + 4;
            int z = (cz << 4) + 4;
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            int[] yHolder = {Integer.MIN_VALUE};
            try {
                int y = OnThread.region(s, world, cx, cz, STEP, () -> {
                    int top = freeY(world, x, world.getHighestBlockYAt(x, z) + 3, z);
                    yHolder[0] = top;
                    build(world, x, top, z);
                    return top;
                });
                String w = world.getName();
                // lava source at (x, y, z), water at (x+1, y, z), flowing lava at (x+1, y, z+1)
                boolean source = OnThread.poll(Duration.ofSeconds(WAIT_SECONDS), () -> seen(log, w, x, y, z));
                boolean flowing = seen(log, w, x + 1, y, z + 1);
                String after = OnThread.region(s, world, cx, cz, STEP,
                        () -> world.getBlockAt(x, y, z).getBlockData().getAsString());
                if (!source) {
                    throw new AssertionError("no \"" + FORM_LINE + w + " " + x + "," + y + "," + z + ": ...\" line within "
                            + WAIT_SECONDS + " s; the lava source position is now " + after + " (" + loaded + ")");
                }
                return "lava source at " + x + "," + y + "," + z + " -> " + after + " with the suppression line logged"
                        + "; flowing lava " + (flowing ? "also logged" : "not logged") + " (" + loaded + ")";
            } finally {
                if (yHolder[0] != Integer.MIN_VALUE) {
                    int y = yHolder[0];
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        clear(world, x, y, z);
                        return null;
                    });
                }
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
            }
        });
    }

    private static boolean seen(LogFindings log, String world, int x, int y, int z) {
        return log.linesContaining(FORM_LINE).stream().anyMatch(l -> formLineFor(l, world, x, y, z));
    }

    /** Lowest y at or above {@code fromY} where the whole box (floor to wall top) is air. */
    private static int freeY(World world, int x, int fromY, int z) {
        int maxY = world.getMaxHeight() - 3;
        for (int y = Math.max(fromY, world.getMinHeight() + 2); y <= maxY; y++) {
            if (boxIsAir(world, x, y, z)) {
                return y;
            }
        }
        throw new AssertionError("no free 5x3x5 space above y " + fromY + " at " + x + "," + z);
    }

    private static boolean boxIsAir(World world, int x, int y, int z) {
        for (int dx = LOW; dx <= HIGH; dx++) {
            for (int dz = LOW; dz <= HIGH; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (!world.getBlockAt(x + dx, y + dy, z + dz).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void build(World world, int x, int y, int z) {
        for (int dx = LOW; dx <= HIGH; dx++) {
            for (int dz = LOW; dz <= HIGH; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE, false);
                boolean wall = dx == LOW || dx == HIGH || dz == LOW || dz == HIGH;
                if (wall) {
                    world.getBlockAt(x + dx, y, z + dz).setType(Material.STONE, false);
                    world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.STONE, false);
                }
            }
        }
        world.getBlockAt(x + 1, y, z).setType(Material.WATER, false);
        world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData("minecraft:lava"), true);
        world.getBlockAt(x + 1, y, z + 1).setBlockData(Bukkit.createBlockData("minecraft:lava[level=1]"), true);
    }

    private static void clear(World world, int x, int y, int z) {
        for (int dx = LOW; dx <= HIGH; dx++) {
            for (int dz = LOW; dz <= HIGH; dz++) {
                for (int dy = 1; dy >= -1; dy--) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }
    }
}
