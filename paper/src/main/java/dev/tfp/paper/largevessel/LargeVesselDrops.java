/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (appendHoverText: the
 *       "Contents:" line and the inventory under the item name),
 *       src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536
 *       (collectImplicitComponents: the contents ride on the dropped item)
 */
package dev.tfp.paper.largevessel;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.item.feature.ItemCount;
import dev.tfp.core.largevessel.LargeVesselState;
import dev.tfp.core.persist.PersistException;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.itemdata.ContentsPdc;
import dev.tfp.paper.tooltip.feature.FeatureData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-POT-16: a dropped large vessel that carries its saved record ({@code tfp:device}, written by the device framework at
 * {@code HIGHEST}) also gets the tooltip summary of its slots, {@code tfp:contents = { tfp:v, tfp:view }}, so the
 * item-list tooltip (PAR-INFRA-50, "Contents:" and the stacks) reads it on the Netty thread without decoding stacks. The
 * summary has no {@code tfp:items}: the record stays the authority. Runs at {@code MONITOR} on the item's region thread.
 */
final class LargeVesselDrops implements Listener {
    private static final NamespacedKey ROOT = new NamespacedKey("tfp", "contents");
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey VIEW = new NamespacedKey("tfp", "view");

    private final CustomItems items;
    private final DeviceKind<LargeVesselState> kind;
    private final StackCodec stacks;

    LargeVesselDrops(CustomItems items, DeviceKind<LargeVesselState> kind, StackCodec stacks) {
        this.items = Objects.requireNonNull(items, "items");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        if (!(event.getEntity() instanceof Item entity)) {
            return;
        }
        ItemStack stack = entity.getItemStack();
        if (summarise(stack)) {
            entity.setItemStack(stack);
        }
    }

    /** Writes the summary onto {@code stack} when it is a large vessel with a saved record that holds items. */
    boolean summarise(ItemStack stack) {
        if (StackCodec.isEmpty(stack) || items.idOf(stack).filter(LargeVesselDevice::covers).isEmpty()) {
            return false;
        }
        Optional<byte[]> saved = DeviceService.savedRecord(stack);
        if (saved.isEmpty()) {
            return false;
        }
        List<String> view = new ArrayList<>();
        try {
            DeviceRecord<LargeVesselState> record = kind.codec().fromBytes(saved.get());
            for (int i = 0; i < kind.slots(); i++) {
                ItemStack slot = stacks.decode(record.slot(i));
                if (StackCodec.isEmpty(slot)) {
                    continue;
                }
                String id = items.idOf(slot).orElse(slot.getType().getKey().toString());
                view.add(FeatureData.viewEntry(new ItemCount(id, id, slot.getAmount())));
            }
        } catch (PersistException e) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (view.isEmpty()) {
            if (!pdc.has(ROOT)) {
                return false;
            }
            pdc.remove(ROOT);
        } else {
            PersistentDataContainer contents = pdc.getAdapterContext().newPersistentDataContainer();
            contents.set(V, PersistentDataType.INTEGER, ContentsPdc.VERSION);
            contents.set(VIEW, PersistentDataType.LIST.strings(), view);
            pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, contents);
        }
        stack.setItemMeta(meta);
        return true;
    }
}
