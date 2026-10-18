/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodComponent.java @ c3b4d9536 (CODEC)
 */
package dev.tfp.paper.food;

import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of {@link FoodState} (M3-SURV-01). The state lives in the stack's persistent data under
 * {@value #KEY} as a nested container, readable with {@code /data get}:
 * <pre>
 * tfp:food = { tfp:v: int (version), tfp:created: long (player ticks or a FoodDecay flag), tfp:traits: [string trait ids, in order] }
 * </pre>
 * Base values only: rot date, stage and rotten flag are derived at read time from the creation date and the
 * current player tick, never stored. Other PDC entries and the rest of the item meta are left untouched.
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack (its region or entity
 * thread for a live inventory, any thread for a snapshot copy).
 *
 * <h2>Version history</h2>
 * <ol>
 *   <li>created, traits (M3-SURV-01)</li>
 * </ol>
 */
public final class FoodPdc {
    public static final String KEY = "tfp:food";
    public static final int VERSION = 1;

    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(KEY));
    private static final NamespacedKey V = new NamespacedKey("tfp", "v");
    private static final NamespacedKey CREATED = new NamespacedKey("tfp", "created");
    private static final NamespacedKey TRAITS = new NamespacedKey("tfp", "traits");

    /**
     * Default trait lookup: the TFC default traits by id; an unknown id (a trait a newer version or another
     * config defined) becomes a neutral trait (modifier 1) with the same id, so a read/write round trip keeps it.
     */
    public static final Function<String, FoodTrait> DEFAULT_TRAITS = id -> {
        FoodTrait t = FoodTrait.byId(id);
        return t != null ? t : new FoodTrait(id, 1f);
    };

    private FoodPdc() {
    }

    /** The stack's food state, or empty when the stack has no {@value #KEY} entry (not a dated food). */
    public static Optional<FoodState> read(ItemStack stack) {
        return read(stack, DEFAULT_TRAITS);
    }

    /** As {@link #read(ItemStack)}, resolving trait ids with {@code traits}. */
    public static Optional<FoodState> read(ItemStack stack, Function<String, FoodTrait> traits) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return read(stack.getItemMeta().getPersistentDataContainer(), traits);
    }

    /**
     * Reads the entry from a container. Absent, malformed or newer-version entries read as empty (a newer
     * entry is not overwritten by {@link #write}, see there).
     */
    public static Optional<FoodState> read(PersistentDataContainer pdc, Function<String, FoodTrait> traits) {
        PersistentDataContainer food = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (food == null) {
            return Optional.empty();
        }
        Integer v = food.get(V, PersistentDataType.INTEGER);
        Long created = food.get(CREATED, PersistentDataType.LONG);
        if (v == null || v > VERSION || created == null) {
            return Optional.empty();
        }
        List<String> ids = food.getOrDefault(TRAITS, PersistentDataType.LIST.strings(), List.of());
        List<FoodTrait> list = new ArrayList<>(ids.size());
        for (String id : ids) {
            list.add(traits.apply(id));
        }
        return Optional.of(new FoodState(created, list));
    }

    /**
     * Writes {@code state} into the stack's PDC ({@code null} removes the entry). Every other item meta and PDC
     * value is kept. Returns {@code false} and leaves the stack unchanged when it has no item meta (air) or holds
     * an entry of a newer version than this plugin knows.
     */
    public static boolean write(ItemStack stack, FoodState state) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !write(meta.getPersistentDataContainer(), state)) {
            return false;
        }
        stack.setItemMeta(meta);
        return true;
    }

    /** As {@link #write(ItemStack, FoodState)} on a container. */
    public static boolean write(PersistentDataContainer pdc, FoodState state) {
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
        PersistentDataContainer food = pdc.getAdapterContext().newPersistentDataContainer();
        food.set(V, PersistentDataType.INTEGER, VERSION);
        food.set(CREATED, PersistentDataType.LONG, state.creationDate());
        List<String> ids = new ArrayList<>(state.traits().size());
        for (FoodTrait t : state.traits()) {
            ids.add(t.id());
        }
        food.set(TRAITS, PersistentDataType.LIST.strings(), ids);
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, food);
        return true;
    }

    /** Whether the stack carries a {@value #KEY} entry of any version. */
    public static boolean has(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(ROOT, PersistentDataType.TAG_CONTAINER);
    }

    /** A copy of {@code stack} without the food entry (for comparing everything else). */
    static ItemStack withoutFood(ItemStack stack) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(ROOT)) {
            meta.getPersistentDataContainer().remove(ROOT);
            copy.setItemMeta(meta);
        }
        return copy;
    }
}
