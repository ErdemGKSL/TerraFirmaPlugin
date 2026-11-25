/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bell;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.pump.BlockWriter;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-18 on MockBukkit with fakes: a right click rings the bell at its metal's pitch (lower with a hammer; a sneaking
 * player with a block places instead), the placement writes the attachment and facing, other blocks are left alone.
 * Stand-in: a blaze rod is the hammer.
 */
class BellDevicesMockBukkitTest extends MockServerTest {
    record Ring(String block, float pitch, Entity by) {
    }

    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:brass_bell", "tfp:bronze_bell", "tfp:other");
    private final MemoryStore store = new MemoryStore();
    private final List<Ring> rings = new ArrayList<>();
    private DeviceService service;
    private BellDevices bells;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("bell");
        StackCodec none = new StackCodec() {
            @Override
            public ItemBlob encode(ItemStack stack) {
                return ItemBlob.EMPTY;
            }

            @Override
            public ItemStack decode(ItemBlob blob) {
                return ItemStack.empty();
            }
        };
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 0L, none, new FakeCustomItems(),
            Logger.getLogger("test"));
        Scheduler inline = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                if (method.getName().equals("isOwner")) {
                    return true;
                }
                if (method.getName().equals("region")) {
                    ((Runnable) args[args.length - 1]).run();
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        bells = new BellDevices(inline, blocks, (block, next) -> blocks.place(block, next, false),
            (bell, by, pitch) -> rings.add(new Ring(blocks.stateAt(bell).orElseThrow().id(), pitch, by)),
            stack -> stack.getType() == Material.BLAZE_ROD,
            (block, d) -> block.getRelative(d.dx(), d.dy(), d.dz()).getType() == Material.STONE); // MockBukkit has no support shapes
        bells.kinds().forEach(service::register);
        player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 65, 5.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private Block bell(String id, int x) {
        Block b = world.getBlockAt(x, 65, 0);
        blocks.place(b, BlockStateRef.of(id).with("attachment", "floor").with("facing", "north"), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        return b;
    }

    private boolean click(Block b, ItemStack held) {
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(b, player, EquipmentSlot.HAND, held);
    }

    @Test
    void bothKindsAreRegisteredForTheirBlocks() {
        assertThat(bells.kinds()).extracting(k -> k.id()).containsExactly("tfp:brass_bell", "tfp:bronze_bell");
    }

    @Test
    void aClickRingsAtTheMetalsPitch() {
        assertThat(click(bell("tfp:brass_bell", 0), ItemStack.empty())).as("brass").isTrue();
        assertThat(click(bell("tfp:bronze_bell", 2), ItemStack.empty())).as("bronze").isTrue();
        assertThat(rings).extracting(Ring::block).containsExactly("tfp:brass_bell", "tfp:bronze_bell");
        assertThat(rings).extracting(Ring::pitch).containsExactly(0.6f, 0.8f);
        assertThat(rings).allSatisfy(r -> assertThat(r.by()).isSameAs(player));
    }

    @Test
    void aHammerInTheMainHandRingsAHardTenthLower() {
        click(bell("tfp:brass_bell", 0), new ItemStack(Material.BLAZE_ROD));
        click(bell("tfp:bronze_bell", 2), new ItemStack(Material.BLAZE_ROD));
        assertThat(rings).extracting(Ring::pitch).containsExactly(0.6f - 0.1f, 0.8f - 0.1f);
    }

    @Test
    void ringingKeepsTheBlockState() {
        Block b = bell("tfp:brass_bell", 0);
        click(b, ItemStack.empty());
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("attachment", "floor").containsEntry("facing", "north");
    }

    @Test
    void placementWritesTheAttachmentAndFacing() {
        Block b = bell("tfp:brass_bell", 0);
        bells.placed(b, Direction.DOWN, Direction.EAST);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("attachment", "ceiling").containsEntry("facing", "east");
        bells.placed(b, Direction.EAST, Direction.NORTH);
        // no sturdy neighbours around: a single wall, its arm toward the wall behind the clicked face
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("attachment", "single_wall").containsEntry("facing", "west");
    }

    @Test
    void placementBetweenTwoFullBlocksIsADoubleWall() {
        Block b = bell("tfp:bronze_bell", 4);
        world.getBlockAt(3, 65, 0).setType(Material.STONE);
        world.getBlockAt(5, 65, 0).setType(Material.STONE);
        bells.placed(b, Direction.EAST, Direction.NORTH);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("attachment", "double_wall").containsEntry("facing", "west");
    }

    @Test
    void anotherBlockIsNotTouched() {
        Block b = world.getBlockAt(8, 65, 0);
        blocks.place(b, BlockStateRef.of("tfp:other"), true);
        bells.placed(b, Direction.DOWN, Direction.EAST);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).isEmpty();
        assertThat(rings).isEmpty();
    }

    @Test
    void theClickedFaceIsTheSideThePlacedBlockLiesOn() {
        Block against = world.getBlockAt(0, 65, 0);
        assertThat(BellDevices.clickedFace(world.getBlockAt(0, 66, 0), against)).isEqualTo(Direction.UP);
        assertThat(BellDevices.clickedFace(world.getBlockAt(0, 64, 0), against)).isEqualTo(Direction.DOWN);
        assertThat(BellDevices.clickedFace(world.getBlockAt(1, 65, 0), against)).isEqualTo(Direction.EAST);
        assertThat(BellDevices.clickedFace(against, against)).as("replaced the block itself").isEqualTo(Direction.UP);
        assertThat(BellDevices.horizontal(0f)).isEqualTo(Direction.SOUTH);
        assertThat(BellDevices.horizontal(90f)).isEqualTo(Direction.WEST);
        assertThat(BellDevices.horizontal(180f)).isEqualTo(Direction.NORTH);
        assertThat(BellDevices.horizontal(-90f)).isEqualTo(Direction.EAST);
    }
}
