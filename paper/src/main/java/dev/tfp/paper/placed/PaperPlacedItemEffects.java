/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/PlacedItemBlockEntityRenderer.java @ c3b4d9536 (what is
 *       drawn where: PlacedVisuals)
 */
package dev.tfp.paper.placed;

import dev.tfp.core.placed.PlacedRules;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.core.placed.PlacedVisuals;
import dev.tfp.paper.device.StackCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The server side of {@link PlacedItemEffects}. TFC draws the four slots with a block entity renderer; a CraftEngine
 * {@code entity_renderer} follows block states only and cannot show an arbitrary item stack, so each drawn slot is a real
 * item display entity (as the anvil's and the scraping surface's) standing at the block centre with yaw 0, marked with {@link #MARKER} = block position and slot so
 * it is found again after a restart and removed with the block, posed by {@link PlacedVisuals}. Region thread only.
 */
public final class PaperPlacedItemEffects implements PlacedItemEffects {
    /** PDC key on a display: {@code "x,y,z:slot"}. */
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "placed_item");

    private final Predicate<ItemStack> asBlock;

    /** @param asBlock whether a stack draws a 3D block model (TFC {@code model.isGui3d()}) */
    public PaperPlacedItemEffects(Predicate<ItemStack> asBlock) {
        this.asBlock = Objects.requireNonNull(asBlock, "asBlock");
    }

    @Override
    public void show(Block block, PlacedState state, List<ItemStack> slots) {
        boolean[] occupied = new boolean[PlacedRules.SLOTS];
        for (int i = 0; i < PlacedRules.SLOTS; i++) {
            occupied[i] = i < slots.size() && !StackCodec.isEmpty(slots.get(i));
        }
        boolean[] drawn = PlacedRules.drawn(state, occupied);
        for (int slot = 0; slot < PlacedRules.SLOTS; slot++) {
            draw(block, slot, drawn[slot] ? slots.get(slot) : null, state);
        }
    }

    @Override
    public void hide(Block block) {
        for (int slot = 0; slot < PlacedRules.SLOTS; slot++) {
            displays(block, slot).forEach(Entity::remove);
        }
    }

    @Override
    public void placed(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        Sound sound = below.getBlockSoundGroup().getPlaceSound();
        block.getWorld().playSound(block.getLocation().add(0.5, 0.0, 0.5), sound, SoundCategory.BLOCKS, 0.5f, 1.0f);
    }

    private void draw(Block block, int slot, ItemStack stack, PlacedState state) {
        List<ItemDisplay> found = displays(block, slot);
        if (StackCodec.isEmpty(stack)) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        Transformation transformation = transformation(PlacedVisuals.pose(slot, state.large(), state.rotation(slot),
            asBlock.test(shown), block.getX(), block.getZ()));
        if (found.isEmpty()) {
            World world = block.getWorld();
            Location at = new Location(world, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, 0f, 0f);
            world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(transformation);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(block, slot));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        if (!shown.equals(kept.getItemStack())) {
            kept.setItemStack(shown);
        }
        kept.setTransformation(transformation);
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    static Transformation transformation(PlacedVisuals.Pose pose) {
        double[] q = pose.rotation();
        // The entity stands at the block centre (inside the block's box, so getNearbyEntities finds it); the pose is
        // relative to the block's minimum corner.
        return new Transformation(new Vector3f((float) (pose.tx() - 0.5), (float) (pose.ty() - 0.5), (float) (pose.tz() - 0.5)),
            new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]), new Vector3f(pose.scale()),
            new Quaternionf());
    }

    static List<ItemDisplay> displays(Block block, int slot) {
        String key = marker(block, slot);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : block.getWorld().getNearbyEntities(BoundingBox.of(block), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block block, int slot) {
        return block.getX() + "," + block.getY() + "," + block.getZ() + ":" + slot;
    }
}
