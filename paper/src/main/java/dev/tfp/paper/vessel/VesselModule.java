/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536
 */
package dev.tfp.paper.vessel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatingRecipe;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.Melt;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tooltip.TooltipPipeline;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-04: the small vessel. Right-click opens the four-slot screen ({@link VesselScreens}); the contents melt into the
 * alloy once the vessel is hot ({@link VesselItems#settle}); the tooltip shows the alloy ({@link VesselTooltipContributor}).
 * Listed after the {@code gui}, {@code tooltip}, {@code itemsize} and {@code heat} modules in the service file, whose
 * services it requires. Also provides {@link VesselItems} and {@link VesselScreens} to the pour item (PAR-POT-05).
 *
 * <p>Debug: {@code /tfp debug vessel <name> open} opens the held vessel, {@code put <item id>} and
 * {@code putn <count> <item id>} insert into it.
 */
public final class VesselModule implements FeatureModule {
    public static final String ID = "vessel";

    private volatile VesselListener listener;
    private volatile VesselItems items;
    private volatile VesselScreens screens;
    private volatile CustomItems customItems;
    private volatile dev.tfp.paper.scheduler.Scheduler scheduler;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        GuiService gui = context.services().require(GuiService.class);
        ItemSizes sizes = context.services().require(ItemSizes.class);
        HeatingService heating = context.services().require(HeatingService.class);
        ItemHeatDefinitionsCache definitions = context.services().require(ItemHeatDefinitionsCache.class);
        CustomItems custom = context.integrations().customItems();
        VesselItems v = new VesselItems(new VesselItems.Services(() -> context.clock().get().playerTicks(),
            custom::idOf, id -> definitions.get().heatCapacity(id), stack -> melt(heating, stack), sizes::fitsSmallVessel,
            () -> context.services().get(dev.tfp.paper.alloy.AlloyRecipes.class).map(dev.tfp.paper.alloy.AlloyRecipes::get)
                .orElse(java.util.List.of())),
            HeatConfig.DEFAULT);
        VesselScreens s = new VesselScreens(gui, context.scheduler(), context.messages(), v);
        VesselListener l = new VesselListener(v, s, context.scheduler());
        items = v;
        screens = s;
        customItems = custom;
        scheduler = context.scheduler();
        listener = l;
        context.listen(l);
        context.services().provide(VesselItems.class, v);
        context.services().provide(VesselScreens.class, s);
        for (Player p : context.plugin().getServer().getOnlinePlayers()) {
            l.startSweep(p);
        }

        FluidLang lang = FluidLang.fromJar(context.plugin().getClass().getClassLoader());
        if (!lang.load(FluidLang.FALLBACK)) {
            context.logger().warning("vessel: " + FluidLang.JAR_DIR + FluidLang.FALLBACK + ".json is missing; the alloy line uses ids");
        }
        context.services().require(TooltipPipeline.class).add(new VesselTooltipContributor(lang));
    }

    @Override
    public void disable() {
        VesselListener l = listener;
        listener = null;
        if (l != null) {
            l.stopSweeps();
        }
    }

    /**
     * The melt of one item of {@code stack}: the heating recipe's temperature and fluid, with the heat values of the
     * fluid's metal (TFC {@code FluidHeat}); empty for an item without a recipe, without a fluid or with a non-metal fluid.
     */
    static Optional<Melt> melt(HeatingService heating, ItemStack stack) {
        Optional<HeatingRecipe> recipe = heating.recipe(stack);
        if (recipe.isEmpty()) {
            return Optional.empty();
        }
        Optional<dev.tfp.core.recipe.fluid.FluidAmount> fluid = HeatingService.fluid(recipe.get(), stack);
        if (fluid.isEmpty() || fluid.get().amount() <= 0) {
            return Optional.empty();
        }
        return VesselItems.fluidHeat(fluid.get().fluid()).map(h -> new Melt(recipe.get().temperature(),
            new FluidAmount(fluid.get().fluid(), fluid.get().amount()), h[0], h[1]));
    }

    @Override
    public void debugCommands(DebugTree tree) {
        var open = Commands.literal("open").executes(tree.run(ctx -> onPlayer(ctx, this::open)));
        var put = Commands.literal("put").then(Commands.argument("item", StringArgumentType.greedyString())
            .executes(tree.run(ctx -> onPlayer(ctx, p -> put(p, StringArgumentType.getString(ctx, "item"), 1)))));
        var putn = Commands.literal("putn").then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
            .then(Commands.argument("item", StringArgumentType.greedyString())
                .executes(tree.run(ctx -> onPlayer(ctx, p -> put(p, StringArgumentType.getString(ctx, "item"),
                    IntegerArgumentType.getInteger(ctx, "count")))))));
        tree.add("vessel", v -> v.then(Commands.argument("name", StringArgumentType.word())
            .then(open).then(put).then(putn)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            java.util.function.Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        if (player == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.vessel.no_player"));
        }
        return onPlayer(player, () -> action.apply(player));
    }

    private CompletableFuture<Reply> onPlayer(Player player, java.util.function.Supplier<Reply> action) {
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        dev.tfp.paper.scheduler.Scheduler sch = scheduler;
        if (sch == null || !sch.entity(player, () -> reply.complete(action.get()),
            () -> reply.complete(Reply.error("debug.vessel.no_vessel")))) {
            reply.complete(Reply.error("debug.vessel.no_vessel"));
        }
        return reply;
    }

    private Reply open(Player player) {
        VesselScreens s = screens;
        if (s == null || !items.isVessel(player.getInventory().getItemInMainHand())) {
            return Reply.error("debug.vessel.no_vessel");
        }
        return s.use(player, EquipmentSlot.HAND) == null ? Reply.error("debug.vessel.not_opened") : Reply.ok("debug.vessel.opened");
    }

    private Reply put(Player player, String id, int count) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.isVessel(held)) {
            return Reply.error("debug.vessel.no_vessel");
        }
        Optional<ItemStack> made = customItems.create(id, count);
        if (made.isEmpty()) {
            return Reply.error("debug.vessel.unknown_item", id);
        }
        ItemStack rest = made.get();
        for (int slot = 0; slot < dev.tfp.core.vessel.VesselRules.SLOTS && !rest.isEmpty(); slot++) {
            rest = items.insert(held, slot, rest);
        }
        player.getInventory().setItemInMainHand(held);
        return Reply.ok("debug.vessel.put", count - rest.getAmount(), id);
    }
}
