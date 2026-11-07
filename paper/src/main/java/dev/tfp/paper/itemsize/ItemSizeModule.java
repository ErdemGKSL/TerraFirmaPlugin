/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerTick: overburdening every 20 ticks)
 */
package dev.tfp.paper.itemsize;

import dev.tfp.core.item.Burden;
import dev.tfp.core.item.BurdenEffects;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PAR-SURV-06: item size and weight on Paper. Provides {@link ItemSizes} (the shared container predicate), enforces
 * TFC's chest and bundle limits ({@link ContainerSizeListener}), adds the size/weight tooltip line
 * ({@link ItemSizeTooltipContributor}) and applies exhausted / overburdened ({@link BurdenApplier}) with one global
 * throttle every {@value BurdenEffects#CHECK_PERIOD_TICKS} ticks that sends a task to each online player's thread
 * (TFC {@code level.getGameTime() % 20 == 0}). Listed after the {@code tags} and {@code tooltip} modules.
 *
 * <p>TFC's {@code enableOverburdening}, {@code chestMaximumItemSize} and {@code passiveExhaustionModifier} are at
 * their defaults (on, large, 1.0); they are not in our config yet.
 */
public final class ItemSizeModule implements FeatureModule, Listener {
    public static final String ID = "item_size";

    private volatile BurdenApplier burden;
    private volatile Scheduler.TaskHandle throttle;
    private volatile ModuleContext context;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        context = ctx;
        Tags tags = ctx.services().require(Tags.class);
        ItemSizes sizes = new ItemSizes(new ItemSizeDefinitionsCache(() -> ctx.registryService().registries()),
                ItemSizes.lookups(tags::snapshot), ctx.integrations().customItems()::idOf,
                ctx.integrations().customBlocks()::exists, ItemSizeLimits.DEFAULT);
        ctx.services().provide(ItemSizes.class, sizes);
        ctx.listen(new ContainerSizeListener(sizes));
        ctx.services().require(TooltipPipeline.class).add(new ItemSizeTooltipContributor(sizes, ctx.messages()::get));
        Messages messages = ctx.messages();
        BurdenApplier applier = new BurdenApplier(sizes, BurdenEffects.DEFAULT_PASSIVE_EXHAUSTION_MODIFIER,
                (player, b) -> player.sendActionBar(Component.text(messages.get(player.locale(),
                        b == Burden.MORE_THAN_ONE ? BurdenApplier.OVERBURDENED_KEY : BurdenApplier.EXHAUSTED_KEY),
                        NamedTextColor.RED)));
        burden = applier;
        ctx.listen(this);
        Scheduler scheduler = ctx.scheduler();
        throttle = scheduler.globalRepeating(BurdenEffects.CHECK_PERIOD_TICKS, BurdenEffects.CHECK_PERIOD_TICKS, () -> {
            for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
                scheduler.entity(player, () -> {
                    if (player.isOnline()) {
                        applier.apply(player);
                    }
                }, null);
            }
        });
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = throttle;
        throttle = null;
        if (t != null) {
            t.cancel();
        }
        BurdenApplier b = burden;
        ModuleContext ctx = context;
        if (b != null && ctx != null) {
            for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
                ctx.scheduler().entity(player, () -> b.clear(player), null);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BurdenApplier b = burden;
        if (b != null) {
            b.clear(event.getPlayer());
        }
    }
}
