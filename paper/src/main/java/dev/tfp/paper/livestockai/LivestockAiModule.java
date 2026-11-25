/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/TFCEntities.java @ c3b4d9536 (the domestic roster),
 * livestock/TFCAnimal.java and OviparousAnimal.java (makeBrain, hurt), livestock/camel/TFCCamelAi.java,
 * livestock/horse/TFCHorse.java and TFCChestedHorse.java (registerGoals), ai/prey/PreyAi.java (broadcastAvoidTarget)
 */
package dev.tfp.paper.livestockai;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.livestockai.LivestockRoster;
import dev.tfp.core.livestockai.LivestockShowcase;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.livestock.LivestockFood;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Livestock AI and roster (PAR-FAUNA-08).
 *
 * <ul>
 *   <li>Pig, cow, goat, yak, alpaca, sheep, musk ox, chicken, duck, quail ({@code LivestockAi}/{@code OviparousAi}) and
 *       the two camels ({@code TFCCamelAi}, at camel speeds) lose the carrier's vanilla goals and run TFC's brain as
 *       goals: swim, panic after a hit, avoid land predators and ramming animals (warning the herd when hit), walk to a
 *       ready mate (males, then the pairing), follow a player holding their food, babies follow an adult, idle stroll /
 *       walk to the look target / graze in place, look at players ({@link LivestockGoals}).</li>
 *   <li>Horse, donkey, mule keep their vanilla horse goals with the tempt goal replaced by TFC's food at speed 1.25.</li>
 *   <li>Cat and dog keep their carrier's goals until the pet commands (PAR-FAUNA-14); the rabbit runs PAR-FAUNA-09.</li>
 * </ul>
 *
 * <p>{@code /tfp debug livestock showcase} spawns a female and a male adult of every one of the 18 domestic species in a
 * fenced pen in front of the player ({@link LivestockShowcase}).
 *
 * <p>Threads: every goal and listener runs on the animal's owning thread; decisions are the pure
 * {@code dev.tfp.core.livestockai} / {@code dev.tfp.core.prey} rules on a snapshot of positions and records.
 */
public final class LivestockAiModule implements FeatureModule, Listener {
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

    private final Map<UUID, LivestockBrain> brains = new ConcurrentHashMap<>();
    private ModuleContext ctx;
    private LivestockBody body = new PaperLivestockBody();
    private LivestockSenses senses;
    private Supplier<ClockSnapshot> clock;
    private RandomGenerator random = ThreadLocalRandom.current();
    /** The food test for a species id. */
    private Function<String, Predicate<ItemStack>> food;
    private BiConsumer<Mob, GoalKey<?>> removeVanilla = LivestockAiModule::removeVanillaGoal;

