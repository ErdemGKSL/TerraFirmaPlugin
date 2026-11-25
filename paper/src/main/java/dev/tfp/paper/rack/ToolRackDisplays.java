/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/ToolRackBlockEntityRenderer.java @ c3b4d9536 (ITEM_LOCATION, META_TO_ANGLE, ITEM_SCALE)
 */
package dev.tfp.paper.rack;

import dev.tfp.core.chest.ChestSide;
import dev.tfp.core.rack.ToolRackRules;
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
 * The four tools of a tool rack as persistent {@link ItemDisplay}s tagged with {@link #MARKER} (TFC draws them with a block
 * entity renderer; CraftEngine has no element for a block entity's items, ledger divergence). One display per slot at most;
 * {@link #sync} makes the world match the slots. Region thread of the rack.
 *
 * <p>Positions, angles and the 0.5 scale are the renderer's tables; a vanilla item display already turns its item by half a
 * turn about y, so the display's rotation is the renderer's angle plus 180 degrees.
 */
public final class ToolRackDisplays {
    /** Item display PDC tag: {@code x,y,z:slot}. */
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "tool_rack_item");
    static final float HEIGHT_LOW = .28125f;
    static final float HEIGHT_HIGH = .78125f;
    static final float COLUMN_1 = .25f;
    static final float COLUMN_2 = .75f;
    static final float OFFSET = .09375f;
    static final float ITEM_SCALE = .5f;

    private ToolRackDisplays() {
    }

    /** The renderer's {@code META_TO_ANGLE} indexed by {@code get2DDataValue} (south 0, west 1, north 2, east 3). */
    static float angle(ChestSide facing) {
        return switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case NORTH -> 0f;
            case EAST -> 270f;
        };
    }

    /** The renderer's {@code ITEM_LOCATION[direction][slot]}: the offset of a slot's item inside the block. */
    static float[] location(ChestSide facing, int slot) {
        float y = slot < 2 ? HEIGHT_HIGH : HEIGHT_LOW;
        float column = slot % 2 == 0 ? COLUMN_1 : COLUMN_2;
        return switch (facing) {
            case SOUTH -> new float[] {column, y, OFFSET};
            case WEST -> new float[] {1f - OFFSET, y, column};
            case NORTH -> new float[] {column, y, 1f - OFFSET};
            case EAST -> new float[] {OFFSET, y, column};
        };
    }

    /** Draws the rack's {@code slots} (4 stacks); an empty stack removes its display. */
    public static void sync(Block rack, ChestSide facing, List<ItemStack> slots) {
        for (int slot = 0; slot < ToolRackRules.SLOTS; slot++) {
            draw(rack, facing, slot, slot < slots.size() ? slots.get(slot) : ItemStack.empty());
        }
    }

    /** Removes every display of the rack (it is gone). */
    public static void clear(Block rack) {
        for (int slot = 0; slot < ToolRackRules.SLOTS; slot++) {
            displays(rack, slot).forEach(Entity::remove);
        }
    }

    private static void draw(Block rack, ChestSide facing, int slot, ItemStack stack) {
        List<ItemDisplay> found = displays(rack, slot);
        if (StackCodec.isEmpty(stack)) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        float[] at = location(facing, slot);
        Location where = new Location(rack.getWorld(), rack.getX() + at[0], rack.getY() + at[1], rack.getZ() + at[2]);
        if (found.isEmpty()) {
            rack.getWorld().spawn(where, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(transformation(facing));
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(rack, slot));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        if (!shown.equals(kept.getItemStack())) {
            kept.setItemStack(shown);
        }
        kept.setTransformation(transformation(facing));
        if (kept.getLocation().distanceSquared(where) > 1e-6) {
            kept.teleport(where);
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    static Transformation transformation(ChestSide facing) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.toRadians(angle(facing) + 180f)),
            new Vector3f(ITEM_SCALE), new Quaternionf());
    }

    static List<ItemDisplay> displays(Block rack, int slot) {
        String key = marker(rack, slot);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : rack.getWorld().getNearbyEntities(BoundingBox.of(rack), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block rack, int slot) {
        return rack.getX() + "," + rack.getY() + "," + rack.getZ() + ":" + slot;
    }
}
