/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/predator/Predator.java @ c3b4d9536 (hurt, finalizeSpawn,
 * createAttributes), ai/predator/PackPredator.java (hurt, tick, finalizeSpawn), ai/predator/PackLeaderSensor.java,
 * ai/predator/PackPredatorAi.java (alertOthers), prey/WildAnimal.java (setCanFloat), same commit
 */
package dev.tfp.paper.predator;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.predator.PackRules;
import dev.tfp.core.predator.PackTaming;
import dev.tfp.core.predator.PredatorMemory;
import dev.tfp.core.predator.PredatorRules;
import dev.tfp.core.predator.PredatorSpecies;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Predator AI (PAR-FAUNA-10): bears, big cats, the crocodile and the pack predators (wolf, direwolf, hyena) lose their
 * carrier's vanilla goals and run TFC's predator brain as goals ({@link PredatorGoals}): hunt by night (bears by day),
 * rest and sleep at home by day, give up beyond 80 blocks from home or after 200 unreachable ticks, retreat when hurt
 * below 5 health. Pack predators follow the alpha chosen by respect, take its target, and alert each other when hurt;
 * wolves and direwolves howl once a night.
 *
 * <p>Threads: every goal, check and listener runs on the predator's owning thread; the decisions are the pure
 * {@code dev.tfp.core.predator} rules on a snapshot, and only their result is applied. Other predators are touched only
 * when the current region owns them.
 */
public final class PredatorModule implements FeatureModule, Listener {
    private final Map<UUID, PredatorBrain> brains = new ConcurrentHashMap<>();
    private PredatorBody body;
    private LongSupplier clock;
    private RandomGenerator random = ThreadLocalRandom.current();
    /** {@code PackPredator.isFood} for a tamable predator: not rotten, in {@code #tfc:dog_food}. */
    private Predicate<ItemStack> dogFood = stack -> false;
    private DogConverter converter = (wolf, male, baby) -> {
    };

    @Override
    public String id() {
        return "predator";
    }

    @Override
    public void enable(ModuleContext context) {
        this.body = new PaperPredatorBody(() -> context.integrations().entityModels());
        this.clock = () -> context.clock().get().playerTicks();
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        Tags tags = context.services().require(Tags.class);
        ItemFoodHooks foodHooks = context.services().require(ItemFoodHooks.class);
        this.dogFood = stack -> PackTaming.isFood(true, foodHooks.isRotten(stack), tags.is(stack, DOG_FOOD));
        this.converter = (wolf, male, baby) -> convertToDog(animals, context.scheduler(), wolf, male, baby);
        animals.behaviours().register(new Ai());
        context.listen(this);
    }

    /** What happens to a wolf that was fed to a dog: a dog of its gender replaces it. */
    interface DogConverter {
        void toDog(Mob wolf, boolean male, boolean baby);
    }

    static final String DOG_FOOD = "tfp:dog_food";

    /**
     * {@code PackPredator.mobInteract}'s {@code convertTo(TFCEntities.DOG)}: the dog takes the wolf's place with the wolf's
     * gender and name, an adult unless the wolf was a baby ({@code setBirthTickToALongTimeAgo}); the wolf is removed once the
     * dog exists.
     */
    private static void convertToDog(AnimalService animals, Scheduler scheduler, Mob wolf, boolean male, boolean baby) {
        Carrier dog = animals.table().find("tfc:dog").orElse(null);
        if (dog == null) {
            return;
        }
        net.kyori.adventure.text.Component name = wolf.customName();
        animals.spawn(wolf.getLocation(), dog, baby ? AnimalSpawnRoll.AgeHint.CHILD : AnimalSpawnRoll.AgeHint.ADULT, male)
                .whenComplete((spawned, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (name != null) {
                        scheduler.entity(spawned, () -> spawned.customName(name), () -> {
                        });
                    }
                    scheduler.entity(wolf, wolf::remove, () -> {
                    });
                });
    }

    /** Test seam: the world, the clock and the draws. */
    void initForTest(PredatorBody body, LongSupplier clock, RandomGenerator random) {
        this.body = body;
        this.clock = clock;
        this.random = random;
    }

