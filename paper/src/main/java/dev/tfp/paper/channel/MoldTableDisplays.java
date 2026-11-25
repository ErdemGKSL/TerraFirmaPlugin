/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/MoldTableBlockModel.java @ c3b4d9536 (the mold drawn in the table's
 *       well; here a persistent item display, as the grill's items)
 */
package dev.tfp.paper.channel;

import dev.tfp.paper.device.StackCodec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The mold and the casting on a mold table as two persistent {@link ItemDisplay}s tagged with {@link #MARKER} (TFC draws
 * the mold with a custom model loader; CraftEngine has no element for a block entity's items, ledger divergence). One display
 * per slot at most; {@link #sync} makes the world match the slots. Region thread of the table.
 */
final class MoldTableDisplays {
    /** Item display PDC tag: {@code x,y,z:slot}. */
    static final NamespacedKey MARKER = new NamespacedKey("tfp", "mold_table_item");
    /** The well floor is at 1/16; the mold lies on it. */
    static final float MOLD_Y = 1.6f / 16f;
    /** The casting sits on the mold. */
    static final float OUTPUT_Y = 3.2f / 16f;
    static final float MOLD_SCALE = 0.75f;
    static final float OUTPUT_SCALE = 0.6f;

    private MoldTableDisplays() {
    }

    /** Draws {@code mold} (slot 0) and {@code output} (slot 1) over {@code table}; an empty stack removes its display. */
    static void sync(Block table, ItemStack mold, ItemStack output) {
        draw(table, 0, mold, MOLD_Y, MOLD_SCALE);
        draw(table, 1, output, OUTPUT_Y, OUTPUT_SCALE);
    }

    /** Removes both displays (the table is gone). */
    static void clear(Block table) {
        for (int slot = 0; slot < 2; slot++) {
            displays(table, slot).forEach(Entity::remove);
        }
    }

    private static void draw(Block table, int slot, ItemStack stack, float y, float scale) {
        List<ItemDisplay> found = displays(table, slot);
        if (StackCodec.isEmpty(stack)) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        if (found.isEmpty()) {
            Location at = new Location(table.getWorld(), table.getX() + 0.5, table.getY() + y, table.getZ() + 0.5);
            table.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(transformation(scale));
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(table, slot));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        if (!shown.equals(kept.getItemStack())) {
            kept.setItemStack(shown);
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    /** Lying flat, face up. */
    static Transformation transformation(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.toRadians(-90)),
            new Vector3f(scale), new Quaternionf());
    }

    static List<ItemDisplay> displays(Block table, int slot) {
        String key = marker(table, slot);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : table.getWorld().getNearbyEntities(BoundingBox.of(table), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block table, int slot) {
        return table.getX() + "," + table.getY() + "," + table.getZ() + ":" + slot;
    }
}
