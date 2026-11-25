/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tooltip/BlockEntityTooltips.java @ c3b4d9536 (CROP; the Jade readout, NP-UI-01 substitute)
 */
package dev.tfp.paper.lookinfo;

import dev.tfp.core.crop.CropPlant;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.crop.CropIds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * PAR-DATA-12: while a player sneaks and looks at a crop (and, through {@link #providers}, any later device) within
 * {@value #REACH} blocks, the action bar shows its readout, replacing the Jade/TOP block tooltips of TFC (NP-UI-01).
 * A per-player entity task runs every {@value #PERIOD} ticks (4 per second) only while sneaking; the ray trace is on the
 * player's thread, the record is caught up on the block's region and the bar is sent from there. The same text is not
 * resent before {@value #RESEND_TICKS} ticks. Spectators see nothing (PROTOCOL 4.7). Animals are read by the familiarity module.
 */
public final class LookInfoOverlay implements Listener {
    public static final long PERIOD = 5L;
    static final long RESEND_TICKS = 40L;
    static final int REACH = 5;

    /** Reads one device record into lines; empty when the record is not of the provider's kind. */
    public interface Provider extends Function<DeviceRecord<?>, Optional<List<LookInfoText.Line>>> {
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Messages messages;
    private final java.util.function.LongSupplier ticks;
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();
    private final Map<UUID, Scheduler.TaskHandle> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();

    private record Sent(String text, long tick) {
    }

    public LookInfoOverlay(Scheduler scheduler, CustomBlocks blocks, DeviceService devices, Messages messages,
            java.util.function.LongSupplier ticks) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.devices = devices;
        this.messages = messages;
        this.ticks = ticks;
        register("tfp:crop/", record -> record.state() instanceof CropPlant p ? Optional.of(LookInfoText.crop(p)) : Optional.empty());
    }

    /** Adds a provider for block ids starting with {@code idPrefix}. */
    public void register(String idPrefix, Provider provider) {
        providers.put(idPrefix, provider);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            Scheduler.TaskHandle handle = scheduler.entityRepeating(player, 1L, PERIOD, () -> check(player),
                () -> stop(player.getUniqueId()));
            if (handle != null) {
                Scheduler.TaskHandle old = tasks.put(player.getUniqueId(), handle);
                if (old != null) {
                    old.cancel();
                }
            }
        } else {
            stop(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    private void stop(UUID id) {
        Scheduler.TaskHandle handle = tasks.remove(id);
        if (handle != null) {
            handle.cancel();
        }
        sent.remove(id);
    }

    /** One look, on the player's thread. */
    void check(Player player) {
        if (!player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block target = player.getTargetBlockExact(REACH);
        if (target != null) {
            scheduler.region(target.getLocation(), () -> read(player, target));
        }
    }

    /** Region thread: the block's device, caught up, then the lines. */
    void read(Player player, Block target) {
        Optional<BlockStateRef> state = blocks.stateAt(target);
        if (state.isEmpty()) {
            return;
        }
        String id = state.get().id();
        Provider provider = providers.entrySet().stream().filter(e -> id.startsWith(e.getKey())).map(Map.Entry::getValue)
            .findFirst().orElse(null);
        if (provider == null) {
            return;
        }
        Block owner = CropIds.type(id).isPresent() && CropIds.isTop(state.get()) ? target.getRelative(BlockFace.DOWN) : target;
        Optional<List<LookInfoText.Line>> lines = devices.catchUp(owner).flatMap(provider);
        lines.ifPresent(l -> send(player, l));
    }

    /** Formats and sends, skipping an identical bar sent less than {@value #RESEND_TICKS} ticks ago. */
    void send(Player player, List<LookInfoText.Line> lines) {
        List<String> parts = new ArrayList<>();
        for (LookInfoText.Line line : lines) {
            parts.add(messages.get(player, line.key(), line.args().toArray()));
        }
        String text = String.join("  ", parts);
        long now = ticks.getAsLong();
        Sent last = sent.get(player.getUniqueId());
        if (last != null && last.text().equals(text) && now - last.tick() < RESEND_TICKS) {
            return;
        }
        sent.put(player.getUniqueId(), new Sent(text, now));
        Component bar = LegacyComponentSerializer.legacySection().deserialize(text);
        player.sendActionBar(bar);
    }
}
