/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (getSize HUGE, getWeight
 *       HEAVY, VERY_HEAVY when sealed, isStackSealed),
 *       src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java @ c3b4d9536 (constructor:
 *       sidedInventory, largeVesselEnableAutomation; VesselInventory: canModify),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (largeVesselEnableAutomation,
 *       largeVesselEnableRedstoneSeal, both default true)
 */
package dev.tfp.paper.largevessel;

import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.largevessel.LargeVesselSides;
import dev.tfp.core.largevessel.LargeVesselState;
import dev.tfp.core.persist.PersistException;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.automation.DeviceAutomation;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-06: the large vessel. Registers the {@link LargeVesselDevice} kind {@code tfp:ceramic/large_vessel} with the
 * device framework, its screen ({@link LargeVesselScreens}), the placement facing listener, the hopper sides (TFC
 * {@code largeVesselEnableAutomation}) and the item size (huge; heavy, very heavy when the saved record is sealed). TFC's
 * two server options are the config switches {@code automation.large-vessel} and {@code automation.large-vessel-redstone-seal}
 * (PAR-POT-16, both read at every use, so {@code /tfp reload config} applies them). A dropped vessel gets TFC's contents
 * tooltip summary ({@link LargeVesselDrops}). The CraftEngine blocks come from datagen
 * ({@code configuration/largevessel/large_vessel.yml}).
 */
public final class LargeVesselModule implements FeatureModule {
    public static final String ID = "largevessel";

    private final List<AutoCloseable> registrations = new ArrayList<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        ItemSizes sizes = context.services().require(ItemSizes.class);
        Recipes recipes = context.services().require(Recipes.class);
        var blocks = context.integrations().customBlocks();
        var scheduler = context.scheduler();
        PaperLargeVesselEffects effects = new PaperLargeVesselEffects(scheduler, blocks,
            () -> context.integrations().blockVisuals());
        AtomicReference<DeviceKind<LargeVesselState>> kindHolder = new AtomicReference<>();
        AtomicReference<LargeVesselDevice.Services> servicesHolder = new AtomicReference<>();
        AtomicReference<LargeVesselScreens> screensHolder = new AtomicReference<>();
        BiConsumer<Player, Block> open = (player, block) -> scheduler.entityDelayed(player, 1, () -> {
            LargeVesselScreens screens = screensHolder.get();
            if (screens != null && player.isOnline()) {
                screens.open(player, new LargeVesselLink(devices, kindHolder.get(), servicesHolder.get(), scheduler, block));
            }
        }, () -> { });
        LargeVesselDevice.Services services = new LargeVesselDevice.Services(() -> context.config().current().automation().largeVesselRedstoneSeal(),
            sizes::fitsLargeVessel,
            Block::isBlockIndirectlyPowered,
            (stack, sealing) -> sealing
                ? recipes.modifiers().addTrait(stack, PRESERVED)
                : recipes.modifiers().removeTrait(stack, PRESERVED),
            effects, open);
        servicesHolder.set(services);
        DeviceKind<LargeVesselState> kind = devices.register(LargeVesselDevice.kind(services));
        kindHolder.set(kind);

        Optional<GuiService> gui = context.services().get(GuiService.class);
        gui.ifPresent(g -> {
            LargeVesselScreens screens = new LargeVesselScreens(g, scheduler, context.messages(), sizes::fitsLargeVessel);
            screensHolder.set(screens);
            context.listen(screens);
            registrations.add(devices.hook(LargeVesselDevice.ID, new ScreenSync(screens, scheduler)));
        });
        context.listen(new LargeVesselListener(blocks, effects, scheduler));
        context.listen(new LargeVesselDrops(context.integrations().customItems(), kind, devices.stacks()));
        context.services().get(DeviceAutomation.class).ifPresent(a -> registrations.add(a.register(
            automation(() -> context.config().current().automation().largeVessel()))));
        registrations.add(sizes.register(id -> LargeVesselDevice.covers(id), new ItemSizes.StackSizer() {
            @Override
            public ItemSizing sizing(ItemStack stack) {
                return new ItemSizing(ItemSize.HUGE, sealed(kind, stack) ? ItemWeight.VERY_HEAVY : ItemWeight.HEAVY);
            }
        }));
    }

    static final String PRESERVED = "tfp:preserved";

    /** The hopper sides, locked while sealed, behind TFC's {@code largeVesselEnableAutomation} ({@code enabled}). */
    static DeviceAutomation.Spec automation(java.util.function.BooleanSupplier enabled) {
        return DeviceAutomation.Spec.of(LargeVesselDevice.ID, state -> LargeVesselSides.table())
            .withEnabled(enabled)
            .withLocked(state -> state instanceof LargeVesselState s && s.sealed());
    }

    /** TFC {@code isStackSealed}: the item carries contents, here the saved record of a sealed vessel. */
    static boolean sealed(DeviceKind<LargeVesselState> kind, ItemStack stack) {
        Optional<byte[]> saved = DeviceService.savedRecord(stack);
        if (saved.isEmpty()) {
            return false;
        }
        try {
            return kind.codec().fromBytes(saved.get()).state().sealed();
        } catch (PersistException e) {
            return false;
        }
    }

    @Override
    public void disable() {
        for (AutoCloseable c : registrations) {
            try {
                c.close();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger("tfp").log(Level.FINE, "largevessel: close failed", e);
            }
        }
        registrations.clear();
    }

    /** Keeps an open screen in step with the other writers of the vessel (sneak seal, redstone, removal). */
    record ScreenSync(LargeVesselScreens screens, dev.tfp.paper.scheduler.Scheduler scheduler)
            implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, false, screens::edit);
        }

        @Override
        public void after(Block block) {
            withViewer(block, true, screens::reload);
        }

        private void withViewer(Block block, boolean queue, java.util.function.Consumer<Object> action) {
            LargeVesselLink.Key key = LargeVesselLink.Key.of(block);
            var session = screens.viewer(key);
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
