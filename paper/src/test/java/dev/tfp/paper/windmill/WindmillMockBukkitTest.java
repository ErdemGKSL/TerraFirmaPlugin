/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.windmill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.Windmill;
import dev.tfp.core.windmill.WindmillBlades;
import dev.tfp.core.windmill.WindmillSample;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-MECH-04 on MockBukkit with a fake CraftEngine adapter: a windmill with five blades in a strong wind reaches TFC's top
 * speed and reports it; a solid block in its disk stops it; a blade on a horizontal axle makes a windmill (and is refused when
 * the disk is blocked); blades go in and out as TFC's useItemOn, and the last blade out gives the axle back.
 */
class WindmillMockBukkitTest extends MockServerTest {
    private static final String WINDMILL = "tfp:wood/windmill/oak";
    private static final String AXLE = "tfp:wood/axle/oak";
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(WINDMILL, AXLE);
    private final InlineScheduler scheduler = new InlineScheduler();
    private final Set<String> solid = new HashSet<>();
    private final Map<String, WindmillSample> stored = new HashMap<>();
    private final Map<String, ItemStack> firstBlade = new HashMap<>();
    private final List<String> reports = new ArrayList<>();
    private final List<String> told = new ArrayList<>();
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
    private float wind = 0.5f;
    private WindmillService service;
    private WindmillDevices devices;
    private World world;

