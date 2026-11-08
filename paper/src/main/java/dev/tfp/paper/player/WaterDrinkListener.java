/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerRightClickBlockLowestPriority,
 * onPlayerRightClickEmpty), src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (attemptDrink, doDrink),
 * src/main/java/net/dries007/tfc/network/PlayerDrinkPacket.java @ c3b4d9536 (swing on success), and
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (salty(): the water of a salty biome is salt water)
 */
package dev.tfp.paper.player;

import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.Drinkables;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.worldwater.WorldWaterService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

/**
 * Right-click on a water source with an empty main hand drinks (M3-SURV-03, PAR-SURV-04). TFC drinks from the fluid
 * the player looks at (the source-only ray, reach of 4.5 blocks); a click that interacts with a block does not drink.
 *
 * <p>The fluid is the vanilla water source ({@code Material.WATER} at level 0); which TFC water it stands for is
 * PAR-FLUID-03's world water ({@link WorldWaterService#kindOf}, PAR-FLUID-07): salt water ({@code tfp:salt_water}) in
 * every {@code salty()} TFC biome of the seed (oceans, shores, tidal flats, salt marshes...), fresh water
 * ({@code minecraft:water}) for river water (TFC's river water is the same drinkable) and lakes, spring water in a hot
 * spring pool (not drinkable in TFC). Without the worldwater module, salt water is water in a vanilla ocean biome.
 * Its drinkable comes from the loaded data ({@link ThirstModule#drinkables}). A drink plays
 * TFC's drink sound, swings the main hand, stops sprinting and applies the drinkable's effects that came up
 * ({@link ThirstEffects#apply}). The work is on the player's thread: the thirst clock is evaluated, then the drink is
 * applied, both through {@link PlayerDataStore#update}.
 *
 * <p>A vanilla client sends nothing for an empty-hand right-click that hits no block, so open water with no block
 * behind it within reach cannot be drunk from (TFC sends its own {@code PlayerDrinkPacket}; see the ledger).
 */
public final class WaterDrinkListener implements Listener {
    /** TFC's hand reach for the source ray (the vanilla survival block reach). */
    static final double REACH = 4.5;

    private final PlayerDataStore data;
    private final LongSupplier playerTicks;
    private final HudSnapshotCache hud;
    private final Consumer<Player> changed;
    private final Supplier<Drinkables> drinkables;
    private final Function<Block, String> waterFluid;

    /**
     * @param playerTicks the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread
     * @param hud          the ambient temperature for the heat share of the decay
     * @param changed      called on the player's thread after a drink changed the thirst (the HUD republishes at once,
     *                     {@code PlayerNeedsHudService.refresh})
     */
    public WaterDrinkListener(PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud,
            Consumer<Player> changed) {
        this(data, playerTicks, hud, changed, ThirstModule::drinkables,
                worldWaterFluid(WaterDrinkListener::pluginWorldWater));
    }

    /**
     * With the drinkable definitions and the water rule given (tests).
     *
     * @param waterFluid the drinkable fluid of a water source, on the player's thread
     */
    WaterDrinkListener(PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud, Consumer<Player> changed,
            Supplier<Drinkables> drinkables, Function<Block, String> waterFluid) {
        this.data = data;
        this.playerTicks = playerTicks;
        this.hud = hud;
        this.changed = changed;
        this.drinkables = drinkables;
        this.waterFluid = waterFluid;
    }

