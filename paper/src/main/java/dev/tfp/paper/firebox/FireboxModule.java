/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start lights an unlit
 *       firebox), src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (HEAT_INSULATION, HEAT_PASSABLE),
 *       src/main/java/net/dries007/tfc/common/blockentities/FireboxBlockEntity.java @ c3b4d9536 (isValidInterior,
 *       isValidExterior: canOcclude, isFaceSturdy, DoorBlock)
 */
package dev.tfp.paper.firebox;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.firebox.FireboxKiln;
import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.calendar.CalendarService;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipStyles;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-12: the firebox. Provides {@link HeatConsumers} (TFC's {@code IHeatConsumer} for the blocks above heat
 * sources), registers the {@link FireboxDevice} kind {@code tfp:firebox}, its screen ({@link FireboxScreens}), its
 * {@code FireLightable} (a strong start lights an unlit firebox) and a chunk-load callback for lit fireboxes (the
 * burn-out shows on time). Needs the tags, device, GUI and fire modules before it in the service file.
 */
public final class FireboxModule implements FeatureModule {
    public static final String ID = "firebox";
    public static final String HEAT_INSULATION = "tfc:heat_insulation";
    public static final String HEAT_PASSABLE = "tfc:heat_passable";
    public static final String SHOVELS = "minecraft:shovels";

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
        CustomBlocks blocks = context.integrations().customBlocks();
        Scheduler scheduler = context.scheduler();
        HeatConsumers consumers = new HeatConsumers();
        context.services().provide(HeatConsumers.class, consumers);
        Supplier<FirepitFuels> fuels = fuels(() -> context.registryService().registries());

