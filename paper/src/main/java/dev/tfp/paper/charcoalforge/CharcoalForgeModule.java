/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start on a charcoal pile of
 *       seven layers or more makes a forge, on a forge or on the crucible above it lights it),
 *       src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (LAYERS)
 */
package dev.tfp.paper.charcoalforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.charcoalforge.CharcoalForgeData;
import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.charcoalforge.CharcoalForgeStructure;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.advancement.criteria.AdvancementTriggerEvent;
import dev.tfp.paper.advancement.criteria.CriteriaMap;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.crucible.CrucibleMolds;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.charcoalforge.gui.ForgeGuiListener;
import dev.tfp.paper.charcoalforge.gui.ForgeScreenLink;
import dev.tfp.paper.charcoalforge.gui.ForgeScreenSync;
import dev.tfp.paper.charcoalforge.gui.ForgeScreens;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.firebox.HeatConsumers;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.vessel.VesselItems;
import dev.tfp.paper.weather.PrecipitationService;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-08: the charcoal forge. Registers the {@link CharcoalForgeDevice} kind {@code tfp:charcoal_forge}, the fire
 * lightables (a strong start on a charcoal pile of seven or eight layers with a valid structure makes a forge; on an unlit
 * forge or on the crucible above it lights it), the forge as a {@link HeatConsumers} source (the crucible's hookup is in
 * {@code CrucibleModule}) and a chunk-load callback for lit forges (the burn-out shows on time). Needs the tags, device,
 * heating, fire and firebox modules before it in the service file. The device is a module service.
 *
 * <p>Debug: {@code /tfp debug charcoalforge <name> info} prints the forge the player looks at, {@code put <slot> <item id>}
 * puts one item into a slot (fuel 0 to 4, inputs 5 to 9, extra 10 to 13), {@code light} lights it from its fuel.
 */
public final class CharcoalForgeModule implements FeatureModule {
    public static final String ID = "charcoalforge";
    public static final String FORGE_FUEL = "tfc:forge_fuel";
    /** TFC {@code FoodTraits.CHARCOAL_GRILLED}, ours. */
    public static final String CHARCOAL_GRILLED = "tfp:charcoal_grilled";
    public static final String CHARCOAL_PILE = "tfp:charcoal_pile";
    public static final String CRUCIBLE = "tfp:crucible";
    /** TFC {@code CharcoalPileBlock.LAYERS} needed to make a forge. */
    public static final int FORGE_LAYERS = 7;
    static final double REACH = 6.0;

