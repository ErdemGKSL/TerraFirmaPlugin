/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.compost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compost.CompostAddition;
import dev.tfp.core.compost.CompostBin;
import dev.tfp.core.compost.CompostRules;
import dev.tfp.core.compost.ComposterState;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
 * PAR-PROC-11 on MockBukkit, with fakes of the CraftEngine adapter and the world effects: filling the bin with greens and
 * browns, ripening after the ready ticks (from the scheduled callback, from a later click and from the catch-up), sneak
 * extraction, the full-side and rotten messages, poison and rotten compost, creative mode, the hopper below, the drop on
 * removal and the TFC tag order of {@link ComposterModule#addition}.
 *
 * <p>Stand-ins: wheat = a high green (4), stick = a high brown (4), seeds = a low green (1), rotten flesh = a poison,
 * bone meal = compost, dirt = rotten compost; the ready ticks are {@link #READY}.
 */
class ComposterMockBukkitTest extends MockServerTest {
    static final long READY = 1000;
    static final long START = 5000;
    /** The composter has no slots. */
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

    /** Records every world effect; the hopper below takes {@link #hopperRoom} items. */
    static final class Effects implements ComposterEffects {
        CompostBin shown;
        final List<Sound> sounds = new ArrayList<>();
        final List<Message> messages = new ArrayList<>();
        final List<ItemStack> spawnedAbove = new ArrayList<>();
        final List<ItemStack> dropped = new ArrayList<>();
        final List<CompostBin.Type> particles = new ArrayList<>();
        final List<Long> ripenAt = new ArrayList<>();
        final List<ItemStack> hopper = new ArrayList<>();
        int hopperRoom;
        FakeCustomBlocks world;

        @Override
        public void show(Block block, CompostBin bin) {
            shown = bin;
            world.place(block, ComposterDevice.state(bin), false); // the next-tick block state write
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void message(Player player, Message message) {
            messages.add(message);
        }

        @Override
        public void spawn(Block block, ItemStack stack, boolean above) {
            (above ? spawnedAbove : dropped).add(stack);
        }

        @Override
        public void particles(Block block, CompostBin.Type type) {
            particles.add(type);
        }

        @Override
        public void ripenAt(Block block, long ticks) {
            ripenAt.add(ticks);
        }

        @Override
        public int hopperRoom(Block block, ItemStack stack) {
            return Math.min(hopperRoom, stack.getAmount());
        }

        @Override
        public void toHopper(Block block, ItemStack stack) {
            hopper.add(stack);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(ComposterDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();

    {
        effects.world = blocks;
    }
    private final AtomicLong clock = new AtomicLong(START);
    private DeviceService service;
    private World world;
    private Block composter;
    private PlayerMock player;

    static CompostAddition addition(ItemStack stack) {
        return switch (stack.getType()) {
            case WHEAT -> new CompostAddition(CompostAddition.Kind.GREEN, 4);
            case WHEAT_SEEDS -> new CompostAddition(CompostAddition.Kind.GREEN, 1);
            case STICK -> new CompostAddition(CompostAddition.Kind.BROWN, 4);
            case ROTTEN_FLESH -> new CompostAddition(CompostAddition.Kind.POISON, 0);
            default -> CompostAddition.NOTHING;
        };
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("composter");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_STACKS,
            new FakeCustomItems(), Logger.getLogger("test"));
        register(true);
        composter = world.getBlockAt(0, 64, 0);
        blocks.place(composter, ComposterDevice.state(CompostBin.empty()), true);
        store.data.put(FakeCustomBlocks.at(composter), new DataMap()); // CraftEngine creates the block entity
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void register(boolean automation) {
        service.register(ComposterDevice.kind(new ComposterDevice.Services(ComposterMockBukkitTest::addition, b -> READY,
            n -> new ItemStack(Material.BONE_MEAL, n), n -> new ItemStack(Material.DIRT, n), automation, effects)));
    }

    @SuppressWarnings("unchecked")
    private ComposterState state() {
        DeviceKind<ComposterState> kind = (DeviceKind<ComposterState>) service.kinds().find(ComposterDevice.ID).orElseThrow();
        DeviceRecord<ComposterState> r = kind.codec().decode(store.read(composter).orElseThrow());
        return r.state();
    }

    private void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, amount == 0 ? null : new ItemStack(type, amount));
    }

    private boolean click() {
        return service.use(composter, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND));
    }

    private int held() {
        ItemStack s = player.getInventory().getItem(EquipmentSlot.HAND);
        return s == null || s.getType() == Material.AIR ? 0 : s.getAmount();
    }

    /** 16 green and 16 brown, the last addition at the current clock. */
    private void fill() {
        hold(Material.WHEAT, 4);
        for (int i = 0; i < 4; i++) {
            assertThat(click()).isTrue();
        }
        hold(Material.STICK, 4);
        for (int i = 0; i < 4; i++) {
            assertThat(click()).isTrue();
        }
        hold(Material.AIR, 0);
    }

    private void sneakExtract() {
        hold(Material.AIR, 0);
        player.setSneaking(true);
        assertThat(click()).isTrue();
        player.setSneaking(false);
    }

    @Test
    void compostIsReadyAfterTheReadyTicksAndSneakExtractionGivesOne() {
        fill();
        ComposterState s = state();
        assertThat(s.bin().green()).isEqualTo(CompostRules.MAX_AMOUNT);
        assertThat(s.bin().brown()).isEqualTo(CompostRules.MAX_AMOUNT);
        assertThat(s.bin().stage()).isEqualTo(8);
        assertThat(held()).as("every addition took one item").isZero();
        assertThat(effects.sounds).hasSize(8).containsOnly(ComposterEffects.Sound.ADD);
        assertThat(effects.shown.stage()).as("the block state follows the stage").isEqualTo(8);
        assertThat(effects.ripenAt).as("the ripening callback is queued at last update + ready + 1")
            .contains(READY + 1);

        clock.set(START + READY);
        service.neighbourChanged(composter);
        assertThat(state().bin().type()).as("not ready at exactly the ready ticks (TFC: strictly more)")
            .isEqualTo(CompostBin.Type.NORMAL);

        clock.set(START + READY + 1);
        service.neighbourChanged(composter); // the scheduled callback
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.READY);
        assertThat(state().bin().output()).isEqualTo(1);
        assertThat(effects.shown.type()).isEqualTo(CompostBin.Type.READY);
        assertThat(ComposterDevice.state(effects.shown))
            .isEqualTo(BlockStateRef.of(ComposterDevice.ID).with("stage", "8").with("type", "ready"));

        sneakExtract();
        assertThat(effects.spawnedAbove).hasSize(1);
        assertThat(effects.spawnedAbove.getFirst().getType()).isEqualTo(Material.BONE_MEAL);
        assertThat(effects.spawnedAbove.getFirst().getAmount()).isEqualTo(1);
        assertThat(effects.sounds.getLast()).isEqualTo(ComposterEffects.Sound.EXTRACT);
        assertThat(state().bin()).isEqualTo(CompostBin.empty().reset(START + READY + 1));
        assertThat(effects.shown.stage()).isZero();
    }

    @Test
    void aLaterClickAndTheCatchUpRipenWithoutTheCallback() {
        fill();
        clock.set(START + 10 * READY); // a restart: the scheduled callback was lost
        service.catchUp(composter);
        assertThat(state().bin().type()).as("catch-up settles with the stored ready ticks").isEqualTo(CompostBin.Type.READY);

        hold(Material.WHEAT, 1);
        assertThat(click()).as("a full green side refuses with TFC's message").isTrue();
        assertThat(effects.messages).containsExactly(ComposterEffects.Message.TOO_MANY_GREENS);
        assertThat(held()).as("nothing taken").isEqualTo(1);
        assertThat(effects.particles).as("ready particles on the interaction").containsExactly(CompostBin.Type.READY);
    }

    @Test
    void extractingAnUnreadyBinGivesNothingAndResets() {
        fill();
        clock.addAndGet(READY / 2);
        sneakExtract();
        assertThat(effects.spawnedAbove).isEmpty();
        assertThat(state().bin().green()).isZero();
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.NORMAL);
    }

    @Test
    void anAdditionRestartsTheTimer() {
        hold(Material.WHEAT, 4);
        for (int i = 0; i < 4; i++) {
            click();
        }
        hold(Material.STICK, 4);
        for (int i = 0; i < 3; i++) {
            click();
        }
        clock.addAndGet(READY * 5);
        hold(Material.STICK, 1);
        click(); // full now, the timer restarts here
        long filled = clock.get();
        clock.set(filled + READY);
        service.neighbourChanged(composter);
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.NORMAL);
        clock.set(filled + READY + 1);
        service.neighbourChanged(composter);
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.READY);
    }

    @Test
    void poisonRotsTheBinAndItGivesRottenCompost() {
        hold(Material.WHEAT, 2);
        click();
        click();
        hold(Material.STICK, 2);
        click();
        click(); // 8 green, 8 brown
        hold(Material.ROTTEN_FLESH, 1);
        assertThat(click()).isTrue();
        assertThat(held()).isZero();
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.ROTTEN);
        assertThat(state().bin().output()).as("min(8, 8) / 4").isEqualTo(2);
        assertThat(effects.shown.type()).isEqualTo(CompostBin.Type.ROTTEN);

        hold(Material.DIAMOND, 1);
        assertThat(click()).as("any other item on a rotten bin shows the rotten message").isTrue();
        assertThat(effects.messages).containsExactly(ComposterEffects.Message.ROTTEN);
        assertThat(held()).isEqualTo(1);

        clock.addAndGet(READY * 100);
        service.neighbourChanged(composter);
        assertThat(state().bin().type()).as("a rotten bin never ripens").isEqualTo(CompostBin.Type.ROTTEN);

        sneakExtract();
        assertThat(effects.spawnedAbove).hasSize(1);
        assertThat(effects.spawnedAbove.getFirst().getType()).isEqualTo(Material.DIRT);
        assertThat(effects.spawnedAbove.getFirst().getAmount()).isEqualTo(2);
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.NORMAL);
    }

    @Test
    void creativeAdditionsKeepTheItem() {
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.WHEAT_SEEDS, 1);
        assertThat(click()).isTrue();
        assertThat(held()).isEqualTo(1);
        assertThat(state().bin().green()).isEqualTo(1);
        assertThat(state().bin().stage()).as("anything inside shows at least stage 1").isEqualTo(1);
    }

    @Test
    void nonCompostPassesAndStandingInsideDoesNothing() {
        hold(Material.DIAMOND, 1);
        assertThat(click()).as("not compost: the item's own use").isFalse();

        player.teleport(new Location(world, 0.5, 64, 0.5));
        hold(Material.WHEAT, 1);
        assertThat(click()).as("TFC FAIL when the player stands in the composter").isFalse();
        assertThat(state().bin().green()).isZero();
    }

    @Test
    void aHopperBelowTakesTheReadyCompost() {
        fill();
        effects.hopperRoom = 64;
        clock.addAndGet(READY + 1);
        service.neighbourChanged(composter);
        assertThat(effects.hopper).hasSize(1);
        assertThat(effects.hopper.getFirst().getType()).isEqualTo(Material.BONE_MEAL);
        assertThat(state().bin().type()).as("an emptied ready bin resets (setAndUpdateSlots)")
            .isEqualTo(CompostBin.Type.NORMAL);
        assertThat(state().bin().green()).isZero();
    }

    @Test
    void aFullHopperLeavesTheCompost() {
        fill();
        effects.hopperRoom = 0;
        clock.addAndGet(READY + 1);
        service.neighbourChanged(composter);
        assertThat(effects.hopper).isEmpty();
        assertThat(state().bin().type()).isEqualTo(CompostBin.Type.READY);
    }

    @Test
    void breakingTheComposterDropsItsOutput() {
        fill();
        clock.addAndGet(READY + 1);
        service.neighbourChanged(composter);
        DataMap data = store.read(composter).orElseThrow();
        blocks.remove(composter);
        service.removed(world, 0, 64, 0, ComposterDevice.ID, data);
        assertThat(effects.dropped).hasSize(1);
        assertThat(effects.dropped.getFirst().getType()).isEqualTo(Material.BONE_MEAL);
    }

    @Test
    void placingStampsTheTimer() {
        service.placed(composter, player, EquipmentSlot.HAND, new ItemStack(Material.STONE));
        assertThat(state().bin().lastUpdate()).isEqualTo(START);
        assertThat(state().readyTicks()).isEqualTo(READY);
    }

    @Test
    void additionFollowsTfcTagOrder() {
        ItemStack s = new ItemStack(Material.WHEAT);
        assertThat(ComposterModule.addition(s, (st, tag) -> Set.of("tfc:compost_greens/high", "tfc:compost_browns/low")
            .contains(tag), st -> false)).as("browns are checked first").isEqualTo(new CompostAddition(CompostAddition.Kind.BROWN, 1));
        assertThat(ComposterModule.addition(s, (st, tag) -> tag.equals("tfc:compost_greens/high"), st -> true))
            .as("a rotten high green adds 2").isEqualTo(new CompostAddition(CompostAddition.Kind.GREEN, 2));
        assertThat(ComposterModule.addition(s, (st, tag) -> tag.equals("tfc:compost_poisons")
            || tag.equals("tfc:compost_greens/low"), st -> false).kind()).isEqualTo(CompostAddition.Kind.POISON);
        assertThat(ComposterModule.addition(s, (st, tag) -> false, st -> false)).isEqualTo(CompostAddition.NOTHING);
    }
}
