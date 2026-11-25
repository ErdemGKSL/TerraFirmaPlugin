/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bellows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bellows.BellowsData;
import dev.tfp.core.charcoalforge.BellowsRules;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-METAL-11 on MockBukkit with fakes: the 20 tick push cadence, air to the consumer in front, receiver rules, the crank. */
class BellowsDeviceMockBukkitTest extends MockServerTest {
    static final String FORGE = "tfp:charcoal_forge";
    static final String WALL = "tfp:other";

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

    static final class Effects implements BellowsEffects {
        final List<String> blows = new ArrayList<>();

        @Override
        public boolean owns(Block block) {
            return true;
        }

        @Override
        public void onRegion(Block block, Runnable task) {
            task.run();
        }

        @Override
        public void blow(Block bellows, String facing) {
            blows.add(facing);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(BellowsDevice.ID, FORGE, WALL);
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(10_000);
    private final BellowsConsumers consumers = new BellowsConsumers();
    private final List<Integer> air = new ArrayList<>();
    private boolean accepts = true;
    private DeviceService service;
    private BellowsDevice device;
    private Block bellows;
    private Block forge;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("bellows");
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
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, none, new FakeCustomItems(),
            Logger.getLogger("test"));
        consumers.register(FORGE, new BellowsConsumers.Consumer() {
            @Override
            public boolean canAcceptAir(Block block) {
                return accepts;
            }

            @Override
            public void intakeAir(Block block, int amount) {
                air.add(amount);
            }
        });
        device = new BellowsDevice(consumers, b -> blocks.stateAt(b).map(BlockStateRef::id), effects);
        service.register(device.kind());
        // bellows at (0,65,0) facing east: the forge is one block east and one below
        bellows = world.getBlockAt(0, 65, 0);
        blocks.place(bellows, BlockStateRef.of(BellowsDevice.ID).with("facing", "east"), true);
        store.data.put(FakeCustomBlocks.at(bellows), new DataMap());
        forge = world.getBlockAt(1, 64, 0);
        blocks.place(forge, BlockStateRef.of(FORGE), true);
        player = server.addPlayer();
        player.teleport(new Location(world, -3.5, 65, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private boolean click() {
        player.getInventory().setItem(EquipmentSlot.HAND, null);
        return service.use(bellows, player, EquipmentSlot.HAND, ItemStack.empty());
    }

    private long lastPushed() {
        return device.kind().codec().decode(store.read(bellows).orElseThrow()).state().lastPushed();
    }

    @Test
    void aPressPushesAirIntoTheForgeInFront() {
        assertThat(click()).isTrue();
        assertThat(air).containsExactly(BellowsRules.BELLOWS_AIR);
        assertThat(lastPushed()).isEqualTo(10_000L);
        assertThat(effects.blows).containsExactly("east");
    }

    @Test
    void aSecondPressWithinTwentyTicksDoesNothingAndTheNextWorks() {
        click();
        clock.addAndGet(19);
        assertThat(click()).as("passed on").isFalse();
        assertThat(air).hasSize(1);
        assertThat(lastPushed()).isEqualTo(10_000L);
        clock.addAndGet(1);
        assertThat(click()).isTrue();
        assertThat(air).hasSize(2);
        assertThat(lastPushed()).isEqualTo(10_020L);
        assertThat(effects.blows).hasSize(2);
    }

    @Test
    void aForgeThatRefusesAirBlocksThePushButNotTheSwing() {
        accepts = false;
        assertThat(click()).as("the arm swings").isTrue();
        assertThat(air).isEmpty();
        assertThat(effects.blows).isEmpty();
        assertThat(lastPushed()).isZero();
    }

    @Test
    void withoutAConsumerTheBellowsPushesIntoEmptySpace() {
        blocks.place(forge, BlockStateRef.of(WALL), true);
        assertThat(click()).isTrue();
        assertThat(air).isEmpty();
        assertThat(effects.blows).containsExactly("east");
        assertThat(lastPushed()).isEqualTo(10_000L);
    }

    @Test
    void theForgeMustBeInFrontOfTheBellows() {
        blocks.place(bellows, BlockStateRef.of(BellowsDevice.ID).with("facing", "west"), true);
        assertThat(click()).isTrue();
        assertThat(air).as("the forge is behind it").isEmpty();
    }

    @Test
    void aCrankedBellowsIsDrivenByTheCrankNotByHand() {
        device.crank(b -> BellowsRules.Crank.TURNING);
        assertThat(click()).isFalse();
        assertThat(air).isEmpty();
        device.crank(b -> BellowsRules.Crank.IDLE);
        assertThat(click()).as("a crank that does not turn fails the press").isTrue();
        assertThat(air).isEmpty();
        assertThat(lastPushed()).isZero();
    }

    @Test
    void theDataSurvivesAReload() {
        click();
        BellowsData d = device.kind().codec().decode(store.read(bellows).orElseThrow()).state();
        assertThat(d).isEqualTo(new BellowsData(10_000L, true));
    }
}