    private final WindmillNetwork network = new WindmillNetwork() {
        @Override
        public void source(UUID w, BlockPos pos, ShaftAxis axis, float speed) {
            reports.add("source " + axis + " " + speed);
        }

        @Override
        public void remove(UUID w, BlockPos pos) {
            reports.add("remove");
        }
    };

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("mill");
        world.getChunkAt(0, 0);
        WindmillService.Records records = new WindmillService.Records() {
            @Override
            public WindmillSample load(Block block) {
                return stored.getOrDefault(key(block), WindmillSample.FRESH);
            }

            @Override
            public void save(Block block, WindmillSample next) {
                stored.put(key(block), next);
            }
        };
        service = new WindmillService(scheduler, blocks, b -> Optional.of(solid.contains(key(b))), b -> () -> wind, records,
            () -> network);
        devices = new WindmillDevices(service, new WindmillChunkIndex(new PdcChunkDataStore()), blocks,
            stack -> stack.getType() == Material.PAPER, new WindmillDevices.World() {
                @Override
                public void setState(Block block, BlockStateRef expected, BlockStateRef next) {
                    blocks.place(block, next, false);
                }

                @Override
                public void replace(Block block, BlockStateRef expected, BlockStateRef next) {
                    blocks.place(block, next, false);
                }

                @Override
                public boolean place(Block block, BlockStateRef state) {
                    return blocks.place(block, state, false);
                }

                @Override
                public void insertFirstBlade(Block block, ItemStack blade) {
                    firstBlade.put(key(block), blade);
                }

                @Override
                public void tell(Player player, String key) {
                    told.add(key);
                }
            });
    }

    private Block windmill(int count, String axis) {
        Block b = world.getBlockAt(8, 100, 8);
        blocks.place(b, BlockStateRef.of(WINDMILL).with("axis", axis).with("count", Integer.toString(count)), false);
        return b;
    }

    private void checks(int n) {
        for (int i = 0; i < n; i++) {
            scheduler.repeating.get(0).run();
        }
    }

    /** The done criterion's windmill side: five blades in a 0.5 wind turn at 2 pi / 80 after 13 checks (500 ticks of ramp). */
    @Test
    void fiveBladesInStrongWindReachTopSpeed() {
        Block b = windmill(5, "z");
        service.start(b);
        checks(12);
        assertThat(stored.get("8,100,8").speed()).isLessThan(Windmill.targetSpeed(5, 0.5f));
        checks(1);
        assertThat(stored.get("8,100,8").speed()).isCloseTo((float) (2 * Math.PI / 80), within(1e-6f));
        assertThat(reports).last().asString().startsWith("source Z 0.0785");
    }

    /** No wind (below sea level, another dimension): the windmill does not turn. */
    @Test
    void noWindNoTurn() {
        wind = 0f;
        Block b = windmill(5, "x");
        service.start(b);
        checks(5);
        assertThat(stored.getOrDefault("8,100,8", WindmillSample.FRESH).speed()).isZero();
    }

    /** TFC isObstructedBySolidBlocks: a solid block 6 below in the windmill's plane stops it; one in the other plane does not. */
    @Test
    void aSolidBlockInTheDiskStopsIt() {
        Block b = windmill(4, "x"); // X windmill: the disk is in the z plane
        service.start(b);
        checks(4);
        assertThat(stored.get("8,100,8").speed()).isPositive();
        solid.add("9,100,8"); // x plane: ignored
        checks(1);
        assertThat(stored.get("8,100,8").obstructed()).isFalse();
        solid.add("8,94,8"); // dy = -6: inside the disk (36 < 49)
        checks(2);
        assertThat(stored.get("8,100,8").obstructed()).isTrue();
        assertThat(stored.get("8,100,8").speed()).isZero();
        solid.clear();
        solid.add("8,94,14"); // dH = 6, dy = -6: outside the disk (72 >= 49)
        checks(1);
        assertThat(stored.get("8,100,8").obstructed()).isFalse();
    }

    /** TFC WindmillBladeItem.useOn: a horizontal axle becomes a windmill with that blade, one blade is used up. */
    @Test
    void aBladeOnAnAxleMakesAWindmill() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER, 3));
        Block b = world.getBlockAt(8, 100, 8);
        blocks.place(b, BlockStateRef.of(AXLE).with("axis", "z"), false);
        assertThat(devices.useBladeOn(b, player, EquipmentSlot.HAND)).isTrue();
        assertThat(blocks.stateAt(b)).contains(BlockStateRef.of(WINDMILL).with("axis", "z").with("count", "1"));
        assertThat(firstBlade.get("8,100,8").getAmount()).isEqualTo(1);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(service.running()).isEqualTo(1);
    }

    @Test
    void aBladeIsRefusedWithoutSpaceOrOnAVerticalAxle() {
        PlayerMock player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER, 1));
        Block b = world.getBlockAt(8, 100, 8);
        blocks.place(b, BlockStateRef.of(AXLE).with("axis", "x"), false);
        solid.add("8,103,10");
        assertThat(devices.useBladeOn(b, player, EquipmentSlot.HAND)).isTrue();
        assertThat(told).containsExactly(WindmillBlades.NOT_ENOUGH_SPACE);
        assertThat(blocks.stateAt(b).orElseThrow().id()).isEqualTo(AXLE);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);

        blocks.place(b, BlockStateRef.of(AXLE).with("axis", "y"), false);
        assertThat(devices.useBladeOn(b, player, EquipmentSlot.HAND)).isFalse();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK, 1));
        blocks.place(b, BlockStateRef.of(AXLE).with("axis", "z"), false);
        assertThat(devices.useBladeOn(b, player, EquipmentSlot.HAND)).isFalse();
    }

    private DeviceContext<WindmillSample> click(Block b, PlayerMock player, DeviceRecord<WindmillSample> record) {
        return new DeviceContext<>(devices.kind(), b, blocks.stateAt(b).orElseThrow(), player, EquipmentSlot.HAND,
            player.getInventory().getItemInMainHand().clone(), record, 0L, codec);
    }

    /** TFC useItemOn: blades go in one at a time up to five; at five, or with an empty hand, the last one comes out. */
    @Test
    void bladesGoInAndOut() {
        DeviceKind<WindmillSample> kind = devices.kind();
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        Block b = windmill(1, "x");
        DeviceRecord<WindmillSample> record = kind.fresh().withSlot(0, codec.encode(new ItemStack(Material.PAPER)));
        player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER, 10));
        for (int expected = 2; expected <= 5; expected++) {
            DeviceResult<WindmillSample> r = kind.handler().useWithItem(click(b, player, record));
            assertThat(r.consumed()).isTrue();
            record = r.updated().orElseThrow();
            assertThat(blocks.stateAt(b).orElseThrow().properties().get("count")).isEqualTo(Integer.toString(expected));
        }
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(6);
        // at five: a click with blades in hand takes one out
        DeviceResult<WindmillSample> out = kind.handler().useWithItem(click(b, player, record));
        record = out.updated().orElseThrow();
        assertThat(blocks.stateAt(b).orElseThrow().properties().get("count")).isEqualTo("4");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(7);
        // a stick is not a blade: the click passes
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK, 1));
        assertThat(kind.handler().useWithItem(click(b, player, record)).consumed()).isFalse();
        // empty hand (the stick is put away): the rest come out; the last one turns the windmill back into its axle
        for (int i = 0; i < 4; i++) {
            player.getInventory().setItemInMainHand(null);
            record = kind.handler().useWithItem(click(b, player, record)).updated().orElseThrow();
            assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.PAPER); // given back
        }
        assertThat(blocks.stateAt(b)).contains(BlockStateRef.of(AXLE).with("axis", "x"));
    }

    @Test
    void codecAndIds() {
        WindmillSample s = new WindmillSample(0.02f, true);
        assertThat(WindmillBlocks.CODEC.fromBytes(WindmillBlocks.CODEC.toBytes(s))).isEqualTo(s);
        assertThat(WindmillBlocks.windmillOf(BlockStateRef.of(AXLE).with("axis", "x")))
            .isEqualTo(BlockStateRef.of(WINDMILL).with("axis", "x").with("count", "1"));
        assertThat(WindmillBlocks.count(BlockStateRef.of(WINDMILL).with("count", "9"))).isEqualTo(5);
    }
}
