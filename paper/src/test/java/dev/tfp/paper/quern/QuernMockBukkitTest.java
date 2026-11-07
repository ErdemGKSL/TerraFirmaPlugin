/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.quern.QuernState;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-04 on MockBukkit, with fakes of the CraftEngine adapter, the recipe runtime and the world effects: putting a
 * handstone and grain on the quern by clicking where TFC's selection says, turning the handle, the lazy finish at tick 90
 * (from the scheduled callback and from a later click), the handstone wearing out, taking the handstone back with its input,
 * and the output overflow.
 *
 * <p>Stand-ins: flint = handstone ({@code tfc:quern_handstones}), wheat = wheat grain, sugar = wheat flour (one per grain),
 * the fake damage = the handstone's durability.
 */
class QuernMockBukkitTest extends MockServerTest {
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

    /** Records every world effect; the handstone breaks after {@code durability} grinds. */
    static final class Effects implements QuernEffects {
        View shown;
        boolean hidden;
        int started;
        int spins;
        int finished;
        final List<ItemStack> broken = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Long> finishAt = new ArrayList<>();
        int durability = 250;
        int damage;

        @Override
        public void show(Block block, View view) {
            shown = view;
        }

        @Override
        public void hide(Block block) {
            hidden = true;
        }

        @Override
        public void started(Block block, ItemStack input) {
            started++;
        }

        @Override
        public void spin(Block block) {
            spins++;
        }

        @Override
        public void finishAt(Block block, long ticks) {
            finishAt.add(ticks);
        }

        @Override
        public void finished(Block block, ItemStack brokenHandstone) {
            finished++;
            if (!StackCodec.isEmpty(brokenHandstone)) {
                broken.add(brokenHandstone);
            }
        }

        @Override
        public ItemStack damage(ItemStack handstone) {
            damage++;
            return damage >= durability ? ItemStack.empty() : handstone;
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(QuernDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(1000);
    private DeviceService service;
    private World world;
    private Block quern;
    private PlayerMock player;

    static Optional<UnaryOperator<ItemStack>> recipe(ItemStack stack) {
        if (StackCodec.isEmpty(stack) || stack.getType() != Material.WHEAT) {
            return Optional.empty();
        }
        return Optional.of(in -> new ItemStack(Material.SUGAR));
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("quern");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        service.register(QuernDevice.kind(new QuernDevice.Services(QuernMockBukkitTest::recipe,
            s -> s.getType() == Material.FLINT, ItemStack::isSimilar, (a, b) -> { }, effects)));
        quern = world.getBlockAt(0, 64, 0);
        blocks.place(quern, QuernDevice.state(false), true);
        store.data.put(FakeCustomBlocks.at(quern), new DataMap()); // CraftEngine creates the block entity
        player = server.addPlayer();
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<QuernState> record() {
        DeviceKind<QuernState> kind = (DeviceKind<QuernState>) service.kinds().find(QuernDevice.ID).orElseThrow();
        return kind.codec().decode(store.read(quern).orElseThrow());
    }

    private ItemStack slot(int slot) {
        return stacks.decode(record().slot(slot));
    }

    /** Puts the eye above pixel (x, z) of the quern, looking straight down. */
    private void lookDownAt(double px, double pz) {
        Location feet = new Location(world, quern.getX() + px / 16, quern.getY() + 2.2 - player.getEyeHeight(),
            quern.getZ() + pz / 16);
        feet.setDirection(new Vector(0, -1, 0));
        player.teleport(feet);
    }

    private void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, amount == 0 ? null : new ItemStack(type, amount));
    }

    private boolean click() {
        return service.use(quern, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND));
    }

    private int count(Material type) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == type) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** A handstone in place and {@code grain} wheat in the input. */
    private void load(int grain) {
        lookDownAt(8, 8);
        hold(Material.FLINT, 1);
        assertThat(click()).isTrue();
        hold(Material.WHEAT, grain);
        assertThat(click()).isTrue();
        hold(Material.AIR, 0);
    }

    @Test
    void grindingWheatGrainGivesFlour() {
        lookDownAt(8, 8);
        hold(Material.FLINT, 1);
        assertThat(click()).as("a handstone on the bare base top").isTrue();
        assertThat(slot(QuernDevice.SLOT_HANDSTONE).getType()).isEqualTo(Material.FLINT);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getType()).isEqualTo(Material.AIR);
        assertThat(effects.shown.handstone().getType()).as("the display follows (and with it has_handstone)")
            .isEqualTo(Material.FLINT);

        hold(Material.WHEAT, 5);
        assertThat(click()).as("any held stack goes to the input once a handstone is in").isTrue();
        assertThat(slot(QuernDevice.SLOT_INPUT).getAmount()).isEqualTo(5);

        hold(Material.AIR, 0);
        lookDownAt(4.8, 4.8);
        assertThat(click()).as("the handle grinds").isTrue();
        assertThat(record().state().grindStart()).isEqualTo(1000);
        assertThat(effects.started).isEqualTo(1);
        assertThat(effects.spins).isEqualTo(1);

        clock.set(1089);
        lookDownAt(1, 1);
        assertThat(click()).as("while grinding only the output is taken; it is empty").isFalse();
        assertThat(record().state().grinding(clock.get())).isTrue();

