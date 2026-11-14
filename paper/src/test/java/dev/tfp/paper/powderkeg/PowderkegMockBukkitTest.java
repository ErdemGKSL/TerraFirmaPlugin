/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powderkeg;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.powderkeg.PowderkegConfig;
import dev.tfp.core.powderkeg.PowderkegRules;
import dev.tfp.core.powderkeg.PowderkegState;
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
 * PAR-PROC-18 on MockBukkit, with fakes of the CraftEngine adapter and the world effects: the click order (sneak seal,
 * sneak extinguish, open), the fuel filter, lighting by fire, redstone and explosions, the lazy fuse and the explosion
 * strength, and the blast filter. Gunpowder is the fuel; a redstone signal is a set of blocks the test powers.
 */
class PowderkegMockBukkitTest extends MockServerTest {
    static final long START = 10_000;

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

    static final class Effects implements PowderkegEffects {
        final List<Sound> sounds = new ArrayList<>();
        final List<Long> fuses = new ArrayList<>();
        final List<Integer> explosions = new ArrayList<>();
        PowderkegState shown;
        int disabled;

        @Override
        public void show(Block block, PowderkegState state) {
            shown = state;
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void disabled(Player player) {
            disabled++;
        }

        @Override
        public void fuse(Block block, long ticks) {
            fuses.add(ticks);
        }

        @Override
        public void explode(Block block, int strength) {
            explosions.add(strength);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(PowderkegDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private final Set<FakeCustomBlocks.At> powered = ConcurrentHashMap.newKeySet();
    private final List<Block> opened = new ArrayList<>();
    private DeviceService service;
    private DeviceKind<PowderkegState> kind;
    private PowderkegDevice.Services services;
    private World world;
    private Block keg;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("powderkeg");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        register(PowderkegConfig.DEFAULTS);
        keg = world.getBlockAt(0, 64, 0);
        blocks.place(keg, PowderkegDevice.state(PowderkegState.FRESH), true);
        store.data.put(FakeCustomBlocks.at(keg), new DataMap());
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void register(PowderkegConfig config) {
        services = new PowderkegDevice.Services(config, s -> s.getType() == Material.GUNPOWDER,
            b -> powered.contains(FakeCustomBlocks.at(b)), effects, (p, b) -> opened.add(b));
        kind = service.register(PowderkegDevice.kind(services));
    }

    private DeviceRecord<PowderkegState> record() {
        return kind.codec().decode(store.read(keg).orElseThrow());
    }

    private void fill(int gunpowder) {
        service.edit(keg, kind, (r, now) -> {
            DeviceRecord<PowderkegState> next = r;
            int left = gunpowder;
            for (int slot = 0; slot < PowderkegRules.SLOTS && left > 0; slot++) {
                int n = Math.min(64, left);
                next = next.withSlot(slot, stacks.encode(new ItemStack(Material.GUNPOWDER, n)));
                left -= n;
            }
            return next;
        });
    }

    private boolean click(boolean sneak, ItemStack held) {
        player.setSneaking(sneak);
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(keg, player, EquipmentSlot.HAND, held == null ? ItemStack.empty() : held);
    }

    private boolean lightByFire(boolean strong) {
        return PowderkegDevice.lightAt(service, kind, keg, player, s -> s.lightsFromFire(strong), services);
    }

    @Test
    void sneakClickWithAnEmptyHandTogglesTheSealAndAnyOtherClickOpensTheScreen() {
        assertThat(click(false, null)).isTrue();
        assertThat(opened).containsExactly(keg);
        assertThat(click(true, new ItemStack(Material.GUNPOWDER))).isTrue();
        assertThat(opened).hasSize(2);
        assertThat(record().state().sealed()).isFalse();

        assertThat(click(true, null)).isTrue();
        assertThat(record().state().sealed()).isTrue();
        assertThat(effects.shown.sealed()).isTrue();
        assertThat(effects.sounds).containsExactly(PowderkegEffects.Sound.PLACE);
        assertThat(click(true, null)).isTrue();
        assertThat(record().state().sealed()).isFalse();
    }

    @Test
    void onlyPowderkegFuelGoesIn() {
        assertThat(kind.filter().canInsert(0, new ItemStack(Material.GUNPOWDER))).isTrue();
        assertThat(kind.filter().canInsert(0, new ItemStack(Material.SAND))).isFalse();
        assertThat(kind.slots()).isEqualTo(12);
    }

    @Test
    void aStrongFireStartLightsOnlyASealedKeg() {
        assertThat(lightByFire(true)).isFalse();
        click(true, null); // seal
        assertThat(lightByFire(false)).isFalse();
        assertThat(lightByFire(true)).isTrue();
        PowderkegState s = record().state();
        assertThat(s.lit()).isTrue();
        assertThat(s.fuseEnd()).isEqualTo(START + 80);
        assertThat(effects.fuses).containsExactly(80L);
        assertThat(effects.sounds).contains(PowderkegEffects.Sound.PRIMED);
    }

    @Test
    void theFuseGoesOffAfterFuseTicksWithTfcsStrengthAndUsesUpTheSealedGunpowder() {
        fill(12 * 64 - 100); // 668 items: floor(668 / 12) = 55
        click(true, null);
        lightByFire(true);
        clock.set(START + 79);
        service.neighbourChanged(keg);
        assertThat(effects.explosions).isEmpty();
        assertThat(effects.fuses).containsExactly(80L, 1L); // the callback re-queues the rest
        clock.set(START + 80);
        service.neighbourChanged(keg);
        assertThat(effects.explosions).containsExactly(55);
        assertThat(record().nonEmptySlots()).isEmpty();
        assertThat(record().state().lit()).isFalse();
    }

    @Test
    void aFullKegExplodesAtTheMaximumStrength() {
        fill(12 * 64);
        click(true, null);
        lightByFire(true);
        clock.addAndGet(500); // the callback came late (chunk unloaded): it goes off at the next one
        service.neighbourChanged(keg);
        assertThat(effects.explosions).containsExactly(PowderkegRules.MAX_STRENGTH);
    }

    @Test
    void sneakClickPutsOutALitKegAndUnsealingALitKegPutsItOutToo() {
        click(true, null);
        lightByFire(true);
        click(true, null); // lit: extinguish, stays sealed
        assertThat(record().state().lit()).isFalse();
        assertThat(record().state().sealed()).isTrue();
        assertThat(effects.sounds).contains(PowderkegEffects.Sound.COOL, PowderkegEffects.Sound.EXTINGUISH);

        lightByFire(true);
        PowderkegLink link = new PowderkegLink(service, kind, services, scheduler(), keg);
        PowderkegLink.Snapshot after = link.toggleSeal(player).orElseThrow();
        assertThat(after.sealed()).isFalse();
        assertThat(record().state().lit()).isFalse();
    }

    @Test
    void redstoneLightsASealedKegOnSealingAndOnANeighbourUpdate() {
        powered.add(FakeCustomBlocks.at(keg));
        service.neighbourChanged(keg);
        assertThat(record().state().lit()).isFalse(); // unsealed
        click(true, null); // sealing runs neighborChanged
        assertThat(record().state().lit()).isTrue();

        click(true, null); // extinguish
        assertThat(record().state().lit()).isFalse();
        service.neighbourChanged(keg);
        assertThat(record().state().lit()).isTrue();
    }

    @Test
    void anotherExplosionLightsAnUnsealedKeg() {
        assertThat(PowderkegDevice.lightAt(service, kind, keg, null, PowderkegState::lightsFromExplosion, services)).isTrue();
        assertThat(record().state().lit()).isTrue();
        assertThat(record().state().sealed()).isFalse();
        assertThat(PowderkegDevice.lightAt(service, kind, keg, null, PowderkegState::lightsFromExplosion, services)).isFalse();
    }

    @Test
    void disabledKegsRefuseToLightWithTfcsMessage() {
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        register(new PowderkegConfig(false, false, true, 1, 80));
        click(true, null);
        assertThat(lightByFire(true)).isFalse();
        assertThat(effects.disabled).isEqualTo(1);
        assertThat(record().state().lit()).isFalse();
    }

    @Test
    void screenCommitStoresFuelAndRefusesWhileSealed() {
        PowderkegLink link = new PowderkegLink(service, kind, services, scheduler(), keg);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < PowderkegRules.SLOTS; i++) {
            slots.add(i == 3 ? new ItemStack(Material.GUNPOWDER, 20) : ItemStack.empty());
        }
        PowderkegLink.Snapshot after = link.commit(slots).orElseThrow();
        assertThat(after.slots().get(3).getAmount()).isEqualTo(20);
        List<ItemStack> sand = new ArrayList<>(slots);
        sand.set(0, new ItemStack(Material.SAND));
        assertThat(link.commit(sand)).isEmpty();
        link.toggleSeal(player);
        List<ItemStack> more = new ArrayList<>(slots);
        more.set(5, new ItemStack(Material.GUNPOWDER, 3));
        assertThat(link.commit(more)).isEmpty();
        assertThat(record().nonEmptySlots()).hasSize(1);
    }

    @Test
    void blastsLightKegsInTheBlockListAndFilterTheKegsOwnExplosionByTfcsTags() {
        Block other = world.getBlockAt(5, 64, 0);
        blocks.place(other, PowderkegDevice.state(PowderkegState.FRESH), true);
        Block stone = world.getBlockAt(1, 63, 0);
        Block proof = world.getBlockAt(2, 63, 0);
        List<Block> lit = new ArrayList<>();
        List<List<Block>> seen = new ArrayList<>();
        PowderkegBlasts[] holder = new PowderkegBlasts[1];
        PowderkegBlasts blasts = new PowderkegBlasts(new PowderkegBlasts.Env(
            b -> PowderkegModule.isKeg(blocks, b),
            (b, tag) -> b.equals(proof) && tag.equals(PowderkegRules.CANNOT_BREAK_TAG),
            false, lit::add, b -> blocks.remove(b),
            (at, power) -> {
                List<Block> list = new ArrayList<>(List.of(stone, proof, other));
                holder[0].filter(list);
                seen.add(list);
            }));
        holder[0] = blasts;
        // any explosion: the keg is kept and lit, nothing else filtered
        List<Block> tnt = new ArrayList<>(List.of(stone, proof, other));
        blasts.filter(tnt);
        assertThat(tnt).containsExactly(stone, proof);
        assertThat(lit).containsExactly(other);
        // the keg's own explosion: the keg becomes air, explosion_proof is spared
        blasts.explode(keg, 10);
        assertThat(blocks.stateAt(keg)).isEmpty();
        assertThat(seen).containsExactly(List.of(stone));
        assertThat(blasts.active()).isFalse();
        // a keg gone meanwhile does not explode
        blasts.explode(keg, 10);
        assertThat(seen).hasSize(1);
    }

    private dev.tfp.paper.scheduler.Scheduler scheduler() {
        return new dev.tfp.paper.scheduler.Scheduler() {
            @Override
            public void region(Location location, Runnable task) {
                task.run();
            }

            @Override
            public void region(World w, int chunkX, int chunkZ, Runnable task) {
                task.run();
            }

            @Override
            public TaskHandle regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
                return null;
            }

            @Override
            public TaskHandle regionRepeating(World w, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                    Runnable task) {
                return null;
            }

            @Override
            public boolean entity(org.bukkit.entity.Entity entity, Runnable task, Runnable retired) {
                task.run();
                return true;
            }

            @Override
            public boolean entityDelayed(org.bukkit.entity.Entity entity, long delayTicks, Runnable task, Runnable retired) {
                return false;
            }

            @Override
            public TaskHandle entityRepeating(org.bukkit.entity.Entity entity, long initialDelayTicks, long periodTicks,
                    Runnable task, Runnable retired) {
                return null;
            }

            @Override
            public void global(Runnable task) {
                task.run();
            }

            @Override
            public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
                return null;
            }

            @Override
            public void cpu(Runnable task) {
                task.run();
            }

            @Override
            public java.util.concurrent.Executor cpuExecutor() {
                return Runnable::run;
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> io(Runnable task) {
                task.run();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.Executor ioExecutor() {
                return Runnable::run;
            }

            @Override
            public boolean isOwner(World w, int chunkX, int chunkZ) {
                return true;
            }

            @Override
            public void shutdown() {
            }
        };
    }
}
