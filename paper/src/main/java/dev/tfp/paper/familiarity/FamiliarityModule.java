/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/IngameOverlays.java (renderFamiliarity) @ c3b4d9536
 */
package dev.tfp.paper.familiarity;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.familiarity.FamiliarityReadout;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.RayTraceResult;

/**
 * Familiarity readout (PAR-FAUNA-22, NP-UI-01 substitute for TFC's crosshair overlay): while a player sneaks and looks at a
 * TFC animal within 5 blocks, the action bar shows a heart in TFC's colour band and the familiarity percent, and the pregnant
 * line of a fertilized mammal. A per-player entity-scheduler task runs every 5 ticks while sneaking; the message is re-sent
 * only when its text changes or every 40 ticks, so the bar does not flicker or spam.
 */
public final class FamiliarityModule implements FeatureModule, Listener {
    static final double RANGE = 5.0;
    static final long PERIOD = 5L;
    static final long RESEND_TICKS = 40L;
    private ModuleContext ctx;
    private final Map<UUID, Scheduler.TaskHandle> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();

    private record Sent(String text, long tick) {
    }

    @Override
    public String id() {
        return "familiarity";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        context.listen(this);
    }

    @Override
    public void disable() {
        tasks.values().forEach(Scheduler.TaskHandle::cancel);
        tasks.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (event.isSneaking()) {
            Scheduler.TaskHandle h = ctx.scheduler().entityRepeating(p, 1L, PERIOD, () -> show(p), () -> stop(p.getUniqueId()));
            if (h != null) {
                Scheduler.TaskHandle old = tasks.put(p.getUniqueId(), h);
                if (old != null) {
                    old.cancel();
                }
            }
        } else {
            stop(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    private void stop(UUID id) {
        Scheduler.TaskHandle h = tasks.remove(id);
        if (h != null) {
            h.cancel();
        }
        sent.remove(id);
    }

    /** Player thread: reads the looked-at animal and sends the action bar when due. */
    void show(Player p) {
        if (!p.isSneaking() || p.isDead()) {
            stop(p.getUniqueId());
            return;
        }
        AnimalService animals = ((TfpPlugin) ctx.plugin()).animals();
        ClockSnapshot now = ctx.clock().get();
        RayTraceResult hit = p.rayTraceEntities((int) RANGE);
        if (animals == null || hit == null || hit.getHitEntity() == null || !now.isSet()) {
            sent.remove(p.getUniqueId());
            return;
        }
        Entity e = hit.getHitEntity();
        Optional<AnimalRecord> record = animals.record(e);
        Optional<LivestockSpecies> species = record.flatMap(r -> animals.table().find(r.species())).flatMap(Carrier::livestock);
        if (species.isEmpty()) {
            sent.remove(p.getUniqueId());
            return;
        }
        Optional<FamiliarityReadout> readout = FamiliarityReadout.of(record.get().state(), species.get(), now.playerTicks());
        if (readout.isEmpty()) {
            return;
        }
        String locale = p.locale().toString().toLowerCase(Locale.ROOT);
        Component text = format(readout.get(), e, locale);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text)
                + readout.get().band();
        Sent last = sent.get(p.getUniqueId());
        long tick = e.getTicksLived();
        if (last == null || !last.text().equals(plain) || tick - last.tick() >= RESEND_TICKS) {
            p.sendActionBar(text);
            sent.put(p.getUniqueId(), new Sent(plain, tick));
        }
    }

    Component format(FamiliarityReadout r, Entity e, String locale) {
        TextColor color = switch (r.band()) {
            case RED -> NamedTextColor.RED;
            case WHITE -> NamedTextColor.WHITE;
            case GRAY -> NamedTextColor.GRAY;
        };
        Component line = Component.text("❤ " + r.percentText() + "%", color);
        if (r.pregnant()) {
            String name = e.getType().getKey().getKey();
            line = line.append(Component.text("  " + ctx.messages().get(locale, "familiarity.pregnant", name), NamedTextColor.WHITE));
        }
        return line;
    }
}
