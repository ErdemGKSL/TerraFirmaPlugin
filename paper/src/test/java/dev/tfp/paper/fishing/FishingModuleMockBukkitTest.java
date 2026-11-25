/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fishing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FAUNA-18: loading bait onto a rod, the bait PDC, rod strength by metal. */
class FishingModuleMockBukkitTest extends MockServerTest {
    private PlayerMock player;
    private FishingModule module;
    private ItemStack rod;
    private ItemStack worm;

    @BeforeEach
    void setUp() {
        rod = new ItemStack(Material.FISHING_ROD);
        worm = new ItemStack(Material.WHEAT_SEEDS, 3);
        player = server.addPlayer();
        module = new FishingModule();
        module.initForTest(null, stack -> stack.getType() == Material.FISHING_ROD ? Optional.of("tfp:metal/fishing_rod/copper")
                : Optional.of("tfp:food/bait"), (id, tag) -> switch (tag) {
            case FishingModule.HOLDS_SMALL -> id.startsWith(FishingModule.ROD_PREFIX);
            case FishingModule.SMALL_BAIT -> id.equals("tfp:food/bait");
            default -> false;
        });
    }

    private InventoryClickEvent click(ClickType type) {
        var chest = server.createInventory(null, 9);
        chest.setItem(0, rod);
        var view = player.openInventory(chest);
        player.setItemOnCursor(worm);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, type, InventoryAction.PICKUP_HALF);
        module.onClick(event);
        return event;
    }

    @Test
    void secondaryClickLoadsOneBaitAndLeavesTheRest() {
        InventoryClickEvent event = click(ClickType.RIGHT);
        assertThat(event.isCancelled()).isTrue();
        assertThat(RodBait.get(event.getCurrentItem())).contains("tfp:food/bait");
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(2);
    }

    @Test
    void primaryClickAndCreativeDoNothing() {
        assertThat(click(ClickType.LEFT).isCancelled()).isFalse();
        assertThat(RodBait.get(rod)).isEmpty();
        player.setGameMode(GameMode.CREATIVE);
        assertThat(click(ClickType.RIGHT).isCancelled()).isFalse();
        assertThat(RodBait.get(rod)).isEmpty();
    }

    @Test
    void baitRoundTripsAndClears() {
        ItemStack stack = new ItemStack(Material.FISHING_ROD);
        assertThat(RodBait.get(stack)).isEmpty();
        RodBait.set(stack, "tfp:food/shellfish/lobster");
        assertThat(RodBait.get(stack)).contains("tfp:food/shellfish/lobster");
        RodBait.set(stack, "minecraft:wheat_seeds");
        assertThat(RodBait.get(stack)).contains("minecraft:wheat_seeds");
        RodBait.clear(stack);
        assertThat(RodBait.get(stack)).isEmpty();
    }

    @Test
    void strengthFollowsTheRodsMetal() {
        assertThat(FishingModule.strengthOf("tfp:metal/fishing_rod/copper")).isEqualTo(5.25f / 12f + 1f);
        assertThat(FishingModule.strengthOf("tfp:metal/fishing_rod/red_steel")).isEqualTo(12f / 12f + 1f);
        assertThat(FishingModule.strengthOf("tfp:metal/fishing_rod/black_bronze")).isEqualTo(6.0f / 12f + 1f);
        assertThat(Set.of(FishingModule.strengthOf("tfp:other"))).containsExactly(5.25f / 12f + 1f);
    }
}
