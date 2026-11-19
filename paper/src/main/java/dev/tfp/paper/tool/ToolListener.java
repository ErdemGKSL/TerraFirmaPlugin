/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/ToolItem.java @ c3b4d9536 (willConsumeDurability, mineBlock),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onLivingHurt: forging bonus damage),
 *       src/main/java/net/dries007/tfc/mixin/ItemStackMixin.java @ c3b4d9536 (applyForgingBonusToPreventItemDamage)
 */
package dev.tfp.paper.tool;

import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * PAR-TOOL-02: the TFC tool rules vanilla's tool component does not carry.
 * <ul>
 *   <li>Mining durability: TFC's own tool classes (hammer, hoe, knife, scythe, chisel) lose durability on a block with the
 *       {@code tfc:consumes_tool_durability} tag even when its destroy speed is 0 (plants); vanilla's
 *       {@code damage_per_block} skips those, so this adds the point. The pickaxe, axe, shovel and saw are vanilla
 *       classes and keep vanilla's rule.</li>
 *   <li>Forging bonus: each point of item damage is ignored with the bonus's durability chance, and the attacker's
 *       damage is multiplied (the mining speed is the item's {@code mining_efficiency}, see {@link ForgingBonusPdc}).</li>
 * </ul>
 */
public final class ToolListener implements Listener {
    private final Function<ItemStack, Optional<String>> idOf;
    private final Predicate<Block> consumesDurability;
    private final ForgingConfig config;

    /**
     * @param idOf               the custom item id of a stack
     * @param consumesDurability whether a block has the {@code tfc:consumes_tool_durability} tag (region thread)
     */
    public ToolListener(Function<ItemStack, Optional<String>> idOf, Predicate<Block> consumesDurability, ForgingConfig config) {
        this.idOf = idOf;
        this.consumesDurability = consumesDurability;
        this.config = config;
    }

    /** The tool kind of a TFC tool item id ({@code tfp:stone/hoe/granite} gives {@code hoe}), empty for anything else. */
    public static Optional<String> kind(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String[] p = id.split("[:/]");
        if (p.length < 4 || !p[0].equals("tfp") || !(p[1].equals("stone") || p[1].equals("metal"))) {
            return Optional.empty();
        }
        return Optional.of(p[2]);
    }

    /** Whether a TFC tool of this kind follows {@code ToolItem.willConsumeDurability} (it extends TFC's {@code ToolItem}). */
    public static boolean kindConsumesTag(String kind) {
        return switch (kind) {
            case "hammer", "hoe", "knife", "scythe", "chisel" -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Optional<String> kind = idOf.apply(tool).flatMap(ToolListener::kind);
        if (kind.isEmpty() || !kindConsumesTag(kind.get())) {
            return;
        }
        Block block = event.getBlock();
        // vanilla's damage_per_block already took the point for a block with a non-zero destroy speed
        if (block.getType().getHardness() == 0.0f && consumesDurability.test(block)) {
            damageTool(player, tool, 1);
        }
    }

    /** One point of damage through {@link PlayerItemDamageEvent}, so the forging bonus and other listeners see it. */
    public static void damageTool(Player player, ItemStack tool, int amount) {
        PlayerItemDamageEvent e = new PlayerItemDamageEvent(player, tool, amount, amount);
        if (!e.callEvent() || e.getDamage() <= 0 || !(tool.getItemMeta() instanceof Damageable d)) {
            return;
        }
        int damage = d.getDamage() + e.getDamage();
        if (d.hasMaxDamage() && damage >= d.getMaxDamage() || damage >= tool.getType().getMaxDurability() && !d.hasMaxDamage()) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        d.setDamage(damage);
        tool.setItemMeta(d);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ForgingQuality q = ForgingBonusPdc.read(event.getItem());
        if (q == ForgingQuality.NONE) {
            return;
        }
        int damage = q.applyLikeUnbreaking(event.getDamage(), config, ThreadLocalRandom.current());
        if (damage <= 0) {
            event.setCancelled(true);
        } else {
            event.setDamage(damage);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker) || attacker.getEquipment() == null) {
            return;
        }
        ForgingQuality q = ForgingBonusPdc.read(attacker.getEquipment().getItemInMainHand());
        if (q != ForgingQuality.NONE) {
            event.setDamage(event.getDamage() * q.damage(config));
        }
    }
}
