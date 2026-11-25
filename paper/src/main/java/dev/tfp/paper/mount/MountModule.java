/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/HorseProperties.java @ c3b4d9536 (createGenes, applyGenes,
 * tickAnimalData), TFCHorse.java (mobInteract, createGenes, applyGenes, canMate), TFCDonkey.java and TFCMule.java
 * (getEntityTypeForBaby, canMate), TFCChestedHorse.java (mobInteract, containerChanged, canBeLeashed, tick)
 */
package dev.tfp.paper.mount;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.item.Burden;
import dev.tfp.core.item.BurdenEffects;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.BreedingRules;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.mount.HorseColour;
import dev.tfp.core.mount.MountBreeding;
import dev.tfp.core.mount.MountGenes;
import dev.tfp.core.mount.MountInteract;
import dev.tfp.core.mount.MountRandom;
import dev.tfp.core.mount.MountRules;
import dev.tfp.core.mount.MountStats;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.animal.SpeciesBehaviours;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;


/**
 * Horses, donkeys and mules (PAR-FAUNA-15), on their vanilla carriers. TFC's rules:
 * <ul>
 *   <li>stats: a new animal is rolled with {@link MountStats#random} (max health, jump, speed) and a horse with a random
 *       colour; the stats are kept in the PDC ({@link MountPdc}) and set as the carrier's base attributes;</li>
 *   <li>taming is familiarity (above 0.15): a click on a tamed adult sets the owner and lets vanilla mount, saddle or open
 *       it; an untamed adult is angry and not mounted; a baby is left to vanilla ({@link MountInteract});</li>
 *   <li>breeding: horse with horse, horse with donkey and donkey with donkey, both tamed, adult and unridden, a mule never
 *       ({@link MountBreeding}); the female keeps the parents' genes until she gives birth and the foal's species (a mule
 *       from horse and donkey), stats and colour are drawn from them ({@link MountGenes});</li>
 *   <li>an old animal moves at half speed; a rider with the overburdened effect is thrown off; a donkey or mule that
 *       carries a very heavy huge item in its chest cannot move and cannot be leashed.</li>
 * </ul>
 *
 * <p>Threads: the interaction, the foal and every check run on the animal's owning thread; the pure rules are in
 * {@code dev.tfp.core.mount}. Only the checks scan the surroundings (the female's mate search, 8 blocks, once per check).
 */
public final class MountModule implements FeatureModule, Listener {
    /** The species this module runs; camels are {@code dev.tfp.paper.camel}. */
    public static final Set<String> SPECIES = Set.of("tfc:horse", "tfc:donkey", "tfc:mule");
    /** TFC's mating goal looks 8 blocks around; a foal finds its mother within this range. */
    static final double MATE_RANGE = 8.0;
    static final double MOTHER_RANGE = 4.0;
    /** A newborn is at most this many player ticks old. */
    static final long NEWBORN_TICKS = 200;
    /** The overburdened modifiers of a loaded donkey or mule ({@code BurdenApplier}'s values, the animal's own key). */
    static final NamespacedKey BURDEN = new NamespacedKey("tfp", "mount_burden");

    private ModuleContext ctx;
    private AnimalService animals;
    private java.util.function.Function<Entity, Optional<AnimalRecord>> records;
    private java.util.function.Function<String, Optional<Carrier>> carriers;
    private ItemSizes sizes;
    private Supplier<ClockSnapshot> clock;
    private BiFunction<Player, String, String> messages;
    private java.util.function.BiConsumer<Entity, Runnable> onEntity;
    private Spawner spawner;
    private final MountRandom random = new MountRandom() {
        @Override
        public double nextDouble() {
            return ThreadLocalRandom.current().nextDouble();
        }

        @Override
        public int nextInt(int bound) {
            return ThreadLocalRandom.current().nextInt(bound);
        }
    };

    /** Spawns a newborn of a carrier's species (the animal service's {@code spawnOffspring}; a recorder in tests). */
    @FunctionalInterface
    interface Spawner {
        java.util.concurrent.CompletableFuture<Entity> spawn(org.bukkit.Location at, Carrier carrier, float familiarity);
    }