    private volatile CharcoalForgeDevice device;
    private volatile DeviceService devices;
    private volatile Scheduler scheduler;
    private volatile CustomItems items;
    private volatile ModuleContext context;
    private volatile Tags tags;
    private volatile ForgeScreens screens;
    private volatile AutoCloseable hook;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tg = ctx.services().require(Tags.class);
        HeatingService heating = ctx.services().require(HeatingService.class);
        ItemHeatDefinitionsCache definitions = ctx.services().require(ItemHeatDefinitionsCache.class);
        HeatConsumers consumers = ctx.services().require(HeatConsumers.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        Scheduler sch = ctx.scheduler();
        Supplier<FirepitFuels> fuels = fuels(() -> ctx.registryService().registries());
        PaperCharcoalForgeEffects effects = new PaperCharcoalForgeEffects(sch, blocks,
            () -> ctx.integrations().blockVisuals(), devs::neighbourChanged);
        CharcoalForgeDevice d = new CharcoalForgeDevice(new CharcoalForgeDevice.Services(
            stack -> tg.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> tg.is(stack, FORGE_FUEL),
            ItemHeatHooks.Capacities.byId(tg::itemId, definitions),
            stack -> container(ctx, stack),
            converter(heating),
            HeatConfig.DEFAULT,
            block -> raining(block),
            consumers,
            block -> blocks.stateAt(block).map(BlockStateRef::id),
            effects,
            stack -> ctx.services().get(Recipes.class).map(r -> r.modifiers().addTrait(stack, CHARCOAL_GRILLED)).orElse(stack),
            CrucibleMolds.lazy(() -> ctx.services().get(MoldItems.class), () -> ctx.services().get(VesselItems.class),
                ctx.services().require(AlloyRecipes.class))), devs.stacks());
        devs.register(d.kind());
        ctx.services().provide(CharcoalForgeDevice.class, d);
        d.structure(block -> valid(block, tg, sch)); // PAR-METAL-09: the multiblock scan on every callback and neighbour change
        installScreen(ctx, devs, d, sch);
        ctx.services().get(FireStarting.class).ifPresent(fire -> fire.register(fc -> {
            if (!fc.strong()) {
                return false;
            }
            // TFC LIT(state): the state at the click, so only lighting a built forge counts, not making one from the pile
            boolean forge = blocks.stateAt(fc.block()).map(BlockStateRef::id).filter(CharcoalForgeDevice.ID::equals).isPresent();
            boolean lit = lightable(blocks, devs, d, tg, sch, fc.block());
            if (lit && forge) {
                AdvancementTriggerEvent.publish(fc.player(), CriteriaMap.FORGE);
            }
            return lit;
        }));
        Optional<CatchUpService> catchUp = ctx.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devs, sch)));
        ctx.listen(new ForgeDropListener(blocks, devs, sch));
        this.device = d;
        this.devices = devs;
        this.scheduler = sch;
        this.items = ctx.integrations().customItems();
        this.context = ctx;
        this.tags = tg;
    }

    /** PAR-METAL-09: the forge screen, its listener and its sync with the device. */
    private void installScreen(ModuleContext ctx, DeviceService devs, CharcoalForgeDevice d, Scheduler sch) {
        Optional<GuiService> gui = ctx.services().get(GuiService.class);
        if (gui.isEmpty()) {
            ctx.logger().warning("charcoalforge: no GuiService; the forge opens no screen");
            return;
        }
        ForgeScreens s = new ForgeScreens(gui.get(), sch, ctx.messages(), TooltipStyles.TFC_DEFAULTS.heat());
        d.screen((player, block) -> sch.entityDelayed(player, 1, () -> {
            if (player.isOnline()) {
                s.open(player, ForgeScreenLink.Key.of(block),
                    new ForgeScreenLink(new ForgeScreenLink.Env(devs, d, sch), block),
                    p -> ctx.messages().get(p, "gui.charcoal_forge.title"));
            }
        }, () -> { }));
        ctx.listen(new ForgeGuiListener(s));
        hook = devs.hook(CharcoalForgeDevice.ID, new ForgeScreenSync(s, sch));
        ctx.services().provide(ForgeScreens.class, s);
        this.screens = s;
    }

    @Override
    public void disable() {
        CharcoalForgeDevice d = device;
        if (d != null) {
            d.screen(null);
            d.structure(null);
        }
        try {
            if (hook != null) {
                hook.close();
            }
        } catch (Exception e) {
            // closing a hook of a stopping module: nothing to do
        }
        hook = null;
        screens = null;
        device = null;
    }

    // ---- fire starting --------------------------------------------------------------------------------------

    /** TFC {@code onFireStart}: the pile, forge and crucible branches. Region thread of {@code block}. */
    static boolean lightable(CustomBlocks blocks, DeviceService devices, CharcoalForgeDevice device, Tags tags,
            Scheduler scheduler, Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return false;
        }
        String id = state.get().id();
        if (id.equals(CHARCOAL_PILE)) {
            if (layers(state.get()) < FORGE_LAYERS || !valid(block, tags, scheduler)) {
                return false;
            }
            BlockStateRef forge = CharcoalForgeDevice.state(2);
            if (!blocks.place(block, forge, true)) {
                return false;
            }
            device.createFromPile(devices, block);
            return true;
        }
        if (id.equals(CharcoalForgeDevice.ID)) {
            return valid(block, tags, scheduler) && device.lightAt(devices, block);
        }
        if (id.equals(CRUCIBLE)) {
            Block below = block.getRelative(BlockFace.DOWN);
            return blocks.stateAt(below).map(BlockStateRef::id).filter(CharcoalForgeDevice.ID::equals).isPresent()
                && valid(below, tags, scheduler) && device.lightAt(devices, below);
        }
        return false;
    }

    static int layers(BlockStateRef state) {
        try {
            return Integer.parseInt(state.properties().getOrDefault("layers", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** TFC {@code CharcoalForgeBlock.isValid(level, pos)}. Region thread of {@code forge}. */
    public static boolean valid(Block forge, Tags tags, Scheduler scheduler) {
        return CharcoalForgeStructure.isValid(new ForgeStructureWorld(forge, tags, scheduler));
    }

    // ---- services ------------------------------------------------------------------------------------------

    /** The heating recipes as the forge uses them: every recipe fires here, a melting one pours into the extra-slot molds. */
    static CharcoalForgeDevice.Converter converter(HeatingService heating) {
        return new CharcoalForgeDevice.Converter() {
            @Override
            public float recipeTemperature(ItemStack stack) {
                return heating.recipe(stack).map(r -> r.temperature()).orElse(Float.NaN);
            }

            @Override
            public Optional<ItemStack> convert(ItemStack heated, float temperature) {
                return heating.convertIfReady(heated, temperature).map(HeatingService.Conversion::item);
            }

            @Override
            public Optional<CharcoalForgeDevice.Melted> melt(ItemStack heated, float temperature) {
                return heating.convertIfReady(heated, temperature).map(c -> new CharcoalForgeDevice.Melted(c.item(),
                    c.fluid().map(f -> new FluidAmount(f.fluid(), f.amount())).orElse(FluidAmount.EMPTY)));
            }
        };
    }

    /** TFC {@code mightHaveCapability(FluidHandler.ITEM)}: a mold or a vessel. */
    static boolean container(ModuleContext ctx, ItemStack stack) {
        boolean mold = ctx.services().get(MoldItems.class).map(m -> m.isMold(stack)).orElse(false);
        return mold || ctx.services().get(VesselItems.class).map(v -> v.isVessel(stack)).orElse(false);
    }

    /** TFC {@code level.isRainingAt(pos.above()) || level.isRainingAt(pos.above(2))}. */
    boolean raining(Block block) {
        ModuleContext ctx = context;
        if (ctx == null) {
            return false;
        }
        World w = block.getWorld();
        return ctx.services().get(PrecipitationService.class).map(p -> {
            for (int dy = 1; dy <= 2; dy++) {
                int y = block.getY() + dy;
                if (p.isRainingAt(w, new BlockPos(block.getX(), y, block.getZ()),
                        PrecipitationService.rainReaches(w, block.getX(), y, block.getZ()))) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /** Lit forges of a loaded chunk get a callback: one whose fuel ran out meanwhile goes out, the rest re-queue. */
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
                .map(s -> s.id().equals(CharcoalForgeDevice.ID) && CharcoalForgeDevice.level(s) > 0).orElse(false);
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

    // ---- debug --------------------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        var info = Commands.literal("info").executes(tree.run(ctx -> onPlayer(ctx, this::info)));
        var light = Commands.literal("light").executes(tree.run(ctx -> onPlayer(ctx, this::light)));
        var put = Commands.literal("put").then(Commands.argument("slot", IntegerArgumentType.integer(0, CharcoalForgeSim.SLOTS - 1))
            .then(Commands.argument("item", StringArgumentType.greedyString())
                .executes(tree.run(ctx -> onPlayer(ctx, p -> put(p, IntegerArgumentType.getInteger(ctx, "slot"),
                    StringArgumentType.getString(ctx, "item")))))));
        tree.add("charcoalforge", c -> c.then(Commands.argument("name", StringArgumentType.word())
            .then(info).then(put).then(light)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            java.util.function.Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        Scheduler sch = scheduler;
        if (player == null || sch == null || !sch.entity(player, () -> reply.complete(action.apply(player)),
            () -> reply.complete(Reply.error("debug.charcoalforge.no_player")))) {
            reply.complete(Reply.error("debug.charcoalforge.no_player"));
        }
        return reply;
    }

    private Optional<Block> target(Player player) {
        Block b = player.getTargetBlockExact((int) REACH);
        if (b == null || device == null || !context.integrations().customBlocks().stateAt(b)
            .map(s -> s.id().equals(CharcoalForgeDevice.ID)).orElse(false)) {
            return Optional.empty();
        }
        return Optional.of(b);
    }

    private Reply light(Player player) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.charcoalforge.no_forge");
        }
        return device.lightAt(devices, b.get()) ? Reply.ok("debug.charcoalforge.lit")
            : Reply.error("debug.charcoalforge.not_lit");
    }

    private Reply info(Player player) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.charcoalforge.no_forge");
        }
        CharcoalForgeData[] seen = {null};
        Optional<DeviceRecord<CharcoalForgeData>> r = devices.edit(b.get(), device.kind(), (record, now) -> {
            seen[0] = record.state();
            return record;
        });
        device.pushHeat(b.get(), seen[0]);
        if (r.isEmpty()) {
            return Reply.error("debug.charcoalforge.no_forge");
        }
        CharcoalForgeData d = r.get().state();
        StringBuilder inputs = new StringBuilder();
        for (int slot = CharcoalForgeSim.SLOT_INPUT_MIN; slot <= CharcoalForgeSim.SLOT_INPUT_MAX; slot++) {
            ItemStack stack = devices.stacks().decode(r.get().slot(slot));
            if (stack.isEmpty()) {
                continue;
            }
            Optional<HeatState> heat = HeatPdc.read(stack);
            if (!inputs.isEmpty()) {
                inputs.append(", ");
            }
            inputs.append(slot).append(' ').append(stack.getType().getKey().getKey()).append(' ')
                .append(heat.map(h -> Math.round(h.temperature())).orElse(0)).append(" C");
        }
        return Reply.ok("debug.charcoalforge.info", d.state().lit(), Math.round(d.temperature()), d.state().burnTicks(),
            d.state().airTicks(), d.heatLevel(), inputs.isEmpty() ? "-" : inputs.toString());
    }

    private Reply put(Player player, int slot, String id) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.charcoalforge.no_forge");
        }
        Optional<ItemStack> made = items.create(id, 1);
        if (made.isEmpty()) {
            return Reply.error("debug.charcoalforge.unknown_item", id);
        }
        ItemStack one = made.get();
        if (!device.isItemValid(slot, one)) {
            return Reply.error("debug.charcoalforge.invalid", id, slot);
        }
        CharcoalForgeData[] seen = {null};
        Optional<DeviceRecord<CharcoalForgeData>> r = devices.edit(b.get(), device.kind(), (record, now) -> {
            seen[0] = record.state();
            return record.withSlot(slot, devices.stacks().encode(one));
        });
        device.pushHeat(b.get(), seen[0]);
        return r.isPresent() ? Reply.ok("debug.charcoalforge.put", id, slot) : Reply.error("debug.charcoalforge.no_forge");
    }
}
