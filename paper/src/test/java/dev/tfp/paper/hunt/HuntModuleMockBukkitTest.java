/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.hunt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.hunt.HuntKill;
import dev.tfp.core.hunt.HuntYield;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.loot.LootDrop;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-FAUNA-17 death drops on MockBukkit: a deer killed by a player drops venison, a medium raw hide and bones from TFC's
 * table; predator kills, babies and {@code doMobLoot false} drop nothing; the vanilla carrier's loot is always replaced; the
 * kill snapshot reads the player's attack damage, the record's sex, size and familiarity.
 */
class HuntModuleMockBukkitTest extends MockServerTest {
    private static final Map<String, Set<String>> TAGS = Map.of(HuntModule.LAND_PREDATORS, Set.of("tfp:wolf", "tfp:cougar"),
        HuntModule.OCEAN_PREDATORS, Set.of("tfp:orca"));

    private World world;
    private HuntModule module;
    private final List<Location> at = new ArrayList<>();
    private final List<List<HuntYield>> dropped = new ArrayList<>();
    private ClockSnapshot clock = ClockSnapshot.UNSET;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new HuntModule();
        DoubleSupplier half = () -> 0.5;
        module.initForTest((id, tag) -> TAGS.getOrDefault(tag, Set.of()).contains(id), s -> false, () -> clock, Runnable::run,
            () -> half, (loc, yields) -> {
                at.add(loc);
                dropped.add(yields);
            });
        server.getPluginManager().registerEvents(module, plugin);
    }

    private Cow animal(String species, boolean male, int size, boolean baby) {
        Cow cow = world.spawn(new Location(world, 3, 64, 4), Cow.class);
        AnimalRecord record = new AnimalRecord(species, AnimalState.spawned(male, 0, 0), size, "", baby,
            baby ? Long.MAX_VALUE : AnimalRecord.NO_GROW_TICK);
        AnimalPdc.write(cow.getPersistentDataContainer(), record);
        if (baby) {
            cow.setBaby();
        }
        return cow;
    }

    private EntityDeathEvent die(LivingEntity animal, Entity attacker) {
        DamageSource.Builder b = DamageSource.builder(attacker == null ? DamageType.FALL : DamageType.MOB_ATTACK);
        if (attacker != null) {
            b.withCausingEntity(attacker).withDirectEntity(attacker);
        }
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.BEEF, 3), new ItemStack(Material.LEATHER)));
        EntityDeathEvent event = new EntityDeathEvent(animal, b.build(), drops, 1);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void deerKilledByAPlayerDropsVenisonAMediumRawHideAndBones() {
        Cow deer = animal("tfc:deer", true, 16, false);
        PlayerMock hunter = server.addPlayer();
        ((org.mockbukkit.mockbukkit.entity.LivingEntityMock) deer).setKiller(hunter);
        EntityDeathEvent event = die(deer, hunter);

        assertThat(event.getDrops()).as("the vanilla carrier's beef and leather are replaced").isEmpty();
        assertThat(dropped).hasSize(1);
        // venison uniform 4..10 at draw 0.5 -> 7; the hide is fixed 1; bone uniform 1..6 at 0.5 -> 4
        assertThat(dropped.get(0)).containsExactly(new HuntYield("tfc:food/venison", 7), new HuntYield("tfc:medium_raw_hide", 1),
            new HuntYield("minecraft:bone", 4));
        assertThat(at.get(0).getBlockX()).isEqualTo(3);
        assertThat(HuntModule.lootDrops(dropped.get(0))).containsExactly(LootDrop.of("tfp:food/venison", 7),
            LootDrop.of("tfp:medium_raw_hide", 1), LootDrop.of("minecraft:bone", 4));
    }

    @Test
    void aPredatorKillSpoilsEveryNotPredatedDrop() {
        Cow deer = animal("tfc:deer", false, 16, false);
        Cow wolf = animal("tfc:wolf", true, 16, false);
        EntityDeathEvent event = die(deer, wolf);
        assertThat(event.getDrops()).isEmpty();
        assertThat(dropped).isEmpty();
    }

    @Test
    void aBlackBearDropsItsHideWhoeverKillsIt() {
        Cow bear = animal("tfc:black_bear", false, 16, false);
        Cow cougar = animal("tfc:cougar", true, 16, false);
        die(bear, cougar);
        assertThat(dropped).hasSize(1);
        assertThat(dropped.get(0)).containsExactly(new HuntYield("tfc:large_raw_hide", 1), new HuntYield("minecraft:bone", 4));
    }

    @Test
    void babiesAndDoMobLootFalseDropNothing() {
        Cow fawn = animal("tfc:deer", true, 16, true);
        assertThat(die(fawn, null).getDrops()).isEmpty();
        assertThat(dropped).isEmpty();

        world.setGameRule(GameRule.DO_MOB_LOOT, false);
        Cow deer = animal("tfc:deer", true, 16, false);
        assertThat(die(deer, null).getDrops()).isEmpty();
        assertThat(dropped).isEmpty();
    }

    @Test
    void entitiesThatAreNotHuntAnimalsKeepTheirVanillaDrops() {
        Zombie zombie = world.spawn(new Location(world, 0, 64, 0), Zombie.class);
        assertThat(die(zombie, null).getDrops()).hasSize(2);
        Cow unknown = animal("tfc:not_an_animal", true, 16, false);
        assertThat(die(unknown, null).getDrops()).hasSize(2);
        assertThat(dropped).isEmpty();
    }

    @Test
    void onlyMalesDropTheGoatHorn() {
        die(animal("tfc:goat", true, 16, false), null);
        die(animal("tfc:goat", false, 16, false), null);
        assertThat(dropped).hasSize(2);
        assertThat(dropped.get(0)).extracting(HuntYield::item).contains("tfc:goat_horn");
        assertThat(dropped.get(1)).extracting(HuntYield::item).doesNotContain("tfc:goat_horn");
    }

    @Test
    void snapshotReadsAttackDamageSexSizeAndCaughtUpFamiliarity() {
        Cow cow = animal("tfc:cow", false, 20, false);
        AnimalRecord record = new AnimalRecord("tfc:cow", AnimalState.spawned(false, 0, 0).withFamiliarity(0.9f, 0), 20, "");
        PlayerMock hunter = server.addPlayer();
        ((org.mockbukkit.mockbukkit.entity.LivingEntityMock) cow).setKiller(hunter);
        HuntKill kill = module.snapshot(cow, record, DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(hunter)
            .withDirectEntity(hunter).build());
        assertThat(kill.killer()).isEqualTo(HuntKill.Killer.PLAYER);
        assertThat(kill.killedByPlayer()).isTrue();
        assertThat(kill.male()).isFalse();
        assertThat(kill.geneticSize()).isEqualTo(20f);
        assertThat(kill.familiarity()).isEqualTo(0.9f); // the clock is unset: the stored value
        var attack = hunter.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        assertThat(kill.playerAttackDamage()).isEqualTo(attack == null ? 0f : (float) attack.getValue()); // MockBukkit: no attribute

        clock = new ClockSnapshot(1, 0L, 0L, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
        assertThat(module.snapshot(cow, record, null).familiarity()).isEqualTo(0.9f);
        assertThat(module.snapshot(cow, record, null).killer()).isEqualTo(HuntKill.Killer.NONE);
    }

    @Test
    void predatorClassesFollowTheEntityTags() {
        assertThat(module.killer(null)).isEqualTo(HuntKill.Killer.NONE);
        assertThat(module.killer(animal("tfc:orca", true, 1, false))).isEqualTo(HuntKill.Killer.OCEAN_PREDATOR);
        assertThat(module.killer(animal("tfc:wolf", true, 1, false))).isEqualTo(HuntKill.Killer.LAND_PREDATOR);
        assertThat(module.killer(animal("tfc:deer", true, 1, false))).isEqualTo(HuntKill.Killer.OTHER);
        assertThat(module.killer(world.spawn(new Location(world, 0, 64, 0), Zombie.class))).isEqualTo(HuntKill.Killer.OTHER);
    }
}
