/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-TOOL-02 on MockBukkit: the mining durability rule for TFC's own tool classes and the forging bonus. */
class ToolRulesMockBukkitTest extends MockServerTest {
    private PlayerMock player;
    private ToolListener listener;
    private boolean tagged;

    @BeforeEach
    void setUpTool() {
        player = server.addPlayer();
        // a hoe item stands in as "tfp:stone/hoe/granite": the id comes from the lookup below, not the material
        listener = new ToolListener(stack -> stack.getType() == Material.STONE_HOE ? Optional.of("tfp:stone/hoe/granite")
            : stack.getType() == Material.STONE_AXE ? Optional.of("tfp:metal/axe/copper") : Optional.empty(),
            block -> tagged, ForgingConfig.DEFAULTS);
    }

    private int damageOf(ItemStack s) {
        return ((Damageable) s.getItemMeta()).getDamage();
    }

    @Test
    void kindsFollowTheItemId() {
        assertThat(ToolListener.kind("tfp:stone/hoe/granite")).contains("hoe");
        assertThat(ToolListener.kind("tfp:metal/scythe/steel")).contains("scythe");
        assertThat(ToolListener.kind("tfp:metal/ingot/steel")).contains("ingot");
        assertThat(ToolListener.kind("tfp:stone/axe_head")).isEmpty();
        assertThat(ToolListener.kind("minecraft:stick")).isEmpty();
        assertThat(ToolListener.kindConsumesTag("hammer")).isTrue();
        assertThat(ToolListener.kindConsumesTag("pickaxe")).isFalse();
        assertThat(ToolListener.kindConsumesTag("axe")).isFalse();
    }

    @Test
    void aHoeLosesOneOnATaggedPlantWithNoDestroySpeed() {
        ItemStack hoe = new ItemStack(Material.STONE_HOE);
        player.getInventory().setItemInMainHand(hoe);
        Block plant = server.addSimpleWorld("w").getBlockAt(0, 64, 0);
        plant.setType(Material.SHORT_GRASS);
        tagged = true;
        listener.onBreak(new BlockBreakEvent(plant, player));
        assertThat(damageOf(player.getInventory().getItemInMainHand())).isEqualTo(1);
    }

    @Test
    void anUntaggedPlantOrAHardBlockOrAnAxeAddsNothing() {
        Block plant = server.addSimpleWorld("w2").getBlockAt(0, 64, 0);
        plant.setType(Material.SHORT_GRASS);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE_HOE));
        tagged = false;
        listener.onBreak(new BlockBreakEvent(plant, player));
        assertThat(damageOf(player.getInventory().getItemInMainHand())).isZero();
        tagged = true;
        Block stone = plant.getRelative(0, 1, 0);
        stone.setType(Material.STONE);
        listener.onBreak(new BlockBreakEvent(stone, player)); // vanilla's damage_per_block takes this one
        assertThat(damageOf(player.getInventory().getItemInMainHand())).isZero();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE_AXE));
        listener.onBreak(new BlockBreakEvent(plant, player));
        assertThat(damageOf(player.getInventory().getItemInMainHand())).isZero();
    }

    @Test
    void forgingBonusRoundTripsAndCopies() {
        ItemStack a = new ItemStack(Material.IRON_PICKAXE);
        assertThat(ForgingBonusPdc.read(a)).isEqualTo(ForgingQuality.NONE);
        ForgingBonusPdc.write(a, ForgingQuality.PERFECT, ForgingConfig.DEFAULTS);
        assertThat(ForgingBonusPdc.read(a)).isEqualTo(ForgingQuality.PERFECT);
        ItemStack b = new ItemStack(Material.IRON_PICKAXE);
        ForgingBonusPdc.copy(b, a, ForgingConfig.DEFAULTS);
        assertThat(ForgingBonusPdc.read(b)).isEqualTo(ForgingQuality.PERFECT);
        ItemStack c = new ItemStack(Material.IRON_PICKAXE);
        ForgingBonusPdc.copy(c, new ItemStack(Material.STICK), ForgingConfig.DEFAULTS);
        assertThat(ForgingBonusPdc.read(c)).isEqualTo(ForgingQuality.NONE);
        ForgingBonusPdc.write(a, ForgingQuality.NONE, ForgingConfig.DEFAULTS);
        assertThat(ForgingBonusPdc.read(a)).isEqualTo(ForgingQuality.NONE);
    }

    @Test
    void aPerfectBonusIgnoresHalfTheItemDamageOnAverage() {
        ItemStack tool = new ItemStack(Material.IRON_PICKAXE);
        ForgingBonusPdc.write(tool, ForgingQuality.PERFECT, ForgingConfig.DEFAULTS);
        int kept = 0;
        int runs = 4000;
        for (int i = 0; i < runs; i++) {
            PlayerItemDamageEvent e = new PlayerItemDamageEvent(player, tool, 1, 1);
            listener.onItemDamage(e);
            if (!e.isCancelled()) {
                kept += e.getDamage();
            }
        }
        assertThat(kept / (double) runs).isBetween(0.45, 0.55); // maxDurability 0.5
        PlayerItemDamageEvent plain = new PlayerItemDamageEvent(player, new ItemStack(Material.IRON_PICKAXE), 3, 3);
        listener.onItemDamage(plain);
        assertThat(plain.getDamage()).isEqualTo(3);
    }

    @Test
    void aPerfectBonusMultipliesTheAttackDamage() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ForgingBonusPdc.write(sword, ForgingQuality.PERFECT, ForgingConfig.DEFAULTS);
        player.getInventory().setItemInMainHand(sword);
        var cow = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Cow.class);
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(player, cow, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10.0);
        listener.onAttack(hit);
        assertThat(hit.getDamage()).isEqualTo(15.0, org.assertj.core.data.Offset.offset(1e-4)); // maxDamage 1.5
    }
}
