/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.testing.MockServerTest;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-03: hot item entities cool in water and snow when touched, on MockBukkit entities and blocks. */
class HotItemListenerTest extends MockServerTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;
    private static final float INGOT = 2.857143f;
    private static final RandomGenerator NEVER = () -> Long.MAX_VALUE;
    private static final RandomGenerator ALWAYS = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public float nextFloat() {
            return 0f;
        }
    };

    private final AtomicLong clock = new AtomicLong(10_000L);
    private World world;
    private HotItemListener listener;
    private ItemHeatHooks hooks;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        ItemHeatHooks.Capacities caps = s -> s.getType() == Material.IRON_INGOT ? INGOT : 0f;
        listener = new HotItemListener(clock::get, CFG, caps, null);
        hooks = new ItemHeatHooks(clock::get, CFG, caps);
    }

    private Item drop(int x, int y, int z, float temperature) {
        ItemStack stack = new ItemStack(Material.IRON_INGOT);
        HeatPdc.write(stack, new HeatState(temperature, clock.get()));
        return world.dropItem(new Location(world, x + 0.5, y, z + 0.5), stack);
    }

    private void age(Item item, int ticks) {
        item.setTicksLived(item.getTicksLived() + ticks);
        clock.addAndGet(ticks);
    }

    @Test
    void anIngotInWaterReadsColderThanInAirAfterFiveSeconds() {
        world.getBlockAt(0, 64, 0).setType(Material.WATER);
        Item wet = drop(0, 64, 0, 900f);
        Item dry = drop(4, 64, 4, 900f);
        age(wet, 100);
        dry.setTicksLived(dry.getTicksLived() + 100);

        assertThat(listener.evaluate(wet, NEVER)).isPresent();
        listener.evaluate(dry, NEVER);

        float wetT = hooks.temperature(wet.getItemStack());
        float dryT = hooks.temperature(dry.getItemStack());
        assertThat(wetT).isLessThan(dryT);
        assertThat(dryT).isCloseTo(900f - 100 * 0.8f / INGOT, within(0.05f));
        assertThat(wetT).isCloseTo(372f, within(3f)); // 10 water checks (golden in HotItemCoolingTest), ticksLived offset
    }

    @Test
    void aSpanIsNeverReplayedTwice() {
        world.getBlockAt(0, 64, 0).setType(Material.WATER);
        Item wet = drop(0, 64, 0, 900f);
        age(wet, 100);
        listener.evaluate(wet, NEVER);
        float once = hooks.temperature(wet.getItemStack());
        assertThat(listener.evaluate(wet, NEVER)).isEmpty();
        assertThat(hooks.temperature(wet.getItemStack())).isEqualTo(once);
        assertThat(wet.getPersistentDataContainer().has(HotItemListener.CHECKED)).isTrue();
    }

    @Test
    void snowLayerMeltsAndTheItemCools() {
        world.getBlockAt(0, 64, 0).setType(Material.SNOW); // one layer (MockBukkit has no Snow block data)
        Item item = drop(0, 64, 0, 1400f);
        age(item, 50);

        listener.evaluate(item, ALWAYS);

        assertThat(world.getBlockAt(0, 64, 0).getType()).isEqualTo(Material.AIR);
        assertThat(hooks.temperature(item.getItemStack())).isLessThan(1400f - 70f);
    }

    @Test
    void aColdOrUnheatedItemIsLeftAlone() {
        Item plain = world.dropItem(new Location(world, 0.5, 64, 0.5), new ItemStack(Material.IRON_INGOT));
        plain.setTicksLived(500);
        assertThat(listener.evaluate(plain, NEVER)).isEmpty();
        assertThat(HeatPdc.has(plain.getItemStack())).isFalse();

        Item stone = world.dropItem(new Location(world, 0.5, 64, 0.5), new ItemStack(Material.STONE));
        HeatPdc.write(stone.getItemStack(), new HeatState(500f, clock.get()));
        stone.setTicksLived(500);
        assertThat(listener.evaluate(stone, NEVER)).isEmpty();
    }

    @Test
    void anItemStillHotAtTheDespawnEventDespawnsLikeInTfc() {
        Item item = drop(0, 64, 0, 2000f); // air: 0.28 C/tick, still about 320 C after one lifespan
        age(item, HotItemListener.LIFESPAN);
        assertThat(listener.keepAlive(item, NEVER)).isFalse();
        assertThat(hooks.temperature(item.getItemStack())).isGreaterThan(0f);
        assertThat(item.getPersistentDataContainer().has(HotItemListener.COLD_AT)).isFalse();
    }

    @Test
    void anItemThatCooledBeforeTheLifespanLivesOneLifespanFromThatCheck() {
        world.getBlockAt(0, 63, 0).setType(Material.PACKED_ICE);
        Item item = drop(0, 64, 0, 300f);
        age(item, 30);
        listener.evaluate(item, NEVER); // 300 - 2.8 - 125 per check: cold at check 3
        assertThat(hooks.temperature(item.getItemStack())).isZero();
        Integer coldAt = item.getPersistentDataContainer().get(HotItemListener.COLD_AT, PersistentDataType.INTEGER);
        assertThat(coldAt).isNotNull().isLessThanOrEqualTo(item.getTicksLived()).isGreaterThan(item.getTicksLived() - 10);

        age(item, coldAt + HotItemListener.LIFESPAN - 1 - item.getTicksLived());
        assertThat(listener.keepAlive(item, NEVER)).isTrue(); // X + 6000 not reached yet
        age(item, 1);
        assertThat(listener.keepAlive(item, NEVER)).isFalse(); // despawns at X + 6000
    }

    @Test
    void columnClassifiesTheTfcBlocks() {
        assertThat(HotItemListener.column(Material.WATER.createBlockData(), Material.STONE).at())
                .isEqualTo(HotItemCooling.At.WATER_BLOCK);
        assertThat(HotItemListener.column(Material.KELP.createBlockData(), Material.STONE).at())
                .isEqualTo(HotItemCooling.At.WATERLOGGED);
        assertThat(HotItemListener.column(Material.AIR.createBlockData(), Material.BLUE_ICE).below())
                .isEqualTo(HotItemCooling.Below.SEA_ICE);
        assertThat(HotItemListener.column(Material.AIR.createBlockData(), Material.FROSTED_ICE).below())
                .isEqualTo(HotItemCooling.Below.ICE);
        assertThat(HotItemListener.column(Material.AIR.createBlockData(), Material.LAVA).below())
                .isEqualTo(HotItemCooling.Below.OTHER);
    }
}
