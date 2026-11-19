/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PropickItem.java @ c3b4d9536 (useOn),
 *       src/main/java/net/dries007/tfc/network/ProspectedPacket.java @ c3b4d9536 (the message)
 */
package dev.tfp.paper.prospect;

import dev.tfp.core.prospect.ProspectOutcome;
import dev.tfp.core.prospect.ProspectRequest;
import dev.tfp.core.prospect.ProspectResult;
import dev.tfp.core.prospect.ProspectRules;
import dev.tfp.core.prospect.PropickTiers;
import dev.tfp.core.prospect.ScanFact;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.tool.ToolListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-TOOL-08: a right click with a prospector's pick on a block scans the {@value ProspectRules#RADIUS}-block cube around it
 * for blocks with the {@code tfc:prospectable} tag ({@code PropickItem.useOn}). The cube is snapshotted on the block's region
 * thread (one pass of 25^3 reads, once per use and a 10 tick cooldown), the decision is {@link ProspectRules#decide}, and the
 * result is TFC's message on the action bar. One durability point and the hit sound per use.
 */
public final class PropickListener implements Listener {
    private final Function<ItemStack, Optional<String>> idOf;
    private final Function<Block, Optional<BlockStateRef>> stateAt;
    private final BiPredicate<BlockStateRef, String> hasTag;

    /**
     * @param idOf    the custom item id of a stack
     * @param stateAt the custom block state at a block (region thread)
     * @param hasTag  whether a block state has a TFC block tag
     */
    public PropickListener(Function<ItemStack, Optional<String>> idOf, Function<Block, Optional<BlockStateRef>> stateAt,
            BiPredicate<BlockStateRef, String> hasTag) {
        this.idOf = idOf;
        this.stateAt = stateAt;
        this.hasTag = hasTag;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack tool = event.getItem();
        final String metal = PropickTiers.metalOf(tool == null ? null : idOf.apply(tool).orElse(null));
        if (metal == null) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasCooldown(tool)) {
            return;
        }
        final Block clicked = event.getClickedBlock();
        player.setCooldown(tool, ProspectRules.COOLDOWN_TICKS);
        clicked.getWorld().playSound(clicked.getLocation(), clicked.getBlockData().getSoundGroup().getHitSound(),
            org.bukkit.SoundCategory.PLAYERS, 1f, 1f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ToolListener.damageTool(player, tool, ProspectRules.DAMAGE_PER_USE);
        }
        final ProspectOutcome outcome = ProspectRules.decide(request(clicked, PropickTiers.level(metal)));
        player.sendActionBar(message(outcome));
    }

    /** The scan: the clicked block and the prospectable blocks of the cube, read on the caller's (region) thread. */
    ProspectRequest request(Block clicked, int level) {
        final Optional<BlockStateRef> clickedState = stateAt.apply(clicked);
        final String clickedId = clickedState.map(BlockStateRef::id).orElse("minecraft:" + clicked.getType().getKey().getKey());
        final boolean prospectable = clickedState.map(s -> hasTag.test(s, "tfc:prospectable")).orElse(false);
        final List<ScanFact> cube = new ArrayList<>();
        if (!prospectable) {
            final int r = ProspectRules.RADIUS;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        final Block b = clicked.getRelative(dx, dy, dz);
                        if (b.getType().isAir()) {
                            continue;
                        }
                        final Optional<BlockStateRef> state = stateAt.apply(b);
                        if (state.isPresent() && hasTag.test(state.get(), "tfc:prospectable")) {
                            cube.add(new ScanFact(dx, dy, dz, state.get().id()));
                        }
                    }
                }
            }
        }
        return new ProspectRequest(clicked.getX(), clicked.getY(), clicked.getZ(), clickedId, prospectable, level, cube);
    }

    /** TFC's message: {@code Found %s} with the block's prospected name, or {@code Found nothing.}. */
    static Component message(ProspectOutcome outcome) {
        final String key = PropickTiers.messageKey(outcome.result());
        if (outcome.result() == ProspectResult.NOTHING) {
            return Component.translatable(key);
        }
        return Component.translatable(key, Component.translatable(PropickTiers.prospectedKey(outcome.block())));
    }
}
