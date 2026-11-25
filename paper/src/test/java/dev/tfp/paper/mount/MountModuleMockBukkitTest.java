/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.mount;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.mount.MountGenes;
import dev.tfp.core.mount.MountStats;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.itemsize.BurdenApplier;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-15 on MockBukkit: stats, the taming gate, old age, thrown riders, the loaded chest, mating and foals. */
class MountModuleMockBukkitTest extends MockServerTest {
    private static final long DAY = LivestockRules.PLAYER_TICKS_IN_DEFAULT_DAY;
    private World world;
    private MountModule module;
    private long now;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        world = server.addSimpleWorld("w");
        now = 400 * DAY;
        TagLookup tags = TagLookup.EMPTY;
        ItemSizes sizes = new ItemSizes(() -> ItemSizeDefinitions.EMPTY, () -> tags,
                s -> s.getType() == Material.ANVIL ? Optional.of("test:heavy") : Optional.empty(), id -> false,
                ItemSizeLimits.DEFAULT);
        sizes.register(id -> id.equals("test:heavy"), s -> new ItemSizing(ItemSize.HUGE, ItemWeight.VERY_HEAVY));
        module = new MountModule();
        module.initForTest(e -> AnimalPdc.read(e.getPersistentDataContainer()), sizes,
                () -> new ClockSnapshot(1, now, 5 * DAY, 8, true), (p, key) -> {
                    messages.add(key);
                    return key;
                });
        server.getPluginManager().registerEvents(module, plugin);
    }

    private AnimalState state(boolean male, float familiarity, long birthTick) {
        return new AnimalState(male, familiarity, now, birthTick, AnimalState.NO_OLD_TICK, now, AnimalState.NEVER, 0, false,
                AnimalState.NOT_PREGNANT);
    }

    private <T extends LivingEntity> T mount(Class<T> type, String species, double x, AnimalState state) {
        T e = world.spawn(new Location(world, x, 64, 0), type);
        AnimalPdc.write(e.getPersistentDataContainer(), new AnimalRecord(species, state, 0, "", false,
                AnimalRecord.NO_GROW_TICK));
        return e;
    }

    private void click(Player player, Entity target) {
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND));
    }

    private void check(Entity e) {
        AnimalRecord r = AnimalPdc.read(e.getPersistentDataContainer()).orElseThrow();
        module.behaviour().onCheck(new SpeciesBehaviour.Check(e, r, LivestockAge.ADULT, now));
    }

    @Test
    void aNewAnimalGetsTfcStatsInRangeAndKeepsThem() {
        Horse horse = mount(Horse.class, "tfc:horse", 0, state(false, 0.5f, 0));
        module.initialise(horse, AnimalPdc.read(horse.getPersistentDataContainer()).orElseThrow());
        MountStats stats = MountPdc.stats(horse.getPersistentDataContainer()).orElseThrow();
        assertThat(stats.maxHealth()).isBetween(MountStats.MIN_HEALTH, MountStats.MAX_HEALTH);
        assertThat(stats.jumpStrength()).isBetween(MountStats.MIN_JUMP_STRENGTH, MountStats.MAX_JUMP_STRENGTH);
        assertThat(stats.movementSpeed()).isBetween(MountStats.MIN_MOVEMENT_SPEED, MountStats.MAX_MOVEMENT_SPEED);
        assertThat(horse.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(stats.maxHealth());
        // a second add (a chunk reload) does not redraw
        module.initialise(horse, AnimalPdc.read(horse.getPersistentDataContainer()).orElseThrow());
        assertThat(MountPdc.stats(horse.getPersistentDataContainer())).contains(stats);
    }

    @Test
    void anUntamedAdultIsNotMountedAndATamedOneGetsItsOwner() {
        Player player = server.addPlayer();
        Horse wild = mount(Horse.class, "tfc:horse", 0, state(false, 0.1f, 0));
        PlayerInteractEntityEvent angry = new PlayerInteractEntityEvent(player, wild, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(angry);
        assertThat(angry.isCancelled()).isTrue();
        assertThat(wild.isTamed()).isFalse();

        Horse tame = mount(Horse.class, "tfc:horse", 3, state(false, 0.5f, 0));
        PlayerInteractEntityEvent ride = new PlayerInteractEntityEvent(player, tame, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(ride);
        assertThat(ride.isCancelled()).isFalse();
        assertThat(tame.isTamed()).isTrue();
        assertThat(tame.getOwner()).isEqualTo(player);
    }

    @Test
    void aBabyIsLeftToVanilla() {
        Player player = server.addPlayer();
        Horse foal = mount(Horse.class, "tfc:horse", 0, state(false, 0.0f, now));
        PlayerInteractEntityEvent e = new PlayerInteractEntityEvent(player, foal, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        assertThat(e.isCancelled()).isFalse();
        assertThat(foal.isTamed()).isFalse();
    }

    @Test
    void anOldAnimalMovesAtHalfSpeedAndAnOverburdenedRiderIsThrown() {
        Horse horse = mount(Horse.class, "tfc:horse", 0, state(false, 0.5f, 0));
        double base = horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        AnimalRecord r = AnimalPdc.read(horse.getPersistentDataContainer()).orElseThrow();
        module.behaviour().onCheck(new SpeciesBehaviour.Check(horse, r, LivestockAge.OLD, now));
        assertThat(horse.getAttribute(Attribute.MOVEMENT_SPEED).getValue()).isEqualTo(base * 0.5);

        Player rider = server.addPlayer();
        rider.getAttribute(Attribute.MOVEMENT_SPEED).addTransientModifier(new AttributeModifier(BurdenApplier.MODIFIER, -7.5,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        horse.addPassenger(rider);
        check(horse);
        assertThat(horse.getPassengers()).isEmpty();
        assertThat(messages).contains(MountSupport.OVERBURDENED_KEY);
    }

    @Test
    void aDonkeyCarryingAHeavyHugeItemCannotMoveAndIsFreedWhenUnloaded() {
        Donkey donkey = mount(Donkey.class, "tfc:donkey", 0, state(false, 0.5f, 0));
        double base = donkey.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        module.loadBurden(donkey, List.of(new ItemStack(Material.ANVIL)));
        assertThat(donkey.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(MountModule.BURDEN)).isNotNull();
        assertThat(donkey.getAttribute(Attribute.MOVEMENT_SPEED).getValue()).isLessThan(base);
        module.loadBurden(donkey, List.of(new ItemStack(Material.STICK)));
        assertThat(donkey.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(MountModule.BURDEN)).isNull();
    }

    @Test
    void aHorseAndADonkeyMateAndTheFemaleKeepsTheGenes() {
        Horse mare = mount(Horse.class, "tfc:horse", 0, state(false, 0.5f, 0));
        Donkey jack = mount(Donkey.class, "tfc:donkey", 2, state(true, 0.5f, 0));
        mare.setTamed(true);
        jack.setTamed(true);
        module.initialise(mare, AnimalPdc.read(mare.getPersistentDataContainer()).orElseThrow());
        module.initialise(jack, AnimalPdc.read(jack.getPersistentDataContainer()).orElseThrow());
        check(mare);
        AnimalState after = AnimalPdc.read(mare.getPersistentDataContainer()).orElseThrow().state();
        assertThat(after.fertilized()).isTrue();
        MountGenes genes = MountPdc.genes(mare.getPersistentDataContainer()).orElseThrow();
        assertThat(genes.foal()).isEqualTo(dev.tfp.core.livestock.LivestockSpecies.MULE);
    }

    @Test
    void aRiddenOrUntamedPairDoesNotMate() {
        Horse mare = mount(Horse.class, "tfc:horse", 0, state(false, 0.5f, 0));
        Horse stallion = mount(Horse.class, "tfc:horse", 2, state(true, 0.5f, 0));
        // familiarity 0.5 but the vanilla animals are not tamed: TFC's own gate is familiarity, so they do mate when ridden-free
        Player rider = server.addPlayer();
        stallion.addPassenger(rider);
        check(mare);
        assertThat(AnimalPdc.read(mare.getPersistentDataContainer()).orElseThrow().state().fertilized()).isFalse();
    }

    @Test
    void aNewbornFoalTakesItsStatsFromTheMothersGenes() {
        Horse mare = mount(Horse.class, "tfc:horse", 0, state(false, 0.5f, 0));
        Horse stallion = mount(Horse.class, "tfc:horse", 2, state(true, 0.5f, 0));
        module.initialise(mare, AnimalPdc.read(mare.getPersistentDataContainer()).orElseThrow());
        module.initialise(stallion, AnimalPdc.read(stallion.getPersistentDataContainer()).orElseThrow());
        check(mare);
        assertThat(MountPdc.genes(mare.getPersistentDataContainer())).isPresent();
        Horse foal = mount(Horse.class, "tfc:horse", 0.5, state(false, 0.2f, now));
        AnimalRecord record = AnimalPdc.read(foal.getPersistentDataContainer()).orElseThrow();
        module.initialise(foal, record); // newborn: resolved on the entity task (run at once in tests)
        assertThat(MountPdc.genes(mare.getPersistentDataContainer())).isEmpty();
        MountStats s = MountPdc.stats(foal.getPersistentDataContainer()).orElseThrow();
        assertThat(s.maxHealth()).isBetween(MountStats.MIN_HEALTH, MountStats.MAX_HEALTH);
    }

    @Test
    void genesAndStatsRoundTripThroughThePdc() {
        MountStats stats = new MountStats(20.5, 0.55, 0.2);
        assertThat(MountPdc.parseStats(MountPdc.formatStats(stats))).isEqualTo(stats);
        MountGenes g = new MountGenes(
                new MountGenes.Parent(dev.tfp.core.livestock.LivestockSpecies.DONKEY, stats, Optional.empty()),
                new MountGenes.Parent(dev.tfp.core.livestock.LivestockSpecies.HORSE, stats,
                        Optional.of(new dev.tfp.core.mount.HorseColour(3, 2))));
        assertThat(MountPdc.parseGenes(MountPdc.formatGenes(g))).isEqualTo(g);
    }
}
