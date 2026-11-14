/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start on a sealed keg
 *       lights it), src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (getSize
 *       HUGE, getWeight HEAVY), src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java @ c3b4d9536
 *       (useItemOn: openMenu)
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.powderkeg.PowderkegConfig;
import dev.tfp.core.powderkeg.PowderkegState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-PROC-18: the powderkeg. Registers the {@link PowderkegDevice} kind {@code tfp:powderkeg} with the device framework,
 * its screen ({@link PowderkegScreens}), its {@code FireLightable} (a strong start on a sealed keg lights it), the blast
 * listener ({@link PowderkegBlasts}), a chunk-load callback for lit kegs (the lazy fuse) and the item size (TFC
 * {@code SealableDeviceBlock}: huge and heavy). TFC's server options are fixed at their defaults
 * ({@link PowderkegConfig#DEFAULTS}). The CraftEngine block ({@code sealed}, {@code lit}) comes with PAR-PROC-39; until
 * it exists the kind is registered and idle.
 */
public final class PowderkegModule implements FeatureModule {
    public static final String ID = "powderkeg";

    private volatile AutoCloseable sizer;
    private volatile AutoCloseable hook;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Optional<GuiService> gui = context.services().get(GuiService.class);
        Optional<ItemSizes> sizes = context.services().get(ItemSizes.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Scheduler scheduler = context.scheduler();
        PowderkegConfig config = PowderkegConfig.DEFAULTS;

        PowderkegScreens screens = gui.map(g -> new PowderkegScreens(g, scheduler, context.messages(),
            stack -> tags.is(stack, dev.tfp.core.powderkeg.PowderkegRules.FUEL_TAG))).orElse(null);
        AtomicReference<PowderkegBlasts> blastHolder = new AtomicReference<>();
        AtomicReference<DeviceKind<PowderkegState>> kindHolder = new AtomicReference<>();
        AtomicReference<PowderkegDevice.Services> servicesHolder = new AtomicReference<>();
        PaperPowderkegEffects effects = new PaperPowderkegEffects(scheduler, blocks,
            () -> context.integrations().blockVisuals(), devices::neighbourChanged, blastHolder::get,
            context.messages());
        BiConsumer<Player, Block> open = (player, block) -> {
            if (screens == null) {
                return;
            }
            scheduler.entityDelayed(player, 1, () -> {
                if (player.isOnline()) {
                    screens.open(player, new PowderkegLink(devices, kindHolder.get(), servicesHolder.get(), scheduler, block));
                }
            }, () -> { });
        };
        PowderkegDevice.Services services = new PowderkegDevice.Services(config,
            stack -> tags.is(stack, dev.tfp.core.powderkeg.PowderkegRules.FUEL_TAG),
            Block::isBlockIndirectlyPowered, effects, open);
        servicesHolder.set(services);
        DeviceKind<PowderkegState> kind = devices.register(PowderkegDevice.kind(services));
        kindHolder.set(kind);

        PowderkegBlasts blasts = new PowderkegBlasts(new PowderkegBlasts.Env(
            block -> isKeg(blocks, block),
            tags::is,
            config.onlyBreaksNaturalBlocks(),
            block -> scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4,
                () -> PowderkegDevice.lightAt(devices, kind, block, null, PowderkegState::lightsFromExplosion, services)),
            block -> {
                block.setType(Material.AIR, true); // TFC setBlock(AIR, 11): the keg's onRemove runs, no keg drop
                return true;
            },
            (at, power) -> at.getWorld().createExplosion(at, power, false, true)));
        blastHolder.set(blasts);
        context.listen(blasts);
        if (screens != null) {
            context.listen(screens);
            hook = devices.hook(PowderkegDevice.ID, new ScreenSync(screens, scheduler));
        }
        context.services().get(FireStarting.class).ifPresent(fire -> fire.register(ctx -> isKeg(blocks, ctx.block())
            && PowderkegDevice.lightAt(devices, kind, ctx.block(), ctx.player(), s -> s.lightsFromFire(ctx.strong()), services)));
        Optional<CatchUpService> catchUp = context.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devices, scheduler)));
        sizes.ifPresent(s -> sizer = s.register(id -> id.equals(PowderkegDevice.ID),
            stack -> new ItemSizing(ItemSize.HUGE, ItemWeight.HEAVY)));
    }

    @Override
    public void disable() {
        for (AutoCloseable c : new AutoCloseable[] {sizer, hook}) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger("tfp").log(Level.FINE, "powderkeg: close failed", e);
                }
            }
        }
        sizer = null;
        hook = null;
    }

    static boolean isKeg(CustomBlocks blocks, Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(PowderkegDevice.ID)).orElse(false);
    }

    /** Lit kegs of a loaded chunk get a callback: one whose fuse ran out meanwhile goes off, the rest re-queue it. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, CustomBlocks blocks, DeviceService devices,
            Scheduler scheduler) {
        Optional<LazyBlockIndex> index = catchUp.index(chunk);
        if (index.isEmpty()) {
            return;
        }
        World world = chunk.getWorld();
        for (Map.Entry<BlockPos, String> e : index.get().entries().entrySet()) {
            if (!CatchUpModule.DEVICE_KIND.equals(e.getValue())) {
                continue;
            }
            BlockPos p = e.getKey();
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            boolean litKeg = blocks.stateAt(block)
                .map(s -> s.id().equals(PowderkegDevice.ID) && "true".equals(s.properties().get(PowderkegDevice.LIT)))
                .orElse(false);
            if (litKeg) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
    }

    /** Keeps an open screen in step with the other writers of the keg (sneak seal, fire, removal). */
    record ScreenSync(PowderkegScreens screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, false, screens::edit);
        }

        @Override
        public void after(Block block) {
            withViewer(block, true, screens::reload);
        }

        private void withViewer(Block block, boolean queue, java.util.function.Consumer<Object> action) {
            PowderkegLink.Key key = PowderkegLink.Key.of(block);
            Optional<GuiSession> session = screens.viewer(key);
            if (session.isEmpty()) {
                return;
            }
            Player viewer = session.get().viewer();
            Location at = viewer.getLocation();
            if (scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                action.accept(key);
            } else if (queue) {
                scheduler.entity(viewer, () -> action.accept(key), () -> { });
            }
        }
    }
}