    /**
     * True for the species this module owns end to end: pairing, genes and birth (PAR-FAUNA-34). The livestock check
     * leaves them alone, so no horse-family pair is made and no foal is born without the tamed and unridden gate and the
     * genes. Camels are the livestock check's, with the unridden gate of {@link MountBreeding#canMate}.
     */
    public static boolean owns(LivestockSpecies species) {
        return species == LivestockSpecies.HORSE || species == LivestockSpecies.DONKEY || species == LivestockSpecies.MULE;
    }

    @Override
    public String id() {
        return "mount";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        this.animals = ((TfpPlugin) context.plugin()).animals();
        this.sizes = context.services().require(ItemSizes.class);
        this.clock = context.clock()::get;
        this.messages = (p, key) -> context.messages().get(p.locale().toString().toLowerCase(Locale.ROOT), key);
        this.onEntity = (e, task) -> context.scheduler().entity(e, task, () -> {
        });
        this.records = animals::record;
        this.carriers = id -> animals.table().find(id);
        this.spawner = animals::spawnOffspring;
        animals.behaviours().register(behaviour());
        context.listen(this);
    }

    /** Test seam: where a newborn is spawned. */
    void spawner(Spawner spawner) {
        this.spawner = spawner;
    }

    /** Test seam: the animal service pieces, the clock and the thread hand-off. */
    void initForTest(java.util.function.Function<Entity, Optional<AnimalRecord>> records, ItemSizes sizes,
            Supplier<ClockSnapshot> clock, BiFunction<Player, String, String> messages) {
        this.records = records;
        this.carriers = id -> dev.tfp.core.animal.CarrierTable.get().find(id);
        this.sizes = sizes;
        this.clock = clock;
        this.messages = messages;
        this.onEntity = (e, task) -> task.run();
    }

    SpeciesBehaviour behaviour() {
        return new SpeciesBehaviour() {
            @Override
            public Set<String> species() {
                return SPECIES;
            }

            @Override
            public boolean keepVanillaGoals() {
                return true;
            }

            @Override
            public void onAdd(Entity entity, AnimalRecord record) {
                initialise(entity, record);
            }

            @Override
            public void onCheck(Check check) {
                MountModule.this.check(check);
            }
        };
    }

    // ---- stats and foals -----------------------------------------------------------------------------

