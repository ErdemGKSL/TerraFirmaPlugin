/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scraping;

import dev.tfp.core.scraping.ScrapingState;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * What the scraping device does to the world around it besides its own record (PAR-PROC-05): the knife's wear, the
 * particles of an explicit click, the extra drop, and the surface's look. All calls run on the scraping block's region
 * thread (inside a device callback). {@link PaperScrapingEffects} is the server implementation; tests record the calls.
 */
public interface ScrapingEffects {
    /** TFC {@code Helpers.damageItem(stack, player, hand)}: one point of wear on the tool in {@code hand}. */
    void damageTool(Player player, EquipmentSlot hand);

    /**
     * TFC {@code ScrapingBlock.doParticles}: two item particles of {@code item} at the clicked point ({@code x}, {@code z}
     * in block units on the surface).
     */
    void particles(Block block, double x, double z, ItemStack item);

    /** TFC {@code Helpers.spawnItem(level, pos, stack)}: a new item entity at the block centre, {@code y + 0.5}. */
    void spawn(Block block, ItemStack item);

    /** Shows (or updates) the surface drawn for {@code state} on {@code block}. */
    void show(Block block, ScrapingState state);

    /** Removes the surface drawn on {@code block}, if any. */
    void hide(Block block);
}
