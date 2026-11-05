/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.loot.LootTable;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * {@code loot} (PAR-INFRA-21): the TFC loot tables datagen ships load in the Paper loot runtime with no error (the entity
 * tables of the spawned TFC animals among them), and every delegated CraftEngine block (no CE {@code loot:}, table in the
 * jar) drops what its TFC table says.
 *
 * <p>Checks per block, each only once the block is generated and delegated (otherwise the detail names the item that
 * delivers it): loose rock {@code [count=3]} by hand gives 3 loose rocks (PAR-WORLD-09); oak leaves give the leaves with
 * shears and never by hand (PAR-WORLD-10); raw granite gives nothing by hand and 1-4 loose rocks with
 * {@code tfp:metal/pickaxe/bronze} (PAR-INFRA-28). A break is the listener's path without a player (the self-test has
 * none): CraftEngine's tool check ({@code CustomBlocks.isCorrectTool}), the roll with the tool snapshot, the drop, then
 * the block's removal; {@code LootBreakMockBukkitTest} covers the event itself. The first delegated block is also blown
 * up by a real explosion (the listener's event path; yield set to 1 so nothing decays), which must drop exactly its
 * table's items: nothing from CraftEngine or the vanilla carrier as well.
 */
public final class LootCase implements SelfTestCase {
    static final int CHUNK_OFFSET = 44;
    /** Above the chunk's highest block: the explosion (power {@value #POWER}) reaches about 9 blocks. */
    static final int HEIGHT = 16;
    static final float POWER = 5f;
    private static final Duration STEP = Duration.ofSeconds(20);
    private static final String LOOSE = "tfp:rock/loose/granite";
    private static final String RAW = "tfp:rock/raw/granite";
    private static final String LEAVES = "tfp:wood/leaves/oak";
    private static final String SAPLING = "tfp:wood/sapling/oak";
    private static final String STICK = "minecraft:stick";
    private static final String BRONZE_PICKAXE = "tfp:metal/pickaxe/bronze";

    /** One break: the block, its properties, the tool (null: empty hand), the item that delivers it, the expectation. */
    record Check(String block, Map<String, String> properties, String tool, String owner,
            Function<Map<String, Integer>, String> expect, boolean explode) {
    }

    static final List<Check> CHECKS = List.of(
            new Check(LOOSE, Map.of("count", "3"), null, "PAR-WORLD-09", d -> exactly(d, Map.of(LOOSE, 3)), true),
            new Check(LEAVES, Map.of(), "minecraft:shears", "PAR-WORLD-10",
                    d -> d.getOrDefault(LEAVES, 0) != 1 ? "expected 1 " + LEAVES : only(d, Set.of(LEAVES, SAPLING)), false),
            new Check(LEAVES, Map.of(), null, "PAR-WORLD-10",
                    d -> d.containsKey(LEAVES) ? "leaves dropped by hand" : only(d, Set.of(STICK, SAPLING)), false),
            new Check(RAW, Map.of(), null, "PAR-INFRA-28", d -> exactly(d, Map.of()), false),
            new Check(RAW, Map.of(), BRONZE_PICKAXE, "PAR-INFRA-28", d -> between(d, LOOSE, 1, 4), true));

    @Override
    public String name() {
        return "loot: shipped TFC loot tables load and delegated CraftEngine blocks drop by them (PAR-INFRA-21)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(CraftEngineBlockRoundTripCase.REBUILD.toSeconds() + 60 + (CHECKS.size() * 2 + 4) * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        LootService service = tfp.moduleServices().get(LootService.class)
                .orElseThrow(() -> new AssertionError("no LootService: the loot module is not enabled"));
        return OnThread.script("loot", () -> {
            List<String> log = new ArrayList<>();
            // the tables load on the CPU pool after enable
            if (!OnThread.poll(Duration.ofSeconds(60), () -> service.tables().size() > 0)) {
                throw new AssertionError("no loot table loaded within 60 s (datagen ships data/tfp/loot_table)");
            }
            if (!service.tables().errors().isEmpty()) {
                throw new AssertionError("loot tables failed to parse: " + service.tables().errors());
            }
            for (String entity : List.of("tfp:cow", "tfp:chicken", "tfp:bluegill")) {
                if (service.tables().forEntity(entity).isEmpty()) {
                    throw new AssertionError("no shipped entity table for " + entity);
                }
            }
            log.add(service.tables().size() + " tables loaded, no errors");
            if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
                log.add("block checks skipped: CraftEngine is not installed");
                return String.join("; ", log);
            }
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            blockChecks(tfp, service, log);
            return String.join("; ", log);
        });
    }

    private static void blockChecks(TfpPlugin tfp, LootService service, List<String> log) throws Exception {
        CustomBlocks blocks = tfp.integrations().customBlocks();
        CustomItems items = tfp.integrations().customItems();
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        int x = (cx << 4) + 8;
        int z = (cz << 4) + 8;
        List<Check> ready = new ArrayList<>();
        for (Check c : CHECKS) {
            if (blocks.exists(c.block()) && service.handlesBlock(c.block())) {
                ready.add(c);
            } else {
                log.add(c.block() + (c.tool() == null ? " by hand" : " with " + c.tool()) + ": not delegated yet ("
                        + c.owner() + ")");
            }
        }
        if (ready.isEmpty()) {
            return;
        }
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        try {
            int y = OnThread.region(s, world, cx, cz, STEP, () -> {
                int top = world.getMinHeight();
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        top = Math.max(top, world.getHighestBlockYAt((cx << 4) + dx, (cz << 4) + dz));
                    }
                }
                return Math.min(top + HEIGHT, world.getMaxHeight() - 4);
            });
            boolean exploded = false;
            for (Check c : ready) {
                log.add(breakCheck(service, blocks, items, s, world, cx, cz, x, y, z, c));
                if (c.explode() && !exploded) {
                    log.add(explodeCheck(tfp, blocks, items, s, world, cx, cz, x, y, z, c));
                    exploded = true;
                }
            }
        } finally {
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static String breakCheck(LootService service, CustomBlocks blocks, CustomItems items, Scheduler s, World world,
            int cx, int cz, int x, int y, int z, Check c) throws Exception {
        CompletableFuture<List<ItemStack>> dropped = OnThread.region(s, world, cx, cz, STEP, () -> {
            Block b = place(blocks, world, x, y, z, c);
            try {
                ItemStack tool = tool(items, c.tool());
                BlockStateRef state = blocks.stateAt(b).orElseThrow();
                LootTable table = service.tables().forBlock(state.id()).orElseThrow();
                if (!blocks.isCorrectTool(b, tool)) {
                    return CompletableFuture.completedFuture(List.<ItemStack>of());
                }
                LootContext context = LootContext.builder().block(state.id(), state.properties())
                        .tool(service.tool(tool)).build();
                return service.dropBlock(b, table, context);
            } finally {
                blocks.remove(b);
            }
        });
        OnThread.await(dropped, STEP, "the drop of " + c.block());
        Thread.sleep(500);
        Map<String, Integer> seen = collect(s, world, cx, cz, x, y, z, items);
        String what = c.block() + c.properties() + (c.tool() == null ? " by hand" : " with " + c.tool());
        String problem = c.expect().apply(seen);
        if (problem != null) {
            throw new AssertionError(what + " dropped " + seen + ": " + problem);
        }
        return what + " -> " + seen;
    }

    private static String explodeCheck(TfpPlugin tfp, CustomBlocks blocks, CustomItems items, Scheduler s, World world,
            int cx, int cz, int x, int y, int z, Check c) throws Exception {
        Location at = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        Listener noDecay = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onBlock(BlockExplodeEvent e) {
                if (near(e.getBlock().getLocation(), at)) {
                    e.setYield(1f);
                }
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onEntity(EntityExplodeEvent e) {
                if (near(e.getLocation(), at)) {
                    e.setYield(1f);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(noDecay, tfp);
        try {
            OnThread.region(s, world, cx, cz, STEP, () -> {
                Block b = place(blocks, world, x, y, z, c);
                try {
                    world.createExplosion(at, POWER, false, true);
                } finally {
                    if (blocks.stateAt(b).isPresent()) {
                        blocks.remove(b);
                        throw new AssertionError("the explosion did not destroy " + c.block());
                    }
                }
                return null;
            });
        } finally {
            HandlerList.unregisterAll(noDecay);
        }
        Thread.sleep(2000); // the roll runs on the CPU pool, the drop back on the region
        Map<String, Integer> seen = collect(s, world, cx, cz, x, y, z, items);
        // in an explosion the tool is empty: the table's hand result, and no CraftEngine or carrier drop on top
        Check byHand = CHECKS.stream().filter(k -> k.block().equals(c.block()) && k.tool() == null).findFirst().orElse(c);
        Function<Map<String, Integer>, String> expect = c.block().equals(RAW) ? d -> between(d, LOOSE, 1, 4) : byHand.expect();
        String problem = expect.apply(seen);
        if (problem != null) {
            throw new AssertionError(c.block() + " in an explosion dropped " + seen + ": " + problem);
        }
        return c.block() + " exploded -> " + seen;
    }

    private static boolean near(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) < 4;
    }

    /** Places the check's state on a 5x5 barrier floor (catches the drops, stops the explosion going down). */
    private static Block place(CustomBlocks blocks, World world, int x, int y, int z, Check c) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.BARRIER, false);
            }
        }
        Block b = world.getBlockAt(x, y, z);
        if (blocks.stateAt(b).isPresent()) {
            blocks.remove(b);
        }
        b.setType(Material.AIR, false);
        BlockStateRef state = BlockStateRef.of(c.block(), c.properties());
        if (!blocks.place(b, state, false)) {
            throw new AssertionError("place(" + state + ") returned false");
        }
        Optional<BlockStateRef> read = blocks.stateAt(b);
        if (read.isEmpty() || !read.get().id().equals(c.block())) {
            throw new AssertionError("after place(" + state + "): " + read);
        }
        for (Map.Entry<String, String> p : c.properties().entrySet()) {
            if (!p.getValue().equals(read.get().properties().get(p.getKey()))) {
                throw new AssertionError("after place(" + state + "): " + read.get());
            }
        }
        return b;
    }

    private static ItemStack tool(CustomItems items, String id) {
        if (id == null) {
            return new ItemStack(Material.AIR);
        }
        if (items.exists(id)) {
            return items.create(id, 1).orElseThrow(() -> new AssertionError("cannot create " + id));
        }
        Material m = Material.matchMaterial(id);
        if (m == null) {
            throw new AssertionError("unknown tool " + id);
        }
        return new ItemStack(m);
    }

    /** The dropped items above the barrier floor, by item id, removed afterwards; the floor is removed too. */
    private static Map<String, Integer> collect(Scheduler s, World world, int cx, int cz, int x, int y, int z,
            CustomItems items) throws Exception {
        return OnThread.region(s, world, cx, cz, STEP, () -> {
            Map<String, Integer> out = new TreeMap<>();
            Location center = new Location(world, x + 0.5, y + 1, z + 0.5);
            for (Entity e : world.getNearbyEntities(center, 4, 4, 4, e -> e instanceof Item)) {
                ItemStack stack = ((Item) e).getItemStack();
                String id = items.idOf(stack).orElse(stack.getType().getKey().toString());
                out.merge(id, stack.getAmount(), Integer::sum);
                e.remove();
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.AIR, false);
                }
            }
            return out;
        });
    }

    static String exactly(Map<String, Integer> seen, Map<String, Integer> expected) {
        return seen.equals(expected) ? null : "expected exactly " + expected;
    }

    static String only(Map<String, Integer> seen, Set<String> allowed) {
        return allowed.containsAll(seen.keySet()) ? null : "only " + allowed + " may drop";
    }

    static String between(Map<String, Integer> seen, String item, int min, int max) {
        int n = seen.getOrDefault(item, 0);
        if (seen.size() > 1 || (!seen.isEmpty() && !seen.containsKey(item))) {
            return "only " + item + " may drop";
        }
        return n >= min && n <= max ? null : "expected " + min + "-" + max + " " + item;
    }
}
