/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/IngotPileBlockModel.java @ c3b4d9536 (the ingots drawn at their places; here
 *       persistent item displays, as the mold table's mold)
 */
package dev.tfp.paper.pile;

import dev.tfp.core.pile.PileKind;
import dev.tfp.core.pile.PileVisual;
import dev.tfp.core.pile.PileVisual.Part;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The ingots of a pile as persistent {@link ItemDisplay}s tagged with {@link #MARKER}: one per ingot, one per full layer of a
 * single metal ({@link PileVisual#plan}). TFC draws a textured trapezoid per ingot with a custom model loader; CraftEngine has
 * no element for per-block data (ledger divergence). A display is a plain paper stack whose item model is
 * {@code tfp:render/pile/<kind>_<variant>} and whose custom model data string 0 is the metal; the pack's definition selects the
 * model. {@link #sync} makes the world match a plan, touching only the displays that differ. Region thread of the block.
 */
final class PileDisplays {
    /** Item display PDC tag: {@code x,y,z|<part signature>}. */
    static final NamespacedKey MARKER = new NamespacedKey("tfp", "ingot_pile_part");
    /** Where the displays stand: the block's centre; the pack models are drawn in block coordinates around it. */
    static final double CENTRE = 0.5;
    /** Half of the blocks search box over the centre, so the zero-size displays are found. */
    static final double SEARCH = 0.25;
    private static final Quaternionf IDENTITY = new Quaternionf();
    private static final Quaternionf TURN = new Quaternionf().rotateY((float) Math.toRadians(90));

    private PileDisplays() {
    }

    /** The item model id of a part: {@code tfp:render/pile/ingot_single}. */
    static String itemModel(PileKind kind, PileVisual.Variant variant) {
        String k = kind == PileKind.INGOT ? "ingot" : "double_ingot";
        return "tfp:render/pile/" + k + "_" + variant.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The stack of a part: paper with the pile item model and the metal as custom model data string 0. */
    static ItemStack stack(PileKind kind, Part part) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(itemModel(kind, part.variant())));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(part.metal()));
        meta.setCustomModelDataComponent(cmd);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * The turn and move of a part. The model is drawn in block coordinates around the display; an odd layer is turned 90 degrees
     * about the block's vertical axis (TFC {@code Axis.YP.rotationDegrees(90)} between two translations), so the part's offset
     * is turned with it.
     */
    static Transformation transformation(Part part) {
        Vector3f t = new Vector3f((float) part.at().x(), (float) part.at().y(), (float) part.at().z());
        if (part.at().rotated()) {
            Vector3f horizontal = new Vector3f(t.x, 0, t.z);
            TURN.transform(horizontal);
            t = new Vector3f(horizontal.x, t.y, horizontal.z);
            return new Transformation(t, new Quaternionf(TURN), new Vector3f(1, 1, 1), new Quaternionf());
        }
        return new Transformation(t, new Quaternionf(IDENTITY), new Vector3f(1, 1, 1), new Quaternionf());
    }

    /**
     * Makes the displays of {@code block} the plan {@code parts}: displays no part names are removed, parts with no display are
     * spawned, the others stay. {@code light} is the (block, sky) light the displays are drawn with (the block they stand in is
     * a transparent carrier, which may be a full opaque outline).
     */
    static void sync(Block block, PileKind kind, List<Part> parts, int blockLight, int skyLight) {
        Map<String, ItemDisplay> found = new HashMap<>();
        for (ItemDisplay d : displays(block)) {
            String signature = signature(block, d);
            if (signature == null || found.putIfAbsent(signature, d) != null) {
                d.remove(); // a stray or a duplicate
            }
        }
        Set<String> wanted = new HashSet<>();
        Display.Brightness brightness = new Display.Brightness(clamp(blockLight), clamp(skyLight));
        for (Part part : parts) {
            wanted.add(part.signature());
            ItemDisplay existing = found.get(part.signature());
            if (existing != null) {
                if (!brightness.equals(existing.getBrightness())) {
                    existing.setBrightness(brightness);
                }
                continue;
            }
            Location at = new Location(block.getWorld(), block.getX() + CENTRE, block.getY() + CENTRE, block.getZ() + CENTRE);
            ItemStack shown = stack(kind, part);
            Transformation t = transformation(part);
            block.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(t);
                d.setBrightness(brightness);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, key(block) + "|" + part.signature());
            });
        }
        for (Map.Entry<String, ItemDisplay> e : found.entrySet()) {
            if (!wanted.contains(e.getKey())) {
                e.getValue().remove();
            }
        }
    }

    /** Removes every display of the pile (it is gone). */
    static void clear(Block block) {
        displays(block).forEach(Entity::remove);
    }

    /** The signatures of the displays standing in {@code block}, for tests and debugging. */
    static List<String> signatures(Block block) {
        List<String> out = new ArrayList<>();
        for (ItemDisplay d : displays(block)) {
            String s = signature(block, d);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<ItemDisplay> displays(Block block) {
        String prefix = key(block) + "|";
        List<ItemDisplay> out = new ArrayList<>();
        BoundingBox box = new BoundingBox(block.getX() + CENTRE - SEARCH, block.getY() + CENTRE - SEARCH,
            block.getZ() + CENTRE - SEARCH, block.getX() + CENTRE + SEARCH, block.getY() + CENTRE + SEARCH,
            block.getZ() + CENTRE + SEARCH);
        for (Entity e : block.getWorld().getNearbyEntities(box, ItemDisplay.class::isInstance)) {
            String tag = e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING);
            if (tag != null && tag.startsWith(prefix)) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    private static String signature(Block block, ItemDisplay d) {
        String tag = d.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING);
        String prefix = key(block) + "|";
        return tag != null && tag.startsWith(prefix) ? tag.substring(prefix.length()) : null;
    }

    static String key(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static int clamp(int light) {
        return Math.max(0, Math.min(15, light));
    }
}
