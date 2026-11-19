/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: strong starts light the grill),
 *       src/main/java/net/dries007/tfc/common/component/food/FoodTraits.java @ c3b4d9536 (WOOD_GRILLED)
 */
package dev.tfp.paper.grill;

import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.grill.GrillSlots;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.firepit.FirepitEffects;
import dev.tfp.paper.firepit.FirepitModule;
import dev.tfp.paper.firepit.PaperFirepitEffects;
import dev.tfp.paper.grill.gui.GrillGuiListener;
import dev.tfp.paper.grill.gui.GrillScreens;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.weather.PrecipitationService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * PAR-HEAT-08: the grill. Registers the {@link GrillDevice} kind {@code tfp:grill}, its {@code FireLightable} (a strong
 * start lights it), the {@link GrillListener} (a wrought iron grill used on a firepit), the grill screen service
 * {@link GrillScreens} with its listener and the device access hook that keeps an open screen in step. Needs the tags,
 * device, GUI, recipe, heating, fire and firepit modules before it in the service file.
 *
 * <p>CraftEngine loads {@code tfp:grill} once its entry moves into {@code configuration/devices/} (PAR-HEAT-24); until
 * then no grill block exists in game and the conversion finds nothing to place, so the firepit stays a firepit.
 */
public final class GrillModule implements FeatureModule {
    public static final String ID = "grill";
    /** TFC {@code FoodTraits.WOOD_GRILLED}, ours. */
    public static final String WOOD_GRILLED = "tfp:wood_grilled";
    /** How far a click is traced to find where it lands on the grill (vanilla's block reach). */
    static final double REACH = 6.0;

    /** An open screen's key: the grill's block position. */
    record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** PAR-HEAT-34: the calendar-jump sweep of the burn-out callbacks. */
    private volatile Scheduler.TaskHandle sweep;

