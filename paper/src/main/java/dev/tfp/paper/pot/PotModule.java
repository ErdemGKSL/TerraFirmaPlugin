/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start lights the pot),
 *       src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (FIREPIT_FUEL, SOLID_TOP_FACE, SOUP_BOWLS,
 *       EMPTY_JARS_WITH_LID, USABLE_IN_POT)
 */
package dev.tfp.paper.pot;

import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pot.gui.PotGuiListener;
import dev.tfp.paper.pot.gui.PotScreens;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.salad.SaladPdc;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.weather.PrecipitationService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-09: the cooking pot. Registers the {@link PotDevice} kind {@code tfp:pot}, the firepit-to-pot conversion and
 * the pot's bucket routing ({@link PotInteractListener}), the pot screen ({@link PotScreens}, also a module service) with
 * an access hook that keeps an open screen in step with the device, and the pot's {@code FireLightable} (a strong start
 * lights it). Needs the tags, device, GUI, recipe, food, fire and firepit modules before it in the service file; the rain
 * comes from PAR-CLIM-02's {@link PrecipitationService} when it runs.
 */
public final class PotModule implements FeatureModule {
    public static final String ID = "pot";
    public static final String FIREPIT_ID = "tfp:firepit";
    public static final String FIREPIT_FUEL = "tfc:firepit_fuel";
    public static final String SOLID_TOP_FACE = "tfc:solid_top_face";
    public static final String SOUP_BOWLS = "tfc:soup_bowls";
    public static final String EMPTY_JAR = "tfp:empty_jar";
    public static final String EMPTY_JARS_WITH_LID = "tfc:foods/empty_jars_with_lid";

