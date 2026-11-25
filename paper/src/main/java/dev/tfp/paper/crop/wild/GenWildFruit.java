/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/SpreadingCropFeature.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/crop/WildSpreadingCropBlock.java @ c3b4d9536 (the fruit it places)
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * PAR-FARM-27: turns the vanilla placeholder the terrain writes for a wild spreading crop's fruit ({@code Material.PUMPKIN} and
 * {@code Material.MELON}, which a TFC world otherwise never holds) into the CraftEngine fruit block {@code tfp:fruit/pumpkin} or
 * {@code tfp:fruit/melon} with a fresh, wild {@link FruitDevice.FruitState} (creation date now; a wild fruit rots later, see
 * {@code WildRules.rotten}). Region thread of the block.
 */
public final class GenWildFruit {
    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final Supplier<Optional<CatchUpService>> catchUp;

    public GenWildFruit(DeviceService devices, CustomBlocks blocks, Supplier<Optional<CatchUpService>> catchUp) {
        this.devices = devices;
        this.blocks = blocks;
        this.catchUp = catchUp;
    }

    /** The placeholder materials of the fruit blocks. */
    public static boolean placeholder(Material material) {
        return material == Material.PUMPKIN || material == Material.MELON;
    }

    /** The fruit block of a placeholder. */
    public static String blockId(Material material) {
        return FruitDevice.ID + "/" + (material == Material.MELON ? "melon" : "pumpkin");
    }

    /** @return whether the block became a fruit block (false: it is no longer the placeholder, or CraftEngine lacks the block) */
    public boolean commit(Block block) {
        Material was = block.getType();
        if (!placeholder(was) || blocks.stateAt(block).isPresent()) {
            return false;
        }
        BlockStateRef ref = BlockStateRef.of(blockId(was));
        if (!blocks.exists(ref.id()) || !blocks.place(block, ref, false)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        DeviceKind<FruitDevice.FruitState> kind = devices.kinds().find(ref.id()).map(k -> (DeviceKind<FruitDevice.FruitState>) k).orElse(null);
        if (kind != null) {
            devices.edit(block, kind, (record, now) -> record.withState(new FruitDevice.FruitState(now, true)));
        }
        catchUp.get().ifPresent(c -> c.track(block, FruitDevice.CATCH_UP_KIND));
        return true;
    }
}
