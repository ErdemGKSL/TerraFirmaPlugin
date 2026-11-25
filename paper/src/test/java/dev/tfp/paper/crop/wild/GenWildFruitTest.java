/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crop.wild;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FARM-27: the vanilla placeholders of a wild crop's fruit become wild {@code tfp:fruit} blocks. */
class GenWildFruitTest extends MockServerTest {
    static final long NOW = 5_000_000L;
    static final StackCodec NO_STACKS = new StackCodec() {
        @Override
        public ItemBlob encode(ItemStack stack) {
            return ItemBlob.EMPTY;
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return ItemStack.empty();
        }
    };

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();
    private DeviceKind<FruitDevice.FruitState> kind;
    private DeviceService service;
    private World world;
    private GenWildFruit gen;

    @BeforeEach
    void setUp() {
        blocks.known("tfp:fruit/pumpkin", "tfp:fruit/melon");
        world = server.addSimpleWorld("wild");
        DeviceStore store = new DeviceStore() {
            @Override
            public Optional<DataMap> read(Block block) {
                DataMap m = data.get(FakeCustomBlocks.at(block));
                return m == null ? Optional.empty() : Optional.of(m.copy());
            }

            @Override
            public boolean write(Block block, DataMap map) {
                data.put(FakeCustomBlocks.at(block), map.copy());
                return true;
            }
        };
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> NOW, NO_STACKS, new FakeCustomItems(),
            Logger.getLogger("test"));
        kind = service.register(DeviceKind.builder(FruitDevice.ID, FruitDevice.CODEC, new FruitDevice.FruitState(FoodDecay.ROTTEN_FLAG, false))
            .slots(0).removal(DeviceKind.RemoveBehaviour.NOOP).build());
        gen = new GenWildFruit(service, blocks, Optional::empty);
    }

    @Test
    void aPumpkinPlaceholderBecomesAWildFruitCreatedNow() {
        Block block = world.getBlockAt(3, 65, 4);
        block.setType(Material.PUMPKIN);
        data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(new FruitDevice.FruitState(0L, false), 0)));
        assertThat(gen.commit(block)).isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo("tfp:fruit/pumpkin");
        FruitDevice.FruitState stored = kind.codec().decode(data.get(FakeCustomBlocks.at(block))).state();
        assertThat(stored.wild()).isTrue();
        assertThat(stored.creationDate()).isEqualTo(NOW);
    }

    @Test
    void aMelonPlaceholderBecomesAMelon() {
        Block block = world.getBlockAt(0, 65, 0);
        block.setType(Material.MELON);
        assertThat(gen.commit(block)).isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo("tfp:fruit/melon");
    }

    @Test
    void otherBlocksAndAlreadyConvertedFruitAreLeftAlone() {
        Block stone = world.getBlockAt(0, 65, 0);
        stone.setType(Material.STONE);
        assertThat(gen.commit(stone)).isFalse();
        assertThat(blocks.stateAt(stone)).isEmpty();
        Block pumpkin = world.getBlockAt(1, 65, 0);
        pumpkin.setType(Material.PUMPKIN);
        assertThat(gen.commit(pumpkin)).isTrue();
        assertThat(gen.commit(pumpkin)).isFalse();
    }

    @Test
    void withoutTheCraftEngineBlockThePlaceholderStays() {
        FakeCustomBlocks empty = new FakeCustomBlocks();
        Block block = world.getBlockAt(0, 65, 0);
        block.setType(Material.MELON);
        assertThat(new GenWildFruit(service, empty, Optional::empty).commit(block)).isFalse();
        assertThat(block.getType()).isEqualTo(Material.MELON);
    }

    @Test
    void theBlockIdsFollowThePlaceholders() {
        assertThat(GenWildFruit.blockId(Material.PUMPKIN)).isEqualTo("tfp:fruit/pumpkin");
        assertThat(GenWildFruit.blockId(Material.MELON)).isEqualTo("tfp:fruit/melon");
        assertThat(GenWildFruit.placeholder(Material.CARVED_PUMPKIN)).isFalse();
    }
}
