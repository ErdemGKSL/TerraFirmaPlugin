/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/BowlBlockEntityRenderer.java @ c3b4d9536 (render: the textured face
 *       at Mth.map(count, 0, 16, 0.5, 2) 16ths, 4..12 wide)
 */
package dev.tfp.paper.bowl;

import dev.tfp.core.bowl.BowlRules;
import dev.tfp.paper.integration.CustomItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
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
 * The server side of {@link BowlEffects}. TFC draws the powder with a block entity renderer (a textured face 8x8 px wide at a
 * height of 0.5 to 2 px by the count). A CraftEngine {@code entity_renderer} follows block states only, so the layer is one
 * persistent item display of the render item {@code tfp:render/powder/<texture>} (a flat quad on TFC's
 * {@code block/powder/<texture>}; datagen {@code BowlBlocks}), standing at the block centre and moved up by the count. It
 * carries {@link #MARKER} = block position, so it is found again after a restart and removed with the bowl.
 */
public final class PaperBowlEffects implements BowlEffects {
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "bowl_powder");
    /** The render item of a powder texture (datagen {@code BowlBlocks.renderItem}). */
    public static final String RENDER_PREFIX = "tfp:render/powder/";

    private final CustomItems items;

    public PaperBowlEffects(CustomItems items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    /** The item display of {@code content}: the render item of its texture, the vanilla item name for the id of {@code content}. */
    Optional<ItemStack> renderItem(ItemStack content) {
        String id = items.idOf(content).orElseGet(() -> content.getType().getKey().toString());
        return items.create(RENDER_PREFIX + BowlRules.texture(id), 1);
    }

    @Override
    public void show(Block block, ItemStack content) {
        List<ItemDisplay> found = displays(block);
        if (content == null || content.getType().isAir() || content.getAmount() <= 0) {
            found.forEach(Entity::remove);
            return;
        }
        Optional<ItemStack> shown = renderItem(content);
        if (shown.isEmpty()) {
            found.forEach(Entity::remove);
            return;
        }
        Transformation t = transformation(content.getAmount());
        if (found.isEmpty()) {
            World world = block.getWorld();
            Location at = new Location(world, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, 0f, 0f);
            world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown.get());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(t);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(block));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        if (!shown.get().equals(kept.getItemStack())) {
            kept.setItemStack(shown.get());
        }
        kept.setTransformation(t);
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    /** The model is a quad on the floor of the block (an item display centres a block model on the entity): raised by the height. */
    static Transformation transformation(int count) {
        float y = (float) (BowlRules.layerHeight(count) / 16.0);
        return new Transformation(new Vector3f(0f, y, 0f), new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf());
    }

    @Override
    public void hide(Block block) {
        displays(block).forEach(Entity::remove);
    }

    @Override
    public void sand(Block block) {
        play(block, Sound.BLOCK_SAND_PLACE);
    }

    @Override
    public void placed(Block block, boolean wooden) {
        play(block, wooden ? Sound.BLOCK_WOOD_PLACE : Sound.BLOCK_STONE_PLACE);
    }

    private static void play(Block block, Sound sound) {
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    static List<ItemDisplay> displays(Block block) {
        String key = marker(block);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : block.getWorld().getNearbyEntities(BoundingBox.of(block), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
