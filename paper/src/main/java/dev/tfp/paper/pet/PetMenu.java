/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/PetCommandScreen.java @ c3b4d9536 (the command list)
 */
package dev.tfp.paper.pet;

import dev.tfp.core.pet.PetCommand;
import dev.tfp.core.pet.PetRules;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The pet command menu (TFC's {@code PetCommandScreen}): a hopper window with one button per command the pet accepts in the
 * menu check ({@link PetRules#menu}), in TFC's command order. TFC draws a custom screen and sends a packet; here the window
 * is a vanilla inventory and a click is the command.
 */
final class PetMenu implements InventoryHolder {
    private final UUID pet;
    private final List<PetCommand> commands;
    private Inventory inventory;

    private PetMenu(UUID pet, List<PetCommand> commands) {
        this.pet = pet;
        this.commands = commands;
    }

    /**
     * Builds the menu of {@code pet}.
     *
     * @param label the localised name of a message key ({@code pet.command.sit})
     */
    static PetMenu create(UUID pet, float health, String title, BiFunction<String, PetCommand, String> label) {
        PetMenu menu = new PetMenu(pet, PetRules.menu(health));
        menu.inventory = Bukkit.createInventory(menu, InventoryType.HOPPER, Component.text(title));
        for (int i = 0; i < menu.commands.size(); i++) {
            PetCommand c = menu.commands.get(i);
            ItemStack icon = new ItemStack(icon(c));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(label.apply("pet.command." + c.name().toLowerCase(java.util.Locale.ROOT), c))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            icon.setItemMeta(meta);
            menu.inventory.setItem(i, icon);
        }
        return menu;
    }

    private static Material icon(PetCommand c) {
        return switch (c) {
            case RELAX -> Material.RED_BED;
            case HOME -> Material.OAK_DOOR;
            case SIT -> Material.OAK_STAIRS;
            case FOLLOW -> Material.LEAD;
            case HUNT -> Material.IRON_SWORD;
        };
    }

    UUID pet() {
        return pet;
    }

    /** The command in {@code slot}, or null for an empty slot. */
    PetCommand command(int slot) {
        return slot >= 0 && slot < commands.size() ? commands.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
