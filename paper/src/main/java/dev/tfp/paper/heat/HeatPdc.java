/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (the stored temperature and last tick)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatState;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of {@link HeatState} (M3-HEAT-01). The state lives in the stack's persistent data under
 * {@value #KEY} as a nested container, readable with {@code /data get}:
 * <pre>
 * tfp:heat = { tfp:v: int (version), tfp:temperature: float (°C at the last tick), tfp:tick: long (calendar tick of the record) }
 * </pre>
 * Base values only: the current temperature is derived at read time from the record and the heat capacity (lazy
 * cooling, nothing ticks). Other PDC entries and the rest of the item meta are left untouched.
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack.
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>temperature, tick (M3-HEAT-01)</li>
 * </ol>
 */
public final class HeatPdc {
    public static final String KEY = "tfp:heat";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey TEMPERATURE = new NamespacedKey("tfp", "temperature");
    private static final NamespacedKey TICK = new NamespacedKey("tfp", "tick");

    private HeatPdc() {
    }

    /** The stack's heat state, or empty when the stack has no {@value #KEY} entry (never heated). */
    public static Optional<HeatState> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return read(stack.getItemMeta().getPersistentDataContainer());
    }

    /** Reads the entry from a container. Absent, malformed or newer-version entries read as empty. */
    public static Optional<HeatState> read(PersistentDataContainer pdc) {
        PersistentDataContainer heat = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (heat == null) {
            return Optional.empty();
        }
        Integer v = heat.get(V, PersistentDataType.INTEGER);
        Float temperature = heat.get(TEMPERATURE, PersistentDataType.FLOAT);
        Long tick = heat.get(TICK, PersistentDataType.LONG);
        if (v == null || v > VERSION || temperature == null || tick == null) {
            return Optional.empty();
        }
        return Optional.of(new HeatState(temperature, tick));
    }

    /**
     * Writes {@code state} into the stack's PDC ({@code null} removes the entry). Every other item meta and PDC
     * value is kept. Returns {@code false} and leaves the stack unchanged when it has no item meta (air) or holds
     * an entry of a newer version than this plugin knows.
     */
    public static boolean write(ItemStack stack, HeatState state) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !write(meta.getPersistentDataContainer(), state)) {
            return false;
        }
        stack.setItemMeta(meta);
        return true;
    }

    /** As {@link #write(ItemStack, HeatState)} on a container. */
    public static boolean write(PersistentDataContainer pdc, HeatState state) {
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (existing != null) {
            Integer v = existing.get(V, PersistentDataType.INTEGER);
            if (v != null && v > VERSION) {
                return false;
            }
        }
        if (state == null) {
            pdc.remove(ROOT);
            return true;
        }
        PersistentDataContainer heat = pdc.getAdapterContext().newPersistentDataContainer();
        heat.set(V, PersistentDataType.INTEGER, VERSION);
        heat.set(TEMPERATURE, PersistentDataType.FLOAT, state.temperature());
        heat.set(TICK, PersistentDataType.LONG, state.lastTick());
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, heat);
        return true;
    }

    /** Whether the stack carries a {@value #KEY} entry of any version. */
    public static boolean has(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(ROOT, PersistentDataType.TAG_CONTAINER);
    }
}
