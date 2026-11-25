/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java @ c3b4d9536 (hurt, finalizeSpawn,
 * mobInteract, canDrownInFluidType, createAttributes), aquatic/AquaticMob.java (canDrownInFluidType),
 * predator/AmphibiousPredator.java (moveControl, travel), prey/TFCFrog.java (finalizeSpawn, mobInteract, isFood), same commit
 */
package dev.tfp.paper.amphibian;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tfp.core.amphibian.AmphibianSpecies;
import dev.tfp.core.amphibian.FrogRules;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Frog;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Amphibians (PAR-FAUNA-12).
 *
 * <ul>
 *   <li><b>Turtle, penguin, leopard seal</b>: TFC's {@code AmphibianAi} / {@code PinnipedAI} replace the carrier's goals
 *       ({@link AmphibianBrain}, one goal per mob). A hit turtle or penguin plays dead out of water for 200 ticks (the turtle
 *       gets regeneration I and resistance III); a hit seal retreats from its attacker. They look at players, follow
 *       temptation and adults, seek water within 6 blocks, swim, stroll, hunt small fish and return home. TFC attributes;
 *       feeding their food heals 1 point when hurt; they do not drown.</li>
 *   <li><b>Land/water</b>: the penguin (a chicken carrier) swims with TFC's {@code TrueAmphibiousMoveControl} in water
 *       ({@link AmphibiousMover}); the turtle carrier swims natively. The crocodile, whose goals are PAR-FAUNA-10's, gets the
 *       same mover from a per-mob task and does not drown ({@code AquaticMob}).</li>
 *   <li><b>Frog</b>: the vanilla frog keeps its brain; TFC's variant by average temperature, its feeding with
 *       {@code #tfc:frog_food} (+0.1 familiarity, once a day) and no slime-ball feeding.</li>
 * </ul>
 *
 * <p>Threads: goals, listeners and tasks run on the mob's owning thread; decisions are the pure {@code dev.tfp.core.amphibian}
 * rules.
 */
public final class AmphibianModule implements FeatureModule, Listener {
    static final Set<String> AMPHIBIANS = Set.of("tfc:turtle", "tfc:penguin", "tfc:leopard_seal");
    static final String CROCODILE = "tfc:crocodile";
    static final String FROG = "tfc:frog";
    static final NamespacedKey HOME = new NamespacedKey("tfp", "amphibian_home");
    static final NamespacedKey FROG_FAMILIARITY = new NamespacedKey("tfp", "frog_familiarity");
    static final NamespacedKey FROG_NEXT_FEED = new NamespacedKey("tfp", "frog_next_feed");
    static final NamespacedKey FROG_VARIANT = new NamespacedKey("tfp", "frog_variant");

    private final Map<UUID, AmphibianBrain> brains = new ConcurrentHashMap<>();
    private final Map<UUID, Scheduler.TaskHandle> movers = new ConcurrentHashMap<>();
    private ModuleContext ctx;
    private AmphibianBody body;
    private BiPredicate<String, String> speciesInTag;
    private Function<String, Predicate<ItemStack>> food;
    private Predicate<ItemStack> frogFood;
    private Predicate<ItemStack> rotten = s -> false;
    private Function<Entity, Optional<AnimalRecord>> records = e -> AnimalPdc.read(e.getPersistentDataContainer());
    private LongSupplier ticks;
    private ToDoubleFunction<Location> temperature;
    private RandomGenerator random = ThreadLocalRandom.current();

    @Override
    public String id() {
        return "amphibian";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        Tags tags = context.services().require(Tags.class);
        dev.tfp.paper.food.ItemFoodHooks foodHooks = context.services().require(dev.tfp.paper.food.ItemFoodHooks.class);
        this.body = new PaperAmphibianBody(() -> context.integrations().entityModels());
        this.speciesInTag = (id, tag) -> tags.is(TagType.ENTITY_TYPE, id, tag);
        // AmphibiousAnimal subclasses' isFood: the tag only (rotten food is refused only by mobInteract)
        this.food = tag -> stack -> stack != null && !stack.isEmpty() && tags.is(stack, AmphibianBrain.ours(tag));
        // TFCFrog.isFood: not rotten and #tfc:frog_food
        this.frogFood = stack -> stack != null && !stack.isEmpty() && !foodHooks.isRotten(stack)
                && tags.is(stack, AmphibianBrain.ours(FrogRules.FOOD_TAG));
        this.rotten = foodHooks::isRotten;
        this.ticks = () -> context.clock().get().playerTicks();
        this.temperature = l -> dev.tfp.paper.climate.ChunkClimateSource.climateAt(l.getWorld().getSeed(), l.getBlockX(),
                l.getBlockZ()).averageSeaLevelTemperature();
        TfpPlugin plugin = (TfpPlugin) context.plugin();
        plugin.animals().behaviours().register(new Ai());
        plugin.animals().behaviours().register(new FrogAi());
        context.listen(this);
    }

    @Override
    public void disable() {
        movers.values().forEach(Scheduler.TaskHandle::cancel);
        movers.clear();
        brains.clear();
    }

    /** Test seam: the world, the tags, the food, the clock, the climate and the draws. */
    void initForTest(ModuleContext context, AmphibianBody body, BiPredicate<String, String> speciesInTag,
            Function<String, Predicate<ItemStack>> food, Predicate<ItemStack> frogFood, Predicate<ItemStack> rotten,
            LongSupplier ticks, ToDoubleFunction<Location> temperature, RandomGenerator random) {
        this.ctx = context;
        this.body = body;
        this.speciesInTag = speciesInTag;
        this.food = food;
        this.frogFood = frogFood;
        this.rotten = rotten;
        this.ticks = ticks;
        this.temperature = temperature;
        this.random = random;
    }

    AmphibianBrain brain(Entity e) {
        return brains.get(e.getUniqueId());
    }

    SpeciesBehaviour behaviour() {
        return new Ai();
    }

    SpeciesBehaviour frogBehaviour() {
        return new FrogAi();
    }

    // ---- turtle, penguin, leopard seal ----------------------------------------------------------------------------

    /** The three {@code AmphibiousAnimal}s: TFC's brain is their only AI. */
    private final class Ai implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return AMPHIBIANS;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            AmphibianSpecies species = AmphibianSpecies.of(record.species()).orElseThrow();
            AmphibianBrain.Deps deps = new AmphibianBrain.Deps(body, speciesInTag, records, food.apply(species.foodTag()), random);
            AmphibianBrain brain = new AmphibianBrain(mob, species, deps);
            brain.home = readHome(mob);
            brains.put(mob.getUniqueId(), brain);
            boolean mover = species == AmphibianSpecies.PENGUIN;
            return List.of(new GoalSpec(0, new AmphibianGoal(brain, mover, () -> AmphibiousMover.movementSpeed(mob))));
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (!(entity instanceof Mob mob)) {
                return;
            }
            AmphibianSpecies.of(record.species()).ifPresent(s -> applyAttributes(mob, s.attributes()));
            AmphibianBrain brain = brains.get(mob.getUniqueId());
            if (brain != null && brain.home == null) {
                // finalizeSpawn: HOME is where the mob first appears
                brain.home = mob.getLocation().getBlock().getLocation();
                writeHome(mob, brain.home);
            }
        }
    }

    static Location readHome(Mob mob) {
        int[] h = mob.getPersistentDataContainer().get(HOME, PersistentDataType.INTEGER_ARRAY);
        return h == null || h.length != 3 ? null : new Location(mob.getWorld(), h[0], h[1], h[2]);
    }

    static void writeHome(Mob mob, Location home) {
        mob.getPersistentDataContainer().set(HOME, PersistentDataType.INTEGER_ARRAY,
                new int[] {home.getBlockX(), home.getBlockY(), home.getBlockZ()});
    }

    /** {@code createAttributes}; health is filled the first time the maximum changes (a fresh carrier). */
    static void applyAttributes(LivingEntity e, AmphibianSpecies.Attributes a) {
        set(e, Attribute.MOVEMENT_SPEED, a.movementSpeed());
        set(e, Attribute.ATTACK_DAMAGE, a.attackDamage());
        set(e, Attribute.STEP_HEIGHT, a.stepHeight());
        if (set(e, Attribute.MAX_HEALTH, a.maxHealth())) {
            e.setHealth(a.maxHealth());
        }
    }

    private static boolean set(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance i = e.getAttribute(attribute);
        if (i == null) {
            try {
                e.registerAttribute(attribute);
            } catch (RuntimeException unsupported) {
                // a carrier type that cannot take the attribute (and MockBukkit's mocks) keeps its own
                return false;
            }
            i = e.getAttribute(attribute);
        }
        if (i == null || i.getBaseValue() == value) {
            return false;
        }
        i.setBaseValue(value);
        return true;
    }

    /** {@code AmphibiousAnimal.hurt}: after the hit (MONITOR), arm the play-dead countdown; the sensor remembers the attacker. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        AmphibianBrain brain = brains.get(event.getEntity().getUniqueId());
        if (brain == null) {
            return;
        }
        boolean alive = brain.mob.getHealth() - event.getFinalDamage() > 0;
        brain.hurt((float) event.getDamage(), alive, causing(event));
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
     * {@code AmphibiousAnimal.mobInteract}: its food, not rotten, on a hurt mob heals one point and uses one item (TFC shrinks
     * the stack in creative too).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        AmphibianBrain brain = brains.get(target.getUniqueId());
        if (brain != null) {
            if (held != null && brain.d.food().test(held) && !rotten.test(held)) {
                double max = maxHealth(brain.mob);
                if (brain.mob.getHealth() < max) {
                    event.setCancelled(true);
                    brain.mob.setHealth(Math.min(max, brain.mob.getHealth() + 1.0));
                    held.setAmount(held.getAmount() - 1);
                }
            }
            return;
        }
        if (target instanceof Mob mob && isFrog(mob)) {
            frogInteract(event, mob, player, held);
        }
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance i = e.getAttribute(Attribute.MAX_HEALTH);
        return i == null ? 20.0 : i.getValue();
    }

    /** Amphibians and the crocodile do not drown in water ({@code canDrownInFluidType}). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAir(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || event.getAmount() >= living.getRemainingAir()
                || !living.isInWater()) {
            return;
        }
        Optional<AnimalRecord> r = records.apply(living);
        if (r.isPresent() && (AMPHIBIANS.contains(r.get().species()) || CROCODILE.equals(r.get().species()))) {
            event.setCancelled(true);
        }
    }

    // ---- crocodile water movement ----------------------------------------------------------------------------------

    /** The crocodile keeps PAR-FAUNA-10's goals; a per-mob task adds {@code TrueAmphibiousMoveControl} in water. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof Mob mob) || !records.apply(e).map(r -> CROCODILE.equals(r.species())).orElse(false)) {
            return;
        }
        UUID id = e.getUniqueId();
        Scheduler.TaskHandle h = ctx.scheduler().entityRepeating(mob, 1, 1,
                () -> AmphibiousMover.step(mob, 1.0, AmphibiousMover.movementSpeed(mob), false), () -> movers.remove(id));
        if (h != null) {
            Scheduler.TaskHandle old = movers.put(id, h);
            if (old != null) {
                old.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        brains.remove(id);
        Scheduler.TaskHandle h = movers.remove(id);
        if (h != null) {
            h.cancel();
        }
    }

    // ---- frog ------------------------------------------------------------------------------------------------------

    private boolean isFrog(Mob mob) {
        return records.apply(mob).map(r -> FROG.equals(r.species())).orElse(false);
    }

    /** The frog keeps the vanilla brain; {@code finalizeSpawn} picks its variant by climate once. */
    private final class FrogAi implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return Set.of(FROG);
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (!(entity instanceof Frog frog)) {
                return;
            }
            PersistentDataContainer pdc = frog.getPersistentDataContainer();
            if (pdc.has(FROG_VARIANT, PersistentDataType.STRING)) {
                return;
            }
            FrogRules.Variant v = FrogRules.variant((float) temperature.applyAsDouble(frog.getLocation()));
            frog.setVariant(switch (v) {
                case COLD -> Frog.Variant.COLD;
                case WARM -> Frog.Variant.WARM;
                case TEMPERATE -> Frog.Variant.TEMPERATE;
            });
            pdc.set(FROG_VARIANT, PersistentDataType.STRING, v.name());
        }
    }

    static FrogRules.State frogState(PersistentDataContainer pdc) {
        Float fam = pdc.get(FROG_FAMILIARITY, PersistentDataType.FLOAT);
        Long next = pdc.get(FROG_NEXT_FEED, PersistentDataType.LONG);
        return new FrogRules.State(fam == null ? 0f : fam, next == null ? Long.MIN_VALUE : next, Long.MIN_VALUE);
    }

    /**
     * {@code TFCFrog.mobInteract}: frog food (not rotten) past the next-feed time adds 0.1 familiarity, waits a day, uses the
     * item (not in creative) and plays the eat sound; before it the click is used up without eating. A slime ball, the
     * vanilla frog food, is not food to a TFC frog.
     */
    private void frogInteract(PlayerInteractEntityEvent event, Mob frog, Player player, ItemStack held) {
        if (held == null || held.isEmpty()) {
            return;
        }
        if (held.getType() == Material.SLIME_BALL) {
            event.setCancelled(true);
            return;
        }
        if (!frogFood.test(held)) {
            return;
        }
        event.setCancelled(true);
        PersistentDataContainer pdc = frog.getPersistentDataContainer();
        FrogRules.State before = frogState(pdc);
        FrogRules.State after = before.feed(ticks.getAsLong());
        if (after == before) {
            return;
        }
        pdc.set(FROG_FAMILIARITY, PersistentDataType.FLOAT, after.familiarity());
        pdc.set(FROG_NEXT_FEED, PersistentDataType.LONG, after.nextFeed());
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        frog.getWorld().playSound(frog.getLocation(), Sound.ENTITY_FROG_EAT, 1f, 1f);
    }
}