    @Override
    public String id() {
        return "livestockai";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        Tags tags = context.services().require(Tags.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        this.clock = context.clock()::get;
        this.senses = new LivestockSenses((species, tag) -> tags.is(TagType.ENTITY_TYPE, species, tag));
        dev.tfp.paper.food.ItemFoodHooks foodHooks = context.services().require(dev.tfp.paper.food.ItemFoodHooks.class);
        this.food = species -> {
            String tag = animals.table().find(species).map(LivestockFood::tag).orElse("tfp:none");
            boolean eatsRotten = species(species).map(LivestockSpecies::eatsRottenFood).orElse(false);
            // TFCAnimalProperties.isFood: (eatsRottenFood() || !isRotten(stack)) && isItem(stack, getFoodTag())
            return stack -> (eatsRotten || !foodHooks.isRotten(stack)) && tags.is(stack, tag);
        };
        animals.behaviours().register(brainBehaviour());
        animals.behaviours().register(horseBehaviour());
        context.listen(this);
    }

    /** Test seam: the world, the clock, the tags, the food, the draws and the vanilla-goal remover. */
    void initForTest(LivestockBody body, Supplier<ClockSnapshot> clock, BiPredicate<String, String> tags,
            Function<String, Predicate<ItemStack>> food, RandomGenerator random, BiConsumer<Mob, GoalKey<?>> removeVanilla) {
        this.body = body;
        this.clock = clock;
        this.senses = new LivestockSenses(tags);
        this.food = food;
        this.random = random;
        this.removeVanilla = removeVanilla;
    }

    private long now() {
        return clock.get().playerTicks();
    }

    /** For tests: the senses (their sighting count). */
    LivestockSenses senses() {
        return senses;
    }

    LivestockBrain brain(Entity e) {
        return brains.get(e.getUniqueId());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void removeVanillaGoal(Mob mob, GoalKey<?> key) {
        Bukkit.getMobGoals().removeGoal(mob, (GoalKey) key);
    }

    private LivestockBrain newBrain(Mob mob, String species, LivestockRoster.Profile profile) {
        LivestockBrain brain = new LivestockBrain(mob, species, profile, food.apply(species));
        brain.kinds = senses::kind;
        brains.put(mob.getUniqueId(), brain);
        return brain;
    }

    /** The 12 species whose TFC brain replaces the carrier's goals. */
    SpeciesBehaviour brainBehaviour() {
        return new SpeciesBehaviour() {
            @Override
            public Set<String> species() {
                return LivestockRoster.species().stream()
                        .filter(s -> LivestockRoster.brain(s).orElseThrow().tfcGoals())
                        .collect(Collectors.toUnmodifiableSet());
            }

            @Override
            public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
                LivestockRoster.Profile profile = LivestockRoster.profile(record.species()).orElseThrow();
                LivestockBrain brain = newBrain(mob, record.species(), profile);
                java.util.function.LongSupplier now = LivestockAiModule.this::now;
                return List.of(
                        new GoalSpec(LivestockGoals.SWIM, new LivestockGoals.Swim(brain, body, random)),
                        new GoalSpec(LivestockGoals.PANIC, new LivestockGoals.Panic(brain, body, now, random)),
                        new GoalSpec(LivestockGoals.AVOID, new LivestockGoals.Avoid(brain, body, senses, now, random)),
                        new GoalSpec(LivestockGoals.BREED, new LivestockGoals.Breed(brain, body, now, random,
                                LivestockAiModule.this::canMate, LivestockAiModule.this::brain, LivestockAiModule.this::pair)),
                        new GoalSpec(LivestockGoals.TEMPT, new LivestockGoals.Tempt(brain, body, now,
                                profile.tempt(false), profile.tempt(true))),
                        new GoalSpec(LivestockGoals.FOLLOW_ADULT, new LivestockGoals.FollowAdult(brain, body, now)),
                        new GoalSpec(LivestockGoals.IDLE, new LivestockGoals.Idle(brain, body, now, random)),
                        new GoalSpec(LivestockGoals.LOOK, new LivestockGoals.Look(brain, body, now, random)));
            }

            @Override
            public void onAdd(Entity entity, AnimalRecord record) {
                if (entity instanceof Mob mob) {
                    mob.getPathfinder().setCanFloat(true);
                }
            }
        };
    }

    /** Horse, donkey, mule: vanilla horse goals, the tempt goal swapped for TFC's food. */
    SpeciesBehaviour horseBehaviour() {
        return new SpeciesBehaviour() {
            @Override
            public Set<String> species() {
                return Set.copyOf(LivestockRoster.species(LivestockRoster.Brain.HORSE));
            }

            @Override
            public boolean keepVanillaGoals() {
                return true;
            }

            @Override
            public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
                LivestockBrain brain = newBrain(mob, record.species(), null);
                return List.of(new GoalSpec(LivestockRoster.HORSE_TEMPT_PRIORITY, new LivestockGoals.Tempt(brain, body,
                        LivestockAiModule.this::now, LivestockRoster.HORSE_TEMPT_SPEED, LivestockRoster.HORSE_TEMPT_SPEED)));
            }

            @Override
            public void onAdd(Entity entity, AnimalRecord record) {
                if (entity instanceof Mob mob) {
                    // EntityHelpers.removeGoalOfPriority(goalSelector, 3): vanilla's horse tempt goal
                    removeVanilla.accept(mob, VanillaGoal.TEMPT);
                }
            }
        };
    }

    /** TFC {@code TFCAnimal.canMate}: same species, opposite genders, both ready ({@link BreedingRules#canMate}). */
    boolean canMate(LivingEntity a, LivingEntity b) {
        Optional<AnimalRecord> ra = LivestockSenses.record(a);
        Optional<AnimalRecord> rb = LivestockSenses.record(b);
        if (ra.isEmpty() || rb.isEmpty() || !ra.get().species().equals(rb.get().species())) {
            return false;
        }
        Optional<LivestockSpecies> sp = species(ra.get().species());
        return sp.isPresent() && BreedingRules.canMate(ra.get().state(), rb.get().state(), sp.get(), now());
    }

