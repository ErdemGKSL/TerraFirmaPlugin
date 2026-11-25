/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (vanilla rule overrides)
 */
package dev.tfp.paper.vanilla;

import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.vanilla.VanillaRules;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.tags.TagSnapshot;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Egg;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
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
 * (placing fluids, shearing, handstones, chests) belong to the features that own those devices and are not here.
 *
 * <p>PAR-DATA-18 adds: mobs spawning with TFC armour and no vanilla held items or enchantments (TFC {@code MobMixin},
 * {@code onEntityJoinLevel}), skeletons spawning with a random {@code #tfc:skeleton_weapons} item, chicken jockies, and the
 * dispenser rules (bone meal, eggs dropped instead of thrown, flint and steel lighting a TFC device).
 */
public final class VanillaRulesModule implements FeatureModule, Listener {
    public static final String ID = "vanilla_rules";

    private volatile Supplier<VanillaRules> rules = () -> VanillaRules.fromProperties(System::getProperty);
    private volatile Predicate<Block> spawnsOn = b -> true;
    private volatile Scheduler scheduler;
    private volatile Gear gear = Gear.NONE;
    private volatile DispenserFire dispenserFire = (target, face, stack, done) -> done.accept(false);
    private volatile Predicate<Block> customBlock = b -> false;

    /** What the equipment rules need from the tag and item registries. */
    public record Gear(Function<String, List<String>> members, Function<String, Optional<ItemStack>> create,
            BiPredicate<ItemStack, String> is, Predicate<ItemStack> custom) {
        /** No registries: nothing is replaced, only cleared. */
        public static final Gear NONE = new Gear(t -> List.of(), id -> Optional.empty(), (i, t) -> false, i -> false);
    }

    /** Lights {@code target} from a dispenser (TFC {@code startFire}, strong, no player); {@code done} gets whether it lit. */
    @FunctionalInterface
    public interface DispenserFire {
        void light(Block target, BlockFace face, ItemStack stack, java.util.function.Consumer<Boolean> done);
    }

    public VanillaRulesModule() {
    }

    /** Tests. */
    public VanillaRulesModule(Supplier<VanillaRules> rules, Predicate<Block> spawnsOn) {
        this.rules = rules;
        this.spawnsOn = spawnsOn;
    }

    /** Tests. */
    public VanillaRulesModule(Supplier<VanillaRules> rules, Predicate<Block> spawnsOn, Gear gear, DispenserFire fire,
            Predicate<Block> customBlock, Scheduler scheduler) {
        this(rules, spawnsOn);
        this.gear = gear;
        this.dispenserFire = fire;
        this.customBlock = customBlock;
        this.scheduler = scheduler;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        scheduler = ctx.scheduler();
        spawnsOn = block -> ctx.services().get(Tags.class).map(t -> t.is(block, "tfc:monster_spawns_on")).orElse(true);
        Tags tags = ctx.services().get(Tags.class).orElse(null);
        CustomItems items = ctx.integrations().customItems();
        if (tags != null) {
            gear = new Gear(tag -> tags.snapshot().registry().members(TagType.ITEM, TagSnapshot.normalise(tag)).stream()
                    .sorted().toList(), id -> stack(items, id), tags::is, stack -> items.idOf(stack).isPresent());
        }
        CustomBlocks blocks = ctx.integrations().customBlocks();
        customBlock = block -> blocks.stateAt(block).isPresent();
        ctx.services().get(FireStarting.class).ifPresent(fire -> dispenserFire = (target, face, stack, done) ->
                fire.start(target, face, null, stack, FireStarting.Starter.fixed(FireStart.Strength.STRONG, 1),
                        result -> done.accept(result.started())));
        ctx.listen(this);
    }

    /** A stack of {@code id}: our custom item, else the vanilla material. */
    static Optional<ItemStack> stack(CustomItems items, String id) {
        if (items.exists(id)) {
            return items.create(id, 1);
        }
        NamespacedKey key = NamespacedKey.fromString(id);
        Material m = key == null ? null : Registry.MATERIAL.get(key);
        return m == null || m.isAir() ? Optional.empty() : Optional.of(new ItemStack(m));
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
        if (event.getEntity() instanceof Chicken chicken && !r.chickenJockies() && chicken.isChickenJockey()) {
            event.setCancelled(true);
            return;
        }
        if (reason != SpawnReason.CUSTOM && event.getEntity() instanceof LivingEntity living) {
            equip(living, r);
        }
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
     * TFC {@code MobMixin} (armour from the TFC mob tags instead of vanilla's, no spawn enchantments), {@code
     * onEntityJoinLevel} (vanilla held items cleared) and the skeleton weapon: applied to what the mob spawned with.
     */
    void equip(LivingEntity mob, VanillaRules r) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        Gear g = gear;
        if (!r.mobEquipment()) {
            if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
                for (VanillaRules.ArmorSlot slot : VanillaRules.ArmorSlot.values()) {
                    EquipmentSlot es = EquipmentSlot.valueOf(slot.name());
                    ItemStack worn = eq.getItem(es);
                    if (worn.getType().isAir() || g.custom().test(worn)) {
                        continue;
                    }
                    eq.setItem(es, VanillaRules.randomMember(g.members().apply(slot.tag()),
                            n -> ThreadLocalRandom.current().nextInt(n)).flatMap(g.create()).orElse(null), true);
                }
            }
            if (mob instanceof Monster) {
                for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                    if (g.is().test(eq.getItem(hand), VanillaRules.DISABLED_HELD_ITEMS)) {
                        eq.setItem(hand, null, true);
                    }
                }
            }
        }
        if (mob.getType() == EntityType.SKELETON) {
            eq.setItem(EquipmentSlot.HAND, VanillaRules.randomMember(g.members().apply(VanillaRules.SKELETON_WEAPONS),
                    n -> ThreadLocalRandom.current().nextInt(n)).flatMap(g.create()).orElseGet(() -> new ItemStack(Material.BOW)),
                    true);
        }
        if (!r.mobEnchantments()) {
            for (EquipmentSlot es : EquipmentSlot.values()) {
                ItemStack item = eq.getItem(es);
                if (!item.getType().isAir() && !item.getEnchantments().isEmpty()) {
                    ItemStack plain = item.clone();
                    plain.getEnchantments().keySet().forEach(plain::removeEnchantment);
                    eq.setItem(es, plain, true);
                }
            }
        }
    }

    /**
     * Dispenser rules: bone meal growth is refused with the option off (TFC {@code onBoneMeal} also covers dispensers; the
     * stack stays); an egg is dropped, not thrown (TFC registers the default behaviour for it); flint and steel first tries
     * TFC's strong fire start on a TFC block in front ({@code dispenserEnableLighting}), else vanilla's behaviour.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.DISPENSER || !(block.getBlockData() instanceof Directional d)) {
            return;
        }
        VanillaRules r = rules.get();
        ItemStack item = event.getItem();
        BlockFace facing = d.getFacing();
        switch (item.getType()) {
            case BONE_MEAL -> {
                if (!r.bonemeal()) {
                    event.setCancelled(true);
                }
            }
            case EGG -> {
                if (!r.eggThrowing()) {
                    event.setCancelled(true);
                    ItemStack one = item.clone();
                    one.setAmount(1);
                    runRegion(block, () -> {
                        if (take(block, one)) {
                            block.getWorld().dropItem(block.getRelative(facing).getLocation().add(0.5, 0.25, 0.5), one,
                                    drop -> drop.setVelocity(facing.getDirection().multiply(0.2)));
                        }
                    });
                }
            }
            case FLINT_AND_STEEL -> {
                Block target = block.getRelative(facing);
                if (r.dispenserLighting() && customBlock.test(target)) {
                    event.setCancelled(true);
                    ItemStack tool = item.clone();
                    tool.setAmount(1);
                    dispenserFire.light(target, facing.getOppositeFace(), tool, lit -> {
                        if (lit) {
                            runRegion(block, () -> damage(block, tool));
                        }
                    });
                }
            }
            default -> {
            }
        }
    }

    private void runRegion(Block block, Runnable task) {
        Scheduler s = scheduler;
        if (s != null) {
            s.region(block.getLocation(), task);
        } else {
            task.run();
        }
    }

    private static boolean take(Block dispenser, ItemStack item) {
        if (!(dispenser.getState(false) instanceof Dispenser state)) {
            return false;
        }
        var inv = state.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.isSimilar(item)) {
                if (stack.getAmount() <= 1) {
                    inv.setItem(i, null);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    inv.setItem(i, stack);
                }
                return true;
            }
        }
        return false;
    }

    /** Vanilla's flint and steel use: one durability, the tool breaks at 0. */
    private static void damage(Block dispenser, ItemStack tool) {
        if (!(dispenser.getState(false) instanceof Dispenser state)) {
            return;
        }
        var inv = state.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.isSimilar(tool) && stack.getItemMeta() instanceof Damageable meta) {
                int next = meta.getDamage() + 1;
                if (next >= stack.getType().getMaxDurability()) {
                    inv.setItem(i, null);
                } else {
                    meta.setDamage(next);
                    stack.setItemMeta(meta);
                    inv.setItem(i, stack);
                }
                return;
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
