/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/WildAnimal.java @ c3b4d9536 (setCanFloat), prey/Prey.java,
 * prey/RammingPrey.java, prey/Pest.java, prey/WingedPrey.java, prey/TFCFox.java, prey/TFCRabbit.java, prey/TFCPanda.java,
 * prey/TFCArmadillo.java (registerGoals, mobInteract, isFood, hurt), ai/prey/PreyAi.java (broadcastAvoidTarget)
 */
package dev.tfp.paper.prey;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.prey.Avoidance;
import dev.tfp.core.prey.FleeState;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.SmallPrey;
import dev.tfp.core.prey.WildPrey;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prey AI and wild animals (PAR-FAUNA-09).
 *
 * <ul>
 *   <li>The 16 {@link WildPrey} species (deer family, game birds, rammers, pests) lose their carrier's vanilla goals and
 *       run TFC's brain as goals: swim, panic (not rammers), flee ({@link PreyFleeGoal}), ram (rammers,
 *       {@link RamGoal}), baby follows adult, idle movement and looking at players. They get TFC's attributes.</li>
 *   <li>Fox, rabbit and panda keep their vanilla AI minus the goals TFC removes, plus TFC's land-predator avoid goal.</li>
 *   <li>The armadillo keeps its vanilla brain, is also scared by awake land predators and flees from land predators and
 *       rammers; scared, it takes TFC's extra damage reduction; it cannot be fed or brushed (TFC passes).</li>
 * </ul>
 *
 * <p>Threads: every goal and listener runs on the animal's owning thread; the decisions are the pure
 * {@code dev.tfp.core.prey} rules on a snapshot of positions and flags, and only their result is applied.
 */
public final class PreyModule implements FeatureModule, Listener {
    static final Set<String> SMALL = Set.of("tfc:fox", "tfc:rabbit", "tfc:panda");
    static final String ARMADILLO = "tfc:armadillo";

