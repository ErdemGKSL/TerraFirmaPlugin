/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java @ c3b4d9536 (doParticles),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (spawnItem, triangle, damageItem)
 */
package dev.tfp.paper.scraping;

import dev.tfp.core.scraping.ScrapingState;
import dev.tfp.core.scraping.ScrapingVisuals;
import dev.tfp.paper.device.StackCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * The server side of {@link ScrapingEffects}. The surface is drawn by one item display entity per scraping block, centred in
 * the block, showing a plain item whose {@code item_model} is the recipe's look ({@link ScrapingVisuals#itemModel}) and whose
 * {@code custom_model_data} carries the 16 scraped flags and the two dye colours. TFC draws the same tiles with a dynamic
 * block model; a CraftEngine {@code entity_renderer} only follows block states, which cannot hold the 2^16 masks, so the
 * display is a real entity, marked with {@link #MARKER} (the block position) so that it is found again after a restart and
 * removed with the block. Region thread of the block only.
 */
public final class PaperScrapingEffects implements ScrapingEffects {
    /** PDC key on the display entity: {@code "x,y,z"} of the scraping block it draws. */
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "scraping_surface");
    /**
     * Yaw of the display. The vanilla item display renderer turns the item model by pi around Y before drawing it
     * ({@code ItemDisplayRenderer}); the FIXED billboard turns it by {@code -yaw}, so a yaw of 180 cancels that turn and the
     * model's x/z (ScrapingArt: cell i at x = column * 4, z = row * 4) lands on world x/z, as TFC's ScrapingBlockModel draws it.
     * Written as -180, the value an entity's yaw of 180 is wrapped to.
     */
    public static final float SURFACE_YAW = -180f;

    @Override
    public void damageTool(Player player, EquipmentSlot hand) {
        if (!StackCodec.isEmpty(player.getInventory().getItem(hand))) {
            player.damageItemStack(hand, 1);
        }
    }

    @Override
    public void particles(Block block, double x, double z, ItemStack item) {
        if (StackCodec.isEmpty(item)) {
            return;
        }
        // TFC: sendParticles(ITEM, pos + point, y + 0.0625, count 2, spread (triangle/2, nextDouble/4, triangle/2), speed 0.15)
        ThreadLocalRandom r = ThreadLocalRandom.current();
        block.getWorld().spawnParticle(Particle.ITEM, block.getX() + x, block.getY() + 0.0625, block.getZ() + z, 2,
            triangle(r) / 2.0, r.nextDouble() / 4.0, triangle(r) / 2.0, 0.15, item.clone());
    }

    /** TFC {@code Helpers.triangle(random)}. */
    static float triangle(ThreadLocalRandom r) {
        return r.nextFloat() - r.nextFloat() * 0.5f;
    }

    @Override
    public void spawn(Block block, ItemStack item) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        // TFC new ItemEntity: vanilla's random motion, pickup delay 0 (CraftWorld.dropItem sets 10 before the consumer).
        block.getWorld().dropItem(at, item, e -> e.setPickupDelay(0));
    }

    @Override
    public void show(Block block, ScrapingState state) {
        if (state.look().isEmpty()) {
            hide(block);
            return;
        }
        ItemStack shown = surfaceItem(state);
        List<ItemDisplay> found = displays(block);
        if (found.isEmpty()) {
            World world = block.getWorld();
            Location at = new Location(world, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, SURFACE_YAW, 0f);
            world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setRotation(SURFACE_YAW, 0f);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker(block));
            });
            return;
        }
        ItemDisplay kept = found.getFirst();
        kept.setItemStack(shown);
        if (kept.getYaw() != SURFACE_YAW || kept.getPitch() != 0f) {
            kept.setRotation(SURFACE_YAW, 0f);
        }
        for (int i = 1; i < found.size(); i++) {
            found.get(i).remove();
        }
    }

    @Override
    public void hide(Block block) {
        displays(block).forEach(Entity::remove);
    }

    /** The plain item the display shows for {@code state}. */
    static ItemStack surfaceItem(ScrapingState state) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(state.look()));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFlags(ScrapingVisuals.flags(state.mask()));
        cmd.setColors(colors(state));
        meta.setCustomModelDataComponent(cmd);
        item.setItemMeta(meta);
        return item;
    }

    /** TFC {@code getColor1} / {@code getColor2}: the dye's texture colour, white ({@code -1}) without a dye. */
    static List<Color> colors(ScrapingState state) {
        List<Color> out = new ArrayList<>(2);
        out.add(color(state.color1()));
        out.add(color(state.color2()));
        return out;
    }

    static Color color(int dye) {
        return dye == ScrapingState.NO_COLOR ? Color.WHITE : DyeColor.values()[dye].getColor();
    }

    private static List<ItemDisplay> displays(Block block) {
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
