/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.gui.BarrelFluidVisuals;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-38 on MockBukkit with TFC's generated barrel recipes: a barrel item on a rack lays the barrel on its side facing
 * the player, racked; a rack item racks a barrel on its side facing the player; sneaking with an empty hand on the rack's
 * top takes it back (elsewhere it seals); a broken racked barrel leaves the rack; a placement facing a side and the support
 * rule; a barrel on its side takes no rain and shows no surface; the tap pours 1 mB per tick into the barrel below,
 * lazily (one access after 100 ticks equals an access on every tick), only through air, not into a sealed barrel; a barrel
 * on its side goes when its support goes.
 */
class BarrelRackMockBukkitTest extends MockServerTest {
    static final String OAK = "tfp:wood/barrel/oak";
    static final String WATER = "minecraft:water";

    @TempDir
    Path dir;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK, BarrelRackListener.RACK_ID);
    private final BarrelTestWorld.MemoryStore store = new BarrelTestWorld.MemoryStore();
    private final BarrelTestWorld.TableCodec stacks = new BarrelTestWorld.TableCodec();
    private final BarrelTestWorld.PdcItems items = new BarrelTestWorld.PdcItems(
        Map.of(BarrelRackListener.RACK_ID, Material.OAK_FENCE, OAK, Material.BARREL));
    private final AtomicLong clock = new AtomicLong(10_000);
    private final AtomicBoolean supported = new AtomicBoolean(true);
    private final AtomicReference<Double> hit = new AtomicReference<>(0.5);
    private final List<String> drips = new ArrayList<>();
    private final List<ItemStack> spawned = new ArrayList<>();
    private final List<FluidAmount> surfaces = new ArrayList<>();
    /** The last surface fluid per barrel ({@link PaperBarrelEffects#marker}). */
    private final Map<String, FluidAmount> surfaceAt = new HashMap<>();
    /** The real surface display path (PAR-PROC-35's item display), so a test can look at the plane itself. */
    private PaperBarrelEffects displays;
    private final List<Block> collapsed = new ArrayList<>();
    private BarrelTestWorld data;
    private DeviceService service;
    private DeviceKind<BarrelData> kind;
    private BarrelDevice.Services barrel;
    private BarrelRackListener racks;
    private World world;
    private PlayerMock player;

    private final BarrelEffects effects = new BarrelEffects() {
        @Override
        public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
            state(block, wanted);
        }

        @Override
        public void recipe(Block block, String sound) {
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }

        @Override
        public void surface(Block block, FluidAmount fluid, int capacity) {
            surfaces.add(fluid);
            surfaceAt.put(PaperBarrelEffects.marker(block), fluid);
            displays.surface(block, fluid, capacity);
        }

        @Override
        public void racked(Block block, BlockStateRef wanted, boolean racked) {
            state(block, wanted);
        }

        @Override
        public void state(Block block, BlockStateRef wanted) {
            // PaperBarrelEffects' region task, inline
            blocks.place(block, PaperBarrelEffects.overlay(blocks.stateAt(block).orElseThrow(), wanted), false);
        }

        @Override
        public void drip(Block block) {
            drips.add(block.getX() + "," + block.getY() + "," + block.getZ());
        }

        @Override
        public void collapse(Block block, ItemStack rack) {
            collapsed.add(block);
            if (!rack.isEmpty()) {
                spawned.add(rack);
            }
        }
    };

    @BeforeEach
    void setUp() {
        data = new BarrelTestWorld(dir).load(clock::get);
        world = server.addSimpleWorld("racks");
        BarrelTestScheduler scheduler = new BarrelTestScheduler();
        displays = new PaperBarrelEffects(scheduler, blocks, () -> null);
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, data.items,
            Logger.getLogger("test"));
        BarrelRecipeBook book = new BarrelRecipeBook(data.recipes, data.tanks(), BarrelRules.DEFAULT_CAPACITY);
        BarrelRackTap[] tap = new BarrelRackTap[1];
        racks = new BarrelRackListener(blocks, items, service, scheduler, effects, () -> tap[0], b -> supported.get(),
            (p, b) -> hit.get());
        barrel = new BarrelDevice.Services(book, s -> true, s -> false, effects, b -> false, BarrelDevice.Screens.NONE, racks);
        kind = service.register(BarrelDevice.kind(barrel, stacks));
        tap[0] = new BarrelRackTap(service, kind, barrel, stacks, scheduler);
        service.hook(BarrelDevice.ID, new BarrelModule.ScreenSync(new dev.tfp.paper.module.ModuleServices(), scheduler, racks));
        player = server.addPlayer("Bot");
        // yaw 180: the player looks north
        player.teleport(new Location(world, 0.5, 65, 3.5, 180f, 0f));
    }

    private static BlockStateRef state(BlockFace facing, boolean rack) {
        return BarrelDevice.rackState(OAK, facing, rack).with(BarrelDevice.SEALED, "false");
    }

    private Block barrelAt(int x, int y, int z, BlockFace facing, boolean rack, int water) {
        Block b = world.getBlockAt(x, y, z);
        blocks.place(b, state(facing, rack), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        if (water > 0) {
            service.edit(b, kind, (r, now) -> r.withState(r.state().withFluid(new FluidAmount(WATER, water))));
        }
        return b;
    }

    private DeviceRecord<BarrelData> record(Block b) {
        DataMap m = store.read(b).orElseThrow();
        return m.size() == 0 ? kind.fresh() : kind.codec().decode(m);
    }

    private int water(Block b) {
        FluidAmount f = record(b).state().fluid();
        return f.isEmpty() ? 0 : f.amount();
    }

    private ItemStack item(String id) {
        return items.create(id, 1).orElseThrow();
    }

    @Test
    void aBarrelItemOnARackLaysTheBarrelOnItsSideFacingThePlayerRacked() {
        Block rack = world.getBlockAt(0, 64, 0);
        blocks.place(rack, BlockStateRef.of(BarrelRackListener.RACK_ID), true);
        ItemStack held = items.create(OAK, 2).orElseThrow();
        player.getInventory().setItemInMainHand(held);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, rack, BlockFace.UP,
            EquipmentSlot.HAND);
        racks.onInteract(e);
        assertThat(e.isCancelled()).as("CraftEngine never places the barrel next to the rack").isTrue();
        BlockStateRef placed = blocks.stateAt(rack).orElseThrow();
        assertThat(placed.id()).isEqualTo(OAK);
        // TFC: context.getHorizontalDirection(), the way the player looks
        assertThat(placed.properties()).containsEntry("facing", "north").containsEntry("rack", "true")
            .containsEntry("sealed", "false");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(racks.facing(rack)).isEqualTo(BlockFace.NORTH);
        assertThat(racks.racked(rack)).isTrue();
        // the off-hand event of the same click (Paper fires it after the cancelled main hand) never reaches the barrel
        PlayerInteractEvent off = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, rack, BlockFace.UP,
            EquipmentSlot.OFF_HAND);
        racks.onInteract(off);
        assertThat(off.isCancelled()).isTrue();
        player.setTicksLived(player.getTicksLived() + 1);
        PlayerInteractEvent next = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, rack, BlockFace.UP,
            EquipmentSlot.OFF_HAND);
        racks.onInteract(next);
        assertThat(next.isCancelled()).as("a click in a later tick is left alone").isFalse();
    }

    @Test
    void aRackWithoutSupportOrAnotherItemIsLeftAlone() {
        Block rack = world.getBlockAt(0, 64, 0);
        blocks.place(rack, BlockStateRef.of(BarrelRackListener.RACK_ID), true);
        supported.set(false);
        ItemStack held = item(OAK);
        racks.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, rack, BlockFace.UP,
            EquipmentSlot.HAND));
        assertThat(blocks.stateAt(rack).orElseThrow().id()).isEqualTo(BarrelRackListener.RACK_ID);
        supported.set(true);
        PlayerInteractEvent other = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STICK),
            rack, BlockFace.UP, EquipmentSlot.HAND);
        racks.onInteract(other);
        assertThat(other.isCancelled()).isFalse();
        assertThat(blocks.stateAt(rack).orElseThrow().id()).isEqualTo(BarrelRackListener.RACK_ID);
    }

    @Test
    void aRackItemRacksABarrelOnItsSideFacingThePlayerAndNotAnUprightOne() {
        Block side = barrelAt(0, 64, 0, BlockFace.EAST, false, 0);
        ItemStack rack = items.create(BarrelRackListener.RACK_ID, 3).orElseThrow();
        player.getInventory().setItemInMainHand(rack);
        assertThat(service.use(side, player, EquipmentSlot.HAND, rack)).isTrue();
        // TFC: FACING = player.getDirection().getOpposite()
        assertThat(blocks.stateAt(side).orElseThrow().properties()).containsEntry("rack", "true").containsEntry("facing", "south");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        // creative keeps the item
        Block other = barrelAt(2, 64, 0, BlockFace.WEST, false, 0);
        player.setGameMode(GameMode.CREATIVE);
        ItemStack held = player.getInventory().getItemInMainHand();
        service.use(other, player, EquipmentSlot.HAND, held);
        assertThat(racks.racked(other)).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        // an upright barrel is not racked (the click opens the screen instead)
        player.setGameMode(GameMode.SURVIVAL);
        Block upright = barrelAt(4, 64, 0, BlockFace.UP, false, 0);
        service.use(upright, player, EquipmentSlot.HAND, player.getInventory().getItemInMainHand());
        assertThat(racks.racked(upright)).isFalse();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    @Test
    void sneakingOnTheRacksTopTakesItBackElsewhereItSeals() {
        Block b = barrelAt(0, 64, 0, BlockFace.EAST, true, 0);
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(ItemStack.empty());
        hit.set(0.5);
        service.use(b, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(record(b).state().sealed()).as("a hit below the top seals").isTrue();
        assertThat(racks.racked(b)).isTrue();
        service.use(b, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(record(b).state().sealed()).isFalse();
        hit.set(0.95);
        service.use(b, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(racks.racked(b)).as("the top above 0.875 takes the rack").isFalse();
        assertThat(record(b).state().sealed()).isFalse();
        assertThat(racks.facing(b)).isEqualTo(BlockFace.EAST);
        assertThat(java.util.Arrays.stream(player.getInventory().getContents())
            .anyMatch(i -> i != null && items.idOf(i).filter(BarrelRackListener.RACK_ID::equals).isPresent()))
            .as("the rack item given back").isTrue();
        // with a block above, the top click seals as TFC
        Block r2 = barrelAt(2, 64, 0, BlockFace.EAST, true, 0);
        world.getBlockAt(2, 65, 0).setType(Material.STONE);
        service.use(r2, player, EquipmentSlot.HAND, ItemStack.empty());
        assertThat(racks.racked(r2)).isTrue();
        assertThat(record(r2).state().sealed()).isTrue();
    }

    @Test
    void breakingARackedBarrelLeavesTheRack() {
        Block b = barrelAt(0, 64, 0, BlockFace.NORTH, true, 0);
        // the event sees the barrel; the region task after it finds the place empty and puts the rack there
        racks.onBreak(new BlockBreakEvent(b, player));
        assertThat(blocks.stateAt(b).orElseThrow().id()).as("the task never replaces a block still there").isEqualTo(OAK);
        blocks.remove(b);
        b.setType(Material.AIR);
        racks.leaveRack(b);
        assertThat(blocks.stateAt(b).orElseThrow().id()).isEqualTo(BarrelRackListener.RACK_ID);
        // an unracked barrel leaves nothing
        Block unracked = barrelAt(2, 64, 0, BlockFace.NORTH, false, 0);
        blocks.remove(unracked);
        racks.onBreak(new BlockBreakEvent(unracked, player));
        assertThat(blocks.stateAt(unracked)).isEmpty();
    }

    @Test
    void thePlayersHorizontalDirectionIsVanillasFromYRot() {
        assertThat(BarrelRackListener.horizontal(0f)).isEqualTo(BlockFace.SOUTH);
        assertThat(BarrelRackListener.horizontal(90f)).isEqualTo(BlockFace.WEST);
        assertThat(BarrelRackListener.horizontal(180f)).isEqualTo(BlockFace.NORTH);
        assertThat(BarrelRackListener.horizontal(-90f)).isEqualTo(BlockFace.EAST);
        assertThat(BarrelRackListener.horizontal(44f)).isEqualTo(BlockFace.SOUTH);
        assertThat(BarrelRackListener.horizontal(46f)).isEqualTo(BlockFace.WEST);
    }

    @Test
    void aPlacementTakesTheClickedSideAndNeedsSupport() {
        assertThat(BarrelRackListener.clickedFacing(BlockFace.EAST)).isEqualTo(BlockFace.EAST);
        assertThat(BarrelRackListener.clickedFacing(BlockFace.DOWN)).as("down counts as up").isEqualTo(BlockFace.UP);
        assertThat(BarrelRackListener.clickedFacing(BlockFace.SELF)).isEqualTo(BlockFace.UP);
        Block wall = world.getBlockAt(0, 64, 0);
        wall.setType(Material.STONE);
        Block placed = world.getBlockAt(1, 64, 0);
        blocks.place(placed, BarrelDevice.state(OAK, false), true);
        BlockPlaceEvent e = new BlockPlaceEvent(placed, placed.getState(), wall, item(OAK), player, true, EquipmentSlot.HAND);
        racks.onPlace(e);
        assertThat(e.isCancelled()).isFalse();
        assertThat(blocks.stateAt(placed).orElseThrow().properties()).containsEntry("facing", "east")
            .containsEntry("sealed", "false");
        Block top = world.getBlockAt(0, 65, 0);
        blocks.place(top, BarrelDevice.state(OAK, false), true);
        racks.onPlace(new BlockPlaceEvent(top, top.getState(), wall, item(OAK), player, true, EquipmentSlot.HAND));
        assertThat(racks.facing(top)).isEqualTo(BlockFace.UP);
        supported.set(false);
        Block floating = world.getBlockAt(5, 70, 5);
        blocks.place(floating, BarrelDevice.state(OAK, false), true);
        BlockPlaceEvent refused = new BlockPlaceEvent(floating, floating.getState(), world.getBlockAt(5, 69, 5), item(OAK),
            player, true, EquipmentSlot.HAND);
        racks.onPlace(refused);
        assertThat(refused.isCancelled()).as("TFC: no sturdy face below, no placement").isTrue();
    }

    @Test
    void aBarrelOnItsSideTakesNoRainAndShowsNoSurface() {
        Block side = barrelAt(0, 64, 0, BlockFace.EAST, false, 0);
        Block upright = barrelAt(2, 64, 0, BlockFace.UP, false, 0);
        for (Block b : List.of(side, upright)) {
            service.edit(b, kind, (r, now) -> BarrelDevice.settle(r, now, true, b, barrel, stacks));
        }
        clock.addAndGet(400);
        for (Block b : List.of(side, upright)) {
            service.edit(b, kind, (r, now) -> BarrelDevice.settle(r, now, true, b, barrel, stacks));
        }
        assertThat(water(side)).isZero();
        assertThat(water(upright)).as("TFC: 1 mB per 4 ticks").isEqualTo(100);
        surfaces.clear();
        BarrelData before = record(side).state();
        BarrelDevice.surface(side, before, before.withFluid(new FluidAmount(WATER, 500)), barrel);
        assertThat(surfaces).containsExactly(FluidAmount.EMPTY);
    }

    /** A racked barrel at (x, 65, 0) facing east with {@code mB}, its tap over the upright barrel at (x + 1, 64, 0). */
    private Block[] pair(int x, int mB) {
        Block source = barrelAt(x, 65, 0, BlockFace.EAST, true, mB);
        world.getBlockAt(x + 1, 65, 0).setType(Material.AIR);
        Block target = barrelAt(x + 1, 64, 0, BlockFace.UP, false, 0);
        return new Block[] {source, target};
    }

    @Test
    void theTapPoursOneMbPerTickIntoTheBarrelBelowLazily() {
        Block[] lazy = pair(0, 500);
        Block[] stepped = pair(10, 500);
        racks.tap(lazy[1]);
        racks.tap(stepped[1]);
        for (int t = 0; t < 120; t++) {
            clock.incrementAndGet();
            racks.tap(t % 2 == 0 ? stepped[1] : stepped[0]); // an access of either barrel
        }
        racks.tap(stepped[1]);
        // one access of the target after 120 ticks
        racks.tap(lazy[1]);
        assertThat(water(lazy[1])).isEqualTo(120);
        assertThat(water(lazy[0])).isEqualTo(380);
        assertThat(water(stepped[1])).isEqualTo(water(lazy[1]));
        assertThat(water(stepped[0])).isEqualTo(water(lazy[0]));
        assertThat(drips).contains("0,65,0");
        // the target's surface follows the pour (TFC BarrelBlockEntityRenderer: any unsealed upright barrel with fluid)
        assertSurface(lazy[1], 120);
        assertSurface(stepped[1], 120);
        assertThat(PaperBarrelEffects.surfaces(lazy[0])).as("a barrel on its side shows no surface").isEmpty();
        // until the source is empty
        clock.addAndGet(1000);
        racks.tap(lazy[0]);
        assertThat(water(lazy[0])).isZero();
        assertThat(water(lazy[1])).isEqualTo(500);
        assertSurface(lazy[1], 500);
    }

    /** The target shows exactly one surface plane of {@code mB} water at TFC's height. */
    private void assertSurface(Block target, int mB) {
        assertThat(surfaceAt.get(PaperBarrelEffects.marker(target))).isEqualTo(new FluidAmount(WATER, mB));
        List<ItemDisplay> planes = PaperBarrelEffects.surfaces(target);
        assertThat(planes).as("one plane, never stacked").hasSize(1);
        float y = BarrelFluidVisuals.surface(mB, BarrelRules.DEFAULT_CAPACITY).y();
        assertThat(planes.getFirst().getTransformation().getTranslation().y()).isEqualTo(y);
        assertThat(planes.getFirst().getLocation().getY()).isEqualTo(target.getY());
    }

    @Test
    void aClickOnEitherBarrelSettlesTheTapFirst() {
        Block[] p = pair(0, 300);
        service.use(p[1], player, EquipmentSlot.HAND, new ItemStack(Material.STICK)); // starts the clock
        clock.addAndGet(50);
        service.use(p[1], player, EquipmentSlot.HAND, new ItemStack(Material.STICK));
        assertThat(water(p[1])).isEqualTo(50);
        clock.addAndGet(25);
        player.setSneaking(true);
        service.use(p[0], player, EquipmentSlot.HAND, ItemStack.empty()); // seals the source after settling
        assertThat(water(p[1])).isEqualTo(75);
        assertThat(record(p[0]).state().sealed()).isTrue();
        clock.addAndGet(100);
        racks.tap(p[1]);
        assertThat(water(p[1])).as("a sealed barrel does not pour").isEqualTo(75);
    }

    @Test
    void theTapPoursOnlyThroughAirAndNotIntoASealedOrSidewaysBarrel() {
        Block[] blocked = pair(0, 300);
        world.getBlockAt(1, 65, 0).setType(Material.STONE);
        racks.tap(blocked[0]);
        clock.addAndGet(50);
        racks.tap(blocked[0]);
        assertThat(water(blocked[1])).isZero();
        Block[] sealed = pair(10, 300);
        service.edit(sealed[1], kind, (r, now) -> r.withState(r.state().sealedAt(now, false)));
        racks.tap(sealed[0]);
        clock.addAndGet(50);
        racks.tap(sealed[0]);
        assertThat(water(sealed[1])).isZero();
        assertThat(water(sealed[0])).isEqualTo(300);
        Block source = barrelAt(20, 65, 0, BlockFace.EAST, true, 300);
        world.getBlockAt(21, 65, 0).setType(Material.AIR);
        Block side = barrelAt(21, 64, 0, BlockFace.NORTH, false, 0);
        racks.tap(source);
        clock.addAndGet(50);
        racks.tap(source);
        assertThat(water(side)).as("TFC: a barrel on its side takes no fluid from above").isZero();
    }

    @Test
    void aBarrelOnItsSideGoesWhenItsSupportGoesWithTheRackItem() {
        Block racked = barrelAt(0, 64, 0, BlockFace.EAST, true, 0);
        Block upright = barrelAt(2, 64, 0, BlockFace.UP, false, 0);
        supported.set(false);
        service.neighbourChanged(racked);
        service.neighbourChanged(upright);
        assertThat(collapsed).containsExactly(racked);
        assertThat(spawned).hasSize(1);
        assertThat(items.idOf(spawned.getFirst())).contains(BarrelRackListener.RACK_ID);
    }
}
