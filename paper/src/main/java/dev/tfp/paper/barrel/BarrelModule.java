/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (getSize HUGE, getWeight
 * HEAVY), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (barrelCapacity),
 * src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java @ c3b4d9536 (useItemOn: openMenu)
 */
package dev.tfp.paper.barrel;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.barrel.gui.BarrelScreens;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.weather.PrecipitationService;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-08: the barrel. Registers the {@link BarrelDevice} kind ({@code tfp:wood/barrel/<wood>}) with the device framework
 * and the barrel item's size (TFC {@code SealableDeviceBlock}: huge and heavy). Needs the tags, device, recipe and item size
 * modules before it in the service file. The CraftEngine blocks are written by datagen (PAR-PROC-32).
 *
 * <p>PAR-PROC-35: a click opens the barrel screen ({@link BarrelScreens}, looked up when needed: the barrel GUI module comes
 * later in the service file) over a {@link BarrelScreenLink}; an access hook keeps an open screen in step with the other
 * writers of the slots; the rain at the block above comes from the {@link PrecipitationService}; and
 * {@code /tfp debug barrel [fill <mB> [fluid] | rain <on|off>]} inspects, fills or forces the rain on the barrel the
 * player looks at. A fluid container used on a barrel is routed by {@link BucketGuard}, which also keeps the vanilla item
 * use (a bucket emptied into the world) from following the click.
 */
public final class BarrelModule implements FeatureModule {
    public static final String ID = "barrel";
    /** The barrel block items. */
    static final String ITEM_PREFIX = BarrelDevice.ID + "/";
    /** How far {@code /tfp debug barrel} looks. */
    static final int REACH = 8;