    private static Optional<LivestockSpecies> species(String id) {
        try {
            return Optional.of(LivestockSpecies.valueOf(id.substring(id.indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * {@code BreedBehavior.tick}'s {@code target.getBreedOffspring}: TFC's {@code onFertilized} on the pair
     * ({@link BreedingRules#fertilize}), written to both records. A female fertilized meanwhile (the PAR-FAUNA-05 check)
     * is left alone, as TFC does.
     */
    void pair(LivingEntity male, LivingEntity female) {
        Optional<AnimalRecord> rm = LivestockSenses.record(male);
        Optional<AnimalRecord> rf = LivestockSenses.record(female);
        ClockSnapshot now = clock.get();
        if (rm.isEmpty() || rf.isEmpty() || !now.isSet() || rf.get().state().male() || !rm.get().state().male()
                || !canMate(male, female)) {
            return;
        }
        LivestockSpecies sp = species(rf.get().species()).orElseThrow();
        BreedingRules.Pairing p = BreedingRules.fertilize(rf.get().state(), rm.get().state(), sp, now.playerTicks(),
                now.calendarTicks());
        AnimalPdc.write(female.getPersistentDataContainer(), rf.get().withState(p.female()));
        AnimalPdc.write(male.getPersistentDataContainer(), rm.get().withState(p.male()));
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

    /** TFC {@code TFCAnimal.hurt}: panic, the threat switch while fleeing and the warning to the herd. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        LivestockBrain brain = brains.get(event.getEntity().getUniqueId());
        if (brain == null || brain.profile == null) {
            return;
        }
        long now = now();
        LivingEntity by = damager(event);
        String type = by == null ? null : LivestockSenses.species(by);
        brain.breedPartner = null;
        if (brain.hurt(by, type, now, PANIC_CAUSES.contains(event.getCause()), random)) {
            broadcast(brain, by, now);
        }
    }

    /** {@code PreyAi.broadcastAvoidTarget}: each visible herd mate takes the nearer of its threat and the attacker. */
    void broadcast(LivestockBrain hurt, LivingEntity attacker, long now) {
        for (LivingEntity e : body.nearby(hurt.mob, LivestockGoals.SENSE_RANGE, dev.tfp.paper.prey.Ticks.now())) {
            LivestockBrain friend = brains.get(e.getUniqueId());
            if (friend == null || friend == hurt || !friend.species.equals(hurt.species) || !Bukkit.isOwnedByCurrentRegion(e)
                    || !body.sees(hurt.mob, e)) {
                continue;
            }
            boolean hasThreat = LivestockBrain.alive(friend.threat);
            boolean takes = PreyFlee.friendTakesAttacker(LivestockSenses.at(e), friend.flee, now,
                    hasThreat ? LivestockSenses.at(friend.threat) : null, LivestockSenses.at(attacker));
            LivingEntity nearest = takes ? attacker : friend.threat;
            friend.lookTarget = null;
            friend.setThreat(nearest, PreyFlee.startFlee(now, LivestockSenses.species(nearest), PreyFlee.retreatTicks(random)));
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        brains.remove(event.getEntity().getUniqueId());
        dev.tfp.paper.prey.SpeciesCache.forget(event.getEntity().getUniqueId());
    }

    // ---- debug ----------------------------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("livestock", node -> node.then(io.papermc.paper.command.brigadier.Commands.literal("showcase")
                .executes(tree.player((player, c) -> showcase(player)))));
    }

    /** The pen: a fence around the footprint, then a female and a male adult of each of the 18 species. */
    private CompletableFuture<Reply> showcase(Player player) {
        AnimalService animals = ((TfpPlugin) ctx.plugin()).animals();
        Location eye = player.getLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(0, 0, 1);
        }
        Location corner = eye.clone().add(dir.normalize().multiply(6));
        corner = new Location(corner.getWorld(), corner.getBlockX(), corner.getBlockY(), corner.getBlockZ());
        List<String> roster = LivestockRoster.species();
        int[] size = LivestockShowcase.size(roster.size());
        fence(corner, size);
        List<CompletableFuture<Entity>> spawned = new ArrayList<>();
        for (LivestockShowcase.Slot slot : LivestockShowcase.layout(roster)) {
            Carrier carrier = animals.table().find(slot.species()).orElseThrow();
            Location at = corner.clone().add(slot.dx() + 0.5, 0, slot.dz() + 0.5);
            spawned.add(animals.spawn(at, carrier, AnimalSpawnRoll.AgeHint.ADULT, slot.male()));
        }
        return CompletableFuture.allOf(spawned.toArray(CompletableFuture[]::new)).handle((v, e) ->
                e == null ? Reply.ok("debug.livestock.showcase.done", spawned.size(), roster.size())
                        : Reply.error("debug.livestock.showcase.failed", String.valueOf(e.getMessage())));
    }

    /** An oak fence one block outside the footprint, on each column's surface, written on the owning regions. */
    private void fence(Location corner, int[] size) {
        org.bukkit.World world = corner.getWorld();
        int x0 = corner.getBlockX() - 2;
        int z0 = corner.getBlockZ() - 2;
        int x1 = corner.getBlockX() + size[0] + 1;
        int z1 = corner.getBlockZ() + size[1] + 1;
        Map<Long, List<int[]>> byChunk = new java.util.HashMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    byChunk.computeIfAbsent(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL), k -> new ArrayList<>())
                            .add(new int[] {x, z});
                }
            }
        }
        for (List<int[]> cols : byChunk.values()) {
            int[] first = cols.get(0);
            ctx.scheduler().region(world, first[0] >> 4, first[1] >> 4, () -> {
                for (int[] c : cols) {
                    int y = world.getHighestBlockYAt(c[0], c[1]) + 1;
                    world.getBlockAt(c[0], y, c[1]).setType(org.bukkit.Material.OAK_FENCE);
                }
            });
        }
    }
}
