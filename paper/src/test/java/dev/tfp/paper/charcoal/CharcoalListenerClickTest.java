/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.charcoal.CharcoalPit;
import dev.tfp.core.charcoal.CharcoalSpread;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-40: one sneak click with charcoal or a log is one use. The vanilla client follows a use-item-on the server
 * answered without a client change with a use-item packet; CraftBukkit ray-traces again, hits the pile just placed and
 * fires a second {@code RIGHT_CLICK_BLOCK} in the same tick, and the client then tries the off hand too. These tests fire both events of one click
 * through {@link CharcoalListener#onInteract} and expect one layer (one log) and one item used, while a click in the next
 * tick still adds one.
 */
class CharcoalListenerClickTest extends MockServerTest {
    static final Set<String> DEVICES = Set.of(CharcoalPitDevice.LOG_PILE, CharcoalPitDevice.BURNING);

    /** The world writes; deferred ones queue until {@link #flush} (region tasks run after the callback). */
    final class Effects implements CharcoalEffects {
        final List<Runnable> queue = new ArrayList<>();

        void flush() {
            for (int guard = 0; !queue.isEmpty() && guard < 1000; guard++) {
                queue.remove(0).run();
            }
        }

        boolean is(Block b, String id) {
            return blocks.stateAt(b).map(s -> s.id().equals(id)).orElse(false);
        }

        void replace(Block b, BlockStateRef state) {
            Optional<BlockStateRef> old = blocks.stateAt(b);
            if (state == null) {
                blocks.remove(b);
            } else {
                blocks.place(b, state, true);
            }
            if (old.isPresent() && DEVICES.contains(old.get().id())) {
                DataMap data = store.data.remove(FakeCustomBlocks.at(b));
                service.removed(b.getWorld(), b.getX(), b.getY(), b.getZ(), old.get().id(), data == null ? new DataMap() : data);
            }
            if (state != null && DEVICES.contains(state.id())) {
                store.data.put(FakeCustomBlocks.at(b), new DataMap());
            }
        }

        @Override
        public void show(Block block, BlockStateRef state) {
            queue.add(() -> {
                if (is(block, state.id())) {
                    blocks.place(block, state, false);
                }
            });
        }

        @Override
        public void place(Block block, BlockStateRef state, String replaceId, Runnable then) {
            queue.add(() -> {
                boolean free = replaceId == null ? block.getType().isAir() && blocks.stateAt(block).isEmpty()
                    : is(block, replaceId);
                if (free) {
                    replace(block, state);
                    if (then != null) {
                        then.run();
                    }
                }
            });
        }

        @Override
        public void air(Block block, String expectedId) {
            queue.add(() -> {
                if (is(block, expectedId)) {
                    replace(block, null);
                }
            });
        }

        @Override
        public void fire(Block block, String replaceId) {
        }

        @Override
        public void later(Block block, long ticks, Runnable task) {
        }

        @Override
        public void breakBlock(Block block) {
        }

        @Override
        public boolean placeNow(Block block, BlockStateRef state) {
            replace(block, state);
            return true;
        }

        @Override
        public void setNow(Block block, BlockStateRef state) {
            blocks.place(block, state, true);
        }

        @Override
        public void give(Player player, ItemStack stack) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
        }

        @Override
        public void sound(Block block, Sound sound) {
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(CharcoalPitDevice.LOG_PILE, CharcoalPitDevice.BURNING,
        CharcoalPitDevice.CHARCOAL_PILE);
    private final CharcoalPitMockBukkitTest.MemoryStore store = new CharcoalPitMockBukkitTest.MemoryStore();
    private final CharcoalPitMockBukkitTest.TableCodec codec = new CharcoalPitMockBukkitTest.TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(10_000);
    private DeviceService service;
    private CharcoalListener listener;
    private Block floor;
    private Block pile;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("clicks");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, new FakeCustomItems(),
            Logger.getLogger("test"));
        CharcoalPitDevice.Services services = new CharcoalPitDevice.Services(
            s -> s.getType() == Material.OAK_LOG,
            blocks::stateAt, blocks::exists, b -> b.getType() == Material.STONE,
            b -> b.getType() == Material.FIRE, (b, f) -> CharcoalSpread.Cover.AIR,
            b -> service.inspect(b).flatMap(DeviceService.Inspection::record),
            () -> (RandomGenerator) () -> 0L, CharcoalPit.DEFAULT_CHARCOAL_TICKS, effects);
        CharcoalPitDevice device = new CharcoalPitDevice(services, codec, new PitClock(), service::neighbourChanged);
        service.register(device.logPileKind());
        service.register(device.burningKind());
        listener = new CharcoalListener(device, services, b -> true, id -> service.kinds().find(id).isPresent());
        floor = world.getBlockAt(0, 63, 0);
        floor.setType(Material.STONE);
        pile = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5, 90f, 0f));
        player.setGameMode(GameMode.SURVIVAL);
        player.setSneaking(true);
        player.setTicksLived(100);
    }

    private void hold(Material m, int n) {
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(m, n));
    }

    private int held() {
        ItemStack s = player.getInventory().getItem(EquipmentSlot.HAND);
        return s == null || s.getType().isAir() ? 0 : s.getAmount();
    }

    private PlayerInteractEvent click(Block clicked, Action action) {
        ItemStack item = player.getInventory().getItem(EquipmentSlot.HAND);
        PlayerInteractEvent e = new PlayerInteractEvent(player, action, item, clicked, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(e);
        return e;
    }

    /** One client click: the use-item-on event on {@code clicked}, then CraftBukkit's re-fired event on the new pile. */
    private PlayerInteractEvent oneClick(Block clicked) {
        click(clicked, Action.RIGHT_CLICK_BLOCK);
        effects.flush();
        PlayerInteractEvent second = click(pile, Action.RIGHT_CLICK_BLOCK);
        effects.flush();
        return second;
    }

    private void nextTick() {
        player.setTicksLived(player.getTicksLived() + 1);
        clock.addAndGet(10); // never a log pile's quick double click
    }

    private int logs(Block b) {
        DeviceRecord<?> r = service.inspect(b).flatMap(DeviceService.Inspection::record).orElseThrow();
        int n = 0;
        for (ItemBlob s : r.slots()) {
            if (!s.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    @Test
    void oneCharcoalClickOnTheFloorMakesOneLayerAndUsesOneCharcoal() {
        hold(Material.CHARCOAL, 16);
        PlayerInteractEvent second = oneClick(floor);
        assertThat(blocks.stateAt(pile)).contains(CharcoalPitDevice.charcoalPile(1));
        assertThat(held()).isEqualTo(15);
        assertThat(second.useInteractedBlock()).as("the re-fired event is denied").isEqualTo(Event.Result.DENY);
        assertThat(second.useItemInHand()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void eachLaterCharcoalClickOnThePileAddsOneLayerUpToEight() {
        hold(Material.CHARCOAL, 16);
        oneClick(floor);
        for (int layers = 2; layers <= 8; layers++) {
            nextTick();
            oneClick(pile);
            assertThat(blocks.stateAt(pile)).as("click " + layers).contains(CharcoalPitDevice.charcoalPile(layers));
            assertThat(held()).isEqualTo(16 - layers);
        }
        // a ninth click starts a pile above, once
        nextTick();
        click(pile, Action.RIGHT_CLICK_BLOCK);
        click(pile.getRelative(BlockFace.UP), Action.RIGHT_CLICK_BLOCK);
        assertThat(blocks.stateAt(pile.getRelative(BlockFace.UP))).contains(CharcoalPitDevice.charcoalPile(1));
        assertThat(held()).isEqualTo(7);
    }

    @Test
    void theFollowingRightClickAirOfTheSameClickIsDeniedToo() {
        hold(Material.CHARCOAL, 4);
        click(floor, Action.RIGHT_CLICK_BLOCK);
        PlayerInteractEvent air = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            player.getInventory().getItem(EquipmentSlot.HAND), null, BlockFace.SELF, EquipmentSlot.HAND);
        listener.onInteract(air);
        assertThat(air.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(held()).isEqualTo(3);
    }

    /**
     * TFC's charcoal handler returns a consumed result, so its client never tries the off hand after a successful main-hand
     * use. Our vanilla client saw no change and sends the off-hand use-item-on and use-item in the same tick; they hit the
     * new pile and must be denied too.
     */
    @Test
    void theOffHandInTheSameTickIsPartOfTheSameClick() {
        hold(Material.CHARCOAL, 4);
        player.getInventory().setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.CHARCOAL, 4));
        oneClick(floor);
        PlayerInteractEvent off = offHand(pile, Action.RIGHT_CLICK_BLOCK);
        PlayerInteractEvent offAir = offHand(null, Action.RIGHT_CLICK_AIR);
        assertThat(blocks.stateAt(pile)).contains(CharcoalPitDevice.charcoalPile(1));
        assertThat(held()).isEqualTo(3);
        assertThat(player.getInventory().getItem(EquipmentSlot.OFF_HAND).getAmount()).isEqualTo(4);
        assertThat(off.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        assertThat(off.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(offAir.useItemInHand()).isEqualTo(Event.Result.DENY);
    }

    /** A log in both hands: one click, one log, also on an existing pile. */
    @Test
    void aLogInBothHandsUsesOneLogPerClick() {
        hold(Material.OAK_LOG, 8);
        player.getInventory().setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.OAK_LOG, 8));
        for (int n = 1; n <= 2; n++) {
            oneClick(n == 1 ? floor : pile);
            offHand(pile, Action.RIGHT_CLICK_BLOCK);
            assertThat(logs(pile)).as("click " + n).isEqualTo(n);
            assertThat(held()).isEqualTo(8 - n);
            assertThat(player.getInventory().getItem(EquipmentSlot.OFF_HAND).getAmount()).isEqualTo(8);
            nextTick();
        }
    }

    /** An off-hand click in a later tick, with the main hand empty, is its own click. */
    @Test
    void anOffHandClickInTheNextTickAddsALayer() {
        hold(Material.CHARCOAL, 4);
        oneClick(floor);
        nextTick();
        player.getInventory().setItem(EquipmentSlot.HAND, null);
        player.getInventory().setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.CHARCOAL, 4));
        offHand(pile, Action.RIGHT_CLICK_BLOCK);
        assertThat(blocks.stateAt(pile)).contains(CharcoalPitDevice.charcoalPile(2));
        assertThat(player.getInventory().getItem(EquipmentSlot.OFF_HAND).getAmount()).isEqualTo(3);
    }

    private PlayerInteractEvent offHand(Block clicked, Action action) {
        PlayerInteractEvent e = new PlayerInteractEvent(player, action, player.getInventory().getItem(EquipmentSlot.OFF_HAND),
            clicked, clicked == null ? BlockFace.SELF : BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.onInteract(e);
        effects.flush();
        return e;
    }

    @Test
    void oneLogClickOnTheFloorAndOneOnThePileUseOneLogEach() {
        hold(Material.OAK_LOG, 64);
        PlayerInteractEvent second = oneClick(floor);
        assertThat(blocks.stateAt(pile).map(BlockStateRef::id)).contains(CharcoalPitDevice.LOG_PILE);
        assertThat(logs(pile)).isEqualTo(1);
        assertThat(held()).isEqualTo(63);
        assertThat(second.useInteractedBlock()).isEqualTo(Event.Result.DENY);

        nextTick();
        oneClick(pile);
        assertThat(logs(pile)).isEqualTo(2);
        assertThat(held()).isEqualTo(62);
    }

    @Test
    void aRespawnClearsTheMemorySoTheFirstClickAtTheSameTickCountWorks() {
        hold(Material.CHARCOAL, 4);
        click(floor, Action.RIGHT_CLICK_BLOCK);
        listener.onRespawn(new PlayerRespawnEvent(player, player.getLocation(), false, false, false,
            PlayerRespawnEvent.RespawnReason.DEATH));
        click(pile, Action.RIGHT_CLICK_BLOCK); // the new entity happens to be at the same tick count
        assertThat(blocks.stateAt(pile)).contains(CharcoalPitDevice.charcoalPile(2));
        assertThat(held()).isEqualTo(2);
    }

    @Test
    void adventureAndSpectatorClicksAreNeitherHandledNorRemembered() {
        for (GameMode mode : new GameMode[] {GameMode.ADVENTURE, GameMode.SPECTATOR}) {
            player.setGameMode(mode);
            hold(Material.CHARCOAL, 4);
            PlayerInteractEvent first = click(floor, Action.RIGHT_CLICK_BLOCK);
            PlayerInteractEvent second = click(floor, Action.RIGHT_CLICK_BLOCK);
            assertThat(blocks.stateAt(pile)).as(mode.name()).isEmpty();
            assertThat(held()).isEqualTo(4);
            assertThat(first.useItemInHand()).isNotEqualTo(Event.Result.DENY);
            assertThat(second.useItemInHand()).isNotEqualTo(Event.Result.DENY);
        }
    }

    @Test
    void creativeKeepsTheItemAndStillAddsOneLayerPerClick() {
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.CHARCOAL, 4);
        oneClick(floor);
        assertThat(blocks.stateAt(pile)).contains(CharcoalPitDevice.charcoalPile(1));
        nextTick();
        oneClick(pile);
        assertThat(blocks.stateAt(pile)).contains(CharcoalPitDevice.charcoalPile(2));
        assertThat(held()).isEqualTo(4);
    }
}
