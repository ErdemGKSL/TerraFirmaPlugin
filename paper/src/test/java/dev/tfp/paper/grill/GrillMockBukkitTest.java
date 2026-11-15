/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.core.grill.GrillSlots;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.firepit.FirepitEffects;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-08 on MockBukkit with fakes of the CraftEngine adapter (a placer that removes the old block entity and creates
 * the new one, as CraftEngine does) and the world effects: the firepit to grill conversion keeps the fuel and the fire,
 * drops the input, outputs and ash; raw beef cooks on the grill with the wood grilled trait and shows on top; TFC's
 * useItemOn order (slot insert by where the click lands, sneak extract, ash, taking the grill off with the burn, fuel below
 * 0.6, the screen); the conversion back keeps the fuel too.
 *
 * <p>Stand-ins: oak log = TFC oak logs fuel, beef = raw beef (capacity 1, recipe 200 C, result cooked beef), gunpowder =
 * wood ash, iron bars = the wrought iron grill, iron shovel = a shovel.
 */
class GrillMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final Fuel OAK = FuelTable.byId("oak_logs").orElseThrow();
    static final NamespacedKey GRILLED = new NamespacedKey("test", "grilled");

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

    static final class Effects implements GrillEffects {
        FakeCustomBlocks world;
        final List<Sound> sounds = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Long> callbacks = new ArrayList<>();
        final List<Douse> doused = new ArrayList<>();
        final List<Float> burns = new ArrayList<>();
        final List<Block> toFirepit = new ArrayList<>();
        final Set<Block> queued = new HashSet<>();
        List<ItemStack> shown = List.of();
        int cooled;
        int placeSounds;
        int opened;
        int smoke;

        @Override
        public void show(Block block, BlockStateRef state) {
            world.place(block, state, false);
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void smoke(Block block, int smokeLevel) {
            smoke++;
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void useDouse(Player player, EquipmentSlot hand, Douse douse) {
            doused.add(douse);
        }

        @Override
        public void callbackIn(Block block, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public void breakBlock(Block block) {
        }

        @Override
        public void items(Block block, List<ItemStack> grill) {
            shown = List.copyOf(grill);
        }

        @Override
        public void burn(Player player, float amount) {
            burns.add(amount);
        }

        @Override
        public void cool(Block block) {
            cooled++;
        }

        @Override
        public void placeSound(Block block) {
            placeSounds++;
        }

        @Override
        public void openScreen(Player player, Block block) {
            opened++;
        }

        final List<Runnable[]> afterConversion = new ArrayList<>();

        @Override
        public boolean toFirepit(Block block, Player player, Runnable atBlock, Runnable atPlayer, Runnable playerGone) {
            if (!queued.add(block)) {
                return false;
            }
            toFirepit.add(block);
            afterConversion.add(new Runnable[] {atBlock, atPlayer, playerGone});
            return true;
        }

        /** The queued take-off as PaperGrillEffects runs it: the conversion, then the hand-outs only when it worked. */
        boolean runConversion(GrillConversion conversion, Block block) {
            queued.remove(block);
            Runnable[] after = afterConversion.removeFirst();
            if (!conversion.toFirepit(block)) {
                return false;
            }
            after[0].run();
            after[1].run();
            return true;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(FirepitDevice.ID, GrillDevice.ID);
    private final FakeCustomItems items = new FakeCustomItems()
        .define(GrillDevice.GRILL_ITEM, n -> new ItemStack(Material.IRON_BARS, n));
    private final MemoryStore store = new MemoryStore();
    private final TableCodec codec = new TableCodec();
    private final Effects pitEffects = new Effects();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private GrillSlots.Point hit;
    private DeviceService service;
    private FirepitDevice firepit;
    private GrillDevice grill;
    private GrillConversion conversion;
    private World world;
    private Block block;
    private PlayerMock player;

    {
        pitEffects.world = blocks;
        effects.world = blocks;
    }

    static Optional<Fuel> fuel(ItemStack s) {
        return s.getType() == Material.OAK_LOG ? Optional.of(OAK) : Optional.empty();
    }

    static final FirepitDevice.Cooker COOKER = new FirepitDevice.Cooker() {
        @Override
        public float recipeTemperature(ItemStack stack) {
            return stack.getType() == Material.BEEF ? 200f : Float.NaN;
        }

        @Override
        public Optional<ItemStack> convert(ItemStack heated, float temperature) {
            if (heated.getType() != Material.BEEF || temperature < 200f) {
                return Optional.empty();
            }
            ItemStack out = heated.withType(Material.COOKED_BEEF);
            out.setAmount(1);
            return Optional.of(out);
        }
    };

    static FirepitEffects.Douse douse(ItemStack s) {
        return s.getType() == Material.IRON_SHOVEL ? FirepitEffects.Douse.SHOVEL : FirepitEffects.Douse.NONE;
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("grill");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, items,
            Logger.getLogger("test"));
        RandomGenerator ash = () -> 0L; // every ash draw lands
        firepit = new FirepitDevice(new FirepitDevice.Services(GrillMockBukkitTest::fuel,
            s -> s.getType() == Material.OAK_LOG, s -> s.getType() == Material.BEEF || s.getType() == Material.COOKED_BEEF ? 1.0 : 0.0,
            COOKER, GrillMockBukkitTest::douse, b -> false, b -> true, n -> new ItemStack(Material.GUNPOWDER, n), () -> ash,
            HeatConfig.DEFAULT, pitEffects), codec);
        grill = new GrillDevice(new GrillDevice.Services(GrillMockBukkitTest::fuel, s -> s.getType() == Material.OAK_LOG,
            s -> s.getType() == Material.BEEF || s.getType() == Material.COOKED_BEEF ? 1.0 : 0.0, COOKER,
            s -> {
                s.editMeta(m -> m.getPersistentDataContainer().set(GRILLED, PersistentDataType.BYTE, (byte) 1));
                return s;
            },
            GrillMockBukkitTest::douse, b -> false, b -> true, n -> new ItemStack(Material.GUNPOWDER, n),
            () -> new ItemStack(Material.IRON_BARS), (p, b) -> Optional.ofNullable(hit), () -> ash, HeatConfig.DEFAULT,
            effects), codec);
        service.register(firepit.kind());
        service.register(grill.kind());
        conversion = new GrillConversion(service, blocks, this::place, effects);
        block = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** CraftEngine replacing a custom block: the old block entity's removal, then a fresh record for the new one. */
    private boolean place(Block at, BlockStateRef state) {
        if (!blocks.exists(state.id())) {
            return false;
        }
        Optional<BlockStateRef> old = blocks.stateAt(at);
        if (old.isPresent()) {
            DataMap data = store.data.remove(FakeCustomBlocks.at(at));
            service.removed(at.getWorld(), at.getX(), at.getY(), at.getZ(), old.get().id(), data == null ? new DataMap() : data);
        }
        blocks.place(at, state, true);
        store.data.put(FakeCustomBlocks.at(at), service.created(state.id()).orElse(new DataMap()));
        return true;
    }

    /** A lit firepit with {@code logs} oak logs in its fuel slots (one taken to light it) and raw beef in its input. */
    private void litFirepit(int logs) {
        blocks.place(block, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "z"),
            true);
        store.data.put(FakeCustomBlocks.at(block), new DataMap());
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < logs; i++) {
            stacks.add(new ItemStack(Material.OAK_LOG));
        }
        firepit.build(block, stacks, service::neighbourChanged);
        service.edit(block, firepit.kind(), (r, now) -> r.withSlot(FirepitSim.SLOT_ITEM_INPUT, codec.encode(new ItemStack(Material.BEEF))));
    }

    private DeviceRecord<FirepitData> grillRecord() {
        return grill.kind().codec().decode(store.read(block).orElseThrow());
    }

    private DeviceRecord<FirepitData> firepitRecord() {
        return firepit.kind().codec().decode(store.read(block).orElseThrow());
    }

    private ItemStack slot(DeviceRecord<FirepitData> r, int i) {
        return codec.decode(r.slot(i));
    }

    private List<Item> drops() {
        return new ArrayList<>(world.getEntitiesByClass(Item.class));
    }

    private boolean click(ItemStack held, GrillSlots.Point at) {
        hit = at;
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(block, player, EquipmentSlot.HAND, held);
    }

    private static GrillSlots.Point top(int slot) {
        GrillSlots.Point c = GrillSlots.center(slot);
        return new GrillSlots.Point(c.x(), GrillSlots.TOP, c.z());
    }

    private void toGrill() {
        assertThat(conversion.toGrill(block)).isTrue();
    }

    @Test
    void convertingAFirepitKeepsTheFuelAndTheFireAndDropsTheRest() {
        litFirepit(3);
        clock.addAndGet(150);
        service.catchUp(block);
        DeviceRecord<FirepitData> before = firepitRecord();
        assertThat(before.state().lit()).isTrue();
        assertThat(before.state().ash()).isPositive();
        int ash = before.state().ash();

        toGrill();

        assertThat(blocks.stateAt(block).orElseThrow()).isEqualTo(BlockStateRef.parse("tfp:grill[axis=z,lit=true,smoke_level=0]"));
        DeviceRecord<FirepitData> after = grillRecord();
        assertThat(after.slots()).hasSize(GrillSim.SLOTS);
        assertThat(after.state().heat()).isEqualTo(before.state().heat()); // burn ticks, burn temperature, temperature, air
        assertThat(after.state().lit()).isTrue();
        assertThat(after.state().ash()).isZero();
        for (int i = 0; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            assertThat(slot(after, i)).as("fuel slot " + i).isEqualTo(slot(before, i));
        }
        for (int i = GrillSim.SLOT_EXTRA_INPUT_START; i <= GrillSim.SLOT_EXTRA_INPUT_END; i++) {
            assertThat(slot(after, i).isEmpty()).isTrue();
        }
        // TFC ejectMainInventory: the heated raw beef from the input drops; the old block entity drops its ash
        assertThat(drops()).extracting(e -> e.getItemStack().getType()).containsExactly(Material.BEEF);
        assertThat(pitEffects.spawned).extracting(ItemStack::getAmount).containsExactly(ash);
        assertThat(effects.placeSounds).isEqualTo(1);
        // the grill's own handler ran (neighbour update): burn-out callback queued for the remaining fuel
        assertThat(effects.callbacks).isNotEmpty();
    }

    @Test
    void aGrillOnAnUnloadedBlockLeavesTheFirepitAsItWas() {
        FakeCustomBlocks noGrill = new FakeCustomBlocks().known(FirepitDevice.ID);
        GrillConversion refused = new GrillConversion(service, blocks, (at, state) -> noGrill.exists(state.id()) && place(at, state),
            effects);
        litFirepit(3);
        DeviceRecord<FirepitData> before = firepitRecord();
        assertThat(refused.toGrill(block)).isFalse();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(FirepitDevice.ID);
        assertThat(firepitRecord().slots()).isEqualTo(before.slots());
        assertThat(conversion.toFirepit(block)).as("not a grill").isFalse();
    }

    @Test
    void rawBeefCooksInItsSlotWithTheWoodGrilledTraitAndShowsOnTop() {
        litFirepit(3);
        toGrill();
        long converted = clock.get();
        assertThat(click(new ItemStack(Material.BEEF, 3), top(5))).isTrue();
        assertThat(slot(grillRecord(), 5).getType()).isEqualTo(Material.BEEF);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(effects.shown.get(1).getType()).isEqualTo(Material.BEEF);
        assertThat(click(new ItemStack(Material.BEEF, 2), top(8))).isTrue();
        assertThat(slot(grillRecord(), 8).getType()).isEqualTo(Material.BEEF);

        clock.addAndGet(3000);
        service.catchUp(block);
        DeviceRecord<FirepitData> r = grillRecord();
        for (int s : new int[] {5, 8}) {
            ItemStack cooked = slot(r, s);
            assertThat(cooked.getType()).as("slot " + s).isEqualTo(Material.COOKED_BEEF);
            assertThat(cooked.getItemMeta().getPersistentDataContainer().has(GRILLED)).isTrue();
            assertThat(HeatPdc.read(cooked)).isPresent();
        }
        assertThat(clock.get() - converted).isGreaterThan(200);
        // the next access redraws the items on top
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(effects.shown.get(1).getType()).isEqualTo(Material.COOKED_BEEF);
        assertThat(effects.shown.get(4).getType()).isEqualTo(Material.COOKED_BEEF);
        assertThat(effects.shown.get(0).isEmpty()).isTrue();
    }

    @Test
    void anEmptyHandTakesAnItemOnlyWhenSneakingOtherwiseTheScreenOpens() {
        litFirepit(2);
        toGrill();
        click(new ItemStack(Material.BEEF), top(6));
        assertThat(click(ItemStack.empty(), top(6))).isTrue();
        assertThat(effects.opened).isEqualTo(1); // still valid there, not sneaking: TFC opens the menu
        assertThat(slot(grillRecord(), 6).getType()).isEqualTo(Material.BEEF);
        player.setSneaking(true);
        assertThat(click(ItemStack.empty(), top(6))).isTrue();
        assertThat(effects.given).extracting(ItemStack::getType).containsExactly(Material.BEEF);
        assertThat(slot(grillRecord(), 6).isEmpty()).isTrue();
        // a click between the slots with an item that fits: the screen
        player.setSneaking(false);
        assertThat(click(new ItemStack(Material.BEEF), new GrillSlots.Point(0.5, GrillSlots.TOP, 0.2))).isTrue();
        assertThat(effects.opened).isEqualTo(2);
    }

    @Test
    void fuelGoesInOnlyOnAClickBelowTheGrill() {
        litFirepit(1);
        toGrill();
        assertThat(click(new ItemStack(Material.OAK_LOG, 4), top(4))).isTrue();
        assertThat(effects.opened).isEqualTo(1); // from above: not a heatable, not low, so the screen
        assertThat(slot(grillRecord(), 0).isEmpty()).isTrue();
        assertThat(click(new ItemStack(Material.OAK_LOG, 4), new GrillSlots.Point(0.0, 0.3, 0.5))).isTrue();
        assertThat(slot(grillRecord(), 0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(3);
        assertThat(effects.sounds).contains(FirepitEffects.Sound.LOG);
    }

    @Test
    void takingALitGrillOffBurnsAndTurnsItBackIntoAFirepitWithItsFuel() {
        litFirepit(3);
        toGrill();
        click(new ItemStack(Material.BEEF), top(7));
        DeviceRecord<FirepitData> before = grillRecord();
        player.setSneaking(true);
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        // nothing is handed out before the conversion has happened
        assertThat(effects.given).isEmpty();
        assertThat(effects.burns).isEmpty();
        // a second click before the conversion runs queues nothing more
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(effects.toFirepit).hasSize(1);

        assertThat(effects.runConversion(conversion, block)).isTrue();
        assertThat(effects.given).extracting(ItemStack::getType).containsExactly(Material.IRON_BARS);
        assertThat(effects.burns).containsExactly(1f);
        assertThat(effects.cooled).isEqualTo(1);
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(FirepitDevice.ID);
        DeviceRecord<FirepitData> pit = firepitRecord();
        assertThat(pit.slots()).hasSize(FirepitSim.SLOTS);
        assertThat(pit.state().heat()).isEqualTo(before.state().heat());
        for (int i = 0; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            assertThat(slot(pit, i)).isEqualTo(slot(before, i));
        }
        assertThat(drops()).extracting(e -> e.getItemStack().getType()).contains(Material.BEEF);
        assertThat(effects.shown).isEmpty(); // the drawn items were removed with the grill
    }

    @Test
    void aRefusedTakeOffKeepsTheGrillAndGivesNothing() {
        litFirepit(3);
        toGrill();
        DeviceRecord<FirepitData> before = grillRecord();
        // tfp:firepit is not a loaded custom block: CraftEngine refuses the placement
        GrillConversion refused = new GrillConversion(service, blocks,
            (at, state) -> !state.id().equals(FirepitDevice.ID) && place(at, state), effects);
        player.setSneaking(true);
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(effects.runConversion(refused, block)).isFalse();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(GrillDevice.ID);
        assertThat(grillRecord().slots()).isEqualTo(before.slots());
        assertThat(effects.given).isEmpty();
        assertThat(effects.burns).isEmpty();
        assertThat(effects.cooled).isZero();
        // the next take-off can be queued again
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(effects.toFirepit).hasSize(2);
    }

    @Test
    void anUnlitGrillGivesItsAshFirstAndTheShovelDousesAWarmOne() {
        litFirepit(1);
        toGrill();
        clock.addAndGet(50);
        assertThat(click(new ItemStack(Material.IRON_SHOVEL), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(grillRecord().state().lit()).isFalse();
        assertThat(effects.doused).containsExactly(FirepitEffects.Douse.SHOVEL);
        assertThat(effects.sounds).contains(FirepitEffects.Sound.EXTINGUISH);
        // the firepit's ash dropped at the conversion; build some on the grill
        service.edit(block, grill.kind(), (r, now) -> r.withState(r.state().withAsh(3)));
        player.setSneaking(true);
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(effects.given).extracting(ItemStack::getAmount).containsExactly(3);
        assertThat(grillRecord().state().ash()).isZero();
        assertThat(effects.burns).isEmpty();
        assertThat(effects.toFirepit).isEmpty();
        // no ash, unlit: the grill comes off without a burn
        assertThat(click(ItemStack.empty(), new GrillSlots.Point(0.05, 0.3, 0.05))).isTrue();
        assertThat(effects.toFirepit).hasSize(1);
        assertThat(effects.burns).isEmpty();
    }

    @Test
    void theGrillItemOnAFirepitConvertsItAndIsUsedUp() {
        litFirepit(2);
        GrillListener listener = new GrillListener(blocks, items, new dev.tfp.paper.grill.TickScheduler(), conversion);
        ItemStack held = items.create(GrillDevice.GRILL_ITEM, 1).orElseThrow();
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, block, BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(GrillDevice.ID);
        assertThat(player.getInventory().getItemInMainHand().isEmpty()).isTrue();
        // the off-hand event of the same click is cancelled too
        PlayerInteractEvent off = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP,
            EquipmentSlot.OFF_HAND);
        listener.onInteract(off);
        assertThat(off.isCancelled()).isTrue();

        // a sneaking click or another item is not the conversion
        Block other = world.getBlockAt(5, 64, 5);
        blocks.place(other, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "x"), true);
        player.setSneaking(true);
        PlayerInteractEvent sneak = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            items.create(GrillDevice.GRILL_ITEM, 1).orElseThrow(), other, BlockFace.UP, EquipmentSlot.HAND);
        server.getScheduler().performOneTick();
        listener.onInteract(sneak);
        assertThat(sneak.isCancelled()).isFalse();
        assertThat(blocks.stateAt(other).orElseThrow().id()).isEqualTo(FirepitDevice.ID);
    }

    @Test
    void aStrongFireStartLightsAnUnlitGrill() {
        litFirepit(3);
        toGrill();
        player.setSneaking(false);
        clock.addAndGet(10);
        service.edit(block, grill.kind(), (r, now) -> r.withState(FirepitSim.extinguish(r.state())));
        assertThat(grillRecord().state().lit()).isFalse();
        assertThat(grill.light(block, service::neighbourChanged)).isTrue();
        assertThat(grillRecord().state().lit()).isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().properties()).containsEntry("lit", "true");
    }

    @Test
    void theScreenLinkReadsCatchesUpAndCommitsTheDevice() {
        litFirepit(3);
        toGrill();
        DeviceGrillLink link = new DeviceGrillLink(service, grill, block, b -> true);
        clock.addAndGet(100);
        var shown = link.snapshot().orElseThrow();
        assertThat(shown.temperature()).isEqualTo(grillRecord().state().temperature()).isPositive();
        List<ItemStack> slots = new ArrayList<>(shown.slots());
        slots.set(3, ItemStack.empty());
        slots.set(0, ItemStack.empty());
        slots.set(2, new ItemStack(Material.OAK_LOG)); // TFC's cascade moves it down to slot 0
        slots.set(4, new ItemStack(Material.BEEF));
        var after = link.commit(slots).orElseThrow();
        assertThat(after.slot(0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(after.slot(2).isEmpty()).isTrue();
        assertThat(slot(grillRecord(), 4).getType()).isEqualTo(Material.BEEF);
        assertThat(effects.shown.getFirst().getType()).isEqualTo(Material.BEEF);
        assertThat(new DeviceGrillLink(service, grill, block, b -> false).snapshot()).as("another region").isEmpty();
        blocks.remove(block);
        assertThat(link.snapshot()).as("gone").isEmpty();
    }

    @Test
    void isItemValidIsTfcs() {
        assertThat(grill.isItemValid(3, new ItemStack(Material.OAK_LOG))).isTrue();
        assertThat(grill.isItemValid(2, new ItemStack(Material.OAK_LOG))).isFalse();
        assertThat(grill.isItemValid(4, new ItemStack(Material.OAK_LOG))).isFalse();
        for (int s = 4; s <= 8; s++) {
            assertThat(grill.isItemValid(s, new ItemStack(Material.BEEF))).isTrue();
        }
        assertThat(grill.isItemValid(3, new ItemStack(Material.BEEF))).isFalse();
        assertThat(grill.kind().slots()).isEqualTo(9);
        for (int s = 0; s < 9; s++) {
            assertThat(grill.kind().slotLimit(s)).isEqualTo(1);
        }
    }
}
