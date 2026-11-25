/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piglin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.piglin.PiglinCurrency;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-DATA-22: the TFC gold ingot barters with piglins through a marked vanilla stand-in; other items do not. */
class PiglinCurrencyMockBukkitTest extends MockServerTest {
    private PiglinScheduler scheduler;
    private PlayerMock player;
    private WorldMock world;
    private Location at;

    /** A paper stack stands in for a TFC ingot; its custom name is the CraftEngine id. */
    private static ItemStack ingot(String id, int amount) {
        ItemStack stack = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("test", "id"),
            org.bukkit.persistence.PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Optional<String> idOf(ItemStack stack) {
        if (stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey("test", "id"), org.bukkit.persistence.PersistentDataType.STRING));
    }

    @BeforeEach
    void setUp() {
        scheduler = new PiglinScheduler();
        server.getPluginManager().registerEvents(new PiglinCurrencyListener(PiglinCurrencyMockBukkitTest::idOf, scheduler), plugin);
        player = server.addPlayer("Bot");
        world = (WorldMock) player.getWorld();
        at = new Location(world, 4, 64, 4);
    }

    private PlayerInteractEntityEvent use(Piglin piglin) {
        PlayerInteractEntityEvent e = new PlayerInteractEntityEvent(player, piglin, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private Collection<Item> items() {
        return world.getEntitiesByClass(Item.class);
    }

    @Test
    void aTfcGoldIngotIsTakenAndAVanillaGoldIngotIsOfferedToThePiglin() {
        Piglin piglin = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        player.getInventory().setItemInMainHand(ingot(PiglinCurrency.GOLD_INGOT, 3));
        assertThat(use(piglin).isCancelled()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(items()).hasSize(1);
        Item offer = items().iterator().next();
        assertThat(offer.getItemStack().getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(offer.getPersistentDataContainer().has(PiglinCurrencyListener.OFFER)).isTrue();
        assertThat(scheduler.delays).containsExactly(PiglinCurrency.OFFER_TICKS);
    }

    @Test
    void otherItemsAndOtherTfcIngotsAreRefused() {
        Piglin piglin = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        player.getInventory().setItemInMainHand(ingot("tfp:metal/ingot/rose_gold", 2));
        assertThat(use(piglin).isCancelled()).isFalse();
        player.getInventory().setItemInMainHand(new ItemStack(Material.PAPER, 2)); // another paper-based item
        assertThat(use(piglin).isCancelled()).isFalse();
        player.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_INGOT, 2)); // vanilla's own path
        assertThat(use(piglin).isCancelled()).isFalse();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(items()).isEmpty();
    }

    @Test
    void aBusyBabyOrBrutePiglinTakesNothing() {
        player.getInventory().setItemInMainHand(ingot(PiglinCurrency.GOLD_INGOT, 4));
        Piglin busy = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        busy.getEquipment().setItemInOffHand(new ItemStack(Material.GOLD_INGOT));
        use(busy);
        Piglin baby = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        baby.setBaby();
        use(baby);
        PiglinBrute brute = (PiglinBrute) world.spawnEntity(at, EntityType.PIGLIN_BRUTE);
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, brute, EquipmentSlot.HAND));
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(4);
        assertThat(items()).isEmpty();
    }

    @Test
    void creativePlayersKeepTheirIngot() {
        player.setGameMode(GameMode.CREATIVE);
        Piglin piglin = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        player.getInventory().setItemInMainHand(ingot(PiglinCurrency.GOLD_INGOT, 1));
        use(piglin);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(items()).hasSize(1);
    }

    @Test
    void onlyAPiglinCanPickUpTheOffer() {
        Piglin piglin = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        player.getInventory().setItemInMainHand(ingot(PiglinCurrency.GOLD_INGOT, 1));
        use(piglin);
        Item offer = items().iterator().next();
        PluginManager pm = server.getPluginManager();
        EntityPickupItemEvent byPlayer = new EntityPickupItemEvent(player, offer, 0);
        pm.callEvent(byPlayer);
        assertThat(byPlayer.isCancelled()).isTrue();
        EntityPickupItemEvent byPiglin = new EntityPickupItemEvent(piglin, offer, 0);
        pm.callEvent(byPiglin);
        assertThat(byPiglin.isCancelled()).isFalse();
        // an ordinary gold ingot item is not touched
        Item plain = world.dropItem(at, new ItemStack(Material.GOLD_INGOT));
        EntityPickupItemEvent plainPickup = new EntityPickupItemEvent(player, plain, 0);
        pm.callEvent(plainPickup);
        assertThat(plainPickup.isCancelled()).isFalse();
    }

    @Test
    void anUntakenOfferIsRemovedAndTheTfcIngotComesBack() {
        Piglin piglin = (Piglin) world.spawnEntity(at, EntityType.PIGLIN);
        player.getInventory().setItemInMainHand(ingot(PiglinCurrency.GOLD_INGOT, 1));
        use(piglin);
        assertThat(scheduler.later).hasSize(1);
        scheduler.later.getFirst().run();
        assertThat(items()).hasSize(1);
        ItemStack back = items().iterator().next().getItemStack();
        assertThat(idOf(back)).contains(PiglinCurrency.GOLD_INGOT);
        assertThat(back.getAmount()).isEqualTo(1);
    }
}
