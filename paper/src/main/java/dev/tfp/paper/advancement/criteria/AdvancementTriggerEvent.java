/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: util/advancements/TFCAdvancements.java @ c3b4d9536 (the generic, block and entity action triggers)
 */
package dev.tfp.paper.advancement.criteria;

import dev.tfp.core.advancement.CriterionRef;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * PAR-DATA-23: the one small event the owning features publish when a player does what one of TFC's own advancement
 * triggers watches ({@code TFCAdvancements.X.trigger(player)}): a firepit built, a forge lit, a perfect forging, a rock
 * anvil made, a full windmill, a fed animal, a full powder keg, a lava lamp. A feature publishes it after its effect, on
 * the thread that owns the player; {@link CriteriaListener} awards the criterion at MONITOR through
 * {@code Advancements.fire}. The feature knows the condition (TFC's {@code ingredient} block), so it names the criterion.
 *
 * <p>Threads: published and handled on the thread that owns the player (the region thread of the click).
 */
public final class AdvancementTriggerEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final CriterionRef criterion;

    public AdvancementTriggerEvent(Player player, CriterionRef criterion) {
        super(Objects.requireNonNull(player, "player"));
        this.criterion = Objects.requireNonNull(criterion, "criterion");
    }

    /** The criterion the feature says the player earned (a {@code CriteriaMap.Kind.EVENT} one). */
    public CriterionRef criterion() {
        return criterion;
    }

    /**
     * Publishes the trigger for {@code player}. A {@code null} player (a start without one: a dispenser, an arrow) and a
     * server that is not running (plain unit tests) are no-ops, as TFC only triggers for a {@code ServerPlayer}.
     */
    public static void publish(Player player, CriterionRef criterion) {
        if (player == null || Bukkit.getServer() == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new AdvancementTriggerEvent(player, criterion));
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
