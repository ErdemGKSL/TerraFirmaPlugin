/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (the block and its menu),
 *       src/main/java/net/dries007/tfc/common/component/heat/IHeatConsumer.java @ c3b4d9536 (the crucible as a consumer)
 */
package dev.tfp.paper.crucible;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.crucible.gui.CrucibleGuiListener;
import dev.tfp.paper.crucible.gui.CrucibleScreens;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.firebox.HeatConsumers;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
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
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipStyles;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-12: the crucible. Registers the {@link CrucibleDevice} kind {@code tfp:crucible}, its screen
 * ({@link CrucibleScreens}, also a module service) with an access hook that keeps an open screen in step with the device,
 * and the crucible as a {@link HeatConsumers} consumer: the heat source below (the firebox; the charcoal forge when it
 * exists) hands it the runs of temperature it gave. Needs the tags, device, GUI, recipe, heat, alloy and firebox modules
 * before it in the service file.
 *
 * <p>Debug: {@code /tfp debug crucible <name> put <item id>} puts one item into the first empty input of the crucible the
 * player looks at, {@code heat <degrees> <ticks>} gives it that temperature for the last {@code ticks} ticks as a heat
 * source would, {@code open} opens its screen, and {@code info} prints its temperature and alloy.
 */
public final class CrucibleModule implements FeatureModule {
    public static final String ID = "crucible";
    /** The heat sources whose block below a crucible is brought up to date before the crucible is read. */
    static final Set<String> SOURCES = Set.of("tfp:firebox", "tfp:charcoal_forge");
    static final double REACH = 6.0;
    /** TFC {@code FoodTraits.BURNT_TO_A_CRISP}, ours: what a crucible leaves of melted food. */
    static final String BURNT_TO_A_CRISP = "tfp:burnt_to_a_crisp";

    private volatile AutoCloseable consumer;
    private volatile AutoCloseable hook;
    private volatile CrucibleDevice device;
    private volatile CrucibleScreens screens;
    private volatile DeviceService devices;
    private volatile Scheduler scheduler;
    private volatile CustomItems items;
    private volatile ModuleContext context;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        GuiService gui = ctx.services().require(GuiService.class);
        HeatingService heating = ctx.services().require(HeatingService.class);
        ItemHeatDefinitionsCache definitions = ctx.services().require(ItemHeatDefinitionsCache.class);
        HeatConsumers consumers = ctx.services().require(HeatConsumers.class);
        AlloyRecipes alloys = ctx.services().require(AlloyRecipes.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        Scheduler sch = ctx.scheduler();
        FluidLang lang = FluidLang.fromJar(ctx.plugin().getClass().getClassLoader());
        if (!lang.load(FluidLang.FALLBACK)) {
            ctx.logger().warning("crucible: " + FluidLang.JAR_DIR + FluidLang.FALLBACK + ".json is missing; the alloy text uses ids");
        }
        CrucibleAlloyText text = new CrucibleAlloyText(lang, alloys::get);
        CrucibleScreens s = new CrucibleScreens(gui, sch, ctx.messages(), TooltipStyles.TFC_DEFAULTS.heat(), text::lines);
        CrucibleDevice[] holder = new CrucibleDevice[1];
        BiConsumer<Player, Block> open = (player, block) -> sch.entityDelayed(player, 1, () -> {
            if (player.isOnline()) {
                s.open(player, CrucibleScreenLink.Key.of(block),
                    new CrucibleScreenLink(new CrucibleScreenLink.Env(devs, holder[0], sch), block),
                    p -> ctx.messages().get(p, "gui.crucible.title"));
            }
        }, () -> { });
        Recipes recipes = ctx.services().require(Recipes.class);
        CrucibleMolds molds = CrucibleMolds.lazy(() -> ctx.services().get(MoldItems.class), alloys);
        CrucibleDevice d = new CrucibleDevice(new CrucibleDevice.Services(
            ItemHeatHooks.Capacities.byId(tags::itemId, definitions),
            CrucibleDevice.Heating.of(heating, stack -> recipes.modifiers().addTrait(stack, BURNT_TO_A_CRISP)),
            HeatConfig.DEFAULT, CrucibleRules.DEFAULT_CAPACITY,
            molds::isMold, // TFC: the output slot takes what has a fluid handler; ours the fired molds
            open, block -> heatBelow(blocks, devs, block), molds), devs.stacks());
        holder[0] = d;
        devs.register(d.kind());
        try {
            consumer = consumers.register(CrucibleDevice.ID, (block, from, to, temperature) ->
                CrucibleDevice.heat(from, to, temperature, () -> devs.edit(block, d.kind(), (record, now) -> record)));
        } catch (IllegalArgumentException e) {
            ctx.logger().warning("crucible: " + e.getMessage());
        }
        ctx.listen(new CrucibleGuiListener(s));
        ctx.listen(new CrucibleDrops(ctx.integrations().customItems()));
        hook = devs.hook(CrucibleDevice.ID, new ScreenSync(s, sch));
        ctx.services().provide(CrucibleScreens.class, s);
        this.device = d;
        this.screens = s;
        this.devices = devs;
        this.scheduler = sch;
        this.items = ctx.integrations().customItems();
        this.context = ctx;
    }