    static Optional<LivestockSpecies> species(String id) {
        try {
            return Optional.of(LivestockSpecies.valueOf(id.substring(id.indexOf(':') + 1).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Add-to-world: an animal with no stats yet gets them (a newborn from its mother's genes, any other by TFC's roll). */
    void initialise(Entity entity, AnimalRecord record) {
        if (!(entity instanceof LivingEntity) || MountPdc.hasStats(entity.getPersistentDataContainer())) {
            return;
        }
        ClockSnapshot now = clock.get();
        Optional<LivestockSpecies> sp = species(record.species());
        if (!now.isSet() || sp.isEmpty()) {
            return;
        }
        boolean newborn = now.playerTicks() - record.state().birthTick() < NEWBORN_TICKS
                && LivestockRules.ageAt(record.state(), sp.get(), now.playerTicks()) == LivestockAge.CHILD;
        if (newborn) {
            onEntity.accept(entity, () -> resolveFoal(entity, record, sp.get()));
        } else {
            apply(entity, MountStats.random(random), horseColour(sp.get()));
        }
    }

    private Optional<HorseColour> horseColour(LivestockSpecies sp) {
        return sp == LivestockSpecies.HORSE
                ? Optional.of(new HorseColour(random.nextInt(HorseColour.VARIANT_COUNT),
                        random.nextInt(HorseColour.MARKINGS_COUNT)))
                : Optional.empty();
    }

    /** A newborn: its mother is the nearest female of the family with genes; with none it is a plain spawn. */
    void resolveFoal(Entity foal, AnimalRecord record, LivestockSpecies own) {
        if (!foal.isValid() || MountPdc.hasStats(foal.getPersistentDataContainer())) {
            return;
        }
        Entity mother = null;
        double best = Double.MAX_VALUE;
        for (Entity other : foal.getNearbyEntities(MOTHER_RANGE, MOTHER_RANGE, MOTHER_RANGE)) {
            if (other == foal || !Bukkit.isOwnedByCurrentRegion(other)
                    || MountPdc.genes(other.getPersistentDataContainer()).isEmpty()) {
                continue;
            }
            double d = other.getLocation().distanceSquared(foal.getLocation());
            if (d < best) {
                best = d;
                mother = other;
            }
        }
        if (mother == null) {
            apply(foal, MountStats.random(random), horseColour(own));
            return;
        }
        MountGenes genes = MountPdc.genes(mother.getPersistentDataContainer()).orElseThrow();
        MountPdc.clearGenes(mother.getPersistentDataContainer());
        MountGenes.Foal drawn = genes.inherit(random);
        // the foal was born as the genes' species (giveBirth); a foal of another species (another pair's genes in reach) keeps
        // the stats but draws its own colour
        apply(foal, drawn.stats(), drawn.species() == own ? drawn.colour() : horseColour(own));
    }

    /** Sets the base attributes and, for a horse, the colour; keeps the values in the PDC. Entity thread. */
    static void apply(Entity entity, MountStats stats, Optional<HorseColour> colour) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        setBase(living, Attribute.MAX_HEALTH, stats.maxHealth());
        setBase(living, Attribute.JUMP_STRENGTH, stats.jumpStrength());
        setBase(living, Attribute.MOVEMENT_SPEED, stats.movementSpeed());
        living.setHealth(stats.maxHealth());
        if (living instanceof Horse horse && colour.isPresent()) {
            Horse.Color[] colours = Horse.Color.values();
            Horse.Style[] styles = Horse.Style.values();
            horse.setColor(colours[colour.get().variant()]);
            horse.setStyle(styles[colour.get().markings()]);
        }
        MountPdc.writeStats(entity.getPersistentDataContainer(), stats);
    }

    private static void setBase(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance a = e.getAttribute(attribute);
        if (a != null) {
            a.setBaseValue(value);
        }
    }

    private Optional<MountStats> statsOf(Entity e) {
        return MountPdc.stats(e.getPersistentDataContainer());
    }

    private static Optional<HorseColour> colourOf(Entity e) {
        return e instanceof Horse h
                ? Optional.of(new HorseColour(h.getColor().ordinal(), h.getStyle().ordinal())) : Optional.empty();
    }

    // ---- checks --------------------------------------------------------------------------------------

    void check(SpeciesBehaviour.Check check) {
        if (!(check.entity() instanceof LivingEntity mount)) {
            return;
        }
        ClockSnapshot now = clock.get();
        MountSupport.applyOldAge(mount, check.age());
        MountSupport.rejectOverburdened(mount, messages);
        loadBurden(mount);
        if (mount instanceof AbstractHorse horse && horse.isTamed()
                && !MountRules.isTamed(LivestockRules.familiarityAt(SpeciesBehaviours.current(check).state(),
                        check.playerTicks()))) {
            horse.setTamed(false); // TFC: tamed is only familiarity above 0.15
        }
        if (!now.isSet()) {
            return;
        }
        AnimalRecord record = SpeciesBehaviours.current(check);
        if (!record.state().male()) {
            if (record.state().fertilized()) {
                ensureGenes(check, record);
                giveBirth(check, record, now);
            } else {
                mate(check, record, now);
            }
        }
    }

    /**
     * TFC {@code MammalProperties.tickAnimalData}: a pregnant horse or donkey whose gestation is due gives birth. The foal's
     * species is the genes' (a mule from a horse and a donkey), spawned as that carrier at once; its stats and colour are
     * drawn from the genes when it is added ({@link #resolveFoal}).
     */
    void giveBirth(SpeciesBehaviour.Check check, AnimalRecord record, ClockSnapshot now) {
        Optional<LivestockSpecies> sp = species(record.species());
        Entity mother = check.entity();
        if (sp.isEmpty() || !MountBreeding.isHorseFamily(sp.get())) {
            return;
        }
        Optional<MountGenes> genes = MountPdc.genes(mother.getPersistentDataContainer());
        if (genes.isEmpty()) {
            return;
        }
        Optional<BreedingRules.Birth> birth = BreedingRules.birth(record.state(), sp.get(), now.playerTicks(),
                now.calendarTicks(), now.playerTicks(), random.nextInt(sp.get().childCount()));
        if (birth.isEmpty()) {
            return;
        }
        Optional<Carrier> own = carriers.apply(record.species());
        Carrier carrier = carriers.apply("tfc:" + genes.get().foal().name().toLowerCase(Locale.ROOT)).or(() -> own).orElse(null);
        if (carrier == null) {
            return;
        }
        SpeciesBehaviours.update(check, record.withState(birth.get().mother()));
        for (int i = 0; i < birth.get().kids(); i++) {
            spawner.spawn(mother.getLocation(), carrier, birth.get().offspringFamiliarity());
        }
    }

    /** TFC's overburdened chest: a donkey or mule that carries a very heavy huge item cannot move. */
    void loadBurden(LivingEntity mount) {
        if (!(mount instanceof ChestedHorse chested)) {
            return;
        }
        loadBurden(mount, chested.isCarryingChest() ? java.util.Arrays.asList(chested.getInventory().getContents())
                : List.of());
    }

    /** The burden of {@code contents} on {@code mount}: immobilised for one very heavy huge item or more. */
    void loadBurden(LivingEntity mount, List<ItemStack> contents) {
        List<dev.tfp.core.item.ItemSizing> carried = new ArrayList<>();
        for (ItemStack s : contents) {
            if (s != null && !s.getType().isAir()) {
                carried.add(sizes.sizing(s));
            }
        }
        boolean heavy = Burden.of(carried).isNonZero();
        modifier(mount.getAttribute(Attribute.MOVEMENT_SPEED), heavy, BurdenEffects.OVERBURDENED_SPEED_MODIFIER);
        modifier(mount.getAttribute(Attribute.JUMP_STRENGTH), heavy, -1.0);
    }

    private static void modifier(AttributeInstance attribute, boolean on, double amount) {
        if (attribute == null) {
            return;
        }
        boolean present = attribute.getModifier(BURDEN) != null;
        if (on && !present) {
            attribute.addTransientModifier(new AttributeModifier(BURDEN, amount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        } else if (!on && present) {
            attribute.removeModifier(BURDEN);
        }
    }

    /** TFC {@code canBeLeashed}: not while the animal is overburdened. */
    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity e && e.getAttribute(Attribute.MOVEMENT_SPEED) != null
                && e.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(BURDEN) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * TFC's mating goal for a ready female: a ready male of the family within range, both tamed, adult and unridden
     * ({@link MountBreeding#canMate}); the female is fertilized and keeps the genes.
     */
    private void mate(SpeciesBehaviour.Check check, AnimalRecord record, ClockSnapshot now) {
        Optional<LivestockSpecies> femaleSp = species(record.species());
        if (femaleSp.isEmpty() || !MountBreeding.isHorseFamily(femaleSp.get())
                || !MountBreeding.isReadyToMate(record.state(), femaleSp.get(), now.playerTicks())) {
            return;
        }
        Entity female = check.entity();
        for (Entity other : female.getNearbyEntities(MATE_RANGE, MATE_RANGE / 2, MATE_RANGE)) {
            if (other == female || !SPECIES.contains(speciesId(other)) || !Bukkit.isOwnedByCurrentRegion(other)) {
                continue;
            }
            Optional<AnimalRecord> maybe = records.apply(other);
            Optional<LivestockSpecies> maleSp = maybe.flatMap(r -> species(r.species()));
            if (maybe.isEmpty() || maleSp.isEmpty() || !maybe.get().state().male()) {
                continue;
            }
            MountBreeding.Mate a = new MountBreeding.Mate(record.state(), femaleSp.get(), MountSupport.ridden(female));
            MountBreeding.Mate b = new MountBreeding.Mate(maybe.get().state(), maleSp.get(), MountSupport.ridden(other));
            if (!MountBreeding.canMate(a, b, now.playerTicks())) {
                continue;
            }
            BreedingRules.Pairing pairing = BreedingRules.fertilize(record.state(), maybe.get().state(), femaleSp.get(),
                    now.playerTicks(), now.calendarTicks());
            SpeciesBehaviours.update(check, record.withState(pairing.female()));
            AnimalPdc.write(other.getPersistentDataContainer(), maybe.get().withState(pairing.male()));
            storeGenes(female, femaleSp.get(), other, maleSp.get());
            return;
        }
    }

    private static String speciesId(Entity e) {
        return "tfc:" + e.getType().name().toLowerCase(Locale.ROOT);
    }

    private void storeGenes(Entity female, LivestockSpecies femaleSp, Entity male, LivestockSpecies maleSp) {
        MountStats fs = statsOf(female).orElseGet(() -> MountStats.random(random));
        MountStats ms = statsOf(male).orElseGet(() -> MountStats.random(random));
        Optional<HorseColour> fc = femaleSp == LivestockSpecies.HORSE ? Optional.of(colourOf(female)
                .orElse(new HorseColour(0, 0))) : Optional.empty();
        Optional<HorseColour> mc = maleSp == LivestockSpecies.HORSE ? Optional.of(colourOf(male)
                .orElse(new HorseColour(0, 0))) : Optional.empty();
        MountPdc.writeGenes(female.getPersistentDataContainer(), new MountGenes(
                new MountGenes.Parent(femaleSp, fs, fc), new MountGenes.Parent(maleSp, ms, mc)));
    }

    /**
     * A female fertilized by another module's pairing (the livestock check pairs two of one species) has no genes yet:
     * the nearest male of her species within range stands for the father.
     */
    private void ensureGenes(SpeciesBehaviour.Check check, AnimalRecord record) {
        Entity female = check.entity();
        if (MountPdc.genes(female.getPersistentDataContainer()).isPresent()) {
            return;
        }
        Optional<LivestockSpecies> femaleSp = species(record.species());
        if (femaleSp.isEmpty() || !MountBreeding.isHorseFamily(femaleSp.get())) {
            return;
        }
        Entity father = null;
        double best = Double.MAX_VALUE;
        for (Entity other : female.getNearbyEntities(MATE_RANGE, MATE_RANGE / 2, MATE_RANGE)) {
            if (other == female || !Bukkit.isOwnedByCurrentRegion(other)
                    || !speciesId(other).equals(record.species())) {
                continue;
            }
            Optional<AnimalRecord> maybe = records.apply(other);
            if (maybe.isEmpty() || !maybe.get().state().male()) {
                continue;
            }
            double d = other.getLocation().distanceSquared(female.getLocation());
            if (d < best) {
                best = d;
                father = other;
            }
        }
        storeGenes(female, femaleSp.get(), father != null ? father : female, femaleSp.get());
    }

    // ---- interaction ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }
        Optional<AnimalRecord> record = records.apply(horse);
        Optional<LivestockSpecies> sp = record.flatMap(r -> species(r.species()));
        ClockSnapshot now = clock.get();
        if (record.isEmpty() || sp.isEmpty() || !SPECIES.contains(record.get().species()) || !now.isSet()) {
            return;
        }
        AnimalState state = record.get().state();
        LivestockAge age = LivestockRules.ageAt(state, sp.get(), now.playerTicks());
        MountInteract.Decision decision = MountInteract.decide(age,
                LivestockRules.familiarityAt(state, now.playerTicks()));
        Player player = event.getPlayer();
        switch (decision) {
            case PASS -> {
            }
            case ANGRY -> {
                event.setCancelled(true);
                MountSupport.angry(horse);
            }
            case MOUNT -> {
                if (!horse.isTamed()) {
                    horse.setTamed(true);
                }
                if (horse.getOwner() == null) {
                    horse.setOwner(player); // TFC tameWithName
                }
            }
        }
    }
}
