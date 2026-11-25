/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.pan.PanDeposits;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanMockBukkitTest extends MockServerTest {
    static final String DEPOSIT = "tfp:deposit/native_copper/granite";
    static final String TABLE = "tfp:deposit/native_copper_granite";

    /** Pans are BOWL (empty) and BUCKET (filled) in these tests; the deposit block is the block's recorded id. */
    static final class FakeEnv implements PanListener.Env {
        final List<String> removed = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<String> rolled = new ArrayList<>();
        boolean water = true;
        String blockId = DEPOSIT;

        @Override
        public Optional<String> itemId(ItemStack stack) {
            return switch (stack.getType()) {
                case BOWL -> Optional.of(PanListener.EMPTY);
                case BUCKET -> Optional.of(PanListener.FILLED);
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<ItemStack> create(String id) {
            return Optional.of(new ItemStack(id.equals(PanListener.EMPTY) ? Material.BOWL : Material.BUCKET));
        }

        @Override
        public Optional<String> blockId(Block block) {
            return Optional.ofNullable(blockId);
        }

        @Override
        public void removeBlock(Block block) {
            removed.add(blockId);
            block.setType(Material.AIR);
        }

        @Override
        public boolean inInfiniteWater(Player player) {
            return water;
        }

        @Override
        public void sound(Player player) {
        }

        @Override
        public void message(Player player, String key) {
            messages.add(key);
        }

        @Override
        public CompletableFuture<List<ItemStack>> roll(Player player, String tableId, ItemStack pan) {
            rolled.add(tableId);
            return CompletableFuture.completedFuture(List.of(new ItemStack(Material.GOLD_NUGGET, 2)));
        }
    }

    FakeEnv env;
    PanListener listener;
    Player player;

    @BeforeEach
    void setUp() {
        env = new FakeEnv();
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "entity" -> {
                    ((Runnable) args[1]).run();
                    yield true;
                }
                case "entityRepeating" -> (Scheduler.TaskHandle) null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        listener = new PanListener(env, () -> PanDeposits.of(Map.of(DEPOSIT, TABLE)), scheduler);
        player = server.addPlayer();
    }

    private PlayerInteractEvent click(Action action, EquipmentSlot hand, ItemStack item, Block block) {
        return new PlayerInteractEvent(player, action, item, block, BlockFace.UP, hand);
    }

    @Test
    void emptyPanTakesDepositBlockAndFillsPan() {
        player.setGameMode(GameMode.SURVIVAL);
        Block block = player.getWorld().getBlockAt(0, 64, 0);
        block.setType(Material.GRAVEL);
        ItemStack pan = new ItemStack(Material.BOWL, 2);
        player.getInventory().setItemInMainHand(pan);
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, pan, block);
        listener.onInteract(event);
        assertThat(env.removed).containsExactly(DEPOSIT);
        assertThat(block.getType()).isEqualTo(Material.AIR);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        ItemStack filled = player.getInventory().first(Material.BUCKET) >= 0
            ? player.getInventory().getItem(player.getInventory().first(Material.BUCKET)) : null;
        assertThat(filled).isNotNull();
        assertThat(PanListener.deposit(filled)).contains(DEPOSIT);
        // MockBukkit's ItemMeta does not keep custom_model_data strings; the real server path is the in-game check
    }

    @Test
    void creativeKeepsThePan() {
        player.setGameMode(GameMode.CREATIVE);
        Block block = player.getWorld().getBlockAt(0, 64, 0);
        ItemStack pan = new ItemStack(Material.BOWL, 1);
        player.getInventory().setItemInMainHand(pan);
        listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, pan, block));
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BOWL);
        assertThat(player.getInventory().contains(Material.BUCKET)).isTrue();
    }

    @Test
    void otherBlocksAreNotDeposits() {
        env.blockId = "tfp:rock/raw/granite";
        Block block = player.getWorld().getBlockAt(0, 64, 0);
        ItemStack pan = new ItemStack(Material.BOWL);
        player.getInventory().setItemInMainHand(pan);
        PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, pan, block);
        listener.onInteract(event);
        assertThat(env.removed).isEmpty();
        assertThat(event.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void filledPanNeedsWaterAndMainHand() {
        ItemStack filled = PanListener.fill(new ItemStack(Material.BUCKET), DEPOSIT);
        env.water = false;
        PlayerInteractEvent dry = click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, filled, null);
        listener.onInteract(dry);
        assertThat(dry.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(env.messages).containsExactly(PanListener.WATER_MESSAGE);
        env.water = true;
        PlayerInteractEvent off = click(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, filled, null);
        listener.onInteract(off);
        assertThat(off.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(env.messages).hasSize(1);
        PlayerInteractEvent wet = click(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, filled, null);
        listener.onInteract(wet);
        assertThat(wet.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void finishingRollsTheDepositTableAndReturnsAnEmptyPan() {
        ItemStack filled = PanListener.fill(new ItemStack(Material.BUCKET), DEPOSIT);
        player.getInventory().setItemInMainHand(filled);
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, filled, EquipmentSlot.HAND);
        listener.onConsume(event);
        assertThat(event.isCancelled()).isTrue();
        assertThat(env.rolled).containsExactly(TABLE);
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BOWL);
        assertThat(player.getInventory().contains(Material.GOLD_NUGGET, 2)).isTrue();
    }

    @Test
    void panWithoutDepositYieldsNothing() {
        ItemStack filled = new ItemStack(Material.BUCKET);
        player.getInventory().setItemInMainHand(filled);
        listener.onConsume(new PlayerItemConsumeEvent(player, filled, EquipmentSlot.HAND));
        assertThat(env.rolled).isEmpty();
    }
}
