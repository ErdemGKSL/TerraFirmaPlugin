/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (placing the firepit),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: strong starts light a firepit),
 *       src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (FIREPIT_FUEL, SOLID_TOP_FACE)
 */
package dev.tfp.paper.firepit;

import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.fire.FirepitFactory;
import dev.tfp.paper.firepit.gui.FirepitScreens;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.weather.PrecipitationService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-06: the firepit. Registers the {@link FirepitDevice} kind {@code tfp:firepit}, its {@code FireLightable}
 * (a strong start lights it) and the {@link FirepitFactory} that builds one from a stick and log pile. Needs the tags,
 * device, heating and fire modules before it in the service file; the rain comes from PAR-CLIM-02's
 * {@link PrecipitationService} when it runs (never raining without it). PAR-HEAT-20: the block's screen step opens the
 * firepit screen ({@link #openScreen}) and an access hook keeps an open screen in step with the device ({@link ScreenSync}).
 */
public final class FirepitModule implements FeatureModule {
    public static final String ID = "firepit";
    public static final String FIREPIT_FUEL = "tfc:firepit_fuel";
    public static final String SOLID_TOP_FACE = "tfc:solid_top_face";
    public static final String SHOVELS = "minecraft:shovels";

    private volatile AutoCloseable hook;
    /** PAR-HEAT-33: the calendar-jump sweep of the burn-out callbacks. */
    private volatile Scheduler.TaskHandle sweep;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle s = sweep;
        sweep = null;
        if (s != null) {
            s.cancel();
        }
        AutoCloseable h = hook;
        hook = null;
        if (h != null) {
            try {
                h.close();
            } catch (Exception ignored) {
                // a map removal
            }
        }
    }

    /**
     * TFC {@code serverPlayer.openMenu(firepit, pos)}: next tick on the player's thread, if the player is still online,
     * this thread owns the block and it is still a firepit, opens {@link FirepitScreens} (PAR-HEAT-07's screen service,
     * from {@code FirepitGuiModule}, enabled after this module) over a {@link FirepitScreenLink} keyed by the block.
     */
    static void openScreen(ModuleContext context, DeviceService devices, FirepitDevice device, Player player, Block block) {
        openLater(context.scheduler(), () -> context.services().get(FirepitScreens.class),
            context.integrations().customBlocks(), devices, device, p -> context.messages().get(p, "gui.firepit.title"),
            player, block);
    }

    /** {@link #openScreen} over its parts (tests). */
    static void openLater(Scheduler scheduler, Supplier<Optional<FirepitScreens>> screens, CustomBlocks blocks,
            DeviceService devices, FirepitDevice device, Function<Player, String> title, Player player, Block block) {
        scheduler.entityDelayed(player, 1, () -> {
            Optional<FirepitScreens> s = screens.get();
            if (s.isEmpty() || device == null || !player.isOnline() || !owned(scheduler, block)
                    || !blocks.stateAt(block).map(st -> st.id().equals(FirepitDevice.ID)).orElse(false)) {
                return;
            }
            FirepitScreenLink link = new FirepitScreenLink(devices, device, block, b -> owned(scheduler, b));
            s.get().open(player, link.key(), link, title);
        }, () -> { });
    }

    /** Folia {@code Bukkit.isOwnedByCurrentRegion(block)}; always true on Paper's main thread. */
    static boolean owned(Scheduler scheduler, Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * Keeps an open firepit screen in step with the other writers of the slots (a log click, the ash pickup, a douse, the
     * burn-out callback, removal): commits its copy before, reloads it after. On the viewer's thread: inline when this
     * thread owns the viewer, otherwise the reload is queued there.
     */
    record ScreenSync(Supplier<Optional<FirepitScreens>> screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, false, FirepitScreens::edit);
        }

        @Override
        public void after(Block block) {
            withViewer(block, true, FirepitScreens::reload);
        }

        private void withViewer(Block block, boolean queue, java.util.function.BiConsumer<FirepitScreens, Object> action) {
            Optional<FirepitScreens> s = screens.get();
            if (s.isEmpty()) {
                return;
            }
            FirepitScreenLink.Key key = FirepitScreenLink.Key.of(block);
            Optional<GuiSession> session = s.get().viewer(key);
            if (session.isEmpty()) {
                return;
            }
            Player viewer = session.get().viewer();
            Location at = viewer.getLocation();
            if (scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                action.accept(s.get(), key);
            } else if (queue) {
                scheduler.entity(viewer, () -> action.accept(s.get(), key), () -> { });
            }
        }
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        HeatingService heating = context.services().require(HeatingService.class);
        ItemHeatDefinitionsCache heatDefinitions = context.services().require(ItemHeatDefinitionsCache.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Supplier<FirepitFuels> fuels = fuels(() -> context.registryService().registries());
        ItemHeatHooks.Capacities capacities = ItemHeatHooks.Capacities.byId(tags::itemId, heatDefinitions);
        PaperFirepitEffects effects = new PaperFirepitEffects(context.scheduler(), blocks,
            () -> context.integrations().blockVisuals(), () -> context.clock().get().playerTicks(),
            devices::neighbourChanged);
        sweep = effects.start();
        AtomicReference<FirepitDevice> deviceRef = new AtomicReference<>();
        FirepitDevice device = new FirepitDevice(new FirepitDevice.Services(
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> tags.is(stack, FIREPIT_FUEL),
            capacities::of,
            cooker(heating),
            stack -> douse(stack, tags),
            block -> raining(context, block),
            block -> canSurvive(block, tags),
            n -> context.integrations().customItems().create(FirepitDevice.WOOD_ASH, n).orElse(ItemStack.empty()),
            ThreadLocalRandom::current, HeatConfig.DEFAULT, effects), devices.stacks(),
            (player, block) -> openScreen(context, devices, deviceRef.get(), player, block));
        deviceRef.set(device);
        devices.register(device.kind());
        hook = devices.hook(FirepitDevice.ID, new ScreenSync(() -> context.services().get(FirepitScreens.class),
            context.scheduler()));
        context.services().get(FireStarting.class).ifPresent(fire -> {
            fire.register(ctx -> ctx.strong() && blocks.stateAt(ctx.block()).map(s -> s.id().equals(FirepitDevice.ID)).orElse(false)
                && device.light(ctx.block(), devices::neighbourChanged));
            fire.firepitFactory(factory(device, blocks, tags, devices));
        });
    }

    /** TFC's {@code StartFireEvent} firepit: placed unlit on the axis, the logs put in, then lit. Region thread. */
    static FirepitFactory factory(FirepitDevice device, CustomBlocks blocks, Tags tags, DeviceService devices) {
        return new FirepitFactory() {
            @Override
            public boolean canSurvive(Block at) {
                // without the CraftEngine block (not loaded until PAR-HEAT-19) no firepit can stand: TFC's fire fallback
                return blocks.exists(FirepitDevice.ID) && FirepitModule.canSurvive(at, tags);
            }

            @Override
            public boolean build(Block at, Axis axis, List<ItemStack> logs, Player player) {
                BlockStateRef state = BlockStateRef.of(FirepitDevice.ID).with(FirepitDevice.LIT, "false")
                    .with(FirepitDevice.SMOKE_LEVEL, "0").with(FirepitDevice.AXIS, axis == Axis.Z ? "z" : "x");
                if (!blocks.place(at, state, true)) {
                    return false;
                }
                device.build(at, logs, devices::neighbourChanged);
                return true;
            }
        };
    }

    /** The heating recipes as the firepit uses them. */
    static FirepitDevice.Cooker cooker(HeatingService heating) {
        return new FirepitDevice.Cooker() {
            @Override
            public float recipeTemperature(ItemStack stack) {
                return heating.recipe(stack).map(r -> r.temperature()).orElse(Float.NaN);
            }

            @Override
            public Optional<ItemStack> convert(ItemStack heated, float before) {
                return heating.convertIfReady(heated, before).map(HeatingService.Conversion::item);
            }
        };
    }

    /** TFC {@code SHOVEL_DOUSE} (vanilla and TFC shovels) and our water bucket. */
    static FirepitEffects.Douse douse(ItemStack stack, Tags tags) {
        if (stack.getType() == Material.WATER_BUCKET) {
            return FirepitEffects.Douse.WATER;
        }
        return tags.is(stack, SHOVELS) ? FirepitEffects.Douse.SHOVEL : FirepitEffects.Douse.NONE;
    }

    /** TFC {@code BottomSupportedDeviceBlock.canSurvive}: a sturdy top face or {@code #tfc:solid_top_face} below. */
    static boolean canSurvive(Block at, Tags tags) {
        Block below = at.getRelative(BlockFace.DOWN);
        return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL) || tags.is(below, SOLID_TOP_FACE);
    }

    /** TFC {@code level.isRainingAt(pos)} at the firepit: PAR-CLIM-02's rain behind vanilla's sky and height gates. */
    static boolean raining(ModuleContext context, Block block) {
        World w = block.getWorld();
        return context.services().get(PrecipitationService.class)
            .map(p -> p.isRainingAt(w, new BlockPos(block.getX(), block.getY(), block.getZ()),
                PrecipitationService.rainReaches(w, block.getX(), block.getY(), block.getZ())))
            .orElse(false);
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
}
