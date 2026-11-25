/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (isItemValid: the hammer and
 * flux tags)
 */
package dev.tfp.paper.anvil;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.anvil.plan.AnvilPlanScreens;
import dev.tfp.paper.anvil.plan.ForgingPdc;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import dev.tfp.paper.anvil.plan.PlanBook;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-METAL-03: the anvil blocks. Registers the device kinds of the metal and rock anvils with the device framework (the
 * blocks are written by datagen, {@code configuration/anvil/anvil.yml}) and the {@link AnvilListener}. Needs the tags and
 * device modules before it in the service file. The plan selection screen (PAR-METAL-04, {@link AnvilPlanScreens}, also a
 * module service) opens on the plain click; the forging screen (PAR-METAL-05) and welding (PAR-METAL-06) build on the same kinds.
 */
public final class AnvilModule implements FeatureModule {
    public static final String ID = "anvil";
    static final double REACH = 6.0;

    private volatile DeviceService devices;
    private volatile AnvilPlanScreens plans;
    private volatile CustomBlocks blocks;
    private volatile Scheduler scheduler;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        PaperAnvilEffects effects = new PaperAnvilEffects(context.scheduler(), context.integrations().customBlocks(),
            () -> context.integrations().blockVisuals());
        AnvilClicks clicks = new AnvilClicks();
        Recipes recipes = context.services().require(Recipes.class);
        AnvilPlanScreens plans = new AnvilPlanScreens(context.services().require(GuiService.class), context.scheduler(), devices,
            new PlanBook(recipes), recipes::ingredients,
            context.integrations().customItems(), context.integrations().customBlocks(),
            p -> context.messages().get(p, "gui.anvil.title"));
        context.services().provide(AnvilPlanScreens.class, plans);
        context.services().provide(AnvilEffects.class, effects); // PAR-METAL-05: the forging screen redraws the displays
        AnvilDevice.Services services = new AnvilDevice.Services(stack -> tags.is(stack, AnvilDevice.HAMMERS),
            stack -> tags.is(stack, AnvilDevice.FLUXES), effects, clicks, plans);
        devices.register(AnvilDevice.kind(AnvilDevice.METAL_ID, services));
        devices.register(AnvilDevice.kind(AnvilDevice.ROCK_ID, services));
        this.devices = devices;
        this.plans = plans;
        this.blocks = context.integrations().customBlocks();
        this.scheduler = context.scheduler();
        context.listen(new AnvilListener(context.integrations().customBlocks(), devices,
            stack -> tags.is(stack, AnvilDevice.HAMMERS), stack -> tags.is(stack, AnvilListener.CHISELS), context.scheduler(),
            effects, clicks));
    }

    @Override
    public void disable() {
        devices = null;
        plans = null;
        blocks = null;
        scheduler = null;
    }

    // ---- debug --------------------------------------------------------------------------------------------

    /** {@code /tfp debug anvil <name> open|info}: the plan screen of, or the main input and plan of, the anvil looked at. */
    @Override
    public void debugCommands(DebugTree tree) {
        var open = Commands.literal("open").executes(tree.run(ctx -> onPlayer(ctx, this::open)));
        var info = Commands.literal("info").executes(tree.run(ctx -> onPlayer(ctx, this::info)));
        tree.add("anvil", c -> c.then(Commands.argument("name", StringArgumentType.word()).then(open).then(info)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            java.util.function.Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        Scheduler sch = scheduler;
        if (player == null || sch == null || !sch.entity(player, () -> reply.complete(action.apply(player)),
            () -> reply.complete(Reply.error("debug.anvil.no_player")))) {
            reply.complete(Reply.error("debug.anvil.no_player"));
        }
        return reply;
    }

    private Optional<Block> target(Player player) {
        Block b = player.getTargetBlockExact((int) REACH);
        CustomBlocks cb = blocks;
        if (b == null || cb == null || !cb.stateAt(b).map(BlockStateRef::id)
            .filter(id -> id.startsWith(AnvilDevice.METAL_ID + "/") || id.startsWith(AnvilDevice.ROCK_ID + "/")).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(b);
    }

    private Reply open(Player player) {
        Optional<Block> b = target(player);
        AnvilPlanScreens p = plans;
        if (b.isEmpty() || p == null) {
            return Reply.error("debug.anvil.no_anvil");
        }
        return p.openNow(player, b.get()).isPresent() ? Reply.ok("debug.anvil.opened") : Reply.error("debug.anvil.no_plans");
    }

    @SuppressWarnings("unchecked")
    private Reply info(Player player) {
        Optional<Block> b = target(player);
        DeviceService d = devices;
        if (b.isEmpty() || d == null) {
            return Reply.error("debug.anvil.no_anvil");
        }
        String id = blocks.stateAt(b.get()).map(BlockStateRef::id).orElse("");
        Optional<dev.tfp.paper.device.DeviceKind<?>> kind = d.kinds().find(id);
        if (kind.isEmpty()) {
            return Reply.error("debug.anvil.no_anvil");
        }
        Optional<DeviceRecord<AnvilState>> r = d.edit(b.get(), (dev.tfp.paper.device.DeviceKind<AnvilState>) kind.get(),
            (record, now) -> record);
        if (r.isEmpty()) {
            return Reply.error("debug.anvil.no_anvil");
        }
        ItemStack main = d.stacks().decode(r.get().slot(AnvilSlots.INPUT_MAIN));
        if (main == null || main.isEmpty()) {
            return Reply.ok("debug.anvil.empty", r.get().state().lastRecipe());
        }
        ForgingPdc.Forging f = ForgingPdc.read(main);
        return f == null
            ? Reply.ok("debug.anvil.noplan", main.getType().name(), r.get().state().lastRecipe())
            : Reply.ok("debug.anvil.plan", main.getType().name(), f.recipe(), f.target(), f.work(), f.steps().total(),
                r.get().state().lastRecipe());
    }
}