    /** Vanilla {@code #minecraft:panic_causes}, as Bukkit causes: what starts {@code AnimalPanic}. */
    static final Set<EntityDamageEvent.DamageCause> PANIC_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK,
            EntityDamageEvent.DamageCause.PROJECTILE, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.CAMPFIRE,
            EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.FREEZE, EntityDamageEvent.DamageCause.CONTACT,
            EntityDamageEvent.DamageCause.LIGHTNING, EntityDamageEvent.DamageCause.MAGIC,
            EntityDamageEvent.DamageCause.THORNS, EntityDamageEvent.DamageCause.SONIC_BOOM);

    private final Map<UUID, PreyBrain> brains = new ConcurrentHashMap<>();
    private PreyBody body = new PaperPreyBody();
    private PreySenses senses;
    private LongSupplier clock;
    private RandomGenerator random = ThreadLocalRandom.current();
    private BiConsumer<LivingEntity, Boolean> danger = (e, on) -> e.setMemory(MemoryKey.DANGER_DETECTED_RECENTLY, on ? true : null);
    private BiConsumer<Mob, GoalKey<?>> removeVanilla = PreyModule::removeVanillaGoal;

    @Override
    public String id() {
        return "prey";
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        init(context.clock()::get, (species, tag) -> tags.is(TagType.ENTITY_TYPE, species, tag));
        ((TfpPlugin) context.plugin()).animals().behaviours().register(new WildAi());
        ((TfpPlugin) context.plugin()).animals().behaviours().register(new SmallAi());
        ((TfpPlugin) context.plugin()).animals().behaviours().register(new ArmadilloAi());
        context.listen(this);
    }

    private void init(java.util.function.Supplier<dev.tfp.core.clock.ClockSnapshot> snapshot, BiPredicate<String, String> tags) {
        this.clock = () -> snapshot.get().playerTicks();
        this.senses = new PreySenses(tags);
    }

    /** Test seam: the world, the clock, the tags, the draws and the brain-memory and vanilla-goal writers. */
    void initForTest(PreyBody body, LongSupplier clock, BiPredicate<String, String> tags, RandomGenerator random,
            BiConsumer<LivingEntity, Boolean> danger, BiConsumer<Mob, GoalKey<?>> removeVanilla) {
        this.body = body;
        this.clock = clock;
        this.senses = new PreySenses(tags);
        this.random = random;
        this.danger = danger;
        this.removeVanilla = removeVanilla;
    }

    PreyBrain brain(Entity e) {
        return brains.get(e.getUniqueId());
    }

    SpeciesBehaviour wildBehaviour() {
        return new WildAi();
    }

    SpeciesBehaviour smallBehaviour() {
        return new SmallAi();
    }

    SpeciesBehaviour armadilloBehaviour() {
        return new ArmadilloAi();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void removeVanillaGoal(Mob mob, GoalKey<?> key) {
        Bukkit.getMobGoals().removeGoal(mob, (GoalKey) key);
    }

    /** TFC's registered attributes; health is filled up the first time the base changes (a fresh carrier). */
    static void applyAttributes(LivingEntity e, WildPrey.Attributes a) {
        set(e, Attribute.MOVEMENT_SPEED, a.movementSpeed());
        set(e, Attribute.KNOCKBACK_RESISTANCE, a.knockbackResistance());
        set(e, Attribute.STEP_HEIGHT, a.stepHeight());
        if (set(e, Attribute.MAX_HEALTH, a.maxHealth())) {
            e.setHealth(a.maxHealth());
        }
    }

    private static boolean set(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance i = e.getAttribute(attribute);
        if (Double.isNaN(value) || i == null || i.getBaseValue() == value) {
            return false;
        }
        i.setBaseValue(value);
        return true;
    }

    private PreyBrain newBrain(Mob mob, String species, WildPrey.Kind kind, Avoidance avoidance, float speed) {
        PreyBrain brain = new PreyBrain(mob, species, kind, avoidance, speed);
        brain.kinds = senses::kind;
        brains.put(mob.getUniqueId(), brain);
        return brain;
    }

    /** The 16 prey species: TFC's brain is their only AI. */
    private final class WildAi implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return WildPrey.species();
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            WildPrey.Kind kind = WildPrey.kind(record.species()).orElseThrow();
            PreyBrain brain = newBrain(mob, record.species(), kind, kind.avoidance(), PreyFlee.FLEE_SPEED);
            List<GoalSpec> goals = new java.util.ArrayList<>(List.of(
                    new GoalSpec(0, new PreyIdleGoals.Swim(brain, body, random)),
                    new GoalSpec(2, new PreyFleeGoal(brain, body, senses, clock, random)),
                    new GoalSpec(4, new PreyIdleGoals.FollowAdult(brain, body, clock)),
                    new GoalSpec(5, new PreyIdleGoals.Idle(brain, body, clock, random)),
                    new GoalSpec(6, new PreyIdleGoals.Look(brain, body, clock, random))));
            if (kind.panics()) {
                goals.add(new GoalSpec(1, new PreyIdleGoals.Panic(brain, body, clock, random)));
            }
            if (kind == WildPrey.Kind.RAMMING) {
                goals.add(new GoalSpec(3, new RamGoal(brain, body, senses, clock, random)));
            }
            return goals;
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (entity instanceof Mob mob) {
                mob.getPathfinder().setCanFloat(true);
            }
            if (entity instanceof LivingEntity living) {
                WildPrey.attributes(record.species()).ifPresent(a -> applyAttributes(living, a));
            }
        }
    }

    /** Fox, rabbit and panda: vanilla AI with TFC's removals and the land-predator avoid goal. */
    private final class SmallAi implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return SMALL;
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            SmallPrey.Speeds speeds = SmallPrey.avoidSpeeds(record.species()).orElseThrow();
            PreyBrain brain = newBrain(mob, record.species(), null, Avoidance.PREDATORS_ONLY, (float) speeds.far());
            int priority = record.species().equals("tfc:panda") ? 6 : 4;
            return List.of(new GoalSpec(priority, new SmallPreyGoals.AvoidPredator(brain, body, senses, speeds, clock, random)));
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (!(entity instanceof Mob mob)) {
                return;
            }
            for (GoalKey<?> key : removedVanillaGoals(record.species())) {
                removeVanilla.accept(mob, key);
            }
            WildPrey.attributes(record.species()).ifPresent(a -> applyAttributes(mob, a));
        }
    }

    /** The vanilla goals TFC removes ({@code removeGoalOfPriority}/{@code removeGoalOfClass}) per species. */
    static List<GoalKey<?>> removedVanillaGoals(String species) {
        return switch (species) {
            // TFCFox: breed (priority 3) and FoxEatBerriesGoal
            case "tfc:fox" -> List.of(VanillaGoal.FOX_BREED, VanillaGoal.FOX_EAT_BERRIES);
            // TFCRabbit: tempt (3), the three avoid goals (4), raid garden (5)
            case "tfc:rabbit" -> List.of(VanillaGoal.TEMPT, VanillaGoal.RABBIT_AVOID_ENTITY, VanillaGoal.RABBIT_RAID_GARDEN);
            // TFCPanda: breed (2)
            case "tfc:panda" -> List.of(VanillaGoal.PANDA_BREED);
            default -> List.of();
        };
    }

    /** The armadillo: vanilla brain, plus the predator scare and the retreat from predators and rammers. */
    private final class ArmadilloAi implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return Set.of(ARMADILLO);
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            PreyBrain brain = newBrain(mob, record.species(), null, Avoidance.PREDATORS_AND_RAMMERS, PreyFlee.ARMADILLO_FLEE_SPEED);
            return List.of(new GoalSpec(1, new SmallPreyGoals.ArmadilloScare(brain, body, senses, clock, danger)),
                    new GoalSpec(2, new PreyFleeGoal(brain, body, senses, clock, random)));
        }
    }

    static LivingEntity damager(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity d = byEntity.getDamager();
        if (d instanceof Projectile p && p.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return d instanceof LivingEntity living ? living : null;
    }

    /** TFC {@code Prey.hurt}: panic, threat switch and the warning to same-type friends that see the attacker. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        PreyBrain brain = brains.get(event.getEntity().getUniqueId());
        if (brain == null) {
            return;
        }
        long now = clock.getAsLong();
        LivingEntity by = damager(event);
        if (brain.hurt(by, now, PANIC_CAUSES.contains(event.getCause()), random)) {
            broadcast(brain, by, now);
        }
    }

    /** {@code PreyAi.broadcastAvoidTarget}: each visible friend of the same type takes the nearer of its threat and the attacker. */
    void broadcast(PreyBrain hurt, LivingEntity attacker, long now) {
        for (LivingEntity e : body.nearby(hurt.mob, PreyFleeGoal.SENSE_RANGE, Ticks.now())) {
            PreyBrain friend = brains.get(e.getUniqueId());
            if (friend == null || friend == hurt || !friend.species.equals(hurt.species) || !Bukkit.isOwnedByCurrentRegion(e)
                    || !body.sees(hurt.mob, e)) {
                continue;
            }
            boolean hasThreat = PreyBrain.alive(friend.threat);
            boolean takes = PreyFlee.friendTakesAttacker(PreySenses.at(e), friend.flee, now,
                    hasThreat ? PreySenses.at(friend.threat) : null, PreySenses.at(attacker));
            LivingEntity nearest = takes ? attacker : friend.threat;
            friend.lookTarget = null;
            friend.setThreat(nearest, PreyFlee.startFlee(now, PreySenses.species(nearest), PreyFlee.retreatTicks(random)));
        }
    }

    /** {@code TFCArmadillo.hurt}: one point less before vanilla halves it while scared. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmadilloHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Armadillo a && a.getState() != Armadillo.State.IDLE
                && ARMADILLO.equals(speciesOf(a))) {
            event.setDamage(SmallPrey.armadilloDamage(event.getDamage()));
        }
    }

    private static String speciesOf(Entity e) {
        return PreySenses.record(e).map(AnimalRecord::species).orElse(null);
    }

    /**
     * TFC's interaction changes: the armadillo passes every interaction (no feeding, no brushing), the fox eats nothing
     * ({@code isFood} false), and a baby panda cannot be fed bamboo to grow up.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        String species = speciesOf(event.getRightClicked());
        if (species == null) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        Material m = held == null ? Material.AIR : held.getType();
        if (blocksInteraction(species, m, PreySenses.baby(event.getRightClicked(), clock.getAsLong()))) {
            event.setCancelled(true);
        }
    }

    static boolean blocksInteraction(String species, Material held, boolean baby) {
        return switch (species) {
            case ARMADILLO -> held == Material.BRUSH || held == Material.SPIDER_EYE;
            case "tfc:fox" -> held == Material.SWEET_BERRIES || held == Material.GLOW_BERRIES;
            case "tfc:panda" -> baby && held == Material.BAMBOO;
            default -> false;
        };
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        brains.remove(event.getEntity().getUniqueId());
        SpeciesCache.forget(event.getEntity().getUniqueId());
    }

    /** For tests: the stored flee state of {@code e}. */
    FleeState flee(Entity e) {
        PreyBrain b = brains.get(e.getUniqueId());
        return b == null ? FleeState.IDLE : b.flee;
    }
}
