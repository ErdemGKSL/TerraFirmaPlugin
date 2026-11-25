/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.channel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.channel.ChannelData;
import dev.tfp.core.channel.MoldTableData;
import dev.tfp.core.channel.MoldTableRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-13: the connection rules of channels and mold tables, and the mold table's slots, solidify and right click. A
 * flower pot stands for the axe head mold.
 */
class ChannelMockBukkitTest extends MockServerTest {
    static final String COPPER = "tfp:metal/copper";
    private final AtomicLong now = new AtomicLong(100_000);
    private final List<ItemStack> table = new ArrayList<>();
    private final StackCodec codec = new StackCodec() {
        @Override
        public ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    };
    private MoldItems molds;
    private MoldTableDevice device;

    @BeforeEach
    void setUp() {
        ItemHeatHooks heat = new ItemHeatHooks(now::get, HeatConfig.DEFAULT, stack -> 1.2f);
        molds = new MoldItems(new MoldItems.Services(now::get,
            s -> Optional.ofNullable(s.getType() == Material.FLOWER_POT ? "tfp:ceramic/axe_head_mold" : null),
            (f, tag) -> f.startsWith("tfp:metal/"), heat,
            mold -> molds.content(mold).amount() >= 100 ? Optional.of(new MoldItems.Cast(new ItemStack(Material.IRON_AXE), 0.5f))
                : Optional.empty()));
        device = new MoldTableDevice(null, null, molds, codec);
    }

    private static Neighbourhood<BlockStateRef> around(BlockStateRef center, Map<Direction, BlockStateRef> sides) {
        return Neighbourhood.of((dx, dy, dz) -> {
            if (dx == 0 && dy == 0 && dz == 0) {
                return center;
            }
            for (Direction d : Direction.values()) {
                if (d.dx() == dx && d.dy() == dy && d.dz() == dz) {
                    return sides.getOrDefault(d, BlockStateRef.of("minecraft:air"));
                }
            }
            return BlockStateRef.of("minecraft:air");
        });
    }

    private static BlockStateRef channel(String... props) {
        BlockStateRef s = BlockStateRef.of(ChannelWorld.CHANNEL, Map.of("east", "false", "south", "false", "west", "false",
            "north", "false", "down", "false", "triggered", "false", "with_metal", "false"));
        for (int i = 0; i < props.length; i += 2) {
            s = s.with(props[i], props[i + 1]);
        }
        return s;
    }

    @Test
    void aChannelConnectsToChannelsCruciblesAndMoldTablesOnItsSidesAndBelow() {
        var rule = ConnectionRules.channel();
        BlockStateRef next = rule.decide(around(channel(), Map.of(Direction.NORTH, BlockStateRef.of(ChannelWorld.CHANNEL),
            Direction.EAST, BlockStateRef.of(ChannelWorld.CRUCIBLE), Direction.SOUTH, BlockStateRef.of(ChannelWorld.TABLE),
            Direction.WEST, BlockStateRef.of("minecraft:stone"), Direction.DOWN, BlockStateRef.of(ChannelWorld.CHANNEL))));
        assertThat(next.properties()).containsEntry("north", "true").containsEntry("east", "true")
            .containsEntry("south", "true").containsEntry("west", "false").containsEntry("down", "true");
        // the flow's own properties are left alone
        BlockStateRef lit = channel("with_metal", "true", "triggered", "true");
        BlockStateRef kept = ConnectionRules.channel().decide(around(lit, Map.of(Direction.NORTH, BlockStateRef.of(ChannelWorld.CHANNEL))));
        assertThat(kept.properties()).containsEntry("with_metal", "true").containsEntry("triggered", "true")
            .containsEntry("north", "true");
    }

    @Test
    void aSettledChannelAsksForNoChange() {
        assertThat(ConnectionRules.channel().decide(around(channel(), Map.of()))).isNull();
        BlockStateRef connected = channel("north", "true");
        assertThat(ConnectionRules.channel().decide(around(connected, Map.of(Direction.NORTH, BlockStateRef.of(ChannelWorld.CHANNEL)))))
            .isNull();
    }

    @Test
    void aMoldTableConnectsOnlyToChannelsAndHasNoDownSide() {
        BlockStateRef t = BlockStateRef.of(ChannelWorld.TABLE, Map.of("east", "false", "south", "false", "west", "false", "north", "false"));
        BlockStateRef next = ConnectionRules.table().decide(around(t, Map.of(Direction.EAST, BlockStateRef.of(ChannelWorld.CHANNEL),
            Direction.WEST, BlockStateRef.of(ChannelWorld.TABLE), Direction.DOWN, BlockStateRef.of(ChannelWorld.CHANNEL))));
        assertThat(next.properties()).containsEntry("east", "true").containsEntry("west", "false").doesNotContainKey("down");
    }

