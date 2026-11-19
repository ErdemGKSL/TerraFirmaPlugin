/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit; // PAR-HEAT-30: in world/ on disk, but it reuses this package's test fixtures

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.firepit.world.FirepitWorldListener;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
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
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-HEAT-30: dropped fuel is gathered into the fuel slot; a lit firepit hurts survival players only. */
class FirepitWorldRulesMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(FirepitDevice.ID);
    private final FirepitMockBukkitTest.MemoryStore store = new FirepitMockBukkitTest.MemoryStore();
    private final FirepitMockBukkitTest.TableCodec codec = new FirepitMockBukkitTest.TableCodec();
    private final FirepitMockBukkitTest.Effects effects = new FirepitMockBukkitTest.Effects();
    private final AtomicLong clock = new AtomicLong(FirepitMockBukkitTest.START);
    private DeviceService service;
    private FirepitDevice device;
    private FirepitWorldListener listener;
    private World world;
    private Block pit;

    private final Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
        new Class<?>[] {Scheduler.class}, (p, m, a) -> {
            if (m.getName().equals("isOwner")) {
                return true;
            }
            throw new UnsupportedOperationException(m.getName());
        });

    @BeforeEach
    void setUp() {
        effects.world = blocks;
        world = server.addSimpleWorld("firepitworld");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, new FakeCustomItems(),
            Logger.getLogger("test"));
        RandomGenerator ash = () -> 0L;
        device = new FirepitDevice(new FirepitDevice.Services(FirepitMockBukkitTest::fuel,
            s -> s.getType() == Material.OAK_LOG || s.getType() == Material.STICK, s -> 0.0,
            new FirepitDevice.Cooker() {
                @Override
                public float recipeTemperature(ItemStack stack) {
                    return Float.NaN;
                }

                @Override
                public Optional<ItemStack> convert(ItemStack heated, float before) {
                    return Optional.empty();
                }
            }, s -> FirepitEffects.Douse.NONE, b -> false, b -> true, n -> new ItemStack(Material.GUNPOWDER, n),
            () -> ash, HeatConfig.DEFAULT, effects), codec, (p, b) -> { });
        service.register(device.kind());
        pit = world.getBlockAt(0, 64, 0);
        blocks.place(pit, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "z"),
            true);
        store.data.put(FakeCustomBlocks.at(pit), new DataMap());
        listener = new FirepitWorldListener(scheduler, blocks, service, Set.of(FirepitDevice.ID));
    }

    private ItemStack slot(int i) {
        DataMap stored = store.read(pit).orElseThrow();
        return codec.decode((stored.size() == 0 ? device.kind().fresh() : device.kind().codec().decode(stored)).slot(i));
    }

    private Item drop(ItemStack stack) {
        return world.dropItem(new Location(world, 0.5, 64.1, 0.5), stack);
    }

    @Test
    void aDroppedLogIsGatheredIntoTheFuelSlotAndTheEntityGoes() {
        Item log = drop(new ItemStack(Material.OAK_LOG, 3));
        assertThat(listener.gatherForTest(log)).isTrue();
        assertThat(slot(FirepitSim.SLOT_FUEL_INPUT).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(slot(FirepitSim.SLOT_FUEL_INPUT).getAmount()).as("TFC's fuel slot holds one item").isEqualTo(1);
        assertThat(log.getItemStack().getAmount()).as("the rest stays for the next check").isEqualTo(2);
        assertThat(listener.gatherForTest(log)).as("the slot is full until the fuel cascades").isFalse();
    }

    @Test
    void aNonFuelItemStaysAnEntity() {
        Item dirt = drop(new ItemStack(Material.DIRT, 2));
        assertThat(listener.gatherForTest(dirt)).isFalse();
        assertThat(dirt.isValid()).isTrue();
        assertThat(slot(FirepitSim.SLOT_FUEL_INPUT).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void anItemOutsideTheBoxIsNotGathered() {
        Item far = world.dropItem(new Location(world, 5.5, 64.1, 0.5), new ItemStack(Material.OAK_LOG));
        assertThat(listener.gatherForTest(far)).isFalse();
    }

    @Test
    void aLitFirepitHurtsASurvivalPlayerOnlyAndNotACreativeOne() {
        blocks.place(pit, BlockStateRef.of(FirepitDevice.ID).with("lit", "true").with("smoke_level", "0").with("axis", "z"),
            false);
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64.3, 0.5));
        assertThat(listener.onHotFloorForTest(player)).isTrue();
        int[] hits = new int[1];
        listener.damageWithForTest(p -> hits[0]++);

        player.setGameMode(GameMode.CREATIVE);
        listener.hurtForTest(player);
        assertThat(hits[0]).as("creative takes none").isZero();
        player.setGameMode(GameMode.SPECTATOR);
        listener.hurtForTest(player);
        assertThat(hits[0]).as("spectator takes none").isZero();

        player.setGameMode(GameMode.SURVIVAL);
        listener.hurtForTest(player);
        assertThat(hits[0]).isEqualTo(1);
        player.setGameMode(GameMode.ADVENTURE);
        listener.hurtForTest(player);
        assertThat(hits[0]).as("adventure behaves like survival").isEqualTo(2);
    }

    @Test
    void anUnlitFirepitIsNotHot() {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64.3, 0.5));
        assertThat(listener.onHotFloorForTest(player)).isFalse();
    }
}
