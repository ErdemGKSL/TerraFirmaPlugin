/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/glass/GlassOperations.java @ c3b4d9536 (the batch and the steps)
 */
package dev.tfp.paper.glass;

import dev.tfp.core.glass.GlassOperation;
import dev.tfp.core.glass.GlassState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of TFC's glass component ({@value #KEY}): the batch item and the operations so far, in the layout the
 * tooltip reads ({@code tfp:v} = 1, {@code tfp:batch} = the batch item id, {@code tfp:steps} = operation paths such as
 * {@code blow}). The core state holds TFC's ids ({@code tfc:silica_glass_batch}, {@code tfc:blow}); the record holds ours
 * ({@code tfp:silica_glass_batch}, {@code blow}). Call on the stack's owning thread.
 */
public final class GlassPdc {
    public static final String KEY = "tfp:glass";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    private static final NamespacedKey V = Objects.requireNonNull(NamespacedKey.fromString("tfp:v"));
    private static final NamespacedKey BATCH = Objects.requireNonNull(NamespacedKey.fromString("tfp:batch"));
    private static final NamespacedKey STEPS = Objects.requireNonNull(NamespacedKey.fromString("tfp:steps"));

    private GlassPdc() {
    }

    /** {@code tfc:silica_glass_batch} to {@code tfp:silica_glass_batch}. */
    static String toOurs(String id) {
        return id.startsWith("tfc:") ? "tfp:" + id.substring(4) : id;
    }

    /** {@code tfp:silica_glass_batch} to {@code tfc:silica_glass_batch}. */
    static String toTfc(String id) {
        return id.startsWith("tfp:") ? "tfc:" + id.substring(4) : id;
    }

    /** The glass state of {@code stack}, or empty when it has none (or a newer record). */
    public static Optional<GlassState> read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer r = stack.getItemMeta().getPersistentDataContainer().get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (r == null) {
            return Optional.empty();
        }
        Integer v = r.get(V, PersistentDataType.INTEGER);
        String batch = r.get(BATCH, PersistentDataType.STRING);
        if (v == null || v > VERSION || batch == null || batch.isEmpty()) {
            return Optional.empty();
        }
        List<String> paths = r.get(STEPS, PersistentDataType.LIST.strings());
        List<GlassOperation> steps = new ArrayList<>();
        for (String path : paths == null ? List.<String>of() : paths) {
            GlassOperation.byId("tfc:" + path).ifPresent(steps::add);
        }
        return Optional.of(new GlassState(steps, toTfc(batch)));
    }

    /**
     * Writes {@code state} onto {@code stack}; an empty state removes the record. Refuses (false) a stack without meta or
     * one that carries a newer record.
     */
    public static boolean write(ItemStack stack, GlassState state) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        Integer v = existing == null ? null : existing.get(V, PersistentDataType.INTEGER);
        if (v != null && v > VERSION) {
            return false;
        }
        if (state == null || state.isEmpty()) {
            pdc.remove(ROOT);
        } else {
            PersistentDataContainer r = pdc.getAdapterContext().newPersistentDataContainer();
            r.set(V, PersistentDataType.INTEGER, VERSION);
            r.set(BATCH, PersistentDataType.STRING, toOurs(state.batch()));
            r.set(STEPS, PersistentDataType.LIST.strings(), state.steps().stream().map(op -> op.id().substring("tfc:".length())).toList());
            pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, r);
        }
        stack.setItemMeta(meta);
        return true;
    }
}