        clock.set(1090);
        service.neighbourChanged(quern); // the callback the visuals schedule at the finish tick
        assertThat(record().state().started()).isFalse();
        assertThat(slot(QuernDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.SUGAR);
        assertThat(slot(QuernDevice.SLOT_OUTPUT).getAmount()).isEqualTo(1);
        assertThat(slot(QuernDevice.SLOT_INPUT).getAmount()).isEqualTo(4);
        assertThat(effects.damage).as("one handstone damage per grind").isEqualTo(1);
        assertThat(effects.finished).isEqualTo(1);
        assertThat(effects.shown.output().getType()).isEqualTo(Material.SUGAR);

        assertThat(click()).as("the base gives the output").isTrue();
        assertThat(count(Material.SUGAR)).isEqualTo(1);
        assertThat(slot(QuernDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aGrindDueWithoutItsCallbackFinishesOnTheNextClickSilently() {
        load(2);
        lookDownAt(11, 11); // the handstone top beside the input slot (over the slot an empty hand takes the input)
        assertThat(click()).as("the handstone top grinds with an empty hand").isTrue();
        assertThat(record().state().started()).isTrue();
        clock.addAndGet(50_000); // a restart: the scheduled callback was lost
        lookDownAt(1, 1);
        assertThat(click()).as("settled first, then the base gives the flour").isTrue();
        assertThat(count(Material.SUGAR)).isEqualTo(1);
        assertThat(slot(QuernDevice.SLOT_INPUT).getAmount()).isEqualTo(1);
        assertThat(effects.finished).as("a late finish plays no sound").isZero();
    }

    @Test
    void aSlowCalendarReschedulesTheFinish() {
        load(1);
        lookDownAt(4.8, 4.8);
        click();
        clock.addAndGet(60);
        service.neighbourChanged(quern);
        assertThat(record().state().started()).isTrue();
        assertThat(effects.finishAt).containsExactly(30L);
    }

    @Test
    void noRecipeOrNoHandstoneNoGrind() {
        lookDownAt(8, 8);
        hold(Material.FLINT, 1);
        click();
        hold(Material.STICK, 3);
        click();
        assertThat(slot(QuernDevice.SLOT_INPUT).getType()).isEqualTo(Material.STICK);
        hold(Material.AIR, 0);
        lookDownAt(4.8, 4.8);
        assertThat(click()).as("TFC FAIL: handled, nothing starts").isTrue();
        assertThat(record().state().started()).isFalse();
        assertThat(effects.started).isZero();
    }

    @Test
    void onlyHandstonesGoIntoTheHandstoneSlot() {
        lookDownAt(8, 8);
        hold(Material.WHEAT, 4);
        assertThat(click()).as("no handstone: the base, which has no output").isFalse();
        assertThat(slot(QuernDevice.SLOT_HANDSTONE).getType()).isEqualTo(Material.AIR);
        assertThat(slot(QuernDevice.SLOT_INPUT).getType()).isEqualTo(Material.AIR);
        hold(Material.FLINT, 3);
        click();
        assertThat(slot(QuernDevice.SLOT_HANDSTONE).getAmount()).as("handstone slot limit 1").isEqualTo(1);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(2);
    }

    @Test
    void sneakingTakesTheHandstoneAndItsInput() {
        load(3);
        player.setSneaking(true);
        // the input slot box covers the handstone top once there is input; aim beside it, still on the handstone
        lookDownAt(11, 11);
        assertThat(click()).isTrue();
        assertThat(slot(QuernDevice.SLOT_HANDSTONE).getType()).isEqualTo(Material.AIR);
        assertThat(slot(QuernDevice.SLOT_INPUT).getType()).isEqualTo(Material.AIR);
        assertThat(count(Material.FLINT)).isEqualTo(1);
        assertThat(count(Material.WHEAT)).isEqualTo(3);
        assertThat(effects.shown.handstone().getType()).isEqualTo(Material.AIR);
    }

    @Test
    void theHandstoneWearsOutAndBreaks() {
        effects.durability = 2;
        load(5);
        for (int i = 0; i < 2; i++) {
            lookDownAt(4.8, 4.8);
            assertThat(click()).isTrue();
            clock.addAndGet(90);
            service.neighbourChanged(quern);
        }
        assertThat(slot(QuernDevice.SLOT_HANDSTONE).getType()).isEqualTo(Material.AIR);
        assertThat(effects.broken).hasSize(1);
        assertThat(slot(QuernDevice.SLOT_OUTPUT).getAmount()).isEqualTo(2);
        lookDownAt(4.8, 4.8);
        click();
        assertThat(record().state().started()).as("no handstone, no grind").isFalse();
    }

    @Test
    void aFullOutputSpillsIntoTheWorld() {
        load(2);
        DeviceRecord<QuernState> r = record();
        @SuppressWarnings("unchecked")
        DeviceKind<QuernState> kind = (DeviceKind<QuernState>) service.kinds().find(QuernDevice.ID).orElseThrow();
        store.data.put(FakeCustomBlocks.at(quern), kind.codec().encode(
            r.withSlot(QuernDevice.SLOT_OUTPUT, stacks.encode(new ItemStack(Material.SUGAR, 64)))));
        lookDownAt(4.8, 4.8);
        click();
        clock.addAndGet(90);
        service.neighbourChanged(quern);
        assertThat(slot(QuernDevice.SLOT_OUTPUT).getAmount()).isEqualTo(64);
        assertThat(effects.spawned).hasSize(1);
        assertThat(effects.spawned.getFirst().getType()).isEqualTo(Material.SUGAR);
    }

    @Test
    void removalHidesTheDisplays() {
        load(1);
        service.removed(world, 0, 64, 0, QuernDevice.ID, store.read(quern).orElseThrow());
        assertThat(effects.hidden).isTrue();
        assertThat(BlockStateRef.of(QuernDevice.ID).with(QuernDevice.HAS_HANDSTONE, "true"))
            .isEqualTo(QuernDevice.state(true));
    }
}
