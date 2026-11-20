/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/HoeOverlays.java @ c3b4d9536 (the overlay while holding a hoe)
 */
package dev.tfp.paper.farm;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.farm.FarmlandRules;
import dev.tfp.core.farm.FarmlandState;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FARM-01: holding a hoe and looking at farmland shows its hydration (now, and at the driest and wettest rain of the year)
 * and nutrients on the action bar, replacing TFC's client {@code HoeOverlays}. A light per-player task every
 * {@value #PERIOD} ticks on the player's thread checks the held item and the block in view; only a hoe and a farmland block
 * go on: the block's region captures a {@link HydrationSnapshot} and the record, the numbers are computed off-thread
 * ({@link FarmlandReadout#compute}) and the bar is sent from there.
 */
public final class HoeOverlay implements Listener {
    public static final long PERIOD = 10L;
    static final double REACH = 5.0;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final Tags tags;
    private final Messages messages;
    private final Supplier<ClockSnapshot> clock;
    private final Predicate<ItemStack> hoes;
    private final Map<UUID, Scheduler.TaskHandle> tasks = new ConcurrentHashMap<>();

    public HoeOverlay(Scheduler scheduler, CustomBlocks blocks, DeviceService devices, Tags tags, Messages messages,
            Supplier<ClockSnapshot> clock, Predicate<ItemStack> hoes) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.devices = devices;
        this.tags = tags;
        this.messages = messages;
        this.clock = clock;
        this.hoes = hoes;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.put(player.getUniqueId(), scheduler.entityRepeating(player, PERIOD, PERIOD, () -> check(player),
            () -> tasks.remove(player.getUniqueId())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Scheduler.TaskHandle handle = tasks.remove(event.getPlayer().getUniqueId());
        if (handle != null) {
            handle.cancel();
        }
    }

    /** One look, on the player's thread. */
    void check(Player player) {
        if (!hoes.test(player.getInventory().getItemInMainHand()) && !hoes.test(player.getInventory().getItemInOffHand())) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        Block target = player.getTargetBlockExact((int) REACH);
        if (target == null) {
            return;
        }
        scheduler.region(target.getLocation(), () -> show(player, target));
    }

    /** Region thread: the record and the water snapshot, then the numbers off-thread. */
    void show(Player player, Block target) {
        Optional<BlockStateRef> state = blocks.stateAt(target);
        if (state.isEmpty() || !FarmlandDevice.kindCovers(state.get().id())) {
            return;
        }
        Optional<FarmlandState> farmland = devices.catchUp(target).map(r -> r.state()).filter(s -> s instanceof FarmlandState)
            .map(s -> (FarmlandState) s);
        if (farmland.isEmpty()) {
            return;
        }
        HydrationSnapshot water = HydrationSnapshot.capture(target, HoeOverlay::isWater, this::soilOf);
        ClockSnapshot snapshot = clock.get();
        long seed = target.getWorld().getSeed();
        var climate = ChunkClimateSource.climateAt(seed, target.getX(), target.getZ());
        scheduler.cpu(() -> player.sendActionBar(text(player,
            FarmlandReadout.compute(farmland.get(), water, climate, snapshot, seed))));
    }

    private Component text(Player player, FarmlandReadout.Lines l) {
        String hydration = messages.get(player, "farm.hydration", l.hydration().now(), l.hydration().min(), l.hydration().max());
        String nutrients = messages.get(player, "farm.nutrients", l.nitrogenPercent(), l.phosphorusPercent(), l.potassiumPercent());
        return LegacyComponentSerializer.legacySection().deserialize(hydration + "  " + nutrients);
    }

    static boolean isWater(Block block) {
        return block.getType() == org.bukkit.Material.WATER
            || block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    private FarmlandRules.SoilHydration soilOf(Block soil) {
        if (tags.is(soil, "tfc:increases_soil_hydration")) {
            return FarmlandRules.SoilHydration.INCREASES;
        }
        return tags.is(soil, "tfc:decreases_soil_hydration") ? FarmlandRules.SoilHydration.DECREASES
            : FarmlandRules.SoilHydration.NORMAL;
    }
}
