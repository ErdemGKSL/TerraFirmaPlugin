/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scraping;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.scraping.ScrapingState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-05 on MockBukkit, with fakes of the CraftEngine adapter and the recipe runtime: the look ray of a real player to
 * a grid cell, placing a hide on a log, 16 knife clicks turning a soaked hide into a scraped hide, the sheepskin's extra
 * drop, wax and dyes, and the support rule.
 *
 * <p>Stand-ins: leather = soaked hide (scraped: rabbit hide), white carpet = sheepskin hide (raw: rabbit hide, extra drop:
 * white wool), shears = knife, honeycomb = wax, vanilla dyes = dyes, oak log = scraping surface.
 */
class ScrapingMockBukkitTest extends MockServerTest {
    /** In-memory store: positions with a "block entity" answer. */
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

    /** Slots hold an index into this table (MockBukkit cannot serialise every meta). */
    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

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
    }

    /** Records every world effect. */
    static final class Effects implements ScrapingEffects {
        int damage;
        int particles;
        final List<ItemStack> spawned = new ArrayList<>();
        ScrapingState shown;
        boolean hidden;

        @Override
        public void damageTool(Player player, EquipmentSlot hand) {
            damage++;
        }

        @Override
        public void particles(Block block, double x, double z, ItemStack item) {
            particles++;
        }

        @Override
        public void spawn(Block block, ItemStack item) {
            spawned.add(item);
        }

        @Override
        public void show(Block block, ScrapingState state) {
            shown = state;
        }

        @Override
        public void hide(Block block) {
            hidden = true;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(ScrapingDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private DeviceService service;
    private ScrapingListener listener;
    private World world;
    private Block log;
    private Block surface;
    private PlayerMock player;

    static Optional<ScrapingDevice.Recipe> recipe(ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return Optional.empty();
        }
        return switch (stack.getType()) {
            case LEATHER -> Optional.of(new ScrapingDevice.Recipe("tfp:item/hide/medium/soaked",
                in -> new ItemStack(Material.RABBIT_HIDE), in -> ItemStack.empty()));
            case WHITE_CARPET -> Optional.of(new ScrapingDevice.Recipe("tfp:item/hide/medium/sheepskin",
                in -> new ItemStack(Material.RABBIT_HIDE), in -> new ItemStack(Material.WHITE_WOOL)));
            default -> Optional.empty();
        };
    }

    static OptionalInt dye(ItemStack stack) {
        String name = stack.getType().name();
        return name.endsWith("_DYE") ? OptionalInt.of(DyeColor.valueOf(name.substring(0, name.length() - 4)).ordinal())
            : OptionalInt.empty();
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("scraping");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, new AtomicLong(100)::get, stacks,
            new FakeCustomItems(), Logger.getLogger("test"));
        service.register(ScrapingDevice.kind(new ScrapingDevice.Services(ScrapingMockBukkitTest::recipe,
            s -> s.getType() == Material.HONEYCOMB, s -> s.getType() == Material.SHEARS, ScrapingMockBukkitTest::dye, effects)));
        listener = new ScrapingListener(new ScrapingListener.Placer() {
            @Override
            public boolean scrapable(ItemStack item) {
                return recipe(item).isPresent();
            }

            @Override
            public boolean surface(Block block) {
                return block.getType() == Material.OAK_LOG;
            }

            @Override
            public boolean owns(Block block) {
                return true;
            }

            @Override
            public boolean place(Block block, BlockStateRef state) {
                blocks.place(block, state, true);
                store.data.put(FakeCustomBlocks.at(block), new DataMap()); // CraftEngine creates the block entity
                return true;
            }

            @Override
            public void placed(Block block, Player p, EquipmentSlot hand, ItemStack one) {
                service.placed(block, p, hand, one);
            }

            @Override
            public boolean holds(Block block) {
                return !record(block).slot(ScrapingDevice.SLOT).isEmpty();
            }
        });
        log = world.getBlockAt(0, 64, 0);
        log.setType(Material.OAK_LOG);
        surface = log.getRelative(0, 1, 0);
        player = server.addPlayer();
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<ScrapingState> record(Block block) {
        DeviceKind<ScrapingState> kind = (DeviceKind<ScrapingState>) service.kinds().find(ScrapingDevice.ID).orElseThrow();
        return kind.codec().decode(store.read(block).orElseThrow());
    }

    private ItemStack slot(Block block) {
        return stacks.decode(record(block).slot(ScrapingDevice.SLOT));
    }

    /** Puts the player's eye at {@code eye} (block-relative) looking at the centre of cell (x, z) on the block's plane. */
    private void aimAt(Block block, Vector eye, int x, int z) {
        Vector target = new Vector((x + 0.5) / 4, 0, (z + 0.5) / 4);
        Vector dir = target.clone().subtract(eye);
        Location feet = new Location(world, block.getX() + eye.getX(), block.getY() + eye.getY() - player.getEyeHeight(),
            block.getZ() + eye.getZ());
        feet.setDirection(dir);
        player.teleport(feet);
    }

    private void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, new ItemStack(type, amount));
    }

    private boolean click() {
        return service.use(surface, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND));
    }

    private void placeHide(Material hide) {
        hold(hide, 3);
        assertThat(listener.tryPlace(player, EquipmentSlot.HAND, log, player.getInventory().getItem(EquipmentSlot.HAND)))
            .isTrue();
    }

    @Test
    void hitToCellMapping() {
        // from above and from three oblique eyes, every cell centre maps to its own cell x + 4 z
        List<Vector> eyes = List.of(new Vector(0.5, 1.7, 0.5), new Vector(-1.2, 1.9, 0.3), new Vector(2.0, 1.6, 2.4),
            new Vector(0.4, 2.2, -1.5));
        for (Vector eye : eyes) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    aimAt(surface, eye, x, z);
                    assertThat(ScrapingDevice.aimedCell(player, surface)).as("eye " + eye + " cell " + x + "," + z)
                        .hasValue(x + 4 * z);
                }
            }
        }
        // looking level never meets the plane
        Location level = player.getLocation();
        level.setPitch(0);
        player.teleport(level);
        assertThat(ScrapingDevice.aimedCell(player, surface)).isEmpty();
    }

    @Test
    void scrapingASoakedHideYieldsAScrapedHide() {
        placeHide(Material.LEATHER);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).as("one hide went onto the log").isEqualTo(2);
        assertThat(blocks.stateAt(surface)).contains(BlockStateRef.of(ScrapingDevice.ID));
        assertThat(slot(surface).getType()).isEqualTo(Material.LEATHER);
        assertThat(slot(surface).getAmount()).isEqualTo(1);
        assertThat(effects.shown.look()).isEqualTo("tfp:scraping/hide/medium/soaked");

        hold(Material.SHEARS, 1);
        Vector eye = new Vector(0.5, 1.7, -0.6);
        int order = 0;
        for (int cell : new int[] {5, 0, 15, 3, 12, 7, 9, 1, 14, 2, 8, 13, 4, 11, 6, 10}) {
            aimAt(surface, eye, cell % 4, cell / 4);
            assertThat(click()).isTrue();
            order++;
            assertThat(record(surface).state().mask()).isEqualTo(effects.shown.mask());
            assertThat(Integer.bitCount(record(surface).state().mask())).isEqualTo(order);
            assertThat(slot(surface).getType()).isEqualTo(order < 16 ? Material.LEATHER : Material.RABBIT_HIDE);
        }
        assertThat(record(surface).state().complete()).isTrue();
        assertThat(effects.damage).as("one wear per scraped cell").isEqualTo(16);
        assertThat(effects.particles).isEqualTo(16);
        assertThat(effects.spawned).as("a soaked hide has no extra drop").isEmpty();
        assertThat(effects.shown.look()).as("the look stays after completion").isEqualTo("tfp:scraping/hide/medium/soaked");

        // a scraped cell: handled (TFC FAIL), no wear
        assertThat(click()).isTrue();
        assertThat(effects.damage).isEqualTo(16);
    }

    @Test
    void sheepskinDropsItsWoolWhenComplete() {
        placeHide(Material.WHITE_CARPET);
        hold(Material.SHEARS, 1);
        for (int cell = 0; cell < 16; cell++) {
            aimAt(surface, new Vector(0.5, 1.8, 0.5), cell % 4, cell / 4);
            click();
        }
        assertThat(slot(surface).getType()).isEqualTo(Material.RABBIT_HIDE);
        assertThat(effects.spawned).hasSize(1);
        assertThat(effects.spawned.getFirst().getType()).isEqualTo(Material.WHITE_WOOL);
    }

    @Test
    void waxTakesDyesAndNoLongerTheKnife() {
        placeHide(Material.LEATHER);
        aimAt(surface, new Vector(0.5, 1.7, 0.5), 1, 1);
        hold(Material.HONEYCOMB, 2);
        assertThat(click()).isTrue();
        assertThat(record(surface).state().waxed()).isTrue();
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(1);
        // a second wax is no wax use; on a waxed surface it is no dye either
        assertThat(click()).isFalse();

        hold(Material.SHEARS, 1);
        assertThat(click()).as("TFC: a waxed surface passes the knife").isFalse();
        assertThat(record(surface).state().mask()).isZero();

        hold(Material.RED_DYE, 1);
        assertThat(click()).isTrue();
        hold(Material.BLUE_DYE, 1);
        assertThat(click()).isTrue();
        hold(Material.BLACK_DYE, 1);
        assertThat(click()).as("both colours set").isFalse();
        ScrapingState s = record(surface).state();
        assertThat(s.color1()).isEqualTo(DyeColor.RED.ordinal());
        assertThat(s.color2()).isEqualTo(DyeColor.BLUE.ordinal());
        assertThat(effects.shown).isEqualTo(s);
    }

    @Test
    void creativeKeepsTheWaxButTfcTakesTheHide() {
        player.setGameMode(GameMode.CREATIVE);
        placeHide(Material.LEATHER);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).as("TFC stack.split(1) in creative too")
            .isEqualTo(2);
        aimAt(surface, new Vector(0.5, 1.7, 0.5), 0, 0);
        hold(Material.HONEYCOMB, 2);
        assertThat(click()).isTrue();
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(2);
    }

    @Test
    void placementNeedsASurfaceTheTopFaceAndAirAbove() {
        hold(Material.LEATHER, 1);
        ItemStack hide = player.getInventory().getItem(EquipmentSlot.HAND);
        Block dirt = world.getBlockAt(5, 64, 0);
        dirt.setType(Material.DIRT);
        assertThat(listener.tryPlace(player, EquipmentSlot.HAND, dirt, hide)).isFalse();
        surface.setType(Material.STONE);
        assertThat(listener.tryPlace(player, EquipmentSlot.HAND, log, hide)).isFalse();
        assertThat(blocks.placed()).isEmpty();
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(1);
    }

    @Test
    void breakingTheDeviceDropsTheHideAndRemovesTheSurface() {
        placeHide(Material.LEATHER);
        DataMap data = store.read(surface).orElseThrow();
        service.removed(world, surface.getX(), surface.getY(), surface.getZ(), ScrapingDevice.ID, data);
        assertThat(effects.hidden).isTrue();
        assertThat(world.getEntities().stream().filter(e -> e instanceof org.bukkit.entity.Item)
            .map(e -> ((org.bukkit.entity.Item) e).getItemStack().getType())).containsExactly(Material.LEATHER);
    }

    @Test
    void theSupportRuleRemovesTheBlockWithoutALogBelow() {
        ScrapingSupport rule = new ScrapingSupport(id -> id.equals("minecraft:oak_log"));
        BlockStateRef self = BlockStateRef.of(ScrapingDevice.ID);
        Neighbourhood<BlockStateRef> onLog = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dz == 0 && dy == -1
            ? BlockStateRef.parse("minecraft:oak_log[axis=y]") : dx == 0 && dy == 0 && dz == 0 ? self : BlockStateRef.of("minecraft:air"));
        assertThat(rule.decide(onLog)).isNull();
        Neighbourhood<BlockStateRef> onAir = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? self
            : BlockStateRef.of("minecraft:air"));
        assertThat(rule.decide(onAir)).isEqualTo(BlockStateRef.of("minecraft:air"));
        Neighbourhood<BlockStateRef> unknown = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? self : null);
        assertThat(rule.decide(unknown)).isNull();
        assertThat(rule.appliesTo(self)).isTrue();
    }
}