    private volatile AutoCloseable hook;
    /** PAR-HEAT-34: the calendar-jump sweep of the burn-out callbacks. */
    private volatile Scheduler.TaskHandle sweep;
    private volatile PotScreens screens;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        GuiService gui = context.services().require(GuiService.class);
        ItemFoodHooks hooks = context.services().require(ItemFoodHooks.class);
        FoodDefinitionsCache definitions = context.services().require(FoodDefinitionsCache.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        CustomItems items = context.integrations().customItems();
        Scheduler scheduler = context.scheduler();
        Supplier<FirepitFuels> fuels = fuels(() -> context.registryService().registries());
        BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
            (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
            id -> items.create(id, 1), items::exists));
        PotRecipeBook book = new PotRecipeBook(new PotRecipeBook.Services(recipes,
            stack -> food(stack, tags::itemId, definitions), hooks::isRotten, stack -> hooks.stamp(stack, List.of()),
            tags::itemId, stack -> PotRecipeBook.vanillaRemainder(stack, tags::itemId)));
        PotScreens s = new PotScreens(gui, scheduler, context.messages(), TooltipStyles.TFC_DEFAULTS.heat());
        screens = s;
        PotDevice[] device = new PotDevice[1];
        PotDevice[] stove = new PotDevice[1];
        PotConversion[] conversion = new PotConversion[1];
        BiConsumer<Player, Block> open = (player, block) -> s.open(player, PotScreenLink.Key.of(block),
            new PotScreenLink(new PotScreenLink.Env(devices,
                blocks.stateAt(block).map(st -> PotDevice.STOVE_ID.equals(st.id())).orElse(false) ? stove[0] : device[0],
                scheduler), block),
            p -> context.messages().get(p, "gui.pot.title"));
        PaperPotEffects effects = new PaperPotEffects(scheduler, blocks, () -> context.integrations().blockVisuals(),
            () -> context.clock().get().playerTicks(), devices::neighbourChanged,
            block -> conversion[0].toFirepit(block), open);
        sweep = effects.start();
        PotDevice.Services services = new PotDevice.Services(
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> tags.is(stack, FIREPIT_FUEL),
            book, tanks,
            stack -> tags.is(stack, SOUP_BOWLS),
            stack -> tags.itemId(stack).map(EMPTY_JAR::equals).orElse(false),
            stack -> tags.is(stack, EMPTY_JARS_WITH_LID),
            block -> raining(context, block),
            block -> canSurvive(block, tags),
            n -> items.create(PotDevice.WOOD_ASH, n).orElse(ItemStack.empty()),
            () -> items.create(PotDevice.POT_ITEM, 1).orElse(ItemStack.empty()),
            ThreadLocalRandom::current, HeatConfig.DEFAULT, effects);
        device[0] = new PotDevice(services, devices.stacks());
        devices.register(device[0].kind());
        // PAR-HEAT-31: TFC's StovePotBlock extends PotBlock: the same rules and services on the block tfp:stove_pot
        stove[0] = new PotDevice(PotDevice.STOVE_ID, services, devices.stacks());
        devices.register(stove[0].kind());
        conversion[0] = new PotConversion(devices, blocks, device[0].kind());
        context.listen(new PotGuiListener(s));
        context.listen(new PotInteractListener(devices, conversion[0], tanks, tags::itemId, scheduler, context.logger()));
        ScreenSync sync = new ScreenSync(s, scheduler);
        AutoCloseable potHook = devices.hook(PotDevice.ID, sync);
        AutoCloseable stoveHook = devices.hook(PotDevice.STOVE_ID, sync);
        hook = () -> {
            potHook.close();
            stoveHook.close();
        };
        context.services().provide(PotScreens.class, s);
        context.services().get(FireStarting.class).ifPresent(fire -> {
            fire.register(ctx -> ctx.strong()
                && blocks.stateAt(ctx.block()).map(st -> st.id().equals(PotDevice.ID)).orElse(false)
                && device[0].light(ctx.block(), devices::neighbourChanged));
            fire.register(ctx -> ctx.strong()
                && blocks.stateAt(ctx.block()).map(st -> st.id().equals(PotDevice.STOVE_ID)).orElse(false)
                && stove[0].light(ctx.block(), devices::neighbourChanged));
        });
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle sw = sweep;
        sweep = null;
        if (sw != null) {
            sw.cancel();
        }
        AutoCloseable h = hook;
        if (h != null) {
            try {
                h.close();
            } catch (Exception ignored) {
                // a map removal
            }
        }
        hook = null;
        screens = null;
    }

    /**
     * Keeps an open screen in step with the other writers of the slots (a fuel or a bowl click, the burn-out callback,
     * removal): commits its copy before, reloads it after. On the viewer's thread: inline when this thread owns the viewer,
     * otherwise the reload is queued there.
     */
    record ScreenSync(PotScreens screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, screens::edit, false);
        }

        @Override
        public void after(Block block) {
            withViewer(block, screens::reload, true);
        }

        private void withViewer(Block block, java.util.function.Consumer<Object> action, boolean queue) {
            PotScreenLink.Key key = PotScreenLink.Key.of(block);
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

    /**
     * TFC {@code FoodCapability.get(stack).getData()}: the stack's own food data for a dynamic food, else its item's food
     * definition; empty when it is not food.
     */
    static Optional<FoodData> food(ItemStack stack, Function<ItemStack, Optional<String>> ids,
            Supplier<FoodDefinitions> defs) {
        Optional<FoodData> dynamic = SaladPdc.food(stack);
        if (dynamic.isPresent()) {
            return dynamic;
        }
        return ids.apply(stack).flatMap(id -> defs.get().get(id));
    }

    /** TFC {@code BottomSupportedDeviceBlock.canSurvive}: a sturdy top face or {@code #tfc:solid_top_face} below. */
    static boolean canSurvive(Block at, Tags tags) {
        Block below = at.getRelative(BlockFace.DOWN);
        return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL) || tags.is(below, SOLID_TOP_FACE);
    }

    /** TFC {@code level.isRainingAt(pos)} at the pot: PAR-CLIM-02's rain behind vanilla's sky and height gates. */
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