    private volatile AutoCloseable sizer;
    private volatile AutoCloseable hook;
    private volatile BarrelScreenLink.Env env;
    private volatile BucketGuard guard;
    /** The barrels {@code /tfp debug barrel rain on} forces the climate's rain on (the vanilla gates still apply). */
    private final Set<BarrelScreenLink.Key> forcedRain = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        Optional<ItemSizes> sizes = context.services().get(ItemSizes.class);
        var items = context.integrations().customItems();
        BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
            (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
            id -> items.create(id, 1), items::exists));
        BarrelRecipeBook book = new BarrelRecipeBook(recipes, tanks, BarrelRules.DEFAULT_CAPACITY);
        Predicate<ItemStack> fits = sizes.<Predicate<ItemStack>>map(s -> s::fitsBarrel).orElse(s -> true);
        PaperBarrelEffects effects = new PaperBarrelEffects(context.scheduler(), context.integrations().customBlocks(),
            () -> context.integrations().blockVisuals());
        ModuleServices services = context.services();
        Scheduler scheduler = context.scheduler();
        Messages messages = context.messages();
        BarrelScreenLink.Env[] holder = new BarrelScreenLink.Env[1];
        BarrelDevice.Services barrel = new BarrelDevice.Services(book, fits,
            stack -> tags.is(stack, BarrelDevice.RODS_WOODEN), effects, rain(services, forcedRain),
            (player, block, blockId) -> open(holder[0], services, player, block, blockId));
        DeviceKind<BarrelData> kind = devices.register(BarrelDevice.kind(barrel, devices.stacks()));
        BarrelScreenLink.Env e = new BarrelScreenLink.Env(devices, kind, barrel, devices.stacks(), scheduler, messages,
            context.clock());
        holder[0] = e;
        env = e;
        hook = devices.hook(BarrelDevice.ID, new ScreenSync(services, scheduler));
        BucketGuard g = new BucketGuard(devices, context.integrations().customBlocks(), tanks, scheduler,
            context.logger());
        guard = g;
        context.listen(g);
        sizes.ifPresent(s -> sizer = s.register(id -> id.startsWith(ITEM_PREFIX),
            stack -> new ItemSizing(ItemSize.HUGE, ItemWeight.HEAVY)));
    }

    /**
     * TFC {@code level.isRainingAt(pos.above())}: vanilla's gates on the region thread, then the climate decision, which
     * {@code forced} (the debug command) replaces by "raining" for its barrels. Vanilla weather ({@code /weather rain})
     * does not decide it; TFC's climate does ({@link PrecipitationService}), as in TFC.
     */
    static BarrelDevice.Rain rain(ModuleServices services, Set<BarrelScreenLink.Key> forced) {
        return barrel -> {
            int x = barrel.getX();
            int y = barrel.getY() + 1;
            int z = barrel.getZ();
            if (!PrecipitationService.rainReaches(barrel.getWorld(), x, y, z)) {
                return false;
            }
            if (forced.contains(BarrelScreenLink.Key.of(barrel))) {
                return true;
            }
            Optional<PrecipitationService> p = services.get(PrecipitationService.class);
            return p.isPresent() && p.get().isRainingAt(barrel.getWorld(), new BlockPos(x, y, z), true);
        };
    }

    /**
     * TFC {@code BarrelBlock.useItemOn} consumes every click on a barrel, so a held bucket never reaches the world. Here a
     * fluid container (a vanilla bucket, TFC's buckets, jugs and bottles: {@link BarrelItemTanks#tank}) used on a barrel
     * is routed to the device from the Bukkit event, before the world bucket listener (HIGH) and CraftEngine (HIGHEST),
     * and the event is cancelled whatever the device did, so no vanilla or TFC world bucket use runs. A failed device call
     * (logged by the device service) still keeps the bucket out of the world.
     *
     * <p>Why not through CraftEngine's {@code useOnBlock} alone (the PAR-PROC-35 in-game failure): the client sees the
     * barrel's carrier state as a block without a use, so it sends the item use packet after the block use. Paper drops
     * that follow-up only while the held stack is unchanged; once the barrel changed the bucket (an empty one filled from
     * the tank), Paper fires a second {@code PlayerInteractEvent}, CraftEngine passes it (one interaction per tick), and
     * vanilla {@code BucketItem.use} emptied the new water bucket next to the barrel. So every interaction of the same
     * player in the tick of a barrel bucket click is cancelled too (main and off hand, block and air), and a
     * {@code PlayerBucketEmptyEvent} / {@code PlayerBucketFillEvent} at a barrel is refused. The world bucket listener
     * (PAR-FLUID-02) sees a barrel as its fake material and would otherwise empty TFC's buckets next to it.
     *
     * <p>Sneaking with an item in hand skips the block, as vanilla's {@code isSecondaryUseActive} does in TFC: the bucket
     * acts on the world then. Threads: the event thread (the player's region); a barrel on another region is left alone.
     */
    static final class BucketGuard implements Listener {
        private final DeviceService devices;
        private final CustomBlocks blocks;
        private final BarrelItemTanks tanks;
        private final Scheduler scheduler;
        private final Logger logger;
        /** The player's own tick ({@link Player#getTicksLived()}) of their last barrel bucket click. */
        private final Map<UUID, Integer> lastUse = new ConcurrentHashMap<>();

        BucketGuard(DeviceService devices, CustomBlocks blocks, BarrelItemTanks tanks, Scheduler scheduler,
                Logger logger) {
            this.devices = devices;
            this.blocks = blocks;
            this.tanks = tanks;
            this.scheduler = scheduler;
            this.logger = logger;
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onInteract(PlayerInteractEvent event) {
            Action action = event.getAction();
            if (event.getHand() == null || (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)) {
                return;
            }
            Player player = event.getPlayer();
            if (sameTick(player)) {
                // the follow-up of a barrel bucket click (Paper's item use packet once the hand changed, the off hand)
                event.setCancelled(true);
                return;
            }
            Block clicked = event.getClickedBlock();
            ItemStack held = event.getItem();
            if (action != Action.RIGHT_CLICK_BLOCK || clicked == null || tanks.tank(held).isEmpty()
                    || player.isSneaking() || !isBarrel(clicked)) {
                return;
            }
            lastUse.put(player.getUniqueId(), player.getTicksLived());
            event.setCancelled(true);
            try {
                devices.use(clicked, player, event.getHand(), held);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Barrel at " + clicked.getWorld().getName() + " " + clicked.getX() + ","
                    + clicked.getY() + "," + clicked.getZ() + ": the bucket click failed", e);
            }
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onBucketEmpty(PlayerBucketEmptyEvent event) {
            if (refuses(event.getPlayer(), event.getBlockClicked())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onBucketFill(PlayerBucketFillEvent event) {
            if (refuses(event.getPlayer(), event.getBlockClicked())) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            lastUse.remove(event.getPlayer().getUniqueId());
        }

        private boolean refuses(Player player, Block clicked) {
            return sameTick(player) || (clicked != null && !player.isSneaking() && isBarrel(clicked));
        }

        private boolean sameTick(Player player) {
            Integer last = lastUse.get(player.getUniqueId());
            return last != null && last == player.getTicksLived();
        }

        /** A {@code tfp:wood/barrel/<wood>} block on a region this thread owns. */
        boolean isBarrel(Block block) {
            if (!scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
                return false;
            }
            return blocks.stateAt(block).map(BlockStateRef::id).filter(id -> id.startsWith(ITEM_PREFIX)).isPresent();
        }
    }

    /** Opens the screen on the player's thread next tick (after the click's record is stored). */
    static void open(BarrelScreenLink.Env env, ModuleServices services, Player player, Block block, String blockId) {
        Optional<BarrelScreens> screens = services.get(BarrelScreens.class);
        if (env == null || screens.isEmpty()) {
            return;
        }
        env.scheduler().entityDelayed(player, 1, () -> {
            if (!player.isOnline()) {
                return;
            }
            BarrelScreenLink link = new BarrelScreenLink(env, block, blockId);
            screens.get().open(player, link.key(), link, p -> env.messages().get(p, "gui.barrel.title"));
        }, () -> { });
    }

    /**
     * Keeps an open screen in step with the other writers of the slots (the sneak seal, a bucket, a neighbour update,
     * removal): commits its copy before, reloads it after. On the viewer's thread: inline when this thread owns the
     * viewer, otherwise the reload is queued there (the click is then not preceded by the commit; the commit after it is
     * refused by a sealed barrel, see {@link BarrelScreenLink#commit}).
     */
    record ScreenSync(ModuleServices services, Scheduler scheduler) implements DeviceService.AccessHook {
        @Override
        public void before(Block block) {
            withViewer(block, (screens, key) -> screens.edit(key), false);
        }

        @Override
        public void after(Block block) {
            withViewer(block, (screens, key) -> screens.reload(key), true);
        }

        private void withViewer(Block block, java.util.function.BiConsumer<BarrelScreens, Object> action, boolean queue) {
            Optional<BarrelScreens> screens = services.get(BarrelScreens.class);
            if (screens.isEmpty()) {
                return;
            }
            BarrelScreenLink.Key key = BarrelScreenLink.Key.of(block);
            Optional<GuiSession> session = screens.get().viewer(key);
            if (session.isEmpty()) {
                return;
            }
            Player viewer = session.get().viewer();
            Location at = viewer.getLocation();
            if (scheduler.isOwner(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                action.accept(screens.get(), key);
            } else if (queue) {
                scheduler.entity(viewer, () -> action.accept(screens.get(), key), () -> { });
            }
        }
    }

    // ---- /tfp debug barrel ----------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("barrel", node -> node
            .executes(tree.player((p, ctx) -> debug(p, -1, TfcFluids.WATER)))
            .then(Commands.literal("rain")
                .then(Commands.argument("on", BoolArgumentType.bool())
                    .executes(tree.player((p, ctx) -> rain(p, BoolArgumentType.getBool(ctx, "on"))))))
            .then(Commands.literal("fill")
                .then(Commands.argument("mB", IntegerArgumentType.integer(0, BarrelRules.DEFAULT_CAPACITY))
                    .executes(tree.player((p, ctx) -> debug(p, IntegerArgumentType.getInteger(ctx, "mB"), TfcFluids.WATER)))
                    .then(Commands.argument("fluid", StringArgumentType.word())
                        .executes(tree.player((p, ctx) -> debug(p, IntegerArgumentType.getInteger(ctx, "mB"),
                            StringArgumentType.getString(ctx, "fluid"))))))));
    }

    /**
     * {@code /tfp debug barrel}: the barrel the player looks at, settled and described ({@code amount < 0}), or its tank set
     * to {@code amount} mB of {@code fluidName} first (an unsealed barrel only).
     */
    CompletableFuture<Reply> debug(Player player, int amount, String fluidName) {
        return debug(player, amount, fluidName, null);
    }

    /**
     * {@code /tfp debug barrel rain <on|off>}: forces the climate's rain on (or back off) at the barrel the player looks
     * at, so the lazy rain fill can be checked in game (TFC: 1 mB per 4 ticks, 100 mB per 400); the vanilla gates (sky, the
     * column's top) still apply. The barrel is settled at once, which starts or ends a rainy interval. Not persisted.
     */
    CompletableFuture<Reply> rain(Player player, boolean on) {
        return debug(player, -1, TfcFluids.WATER, on);
    }

    private CompletableFuture<Reply> debug(Player player, int amount, String fluidName, Boolean forceRain) {
        BarrelScreenLink.Env e = env;
        if (e == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.gui.refused"));
        }
        Optional<TfcFluid> fluid = TfcFluids.resolve(fluidName);
        if (fluid.isEmpty()) {
            return CompletableFuture.completedFuture(Reply.error("debug.fluid.unknown_fluid", fluidName));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        boolean scheduled = e.scheduler().entity(player, () -> {
            Block block = player.getTargetBlockExact(REACH);
            if (block == null) {
                out.complete(Reply.error("debug.block.none", REACH));
                return;
            }
            String where = block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ();
            e.scheduler().region(block.getLocation(), () -> {
                try {
                    BarrelScreenLink.Key key = BarrelScreenLink.Key.of(block);
                    if (forceRain != null && e.devices().inspect(block)
                            .filter(i -> i.kindId().equals(BarrelDevice.ID)).isPresent()) {
                        if (forceRain) {
                            forcedRain.add(key);
                        } else {
                            forcedRain.remove(key);
                        }
                    }
                    out.complete(debugAt(e, block, where, amount, fluid.get(), forcedRain.contains(key)));
                } catch (RuntimeException ex) {
                    out.completeExceptionally(ex);
                }
            });
        }, () -> out.complete(Reply.error("command.player_only")));
        if (!scheduled) {
            out.complete(Reply.error("command.player_only"));
        }
        return out;
    }

    /** The debug step on the barrel's region thread. */
    static Reply debugAt(BarrelScreenLink.Env e, Block block, String where, int amount, TfcFluid fluid, boolean forced) {
        // an open screen sees the new tank at its next reload (the fill changes no slot)
        boolean raining = e.services().rain().rainingAbove(block);
        BarrelData[] before = new BarrelData[1];
        boolean[] sealed = new boolean[1];
        Optional<DeviceRecord<BarrelData>> stored = e.devices().edit(block, e.kind(), (record, now) -> {
            before[0] = record.state();
            DeviceRecord<BarrelData> r = BarrelDevice.settle(record, now, raining, block, e.services(), e.stacks());
            sealed[0] = r.state().sealed();
            if (amount < 0 || sealed[0]) {
                return r;
            }
            FluidAmount tank = amount == 0 ? FluidAmount.EMPTY : new FluidAmount(fluid.id(), amount);
            BarrelContents c = BarrelDevice.contents(r, e.stacks()).withFluid(tank);
            return BarrelDevice.write(r, r.state(), BarrelDevice.instant(block, c, e.services()), e.stacks());
        });
        if (stored.isEmpty()) {
            return Reply.error("debug.barrel.none", where);
        }
        BarrelDevice.surface(block, before[0], stored.get().state(), e.services());
        BarrelData d = stored.get().state();
        List<String> lines = new ArrayList<>();
        lines.add("fluid: " + (d.fluid().isEmpty() ? "-" : d.fluid().amount() + " mB " + d.fluid().fluid()) + " / "
            + e.services().book().capacity() + " mB");
        lines.add("sealed: " + d.sealed() + (d.sealed() ? " at " + d.sealedTick() + ", recipe tick " + d.recipeTick() : ""));
        lines.add("rain: " + (raining ? "raining" : "dry") + " above" + (forced ? " (forced)" : "") + ", clock "
            + d.rainTick());
        BarrelContents c = BarrelDevice.contents(stored.get(), e.stacks());
        lines.add("slots: in " + describe(c.containerIn()) + ", out " + describe(c.containerOut()) + ", item "
            + describe(c.main()) + ", excess " + c.excess().size());
        String header = amount >= 0 && sealed[0] ? "debug.barrel.sealed" : "debug.block.header";
        return (amount >= 0 && sealed[0] ? Reply.error(header, where) : Reply.ok(header, where)).withDetails(lines);
    }

    private static String describe(ItemStack s) {
        return s == null || s.isEmpty() ? "-" : s.getAmount() + " x " + s.getType().getKey();
    }

    @Override
    public void disable() {
        env = null;
        BucketGuard g = guard;
        if (g != null) {
            HandlerList.unregisterAll(g);
        }
        guard = null;
        forcedRain.clear();
        for (AutoCloseable c : new AutoCloseable[] {sizer, hook}) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // the registrations are map and list removals
                }
            }
        }
        sizer = null;
        hook = null;
    }
}
