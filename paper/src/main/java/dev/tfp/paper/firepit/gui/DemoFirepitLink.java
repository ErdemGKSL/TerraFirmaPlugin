/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-07: a firepit that lives only while its screen is open ({@code /tfp debug firepitgui [temperature]}), to see
 * and check the screen before the firepit block opens it (PAR-HEAT-20). Its temperature is fixed, nothing burns or
 * cooks; a commit cascades the fuel slots as TFC does; closing gives every slot back.
 */
public final class DemoFirepitLink implements FirepitLink {
    private final FirepitSlotRules rules;
    private FirepitSnapshot firepit;

    public DemoFirepitLink(float temperature, FirepitSlotRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.firepit = new FirepitSnapshot(temperature, FirepitSnapshot.emptySlots());
    }

    @Override
    public Optional<FirepitSnapshot> snapshot() {
        return Optional.of(firepit);
    }

    @Override
    public Optional<FirepitSnapshot> commit(List<ItemStack> slots) {
        firepit = firepit.withSlots(FirepitScreenLayout.cascade(slots));
        return snapshot();
    }

    @Override
    public boolean isItemValid(int tfcSlot, ItemStack stack) {
        return rules.isItemValid(tfcSlot, stack);
    }

    @Override
    public void closed(Player viewer) {
        for (ItemStack s : firepit.slots()) {
            if (s.isEmpty()) {
                continue;
            }
            for (ItemStack rest : viewer.getInventory().addItem(s.clone()).values()) {
                viewer.getWorld().dropItem(viewer.getLocation(), rest);
            }
        }
        firepit = firepit.withSlots(FirepitSnapshot.emptySlots());
    }
}
