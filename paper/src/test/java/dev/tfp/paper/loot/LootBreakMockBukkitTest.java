/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-INFRA-09: the Paper side on MockBukkit. A CraftEngine block (fake adapter) whose table is loaded drops TFC's loot
 * when a player breaks it (tool from the main hand, CraftEngine/vanilla drops switched off) and when an explosion with
 * decay destroys it; creative breaks and blocks without a table are left alone; item-data ops reach the hooks.
 */
class LootBreakMockBukkitTest extends MockServerTest {
    private static final String LOOSE = "tfp:rock/loose/granite";
    private static final String LEAVES = "tfp:wood/leaves/oak";

    /** Region and CPU work run inline: the test thread owns every region. */
    private static final class InlineScheduler implements Scheduler {
        int regionTasks;

        @Override
        public void region(Location location, Runnable task) {
            regionTasks++;
            task.run();
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            regionTasks++;
            task.run();
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Executor ioExecutor() {
            return Runnable::run;
        }

        @Override
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }

    /** A generator whose every float is {@code value} (0 or just under 1) and every bounded int is 0 or bound - 1. */
    private static RandomGenerator constant(long bits) {
        return () -> bits;
    }

    private static final RandomGenerator LOW = constant(0L);
    private static final RandomGenerator HIGH = constant(-1L);

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LOOSE, LEAVES, "tfp:other");
    private final FakeCustomItems items = new FakeCustomItems();
    private final InlineScheduler scheduler = new InlineScheduler();
    private final AtomicReference<RandomGenerator> random = new AtomicReference<>(LOW);
    private final AtomicReference<LootStackHooks> hooks = new AtomicReference<>(LootStackHooks.NONE);
    private final AtomicReference<LootBreakListener.CorrectTool> correctTool =
            new AtomicReference<>(LootBreakListener.CorrectTool.ANY);
    private LootService service;
    private WorldMock world;
    private PlayerMock player;

    /** TFC's tables as datagen ships them (ids renamed to our namespace). */
    private static LootTable renamed(String id, String json) {
        return LootTableParser.parse(id, json.replace("\"tfc:", "\"tfp:"));
    }

