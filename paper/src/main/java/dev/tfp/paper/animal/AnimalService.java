/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimalProperties.java @ c3b4d9536
 * (initCommonAnimalData, setGeneticSize, tickAnimalData),
 * src/main/java/net/dries007/tfc/common/entities/livestock/TFCAnimal.java (isBaby) and
 * src/main/java/net/dries007/tfc/common/entities/prey/WildAnimal.java (finalizeSpawn, tick, getAge), same commit
 */
package dev.tfp.paper.animal;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalScale;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.animal.ModelVariants;
import dev.tfp.core.animal.CarrierTable;
import dev.tfp.core.animal.WildAnimalRoll;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.EntityModels;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * TFC animals on vanilla carriers (M5-ENT-01): spawning, the stored record ({@link AnimalPdc}), the model and its
 * scale, and the staggered per-animal check that later goal AI (M5-LIV/WILD/PET) plugs into.
 *
 * <ul>
 *   <li><b>Spawn</b> ({@link #spawn}): the carrier from {@link CarrierTable} on the location's region thread, silent,
 *       persistent, no equipment, AI on; TFC's spawn roll ({@link AnimalSpawnRoll} for livestock,
 *       {@link WildAnimalRoll} for wild animals: gender, 10% baby, lazy grow-up tick) written to the PDC; the SCALE
 *       attribute set to TFC's {@code AGE_SCALES} as {@code setGeneticSize} does; the model attached.</li>
 *   <li><b>Scale</b>: BetterModel's entity scaler multiplies the SCALE attribute (M0-BM-01), so the model gets
 *       {@link AnimalScale#modelMultiplier} (TFC's render scale, times 0.5 for a child or a wild baby). The carrier's
 *       vanilla baby flag follows TFC's {@code isBaby} (age locked), which halves its hitbox as in TFC.</li>
 *   <li><b>Check</b>: an entity-scheduler task every {@value #PERIOD} ticks, first after {@code 1 + uuid hash mod 20}
 *       ticks so animals do not all run on one tick. It snapshots the PDC record and the clock and asks
 *       {@link AnimalCheck#decide} (pure, core), runs every species hook on that one record and writes it once at the
 *       end, only when the old-age mark is set or a hook changed it (PAR-FAUNA-28); it re-applies the scale only when
 *       the stage changed. Started on {@link EntityAddToWorldEvent} (spawn and chunk load), stopped
 *       on {@link EntityRemoveFromWorldEvent} and when the entity scheduler retires the task.</li>
 *   <li><b>Re-apply</b>: on every model (re)creation ({@link EntityModels#onModelCreated}), and on every model load
 *       ({@link EntityModels#onReady}) for carriers that loaded before the models existed.</li>
 * </ul>
 * Carrier goals: a species with a {@link SpeciesBehaviour} gets its goals in place of the vanilla ones on every add to a
 * world; other species keep the vanilla goals (PAR-FAUNA-21). Vanilla breeding is cancelled for our carriers (TFC breeds by its
 * own rules, M5-LIV).
 *
 * <p>Threads: the decision is computed on the entity's thread because it is a few comparisons; the snapshot and the write
 * happen there as well, so nothing crosses threads.
 */
public final class AnimalService implements Listener {
    /** The check period: TFC's {@code tickAnimalData} interval. */
    public static final long PERIOD = AnimalCheck.PERIOD_TICKS;

    private final Scheduler scheduler;
    private final GlobalClockHolder clock;
    private final Supplier<EntityModels> models;
    private final Logger logger;
    private final CarrierTable table = CarrierTable.get();
    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();
    private final SpeciesBehaviours behaviours;
    /** The id of the model attached by {@link #ensureModel}, so a changed look (age, gender) swaps it. */
    static final NamespacedKey MODEL_KEY = new NamespacedKey("tfp", "animal_model");

    /** One running check. {@code applied} is the stage last applied to the carrier (TFC's {@code lastAge}). */
    private static final class Tracked {
        final Entity entity;
        volatile Scheduler.TaskHandle task;
        volatile LivestockAge applied;
        volatile long checks;

        Tracked(Entity entity) {
            this.entity = entity;
        }
    }

    public AnimalService(Scheduler scheduler, GlobalClockHolder clock, Supplier<EntityModels> models, Logger logger) {
        this.scheduler = scheduler;
        this.clock = clock;
        this.models = models;
        this.logger = logger;
        this.behaviours = new SpeciesBehaviours(logger);
    }

    /** The registry FAUNA features register their {@link SpeciesBehaviour} in (goals, add and check hooks). */
    public SpeciesBehaviours behaviours() {
        return behaviours;
    }

    /** Registers the model hooks. Call once, after the integrations are enabled. */
    public void start() {
        models.get().onModelCreated(this::reapply);
        models.get().onReady(this::onModelsReady);
    }

    public CarrierTable table() {
        return table;
    }

    /** The record on {@code entity}, if it is one of our animals. Owning thread. */
    public Optional<AnimalRecord> record(Entity entity) {
        return AnimalPdc.read(entity.getPersistentDataContainer());
    }

    /** Whether the per-animal check of {@code entity} is running. Any thread. */
    public boolean checking(Entity entity) {
        return tracked.containsKey(entity.getUniqueId());
    }

    /** How many checks of {@code entity} ran since it was added to the world. Any thread. */
    public long checks(Entity entity) {
        Tracked t = tracked.get(entity.getUniqueId());
        return t == null ? 0 : t.checks;
    }

    // ---- spawn -------------------------------------------------------------------------------------

    /**
     * Spawns {@code carrier}'s species at {@code at}, on the location's region thread.
     *
     * @param hint the age to give it ({@code RANDOM} is TFC's roll; a wild animal's {@code CHILD} is a baby, and its
     *             {@code OLD} is an adult, as TFC's wild animals have no old age)
     * @param male the gender, or null for TFC's draw
     * @return the carrier entity; fails if the calendar is not loaded or the carrier type is not a living entity
     */
    public CompletableFuture<Entity> spawn(Location at, Carrier carrier, AnimalSpawnRoll.AgeHint hint, Boolean male) {
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return CompletableFuture.failedFuture(new IllegalStateException("the calendar is not loaded yet"));
        }
        return spawn(at, carrier, roll(carrier, now.playerTicks(), hint, male), now);
    }

    /**
     * Spawns a newborn of {@code carrier}'s species (PAR-FAUNA-05): TFC's {@code setBabyTraits} gives it a random gender,
     * today as its birth tick and the given familiarity; its genetic size is the usual spawn draw.
     */
    public CompletableFuture<Entity> spawnOffspring(Location at, Carrier carrier, float familiarity) {
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return CompletableFuture.failedFuture(new IllegalStateException("the calendar is not loaded yet"));
        }
        AnimalRecord drawn = roll(carrier, now.playerTicks(), AnimalSpawnRoll.AgeHint.CHILD, null);
        AnimalState child = AnimalState.offspring(drawn.state().male(), familiarity, now.playerTicks());
        return spawn(at, carrier, new AnimalRecord(carrier.id(), child, drawn.geneticSize(), ""), now);
    }

    private CompletableFuture<Entity> spawn(Location at, Carrier carrier, AnimalRecord record, ClockSnapshot now) {
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.fromString(carrier.carrier()));
        if (type == null || type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            return CompletableFuture.failedFuture(new IllegalStateException(carrier.carrier() + " is not a living entity type"));
        }
        LivestockAge age = AnimalCheck.ageAt(record, now.playerTicks());
        CompletableFuture<Entity> out = new CompletableFuture<>();
        scheduler.region(at, () -> {
            try {
                Entity e = at.getWorld().spawn(at, type.getEntityClass(), en -> prepare(en, record, age));
                ensureModel(e, record, age);
                out.complete(e);
            } catch (RuntimeException ex) {
                out.completeExceptionally(ex);
            }
        });
        return out;
    }

    /**
     * TFC's spawn roll: {@code initCommonAnimalData} for livestock, {@code WildAnimal.finalizeSpawn} (gender, baby,
     * lazy grow-up tick) for wild animals. Other species (TFC's vanilla-based fish, critters and pets without livestock
     * rules) keep only a gender.
     */
    AnimalRecord roll(Carrier carrier, long now, AnimalSpawnRoll.AgeHint hint, Boolean male) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (carrier.livestock().isEmpty()) {
            if (carrier.wildAnimal()) {
                WildAnimalRoll.Rolled w = WildAnimalRoll.roll(new WildAnimalRoll.Draws() {
                    @Override
                    public boolean nextBoolean() {
                        return rnd.nextBoolean();
                    }

                    @Override
                    public float nextFloat() {
                        return rnd.nextFloat();
                    }

                    @Override
                    public double nextDouble() {
                        return rnd.nextDouble();
                    }
                }, now, hint, male);
                return new AnimalRecord(carrier.id(), AnimalState.spawned(w.male(), now, now), AnimalScale.NO_SIZE, "",
                        w.baby(), w.growTick());
            }
            boolean m = male != null ? male : rnd.nextBoolean();
            return new AnimalRecord(carrier.id(), AnimalState.spawned(m, now, now), AnimalScale.NO_SIZE, "");
        }
        AnimalSpawnRoll.Draws draws = new AnimalSpawnRoll.Draws() {
            @Override
            public boolean nextBoolean() {
                return rnd.nextBoolean();
            }

            @Override
            public float nextFloat() {
                return rnd.nextFloat();
            }

            @Override
            public int nextInt(int bound) {
                return rnd.nextInt(bound);
            }
        };
        AnimalSpawnRoll.Rolled r = AnimalSpawnRoll.roll(draws, carrier.livestock().get().adulthoodDays(), now, hint, male);
        return new AnimalRecord(carrier.id(), r.state(), r.geneticSize(), "");
    }

    /** Before the carrier is added to the world (spawn consumer). */
    private void prepare(Entity e, AnimalRecord record, LivestockAge age) {
        e.setSilent(true);
        e.setPersistent(true);
        e.customName(null);
        if (e instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            mob.getEquipment().clear();
            mob.setAI(true);
        }
        AnimalPdc.write(e.getPersistentDataContainer(), record);
        applyCarrier(e, record, age); // not in the world yet: no model, no thread owner
    }

    // ---- scale and model ---------------------------------------------------------------------------

    /**
     * The SCALE attribute (TFC {@code setGeneticSize}), the vanilla baby flag (TFC {@code isBaby}) and the scale of any
     * attached model. Idempotent; owning thread.
     */
    private void applyBody(Entity e, AnimalRecord record, LivestockAge age) {
        applyCarrier(e, record, age);
        models.get().rescale(e, AnimalScale.modelMultiplier(record.geneticSize(), age));
    }

    /** The carrier part of {@link #applyBody}: SCALE attribute and baby flag. */
    private static void applyCarrier(Entity e, AnimalRecord record, LivestockAge age) {
        if (e instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(AnimalScale.ageScale(record.geneticSize()));
            }
        }
        if (e instanceof Ageable ageable) {
            if (age == LivestockAge.CHILD) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
            if (ageable instanceof Breedable breedable) {
                breedable.setAgeLock(true);
            }
        }
    }

    /** The model for the animal's gender and stage (PAR-FAUNA-03 variants), else the carrier table's model. */
    Optional<String> modelFor(Entity e, AnimalRecord record, LivestockAge age) {
        boolean sheared = clock.get().isSet() && ProductPdc.isSheared(e, record, clock.get().playerTicks());
        return ModelVariants.get().pick(record.species(), record.state().male(), age, sheared)
                .or(() -> table.find(record.species()).flatMap(Carrier::modelOrFallback));
    }

    /**
     * {@link #applyBody}, attaching the species model first if the carrier has none and models are loaded, and swapping it
     * when the animal's look changed (older, a different texture). The id of the model on the carrier is kept in its PDC.
     */
    private void ensureModel(Entity e, AnimalRecord record, LivestockAge age) {
        EntityModels m = models.get();
        Optional<String> modelId = modelFor(e, record, age);
        if (modelId.isPresent() && m.isReady()) {
            String id = modelId.get();
            String current = e.getPersistentDataContainer().get(MODEL_KEY, PersistentDataType.STRING);
            if (m.modelCount(e) > 0 && current != null && !current.equals(id)) {
                m.detach(e);
            }
            if (m.modelCount(e) == 0) {
                if (m.attach(e, id, AnimalScale.modelMultiplier(record.geneticSize(), age))) {
                    e.getPersistentDataContainer().set(MODEL_KEY, PersistentDataType.STRING, id);
                } else {
                    logger.warning("Animal " + e.getUniqueId() + ": model " + id + " is not loaded");
                }
            } else if (current == null) {
                e.getPersistentDataContainer().set(MODEL_KEY, PersistentDataType.STRING, id);
            }
        }
        applyBody(e, record, age);
    }

    /**
     * Re-evaluates which model the animal should wear (a shearing or the wool growing back changes it) and swaps it.
     * Owning thread; no-op for an entity that is not one of our animals.
     */
    public void refreshModel(Entity e) {
        record(e).ifPresent(r -> refreshModel(e, r));
    }

    /** {@link #refreshModel(Entity)} with the record already in hand (a check hook). Owning thread. */
    public void refreshModel(Entity e, AnimalRecord record) {
        ClockSnapshot now = clock.get();
        if (now.isSet()) {
            ensureModel(e, record, AnimalCheck.ageAt(record, now.playerTicks()));
        }
    }

    /**
     * Whether the model the carrier wears (its id kept in the PDC) differs from the one it should wear now, so a check
     * hook swaps it only when its look changed. Owning thread.
     */
    public boolean modelOutdated(Entity e, AnimalRecord record) {
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return false;
        }
        Optional<String> want = modelFor(e, record, AnimalCheck.ageAt(record, now.playerTicks()));
        String current = e.getPersistentDataContainer().get(MODEL_KEY, PersistentDataType.STRING);
        return want.isPresent() && !want.get().equals(current);
    }

    /** {@link EntityModels#onModelCreated}: the model plugin (re)created a model; owning thread. */
    private void reapply(Entity e) {
        Optional<AnimalRecord> record = record(e);
        ClockSnapshot now = clock.get();
        if (record.isEmpty() || !now.isSet()) {
            return;
        }
        LivestockAge age = AnimalCheck.ageAt(record.get(), now.playerTicks());
        applyBody(e, record.get(), age);
        Tracked t = tracked.get(e.getUniqueId());
        if (t != null) {
            t.applied = age;
        }
    }

    /** {@link EntityModels#onReady}: attach models to carriers that loaded before the models existed. */
    private void onModelsReady() {
        for (Tracked t : List.copyOf(tracked.values())) {
            scheduler.entity(t.entity, () -> {
                Optional<AnimalRecord> record = record(t.entity);
                ClockSnapshot now = clock.get();
                if (record.isPresent() && now.isSet()) {
                    LivestockAge age = AnimalCheck.ageAt(record.get(), now.playerTicks());
                    ensureModel(t.entity, record.get(), age);
                    t.applied = age;
                }
            }, null);
        }
    }

    // ---- the per-animal check ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        Entity e = event.getEntity();
        if (!AnimalPdc.has(e.getPersistentDataContainer())) {
            return;
        }
        UUID id = e.getUniqueId();
        Tracked t = new Tracked(e);
        Tracked previous = tracked.put(id, t);
        if (previous != null && previous.task != null) {
            previous.task.cancel();
        }
        if (e instanceof Mob mob) {
            record(e).ifPresent(r -> behaviours.onAdd(mob, e, r));
        }
        long delay = 1 + Math.floorMod(id.hashCode(), (int) PERIOD);
        Scheduler.TaskHandle handle = scheduler.entityRepeating(e, delay, PERIOD, () -> check(t),
                () -> tracked.remove(id, t));
        if (handle == null) {
            tracked.remove(id, t);
        } else {
            t.task = handle;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        Tracked t = tracked.remove(event.getEntity().getUniqueId());
        if (t != null && t.task != null) {
            t.task.cancel();
        }
    }

    /** TFC breeds livestock by its own rules (M5-LIV); vanilla love mode would make vanilla babies without a record. */
    @EventHandler(ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (AnimalPdc.has(event.getEntity().getPersistentDataContainer())) {
            event.setCancelled(true);
        }
    }

    /** Where a check reads and writes the carrier's record: its PDC in the server, a counter in tests. */
    interface RecordStore {
        Optional<AnimalRecord> read(Entity e);

        void write(Entity e, AnimalRecord record);

        RecordStore PDC = new RecordStore() {
            @Override
            public Optional<AnimalRecord> read(Entity e) {
                return AnimalPdc.read(e.getPersistentDataContainer());
            }

            @Override
            public void write(Entity e, AnimalRecord record) {
                AnimalPdc.write(e.getPersistentDataContainer(), record);
            }
        };
    }

    /** What one check body did: no record (stop the check), not run (calendar unset), or ran with the stage applied. */
    record CheckOutcome(boolean stop, boolean ran, LivestockAge applied) {
    }

    /** One staggered check, on the carrier's thread: snapshot, decide (core), write only what changed. */
    private void check(Tracked t) {
        Entity e = t.entity;
        try {
            CheckOutcome out = checkBody(e, t.applied, RecordStore.PDC);
            if (out.stop()) {
                stop(t);
                return;
            }
            if (out.ran()) {
                t.applied = out.applied();
                t.checks++;
            }
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Animal check failed for " + e.getUniqueId() + "; stopping it", ex);
            stop(t);
        }
    }

    /**
     * The check body (PAR-FAUNA-28): reads the record once, runs the age step and every species hook on that one record
     * ({@link SpeciesBehaviours#update} collects their changes), and writes it once at the end, only when the age step
     * or a hook changed it. Owning thread.
     *
     * @param applied the stage last applied to the carrier, null before the first check after an add
     */
    CheckOutcome checkBody(Entity e, LivestockAge applied, RecordStore store) {
        Optional<AnimalRecord> record = store.read(e);
        if (record.isEmpty()) {
            return new CheckOutcome(true, false, applied);
        }
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return new CheckOutcome(false, false, applied);
        }
        int dayDraw = ThreadLocalRandom.current().nextInt(LivestockRules.OLD_DAY_DRAWS);
        AnimalCheck.Decision d = AnimalCheck.decide(record.get(), now.playerTicks(), dayDraw, applied);
        // Carriers can come back from a restart with NoAI (M0-BM-01), so the first check after load restores AI. It
        // is deliberately not re-checked later: NoAI set afterwards comes from an operator or another plugin
        // (/data, a freeze command) and is theirs to undo.
        if (applied == null && e instanceof Mob mob && !mob.hasAI()) {
            mob.setAI(true);
        }
        LivestockAge nowApplied = applied;
        if (d.stageChanged()) {
            ensureModel(e, d.record(), d.age());
            nowApplied = d.age();
        }
        SpeciesBehaviours.Pending pending = behaviours.dispatchCheck(
                new SpeciesBehaviour.Check(e, d.record(), d.age(), now.playerTicks()), d.recordChanged());
        if (pending.changed) {
            store.write(e, pending.current);
        }
        return new CheckOutcome(false, true, nowApplied);
    }

    private void stop(Tracked t) {
        tracked.remove(t.entity.getUniqueId(), t);
        if (t.task != null) {
            t.task.cancel();
        }
    }

    // ---- diagnostics -------------------------------------------------------------------------------

    /**
     * {@code /tfp debug animal info} lines for {@code e}, in {@code locale} ({@code lang/*.properties},
     * {@code debug.animal.info.*}); owning thread. Empty if it is not one of our animals.
     */
    public Optional<List<String>> describe(Entity e, Messages messages, String locale) {
        Optional<AnimalRecord> record = record(e);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        AnimalRecord r = record.get();
        ClockSnapshot now = clock.get();
        AnimalState s = r.state();
        List<String> lines = new ArrayList<>();
        Optional<Carrier> carrier = table.find(r.species());
        LivestockAge age = AnimalCheck.ageAt(r, now.playerTicks());
        lines.add(messages.get(locale, "debug.animal.info.gender_age", gender(messages, locale, s.male()),
                ageName(messages, locale, age),
                now.isSet() ? "" : messages.get(locale, "debug.animal.info.calendar_unset")));
        lines.add(messages.get(locale, "debug.animal.info.size",
                r.geneticSize() == AnimalScale.NO_SIZE ? messages.get(locale, "debug.animal.info.size_none")
                        : r.geneticSize(),
                AnimalScale.ageScale(r.geneticSize()), AnimalScale.modelMultiplier(r.geneticSize(), age),
                AnimalScale.modelScale(r.geneticSize(), age)));
        if (e instanceof LivingEntity living && living.getAttribute(Attribute.SCALE) != null) {
            lines.add(messages.get(locale, "debug.animal.info.scale_now",
                    living.getAttribute(Attribute.SCALE).getBaseValue()));
        }
        lines.add(messages.get(locale, "debug.animal.info.ticks", s.birthTick(), s.oldTick(), now.playerTicks()));
        if (r.baby()) {
            lines.add(messages.get(locale, "debug.animal.info.wild_baby", r.growTick()));
        }
        carrier.flatMap(Carrier::livestock).ifPresent(sp -> lines.add(messages.get(locale,
                "debug.animal.info.livestock", LivestockRules.familiarityAt(s, now.playerTicks()), s.uses(),
                sp.adulthoodDays())));
        lines.add(messages.get(locale, "debug.animal.info.model",
                modelFor(e, r, age).orElseGet(() -> messages.get(locale,
                        "debug.animal.info.model_none")),
                models.get().modelCount(e),
                checking(e) ? messages.get(locale, "debug.animal.info.check_running", checks(e))
                        : messages.get(locale, "debug.animal.info.check_stopped")));
        return Optional.of(lines);
    }

    /** The translated gender word ({@code debug.animal.male|female}). */
    public static String gender(Messages messages, String locale, boolean male) {
        return messages.get(locale, male ? "debug.animal.male" : "debug.animal.female");
    }

    /** The translated stage word ({@code debug.animal.age.child|adult|old}). */
    public static String ageName(Messages messages, String locale, LivestockAge age) {
        return messages.get(locale, "debug.animal.age." + age.name().toLowerCase(java.util.Locale.ROOT));
    }
}
