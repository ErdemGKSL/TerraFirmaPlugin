/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.beam;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.beam.BeamShape;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-PHYS-04 on MockBukkit with a fake CraftEngine adapter: placing the support item (a pole with two more above from a big
 * stack, one when sneaking, none in adventure, free in creative; a horizontal span of five to the far pole), the beam runner
 * (connections decided async and applied by the commit queue, converging with one decision per changed block), and the
 * collapse of a span when a pole is broken: each beam drops its support item.
 */
class BeamMockBukkitTest extends MockServerTest {
    static final String WOOD = "oak";
    static final NamespacedKey ID = new NamespacedKey("tfptest", "id");

    /** Region tasks wait for {@link #drain}; cpu tasks run at once; the test thread owns everything. */
    final Deque<Runnable> tasks = new ArrayDeque<>();
    final FakeCustomBlocks custom = new FakeCustomBlocks();
    final CustomItems items = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return BeamBlocks.woodOfItem(id).isPresent();
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            ItemStack stack = new ItemStack(Material.STICK, amount);
            ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
            return Optional.of(stack);
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            if (stack == null || !stack.hasItemMeta()) {
                return Optional.empty();
            }
            return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        }
    };
    final RegionExecutor regions = new RegionExecutor() {
        @Override
        public void run(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void runNextTick(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public boolean isOwner(int chunkX, int chunkZ) {
            return true;
        }
    };
    World world;
    BeamService service;
    BeamListener listener;
    PlayerMock player;

    @BeforeEach
    void setUp() {
        BeamBlocks.IDS.forEach(custom::known);
        world = server.addSimpleWorld("beam");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        for (int x = -2; x <= 9; x++) {
            for (int z = -2; z <= 5; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }
        service = new BeamService(w -> regions, custom, items, Runnable::run,
            new CommitBudget(1_000_000_000L, System::nanoTime), b -> b.getType() == Material.STONE);
        listener = new BeamListener(service, new Ticks(), items);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    void drain() {
        for (int i = 0; i < 10_000 && !tasks.isEmpty(); i++) {
            tasks.poll().run();
        }
        assertThat(tasks).as("settled").isEmpty();
        assertThat(service.isIdle()).isTrue();
    }

    ItemStack support(int amount) {
        return items.create(BeamBlocks.itemId(WOOD), amount).orElseThrow();
    }

    /** The player at (3, 64, 4) holding {@code amount} support items, looking with {@code yaw} and {@code pitch}. */
    void hold(int amount, float yaw, float pitch) {
        player.teleport(new Location(world, 3.5, 64, 4.5, yaw, pitch));
        player.getInventory().setItemInMainHand(support(amount));
    }

    PlayerInteractEvent click(Block clicked, BlockFace face) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            player.getInventory().getItemInMainHand(), clicked, face, EquipmentSlot.HAND);
        listener.onInteract(event);
        drain();
        return event;
    }

    BlockStateRef at(int x, int y, int z) {
        return custom.stateAt(world.getBlockAt(x, y, z)).orElse(null);
    }

    static BlockStateRef beam(BeamShape shape, Direction... connections) {
        Set<Direction> c = connections.length == 0 ? Set.of() : EnumSet.of(connections[0], connections);
        return BeamBlocks.state(WOOD, shape, c);
    }

    int held() {
        ItemStack s = player.getInventory().getItemInMainHand();
        return s == null || s.getType() == Material.AIR ? 0 : s.getAmount();
    }

    long droppedSupports() {
        return world.getEntities().stream().filter(e -> e instanceof Item)
            .map(e -> ((Item) e).getItemStack())
            .filter(s -> items.idOf(s).equals(Optional.of(BeamBlocks.itemId(WOOD))))
            .mapToLong(ItemStack::getAmount).sum();
    }

    @Test
    void lookingDownOnStonePlacesThreePolesFromABigStack() {
        hold(8, 0f, 90f);
        PlayerInteractEvent event = click(world.getBlockAt(2, 63, 2), BlockFace.UP);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
        for (int y = 64; y <= 66; y++) {
            assertThat(at(2, y, 2)).as("pole at y " + y).isEqualTo(beam(BeamShape.VERTICAL));
        }
        assertThat(at(2, 67, 2)).isNull();
        assertThat(held()).isEqualTo(5);
    }

    @Test
    void sneakingPlacesOnePoleAndCreativeTakesNothing() {
        hold(8, 0f, 90f);
        player.setSneaking(true);
        click(world.getBlockAt(2, 63, 2), BlockFace.UP);
        assertThat(at(2, 64, 2)).isEqualTo(beam(BeamShape.VERTICAL));
        assertThat(at(2, 65, 2)).isNull();
        assertThat(held()).isEqualTo(7);

        player.setSneaking(false);
        player.setGameMode(GameMode.CREATIVE);
        hold(8, 0f, 90f);
        click(world.getBlockAt(0, 63, 2), BlockFace.UP);
        assertThat(at(0, 66, 2)).isEqualTo(beam(BeamShape.VERTICAL));
        assertThat(held()).isEqualTo(8);
    }

    @Test
    void adventureAndPlainItemsPlaceNothing() {
        player.setGameMode(GameMode.ADVENTURE);
        hold(8, 0f, 90f);
        PlayerInteractEvent event = click(world.getBlockAt(2, 63, 2), BlockFace.UP);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(at(2, 64, 2)).isNull();
        assertThat(held()).isEqualTo(8);

        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK, 4));
        PlayerInteractEvent plain = click(world.getBlockAt(2, 63, 2), BlockFace.UP);
        assertThat(plain.useItemInHand()).isNotEqualTo(Event.Result.DENY);
        assertThat(at(2, 64, 2)).isNull();
    }

    /** The done criterion: a beam spanning 5 blocks connects; removing a pole drops the span. */
    @Test
    void aSpanOfFiveConnectsAndFallsWhenAPoleIsBroken() {
        service.place(world.getBlockAt(0, 64, 0), beam(BeamShape.VERTICAL));
        service.place(world.getBlockAt(6, 64, 0), beam(BeamShape.VERTICAL));
        drain();
        hold(8, 90f, 10f); // looking west at the east face of the west pole
        click(world.getBlockAt(0, 64, 0), BlockFace.EAST);
        for (int x = 1; x <= 5; x++) {
            assertThat(at(x, 64, 0)).as("span at x " + x).isEqualTo(beam(BeamShape.HORIZONTAL, Direction.EAST, Direction.WEST));
        }
        assertThat(at(0, 64, 0)).isEqualTo(beam(BeamShape.VERTICAL, Direction.EAST));
        assertThat(at(6, 64, 0)).isEqualTo(beam(BeamShape.VERTICAL, Direction.WEST));
        assertThat(held()).as("one for the block, four for the span").isEqualTo(3);

        Block pole = world.getBlockAt(6, 64, 0);
        custom.remove(pole);
        service.changed(pole);
        drain();
        for (int x = 1; x <= 6; x++) {
            assertThat(at(x, 64, 0)).as("x " + x).isNull();
        }
        assertThat(at(0, 64, 0)).isEqualTo(beam(BeamShape.VERTICAL));
        assertThat(service.drops(world)).isEqualTo(5);
        assertThat(droppedSupports()).isEqualTo(5);
    }

    @Test
    void aShortStackOrAPoleTooFarPlacesNoSpan() {
        service.place(world.getBlockAt(0, 64, 0), beam(BeamShape.VERTICAL));
        service.place(world.getBlockAt(6, 64, 0), beam(BeamShape.VERTICAL));
        drain();
        hold(4, 90f, 10f);
        click(world.getBlockAt(0, 64, 0), BlockFace.EAST);
        assertThat(at(1, 64, 0)).as("TFC destroys a span the stack cannot fill").isNull();
        assertThat(held()).isEqualTo(4);

        Block far = world.getBlockAt(7, 64, 3);
        service.place(world.getBlockAt(0, 64, 3), beam(BeamShape.VERTICAL));
        service.place(far, beam(BeamShape.VERTICAL));
        drain();
        hold(16, 90f, 10f);
        click(world.getBlockAt(0, 64, 3), BlockFace.EAST);
        // no horizontal (the pole is six away); the next looking direction is down onto stone: a pole
        assertThat(at(1, 64, 3)).isEqualTo(beam(BeamShape.VERTICAL, Direction.WEST));
        assertThat(at(2, 64, 3)).isNull();
    }

    @Test
    void aPoleOnAirFallsAndItsBeamsConvergeOnce() {
        Block floor = world.getBlockAt(4, 63, 4);
        service.place(world.getBlockAt(4, 64, 4), beam(BeamShape.VERTICAL));
        service.place(world.getBlockAt(4, 65, 4), beam(BeamShape.VERTICAL));
        drain();
        assertThat(at(4, 65, 4)).isEqualTo(beam(BeamShape.VERTICAL));
        floor.setType(Material.AIR);
        service.changed(floor);
        drain();
        assertThat(at(4, 64, 4)).isNull();
        assertThat(at(4, 65, 4)).as("the pole above loses its pole below").isNull();
        assertThat(droppedSupports()).isEqualTo(2);
    }

    @Test
    void theLoadedSupportTableCountsTheHorizontalBeamsOnly() {
        // TFC's horizontal_support_beam definition, renamed by datagen, names exactly our horizontal ids
        assertThat(BeamBlocks.WOODS).hasSize(20);
        assertThat(BeamBlocks.IDS).hasSize(40).contains("tfp:wood/horizontal_support/oak", "tfp:wood/vertical_support/willow");
        assertThat(BeamBlocks.shape("tfp:wood/horizontal_support/white_cedar")).isEqualTo(BeamShape.HORIZONTAL);
        assertThat(BeamBlocks.shape("tfp:wood/vertical_support/foo")).isEqualTo(BeamShape.NONE);
        assertThat(BeamBlocks.woodOfItem("tfp:wood/support/douglas_fir")).contains("douglas_fir");
    }

    /** Region tasks join the test queue; cpu tasks run at once. */
    final class Ticks implements Scheduler {
        @Override
        public void region(Location location, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void region(World w, int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public TaskHandle regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            tasks.add(task);
            return null;
        }

        @Override
        public TaskHandle regionRepeating(World w, int cx, int cz, long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            task.run();
            return true;
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity e, long i, long p, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Executor ioExecutor() {
            return Runnable::run;
        }

        @Override
        public boolean isOwner(World w, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {}
    }
}
