/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rack;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.rack.RackState;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-DECO-05 on MockBukkit with the in-memory CraftEngine fake: put and take on a tool rack (the four quadrants, one tool
 * each, swap, the displays), the bookshelf's six slots (front face only, books only, the occupancy states), the placement
 * facings and the support rule.
 */
class RackMockBukkitTest extends MockServerTest {
    static final String RACK = "tfp:wood/tool_rack/oak";
    static final String SHELF = "tfp:wood/bookshelf/oak";
    static final Set<Material> TOOLS = Set.of(Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE,
        Material.SPYGLASS);
    static final Set<Material> BOOKS = Set.of(Material.BOOK, Material.ENCHANTED_BOOK);

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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(RACK, SHELF, "tfp:wood/shelf/oak", "tfp:wood/lectern/oak");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final RackScheduler scheduler = new RackScheduler();
    private DeviceService service;
    private DeviceKind<RackState> rackKind;
    private DeviceKind<RackState> shelfKind;
    private RackInteractions interactions;
    private RackPlacement placement;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("rack");
        world.getChunkAt(0, 0).load();
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        rackKind = service.register(RackDevices.toolRack(s -> TOOLS.contains(s.getType()), ToolRackDisplays::clear));
        shelfKind = service.register(RackDevices.bookshelf(s -> BOOKS.contains(s.getType())));
        interactions = new RackInteractions(service, rackKind, shelfKind, blocks, scheduler, s -> TOOLS.contains(s.getType()),
            s -> BOOKS.contains(s.getType()), (b, s) -> blocks.place(b, s, false));
        placement = new RackPlacement(blocks, scheduler, (b, s) -> blocks.place(b, s, false), (n, face) -> n.getType().isSolid());
        player = server.addPlayer();
        player.teleport(new Location(world, 5.5, 64, 5.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private Block rack(int x, String facing) {
        Block b = world.getBlockAt(x, 64, 0);
        blocks.place(b, BlockStateRef.of(RACK).with("facing", facing), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        return b;
    }

    private Block shelf(int x, String facing) {
        Block b = world.getBlockAt(x, 65, 0);
        blocks.place(b, BlockStateRef.of(SHELF).with("facing", facing), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        return b;
    }

    private ItemStack slot(Block b, int slot) {
        var inspection = service.inspect(b).orElseThrow();
        assertThat(inspection.record()).as(inspection.problem()).isPresent();
        var record = inspection.record().orElseThrow();
        return record.slot(slot).isEmpty() ? null : stacks.decode(record.slot(slot));
    }

    private static List<ItemDisplay> displays(Block b) {
        List<ItemDisplay> out = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            out.addAll(ToolRackDisplays.displays(b, s));
        }
        return out;
    }

    private boolean clickRack(Block b, double x, double y, double z) {
        return interactions.click(player, b, ChestSide.SOUTH, new Vector(x, y, z));
    }

    // ---- the tool rack ---------------------------------------------------------------------------------

    @Test
    void fourToolsGoIntoTheFourQuadrantsOneEach() {
        Block b = rack(0, "north"); // x runs along the board
        Material[] tools = {Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_HOE};
        double[][] hits = {{0.25, 0.75, 0.9}, {0.75, 0.75, 0.9}, {0.25, 0.25, 0.9}, {0.75, 0.25, 0.9}};
        for (int i = 0; i < 4; i++) {
            player.getInventory().setItemInMainHand(new ItemStack(tools[i], 1));
            assertThat(clickRack(b, hits[i][0], hits[i][1], hits[i][2])).isTrue();
            assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.AIR);
        }
        for (int i = 0; i < 4; i++) {
            assertThat(slot(b, i).getType()).isEqualTo(tools[i]);
        }
        assertThat(displays(b)).hasSize(4);
    }

    @Test
    void oneToolFromAStackAndTheRestStays() {
        Block b = rack(0, "north");
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE, 3));
        assertThat(clickRack(b, 0.2, 0.8, 0.9)).isTrue();
        assertThat(slot(b, 0).getAmount()).isEqualTo(1);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    @Test
    void anOccupiedSlotSwapsWithTheToolInHand() {
        Block b = rack(0, "north");
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        clickRack(b, 0.2, 0.8, 0.9);
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL, 2));
        assertThat(clickRack(b, 0.2, 0.8, 0.9)).isTrue();
        assertThat(slot(b, 0).getType()).isEqualTo(Material.IRON_SHOVEL);
        // one shovel left in hand, the axe went to the inventory (TFC giveItemToPlayer)
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.IRON_SHOVEL);
        assertThat(player.getInventory().contains(Material.IRON_AXE)).isTrue();
    }

    @Test
    void anEmptyHandTakesTheToolIntoTheHand() {
        Block b = rack(0, "north");
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        clickRack(b, 0.8, 0.2, 0.9);
        assertThat(clickRack(b, 0.8, 0.2, 0.9)).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.IRON_AXE);
        assertThat(slot(b, 3)).isNull();
        assertThat(displays(b)).isEmpty();
    }

    @Test
    void aNonToolOnAnEmptySlotPassesAndOnAFullSlotTakesTheTool() {
        Block b = rack(0, "north");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT, 4));
        assertThat(clickRack(b, 0.2, 0.8, 0.9)).isFalse(); // TFC PASS_TO_DEFAULT_BLOCK_INTERACTION
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        clickRack(b, 0.2, 0.8, 0.9);
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT, 4));
        assertThat(clickRack(b, 0.2, 0.8, 0.9)).isTrue(); // canInsert false, slot full: the tool comes out
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.DIRT);
        assertThat(player.getInventory().contains(Material.IRON_AXE)).isTrue();
        assertThat(slot(b, 0)).isNull();
    }

    @Test
    void sneakingWithAnItemUsesTheItemNotTheRack() {
        Block b = rack(0, "north");
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        player.setSneaking(true);
        assertThat(clickRack(b, 0.2, 0.8, 0.9)).isFalse();
        assertThat(slot(b, 0)).isNull();
    }

    @Test
    void theKindTakesOnlyToolsOneEachAndDropsItsContents() {
        assertThat(rackKind.slots()).isEqualTo(4);
        assertThat(rackKind.slotLimit(0)).isEqualTo(1);
        assertThat(rackKind.filter().canInsert(0, new ItemStack(Material.IRON_AXE))).isTrue();
        assertThat(rackKind.filter().canInsert(0, new ItemStack(Material.DIRT))).isFalse();
        assertThat(rackKind.removal()).isEqualTo(DeviceKind.RemoveBehaviour.DROP);
        assertThat(shelfKind.slots()).isEqualTo(6);
        assertThat(shelfKind.removal()).isEqualTo(DeviceKind.RemoveBehaviour.DROP);
        assertThat(rackKind.covers(RACK)).isTrue();
        assertThat(rackKind.covers("tfp:wood/shelf/oak")).isFalse();
    }

    @Test
    void breakingARackClearsItsDisplays() {
        Block b = rack(0, "south");
        player.getInventory().setItemInMainHand(new ItemStack(Material.SPYGLASS));
        clickRack(b, 0.2, 0.8, 0.1);
        assertThat(displays(b)).hasSize(1);
        ToolRackDisplays.clear(b);
        assertThat(displays(b)).isEmpty();
    }

    @Test
    void displayPositionsAreTfcsRendererTable() {
        // facing south: the board is on the north edge, items 3/32 from it; slot 0 high left column, slot 3 low right column
        assertThat(ToolRackDisplays.location(ChestSide.SOUTH, 0)).containsExactly(0.25f, 0.78125f, 0.09375f);
        assertThat(ToolRackDisplays.location(ChestSide.SOUTH, 3)).containsExactly(0.75f, 0.28125f, 0.09375f);
        assertThat(ToolRackDisplays.location(ChestSide.NORTH, 1)).containsExactly(0.75f, 0.78125f, 0.90625f);
        assertThat(ToolRackDisplays.location(ChestSide.WEST, 2)).containsExactly(0.90625f, 0.28125f, 0.25f);
        assertThat(ToolRackDisplays.location(ChestSide.EAST, 3)).containsExactly(0.09375f, 0.28125f, 0.75f);
        assertThat(ToolRackDisplays.angle(ChestSide.SOUTH)).isEqualTo(180f);
        assertThat(ToolRackDisplays.angle(ChestSide.WEST)).isEqualTo(90f);
        assertThat(ToolRackDisplays.angle(ChestSide.NORTH)).isEqualTo(0f);
        assertThat(ToolRackDisplays.angle(ChestSide.EAST)).isEqualTo(270f);
    }

    // ---- the bookshelf ---------------------------------------------------------------------------------

    private boolean clickShelf(Block b, ChestSide face, double x, double y, double z) {
        return interactions.click(player, b, face, new Vector(x, y, z));
    }

    private BlockStateRef state(Block b) {
        return blocks.stateAt(b).orElseThrow();
    }

    @Test
    void aBookGoesIntoTheHitSlotAndSetsItsOccupiedState() {
        Block b = shelf(0, "south");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BOOK, 3));
        assertThat(clickShelf(b, ChestSide.SOUTH, 0.5, 0.1, 1.0)).isTrue(); // bottom middle: slot 4
        assertThat(slot(b, 4).getType()).isEqualTo(Material.BOOK);
        assertThat(state(b).properties()).containsEntry("slot_4_occupied", "true").containsEntry("slot_0_occupied", "false");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(clickShelf(b, ChestSide.SOUTH, 0.1, 0.9, 1.0)).isTrue(); // top left: slot 0
        assertThat(state(b).properties()).containsEntry("slot_0_occupied", "true").containsEntry("slot_4_occupied", "true");
        assertThat(state(b).properties().get("facing")).isEqualTo("south");
    }

    @Test
    void anEmptyHandTakesTheBookOutAndClearsTheState() {
        Block b = shelf(0, "south");
        player.getInventory().setItemInMainHand(new ItemStack(Material.ENCHANTED_BOOK));
        clickShelf(b, ChestSide.SOUTH, 0.9, 0.9, 1.0); // top right: slot 2
        assertThat(state(b).properties()).containsEntry("slot_2_occupied", "true");
        assertThat(clickShelf(b, ChestSide.SOUTH, 0.9, 0.9, 1.0)).isTrue();
        assertThat(player.getInventory().contains(Material.ENCHANTED_BOOK)).isTrue();
        assertThat(slot(b, 2)).isNull();
        assertThat(state(b).properties()).containsEntry("slot_2_occupied", "false");
    }

    @Test
    void aBookInHandOnAnOccupiedSlotTakesTheBookOut() {
        Block b = shelf(0, "south");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BOOK, 2));
        clickShelf(b, ChestSide.SOUTH, 0.5, 0.9, 1.0);
        assertThat(clickShelf(b, ChestSide.SOUTH, 0.5, 0.9, 1.0)).isTrue();
        assertThat(slot(b, 1)).isNull();
        assertThat(player.getInventory().getItemInMainHand().getAmount() + countInInventory(Material.BOOK)).isGreaterThanOrEqualTo(2);
    }

    private int countInInventory(Material m) {
        int n = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && s.getType() == m) {
                n += s.getAmount();
            }
        }
        return n;
    }

    @Test
    void onlyTheFrontFaceAndOnlyBooksCount() {
        Block b = shelf(0, "south");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BOOK));
        assertThat(clickShelf(b, ChestSide.NORTH, 0.5, 0.5, 0.0)).isTrue(); // a book on another face is eaten, nothing goes in
        assertThat(slot(b, 0)).isNull();
        assertThat(state(b).properties()).doesNotContainValue("true");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));
        assertThat(clickShelf(b, ChestSide.SOUTH, 0.5, 0.5, 1.0)).isTrue(); // front face, no book anywhere: handled, nothing changes
        assertThat(slot(b, 1)).isNull();
        assertThat(clickShelf(b, ChestSide.NORTH, 0.5, 0.5, 0.0)).isFalse(); // another face with no book: the click goes on
        assertThat(clickShelf(b, null, 0.5, 1.0, 0.5)).isFalse();
    }

    @Test
    void creativeKeepsTheBook() {
        Block b = shelf(0, "south");
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BOOK, 1));
        clickShelf(b, ChestSide.SOUTH, 0.1, 0.1, 1.0);
        assertThat(slot(b, 3).getType()).isEqualTo(Material.BOOK);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
    }

    @Test
    void aWestFacingShelfReadsTheZAxis() {
        Block b = shelf(0, "west");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BOOK, 2));
        clickShelf(b, ChestSide.WEST, 0.0, 0.9, 0.9); // z grows to the right of a west facing shelf: top right
        assertThat(state(b).properties()).containsEntry("slot_2_occupied", "true");
    }

    // ---- placement and support -------------------------------------------------------------------------

    @Test
    void lecternAndBookshelfFaceTheBackOfThePlacer() {
        Block b = world.getBlockAt(2, 64, 2);
        RackBlocks.Found lectern = new RackBlocks.Found("tfp:wood/lectern/oak", ChestSide.NORTH, new boolean[6]);
        // yaw 0 looks south: the block faces north, back at the player
        assertThat(placement.decide(b, lectern, 0f, 0f, null, null)).contains(ChestSide.NORTH);
        assertThat(placement.decide(b, lectern, 90f, 0f, null, null)).contains(ChestSide.EAST);
        assertThat(placement.decide(b, lectern, 180f, 0f, null, null)).contains(ChestSide.SOUTH);
        assertThat(placement.decide(b, lectern, 270f, 0f, null, null)).contains(ChestSide.WEST);
        assertThat(RackPlacement.horizontal(-90f)).isEqualTo(ChestSide.EAST);
    }

    @Test
    void aRackNeedsASturdyBlockOnTheSideItIsPlacedAgainst() {
        Block b = world.getBlockAt(3, 64, 3);
        RackBlocks.Found rack = new RackBlocks.Found(RACK, ChestSide.NORTH, new boolean[6]);
        assertThat(placement.decide(b, rack, 0f, 0f, null, null)).isEmpty(); // open air all around
        world.getBlockAt(3, 64, 4).setType(Material.STONE); // a wall to the south, the player looks south
        assertThat(placement.decide(b, rack, 0f, 0f, null, null)).contains(ChestSide.NORTH);
        // the next nearest looking direction is tried: west is nearest after south at yaw 30, but only the south wall exists
        assertThat(placement.decide(b, rack, 30f, 0f, null, null)).contains(ChestSide.NORTH);
    }

    @Test
    void aRackIsNotPlacedOnTheFaceOfARackThatFacesIt() {
        Block against = rack(6, "south");
        Block b = world.getBlockAt(6, 64, 1);
        world.getBlockAt(6, 64, 2).setType(Material.STONE);
        RackBlocks.Found rack = new RackBlocks.Found(RACK, ChestSide.NORTH, new boolean[6]);
        assertThat(placement.decide(b, rack, 0f, 0f, against, ChestSide.SOUTH)).isEmpty();
        assertThat(placement.decide(b, rack, 0f, 0f, against, ChestSide.NORTH)).contains(ChestSide.NORTH);
    }

    @Test
    void aShelfFacesTheSideOfItsSupport() {
        Block b = world.getBlockAt(8, 64, 3);
        RackBlocks.Found shelf = new RackBlocks.Found("tfp:wood/shelf/oak", ChestSide.NORTH, new boolean[6]);
        assertThat(placement.decide(b, shelf, 0f, 0f, null, null)).isEmpty();
        world.getBlockAt(8, 64, 2).setType(Material.STONE); // behind the player: TFC's helper takes the opposite of the look
        assertThat(placement.decide(b, shelf, 0f, 0f, null, null)).contains(ChestSide.NORTH);
    }

    @Test
    void theSupportRuleRemovesARackWhoseBackIsGone() {
        RackSupport rule = new RackSupport(id -> id.equals("tfp:solid"));
        BlockStateRef north = BlockStateRef.of(RACK).with("facing", "north"); // hangs on the south block
        Neighbourhood<BlockStateRef> held = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? north
            : dx == 0 && dy == 0 && dz == 1 ? BlockStateRef.of("tfp:solid") : BlockStateRef.of("minecraft:air"));
        assertThat(rule.decide(held)).isNull();
        Neighbourhood<BlockStateRef> gone = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? north
            : BlockStateRef.of("minecraft:air"));
        assertThat(rule.decide(gone)).isEqualTo(RackSupport.AIR);
        // a shelf facing north needs the north block (TFC canSurvive: pos.relative(facing))
        BlockStateRef shelf = BlockStateRef.of("tfp:wood/shelf/oak").with("facing", "north");
        Neighbourhood<BlockStateRef> shelfHeld = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? shelf
            : dx == 0 && dy == 0 && dz == -1 ? BlockStateRef.of("tfp:solid") : BlockStateRef.of("minecraft:air"));
        assertThat(rule.decide(shelfHeld)).isNull();
        assertThat(rule.decide(Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? shelf
            : BlockStateRef.of("minecraft:air")))).isEqualTo(RackSupport.AIR);
        // an unknown neighbour keeps the block
        assertThat(rule.decide(Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? north : null))).isNull();
        assertThat(rule.appliesTo(BlockStateRef.of("tfp:wood/lectern/oak"))).isFalse();
        assertThat(rule.blockIds()).hasSize(40).contains(RACK, "tfp:wood/shelf/willow");
        assertThat(RackSupport.solidId("tfp:wood/tool_rack/oak")).isFalse();
        assertThat(RackSupport.solidId("minecraft:stone")).isTrue();
    }

    @Test
    void parsingReadsFacingAndSlots() {
        RackBlocks.Found f = RackBlocks.parse(BlockStateRef.of(SHELF).with("facing", "east").with("slot_3_occupied", "true")).orElseThrow();
        assertThat(f.facing()).isEqualTo(ChestSide.EAST);
        assertThat(f.occupied()).containsExactly(false, false, false, true, false, false);
        assertThat(f.state().properties()).containsEntry("slot_3_occupied", "true").containsEntry("facing", "east");
        assertThat(RackBlocks.parse(BlockStateRef.of("tfp:wood/chest/oak"))).isEmpty();
        assertThat(RackBlocks.parse(BlockStateRef.of("tfp:wood/lectern/oak")).orElseThrow().facing()).isEqualTo(ChestSide.NORTH);
    }
}
