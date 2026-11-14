/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (toggleSeal's block state and
 * WOOD_PLACE sound at pitch 0.85; attack: NOTE_BLOCK_BASEDRUM, RECORDS, volume 3),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (onSeal / onUnseal sounds,
 * the instant recipe sound, ejectInventory's PLAYER_SPLASH),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (playTransferSound),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (spawnItem),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/BarrelBlockEntityRenderer.java @ c3b4d9536 (the fluid face)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.gui.BarrelFluidVisuals;
import dev.tfp.paper.barrel.gui.BarrelTank;
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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
 * The server side of {@link BarrelEffects}. The {@code sealed} block state is written by a region task after the callback
 * with {@code setBlockData(data, false)} (CraftEngine keeps the block entity while the block stays the same barrel). TFC's
 * own barrel sounds ({@code tfc:block.barrel.open/close}) are not in the pack: the vanilla barrel's stand in (ledger).
 *
 * <p>PAR-PROC-35: the fluid surface (TFC {@code BarrelBlockEntityRenderer}) is one item display per barrel, marked with
 * {@link #SURFACE} = the block position (found again after a restart, removed with the block), showing the pack's flat
 * still-water plane {@link #SURFACE_MODEL} tinted with the fluid's colour (custom model data colour 0), scaled and raised
 * to {@link BarrelFluidVisuals#surface}. A CraftEngine {@code entity_renderer} follows block states only, and a state per
 * fill level and fluid colour would not fit, so the plane is a real display, as the quern's parts.
 */
public final class PaperBarrelEffects implements BarrelEffects {
    static final Sound OPEN = Sound.BLOCK_BARREL_OPEN;
    static final Sound CLOSE = Sound.BLOCK_BARREL_CLOSE;
    /** PDC key on the fluid surface display: {@code "x,y,z"}. */
    public static final NamespacedKey SURFACE = new NamespacedKey("tfp", "barrel_fluid");
    /** The item model of the surface plane (datagen {@code BarrelScreenArt}): a 1 x 1 block face at model y 8, tint 0. */
    public static final String SURFACE_MODEL = "tfp:render/barrel_fluid";

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;

    public PaperBarrelEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
    }

    private static Location centre(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    @Override
    public void sealed(Block block, BlockStateRef wanted, boolean sealed) {
        Location at = centre(block);
        block.getWorld().playSound(at, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.85f);
        block.getWorld().playSound(at, sealed ? CLOSE : OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> syncState(block, wanted));
    }

    private void syncState(Block block, BlockStateRef wanted) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().id().equals(wanted.id()) || now.get().equals(wanted)) {
            return; // broken, replaced or already right
        }
        Optional<BlockData> data = visuals.get().blockData(wanted);
        data.ifPresent(d -> block.setBlockData(d, false));
    }

    @Override
    public void recipe(Block block, String sound) {
        block.getWorld().playSound(centre(block), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
        boolean lava = TfcFluids.LAVA.equals(fluid.fluid());
        Sound sound = intoItem
            ? (lava ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL)
            : (lava ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY);
        block.getWorld().playSound(centre(block), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        block.getWorld().dropItem(centre(block), stack, item -> item.setPickupDelay(0));
    }

    @Override
    public void drum(Block block, float pitch) {
        block.getWorld().playSound(centre(block), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS, 3.0f, pitch);
    }

    @Override
    public void spilled(Block block, FluidAmount fluid) {
        block.getWorld().playSound(centre(block), Sound.ENTITY_PLAYER_SPLASH, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public void surface(Block block, FluidAmount fluid, int capacity) {
        List<ItemDisplay> found = surfaces(block);
        BarrelFluidVisuals.Surface face = BarrelFluidVisuals.surface(fluid.amount(), capacity);
        if (face == null) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = surfaceItem(fluid.fluid());
        Transformation t = transformation(face);
        Location at = block.getLocation().add(0.5, 0, 0.5);
        if (found.isEmpty()) {
            block.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(t);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(SURFACE, PersistentDataType.STRING, marker(block));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        if (!shown.equals(kept.getItemStack())) {
            kept.setItemStack(shown);
        }
        kept.setInterpolationDuration(0);
        kept.setTransformation(t);
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    /** The plane item of {@code fluidId}: the surface model tinted with the fluid's colour. */
    static ItemStack surfaceItem(String fluidId) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(SURFACE_MODEL));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(Color.fromRGB(BarrelTank.tint(fluidId) & 0xFFFFFF)));
        meta.setCustomModelDataComponent(cmd);
        stack.setItemMeta(meta);
        return stack;
    }

    /** TFC's face rectangle and height: the unit plane at the display's origin, scaled to the rectangle, raised to y. */
    static Transformation transformation(BarrelFluidVisuals.Surface face) {
        float sx = face.maxX() - face.minX();
        float sz = face.maxZ() - face.minZ();
        float cx = (face.minX() + face.maxX()) / 2f - 0.5f;
        float cz = (face.minZ() + face.maxZ()) / 2f - 0.5f;
        return new Transformation(new Vector3f(cx, face.y(), cz), new Quaternionf(), new Vector3f(sx, 1f, sz),
            new Quaternionf());
    }

    static List<ItemDisplay> surfaces(Block block) {
        String key = marker(block);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : block.getWorld().getNearbyEntities(BoundingBox.of(block), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(SURFACE, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
