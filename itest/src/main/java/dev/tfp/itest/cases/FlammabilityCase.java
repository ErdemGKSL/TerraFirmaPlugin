/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java @ c3b4d9536 (flammable: the block is
 *       registered with FireBlock.setFlammable(igniteOdds, burnOdds))
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code flammability.spread} (PAR-INFRA-65): the burn settings datagen writes on the CraftEngine blocks (PAR-INFRA-47)
 * reach vanilla's fire. A 3 x 3 cluster of {@code tfp:wood/log/oak} with {@code tfp:wood/leaves/oak} on top and a 3 x 3 x 2
 * cluster of {@code tfp:rock/raw/granite} stand on stone, each with a fire block beside it that the script lights again
 * every {@value #RELIGHT_SECONDS} s while the fire spreads (a player holding the flint and steel on the base). Within
 * {@value #LIMIT_SECONDS} s fire must have consumed at least one block of the wood cluster, and no block of the granite
 * cluster may burn (TFC rock is not flammable: it has no burn settings).
 */
public final class FlammabilityCase implements SelfTestCase {
    static final String LOG = "tfp:wood/log/oak";
    static final String LEAVES = "tfp:wood/leaves/oak";
    static final String GRANITE = "tfp:rock/raw/granite";
    /** Chunks east of spawn: away from the other CE block cases (40 to 72). */
    static final int CHUNK_OFFSET = 76;
    static final int LIMIT_SECONDS = 120;
    static final int RELIGHT_SECONDS = 3;
    /** The 1.21.11 game rule that limits fire ticks to the surroundings of players (a no-op command on older versions). */
    static final String FIRE_RADIUS_RULE = "fire_spread_radius_around_player";
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "flammability.spread: fire consumes a tfp oak log and leaves cluster, a granite cluster does not (PAR-INFRA-65)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60 + LIMIT_SECONDS + 10 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        return OnThread.script("flammability", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            for (String id : List.of(LOG, LEAVES, GRANITE)) {
                if (!blocks.exists(id)) {
                    throw new AssertionError(id + " is not a loaded CraftEngine block");
                }
            }
            return burn(tfp, blocks);
        });
    }

    private static String burn(TfpPlugin tfp, CustomBlocks blocks) throws Exception {
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        int cx = (world.getSpawnLocation().getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = world.getSpawnLocation().getBlockZ() >> 4;
        int x0 = (cx << 4) + 2;
        int z0 = (cz << 4) + 6;
        int graniteX0 = x0 + 8;
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            // 1.21.11: fire only spreads within this many blocks of a player (default 128), and the self-test has none
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule " + FIRE_RADIUS_RULE + " -1");
            return null;
        });
        int[] base = {Integer.MIN_VALUE};
        List<String> log = new ArrayList<>();
        try {
            int total = OnThread.region(s, world, cx, cz, STEP, () -> {
                int y = CraftEngineBlockRoundTripCase.freeY(world, blocks, x0, world.getHighestBlockYAt(x0, z0) + 3, z0, log);
                base[0] = y;
                for (int dx = -1; dx <= 3; dx++) {
                    for (int dz = -1; dz <= 3; dz++) {
                        world.getBlockAt(x0 + dx, y - 1, z0 + dz).setType(Material.STONE, false);
                        world.getBlockAt(graniteX0 + dx, y - 1, z0 + dz).setType(Material.STONE, false);
                        for (int dy = 0; dy < 3; dy++) {
                            world.getBlockAt(x0 + dx, y + dy, z0 + dz).setType(Material.AIR, false);
                            world.getBlockAt(graniteX0 + dx, y + dy, z0 + dz).setType(Material.AIR, false);
                        }
                    }
                }
                for (int dx = 0; dx < 3; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        place(blocks, world.getBlockAt(x0 + dx, y, z0 + dz), LOG);
                        place(blocks, world.getBlockAt(x0 + dx, y + 1, z0 + dz), LEAVES);
                        place(blocks, world.getBlockAt(graniteX0 + dx, y, z0 + dz), GRANITE);
                        place(blocks, world.getBlockAt(graniteX0 + dx, y + 1, z0 + dz), GRANITE);
                    }
                }
                return 18;
            });
            log.add(total + " wood blocks and 18 granite blocks at " + x0 + "," + base[0] + "," + z0);
            int y = base[0];
            int burnt = 0;
            int granite = 18;
            long deadline = System.nanoTime() + Duration.ofSeconds(LIMIT_SECONDS).toNanos();
            while (System.nanoTime() < deadline) {
                int[] counts = OnThread.region(s, world, cx, cz, STEP, () -> {
                    // the fire starter: a fire block beside the base of each cluster where the cell is air
                    for (int dz = 0; dz < 3; dz++) {
                        light(world.getBlockAt(x0 - 1, y, z0 + dz));
                        light(world.getBlockAt(graniteX0 - 1, y, z0 + dz));
                    }
                    int wood = 0;
                    int rock = 0;
                    for (int dx = 0; dx < 3; dx++) {
                        for (int dz = 0; dz < 3; dz++) {
                            for (int dy = 0; dy < 2; dy++) {
                                wood += blocks.stateAt(world.getBlockAt(x0 + dx, y + dy, z0 + dz)).isPresent() ? 1 : 0;
                                rock += blocks.stateAt(world.getBlockAt(graniteX0 + dx, y + dy, z0 + dz)).isPresent() ? 1 : 0;
                            }
                        }
                    }
                    return new int[] {wood, rock};
                });
                burnt = 18 - counts[0];
                granite = counts[1];
                if (granite != 18) {
                    throw new AssertionError("the granite cluster lost " + (18 - granite) + " blocks to fire");
                }
                if (burnt >= 4) {
                    break;
                }
                Thread.sleep(RELIGHT_SECONDS * 1000L);
            }
            if (burnt < 1) {
                throw new AssertionError("fire consumed no block of the tfp oak log and leaves cluster in " + LIMIT_SECONDS
                    + " s (the burn settings of PAR-INFRA-47 do not reach FireBlock?)");
            }
            log.add("fire consumed " + burnt + " of 18 wood blocks, granite cluster intact (" + granite + "/18)");
            return String.join("; ", log);
        } finally {
            if (base[0] != Integer.MIN_VALUE) {
                int y = base[0];
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        for (int dx = -1; dx <= 3; dx++) {
                            for (int dz = -1; dz <= 3; dz++) {
                                for (int dy = 0; dy < 3; dy++) {
                                    for (int bx : new int[] {x0 + dx, graniteX0 + dx}) {
                                        Block b = world.getBlockAt(bx, y + dy, z0 + dz);
                                        blocks.remove(b);
                                        b.setType(Material.AIR, false);
                                    }
                                }
                            }
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("flammability.spread cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule " + FIRE_RADIUS_RULE + " 128");
                return null;
            });
        }
    }

    private static void place(CustomBlocks blocks, Block b, String id) {
        if (!blocks.place(b, BlockStateRef.of(id), false)) {
            throw new AssertionError("place(" + id + ") returned false at " + b.getX() + "," + b.getY() + "," + b.getZ());
        }
    }

    private static void light(Block b) {
        if (b.getType().isAir()) {
            b.setType(Material.FIRE, true);
        }
    }
}