    /** Without a change callback: the HUD picks the drink up at its next throttled publish. */
    public WaterDrinkListener(PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud) {
        this(data, playerTicks, hud, p -> { });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!EquipmentSlot.HAND.equals(event.getHand())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held != null && !held.getType().isAir()) {
            return;
        }
        Player player = event.getPlayer();
        ThreadAsserts.owned(player);
        Block clicked = event.getClickedBlock();
        // TFC: a click that interacts with a block (a chest, an anvil) is not a drink. The client does not target fluids, so
        // a click on water reaches here as a click on the solid block behind it: then the water must be the nearer target.
        if (clicked != null && !clicked.getType().isAir() && !isPlainWaterSource(clicked)) {
            Block source = sourceInView(player);
            if (source == null || !isNearer(player, source, clicked)) {
                return;
            }
        }
        drinkFromView(player);
    }

    /**
     * Drinks from the water source in view, if there is one. Must run on the player's thread (the caller checked the empty
     * main hand).
     */
    public void drinkFromView(Player player) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        if (!data.isLoaded(id)) {
            return;
        }
        Block source = sourceInView(player);
        if (source == null || !isPlainWaterSource(source)) {
            return; // not water: nothing changes, and no decay is written
        }
        Optional<Drinkable> drinkable = drinkables.get().get(fluidOf(source));
        if (drinkable.isEmpty()) {
            return;
        }
        long now = playerTicks.getAsLong();
        float temperature = (float) hud.snapshot(id).ambientTemperature();
        // The draws are taken before the pure update: one float per effect, in order (TFC's player random).
        double[] draws = ThreadLocalRandom.current().doubles(drinkable.get().effects().size()).toArray();
        int[] next = {0};
        WaterDrink.Result[] result = {null};
        data.update(id, d -> {
            next[0] = 0;
            result[0] = WaterDrink.drink(d, now, temperature, drinkable, () -> draws[next[0]++]);
            return result[0].data();
        });
        if (result[0].drunk()) {
            // TFC doDrink: GENERIC_DRINK in PLAYERS at 1/1 (here at the player, whose region this thread owns),
            // the swing of PlayerDrinkPacket, onDrink's setSprinting(false) and effects.
            player.getWorld().playSound(player, Sound.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1f, 1f);
            player.swingMainHand();
            player.setSprinting(false);
            for (Drinkable.Effect effect : result[0].effects()) {
                ThirstEffects.apply(player, effect);
            }
        }
        changed.accept(player);
    }

    /** The drinkable fluid of a water source (the water rule this listener was built with). */
    String fluidOf(Block source) {
        return waterFluid.apply(source);
    }

    /**
     * The water rule over a world water service looked up at each drink (the worldwater module is enabled after this
     * listener is built, and may be off): the service's container fluid of the block, else {@link #biomeWaterFluid}.
     */
    static Function<Block, String> worldWaterFluid(Supplier<Optional<WorldWaterService>> service) {
        return block -> service.get().map(w -> w.kindOf(block).containerFluid())
                .orElseGet(() -> biomeWaterFluid(block));
    }

    /** The plugin's world water service, when the worldwater module provides one. */
    private static Optional<WorldWaterService> pluginWorldWater() {
        try {
            return JavaPlugin.getPlugin(TfpPlugin.class).moduleServices().get(WorldWaterService.class);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException e) {
            return Optional.empty(); // not running as the plugin (tests)
        }
    }

    /** The fallback water rule without the worldwater module: salt water in a vanilla ocean biome, else fresh water. */
    static String biomeWaterFluid(Block source) {
        return isSaltyBiome(source.getBiome()) ? Drinkables.SALT_WATER : Drinkables.WATER;
    }

    /**
     * Whether water in {@code biome} is salt water for the fallback rule: the vanilla ocean biomes, which stand in for
     * TFC's ocean biomes (worldgen {@code BiomeKeys}).
     */
    @SuppressWarnings("deprecation")
    static boolean isSaltyBiome(Biome biome) {
        return biome != null && biome.getKey().getKey().contains("ocean");
    }

    /** Whether {@code source} is closer to the player's eye than {@code other} (block centres). */
    private static boolean isNearer(Player player, Block source, Block other) {
        Location eye = player.getEyeLocation();
        return eye.distanceSquared(source.getLocation().add(0.5, 0.5, 0.5))
                < eye.distanceSquared(other.getLocation().add(0.5, 0.5, 0.5));
    }

    /** The first block the player looks at, counting only fluid sources, within {@link #REACH}; or null. */
    private static Block sourceInView(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), REACH,
                FluidCollisionMode.SOURCE_ONLY, false);
        return hit == null ? null : hit.getHitBlock();
    }

    /** A vanilla water block that is a source (level 0). Waterlogged blocks are another material and do not count. */
    static boolean isPlainWaterSource(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled levelled
                && levelled.getLevel() == 0;
    }
}
