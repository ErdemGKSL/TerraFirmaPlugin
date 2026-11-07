/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/ContainedFluidModel.java @ c3b4d9536 (the fluid layer of a container
 *       item, tinted with the contained fluid)
 */
package dev.tfp.paper.bucket;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.itemdata.ItemFluids;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/**
 * The look of a tank bucket: datagen's item model shows the fluid layer when {@code custom_model_data} flag 0 is true and
 * tints it with colour 0. This writes both from the bucket's fluid ({@code FluidPdc}), or removes them when it is empty, so
 * an emptied bucket stacks with a new one again. Any code that fills or drains a bucket calls {@link #sync} afterwards.
 *
 * <p>Threads: the thread that owns the stack.
 */
public final class BucketVisuals {

    private BucketVisuals() {
    }

    /**
     * Brings the stack's model data in line with its fluid.
     *
     * @return whether the meta changed
     */
    public static boolean sync(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        FluidAmount content = ItemFluids.content(stack);
        ItemMeta meta = stack.getItemMeta();
        if (content.isEmpty()) {
            if (!meta.hasCustomModelDataComponent()) {
                return false;
            }
            meta.setCustomModelDataComponent(null);
            stack.setItemMeta(meta);
            return true;
        }
        int rgb = tint(content.fluid());
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        List<Boolean> flags = List.of(true);
        List<Color> colors = List.of(Color.fromRGB(rgb));
        if (meta.hasCustomModelDataComponent() && flags.equals(cmd.getFlags()) && colors.equals(cmd.getColors())) {
            return false;
        }
        cmd.setFlags(flags);
        cmd.setColors(colors);
        meta.setCustomModelDataComponent(cmd);
        stack.setItemMeta(meta);
        return true;
    }

    /**
     * The look of vanilla lava in the mask: TFC tints lava white over the orange lava texture, which a flat-tinted mask
     * does not have, so the mask takes the average colour of the vanilla lava texture instead.
     */
    static final int LAVA_LOOK = 0xCF5C14;

    /** The RGB tint of a fluid's layer, white for an unknown one. */
    static int tint(String fluid) {
        if (TfcFluids.LAVA.equals(fluid)) {
            return LAVA_LOOK;
        }
        return TfcFluids.get(fluid).map(f -> f.rgb()).orElse(0xFFFFFF);
    }
}