    @Test
    void aChannelWithAnUnloadedSideKeepsIt() {
        BlockStateRef connected = channel("north", "true");
        Neighbourhood<BlockStateRef> n = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? connected
            : dz == -1 && dx == 0 && dy == 0 ? null : BlockStateRef.of("minecraft:air"));
        assertThat(ConnectionRules.channel().decide(n)).isNull();
    }

    private ItemStack hotMold(int copper, float temperature) {
        ItemStack mold = new ItemStack(Material.FLOWER_POT);
        FluidPdc.write(mold, new FluidAmount(COPPER, copper));
        HeatPdc.write(mold, new HeatState(temperature, now.get()));
        return mold;
    }

    private DeviceRecord<MoldTableData> recordWith(ItemStack mold, ItemStack output) {
        DeviceRecord<MoldTableData> r = DeviceRecord.fresh(MoldTableData.IDLE, 2);
        return r.withSlot(MoldTableRules.MOLD_SLOT, codec.encode(mold)).withSlot(MoldTableRules.OUTPUT_SLOT, codec.encode(output));
    }

    @Test
    void theMoldSlotTakesMoldsOnlyAndEveryTableSlotHoldsOne() {
        assertThat(device.isItemValid(MoldTableRules.MOLD_SLOT, new ItemStack(Material.FLOWER_POT))).isTrue();
        assertThat(device.isItemValid(MoldTableRules.MOLD_SLOT, new ItemStack(Material.STONE))).isFalse();
        assertThat(device.isItemValid(MoldTableRules.OUTPUT_SLOT, new ItemStack(Material.STONE))).isTrue();
        assertThat(device.kind().slots()).isEqualTo(2);
        assertThat(device.kind().slotLimit(0)).isEqualTo(1);
        assertThat(device.kind().slotLimit(1)).isEqualTo(1);
        assertThat(device.kind().removal()).isEqualTo(DeviceKind.RemoveBehaviour.DROP);
    }

    @Test
    void aSolidMoldGivesItsCastingToTheOutputSlotAndIsDrained() {
        // cold mold (never heated: 0 degrees) holding 100 mB of copper: solid
        DeviceRecord<MoldTableData> after = device.solidify(recordWith(hotMold(100, 0f), ItemStack.empty()), 0, 10);
        assertThat(codec.decode(after.slot(MoldTableRules.OUTPUT_SLOT)).getType()).isEqualTo(Material.IRON_AXE);
        ItemStack mold = codec.decode(after.slot(MoldTableRules.MOLD_SLOT));
        assertThat(mold.getType()).isEqualTo(Material.FLOWER_POT);
        assertThat(molds.content(mold).isEmpty()).isTrue();
    }

    @Test
    void aMoltenMoldOrAFullOutputKeepsThingsAsTheyAre() {
        DeviceRecord<MoldTableData> molten = recordWith(hotMold(100, 1300f), ItemStack.empty());
        assertThat(device.solidify(molten, 0, 10)).isEqualTo(molten);
        DeviceRecord<MoldTableData> full = recordWith(hotMold(100, 0f), new ItemStack(Material.STONE));
        assertThat(device.solidify(full, 0, 10)).isEqualTo(full);
        DeviceRecord<MoldTableData> empty = recordWith(new ItemStack(Material.FLOWER_POT), ItemStack.empty());
        assertThat(device.solidify(empty, 0, 10)).isEqualTo(empty);
    }

    private DeviceContext<MoldTableData> click(PlayerMock player, DeviceRecord<MoldTableData> record) {
        return new DeviceContext<>(device.kind(), null, null, player, EquipmentSlot.HAND,
            player.getInventory().getItemInMainHand().clone(), record, now.get(), codec);
    }

    @Test
    void anEmptyTableTakesTheHeldMoldAndAFullOneGivesItsOutputWithoutSneaking() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.FLOWER_POT, 2));
        var handler = device.kind().handler();
        DeviceResult<MoldTableData> in = handler.useWithItem(click(player, recordWith(ItemStack.empty(), ItemStack.empty())));
        assertThat(in.consumed()).isTrue();
        assertThat(codec.decode(in.updated().orElseThrow().slot(MoldTableRules.MOLD_SLOT)).getType()).isEqualTo(Material.FLOWER_POT);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);

        player.getInventory().setItemInMainHand(ItemStack.empty());
        DeviceResult<MoldTableData> out = handler.useWithItem(click(player,
            recordWith(new ItemStack(Material.FLOWER_POT), new ItemStack(Material.IRON_AXE))));
        assertThat(out.consumed()).isTrue();
        assertThat(codec.decode(out.updated().orElseThrow().slot(MoldTableRules.OUTPUT_SLOT)).isEmpty()).isTrue();
        assertThat(player.getInventory().contains(Material.IRON_AXE)).isTrue();
        // a mold with no output and nothing to do passes the click on
        assertThat(handler.useWithItem(click(player, recordWith(new ItemStack(Material.FLOWER_POT), ItemStack.empty()))).consumed())
            .isFalse();
    }

    @Test
    void sneakingWithAMoldInTheSlotTakesItAndTheOutput() {
        PlayerMock player = server.addPlayer();
        player.setSneaking(true);
        DeviceResult<MoldTableData> r = device.kind().handler().useWithItem(click(player,
            recordWith(new ItemStack(Material.FLOWER_POT), new ItemStack(Material.IRON_AXE))));
        assertThat(r.consumed()).isTrue();
        DeviceRecord<MoldTableData> next = r.updated().orElseThrow();
        assertThat(next.slot(MoldTableRules.MOLD_SLOT).isEmpty()).isTrue();
        assertThat(next.slot(MoldTableRules.OUTPUT_SLOT).isEmpty()).isTrue();
        assertThat(player.getInventory().contains(Material.FLOWER_POT)).isTrue();
        assertThat(player.getInventory().contains(Material.IRON_AXE)).isTrue();
    }

    @Test
    void theChannelKindHasNoSlotsAndDropsNothing() {
        ChannelDevice channel = new ChannelDevice(null, null);
        assertThat(channel.kind().slots()).isZero();
        assertThat(channel.kind().id()).isEqualTo("tfp:channel");
        assertThat(channel.kind().fresh().state()).isEqualTo(ChannelData.IDLE);
        assertThat(device.kind().id()).isEqualTo("tfp:mold_table");
    }
}
