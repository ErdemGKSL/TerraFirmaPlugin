/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.chest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.chest.ChestRules;
import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.chest.ChestState;
import dev.tfp.core.chest.ChestType;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-DECO-04 on MockBukkit with the in-memory CraftEngine fake: the 18 slot chest and the 36 slot double chest (right half
 * first) as device slots, TFC's size restriction through PAR-SURV-06's {@link ItemSizes#fitsChest}, the placement and
 * pairing of chests, a half going single when its partner is broken, and the right click rules.
 */
class ChestMockBukkitTest extends MockServerTest {
    static final String OAK = "tfp:wood/chest/oak";

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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK, "tfp:wood/trapped_chest/oak");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final ChestScheduler scheduler = new ChestScheduler();
    private final List<Block> opened = new ArrayList<>();
    private DeviceService service;
    private ItemSizes sizes;
    private ChestPlacement placement;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("chest");
        world.getChunkAt(0, 0).load(); // the placement reads neighbours only in loaded chunks
        ItemSizeDefinitions defs = new ItemSizeDefinitions(List.of(new ItemSizeDefinitions.Definition("tfp:anvils",
            new Ingredient.Tag("tfp:anvils"), new ItemSizing(ItemSize.HUGE, ItemWeight.VERY_HEAVY))));
        TagLookup tags = new TagLookup() {
            @Override
            public boolean contains(TagType type, String tag, String id) {
                return type == TagType.ITEM && tag.equals("tfp:anvils") && id.equals("minecraft:anvil");
            }

            @Override
            public boolean exists(TagType type, String tag) {
                return true;
            }
        };
        sizes = new ItemSizes(() -> defs, () -> tags, s -> Optional.empty(), id -> false, ItemSizeLimits.DEFAULT);
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        placement = new ChestPlacement(blocks, scheduler, (b, s) -> blocks.place(b, s, false));
        for (String id : List.of(ChestBlocks.CHEST, ChestBlocks.TRAPPED_CHEST)) {
            service.register(ChestDevice.kind(id, sizes::fitsChest, (p, b) -> opened.add(b), placement));
        }
        player = server.addPlayer();
        player.teleport(new Location(world, 5.5, 64, 5.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private Block chest(int x, String facing, String type) {
        Block b = world.getBlockAt(x, 64, 0);
        blocks.place(b, BlockStateRef.of(OAK).with("facing", facing).with("type", type), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        return b;
    }

    private ChestBlocks.Found at(Block b) {
        return ChestBlocks.at(blocks, b).orElseThrow();
    }

    private ChestLink link(Block b) {
        return ChestLink.resolve(service, blocks, scheduler, b, sizes::fitsChest).orElseThrow();
    }

    private ItemStack device(Block b, int slot) {
        return service.inspect(b).orElseThrow().record().orElseThrow().slot(slot).isEmpty() ? null
            : stacks.decode(service.inspect(b).orElseThrow().record().orElseThrow().slot(slot));
    }

    private static List<ItemStack> slots(int n) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(ItemStack.empty());
        }
        return l;
    }

    @Test
    void aChestIsEighteenSlotsThatRefuseHugeItems() {
        Block b = chest(0, "south", "single");
        ChestLink link = link(b);
        assertThat(link.size()).isEqualTo(18).isEqualTo(ChestRules.SINGLE_SLOTS);
        assertThat(link.isDouble()).isFalse();
        List<ItemStack> in = slots(18);
        in.set(0, new ItemStack(Material.DIRT, 5));
        in.set(3, new ItemStack(Material.ANVIL)); // TFC: a huge item does not go into a chest
        in.set(17, new ItemStack(Material.STONE, 64));
        ChestLink.Committed c = link.commit(in).orElseThrow();
        assertThat(c.overflow()).hasSize(1);
        assertThat(c.overflow().getFirst().getType()).isEqualTo(Material.ANVIL);
        assertThat(c.snapshot().slots().get(0)).isEqualTo(new ItemStack(Material.DIRT, 5));
        assertThat(c.snapshot().slots().get(3).isEmpty()).isTrue();
        assertThat(device(b, 17)).isEqualTo(new ItemStack(Material.STONE, 64));
        assertThat(link.snapshot().orElseThrow()).isEqualTo(c.snapshot());
        assertThat(sizes.fitsChest(new ItemStack(Material.DIRT))).isTrue();
        assertThat(sizes.fitsChest(new ItemStack(Material.ANVIL))).isFalse();
    }

    @Test
    void aDoubleChestIsThirtySixSlotsWithTheRightHalfFirst() {
        // facing south: the right half's partner is east, the left half's is west
        Block right = chest(0, "south", "right");
        Block left = chest(1, "south", "left");
        for (Block b : List.of(right, left)) {
            ChestLink link = link(b);
            assertThat(link.isDouble()).isTrue();
            assertThat(link.size()).isEqualTo(36);
            assertThat(link.firstBlock()).isEqualTo(right);
            assertThat(link.keys()).containsExactly(ChestLink.Key.of(right), ChestLink.Key.of(left));
        }
        List<ItemStack> in = slots(36);
        in.set(0, new ItemStack(Material.COBBLESTONE, 3));
        in.set(18, new ItemStack(Material.DIRT, 2));
        in.set(35, new ItemStack(Material.SAND));
        link(left).commit(in).orElseThrow();
        assertThat(device(right, 0)).isEqualTo(new ItemStack(Material.COBBLESTONE, 3));
        assertThat(device(left, 0)).isEqualTo(new ItemStack(Material.DIRT, 2));
        assertThat(device(left, 17)).isEqualTo(new ItemStack(Material.SAND));
        assertThat(link(right).snapshot().orElseThrow().slots().get(18)).isEqualTo(new ItemStack(Material.DIRT, 2));
    }

    @Test
    void aHalfWhosePartnerIsGoneReadsAsASingleChest() {
        Block lonely = chest(4, "south", "left");
        assertThat(link(lonely).size()).isEqualTo(18);
        Block wrongFacing = chest(5, "north", "right"); // a chest beside it, but not its partner
        assertThat(link(lonely).isDouble()).isFalse();
        assertThat(link(wrongFacing).isDouble()).isFalse();
    }

    @Test
    void placingBesideAChestPairsThem() {
        Block first = chest(0, "north", "single");
        placement.placed(first, ChestSide.NORTH, null, false); // the player looked north: the chest faces south
        assertThat(at(first).chest()).isEqualTo(new ChestRules.Chest(ChestSide.SOUTH, ChestType.SINGLE));
        Block second = chest(1, "north", "single"); // CraftEngine places the default state
        placement.placed(second, ChestSide.NORTH, null, false);
        assertThat(at(second).chest()).isEqualTo(new ChestRules.Chest(ChestSide.SOUTH, ChestType.LEFT));
        assertThat(at(first).chest()).isEqualTo(new ChestRules.Chest(ChestSide.SOUTH, ChestType.RIGHT));
        assertThat(link(second).keys()).containsExactly(ChestLink.Key.of(first), ChestLink.Key.of(second));
        // a third chest does not join a double chest
        Block third = chest(2, "north", "single");
        placement.placed(third, ChestSide.NORTH, null, false);
        assertThat(at(third).chest().type()).isEqualTo(ChestType.SINGLE);
    }

    @Test
    void sneakingOnAChestsSideJoinsItWhicheverWayYouLook() {
        Block first = chest(0, "north", "single");
        placement.placed(first, ChestSide.SOUTH, null, false); // faces north
        Block second = chest(1, "north", "single");
        // the player looks south at the chest's east face while sneaking: the new chest faces the partner's way
        placement.placed(second, ChestSide.SOUTH, ChestSide.EAST, true);
        assertThat(at(second).chest().facing()).isEqualTo(ChestSide.NORTH);
        assertThat(at(second).chest().type()).isNotEqualTo(ChestType.SINGLE);
        assertThat(at(first).chest().type()).isEqualTo(at(second).chest().type().opposite());
    }

    @Test
    void breakingAHalfMakesTheOtherSingle() {
        Block right = chest(0, "south", "right");
        Block left = chest(1, "south", "left");
        blocks.remove(right);
        placement.removed(right);
        assertThat(at(left).chest()).isEqualTo(new ChestRules.Chest(ChestSide.SOUTH, ChestType.SINGLE));
        assertThat(link(left).size()).isEqualTo(18);
    }

    @Test
    void placementDirectionsMatchVanilla() {
        assertThat(ChestPlacement.side(0f)).isEqualTo(ChestSide.SOUTH);
        assertThat(ChestPlacement.side(90f)).isEqualTo(ChestSide.WEST);
        assertThat(ChestPlacement.side(180f)).isEqualTo(ChestSide.NORTH);
        assertThat(ChestPlacement.side(-90f)).isEqualTo(ChestSide.EAST);
        assertThat(ChestPlacement.of(org.bukkit.block.BlockFace.UP)).isNull();
    }

    @Test
    void rightClickOpensUnlessSneakingWithAnItemOrBlockedAbove() {
        Block b = chest(0, "south", "single");
        player.setSneaking(false);
        assertThat(service.use(b, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(service.use(b, player, EquipmentSlot.HAND, new ItemStack(Material.DIRT))).as("with a block in hand").isTrue();
        assertThat(opened).containsExactly(b, b);
        player.setSneaking(true);
        assertThat(service.use(b, player, EquipmentSlot.HAND, new ItemStack(Material.DIRT))).as("sneaking with an item places it").isFalse();
        assertThat(service.use(b, player, EquipmentSlot.HAND, ItemStack.empty())).as("sneaking with an empty hand opens").isTrue();
        assertThat(opened).hasSize(3);
        player.setSneaking(false);
        b.getRelative(0, 1, 0).setType(Material.STONE);
        assertThat(service.use(b, player, EquipmentSlot.HAND, ItemStack.empty())).as("a solid block above: nothing opens").isTrue();
        assertThat(opened).hasSize(3);
    }

    @Test
    void bothKindsAreDevices() {
        DeviceKind<?> chest = service.kinds().find("tfp:wood/chest/willow").orElseThrow();
        DeviceKind<?> trapped = service.kinds().find("tfp:wood/trapped_chest/willow").orElseThrow();
        assertThat(chest.slots()).isEqualTo(18);
        assertThat(trapped.slots()).isEqualTo(18);
        assertThat(chest.removal()).isEqualTo(DeviceKind.RemoveBehaviour.DROP);
        assertThat(chest.filter().canInsert(0, new ItemStack(Material.ANVIL))).isFalse();
        assertThat(ChestBlocks.isChestId("tfp:wood/chest/oak")).isTrue();
        assertThat(ChestBlocks.isChestId("tfp:wood/chest")).isFalse();
        assertThat(ChestBlocks.isChestId("tfp:wood/crate/oak")).isFalse();
        assertThat(ChestState.CODEC).isNotNull();
    }

    @Test
    void theScreenIsTwoOrFourRowsAndWritesBackWhenItCloses() {
        GuiService gui = new GuiService(server, scheduler, new AtomicLong(100_000)::get, true);
        ChestScreens screens = new ChestScreens(gui, scheduler, Messages.fromJar(), sizes::fitsChest);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(screens, plugin);
        Block right = chest(0, "south", "right");
        Block left = chest(1, "south", "left");
        GuiSession session = screens.open(player, link(left)).orElseThrow();
        assertThat(session.getInventory().getSize()).as("a double chest has four rows").isEqualTo(36);
        assertThat(screens.viewer(ChestLink.Key.of(right))).as("both halves are held").isPresent();
        PlayerMock other = server.addPlayer("Other");
        assertThat(screens.open(other, link(right))).as("one viewer at a time").isEmpty();
        session.getInventory().setItem(0, new ItemStack(Material.COBBLESTONE, 7));
        session.getInventory().setItem(20, new ItemStack(Material.DIRT, 1));
        player.closeInventory();
        assertThat(device(right, 0)).isEqualTo(new ItemStack(Material.COBBLESTONE, 7));
        assertThat(device(left, 2)).isEqualTo(new ItemStack(Material.DIRT, 1));
        assertThat(screens.viewer(ChestLink.Key.of(left))).isEmpty();
        Block single = chest(4, "south", "single");
        GuiSession two = screens.open(player, link(single)).orElseThrow();
        assertThat(two.getInventory().getSize()).as("a chest has two rows").isEqualTo(18);
        player.closeInventory();
        assertThat(screens.open(other, link(single))).as("free again after closing").isPresent();
    }

    @Test
    void aSittingCatAboveBlocksOpeningButAStandingOneDoesNot() {
        Block b = chest(0, "south", "single");
        player.setSneaking(false);
        org.bukkit.entity.Cat cat = world.spawn(new Location(world, 0.5, 65, 0.5), org.bukkit.entity.Cat.class);
        cat.setSitting(false);
        assertThat(service.use(b, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(opened).as("a standing cat does not block").hasSize(1);
        cat.setSitting(true);
        assertThat(service.use(b, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(opened).as("a sitting cat blocks").hasSize(1);
        cat.remove();
        assertThat(service.use(b, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(opened).hasSize(2);
    }

    @Test
    void theLidOpensWithTheScreenAndClosesWithIt() {
        GuiService gui = new GuiService(server, scheduler, new AtomicLong(100_000)::get, true);
        ChestLid lid = new ChestLid(blocks, scheduler, (b, s) -> blocks.place(b, s, false));
        ChestScreens screens = new ChestScreens(gui, scheduler, Messages.fromJar(), sizes::fitsChest, lid);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(screens, plugin);
        Block right = chest(0, "south", "right");
        Block left = chest(1, "south", "left");
        Block single = chest(4, "north", "single");
        assertThat(at(single).open()).isFalse();
        screens.open(player, link(left)).orElseThrow();
        assertThat(at(left).open()).as("both halves of a double chest").isTrue();
        assertThat(at(right).open()).isTrue();
        assertThat(at(left).chest()).as("facing and type are kept").isEqualTo(new ChestRules.Chest(ChestSide.SOUTH, ChestType.LEFT));
        assertThat(at(single).open()).as("another chest stays closed").isFalse();
        player.closeInventory();
        assertThat(at(left).open()).isFalse();
        assertThat(at(right).open()).isFalse();
        screens.open(player, link(single)).orElseThrow();
        assertThat(at(single).open()).isTrue();
        assertThat(at(single).state().properties()).containsEntry("open", "true");
        player.closeInventory();
        assertThat(at(single).open()).isFalse();
    }

    @Test
    void theLidIsCentredOnADoubleChest() {
        Block right = chest(0, "south", "right");
        Block left = chest(1, "south", "left");
        Location c = ChestLid.centre(List.of(ChestLink.Key.of(right), ChestLink.Key.of(left)));
        assertThat(c.getX()).isEqualTo(1.0);
        assertThat(c.getY()).isEqualTo(64.5);
        assertThat(c.getZ()).isEqualTo(0.5);
    }
}