    @Override
    public void disable() {
        close(consumer);
        close(hook);
        consumer = null;
        hook = null;
        screens = null;
        device = null;
    }

    private static void close(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // a map removal
            }
        }
    }

    /** The heat source below {@code crucible} catches up, which hands its runs to the crucible. Region thread. */
    static void heatBelow(CustomBlocks blocks, DeviceService devices, Block crucible) {
        Block below = crucible.getRelative(BlockFace.DOWN);
        if (blocks.stateAt(below).map(BlockStateRef::id).filter(SOURCES::contains).isPresent()) {
            devices.neighbourChanged(below);
        }
    }

    /**
     * Keeps an open screen in step with the other writers of the slots (a heat run melting an item, removal): commits its
     * copy before, reloads it after. On the viewer's thread: inline when this thread owns the viewer, otherwise the reload
     * is queued there.
     */
    record ScreenSync(CrucibleScreens screens, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, screens::edit, false);
        }

        @Override
        public void after(Block block) {
            withViewer(block, screens::reload, true);
        }

        private void withViewer(Block block, java.util.function.Consumer<Object> action, boolean queue) {
            CrucibleScreenLink.Key key = CrucibleScreenLink.Key.of(block);
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

    // ---- debug --------------------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        var open = Commands.literal("open").executes(tree.run(ctx -> onPlayer(ctx, this::openTarget)));
        var info = Commands.literal("info").executes(tree.run(ctx -> onPlayer(ctx, this::info)));
        var put = Commands.literal("put").then(Commands.argument("item", StringArgumentType.greedyString())
            .executes(tree.run(ctx -> onPlayer(ctx, p -> put(p, StringArgumentType.getString(ctx, "item"))))));
        var heat = Commands.literal("heat").then(Commands.argument("degrees", IntegerArgumentType.integer(0, 2000))
            .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 10_000_000))
                .executes(tree.run(ctx -> onPlayer(ctx, p -> heat(p, IntegerArgumentType.getInteger(ctx, "degrees"),
                    IntegerArgumentType.getInteger(ctx, "ticks")))))));
        tree.add("crucible", c -> c.then(Commands.argument("name", StringArgumentType.word())
            .then(open).then(info).then(put).then(heat)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            java.util.function.Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        Scheduler sch = scheduler;
        if (player == null || sch == null || !sch.entity(player, () -> reply.complete(action.apply(player)),
            () -> reply.complete(Reply.error("debug.crucible.no_player")))) {
            reply.complete(Reply.error("debug.crucible.no_player"));
        }
        return reply;
    }

    private Optional<Block> target(Player player) {
        Block b = player.getTargetBlockExact((int) REACH);
        CrucibleDevice dev = device;
        if (b == null || dev == null || !context.integrations().customBlocks().stateAt(b)
            .map(s -> s.id().equals(CrucibleDevice.ID)).orElse(false)) {
            return Optional.empty();
        }
        return Optional.of(b);
    }

    private Reply openTarget(Player player) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.crucible.no_crucible");
        }
        heatBelow(context.integrations().customBlocks(), devices, b.get());
        BiConsumer<Player, Block> open = device.services().open();
        open.accept(player, b.get());
        return Reply.ok("debug.crucible.opened");
    }

    private Reply info(Player player) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.crucible.no_crucible");
        }
        Optional<DeviceRecord<CrucibleData>> r = devices.edit(b.get(), device.kind(), (record, now) -> record);
        if (r.isEmpty()) {
            return Reply.error("debug.crucible.no_crucible");
        }
        CrucibleData d = r.get().state();
        int items = 0;
        for (int i = 0; i < CrucibleDevice.SLOTS; i++) {
            if (!r.get().slot(i).isEmpty()) {
                items++;
            }
        }
        return Reply.ok("debug.crucible.info", Math.round(d.degrees()), d.alloy().amount(),
            d.alloy().contents().keySet().toString(), items);
    }

    private Reply put(Player player, String id) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.crucible.no_crucible");
        }
        Optional<ItemStack> made = items.create(id, 1);
        if (made.isEmpty()) {
            return Reply.error("debug.crucible.unknown_item", id);
        }
        ItemStack one = made.get();
        boolean[] put = {false};
        Optional<DeviceRecord<CrucibleData>> r = devices.edit(b.get(), device.kind(), (record, now) -> {
            for (int slot = 0; slot < CrucibleRules.INPUT_SLOTS; slot++) {
                if (record.slot(slot).isEmpty() && device.isItemValid(slot, one)) {
                    put[0] = true;
                    return record.withSlot(slot, devices.stacks().encode(one));
                }
            }
            return record;
        });
        return r.isPresent() && put[0] ? Reply.ok("debug.crucible.put", id) : Reply.error("debug.crucible.full", id);
    }

    private Reply heat(Player player, int degrees, int ticks) {
        Optional<Block> b = target(player);
        if (b.isEmpty()) {
            return Reply.error("debug.crucible.no_crucible");
        }
        long now = context.clock().get().playerTicks();
        CrucibleDevice.heat(Math.max(1, now - ticks + 1), now, degrees,
            () -> devices.edit(b.get(), device.kind(), (record, t) -> record));
        return Reply.ok("debug.crucible.heated", degrees, ticks);
    }
}
