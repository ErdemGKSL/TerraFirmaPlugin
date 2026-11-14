/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (useOn)
 */
package dev.tfp.paper.light;

import dev.tfp.core.light.TorchRules.Kind;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * TFC {@code TorchItem.useOn}: a right click with a lit torch first tries a weak fire start on the clicked block and only
 * places the torch when nothing was lit. The lit torch's own use consumes every click on it ({@link TorchDevice}), so the
 * blocks a held torch lights here are the dead torches (relit through the fire starting service, or directly without
 * it). Other weak lightables (candles, PAR-HEAT-11) are not asked yet: their click places the torch (ledger).
 */
public final class TorchListener implements Listener {
    /** The lit torch item. */
    public static final String TORCH_ITEM = Kind.TORCH.id();

    /** TFC {@code StartFireEvent.startFire(level, pos, state, face, player, stack, WEAK, -1)}. */
    @FunctionalInterface
    public interface WeakStart {
        void start(Block block, BlockFace face, Player player, ItemStack stack);
    }

    private final Function<ItemStack, Optional<String>> itemId;
    private final CustomBlocks blocks;
    private final WeakStart start;

    public TorchListener(Function<ItemStack, Optional<String>> itemId, CustomBlocks blocks, WeakStart start) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.start = Objects.requireNonNull(start, "start");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || !itemId.apply(held).filter(TORCH_ITEM::equals).isPresent()) {
            return;
        }
        Player player = event.getPlayer();
        // vanilla ServerPlayerGameMode.useItemOn: no item use in spectator, and none in adventure without can_place_on
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        Block block = event.getClickedBlock();
        boolean dead = blocks.stateAt(block).flatMap(s -> Kind.byId(s.id())).filter(k -> !k.lit()).isPresent();
        if (!dead) {
            return;
        }
        event.setCancelled(true); // TFC: startFire succeeded, so the torch is not placed
        start.start(block, event.getBlockFace(), player, held.clone());
    }
}
