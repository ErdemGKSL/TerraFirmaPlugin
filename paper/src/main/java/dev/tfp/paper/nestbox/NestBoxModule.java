/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java @ c3b4d9536 (useItemOn: openMenu),
 *       src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (serverTick),
 *       src/main/java/net/dries007/tfc/common/entities/ai/livestock/LayEggBehavior.java @ c3b4d9536
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.livestock.products.NestRules;
import dev.tfp.core.nestbox.NestBoxState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-FAUNA-07: the nest box. Registers the {@link NestBoxDevice} kind {@code tfp:nest_box} with the device framework,
 * its screen ({@link NestBoxScreens}), a {@link NestSeeker} for every egg-laying species (a ready hen looks for a nest
 * with a free slot) and a chunk-load callback that brings a loaded nest up to date (a hatch that came due, a stale
 * sitter). The CraftEngine block comes with PAR-FAUNA-26; until it exists the kind is registered and idle.
 */
public final class NestBoxModule implements FeatureModule {
    public static final String ID = "nestbox";

    private volatile AutoCloseable hook;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        ItemFoodHooks food = context.services().require(ItemFoodHooks.class);
        Optional<GuiService> gui = context.services().get(GuiService.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Scheduler scheduler = context.scheduler();
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();

        NestFinder finder = new NestFinder();
        PaperNestHens hens = new PaperNestHens(animals, scheduler);
        PaperNestEffects effects = new PaperNestEffects(scheduler, () -> devices, animals, finder, context.logger());
        AtomicReference<DeviceKind<NestBoxState>> kindHolder = new AtomicReference<>();
        AtomicReference<NestBoxDevice.Services> servicesHolder = new AtomicReference<>();
        NestBoxScreens screens = gui.map(g -> new NestBoxScreens(g, scheduler, context.messages(),
            stack -> NestBoxDevice.accepts(stack, servicesHolder.get()))).orElse(null);
        BiConsumer<Player, Block> open = (player, block) -> {
            if (screens == null) {
                return;
            }
            scheduler.entityDelayed(player, 1, () -> {
                if (player.isOnline()) {
                    screens.open(player, new NestBoxLink(devices, kindHolder.get(), servicesHolder.get(), scheduler, block));
                }
            }, () -> { });
        };
        NestBoxDevice.Services services = new NestBoxDevice.Services(food::isRotten, context.clock()::get,
            () -> new ThreadLocalNestRandom(), hens, effects, open);
        servicesHolder.set(services);
        kindHolder.set(devices.register(NestBoxDevice.kind(services)));

        animals.behaviours().register(new NestSeeker(animals, scheduler, finder, hens, () -> devices, kindHolder::get, services));
        if (screens != null) {
            context.listen(screens);
            hook = devices.hook(NestBoxDevice.ID, new ScreenSync(screens, scheduler));
        }
        Optional<CatchUpService> catchUp = context.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devices, scheduler, finder)));
    }

    @Override
    public void disable() {
        AutoCloseable h = hook;
        hook = null;
        if (h != null) {
            try {
                h.close();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger("tfp").log(Level.FINE, "nestbox: close failed", e);
            }
        }
    }

    /** The draws of TFC's {@code bird.getRandom()}: {@code nextInt(bound)} and {@code nextBoolean()}. */
    static final class ThreadLocalNestRandom implements NestRules.NestRandom {
        @Override
        public int nextInt(int bound) {
            return ThreadLocalRandom.current().nextInt(bound);
        }

        @Override
        public boolean nextBoolean() {
            return ThreadLocalRandom.current().nextBoolean();
        }
    }

    static boolean isNestBox(CustomBlocks blocks, Block block) {
        return blocks.stateAt(block).map(s -> s.id().equals(NestBoxDevice.ID)).orElse(false);
    }

    /** Nest boxes of a loaded chunk are registered for hens and called once: a hatch that came due meanwhile happens. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, CustomBlocks blocks, DeviceService devices,
            Scheduler scheduler, NestFinder finder) {
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
            if (isNestBox(blocks, block)) {
                finder.add(world.getUID(), p);
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
    }

    /** Keeps an open screen in step with the other writers of the box (a hatch, a hen's egg, removal). */
    record ScreenSync(NestBoxScreens screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, false, screens::edit);
        }

        @Override
        public void after(Block block) {
            withViewer(block, true, screens::reload);
        }

        private void withViewer(Block block, boolean queue, java.util.function.Consumer<Object> action) {
            NestBoxLink.Key key = NestBoxLink.Key.of(block);
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
