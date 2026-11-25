/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.crop.CropIds;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code crop.grow} (PAR-FARM-20): a {@code tfp:crop/wheat} on a {@code tfp:farmland/<soil>} block, 60 calendar days, then the chunk is
 * unloaded and loaded again so the catch-up of the crop device runs: the crop must have a higher age or have died
 * ({@code tfp:dead_crop/wheat}). Skipped (not failed) while the farmland blocks are not in the pack: the soil block set and the
 * farmland CraftEngine blocks of the follow-up item are what it needs. Run it with the device and crop modules enabled.
 */
public final class CropGrowCase implements SelfTestCase {
    /** Chunks east of spawn: away from the other cases (catchup.lazyblock 56). */
    static final int CHUNK_OFFSET = 64;
    private static final Duration STEP = Duration.ofSeconds(20);
    private static final List<String> SOILS = List.of("tfp:farmland/loam", "tfp:farmland/silt", "tfp:farmland/sandy_loam",
        "tfp:farmland/silty_loam");

    @Override
    public String name() {
        return "crop.grow: wheat on farmland grows or dies over 60 days and catches up on chunk load (PAR-FARM-20)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(8 * STEP.toSeconds() + 90);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        CustomBlocks blocks = tfp.integrations().customBlocks();
        Scheduler s = tfp.scheduler();
        return OnThread.script("crop-grow", () -> {
            String soil = SOILS.stream().filter(blocks::exists).findFirst()
                .orElseThrow(() -> new Skip("no tfp:farmland/<soil> block is loaded (the farmland CE blocks are missing)"));
            String wheat = CropIds.livingId(dev.tfp.core.crop.CropType.WHEAT);
            if (!blocks.exists(wheat)) {
                throw new Skip(wheat + " is not loaded");
            }
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            int cz = spawn.getBlockZ() >> 4;
            int x = (cx << 4) + 8;
            int z = (cz << 4) + 8;
            int y = world.getMinHeight() + 6;
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            try {
                OnThread.region(s, world, cx, cz, STEP, () -> {
                    Block farm = world.getBlockAt(x, y, z);
                    Block crop = farm.getRelative(0, 1, 0);
                    crop.setType(Material.AIR, false);
                    check(blocks.place(farm, BlockStateRef.of(soil), false), "the farmland " + soil + " was refused");
                    check(blocks.place(crop, BlockStateRef.of(wheat).with(CropIds.AGE, "0"), false), "the wheat was refused");
                    return null;
                });
                OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.DAYS, 60)));
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
                Thread.sleep(3_000);
                OnThread.await(world.getChunkAtAsync(cx, cz), Duration.ofSeconds(60), "reloading chunk " + cx + "," + cz);
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, true);
                    return null;
                });
                String result = null;
                long deadline = System.nanoTime() + STEP.toNanos();
                while (result == null && System.nanoTime() < deadline) {
                    result = OnThread.region(s, world, cx, cz, STEP, () -> {
                        Optional<BlockStateRef> state = blocks.stateAt(world.getBlockAt(x, y + 1, z));
                        if (state.isEmpty()) {
                            throw new AssertionError("the crop block is gone");
                        }
                        if (state.get().id().startsWith(CropIds.DEAD)) {
                            return "the wheat died: " + state.get();
                        }
                        return CropIds.age(state.get()) > 0 ? "the wheat grew to age " + CropIds.age(state.get()) : null;
                    });
                    if (result == null) {
                        Thread.sleep(200);
                    }
                }
                check(result != null, "after 60 days the wheat is still at age 0 and alive: the catch-up did not run");
                return result + " on " + soil;
            } finally {
                OnThread.region(s, world, cx, cz, STEP, () -> {
                    world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                    world.getBlockAt(x, y, z).setType(Material.STONE, false);
                    return null;
                });
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
