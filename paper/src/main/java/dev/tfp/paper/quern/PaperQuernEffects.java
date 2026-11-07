/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/QuernBlockEntityRenderer.java @ c3b4d9536 (what is drawn
 * where),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (startGrinding's drag sound
 * and pitch, serverTick's sounds and sendParticle),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (playSound, spawnItem, damageItem, triangle)
 */
package dev.tfp.paper.quern;

import dev.tfp.core.quern.QuernRules;
import dev.tfp.core.quern.QuernVisuals;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The server side of {@link QuernEffects}. TFC draws the handstone, the input and the output with a block entity renderer,
 * the handstone turned every frame. A CraftEngine {@code entity_renderer} follows block states only and snaps, so each part
 * is a real item display entity here (marked with {@link #MARKER} = block position and part, so it is found again after a
 * restart and removed with the block), and the turn is sent as {@link QuernVisuals#spin} interpolated steps. The block state
 * {@code has_handstone} (TFC {@code updateHandstone}) is written on the next tick with {@code setBlockData(data, false)}:
 * CraftEngine keeps the block entity while the block stays {@code tfp:quern}.
 */
public final class PaperQuernEffects implements QuernEffects {
    /** PDC key on a display: {@code "x,y,z:part"}. */
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "quern_part");
    /** TFC {@code TFCSounds.QUERN_DRAG} is not in the pack yet: the grindstone's sound stands in (ledger). */
    static final Sound DRAG = Sound.BLOCK_GRINDSTONE_USE;

    enum Part {
        HANDSTONE, INPUT, OUTPUT
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> finisher;

    /**
     * @param finisher calls the device back (the device router's neighbour update), at the finish tick
     */
    public PaperQuernEffects(Scheduler scheduler, CustomBlocks blocks, Supplier<BlockVisuals> visuals, Consumer<Block> finisher) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.finisher = Objects.requireNonNull(finisher, "finisher");
    }

    // ---- displays -----------------------------------------------------------------------------------

    @Override
    public void show(Block block, View view) {
        boolean hasHandstone = !StackCodec.isEmpty(view.handstone());
        draw(block, Part.HANDSTONE, view.handstone(), hasHandstone, view.grinding());
        draw(block, Part.INPUT, view.input(), hasHandstone, view.grinding());
        draw(block, Part.OUTPUT, view.output(), hasHandstone, view.grinding());
        scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> syncState(block, hasHandstone));
    }

    @Override
    public void hide(Block block) {
        for (Part p : Part.values()) {
            displays(block, p).forEach(Entity::remove);
        }
    }

    private void draw(Block block, Part part, ItemStack stack, boolean hasHandstone, boolean grinding) {
        List<ItemDisplay> found = displays(block, part);
        if (StackCodec.isEmpty(stack)) {
            found.forEach(Entity::remove);
            return;
        }
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        Location at = location(block, part, hasHandstone);
        if (found.isEmpty()) {
            block.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(transformation(part, 0));
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(block, part));
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
        if (part != Part.HANDSTONE || !grinding) {
            kept.setInterpolationDuration(0);
            kept.setTransformation(transformation(part, 0));
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    /** The display position of a part (TFC's renderer translations; the input sits higher on a handstone). */
    static Location location(Block block, Part part, boolean hasHandstone) {
        World w = block.getWorld();
        return switch (part) {
            case HANDSTONE -> new Location(w, block.getX() + 0.5, block.getY() + QuernVisuals.HANDSTONE_Y, block.getZ() + 0.5);
            case INPUT -> new Location(w, block.getX() + 0.5,
                block.getY() + (hasHandstone ? QuernVisuals.INPUT_Y_WITH_HANDSTONE : QuernVisuals.INPUT_Y), block.getZ() + 0.5);
            case OUTPUT -> new Location(w, block.getX() + QuernVisuals.OUTPUT_X, block.getY() + QuernVisuals.OUTPUT_Y,
                block.getZ() + QuernVisuals.OUTPUT_Z);
        };
    }

    /** The display transformation of a part; {@code angle}: the handstone's turn about +y, radians. */
    static Transformation transformation(Part part, double angle) {
        return switch (part) {
            case HANDSTONE -> new Transformation(new Vector3f(), new Quaternionf().rotateY((float) angle),
                new Vector3f(QuernVisuals.HANDSTONE_SCALE), new Quaternionf());
            case INPUT -> new Transformation(new Vector3f(),
                new Quaternionf(new AxisAngle4f((float) Math.toRadians(QuernVisuals.INPUT_YAW_DEGREES), 0, 1, 0)),
                new Vector3f(QuernVisuals.INPUT_SCALE), new Quaternionf());
            case OUTPUT -> new Transformation(new Vector3f(),
                new Quaternionf(new AxisAngle4f((float) Math.toRadians(QuernVisuals.OUTPUT_PITCH_DEGREES), 1, 0, 0)),
                new Vector3f(QuernVisuals.OUTPUT_SCALE), new Quaternionf());
        };
    }

    private void syncState(Block block, boolean hasHandstone) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().id().equals(QuernDevice.ID)) {
            return; // broken or replaced meanwhile
        }
        BlockStateRef wanted = QuernDevice.state(hasHandstone);
        if (wanted.equals(now.get())) {
            return;
        }
        Optional<BlockData> data = visuals.get().blockData(wanted);
        data.ifPresent(d -> block.setBlockData(d, false));
    }

    static List<ItemDisplay> displays(Block block, Part part) {
        String key = marker(block, part);
        List<ItemDisplay> out = new ArrayList<>();
        for (Entity e : block.getWorld().getNearbyEntities(BoundingBox.of(block), ItemDisplay.class::isInstance)) {
            if (key.equals(e.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))) {
                out.add((ItemDisplay) e);
            }
        }
        return out;
    }

    static String marker(Block block, Part part) {
        return block.getX() + "," + block.getY() + "," + block.getZ() + ":" + part.name().toLowerCase(java.util.Locale.ROOT);
    }

    // ---- the grind --------------------------------------------------------------------------------------

    @Override
    public void started(Block block, ItemStack input) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // TFC: playSound(QUERN_DRAG, BLOCKS, 1, 1 + (nextFloat - nextFloat) / 16)
        block.getWorld().playSound(centre(block), DRAG, SoundCategory.BLOCKS, 1f, 1f + (r.nextFloat() - r.nextFloat()) / 16f);
    }

    @Override
    public void spin(Block block) {
        World world = block.getWorld();
        int cx = block.getX() >> 4;
        int cz = block.getZ() >> 4;
        for (QuernVisuals.Step step : QuernVisuals.spin()) {
            scheduler.regionDelayed(world, cx, cz, Math.max(1, step.delay()), () -> {
                for (ItemDisplay d : displays(block, Part.HANDSTONE)) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(step.duration());
                    d.setTransformation(transformation(Part.HANDSTONE, step.angle()));
                }
                // TFC sends one input particle per tick while grinding; here one burst per step (explicit action only)
                for (ItemDisplay d : displays(block, Part.INPUT)) {
                    particles(block, d.getItemStack(), 4);
                }
            });
        }
        finishAt(block, QuernRules.TICKS_PER_GRIND);
    }

    @Override
    public void finishAt(Block block, long ticks) {
        scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, Math.max(1, ticks),
            () -> finisher.accept(block));
    }

    @Override
    public void finished(Block block, ItemStack brokenHandstone) {
        playTfc(block, Sound.ENTITY_ARMOR_STAND_FALL);
        if (!StackCodec.isEmpty(brokenHandstone)) {
            playTfc(block, Sound.BLOCK_STONE_BREAK);
            playTfc(block, Sound.ENTITY_ITEM_BREAK);
            particles(block, brokenHandstone, 15);
        }
    }

    /** TFC {@code Helpers.playSound}: volume {@code 1 + nextFloat}, pitch {@code nextFloat + 1}. */
    private static void playTfc(Block block, Sound sound) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().playSound(centre(block), sound, SoundCategory.BLOCKS, 1f + r.nextFloat(), r.nextFloat() + 0.7f + 0.3f);
    }

    /** TFC {@code sendParticle}: ITEM at (x + 0.5, y + 0.875, z + 0.5), spread (triangle / 2, nextDouble / 4, triangle / 2), speed 0.15. */
    private static void particles(Block block, ItemStack item, int count) {
        if (StackCodec.isEmpty(item)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().spawnParticle(Particle.ITEM, block.getX() + 0.5, block.getY() + 0.875, block.getZ() + 0.5, count,
            triangle(r) / 2.0, r.nextDouble() / 4.0, triangle(r) / 2.0, 0.15, item.clone());
    }

    /** TFC {@code Helpers.triangle(random)}. */
    static float triangle(ThreadLocalRandom r) {
        return r.nextFloat() - r.nextFloat() * 0.5f;
    }

    @Override
    public ItemStack damage(ItemStack handstone) {
        return damageOnce(handstone, ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
    }

    /**
     * Vanilla {@code hurtAndBreak(1, level, null, ...)}: Unbreaking level n skips the damage with chance n / (n + 1)
     * ({@code roll % (n + 1) != 0}); a stack whose damage reaches its max damage breaks. Undamageable stacks are unchanged.
     */
    static ItemStack damageOnce(ItemStack stack, int roll) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable dmg) || !dmg.hasMaxDamage() && stack.getType().getMaxDurability() <= 0) {
            return stack;
        }
        int unbreaking = stack.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && roll % (unbreaking + 1) != 0) {
            return stack;
        }
        int max = dmg.hasMaxDamage() ? dmg.getMaxDamage() : stack.getType().getMaxDurability();
        int next = dmg.getDamage() + 1;
        if (next >= max) {
            return ItemStack.empty();
        }
        dmg.setDamage(next);
        ItemStack out = stack.clone();
        out.setItemMeta(dmg);
        return out;
    }

    @Override
    public void spawn(Block block, ItemStack stack) {
        // TFC Helpers.spawnItem: an item entity at the block centre, vanilla's random motion, pickup delay 0
        block.getWorld().dropItem(centre(block), stack, e -> e.setPickupDelay(0));
    }

    private static Location centre(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }
}
