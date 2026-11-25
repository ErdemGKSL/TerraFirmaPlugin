/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/advancement/**\/*.json, util/advancements/TFCAdvancements.java @ c3b4d9536
 */
package dev.tfp.paper.advancement.criteria;

import dev.tfp.core.advancement.CriterionRef;
import dev.tfp.paper.advancement.Advancements;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.player.NutritionFoodEatenEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-DATA-03: awards TFC's advancement criteria from the events of the features that exist, through the
 * {@link Advancements} trigger API and the {@link CriteriaMap} routes: items entering the inventory, eating, a rotten or
 * fully nourishing meal, killing animals, and standing in a biome or beyond 9999 blocks of the equator. Criteria whose
 * feature publishes no event yet stay {@link CriteriaMap.Kind#PENDING}.
 *
 * <p>Game modes: as vanilla, advancements are earned in every game mode (spectators cannot pick up or eat, but walk).
 *
 * <p>Threads: inventory, meal and move events run on the thread that owns the player; the death event on the entity's
 * region ({@code fire} hops to the player's thread). The biome check runs only when a player enters a new 4x4x4 cell, so
 * walking costs one map lookup per event.
 */
public final class CriteriaListener implements Listener {
    private final CriteriaMap map;
    private final Advancements advancements;
    private final Function<ItemStack, Optional<String>> itemId;
    private final BiPredicate<String, String> itemInTag;
    private final BiPredicate<String, String> entityInTag;
    /** The biome key at a location ({@code tfp:plains}), read on the thread that owns it. */
    private final Function<Location, String> biomeAt;
    /** The last 4x4x4 cell each player was checked in. */
    private final Map<UUID, Long> cells = new ConcurrentHashMap<>();

    public CriteriaListener(CriteriaMap map, Advancements advancements, Function<ItemStack, Optional<String>> itemId,
            BiPredicate<String, String> itemInTag, BiPredicate<String, String> entityInTag,
            Function<Location, String> biomeAt) {
        this.map = Objects.requireNonNull(map, "map");
        this.advancements = Objects.requireNonNull(advancements, "advancements");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.itemInTag = Objects.requireNonNull(itemInTag, "itemInTag");
        this.entityInTag = Objects.requireNonNull(entityInTag, "entityInTag");
        this.biomeAt = Objects.requireNonNull(biomeAt, "biomeAt");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        onItem(event.getPlayer(), event.getNewItemStack());
    }

    void onItem(Player player, ItemStack stack) {
        Optional<String> id = itemId.apply(stack);
        if (id.isEmpty()) {
            return;
        }
        for (CriterionRef c : map.inventory(id.get(), itemInTag, d -> advancements.isAwarded(player, d))) {
            advancements.fire(player, c);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMeal(NutritionFoodEatenEvent event) {
        Player player = event.getPlayer();
        map.consumed(event.itemId()).ifPresent(c -> advancements.fire(player, c));
        for (CriterionRef c : map.meal(event.rotten(), event.averageNutrition())) {
            advancements.fire(player, c);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) {
            return;
        }
        for (CriterionRef c : map.kills(entityId(dead), entityInTag)) {
            advancements.fire(killer, c);
        }
    }

    /** Our id of an entity: a TFC animal's species, else its vanilla type key. */
    static String entityId(Entity entity) {
        Optional<String> species = AnimalPdc.read(entity.getPersistentDataContainer()).map(r -> r.species());
        String id = species.orElseGet(() -> entity.getType().getKey().toString());
        return id.startsWith("tfc:") ? "tfp:" + id.substring(4) : id;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Player player = event.getPlayer();
        long cell = ((long) (to.getBlockX() >> 2) & 0x3FFFFFFL) << 38 | ((long) (to.getBlockZ() >> 2) & 0x3FFFFFFL) << 12
            | ((long) (to.getBlockY() >> 2) & 0xFFFL);
        Long before = cells.put(player.getUniqueId(), cell);
        if (before != null && before == cell) {
            return;
        }
        onPosition(player, to);
    }

    /** Checks the position-bound criteria for a player at {@code at}. Thread that owns the player. */
    void onPosition(Player player, Location at) {
        List<CriterionRef> hits = new java.util.ArrayList<>(2);
        hits.addAll(map.biome(biomeAt.apply(at)));
        map.position(at.getBlockZ()).ifPresent(hits::add);
        for (CriterionRef c : hits) {
            if (!advancements.isAwarded(player, c)) {
                advancements.fire(player, c);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cells.remove(event.getPlayer().getUniqueId());
    }
}
