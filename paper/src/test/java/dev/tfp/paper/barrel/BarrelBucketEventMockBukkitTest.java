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
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-35 regression (the in-game run at 78a63b7: five water buckets on a placed barrel left the tank empty and water
 * in the world): a bucket right click on a barrel through the Bukkit event path, the way Paper delivers it. The block use
 * ({@code PlayerInteractEvent} RIGHT_CLICK_BLOCK) comes first; the client then sends the item use packet, which Paper turns
 * into a second {@code PlayerInteractEvent} when the held stack changed, else drops when the first was denied; an item use
 * that is not denied runs vanilla {@code BucketItem.use}, which fires {@code PlayerBucketEmptyEvent} at the barrel and places
 * water next to it. CraftEngine's {@code useOnBlock} (HIGHEST, ignoring cancelled events) is emulated by routing an
 * uncancelled first event to the device service.
 */
class BarrelBucketEventMockBukkitTest extends MockServerTest {
    static final String OAK = "tfp:wood/barrel/oak";

    @TempDir
    Path dir;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK);
    private final BarrelTestWorld.MemoryStore store = new BarrelTestWorld.MemoryStore();
    private final BarrelTestWorld.TableCodec stacks = new BarrelTestWorld.TableCodec();
    private final AtomicLong clock = new AtomicLong(10_000);
    private final AtomicBoolean failTransfer = new AtomicBoolean();
    private final AtomicInteger opened = new AtomicInteger();
    private DeviceService service;
    private DeviceKind<BarrelData> kind;
    private BarrelTestScheduler scheduler;
    private Block barrel;
    private PlayerMock player;

    private final BarrelEffects effects = new BarrelEffects() {
        @Override
        public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
        }

        @Override
        public void recipe(Block block, String sound) {
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
            if (failTransfer.get()) {
                throw new IllegalStateException("effects down");
            }
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }

        @Override
        public void surface(Block block, FluidAmount fluid, int capacity) {
        }
    };

    @BeforeEach
    void setUp() {
        BarrelTestWorld data = new BarrelTestWorld(dir).load(clock::get);
        World world = server.addSimpleWorld("barrel");
        scheduler = new BarrelTestScheduler();
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, data.items,
            Logger.getLogger("test"));
        BarrelItemTanks tanks = data.tanks();
        BarrelRecipeBook book = new BarrelRecipeBook(data.recipes, tanks, BarrelRules.DEFAULT_CAPACITY);
        BarrelDevice.Services barrelServices = new BarrelDevice.Services(book, s -> true, s -> false, effects,
            BarrelModule.rain(new ModuleServices(), Set.of()), (p, block, blockId) -> opened.incrementAndGet());
        kind = service.register(BarrelDevice.kind(barrelServices, stacks));
        server.getPluginManager().registerEvents(new BarrelModule.BucketGuard(service, blocks, tanks, scheduler,
            Logger.getLogger("test")), plugin);

        barrel = world.getBlockAt(0, 64, 0);
        blocks.place(barrel, BarrelDevice.state(OAK, false), true);
        store.data.put(FakeCustomBlocks.at(barrel), new DataMap());
        player = server.addPlayer("Bot");
        player.setTicksLived(100);
    }

    private DeviceRecord<BarrelData> record() {
        DataMap m = store.read(barrel).orElseThrow();
        return m.size() == 0 ? kind.fresh() : kind.codec().decode(m);
    }

    private Block above() {
        return barrel.getRelative(BlockFace.UP);
    }

    /** One client right click with {@code held} in the main hand, on the barrel's top, in a new player tick. */
    private void rightClick(ItemStack held) {
        player.setTicksLived(player.getTicksLived() + 1);
        player.getInventory().setItemInMainHand(held);
        ItemStack before = held.clone();
        PlayerInteractEvent use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, barrel, BlockFace.UP,
            EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        if (use.useInteractedBlock() != Event.Result.DENY) {
            // CraftEngine's useOnBlock (HIGHEST, ignoreCancelled)
            if (service.use(barrel, player, EquipmentSlot.HAND, held)) {
                use.setCancelled(true);
            }
        }
        // the client's item use packet (ServerGamePacketListenerImpl.handleUseItem)
        ItemStack now = player.getInventory().getItemInMainHand();
        boolean denied;
        if (now.isSimilar(before) && now.getAmount() == before.getAmount()) {
            denied = use.useItemInHand() == Event.Result.DENY;
        } else {
            PlayerInteractEvent again = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, now, barrel,
                BlockFace.UP, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(again);
            denied = again.useItemInHand() == Event.Result.DENY;
        }
        if (!denied) {
            vanillaBucketUse(now);
        }
    }

    /** Vanilla {@code BucketItem.use} aimed at the barrel's top: empties a water bucket above it, fills nothing. */
    private void vanillaBucketUse(ItemStack now) {
        if (now.getType() == Material.WATER_BUCKET) {
            PlayerBucketEmptyEvent e = new PlayerBucketEmptyEvent(player, above(), barrel, BlockFace.UP,
                Material.WATER_BUCKET, now, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(e);
            if (!e.isCancelled()) {
                above().setType(Material.WATER);
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            }
        }
    }

    @Test
    void aWaterBucketFillsTheBarrelAndReturnsAnEmptyBucketWithNoWorldWater() {
        rightClick(new ItemStack(Material.WATER_BUCKET));
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 1000));
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);
        assertThat(above().getType()).isNotEqualTo(Material.WATER);
        assertThat(opened).as("a transfer opens no screen").hasValue(0);
    }

    @Test
    void fiveBucketsGiveFiveThousandMillibuckets() {
        for (int i = 0; i < 5; i++) {
            rightClick(new ItemStack(Material.WATER_BUCKET));
            assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);
        }
        assertThat(record().state().fluid()).isEqualTo(new FluidAmount("minecraft:water", 5000));
        assertThat(above().getType()).isNotEqualTo(Material.WATER);
    }

    @Test
    void anEmptyBucketFilledFromTheBarrelIsNotEmptiedIntoTheWorld() {
        // the in-game failure: the item use packet after the hand changed to a water bucket placed it next to the barrel
        rightClick(new ItemStack(Material.WATER_BUCKET));
        rightClick(new ItemStack(Material.BUCKET));
        assertThat(record().state().fluid().isEmpty()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.WATER_BUCKET);
        assertThat(above().getType()).isNotEqualTo(Material.WATER);
    }

    @Test
    void anEmptyBucketOnAnEmptyBarrelOpensTheScreenAndTouchesNoWorld() {
        rightClick(new ItemStack(Material.BUCKET));
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);
        assertThat(opened).hasValue(1);
        assertThat(above().getType()).isNotEqualTo(Material.WATER);
    }

    @Test
    void aFailingDeviceCallStillKeepsTheWaterOutOfTheWorld() {
        failTransfer.set(true);
        rightClick(new ItemStack(Material.WATER_BUCKET));
        assertThat(record().state().fluid().isEmpty()).as("the failed click wrote nothing").isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.WATER_BUCKET);
        assertThat(above().getType()).isNotEqualTo(Material.WATER);
    }

    @Test
    void theOffHandEventOfTheSameClickIsCancelled() {
        rightClick(new ItemStack(Material.WATER_BUCKET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.WATER_BUCKET));
        PlayerInteractEvent off = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            player.getInventory().getItemInOffHand(), barrel, BlockFace.UP, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(off);
        assertThat(off.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(record().state().fluid().amount()).as("one click, one transfer").isEqualTo(1000);
    }

    @Test
    void aBucketEventAtABarrelIsRefusedButSneakingLeavesTheBucketToTheWorld() {
        PlayerBucketEmptyEvent empty = new PlayerBucketEmptyEvent(player, above(), barrel, BlockFace.UP,
            Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(empty);
        assertThat(empty.isCancelled()).isTrue();
        PlayerBucketFillEvent fill = new PlayerBucketFillEvent(player, above(), barrel, BlockFace.UP, Material.BUCKET,
            new ItemStack(Material.BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(fill);
        assertThat(fill.isCancelled()).isTrue();

        // TFC: sneaking with an item in hand skips the block (isSecondaryUseActive), so the bucket acts on the world
        player.setSneaking(true);
        player.setTicksLived(player.getTicksLived() + 1);
        PlayerInteractEvent use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.WATER_BUCKET), barrel, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        assertThat(use.useItemInHand()).isNotEqualTo(Event.Result.DENY);
        PlayerBucketEmptyEvent sneaking = new PlayerBucketEmptyEvent(player, above(), barrel, BlockFace.UP,
            Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(sneaking);
        assertThat(sneaking.isCancelled()).isFalse();
    }

    @Test
    void aBucketOnAnotherBlockIsLeftAlone() {
        Block stone = barrel.getRelative(BlockFace.EAST);
        stone.setType(Material.STONE);
        PlayerInteractEvent use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.WATER_BUCKET), stone, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        assertThat(use.useItemInHand()).isNotEqualTo(Event.Result.DENY);
        assertThat(record().state().fluid().isEmpty()).isTrue();
    }

    @Test
    void forcedRainAtTheBarrelRainsWhereTheSkyReaches() {
        Set<BarrelScreenLink.Key> forced = ConcurrentHashMap.newKeySet();
        BarrelDevice.Rain rain = BarrelModule.rain(new ModuleServices(), forced);
        assertThat(rain.rainingAbove(barrel)).as("no precipitation service: dry").isFalse();
        forced.add(BarrelScreenLink.Key.of(barrel));
        assertThat(rain.rainingAbove(barrel)).isTrue();
        above().getRelative(BlockFace.UP, 3).setType(Material.STONE);
        assertThat(rain.rainingAbove(barrel)).as("a roof keeps the forced rain out (vanilla's gates)").isFalse();
    }
}