    @BeforeEach
    void setUp() {
        items.define(LOOSE, n -> new ItemStack(Material.COBBLESTONE, n));
        items.define(LEAVES, n -> new ItemStack(Material.OAK_LEAVES, n));
        items.define("tfp:wood/sapling/oak", n -> new ItemStack(Material.OAK_SAPLING, n));
        items.define("tfp:melon", n -> new ItemStack(Material.MELON, n));
        LootTables tables = new LootTables(Map.of(
                "tfp:blocks/rock/loose/granite", renamed("tfp:blocks/rock/loose/granite", TfcLootFixtures.LOOSE_GRANITE),
                "tfp:blocks/wood/leaves/oak", renamed("tfp:blocks/wood/leaves/oak", TfcLootFixtures.OAK_LEAVES)),
                List.of());
        service = new LootService(() -> tables, items,
                stack -> items.idOf(stack).or(() -> Optional.of(stack.getType().getKey().toString())),
                (item, tag) -> item.equals("minecraft:shears") && tag.equals("c:tools/shear"), scheduler,
                hooks::get, random::get, Logger.getLogger("loot-test"));
        server.getPluginManager().registerEvents(new LootBreakListener(service, blocks,
                (block, tool) -> correctTool.get().test(block, tool)), plugin());
        world = server.addSimpleWorld("loot");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private Block place(int x, String id, Map<String, String> state) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.STONE);
        assertThat(blocks.place(block, BlockStateRef.of(id, state), false)).isTrue();
        return block;
    }

    private List<ItemStack> droppedItems() {
        List<ItemStack> out = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            if (e instanceof Item item) {
                out.add(item.getItemStack());
            }
        }
        out.sort(Comparator.comparing(s -> s.getType().name()));
        return out;
    }

    private BlockBreakEvent breakWith(Block block, ItemStack hand) {
        player.getInventory().setItemInMainHand(hand);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void breakingALooseRockDropsItsStateCount() {
        Block block = place(0, LOOSE, Map.of("count", "3"));
        BlockBreakEvent event = breakWith(block, null);
        assertThat(event.isDropItems()).as("the TFC table replaces the block's own drops").isFalse();
        assertThat(droppedItems()).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(Material.COBBLESTONE);
            assertThat(s.getAmount()).isEqualTo(3);
        });
        assertThat(scheduler.regionTasks).as("dropped back on the block's region").isEqualTo(1);
    }

    @Test
    void leavesReadTheToolFromTheMainHand() {
        // shears: leaves (and, every float being 0, the 1.3% sapling)
        breakWith(place(0, LEAVES, Map.of()), new ItemStack(Material.SHEARS));
        assertThat(droppedItems()).extracting(ItemStack::getType)
                .containsExactly(Material.OAK_LEAVES, Material.OAK_SAPLING);
    }

    @Test
    void leavesByHandDropOnlySaplingAndStick() {
        breakWith(place(0, LEAVES, Map.of()), null);
        assertThat(droppedItems()).extracting(ItemStack::getType).containsExactly(Material.OAK_SAPLING, Material.STICK);
    }

    @Test
    void silkTouchCountsAsAMatchingTool() {
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        pick.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        random.set(HIGH);
        breakWith(place(0, LEAVES, Map.of()), pick);
        assertThat(droppedItems()).extracting(ItemStack::getType).containsExactly(Material.OAK_LEAVES);
    }

    @Test
    void creativeBreaksAndUntabledBlocksAreLeftAlone() {
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent creative = breakWith(place(0, LOOSE, Map.of("count", "2")), null);
        assertThat(creative.isDropItems()).isTrue();
        player.setGameMode(GameMode.SURVIVAL);
        BlockBreakEvent other = breakWith(place(1, "tfp:other", Map.of()), null);
        assertThat(other.isDropItems()).isTrue();
        BlockBreakEvent vanilla = breakWith(world.getBlockAt(2, 64, 0), null);
        assertThat(vanilla.isDropItems()).isTrue();
        assertThat(droppedItems()).isEmpty();
    }

    @Test
    void explosionWithDecayRollsSurvivalAndDecay() {
        Block kept = place(0, LOOSE, Map.of("count", "3"));
        EntityExplodeEvent survive = new EntityExplodeEvent(player, kept.getLocation(), new ArrayList<>(List.of(kept)),
                0.25f, ExplosionResult.DESTROY_WITH_DECAY);
        server.getPluginManager().callEvent(survive);
        assertThat(droppedItems()).singleElement().satisfies(s -> assertThat(s.getAmount()).isEqualTo(3));

        random.set(HIGH);
        Block lost = place(1, LOOSE, Map.of("count", "3"));
        EntityExplodeEvent decay = new EntityExplodeEvent(player, lost.getLocation(), new ArrayList<>(List.of(lost)),
                0.25f, ExplosionResult.DESTROY_WITH_DECAY);
        server.getPluginManager().callEvent(decay);
        assertThat(droppedItems()).as("radius 4: a float near 1 fails survives_explosion").hasSize(1);
    }

    @Test
    void explosionWithoutDecayHasNoRadius() {
        random.set(HIGH);
        Block block = place(0, LOOSE, Map.of("count", "2"));
        EntityExplodeEvent keepAll = new EntityExplodeEvent(player, block.getLocation(), new ArrayList<>(List.of(block)),
                1.0f, ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(keepAll);
        assertThat(droppedItems()).singleElement().satisfies(s -> assertThat(s.getAmount()).isEqualTo(2));
        assertThat(LootService.radiusFromYield(0.25f)).isEqualTo(4f);
        assertThat(LootService.radiusFromYield(1f)).isNull();
    }

    @Test
    void destroyWithoutDecayPassesNoRadiusWhateverTheYield() {
        random.set(HIGH);
        Block block = place(0, LOOSE, Map.of("count", "2"));
        server.getPluginManager().callEvent(new EntityExplodeEvent(player, block.getLocation(),
                new ArrayList<>(List.of(block)), 0.25f, ExplosionResult.DESTROY));
        assertThat(droppedItems()).as("no radius: survives_explosion and explosion_decay pass")
                .singleElement().satisfies(s -> assertThat(s.getAmount()).isEqualTo(2));
    }

    @Test
    void explosionsThatKeepTheirBlocksDropNothing() {
        // Wind charges and breezes are TRIGGER_BLOCK: Paper lists nearby blocks with yield 1 but does not destroy them.
        for (ExplosionResult result : List.of(ExplosionResult.TRIGGER_BLOCK, ExplosionResult.KEEP)) {
            Block block = place(0, LOOSE, Map.of("count", "3"));
            server.getPluginManager().callEvent(new EntityExplodeEvent(player, block.getLocation(),
                    new ArrayList<>(List.of(block)), 1.0f, result));
            server.getPluginManager().callEvent(new BlockExplodeEvent(block, block.getState(),
                    new ArrayList<>(List.of(block)), 1.0f, result));
        }
        assertThat(droppedItems()).isEmpty();
        assertThat(scheduler.regionTasks).isZero();
    }

    @Test
    void blockExplosionsDropLikeEntityExplosions() {
        Block block = place(0, LOOSE, Map.of("count", "2"));
        server.getPluginManager().callEvent(new BlockExplodeEvent(block, block.getState(),
                new ArrayList<>(List.of(block)), 1.0f, ExplosionResult.DESTROY));
        assertThat(droppedItems()).singleElement().satisfies(s -> assertThat(s.getAmount()).isEqualTo(2));
    }

    @Test
    void anIncorrectToolDropsNothing() {
        // TFC: ServerPlayerGameMode skips playerDestroy (and so the loot) without hasCorrectToolForDrops.
        List<ItemStack> seen = new ArrayList<>();
        correctTool.set((block, tool) -> {
            seen.add(tool);
            return false;
        });
        BlockBreakEvent event = breakWith(place(0, LOOSE, Map.of("count", "3")), new ItemStack(Material.STICK));
        assertThat(event.isDropItems()).as("the block's own drops stay off too").isFalse();
        assertThat(seen).extracting(ItemStack::getType).containsExactly(Material.STICK);
        assertThat(droppedItems()).isEmpty();
        assertThat(scheduler.regionTasks).isZero();
    }

    @Test
    void aBreakAnotherPluginSetToDropNothingIsRespected() {
        Block block = place(0, LOOSE, Map.of("count", "3"));
        player.getInventory().setItemInMainHand(null);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setDropItems(false);
        server.getPluginManager().callEvent(event);
        assertThat(droppedItems()).isEmpty();
        assertThat(scheduler.regionTasks).isZero();
    }

    @Test
    void stackOpsReachTheHooksWithTheCapturedData() {
        AtomicInteger captures = new AtomicInteger();
        List<LootFunction.StackOp> seen = new ArrayList<>();
        hooks.set(new LootStackHooks() {
            @Override
            public Object capture(Block block) {
                captures.incrementAndGet();
                return "contents@" + block.getX();
            }

            @Override
            public ItemStack apply(ItemStack stack, LootFunction.StackOp op, Object captured) {
                seen.add(op);
                assertThat(captured).isEqualTo("contents@5");
                ItemStack out = stack.clone();
                out.setType(Material.MELON_SLICE);
                return out;
            }
        });
        LootTable melon = renamed("tfp:blocks/rotten_melon", TfcLootFixtures.ROTTEN_MELON);
        Block block = world.getBlockAt(5, 64, 0);
        List<ItemStack> stacks = service.dropBlock(block, melon,
                LootContext.builder().block("tfp:rotten_melon", Map.of()).build()).join();
        assertThat(captures.get()).isEqualTo(1);
        assertThat(seen).containsExactly(new LootFunction.StackOp.Rotten());
        assertThat(stacks).extracting(ItemStack::getType).containsExactly(Material.MELON_SLICE);
    }

    @Test
    void largeCountsSplitAtTheMaxStackSizeAndUnknownItemsAreSkipped() {
        List<ItemStack> stacks = service.stacks(List.of(LootDrop.of("minecraft:bone", 70), LootDrop.of("tfp:nope", 2),
                LootDrop.of("minecraft:diamond_sword", 2)), null);
        assertThat(stacks).extracting(ItemStack::getType, ItemStack::getAmount).containsExactly(
                org.assertj.core.groups.Tuple.tuple(Material.BONE, 64), org.assertj.core.groups.Tuple.tuple(Material.BONE, 6),
                org.assertj.core.groups.Tuple.tuple(Material.DIAMOND_SWORD, 1),
                org.assertj.core.groups.Tuple.tuple(Material.DIAMOND_SWORD, 1));
    }
}
