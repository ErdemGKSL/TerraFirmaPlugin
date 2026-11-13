/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FirestarterItem.java @ c3b4d9536 (onUseTick, getUseDuration),
 *       src/main/java/net/dries007/tfc/common/items/FlintAndPyriteItem.java @ c3b4d9536 (useOn)
 */
package dev.tfp.paper.fire;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The firestarter and flint and pyrite on vanilla clients.
 *
 * <p><b>Firestarter.</b> TFC's item is used like a bow for 72 ticks and acts on the last one. Ours carries a vanilla
 * {@code minecraft:consumable} component ({@code consume_seconds: 3.6}, bow animation, TFC's firestarter sound), so the
 * client holds it up and plays the sound by itself; the finish arrives as a {@link PlayerItemConsumeEvent}, which is
 * cancelled (the item is not eaten) and runs TFC's last use tick: the block the player looks at (fluids ignored), one
 * durability unless creative (an off-hand cooldown of 20 ticks when it breaks), then
 * {@link FireStarting#start} with TFC's {@code fireStarterChance}.
 *
 * <p><b>Flint and pyrite.</b> TFC's {@code useOn}: a right click on a block (after the block's own use, so not on a
 * container unless sneaking) costs one durability unless creative, plays the flint and steel sound, draws the strength
 * with {@code flintAndPyriteChance} and starts the fire.
 */
public final class FireStarterListener implements Listener {
    public static final String FIRESTARTER = "tfp:firestarter";
    public static final String FLINT_AND_PYRITE = "tfp:flint_and_pyrite";
    /** TFC {@code getUseDuration}: 72 ticks. */
    public static final int USE_TICKS = 72;
    /** TFC: the off-hand cooldown when a firestarter breaks. */
    public static final int BREAK_COOLDOWN_TICKS = 20;

    private final Function<ItemStack, Optional<String>> ids;
    private final FireStarting fire;
    private final FireTools tools;
    private final double firestarterChance;
    private final double flintAndPyriteChance;
    private final Consumer<FireStarting.Result> done;

    /**
     * @param ids  the custom item id of a stack
     * @param done told every result (logging, tests)
     */
    public FireStarterListener(Function<ItemStack, Optional<String>> ids, FireStarting fire, FireTools tools,
                               double firestarterChance, double flintAndPyriteChance,
                               Consumer<FireStarting.Result> done) {
        this.ids = Objects.requireNonNull(ids, "ids");
        this.fire = Objects.requireNonNull(fire, "fire");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.firestarterChance = firestarterChance;
        this.flintAndPyriteChance = flintAndPyriteChance;
        this.done = Objects.requireNonNull(done, "done");
    }

    private boolean is(ItemStack stack, String id) {
        return stack != null && !stack.getType().isAir() && ids.apply(stack).filter(id::equals).isPresent();
    }

    /** The firestarter's use duration has run out: TFC's {@code onUseTick} with {@code countLeft == 1}. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!is(event.getItem(), FIRESTARTER)) {
            return;
        }
        event.setCancelled(true);
        finishFirestarter(event.getPlayer(), event.getHand(), event.getItem());
    }

    /** Returns whether a fire start was sent. */
    boolean finishFirestarter(Player player, EquipmentSlot hand, ItemStack stack) {
        Optional<FireTools.Target> target = tools.target(player);
        if (target.isEmpty()) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            tools.damage(player, hand);
            ItemStack after = player.getInventory().getItem(hand);
            if (after == null || after.getType().isAir()) {
                tools.offhandCooldown(player, BREAK_COOLDOWN_TICKS);
            }
        }
        fire.start(target.get().block(), target.get().face(), player, stack,
            FireStarting.Starter.firestarter(firestarterChance), done);
        return true;
    }

    /** Flint and pyrite: TFC's {@code useOn}. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    @SuppressWarnings("deprecation") // Material.isInteractable: the closest Bukkit answer to "the block has a use"
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Block clicked = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (clicked == null || !is(item, FLINT_AND_PYRITE)) {
            return;
        }
        Player player = event.getPlayer();
        // the block's own use comes first in vanilla (a chest opens), unless the player sneaks
        if (!player.isSneaking() && clicked.getType().isInteractable()
                && event.useInteractedBlock() != Event.Result.DENY) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        strike(player, event.getHand(), clicked, event.getBlockFace(), item);
    }

    void strike(Player player, EquipmentSlot hand, Block clicked, BlockFace face, ItemStack item) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            tools.damage(player, hand);
        }
        tools.strikeSound(clicked);
        fire.start(clicked, face, player, item, FireStarting.Starter.flintAndPyrite(flintAndPyriteChance), done);
    }
}
