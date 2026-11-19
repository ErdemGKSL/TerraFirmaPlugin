/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/AnvilBlockEntityRenderer.java @ c3b4d9536 (what is drawn
 * where)
 */
package dev.tfp.paper.anvil;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilVisuals;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The server side of {@link AnvilEffects}. TFC draws the hammer, the two inputs and the flux with a block entity renderer;
 * a CraftEngine {@code entity_renderer} follows block states only, so each slot is a real item display entity here (marked
 * with {@link #MARKER} = block position and slot, so it is found again after a restart and removed with the block), posed
 * by {@link AnvilVisuals}. The placement facing is written on the next tick with {@code setBlockData(data, false)}:
 * CraftEngine keeps the block entity while the block stays the same anvil.
 */
public final class PaperAnvilEffects implements AnvilEffects {
    /** PDC key on a display: {@code "x,y,z:slot"}. */
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "anvil_part");

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;

    public PaperAnvilEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    @Override
    public void show(Block block, View view) {
        for (int slot = 0; slot < AnvilSlots.COUNT; slot++) {
            draw(block, slot, view.stack(slot), view);
        }
    }

    @Override
    public void hide(Block block) {
        for (int slot = 0; slot < AnvilSlots.COUNT; slot++) {
            displays(block, slot).forEach(Entity::remove);
        }
    }

    @Override
    public void state(Block block, BlockStateRef wanted) {
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().id().equals(wanted.id()) || now.get().equals(wanted)) {
                return; // broken or replaced meanwhile, or already there
            }
            Optional<BlockData> data = visuals.get().blockData(wanted);
            data.ifPresent(d -> block.setBlockData(d, false));
        });
    }

    private void draw(Block block, int slot, ItemStack stack, View view) {
        List<ItemDisplay> found = displays(block, slot);
        if (StackCodec.isEmpty(stack)) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + AnvilVisuals.displayY(view.tier()),
            block.getZ() + 0.5);
        Transformation transformation = transformation(slot, view.rotation());
        if (found.isEmpty()) {
            block.getWorld().spawn(at, ItemDisplay.class, d -> {
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
        if (!kept.getLocation().toVector().equals(at.toVector())) {
            kept.teleport(at);
        }
        kept.setTransformation(transformation);
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    /** The display transformation of a slot: translation, left rotation and scale of {@link AnvilVisuals#pose}. */
    static Transformation transformation(int slot, int rotation) {
        AnvilVisuals.Pose pose = AnvilVisuals.pose(slot, rotation);
        double[] q = pose.rotation();
        return new Transformation(new Vector3f((float) pose.tx(), (float) pose.ty(), (float) pose.tz()),
            new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]), new Vector3f(pose.scale()),
            new Quaternionf());
    }

    static List<ItemDisplay> displays(Block block, int slot) {
        String key = marker(block, slot);
        List<ItemDisplay> out = new ArrayList<>();
        World world = block.getWorld();
        for (Entity e : world.getNearbyEntities(BoundingBox.of(block), ItemDisplay.class::isInstance)) {
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