    /** Test seam: the dog food test and the wolf-to-dog conversion. */
    void initTamingForTest(Predicate<ItemStack> dogFood, DogConverter converter) {
        this.dogFood = dogFood;
        this.converter = converter;
    }

    PredatorBrain brain(Entity e) {
        return brains.get(e.getUniqueId());
    }

    SpeciesBehaviour behaviour() {
        return new Ai();
    }

    /** The 12 predators: TFC's brain is their only AI. */
    private final class Ai implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return PredatorSpecies.ids();
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            PredatorSpecies.Profile profile = PredatorSpecies.profile(record.species()).orElseThrow();
            PredatorBrain brain = new PredatorBrain(mob, profile, body);
            brain.load(() -> random.nextInt(PackRules.INITIAL_RESPECT_BOUND));
            brains.put(mob.getUniqueId(), brain);
            PredatorGoals.Deps d = new PredatorGoals.Deps(body, clock, random, dogFood);
            List<GoalSpec> goals = new ArrayList<>(5);
            // AmphibiousPredatorAi.initCoreActivity has no AggressiveSwim (PredatorAi's has): the crocodile dives.
            if (profile.kind() != PredatorSpecies.Kind.AMPHIBIOUS) {
                goals.add(new GoalSpec(0, new PredatorGoals.Swim(brain, d)));
            }
            goals.add(new GoalSpec(1, new PredatorGoals.Fight(brain, d)));
            goals.add(new GoalSpec(2, new PredatorGoals.Retreat(brain, d)));
            goals.add(new GoalSpec(3, new PredatorGoals.Rest(brain, d)));
            goals.add(new GoalSpec(4, new PredatorGoals.Hunt(brain, d)));
            return goals;
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (entity instanceof Mob mob) {
                mob.getPathfinder().setCanFloat(true);
                PredatorSpecies.profile(record.species()).ifPresent(p -> applyAttributes(mob, p.attributes()));
            }
        }

        @Override
        public void onCheck(Check check) {
            PredatorBrain brain = brains.get(check.entity().getUniqueId());
            if (brain == null || !brain.profile.pack()) {
                return;
            }
            long now = clock.getAsLong();
            if (brain.packScanAt == Long.MIN_VALUE || now - brain.packScanAt >= PackRules.SENSOR_TICKS || now < brain.packScanAt) {
                brain.packScanAt = now;
                scanPack(brain, now);
            }
            howl(brain);
        }
    }

    /** TFC's registered attributes; health is filled up the first time the maximum changes (a fresh carrier). */
    static void applyAttributes(LivingEntity e, PredatorSpecies.Attributes a) {
        set(e, Attribute.MOVEMENT_SPEED, a.movementSpeed());
        set(e, Attribute.ATTACK_KNOCKBACK, a.attackKnockback());
        set(e, Attribute.ATTACK_DAMAGE, a.attackDamage());
        set(e, Attribute.KNOCKBACK_RESISTANCE, a.knockbackResistance());
        set(e, Attribute.STEP_HEIGHT, a.stepHeight());
        if (set(e, Attribute.MAX_HEALTH, a.maxHealth())) {
            e.setHealth(a.maxHealth());
        }
    }

    private static boolean set(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance i = e.getAttribute(attribute);
        if (i == null) {
            e.registerAttribute(attribute);
            i = e.getAttribute(attribute);
        }
        if (i == null || i.getBaseValue() == value) {
            return false;
        }
        i.setBaseValue(value);
        return true;
    }

    /** {@code PackLeaderSensor.doTick}: the same-type predators within 32 blocks choose the alpha by respect. */
    void scanPack(PredatorBrain brain, long now) {
        List<PredatorBrain> members = new ArrayList<>();
        List<PackRules.Member> view = new ArrayList<>();
        for (LivingEntity e : body.nearby(brain.mob, PackRules.SENSOR_RANGE)) {
            PredatorBrain other = brains.get(e.getUniqueId());
            if (other == null || other == brain || !other.profile.id().equals(brain.profile.id())) {
                continue;
            }
            members.add(other);
            view.add(new PackRules.Member(other.respect(), PredatorSenses.baby(e, now)));
        }
        PackRules.Pack pack = PackRules.chooseAlpha(brain.respect(), view);
        for (int i = 0; i < members.size(); i++) {
            int r = pack.respects().get(i);
            if (r != members.get(i).respect()) {
                members.get(i).setRespect(r);
            }
        }
        brain.alpha = pack.alpha() < 0 ? null : members.get(pack.alpha());
    }

    /** {@code PackPredator.tick}: a wolf or direwolf howls once a night between 18000 and 19000. */
    void howl(PredatorBrain brain) {
        long dayTime = body.dayTime(brain.mob);
        if (PackRules.drawsHowl(brain.howled, brain.profile.howling(), dayTime)
                && PackRules.howls(brain.howled, brain.profile.howling(), dayTime, random.nextInt(PackRules.HOWL_ONE_IN))) {
            body.vanillaSound(brain.mob, "minecraft:entity.wolf.howl", PackRules.HOWL_VOLUME,
                    PaperPredatorBody.voicePitch(brain.mob));
            brain.howled = true;
        }
        if (PackRules.howlResets(dayTime)) {
            brain.howled = false;
        }
    }

    static LivingEntity direct(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof LivingEntity l ? l : null;
    }

    static LivingEntity causing(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity d = byEntity.getDamager();
        if (d instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return d instanceof LivingEntity living ? living : null;
    }

    /**
     * TFC {@code PackPredator.hurt} then {@code Predator.hurt}: a pack predator alerts the pack (the direct attacker when it
     * is a living non-creative entity, else only when asleep); the hurt predator takes the attacker as its target, forgets
     * pacified, hunted-recently and unreachable, and wakes.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        PredatorBrain brain = brains.get(event.getEntity().getUniqueId());
        if (brain == null) {
            return;
        }
        long now = clock.getAsLong();
        LivingEntity direct = direct(event);
        LivingEntity target = direct != null && !PredatorSenses.creativeOrSpectator(direct) ? direct : null;
        LivingEntity by = causing(event);
        if (by != null) {
            brain.attacker = by;
            brain.hurtAt = now;
        }
        if (brain.profile.pack()) {
            if (target != null) {
                alertOthers(brain, target);
            } else if (brain.memory.sleeping()) {
                alertOthers(brain, null);
            }
        }
        boolean alive = brain.mob.getHealth() - event.getFinalDamage() > 0;
        if (alive && target != null) {
            brain.target = target;
        }
        brain.update(PredatorRules.hurt(brain.memory, alive, alive && target != null));
    }

    /** {@code PackPredatorAi.alertOthers}: every pack predator among the nearby living entities (16 blocks). */
    void alertOthers(PredatorBrain hurt, LivingEntity attacker) {
        for (LivingEntity e : body.nearby(hurt.mob, PredatorGoals.SENSE_RANGE)) {
            PredatorBrain other = brains.get(e.getUniqueId());
            if (other == null || other == hurt || !other.profile.pack() || !Bukkit.isOwnedByCurrentRegion(e)) {
                continue;
            }
            if (attacker != null) {
                other.target = attacker;
            }
            PredatorMemory next = PackRules.alerted(other.memory, attacker != null);
            other.update(next);
        }
    }

    /**
     * {@code PackPredator.mobInteract}: a tamable pack predator offered dog food eats it at most once a calendar day, gains
     * 0.1 familiarity, and above 0.99 becomes a dog. The click is taken whether or not the food was eaten.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        PredatorBrain brain = brains.get(event.getRightClicked().getUniqueId());
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (brain == null || !brain.profile.tamable() || held.isEmpty() || !dogFood.test(held)
                || !Bukkit.isOwnedByCurrentRegion(brain.mob)) {
            return;
        }
        event.setCancelled(true);
        long now = clock.getAsLong();
        PackTaming.Feed feed = PackTaming.feed(brain.taming(), now);
        if (!feed.accepted()) {
            return;
        }
        brain.setTaming(feed.state());
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
        body.vanillaSound(brain.mob, "minecraft:entity.generic.eat", 1.0f,
                PaperPredatorBody.voicePitch(brain.mob));
        if (feed.converts()) {
            boolean male = AnimalPdc.read(brain.mob.getPersistentDataContainer()).map(r -> r.state().male()).orElse(true);
            converter.toDog(brain.mob, male, PredatorSenses.baby(brain.mob, now));
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        brains.remove(event.getEntity().getUniqueId());
    }
}
