/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerRightClickEmpty)
 */
package dev.tfp.paper.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import dev.tfp.paper.packet.PacketContext;
import dev.tfp.paper.packet.PacketDecision;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A right-click with an empty main hand on open water. Bukkit fires no interaction event for an empty-hand click on air
 * (SPIGOT-410), and the client does not target fluids, so the drink is triggered from the serverbound use-item packet (TFC's
 * {@code onPlayerRightClickEmpty}). The packet only says the click happened: the drink itself runs on the player's thread,
 * where the held item and the water in view are checked again.
 */
public final class EmptyHandDrinkHandler implements PacketHandler {
    public static final String NAME = "water-drink-empty-hand";

    private final WaterDrinkListener drink;
    private final Scheduler scheduler;

    /**
     * @param drink     the drink rule and the water-in-view check, shared with the interaction listener
     * @param scheduler runs the drink on the player's thread
     */
    public EmptyHandDrinkHandler(WaterDrinkListener drink, Scheduler scheduler) {
        this.drink = drink;
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> receiveTypes() {
        return Set.of("USE_ITEM");
    }

    /** A use-item packet of the main hand. Netty thread: it only schedules the drink on the player's thread. */
    @Override
    public PacketDecision onReceive(PacketContext packet) {
        UUID id = packet.playerId();
        if (id == null) {
            return PacketDecision.PASS;
        }
        WrapperPlayClientUseItem use = new WrapperPlayClientUseItem(packet.nativeEvent(PacketReceiveEvent.class));
        if (use.getHand() != InteractionHand.MAIN_HAND) {
            return PacketDecision.PASS;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return PacketDecision.PASS;
        }
        scheduler.entity(player, () -> {
            if (player.getInventory().getItemInMainHand().getType().isAir()) {
                drink.drinkFromView(player);
            }
        }, () -> { });
        return PacketDecision.PASS;
    }
}
