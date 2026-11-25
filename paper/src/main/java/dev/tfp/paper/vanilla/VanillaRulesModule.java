/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (vanilla rule overrides)
 */
package dev.tfp.paper.vanilla;

import dev.tfp.core.vanilla.VanillaRules;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;

/**
 * PAR-DATA-10: TFC's vanilla rule overrides, each behind its {@link VanillaRules} option: bone meal growth, skeleton trap
 * horses, built golems, natural monster spawns, egg throwing and milk clearing potion effects. Dispenser behaviours
 * (lighting devices, placing fluids, shearing) belong to the features that own those devices and are not here; mobs
 * spawning with vanilla equipment or enchantments are a follow-up (see the ledger).
 */
public final class VanillaRulesModule implements FeatureModule, Listener {
    public static final String ID = "vanilla_rules";

    private volatile Supplier<VanillaRules> rules = () -> VanillaRules.fromProperties(System::getProperty);
    private volatile Predicate<Block> spawnsOn = b -> true;
    private volatile Scheduler scheduler;

    public VanillaRulesModule() {
    }

    /** Tests. */
    public VanillaRulesModule(Supplier<VanillaRules> rules, Predicate<Block> spawnsOn) {
        this.rules = rules;
        this.spawnsOn = spawnsOn;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        scheduler = ctx.scheduler();
        spawnsOn = block -> ctx.services().get(Tags.class).map(t -> t.is(block, "tfc:monster_spawns_on")).orElse(true);
        ctx.listen(this);
    }

    /** TFC {@code onBoneMeal}: bone meal's instant growth is cancelled (fertiliser replaces it). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (!rules.get().bonemeal() && event.getPlayer() != null) {
            event.setCancelled(true);
        }
    }

    /** TFC {@code InteractionManager} egg rule: an egg is not thrown (nothing is consumed). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Egg && !rules.get().eggThrowing()) {
            event.setCancelled(true);
        }
    }

    /** TFC {@code onLivingSpawnCheck} and the trap horse and golem cancels. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        VanillaRules r = rules.get();
        SpawnReason reason = event.getSpawnReason();
        if (event.getEntityType() == EntityType.SKELETON_HORSE && !r.skeletonHorses()) {
            event.setCancelled(true);
        } else if ((event.getEntityType() == EntityType.IRON_GOLEM || event.getEntityType() == EntityType.SNOW_GOLEM)
                && !r.golems()) {
            event.setCancelled(true);
        } else if ((event.getEntity() instanceof Monster || event.getEntity() instanceof Slime)
                && (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN || reason == SpawnReason.REINFORCEMENTS)) {
            Block at = event.getLocation().getBlock();
            World world = at.getWorld();
            boolean slime = event.getEntity() instanceof Slime;
            int light = Math.max(at.getLightFromBlocks(), at.getLightFromSky());
            boolean surface = world.getHighestBlockYAt(at.getLocation(), org.bukkit.HeightMap.WORLD_SURFACE) <= at.getY();
            boolean ground = spawnsOn.test(at.getRelative(0, -1, 0));
            if (r.deniesMonster(slime, light, surface, ground)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * TFC {@code MilkBucketItemMixin}: drinking milk does not clear potion effects. Vanilla clears them after the
     * consume event, so the effects present now are put back on the next tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET || rules.get().milkClearsEffects()) {
            return;
        }
        Player player = event.getPlayer();
        List<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
        if (effects.isEmpty()) {
            return;
        }
        Scheduler s = scheduler;
        Runnable restore = () -> {
            for (PotionEffect e : effects) {
                if (!player.hasPotionEffect(e.getType())) {
                    player.addPotionEffect(e);
                }
            }
        };
        if (s != null) {
            s.entity(player, restore, null);
        } else {
            restore.run();
        }
    }
}
