/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.lobstertrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fishing.LobsterTrap;
import dev.tfp.core.lobstertrap.LobsterTrapState;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

/** PAR-FAUNA-19 on MockBukkit with fakes of the CraftEngine adapter and the effects: baiting, the catch, taking the contents. */
class LobsterTrapMockBukkitTest extends MockServerTest {
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

    static final class Effects implements TrapEffects {
        final List<Sound> sounds = new ArrayList<>();
        final List<Integer> bubbles = new ArrayList<>();
        LobsterTrap.State shown;
        Boolean baited;
        int removed;

        @Override
        public void show(Block block, LobsterTrap.State state) {
            shown = state;
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void bubbles(Block block, int count) {
            bubbles.add(count);
        }

        @Override
        public void baited(Block block, boolean baited) {
            this.baited = baited;
        }

        @Override
        public void removed(Block block) {
            removed++;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LobsterTrapDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private DeviceService service;
    private DeviceKind<LobsterTrapState> kind;
    private Block trap;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("trap");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1000L, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        kind = service.register(LobsterTrapDevice.kind(new LobsterTrapDevice.Services(s -> s.getType() == Material.COD, effects)));
        trap = world.getBlockAt(0, 64, 0);
        blocks.place(trap, dev.tfp.paper.integration.BlockStateRef.of(LobsterTrapDevice.ID), true);
        store.data.put(FakeCustomBlocks.at(trap), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private DeviceRecord<LobsterTrapState> record() {
        return kind.codec().decode(store.read(trap).orElseThrow());
    }

    private boolean click(ItemStack held) {
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(trap, player, EquipmentSlot.HAND, held == null ? ItemStack.empty() : held);
    }

    private DeviceRecord<LobsterTrapState> catchOne(String species, List<Double> draws, Material loot) {
        AtomicInteger i = new AtomicInteger();
        LobsterTrapDevice.Caught[] out = {null};
        service.edit(trap, kind, (r, now) -> {
            out[0] = LobsterTrapDevice.catchMob(kind, r, stacks, species, List.of("tfp:food/fresh_seaweed"),
                () -> draws.get(Math.min(i.getAndIncrement(), draws.size() - 1)),
                yields -> yields.stream().map(y -> new ItemStack(loot, y.count())).toList());
            return out[0] == null ? r : out[0].record();
        });
        return out[0] == null ? null : out[0].record();
    }

    @Test
    void baitOnAnOpenTrapPutsThreeUnitsInAndUsesOneItem() {
        assertThat(click(new ItemStack(Material.COD, 2))).isTrue();
        assertThat(record().state().bait()).isEqualTo(3);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(effects.sounds).containsExactly(TrapEffects.Sound.BAIT);
        assertThat(effects.bubbles).containsExactly(4);
        assertThat(effects.shown).isEqualTo(LobsterTrap.State.BAITED);
        assertThat(effects.baited).isTrue();
    }

    @Test
    void creativeBaitingKeepsTheItem() {
        player.setGameMode(GameMode.CREATIVE);
        assertThat(click(new ItemStack(Material.COD, 2))).isTrue();
        assertThat(record().state().bait()).isEqualTo(3);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    @Test
    void aBaitedTrapRefusesMoreBaitAndOtherItemsAndAnEmptyHandOnAnOpenTrapPasses() {
        assertThat(click(new ItemStack(Material.STONE))).isFalse();
        assertThat(click(null)).isFalse();
        assertThat(record().state().bait()).isZero();
        click(new ItemStack(Material.COD));
        assertThat(click(new ItemStack(Material.COD))).isFalse();
        assertThat(click(new ItemStack(Material.STONE))).isFalse(); // a held item never takes the contents
        assertThat(record().state().bait()).isEqualTo(3);
    }

    @Test
    void aCatchPutsOneItemPerSlotUsesOneBaitAndClosesAtZeroBait() {
        click(new ItemStack(Material.COD));
        for (int bait = 2; bait >= 0; bait--) {
            DeviceRecord<LobsterTrapState> next = catchOne("lobster", List.of(0.5, 0.0), Material.COD);
            assertThat(next).isNotNull();
            assertThat(next.state().bait()).isEqualTo(bait);
        }
        assertThat(catchOne("lobster", List.of(0.5), Material.COD)).as("no bait left, closed").isNull();
        assertThat(LobsterTrapDevice.trapState(record())).isEqualTo(LobsterTrap.State.CLOSED);
        assertThat(record().nonEmptySlots()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void theJunkRollGivesTheJunkItem() {
        click(new ItemStack(Material.COD));
        DeviceRecord<LobsterTrapState> next = catchOne("lobster", List.of(0.05, 0.0), Material.COD);
        assertThat(next.nonEmptySlots()).hasSize(1);
        assertThat(stacks.decode(next.nonEmptySlots().get(0)).getType()).isEqualTo(Material.COD); // the codec fake maps every yield to COD
        assertThat(next.state().bait()).isEqualTo(2);
    }

    @Test
    void anUnbaitedOrFullTrapCatchesNothing() {
        assertThat(catchOne("lobster", List.of(0.5), Material.COD)).isNull();
        service.edit(trap, kind, (r, now) -> {
            DeviceRecord<LobsterTrapState> next = r.withState(new LobsterTrapState(3));
            for (int slot = 0; slot < LobsterTrap.CATCH_SLOTS; slot++) {
                next = next.withSlot(slot, stacks.encode(new ItemStack(Material.COD)));
            }
            return next;
        });
        assertThat(catchOne("lobster", List.of(0.5), Material.COD)).isNull();
    }

    @Test
    void anEmptyHandTakesEveryCatchAndTheBaitOfAClosedTrapIsKept() {
        click(new ItemStack(Material.COD));
        catchOne("lobster", List.of(0.5, 0.0), Material.COD);
        int caught = record().nonEmptySlots().size();
        assertThat(caught).isPositive();
        assertThat(click(null)).isTrue();
        assertThat(player.getInventory().all(Material.COD).values().stream().mapToInt(ItemStack::getAmount).sum()).isEqualTo(caught);
        assertThat(record().nonEmptySlots()).isEmpty();
        assertThat(record().state().bait()).isEqualTo(2); // TFC takeContents keeps the bait when something was taken
        assertThat(effects.shown).isEqualTo(LobsterTrap.State.BAITED);
    }

    @Test
    void takingFromABaitedTrapWithNoCatchClearsTheBait() {
        click(new ItemStack(Material.COD));
        assertThat(click(null)).isTrue();
        assertThat(record().state().bait()).isZero();
        assertThat(effects.shown).isEqualTo(LobsterTrap.State.OPEN);
        assertThat(effects.baited).isFalse();
    }

    @Test
    void theKindIsSixSlotsOfOneItemThatDropOnRemoval() {
        assertThat(kind.slots()).isEqualTo(6);
        assertThat(kind.slotLimit(0)).isEqualTo(1);
        assertThat(kind.removal()).isEqualTo(DeviceKind.RemoveBehaviour.DROP);
        assertThat(LobsterTrapDevice.withTrap(dev.tfp.paper.integration.BlockStateRef.of(LobsterTrapDevice.ID).with("facing", "north"),
            LobsterTrap.State.CLOSED).properties()).containsEntry("trap", "closed").containsEntry("facing", "north");
    }

    @Test
    void theFinderAnswersTheNearestBaitedTrapWithinRange() {
        TrapFinder finder = new TrapFinder();
        UUID w = UUID.randomUUID();
        finder.add(w, new BlockPos(10, 64, 0));
        finder.add(w, new BlockPos(3, 64, 0));
        assertThat(finder.nearest(w, 0.5, 64.5, 0.5, 16)).contains(new BlockPos(3, 64, 0));
        assertThat(finder.nearest(w, 40, 64, 0, 16)).isEmpty();
        finder.remove(w, new BlockPos(3, 64, 0));
        assertThat(finder.nearest(w, 0.5, 64.5, 0.5, 16)).contains(new BlockPos(10, 64, 0));
        assertThat(finder.size()).isEqualTo(1);
    }
}
