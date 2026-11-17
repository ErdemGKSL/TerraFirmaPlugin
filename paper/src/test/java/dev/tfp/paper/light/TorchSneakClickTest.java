/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.light;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-21: TFC {@code TorchItem.useOn} on a lit torch while sneaking. Sneaking skips the block's own use
 * ({@code TFCTorchBlock.useItemOn}), so the held torch weak-starts the lit torch (its counter is reset) and is not placed
 * against it. Without sneaking the torch block's use consumes the click and the listener leaves it alone.
 *
 * <p>Stand-in: vanilla torch = the {@code tfp:torch} item; the weak start is recorded.
 */
class TorchSneakClickTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks()
        .known("tfp:torch", "tfp:wall_torch", "tfp:dead_torch", "tfp:dead_wall_torch");
    private final List<Block> started = new ArrayList<>();
    private TorchListener listener;
    private World world;
    private Block at;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("torch-sneak");
        world.loadChunk(0, 0);
        at = world.getBlockAt(2, 64, 3);
        player = server.addPlayer();
        player.teleport(new Location(world, 4.5, 64, 4.5));
        player.setGameMode(GameMode.SURVIVAL);
        listener = new TorchListener(
            s -> s.getType() == Material.TORCH ? Optional.of("tfp:torch") : Optional.empty(), blocks,
            (block, face, p, stack) -> started.add(block));
    }

    private PlayerInteractEvent click(BlockFace face) {
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.TORCH),
            at, face, EquipmentSlot.HAND);
        listener.onUse(e);
        return e;
    }

    @Test
    void sneakClickOnALitTorchWeakStartsItAndPlacesNothing() {
        blocks.place(at, BlockStateRef.of("tfp:torch"), true);
        player.setSneaking(true);
        PlayerInteractEvent e = click(BlockFace.NORTH);
        assertThat(e.isCancelled()).isTrue();
        assertThat(started).containsExactly(at);
        assertThat(blocks.placed()).hasSize(1);
        assertThat(blocks.stateAt(at.getRelative(BlockFace.NORTH))).isEmpty();
    }

    @Test
    void sneakClickOnALitWallTorchWeakStartsItInCreativeToo() {
        blocks.place(at, BlockStateRef.of("tfp:wall_torch").with("facing", "east"), true);
        player.setGameMode(GameMode.CREATIVE);
        player.setSneaking(true);
        PlayerInteractEvent e = click(BlockFace.UP);
        assertThat(e.isCancelled()).isTrue();
        assertThat(started).containsExactly(at);
        assertThat(blocks.stateAt(at)).contains(BlockStateRef.of("tfp:wall_torch").with("facing", "east"));
    }

    @Test
    void normalClickOnALitTorchIsLeftToTheTorchBlocksOwnUse() {
        blocks.place(at, BlockStateRef.of("tfp:torch"), true);
        PlayerInteractEvent e = click(BlockFace.UP);
        assertThat(e.isCancelled()).isFalse();
        assertThat(started).isEmpty();
    }

    @Test
    void deadTorchIsRelitSneakingOrNot() {
        blocks.place(at, BlockStateRef.of("tfp:dead_wall_torch").with("facing", "south"), true);
        assertThat(click(BlockFace.UP).isCancelled()).isTrue();
        player.setSneaking(true);
        assertThat(click(BlockFace.UP).isCancelled()).isTrue();
        assertThat(started).containsExactly(at, at);
    }

    @Test
    void sneakClickInAdventureOrSpectatorOrOnAnotherBlockDoesNothing() {
        player.setSneaking(true);
        assertThat(click(BlockFace.UP).isCancelled()).isFalse(); // no torch there
        blocks.place(at, BlockStateRef.of("tfp:torch"), true);
        player.setGameMode(GameMode.ADVENTURE);
        assertThat(click(BlockFace.UP).isCancelled()).isFalse();
        player.setGameMode(GameMode.SPECTATOR);
        assertThat(click(BlockFace.UP).isCancelled()).isFalse();
        assertThat(started).isEmpty();
    }
}
