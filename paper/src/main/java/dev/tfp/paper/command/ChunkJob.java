/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TFCCommands.java @ c3b4d9536 (the command glue)
 */
package dev.tfp.paper.command;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.bukkit.World;

/**
 * A square of block columns worked chunk by chunk (PAR-DATA-01, the shared walk of {@code count} and {@code clearworld}).
 * Each chunk is one task on its own region thread ({@link Scheduler#region}); a task works columns until its time slice
 * is spent, then queues itself again, so a big radius never holds a tick (the budgeted region commit). A chunk that is not
 * loaded is skipped (TFC would load it; see the ledger).
 */
final class ChunkJob {
    /** The time one task works before it hands the region back and queues its rest. */
    static final long SLICE_NANOS = 4_000_000L;

    /** The inclusive block bounds of the square. */
    record Square(int minX, int maxX, int minZ, int maxZ) {
        /** The square of {@code radius} blocks around block column {@code (x, z)}: {@code 2 * radius + 1} wide. */
        static Square around(int x, int z, int radius) {
            return new Square(x - radius, x + radius, z - radius, z + radius);
        }
    }

    /** One column of the square, called on the region thread that owns it. */
    @FunctionalInterface
    interface Column {
        void work(int x, int z);
    }

    /** How the walk ended: chunks worked and chunks skipped for not being loaded. */
    record Result(int worked, int skipped) {
    }

    private ChunkJob() {
    }

    static CompletableFuture<Result> run(Scheduler scheduler, World world, Square square, LongSupplier nanos,
            long sliceNanos, Column column) {
        CompletableFuture<Result> done = new CompletableFuture<>();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = square.minX() >> 4; cx <= square.maxX() >> 4; cx++) {
            for (int cz = square.minZ() >> 4; cz <= square.maxZ() >> 4; cz++) {
                chunks.add(new int[] {cx, cz});
            }
        }
        AtomicInteger remaining = new AtomicInteger(chunks.size());
        AtomicInteger worked = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        for (int[] c : chunks) {
            int cx = c[0];
            int cz = c[1];
            int x0 = Math.max(square.minX(), cx << 4);
            int x1 = Math.min(square.maxX(), (cx << 4) + 15);
            int z0 = Math.max(square.minZ(), cz << 4);
            int z1 = Math.min(square.maxZ(), (cz << 4) + 15);
            int width = z1 - z0 + 1;
            int columns = (x1 - x0 + 1) * width;
            Runnable[] step = new Runnable[1];
            int[] next = {0};
            step[0] = () -> {
                try {
                    if (next[0] == 0 && !world.isChunkLoaded(cx, cz)) {
                        skipped.incrementAndGet();
                        next[0] = columns;
                    } else {
                        long deadline = nanos.getAsLong() + sliceNanos;
                        while (next[0] < columns) {
                            int i = next[0]++;
                            column.work(x0 + i / width, z0 + i % width);
                            if (next[0] < columns && nanos.getAsLong() >= deadline) {
                                scheduler.region(world, cx, cz, step[0]);
                                return;
                            }
                        }
                        worked.incrementAndGet();
                    }
                } catch (RuntimeException | Error e) {
                    done.completeExceptionally(e);
                    return;
                }
                if (remaining.decrementAndGet() == 0) {
                    done.complete(new Result(worked.get(), skipped.get()));
                }
            };
            scheduler.region(world, cx, cz, step[0]);
        }
        return done;
    }
}
