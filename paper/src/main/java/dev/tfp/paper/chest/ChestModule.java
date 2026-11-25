/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/wood/TFCTrappedChestBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/container/RestrictedChestContainer.java @ c3b4d9536
 */
package dev.tfp.paper.chest;

import dev.tfp.core.chest.ChestState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-DECO-04: the chests and trapped chests of the 20 woods (blocks written by datagen {@code chest/ChestBlocks}): the
 * two device kinds, the screen of a chest or a double chest ({@link ChestScreens}, items above TFC's chest size refused
 * through {@link ItemSizes#fitsChest}) and the placement and pairing ({@link ChestPlacement}). Needs the device, GUI and
 * item size modules before it in the service file.
 */
public final class ChestModule implements FeatureModule {
    public static final String ID = "chest";

    private volatile List<AutoCloseable> hooks = List.of();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        ItemSizes sizes = context.services().require(ItemSizes.class);
        Optional<GuiService> gui = context.services().get(GuiService.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Scheduler scheduler = context.scheduler();
        Predicate<ItemStack> fits = sizes::fitsChest;

        BiConsumer<Block, dev.tfp.paper.integration.BlockStateRef> writer = (block, next) -> {
            BlockVisuals visuals = context.integrations().blockVisuals();
            visuals.blockData(next).ifPresent(d -> block.setBlockData(d, false));
        };
        ChestPlacement placement = new ChestPlacement(blocks, scheduler, writer);
        ChestScreens screens = gui.map(g -> new ChestScreens(g, scheduler, context.messages(), fits)).orElse(null);
        BiConsumer<Player, Block> open = (player, block) -> {
            if (screens == null) {
                return;
            }
            scheduler.entityDelayed(player, 1, () -> {
                if (!player.isOnline()) {
                    return;
                }
                ChestLink.resolve(devices, blocks, scheduler, block, fits).ifPresent(link -> screens.open(player, link));
            }, () -> { });
        };
        List<DeviceKind<ChestState>> kinds = List.of(
            devices.register(ChestDevice.kind(ChestBlocks.CHEST, fits, open, placement)),
            devices.register(ChestDevice.kind(ChestBlocks.TRAPPED_CHEST, fits, open, placement)));
        context.listen(placement);
        if (screens != null) {
            context.listen(screens);
            hooks = kinds.stream().map(k -> devices.hook(k.id(), new ScreenSync(screens, scheduler, blocks))).toList();
        }
    }

    @Override
    public void disable() {
        List<AutoCloseable> h = hooks;
        hooks = List.of();
        for (AutoCloseable c : h) {
            try {
                c.close();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger("tfp").log(Level.FINE, "chest: close failed", e);
            }
        }
    }

    /** Keeps an open screen in step with the other writers of a chest (a neighbour update, a removal). */
    record ScreenSync(ChestScreens screens, Scheduler scheduler, CustomBlocks blocks) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, false, screens::edit);
        }

        @Override
        public void after(Block block) {
            withViewer(block, true, screens::reload);
        }

        private void withViewer(Block block, boolean queue, java.util.function.Consumer<ChestLink.Key> action) {
            ChestLink.Key key = ChestLink.Key.of(block);
            Optional<dev.tfp.paper.gui.GuiSession> session = screens.viewer(key);
            if (session.isEmpty()) {
                return;
            }
            Player viewer = session.get().viewer();
            org.bukkit.Location at = viewer.getLocation();
            if (scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                action.accept(key);
            } else if (queue) {
                scheduler.entity(viewer, () -> action.accept(key), () -> { });
            }
        }
    }
}
