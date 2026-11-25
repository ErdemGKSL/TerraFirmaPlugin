/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldrules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.worldrules.FluidSourceListener.FluidSample;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tadpole;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-DATA-14: one test per world rule hook, through the real listeners. */
class WorldRulesMockBukkitTest extends MockServerTest {

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, m, a) -> switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> type.getSimpleName();
            default -> m.getReturnType() == boolean.class ? false : null;
        });
    }

    private static Scheduler capturing(List<Runnable> tasks) {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("region")) {
                        tasks.add((Runnable) args[args.length - 1]);
                    }
                    return method.getReturnType() == boolean.class ? true : null;
                });
    }

    private WorldMock world() {
        WorldMock w = server.addSimpleWorld("w");
        return w;
    }

    private static BlockFormEvent form(Block at, Material from, Material to) {
        at.setType(from);
        BlockState next = at.getState();
        next.setType(to);
        return new BlockFormEvent(at, next);
    }

    @Test
    void lavaAndWaterFormHardenedRock() {
        WorldMock w = world();
        BlockData gabbro = Material.TUFF.createBlockData(); // stands in for the CraftEngine block's server data
        BlockData rhyolite = Material.CALCITE.createBlockData();
        HardenedRockListener l = new HardenedRockListener(id -> switch (id) {
            case "tfp:rock/hardened/gabbro" -> Optional.of(gabbro);
            case "tfp:rock/hardened/rhyolite" -> Optional.of(rhyolite);
            default -> Optional.empty();
        });

        BlockFormEvent stone = form(w.getBlockAt(0, 64, 0), Material.WATER, Material.STONE);
        l.decide(stone);
        stone.getNewState().setType(Material.STONE); // the suppression listener at HIGH may rewrite in between
        l.apply(stone);
        assertThat(stone.getNewState().getType()).isEqualTo(Material.TUFF);

        BlockFormEvent cobble = form(w.getBlockAt(1, 64, 0), Material.LAVA, Material.COBBLESTONE);
        l.decide(cobble);
        cobble.getNewState().setType(Material.STONE); // suppressed to stone, still rhyolite as TFC
        l.apply(cobble);
        assertThat(cobble.getNewState().getType()).isEqualTo(Material.CALCITE);

        BlockFormEvent basalt = form(w.getBlockAt(2, 64, 0), Material.LAVA, Material.BASALT);
        l.decide(basalt);
        l.apply(basalt);
        assertThat(basalt.getNewState().getType()).isEqualTo(Material.BASALT); // CraftEngine block not loaded: vanilla

        BlockFormEvent obsidian = form(w.getBlockAt(3, 64, 0), Material.LAVA, Material.OBSIDIAN);
        l.decide(obsidian);
        l.apply(obsidian);
        assertThat(obsidian.getNewState().getType()).isEqualTo(Material.OBSIDIAN);

        BlockFormEvent snow = form(w.getBlockAt(4, 64, 0), Material.AIR, Material.STONE); // not a fluid interaction
        l.decide(snow);
        l.apply(snow);
        assertThat(snow.getNewState().getType()).isEqualTo(Material.STONE);

        // a stale decision of another event is never applied
        BlockFormEvent first = form(w.getBlockAt(5, 64, 0), Material.WATER, Material.STONE);
        BlockFormEvent second = form(w.getBlockAt(6, 64, 0), Material.WATER, Material.STONE);
        l.decide(first);
        l.apply(second);
        assertThat(second.getNewState().getType()).isEqualTo(Material.STONE);
    }

    /** MockBukkit's snow block data is no {@link Snow}: a snow layer data stub. */
    private static Snow snow(int layers) {
        int[] n = {layers};
        return (Snow) Proxy.newProxyInstance(Snow.class.getClassLoader(), new Class<?>[] {Snow.class}, (p, m, a) -> switch (m.getName()) {
            case "getLayers" -> n[0];
            case "setLayers" -> {
                n[0] = (Integer) a[0];
                yield null;
            }
            case "getMaximumLayers" -> 8;
            case "getMaterial" -> Material.SNOW;
            case "clone" -> snow(n[0]);
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> null;
        });
    }

    /** A block stub holding its data; {@code below} answers {@code getRelative(DOWN)}. */
    private static Block block(BlockData[] data, Block below) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class}, (p, m, a) -> switch (m.getName()) {
            case "getType" -> data[0] == null ? Material.AIR : data[0].getMaterial();
            case "getBlockData" -> data[0];
            case "setBlockData" -> {
                data[0] = (BlockData) a[0];
                yield null;
            }
            case "setType" -> {
                data[0] = null;
                yield null;
            }
            case "getRelative" -> below;
            case "getLocation" -> null;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
    }

    @Test
    void breakingSnowLeavesOneLayerLessOutOfCreative() {
        world();
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        List<Runnable> tasks = new ArrayList<>();
        SnowLayerListener l = new SnowLayerListener(capturing(tasks), b -> false, b -> false);
        BlockData[] data = {snow(4)};
        Block block = block(data, null);
        l.onBreak(new BlockBreakEvent(block, player));
        assertThat(tasks).hasSize(1);
        block.setType(Material.AIR); // vanilla's break
        tasks.get(0).run();
        assertThat(((Snow) block.getBlockData()).getLayers()).isEqualTo(3);

        tasks.clear();
        player.setGameMode(GameMode.CREATIVE);
        l.onBreak(new BlockBreakEvent(block, player));
        assertThat(tasks).isEmpty();

        player.setGameMode(GameMode.SURVIVAL);
        data[0] = snow(1); // one layer: a normal break
        l.onBreak(new BlockBreakEvent(block, player));
        assertThat(tasks).isEmpty();

        data[0] = snow(3); // something filled the hole first: kept
        l.onBreak(new BlockBreakEvent(block, player));
        data[0] = Material.STONE.createBlockData();
        tasks.get(0).run();
        assertThat(block.getType()).isEqualTo(Material.STONE);
    }

    @Test
    void tfcLeavesHoldOneSnowLayer() {
        world();
        PlayerMock player = server.addPlayer();
        Block leaves = block(new BlockData[] {Material.OAK_LEAVES.createBlockData()}, null);
        Block dirt = block(new BlockData[] {Material.DIRT.createBlockData()}, null);
        SnowLayerListener l = new SnowLayerListener(capturing(new ArrayList<>()), b -> false, b -> b == leaves);
        Block two = block(new BlockData[] {snow(2)}, leaves);
        BlockPlaceEvent onLeaves = new BlockPlaceEvent(two, null, leaves, new ItemStack(Material.SNOW), player, true,
                EquipmentSlot.HAND);
        l.onPlace(onLeaves);
        assertThat(onLeaves.isCancelled()).isTrue();

        Block one = block(new BlockData[] {snow(1)}, leaves);
        BlockPlaceEvent single = new BlockPlaceEvent(one, null, leaves, new ItemStack(Material.SNOW), player, true,
                EquipmentSlot.HAND);
        l.onPlace(single);
        assertThat(single.isCancelled()).isFalse();

        Block five = block(new BlockData[] {snow(5)}, dirt);
        BlockPlaceEvent onDirt = new BlockPlaceEvent(five, null, dirt, new ItemStack(Material.SNOW), player, true,
                EquipmentSlot.HAND);
        l.onPlace(onDirt);
        assertThat(onDirt.isCancelled()).isFalse();
    }

    @Test
    void tadpolesAreNotFedAndGrowIntoTfcFrogs() {
        world();
        PlayerMock player = server.addPlayer();
        Tadpole tadpole = stub(Tadpole.class);
        List<Tadpole> converted = new ArrayList<>();
        EntityRulesListener l = new EntityRulesListener(e -> false, t -> converted.add(t));
        player.getInventory().setItemInMainHand(new ItemStack(Material.SLIME_BALL));
        PlayerInteractEntityEvent feed = new PlayerInteractEntityEvent(player, tadpole, EquipmentSlot.HAND);
        l.onFeed(feed);
        assertThat(feed.isCancelled()).isTrue();
        player.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET)); // bucketing still works
        PlayerInteractEntityEvent bucket = new PlayerInteractEntityEvent(player, tadpole, EquipmentSlot.HAND);
        l.onFeed(bucket);
        assertThat(bucket.isCancelled()).isFalse();

        EntityTransformEvent grow = new EntityTransformEvent(tadpole, List.of(stub(Frog.class)),
                EntityTransformEvent.TransformReason.METAMORPHOSIS);
        l.onGrowUp(grow);
        assertThat(grow.isCancelled()).isTrue();
        assertThat(converted).containsExactly(tadpole);

        EntityRulesListener noFrog = new EntityRulesListener(e -> false, t -> false);
        EntityTransformEvent vanilla = new EntityTransformEvent(tadpole, List.of(stub(Frog.class)),
                EntityTransformEvent.TransformReason.METAMORPHOSIS);
        noFrog.onGrowUp(vanilla);
        assertThat(vanilla.isCancelled()).isFalse();
    }

    @Test
    void predatorsDoNotRideBoats() {
        world();
        Mob bear = stub(Mob.class);
        Mob cow = stub(Mob.class);
        EntityRulesListener l = new EntityRulesListener(e -> e == bear, t -> false);
        EntityMountEvent inBoat = new EntityMountEvent(bear, stub(Boat.class));
        l.onMount(inBoat);
        assertThat(inBoat.isCancelled()).isTrue();
        EntityMountEvent preyInBoat = new EntityMountEvent(cow, stub(Boat.class));
        l.onMount(preyInBoat);
        assertThat(preyInBoat.isCancelled()).isFalse();
        EntityMountEvent onMob = new EntityMountEvent(bear, stub(Entity.class));
        l.onMount(onMob);
        assertThat(onMob.isCancelled()).isFalse();
    }

    @Test
    void bambooGrowsOnlyWithLongGrowthChance() {
        WorldMock w = world();
        Block stalk = w.getBlockAt(0, 64, 0);
        stalk.setType(Material.BAMBOO);
        Block above = stalk.getRelative(BlockFace.UP);
        float[] roll = {0.5f};
        RandomGenerator random = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public float nextFloat() {
                return roll[0];
            }
        };
        BambooListener l = new BambooListener(() -> random);
        BlockState grown = above.getState();
        grown.setType(Material.BAMBOO);
        BlockSpreadEvent miss = new BlockSpreadEvent(above, stalk, grown);
        l.onSpread(miss);
        assertThat(miss.isCancelled()).isTrue();
        roll[0] = 0.01f;
        BlockSpreadEvent hit = new BlockSpreadEvent(above, stalk, grown);
        l.onSpread(hit);
        assertThat(hit.isCancelled()).isFalse();

        roll[0] = 0.9f;
        Block sapling = w.getBlockAt(3, 64, 0);
        sapling.setType(Material.BAMBOO_SAPLING);
        BlockSpreadEvent fromSapling = new BlockSpreadEvent(sapling.getRelative(BlockFace.UP), sapling, grown);
        l.onSpread(fromSapling);
        assertThat(fromSapling.isCancelled()).isFalse();
    }

    @Test
    void aqueductsAndSluiceFedWaterCreateNoSource() {
        WorldMock w = world();
        Map<String, FluidSample> fluids = new HashMap<>();
        FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:rock/aqueduct/granite", "tfp:wood/sluice/oak");
        List<Runnable> tasks = new ArrayList<>();
        FluidSourceListener l = new FluidSourceListener(capturing(tasks),
                b -> fluids.getOrDefault(b.getX() + "," + b.getY() + "," + b.getZ(), FluidSample.NONE), blocks::stateAt);
        FluidSample source = new FluidSample(true, 8, true);

        // flowing water between a plain source and a waterlogged aqueduct, on stone
        Block target = w.getBlockAt(0, 64, 0);
        w.getBlockAt(0, 63, 0).setType(Material.STONE);
        target.setBlockData(Material.WATER.createBlockData(d -> ((Levelled) d).setLevel(1)));
        fluids.put("1,64,0", source);
        fluids.put("-1,64,0", source);
        Block aqueduct = w.getBlockAt(-1, 64, 0);
        aqueduct.setType(Material.STONE_BRICKS); // the CraftEngine block's server block
        blocks.place(aqueduct, BlockStateRef.of("tfp:rock/aqueduct/granite"), false);
        Levelled toSource = (Levelled) Material.WATER.createBlockData();
        toSource.setLevel(0);
        FluidLevelChangeEvent change = new FluidLevelChangeEvent(target, toSource);
        l.onLevelChange(change);
        assertThat(change.isCancelled()).isFalse();
        assertThat(((Levelled) change.getNewData()).getLevel()).isEqualTo(1); // flowing, amount 7

        // the same with two plain sources: vanilla's source stands
        blocks.remove(aqueduct);
        aqueduct.setType(Material.WATER);
        FluidLevelChangeEvent plain = new FluidLevelChangeEvent(target, toSource.clone());
        l.onLevelChange(plain);
        assertThat(((Levelled) plain.getNewData()).getLevel()).isZero();

        // a sluice's lower half above and beside the west source, facing it, feeds it: no source spreads into the hole
        Block hole = w.getBlockAt(10, 64, 0);
        w.getBlockAt(10, 63, 0).setType(Material.STONE);
        fluids.put("11,64,0", source);
        fluids.put("9,64,0", source);
        blocks.place(w.getBlockAt(8, 65, 0),
                new BlockStateRef("tfp:wood/sluice/oak", new java.util.TreeMap<>(Map.of("upper", "false", "facing", "east"))),
                false);
        Block from = w.getBlockAt(11, 64, 0);
        from.setType(Material.WATER);
        BlockFromToEvent flow = new BlockFromToEvent(from, hole);
        l.onFlow(flow);
        assertThat(flow.isCancelled()).isTrue();
        assertThat(tasks).hasSize(1);
        tasks.get(0).run();
        assertThat(hole.getType()).isEqualTo(Material.WATER);
        assertThat(((Levelled) hole.getBlockData()).getLevel()).isEqualTo(1);
    }
}
