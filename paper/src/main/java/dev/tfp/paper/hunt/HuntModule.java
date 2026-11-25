/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java @ c3b4d9536 (the loot context it reads),
 * src/main/java/net/dries007/tfc/util/loot/NotPredatedCondition.java and
 * src/generated/resources/data/tfc/loot_table/entities/*.json, same commit
 */
package dev.tfp.paper.hunt;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.hunt.HuntDrops;
import dev.tfp.core.hunt.HuntKill;
import dev.tfp.core.hunt.HuntYield;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.loot.LootDrop;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FAUNA-17: the death drops of TFC animals from {@link HuntDrops}, TFC's {@code loot_table/entities} tables with
 * {@code tfc:animal_yield} (genetic size, the killing player's attack damage, familiarity above 0.5), {@code tfc:not_predated},
 * {@code tfc:is_male}, the player-kill conditions and the random chances.
 *
 * <p>Decide async, apply sync: {@link #onDeath} runs on the animal's region thread. It replaces the vanilla carrier's loot
 * (equipment and inventory contents stay), snapshots the kill into an immutable {@link HuntKill}, and returns. The table is
 * rolled on the CPU pool (pure core code); the stacks are created and dropped at the death position on that position's region
 * thread. Babies drop nothing and the {@code doMobLoot} game rule is honoured, as vanilla's {@code dropAllDeathLoot} does for
 * TFC. Experience is left to vanilla.
 */
public final class HuntModule implements FeatureModule, Listener {
    static final String LAND_PREDATORS = "tfp:land_predators";
    static final String OCEAN_PREDATORS = "tfp:ocean_predators";
    static final Key COUNTS_AS_PLAYER_KILL = Key.key("tfp", "counts_as_player_kill");

    private Function<Entity, Optional<AnimalRecord>> records = e -> AnimalPdc.read(e.getPersistentDataContainer());
    private BiPredicate<String, String> entityInTag = (id, tag) -> false;
    private Predicate<DamageSource> countsAsPlayerKill = HuntModule::damageTypeCountsAsPlayerKill;
    private Supplier<ClockSnapshot> clock = () -> ClockSnapshot.UNSET;
    private Executor cpu = Runnable::run;
    private Supplier<DoubleSupplier> random = () -> ThreadLocalRandom.current()::nextDouble;
    private BiConsumer<Location, List<HuntYield>> apply = (at, yields) -> { };
    private Logger logger = Logger.getLogger("tfp-hunt");

    @Override
    public String id() {
        return "hunt";
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        this.entityInTag = (id, tag) -> tags.is(TagType.ENTITY_TYPE, id, tag);
        this.clock = context.clock()::get;
        this.cpu = context.scheduler().cpuExecutor();
        this.logger = context.logger();
        this.apply = (at, yields) -> context.scheduler().region(at, () -> {
            LootService loot = context.services().require(LootService.class);
            for (ItemStack stack : loot.stacks(lootDrops(yields), null)) {
                at.getWorld().dropItemNaturally(at, stack);
            }
        });
        context.listen(this);
    }

    /** Test seam: the tags, the damage-tag check, the clock, the executor, the draws and the region-thread drop. */
    void initForTest(BiPredicate<String, String> entityInTag, Predicate<DamageSource> countsAsPlayerKill,
            Supplier<ClockSnapshot> clock, Executor cpu, Supplier<DoubleSupplier> random,
            BiConsumer<Location, List<HuntYield>> apply) {
        this.entityInTag = entityInTag;
        this.countsAsPlayerKill = countsAsPlayerKill;
        this.clock = clock;
        this.cpu = cpu;
        this.random = random;
        this.apply = apply;
    }

    /** The species key of {@link HuntDrops} for a record species ({@code tfc:deer} → {@code deer}), if it has a table. */
    static Optional<String> species(AnimalRecord record) {
        String s = record.species();
        String path = s.substring(s.indexOf(':') + 1);
        return HuntDrops.species().contains(path) ? Optional.of(path) : Optional.empty();
    }

    /** Our item ids of the yields: TFC's {@code tfc:} items are {@code tfp:} CraftEngine items, vanilla ids stay. */
    static List<LootDrop> lootDrops(List<HuntYield> yields) {
        List<LootDrop> out = new ArrayList<>(yields.size());
        for (HuntYield y : yields) {
            String id = y.item().startsWith("tfc:") ? "tfp:" + y.item().substring("tfc:".length()) : y.item();
            out.add(LootDrop.of(id, y.count()));
        }
        return out;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity animal = event.getEntity();
        if (animal instanceof Player) {
            return;
        }
        Optional<AnimalRecord> record = records.apply(animal);
        Optional<String> species = record.flatMap(HuntModule::species);
        if (species.isEmpty()) {
            return;
        }
        keepOnlyCarried(event.getDrops(), animal);
        if ((animal instanceof Ageable ageable && !ageable.isAdult())
                || Boolean.FALSE.equals(animal.getWorld().getGameRuleValue(GameRule.DO_MOB_LOOT))) {
            return;
        }
        HuntKill kill = snapshot(animal, record.get(), event.getDamageSource());
        String table = species.get();
        Location at = animal.getLocation();
        DoubleSupplier draws = random.get();
        CompletableFuture.supplyAsync(() -> HuntDrops.yields(table, kill, draws), cpu).whenComplete((yields, err) -> {
            if (err != null) {
                logger.warning("Cannot roll the hunt drops of " + table + ": " + err);
            } else if (!yields.isEmpty()) {
                apply.accept(at, yields);
            }
        });
    }

    /** The kill as the loot rules read it. Region thread of the animal. */
    HuntKill snapshot(LivingEntity animal, AnimalRecord record, DamageSource source) {
        Player player = animal.getKiller();
        float attack = 0f;
        if (player != null) {
            AttributeInstance a = player.getAttribute(Attribute.ATTACK_DAMAGE);
            attack = a == null ? 0f : (float) a.getValue();
        }
        ClockSnapshot now = clock.get();
        float familiarity = now.isSet() ? LivestockRules.familiarityAt(record.state(), now.playerTicks())
                : record.state().familiarity();
        return new HuntKill(killer(source == null ? null : source.getCausingEntity()), player != null,
                source != null && countsAsPlayerKill.test(source), record.state().male(), record.geneticSize(), familiarity,
                attack);
    }

    /** TFC's {@code ATTACKING_ENTITY} classified for {@code tfc:not_predated}. */
    HuntKill.Killer killer(Entity attacker) {
        if (attacker == null) {
            return HuntKill.Killer.NONE;
        }
        if (attacker instanceof Player) {
            return HuntKill.Killer.PLAYER;
        }
        String id = records.apply(attacker).map(r -> "tfp:" + r.species().substring(r.species().indexOf(':') + 1))
                .orElse(attacker.getType().getKey().toString());
        if (entityInTag.test(id, LAND_PREDATORS)) {
            return HuntKill.Killer.LAND_PREDATOR;
        }
        if (entityInTag.test(id, OCEAN_PREDATORS)) {
            return HuntKill.Killer.OCEAN_PREDATOR;
        }
        return HuntKill.Killer.OTHER;
    }

    /**
     * Removes the vanilla carrier's loot from {@code drops}, keeping what the animal carried: its equipment and the contents of
     * its inventory (a horse's saddle and chest). TFC animals drop only their TFC table.
     */
    static void keepOnlyCarried(List<ItemStack> drops, LivingEntity animal) {
        List<ItemStack> carried = new ArrayList<>();
        EntityEquipment equipment = animal.getEquipment();
        if (equipment != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    ItemStack s = equipment.getItem(slot);
                    if (s != null && !s.isEmpty()) {
                        carried.add(s);
                    }
                } catch (IllegalArgumentException unsupported) {
                    // a slot this entity has no equipment for (body armour of a non-horse)
                }
            }
        }
        if (animal instanceof InventoryHolder holder) {
            for (ItemStack s : holder.getInventory().getContents()) {
                if (s != null && !s.isEmpty()) {
                    carried.add(s);
                }
            }
        }
        drops.removeIf(d -> d == null || carried.stream().noneMatch(c -> c.isSimilar(d)));
    }

    /** Whether the damage type is in {@code tfp:counts_as_player_kill} (TFC's lobster trap). False when the tag is absent. */
    static boolean damageTypeCountsAsPlayerKill(DamageSource source) {
        try {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE);
            TagKey<DamageType> tag = TagKey.create(RegistryKey.DAMAGE_TYPE, COUNTS_AS_PLAYER_KILL);
            return registry.hasTag(tag)
                    && registry.getTag(tag).contains(TypedKey.create(RegistryKey.DAMAGE_TYPE, source.getDamageType().getKey()));
        } catch (RuntimeException unsupported) {
            return false;
        }
    }
}
