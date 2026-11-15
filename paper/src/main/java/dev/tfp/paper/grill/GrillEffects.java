/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill;

import dev.tfp.paper.firepit.FirepitEffects;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What the grill does to the world around its record (PAR-HEAT-08): the firepit's effects ({@link FirepitEffects}: the
 * block state, sounds, smoke, drops, the burn-out callback) plus the five items drawn on top, the grill damage, the sound
 * of a grill taken off hot, opening the screen and the conversions back and forth. Region thread of the block; tests
 * record the calls.
 */
public interface GrillEffects extends FirepitEffects {
    /**
     * Draws the five grill items on top ({@code GrillBlockEntityRenderer}; index 0 = slot 4, an empty stack for none), and
     * removes the drawings when every entry is empty.
     */
    void items(Block block, List<ItemStack> grill);

    /** TFC {@code TFCDamageTypes.grill(player, 1f)}: the {@code tfp:grill} damage for taking a lit grill off. */
    void burn(Player player, float amount);

    /** TFC {@code Helpers.playSound(level, pos, TFCSounds.ITEM_COOL)}. */
    void cool(Block block);

    /** TFC {@code Helpers.playPlaceSound(null, level, pos, newState)}: the place sound of the converted block. */
    void placeSound(Block block);

    /** TFC {@code serverPlayer.openMenu(grill, pos)}: the grill screen for {@code player}, after this click. */
    void openScreen(Player player, Block block);

    /**
     * TFC {@code convertTo(..., FIREPIT)}: the grill becomes a firepit again, after this click (outside the device route).
     * Returns false when a conversion of this block is already queued (a second click in the same tick does nothing).
     */
    boolean toFirepit(Block block);
}
