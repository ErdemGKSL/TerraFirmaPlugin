/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/HoeOverlays.java @ c3b4d9536 (the overlay while holding a hoe),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/{FruitTreeBranchBlock,FruitTreeSaplingBlock,BananaPlantBlock}.java @ c3b4d9536
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.banana.BananaRules;
import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.banana.BananaDevice;
import dev.tfp.paper.banana.BananaEffects;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FARM-28: holding a hoe and looking at a fruit tree block or a banana plant shows the climate the plant needs on the action bar, as TFC's
 * {@code addHoeOverlayInfo} does ({@link SplicLines}). The same per-player task as the farmland overlay ({@code farm.HoeOverlay}), on its own
 * targets: farmland is that overlay's, these blocks are ours, so the two never write the same bar. The region of the block builds the
 * lines (the climate reads the live blocks), the bar is sent from a worker.
 */
public final class SplicOverlay implements Listener {
    public static final long PERIOD = 10L;
    static final int REACH = 5;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final FruitTreeEffects treeFx;
    private final BananaEffects bananaFx;
    private final Messages messages;
    private final Supplier<ClockSnapshot> clock;
    private final Predicate<ItemStack> hoes;
    private final Map<UUID, Scheduler.TaskHandle> tasks = new ConcurrentHashMap<>();

    public SplicOverlay(Scheduler scheduler, CustomBlocks blocks, DeviceService devices, FruitTreeEffects treeFx, BananaEffects bananaFx,
                        Messages messages, Supplier<ClockSnapshot> clock, Predicate<ItemStack> hoes) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.devices = devices;
        this.treeFx = treeFx;
        this.bananaFx = bananaFx;
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
        if (!hoes.test(player.getInventory().getItemInMainHand()) && !hoes.test(player.getInventory().getItemInOffHand())
            || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block target = player.getTargetBlockExact(REACH);
        if (target != null) {
            scheduler.region(target.getLocation(), () -> show(player, target));
        }
    }

    /** Region thread: the lines of the targeted block, if it is ours. */
    void show(Player player, Block target) {
        Optional<BlockStateRef> state = blocks.stateAt(target);
        if (state.isEmpty()) {
            return;
        }
        List<String> lines = lines(target, state.get(), (key, args) -> messages.get(player, key, args));
        if (!lines.isEmpty()) {
            Component bar = LegacyComponentSerializer.legacySection().deserialize(String.join("  ", lines));
            scheduler.cpu(() -> player.sendActionBar(bar));
        }
    }

    /** The overlay lines of a block (empty for a block that is not a fruit tree block or a banana plant). Region thread. */
    public List<String> lines(Block target, BlockStateRef ref, java.util.function.BiFunction<String, Object[], String> text) {
        Optional<FruitTreeDevice.Parsed> tree = FruitTreeDevice.parse(ref);
        if (tree.isPresent()) {
            return treeLines(target, tree.get(), text);
        }
        Optional<BananaDevice.Parsed> banana = BananaDevice.parse(ref).filter(p -> p.part() == BananaDevice.Part.PLANT);
        if (banana.isPresent()) {
            Optional<FruitTreeState> stored = stored(target);
            if (stored.isEmpty()) {
                return List.of();
            }
            Block stem = target.getWorld().getBlockAt(stored.get().stemX(), stored.get().stemY(), stored.get().stemZ());
            BerryEffects.Environment env = bananaFx.environment(stem, stem.getRelative(0, -1, 0));
            return SplicLines.banana(text, BananaRules.CLIMATE, env.hydration(), env.temperature(), ClimateTemperatureStyle.DEFAULT);
        }
        return List.of();
    }

    private List<String> treeLines(Block target, FruitTreeDevice.Parsed p, java.util.function.BiFunction<String, Object[], String> text) {
        boolean hasRecord = p.part() != FruitTreeDevice.Part.BRANCH;
        Optional<FruitTreeState> stored = hasRecord ? stored(target) : Optional.empty();
        // TFC's growing branch and leaves read the block entity; a static branch has none and says where to look
        if (hasRecord && stored.isEmpty()) {
            return List.of();
        }
        boolean splice = switch (p.part()) {
            case SAPLING -> SplicAction.maySplice(treeFx, target);
            case GROWING, BRANCH -> SplicAction.maySplice(treeFx, target.getRelative(0, 1, 0));
            case LEAVES -> false;
        };
        int hydration = 0;
        float celsius = 0f;
        boolean active = true;
        if (stored.isPresent()) {
            Block stem = target.getWorld().getBlockAt(stored.get().stemX(), stored.get().stemY(), stored.get().stemZ());
            BerryEffects.Environment env = treeFx.environment(stem);
            hydration = env.hydration();
            celsius = env.temperature();
            ClockSnapshot snapshot = clock.get();
            Month month = new BerryClock(snapshot.playerTicks(), snapshot.calendarTicks(), snapshot.daysInMonth(), env.northern())
                .hemisphericMonth(snapshot.calendarTicks());
            active = p.tree().lifecycleFor(month).active();
        }
        return SplicLines.tree(text, p.part(), p.tree().climate(), hydration, celsius, ClimateTemperatureStyle.DEFAULT, splice, active);
    }

    private Optional<FruitTreeState> stored(Block target) {
        return devices.catchUp(target).map(r -> r.state()).filter(s -> s instanceof FruitTreeState).map(s -> (FruitTreeState) s);
    }
}
