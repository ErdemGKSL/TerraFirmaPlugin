/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel; // PAR-PROC-42: in world/ on disk, but the package-private BarrelTestWorld lives here

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PROC-08 on MockBukkit with TFC's generated barrel recipes (brined, pickled, preserved_in_vinegar, limewater, brine)
 * on the real recipe runtime and food PDC: filling by bucket, putting meat in, sealing, the lazy completion, the on-seal and
 * on-unseal traits, the instant and instant fluid recipes, the sealed barrel refusing changes.
 */
class BarrelWorldRulesMockBukkitTest extends MockServerTest {
    static final String OAK = "tfp:wood/barrel/oak";

    @TempDir
    Path dir;

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(OAK);
    private final BarrelTestWorld.MemoryStore store = new BarrelTestWorld.MemoryStore();
    private final BarrelTestWorld.TableCodec stacks = new BarrelTestWorld.TableCodec();
    private final AtomicLong clock = new AtomicLong(10_000);
    private final List<String> sounds = new ArrayList<>();
    private final List<Boolean> seals = new ArrayList<>();
    private final List<ItemStack> spawned = new ArrayList<>();
    private BarrelTestWorld data;
    private DeviceService service;
    private Block barrel;
    private PlayerMock player;

    private final BarrelEffects effects = new BarrelEffects() {
        @Override
        public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
            seals.add(sealed);
            assertThat(wanted).isEqualTo(BarrelDevice.state(OAK, sealed));
        }

        @Override
        public void recipe(Block block, String sound) {
            sounds.add(sound);
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void drum(Block block, float pitch) {
        }

        @Override
        public void spilled(Block block, FluidAmount fluid) {
        }
    };

    @org.junit.jupiter.api.AfterEach
    void resetSignal() {
        BarrelDevice.SIGNAL = Block::isBlockIndirectlyPowered;
    }