        FireboxScreens screens = gui.map(g -> new FireboxScreens(g, scheduler, context.messages(),
            TooltipStyles.TFC_DEFAULTS.heat(), () -> daysInMonth(context),
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)).isPresent())).orElse(null);
        AtomicReference<FireboxDevice> deviceRef = new AtomicReference<>();
        PaperFireboxEffects effects = new PaperFireboxEffects(scheduler, blocks,
            () -> context.integrations().blockVisuals(), devices::neighbourChanged);
        FireboxDevice device = new FireboxDevice(new FireboxDevice.Services(
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> douse(stack, tags),
            block -> kiln(block, tags, scheduler),
            consumers,
            block -> blocks.stateAt(block).map(BlockStateRef::id),
            effects,
            (player, block) -> openLater(screens, devices, deviceRef.get(), scheduler, player, block)), devices.stacks());
        deviceRef.set(device);
        devices.register(device.kind());
        if (screens != null) {
            context.listen(screens);
            hook = devices.hook(FireboxDevice.ID, new ScreenSync(screens, scheduler));
        }
        context.services().get(FireStarting.class).ifPresent(fire -> fire.register(ctx -> ctx.strong()
            && isUnlitFirebox(blocks, ctx.block()) && device.lightAt(devices, ctx.block())));
        Optional<CatchUpService> catchUp = context.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devices, scheduler)));
    }

    @Override
    public void disable() {
        AutoCloseable h = hook;
        hook = null;
        if (h != null) {
            try {
                h.close();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger("tfp").log(Level.FINE, "firebox: close failed", e);
            }
        }
    }

    /** TFC {@code openMenu}: next tick on the player's thread, if still online. */
    static void openLater(FireboxScreens screens, DeviceService devices, FireboxDevice device, Scheduler scheduler,
            Player player, Block block) {
        if (screens == null || device == null) {
            return;
        }
        scheduler.entityDelayed(player, 1, () -> {
            if (player.isOnline()) {
                screens.open(player, new FireboxLink(devices, device, scheduler, block));
            }
        }, () -> { });
    }

    static boolean isUnlitFirebox(CustomBlocks blocks, Block block) {
        return blocks.stateAt(block)
            .map(s -> s.id().equals(FireboxDevice.ID) && !"true".equals(s.properties().get(FireboxDevice.LIT)))
            .orElse(false);
    }

    /** TFC {@code SHOVEL_DOUSE} (vanilla and TFC shovels) and our water bucket, as for the firepit. */
    static FireboxEffects.Douse douse(ItemStack stack, Tags tags) {
        if (stack.getType() == Material.WATER_BUCKET) {
            return FireboxEffects.Douse.WATER;
        }
        return tags.is(stack, SHOVELS) ? FireboxEffects.Douse.SHOVEL : FireboxEffects.Douse.NONE;
    }

    /**
     * TFC's kiln view of the world: {@code isValidExterior} (insulation sturdy toward the inside, or a door) and
     * {@code isValidInterior} (not occluding, or heat passable). A block in an unloaded chunk or another region counts as
     * neither, so the fill gives up there (TFC would load the chunk).
     */
    static FireboxKiln.World kiln(Block firebox, Tags tags, Scheduler scheduler) {
        World w = firebox.getWorld();
        return new FireboxKiln.World() {
            private Block at(FireboxKiln.Pos p) {
                int cx = p.x() >> 4;
                int cz = p.z() >> 4;
                if (p.y() < w.getMinHeight() || p.y() >= w.getMaxHeight() || !w.isChunkLoaded(cx, cz)
                        || !scheduler.isOwner(w, cx, cz)) {
                    return null;
                }
                return w.getBlockAt(p.x(), p.y(), p.z());
            }

            @Override
            public boolean exterior(FireboxKiln.Pos pos, FireboxKiln.Dir moved) {
                Block b = at(pos);
                if (b == null || !tags.is(b, HEAT_INSULATION)) {
                    return false;
                }
                return b.getBlockData().isFaceSturdy(face(moved.opposite()), BlockSupport.FULL)
                    || b.getBlockData() instanceof Door;
            }

            @Override
            public boolean interior(FireboxKiln.Pos pos) {
                Block b = at(pos);
                return b != null && (!b.getType().isOccluding() || tags.is(b, HEAT_PASSABLE));
            }
        };
    }

    static BlockFace face(FireboxKiln.Dir d) {
        return switch (d) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    static int daysInMonth(ModuleContext context) {
        return context.services().get(CalendarService.class).map(c -> c.state().daysInMonth())
            .orElse(ClockSnapshot.DEFAULT_DAYS_IN_MONTH);
    }

    /** Lit fireboxes of a loaded chunk get a callback: one whose fuel ran out meanwhile goes out, the rest re-queue. */
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
            boolean lit = blocks.stateAt(block)
                .map(s -> s.id().equals(FireboxDevice.ID) && "true".equals(s.properties().get(FireboxDevice.LIT)))
                .orElse(false);
            if (lit) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
    }

    /** The fuel index, rebuilt when the registries change version. Thread-safe. */
    static Supplier<FirepitFuels> fuels(Supplier<Registries> registries) {
        record Entry(long version, FirepitFuels fuels) {
        }
        AtomicReference<Entry> cache = new AtomicReference<>(new Entry(0, FirepitFuels.EMPTY));
        return () -> {
            Registries r = registries.get();
            if (r == null) {
                return FirepitFuels.EMPTY;
            }
            long version = r.version();
            Entry e = cache.get();
            if (e.version() == version) {
                return e.fuels();
            }
            FirepitFuels f = version == 0 ? FirepitFuels.EMPTY : FirepitFuels.fromRegistry(r.get());
            cache.set(new Entry(version, f));
            return f;
        };
    }

    /** Keeps an open screen in step with the other writers of the firebox (fuel clicks, removal, callbacks). */
    record ScreenSync(FireboxScreens screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, false, screens::edit);
        }

        @Override
        public void after(Block block) {
            withViewer(block, true, screens::reload);
        }

        private void withViewer(Block block, boolean queue, java.util.function.Consumer<Object> action) {
            FireboxLink.Key key = FireboxLink.Key.of(block);
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