    @Override
    public void disable() {
        Scheduler.TaskHandle s = sweep;
        sweep = null;
        if (s != null) {
            s.cancel();
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        HeatingService heating = context.services().require(HeatingService.class);
        ItemHeatDefinitionsCache heatDefinitions = context.services().require(ItemHeatDefinitionsCache.class);
        Recipes recipes = context.services().require(Recipes.class);
        GuiService gui = context.services().require(GuiService.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Scheduler scheduler = context.scheduler();
        Supplier<FirepitFuels> fuels = fuels(() -> context.registryService().registries());
        ItemHeatHooks.Capacities capacities = ItemHeatHooks.Capacities.byId(tags::itemId, heatDefinitions);

        GrillScreens screens = new GrillScreens(gui, scheduler, context.messages(), TooltipStyles.TFC_DEFAULTS.heat());
        AtomicReference<GrillDevice> deviceRef = new AtomicReference<>();
        AtomicReference<GrillConversion> conversionRef = new AtomicReference<>();
        PaperFirepitEffects firepitEffects = new PaperFirepitEffects(scheduler, blocks,
            () -> context.integrations().blockVisuals(), () -> context.clock().get().playerTicks(),
            devices::neighbourChanged);
        sweep = firepitEffects.start();
        PaperGrillEffects effects = new PaperGrillEffects(scheduler, blocks, () -> context.integrations().blockVisuals(),
            firepitEffects, (player, block) -> scheduler.entityDelayed(player, 1, () -> {
                GrillDevice d = deviceRef.get();
                if (d != null) {
                    screens.open(player, Key.of(block), new DeviceGrillLink(devices, d, block, b -> owned(scheduler, b)),
                        p -> context.messages().get(p, "gui.grill.title"));
                }
            }, () -> { }), conversionRef::get);
        GrillDevice device = new GrillDevice(new GrillDevice.Services(
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> tags.is(stack, FirepitModule.FIREPIT_FUEL),
            capacities::of,
            cooker(heating),
            stack -> recipes.modifiers().addTrait(stack, WOOD_GRILLED),
            stack -> douse(stack, tags),
            block -> raining(context, block),
            block -> canSurvive(block, tags),
            n -> context.integrations().customItems().create("tfp:powder/wood_ash", n).orElse(ItemStack.empty()),
            () -> context.integrations().customItems().create(GrillDevice.GRILL_ITEM, 1).orElse(ItemStack.empty()),
            GrillModule::hit,
            ThreadLocalRandom::current, HeatConfig.DEFAULT, effects), devices.stacks());
        deviceRef.set(device);
        devices.register(device.kind());
        GrillConversion conversion = new GrillConversion(devices, blocks, effects);
        conversionRef.set(conversion);
        devices.hook(GrillDevice.ID, new DeviceService.AccessHook() {
            @Override
            public void before(Block block) {
                screens.edit(Key.of(block));
            }

            @Override
            public void after(Block block) {
                screens.reload(Key.of(block));
            }
        });
        context.services().provide(GrillScreens.class, screens);
        context.listen(new GrillListener(blocks, context.integrations().customItems(), scheduler, conversion));
        context.listen(new GrillGuiListener(screens));
        context.services().get(FireStarting.class).ifPresent(fire -> fire.register(ctx -> ctx.strong()
            && blocks.stateAt(ctx.block()).map(s -> s.id().equals(GrillDevice.ID)).orElse(false)
            && device.light(ctx.block(), devices::neighbourChanged)));
    }

    static boolean owned(Scheduler scheduler, Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * Where the player's click lands in {@code block}, block-local. A hit on the top face counts at TFC's grill shape
     * ({@link GrillSlots#topHitY}): the 11 px core top, or the 2 px base plate on the outer rim; the carrier's top is
     * flat at another height, and TFC's slot boxes sit above it (ledger divergence).
     */
    static Optional<GrillSlots.Point> hit(Player player, Block block) {
        RayTraceResult r = player.rayTraceBlocks(REACH);
        if (r == null || r.getHitBlock() == null || !r.getHitBlock().equals(block)) {
            return Optional.empty();
        }
        double x = r.getHitPosition().getX() - block.getX();
        double y = r.getHitPosition().getY() - block.getY();
        double z = r.getHitPosition().getZ() - block.getZ();
        return Optional.of(new GrillSlots.Point(x, r.getHitBlockFace() == BlockFace.UP ? GrillSlots.topHitY(x, z) : y, z));
    }

    /** The heating recipes, as the firepit uses them. */
    static dev.tfp.paper.firepit.FirepitDevice.Cooker cooker(HeatingService heating) {
        return new dev.tfp.paper.firepit.FirepitDevice.Cooker() {
            @Override
            public float recipeTemperature(ItemStack stack) {
                return heating.recipe(stack).map(r -> r.temperature()).orElse(Float.NaN);
            }

            @Override
            public Optional<ItemStack> convert(ItemStack heated, float temperature) {
                return heating.convertIfReady(heated, temperature).map(HeatingService.Conversion::item);
            }
        };
    }

    /** TFC {@code SHOVEL_DOUSE} and our water bucket, as the firepit's. */
    static FirepitEffects.Douse douse(ItemStack stack, Tags tags) {
        if (stack.getType() == Material.WATER_BUCKET) {
            return FirepitEffects.Douse.WATER;
        }
        return tags.is(stack, FirepitModule.SHOVELS) ? FirepitEffects.Douse.SHOVEL : FirepitEffects.Douse.NONE;
    }

    /** TFC {@code BottomSupportedDeviceBlock.canSurvive}. */
    static boolean canSurvive(Block at, Tags tags) {
        Block below = at.getRelative(BlockFace.DOWN);
        return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL) || tags.is(below, FirepitModule.SOLID_TOP_FACE);
    }

    static boolean raining(ModuleContext context, Block block) {
        World w = block.getWorld();
        return context.services().get(PrecipitationService.class)
            .map(p -> p.isRainingAt(w, new BlockPos(block.getX(), block.getY(), block.getZ()),
                PrecipitationService.rainReaches(w, block.getX(), block.getY(), block.getZ())))
            .orElse(false);
    }

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