    @BeforeEach
    void setUp() {
        data = new BarrelTestWorld(dir).load(clock::get);
        World world = server.addSimpleWorld("barrel");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, data.items,
            Logger.getLogger("test"));
        BarrelRecipeBook book = new BarrelRecipeBook(data.recipes, data.tanks(), BarrelRules.DEFAULT_CAPACITY);
        BarrelDevice.Services services = new BarrelDevice.Services(book, s -> true,
            s -> s.getType() == Material.STICK, effects);
        DeviceKind<BarrelData> kind = service.register(BarrelDevice.kind(services, stacks));
        barrel = world.getBlockAt(0, 64, 0);
        blocks.place(barrel, BarrelDevice.state(OAK, false), true);
        store.data.put(FakeCustomBlocks.at(barrel), new DataMap());
        player = server.addPlayer();
        link = new BarrelScreenLink(new BarrelScreenLink.Env(service, kind, services, stacks, new BarrelTestScheduler(),
            Messages.fromJar(), new GlobalClockHolder()), barrel, OAK);
    }

    private BarrelScreenLink link;

    /** PAR-PROC-35: the item slot is edited in the barrel screen (TFC's GUI slot), here through its link. */
    private void put(ItemStack stack) {
        BarrelContents c = contents();
        assertThat(link.commit(List.of(c.containerIn(), c.containerOut(), stack))).isPresent();
    }

    /** The viewer takes the item slot out of the screen. */
    private ItemStack take() {
        BarrelContents c = contents();
        ItemStack main = c.main();
        assertThat(link.commit(List.of(c.containerIn(), c.containerOut(), ItemStack.empty()))).isPresent();
        return main;
    }

    @SuppressWarnings("unchecked")
    private DeviceRecord<BarrelData> record() {
        DeviceKind<BarrelData> kind = (DeviceKind<BarrelData>) service.kinds().find(OAK).orElseThrow();
        DataMap stored = store.read(barrel).orElseThrow();
        return stored.size() == 0 ? kind.fresh() : kind.codec().decode(stored);
    }

    private BarrelContents contents() {
        return BarrelDevice.contents(record(), stacks);
    }

    private void hold(ItemStack stack) {
        player.getInventory().setItem(EquipmentSlot.HAND, stack);
    }

    private ItemStack held() {
        return player.getInventory().getItem(EquipmentSlot.HAND);
    }

    private boolean click() {
        return service.use(barrel, player, EquipmentSlot.HAND, held());
    }

    private void toggleSeal() {
        hold(null);
        player.setSneaking(true);
        assertThat(click()).isTrue();
        player.setSneaking(false);
    }

    private ItemStack woodenBucket(String fluid, int mb) {
        ItemStack s = data.items.create("tfp:wooden_bucket", 1).orElseThrow();
        if (mb > 0) {
            ItemFluids.fill(s, new FluidAmount(fluid, mb), 1000, true);
        }
        return s;
    }

    private ItemStack emptyWoodenBucket() {
        return data.items.create("tfp:wooden_bucket", 1).orElseThrow();
    }

    private ItemStack jug(String fluid, int mb) {
        ItemStack s = data.items.create("tfp:ceramic/jug", 1).orElseThrow();
        ItemFluids.fill(s, new FluidAmount(fluid, mb), 100, true);
        return s;
    }

    private ItemStack meat(int count, FoodTrait... traits) {
        ItemStack s = new ItemStack(Material.BEEF, count);
        FoodPdc.write(s, new FoodState(clock.get(), List.of(traits)));
        return s;
    }

    private static List<FoodTrait> traits(ItemStack stack) {
        return FoodPdc.read(stack).orElseThrow().traits();
    }

    private void power(boolean on) {
        BarrelDevice.SIGNAL = b -> on; // MockBukkit computes no redstone
        service.neighbourChanged(barrel);
    }

    @Test
    void aSignalSealsAnOpenBarrelAndRemovingItUnseals() {
        power(true);
        assertThat(record().state().sealed()).isTrue();
        assertThat(record().state().powered()).isTrue();
        assertThat(seals).containsExactly(true);
        power(false);
        assertThat(record().state().sealed()).isFalse();
        assertThat(record().state().powered()).isFalse();
        assertThat(seals).containsExactly(true, false);
    }

    @Test
    void aBarrelSealedByHandOnlyRecordsTheSignalAndThenUnsealsWhenItGoesOff() {
        toggleSeal();
        power(true);
        assertThat(record().state().sealed()).isTrue();
        assertThat(record().state().powered()).isTrue();
        assertThat(seals).as("only the hand's seal").containsExactly(true);
        power(false);
        assertThat(record().state().sealed()).isFalse();
    }

    @Test
    void theSwitchOffLeavesTheBarrelAlone() {
        System.setProperty(BarrelDevice.REDSTONE_PROPERTY, "false");
        try {
            power(true);
            assertThat(record().state().sealed()).isFalse();
            assertThat(record().state().powered()).isFalse();
        } finally {
            System.clearProperty(BarrelDevice.REDSTONE_PROPERTY);
        }
    }

    @Test
    void theGatherSlotTakesWhatTheSlotAccepts() {
        assertThat(dev.tfp.paper.barrel.world.BarrelGather.accepted(ItemStack.empty(), new ItemStack(Material.BEEF, 4)))
            .isEqualTo(4);
        assertThat(dev.tfp.paper.barrel.world.BarrelGather.accepted(new ItemStack(Material.BEEF, 62),
            new ItemStack(Material.BEEF, 4))).isEqualTo(2);
        assertThat(dev.tfp.paper.barrel.world.BarrelGather.accepted(new ItemStack(Material.BEEF, 1),
            new ItemStack(Material.PORKCHOP, 4))).isZero();
        assertThat(dev.tfp.paper.barrel.world.BarrelGather.accepted(ItemStack.empty(), ItemStack.empty())).isZero();
    }
}
