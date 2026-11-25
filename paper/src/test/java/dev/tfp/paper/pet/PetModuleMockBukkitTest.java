/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pet;

import static org.assertj.core.api.Assertions.assertThat;

import com.destroystokyo.paper.entity.ai.Goal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.pet.PetActivity;
import dev.tfp.core.pet.PetCommand;
import dev.tfp.core.pet.PetSpecies;
import dev.tfp.core.pet.PetState;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-14 on MockBukkit: taming by familiarity, the sneak-click command window, the commands and their feedback, the
 * brain's transitions (sit timeout, flee, fight, rest and sleep), the saved state, the collar dye and the owner's death message.
 */
class PetModuleMockBukkitTest extends MockServerTest {
    private World world;
    private PetModule module;
    private Body body;
    private long now;
    private final List<String> messages = new ArrayList<>();
    private final List<Entity> hunted = new ArrayList<>();

    /** Records the world writes; reads are answered from fields. */
    private static final class Body implements PetBody {
        final List<String> log = new ArrayList<>();
        List<LivingEntity> nearby = new ArrayList<>();
        boolean path;

        @Override
        public List<LivingEntity> nearby(Mob mob, double range) {
            return nearby.stream().filter(e -> e != mob).toList();
        }

        @Override
        public boolean sees(Mob mob, LivingEntity target) {
            return true;
        }

        @Override
        public Location standable(Mob mob, int dx, int dy, int dz) {
            return mob.getLocation().getBlock().getLocation().add(dx + 0.5, 0, dz + 0.5);
        }

        @Override
        public Location standableAround(Mob mob, Location center, int dx, int dy, int dz) {
            return center.getBlock().getLocation().add(dx + 0.5, 0, dz + 0.5);
        }

        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            log.add("move " + speed);
            path = true;
            return true;
        }

        @Override
        public boolean moveTo(Mob mob, LivingEntity to, double speed) {
            log.add("follow " + speed);
            path = true;
            return true;
        }

        @Override
        public void stop(Mob mob) {
            path = false;
        }

        @Override
        public boolean hasPath(Mob mob) {
            return path;
        }

        @Override
        public void lookAt(Mob mob, Entity at) {
        }

        @Override
        public boolean inWater(Mob mob) {
            return false;
        }

        @Override
        public String dimension(Mob mob) {
            return "minecraft:overworld";
        }

        @Override
        public void pose(Mob mob, boolean sitting, boolean sleeping) {
            log.add("pose " + sitting + " " + sleeping);
        }

        @Override
        public Location findBed(Mob mob, int horizontal, int vertical, Predicate<Block> sitsOn) {
            log.add("bed " + horizontal + " " + vertical);
            return mob.getLocation().getBlock().getLocation();
        }

        @Override
        public void attack(Mob mob, LivingEntity target, String soundKey) {
            log.add("attack " + soundKey);
        }

        @Override
        public void sound(Mob mob, String key, float volume, float pitch) {
            log.add("sound " + key);
        }

        @Override
        public void hearts(Mob mob) {
            log.add("hearts");
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new PetModule();
        body = new Body();
        now = 100_000;
        PetGoal.PetSenses senses = new PetGoal.PetSenses() {
            @Override
            public boolean sitsOn(Block block) {
                return true;
            }

            @Override
            public boolean huntedBy(PetSpecies species, LivingEntity e) {
                return hunted.contains(e);
            }

            @Override
            public boolean pest(LivingEntity e) {
                return false;
            }

            @Override
            public boolean monster(LivingEntity e) {
                return false;
            }
        };
        module.initForTest(body, () -> now, new Random(5L), senses, species -> s -> s.getType() == Material.BONE,
            e -> AnimalPdc.read(e.getPersistentDataContainer()), (p, key) -> {
                messages.add(key);
                return key;
            });
        server.getPluginManager().registerEvents(module, plugin);
    }

    private Mob pet(String species, double x, float familiarity) {
        Cow cow = world.spawn(new Location(world, x, 64, 0), Cow.class);
        AnimalState state = new AnimalState(false, familiarity, now, 0, AnimalState.NO_OLD_TICK, AnimalState.NEVER,
            AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT);
        AnimalPdc.write(cow.getPersistentDataContainer(), new AnimalRecord(species, state, 0, "", false, AnimalRecord.NO_GROW_TICK));
        return cow;
    }

    private PetBrain brainOf(Mob mob, Goal<Mob>[] out) {
        AnimalRecord record = AnimalPdc.read(mob.getPersistentDataContainer()).orElseThrow();
        List<SpeciesBehaviour.GoalSpec> goals = module.behaviour().goals(mob, record);
        @SuppressWarnings("unchecked")
        Goal<Mob> goal = goals.get(0).goal();
        out[0] = goal;
        return module.brain(mob);
    }

    @SuppressWarnings("unchecked")
    private Goal<Mob>[] holder() {
        return new Goal[1];
    }

    private static void align(Mob mob, int period) {
        int t = mob.getTicksLived() + 1;
        while ((t + mob.getEntityId()) % period != 0) {
            t++;
        }
        mob.setTicksLived(t);
    }

    private static PetMenu menuOf(Player p) {
        var top = p.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof PetMenu m ? m : null;
    }

    private void click(Player player, Mob pet) {
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, pet, EquipmentSlot.HAND));
    }

    @Test
    void anUntamedPetFedWithEnoughFamiliarityIsTamed() {
        Mob dog = pet("tfc:dog", 0, 0.1f);
        PetBrain brain = brainOf(dog, holder());
        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.BONE));
        click(player, dog);
        // 0.1 + 0.06 > 0.15
        assertThat(brain.ownerId).isEqualTo(player.getUniqueId());
        assertThat(body.log).contains("hearts");
        assertThat(PetPdc.owner(dog.getPersistentDataContainer())).isEqualTo(player.getUniqueId());
    }

    @Test
    void notEnoughFamiliarityANonFoodItemOrAnOwnedPetStaysUntamed() {
        Mob shy = pet("tfc:dog", 0, 0.05f);
        PetBrain shyBrain = brainOf(shy, holder());
        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.BONE));
        click(player, shy);
        assertThat(shyBrain.tamed()).isFalse(); // 0.05 + 0.06 = 0.11

        Mob dog = pet("tfc:dog", 3, 0.5f);
        PetBrain brain = brainOf(dog, holder());
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        click(player, dog);
        assertThat(brain.tamed()).isFalse();

        // fed within the day: not hungry
        AnimalPdc.write(dog.getPersistentDataContainer(),
            AnimalPdc.read(dog.getPersistentDataContainer()).orElseThrow().withState(new AnimalState(false, 0.5f, now, 0,
                AnimalState.NO_OLD_TICK, now, AnimalState.NEVER, 0, false, AnimalState.NOT_PREGNANT)));
        player.getInventory().setItemInMainHand(new ItemStack(Material.BONE));
        click(player, dog);
        assertThat(brain.tamed()).isFalse();
    }

    @Test
    void theOwnersSneakClickWithAnEmptyHandOpensTheCommandWindow() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        PetBrain brain = brainOf(dog, holder());
        Player owner = server.addPlayer();
        brain.ownerId = owner.getUniqueId();
        owner.setSneaking(true);
        click(owner, dog);
        assertThat(menuOf(owner)).isNotNull();
        PetMenu menu = menuOf(owner);
        // 20 health: all five commands in TFC's order
        assertThat(List.of(menu.command(0), menu.command(1), menu.command(2), menu.command(3), menu.command(4)))
            .containsExactly(PetCommand.RELAX, PetCommand.HOME, PetCommand.SIT, PetCommand.FOLLOW, PetCommand.HUNT);

        // not the owner, or standing: no window
        Player other = server.addPlayer();
        other.setSneaking(true);
        click(other, dog);
        assertThat(menuOf(other)).isNull();

        // below 5 health the SIT is not offered
        dog.setHealth(4);
        owner.closeInventory();
        click(owner, dog);
        PetMenu hurt = menuOf(owner);
        assertThat(hurt.command(2)).isEqualTo(PetCommand.FOLLOW);
        assertThat(hurt.command(4)).isNull();
    }

    @Test
    void aSitCommandSitsTheOwnersPetAndAStrangerIsToldOff() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        PetBrain brain = brainOf(dog, holder());
        Player owner = server.addPlayer();
        Player stranger = server.addPlayer();
        brain.ownerId = owner.getUniqueId();

        module.command(stranger, dog, PetCommand.SIT);
        assertThat(brain.state.sitting()).isFalse();
        assertThat(messages).contains("pet.not_owner");

        module.command(owner, dog, PetCommand.SIT);
        assertThat(brain.state.sitting()).isTrue();
        assertThat(brain.state.sitTick()).isEqualTo(now);
        assertThat(brain.activity).isEqualTo(PetActivity.SIT);
        assertThat(body.log).contains("pose true false");
        // saved in the carrier's PDC: command, sitting flag (4), SIT tick
        PetState saved = PetPdc.read(dog.getPersistentDataContainer());
        assertThat(saved.command()).isEqualTo(PetCommand.SIT);
        assertThat(saved.sitting()).isTrue();
        assertThat(saved.sitTick()).isEqualTo(now);
    }

    @Test
    void homeStoresTheOwnersBlockAndTheCommandBecomesRelax() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        PetBrain brain = brainOf(dog, holder());
        Player owner = server.addPlayer();
        owner.teleport(new Location(world, 7.5, 70, -2.5));
        brain.ownerId = owner.getUniqueId();
        module.command(owner, dog, PetCommand.HOME);
        assertThat(brain.state.command()).isEqualTo(PetCommand.RELAX);
        assertThat(brain.state.home()).isEqualTo(new PetState.Home("minecraft:overworld", 7, 70, -3));
        assertThat(brain.activity).isEqualTo(PetActivity.IDLE_AT_HOME);
    }

    @Test
    void aCatPurrsWhenTheOwnerCommandsIt() {
        Mob cat = pet("tfc:cat", 0, 0.5f);
        PetBrain brain = brainOf(cat, holder());
        Player owner = server.addPlayer();
        brain.ownerId = owner.getUniqueId();
        module.command(owner, cat, PetCommand.FOLLOW);
        assertThat(body.log).contains("sound minecraft:entity.cat.purreow");
        assertThat(brain.activity).isEqualTo(PetActivity.FOLLOW);
    }

    @Test
    void aSittingPetStandsUpAfterTwoThousandTicksAndIdlesAtHome() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        Goal<Mob>[] g = holder();
        PetBrain brain = brainOf(dog, g);
        Player owner = server.addPlayer();
        brain.ownerId = owner.getUniqueId();
        brain.setState(new PetState(PetCommand.RELAX, false, false, PetState.NO_SIT_TICK,
            new PetState.Home("minecraft:overworld", 0, 64, 0)));
        module.command(owner, dog, PetCommand.SIT);
        long sat = now;
        align(dog, PetGoal.SLOW);
        now = sat + 2000;
        g[0].start();
        assertThat(brain.state.sitting()).isTrue();
        now = sat + 2001;
        align(dog, PetGoal.SLOW);
        g[0].tick();
        assertThat(brain.state.sitting()).isFalse();
        assertThat(brain.state.command()).isEqualTo(PetCommand.RELAX);
        assertThat(brain.activity).isEqualTo(PetActivity.IDLE_AT_HOME);
    }

    @Test
    void aFollowingPetWalksToItsOwnerAndStopsWithinFiveBlocks() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        Goal<Mob>[] g = holder();
        PetBrain brain = brainOf(dog, g);
        Player owner = server.addPlayer();
        owner.teleport(new Location(world, 12, 64, 0));
        brain.ownerId = owner.getUniqueId();
        module.command(owner, dog, PetCommand.FOLLOW);
        align(dog, PetGoal.THINK);
        g[0].start();
        assertThat(body.log).contains("follow 1.100000023841858");
        body.path = false;
        owner.teleport(new Location(world, 4, 64, 0));
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(body.path).isFalse();
    }

    @Test
    void aHurtPetBelowFiveHealthFleesItsAttackerAndReturnsToItsCommand() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        Mob wolf = pet("tfc:wolf", 5, 0.5f);
        Goal<Mob>[] g = holder();
        PetBrain brain = brainOf(dog, g);
        Player owner = server.addPlayer();
        brain.ownerId = owner.getUniqueId();
        brain.setState(new PetState(PetCommand.RELAX, false, false, PetState.NO_SIT_TICK,
            new PetState.Home("minecraft:overworld", 0, 64, 0)));
        module.command(owner, dog, PetCommand.SIT);
        dog.setHealth(4);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(wolf, dog, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).build(), 0.5));
        assertThat(brain.attacker).isSameAs(wolf);
        align(dog, PetGoal.THINK);
        g[0].start();
        // startFleeing: a sitting pet's command becomes RELAX and it stops sitting
        assertThat(brain.activity).isEqualTo(PetActivity.AVOID);
        assertThat(brain.state.sitting()).isFalse();
        assertThat(brain.state.command()).isEqualTo(PetCommand.RELAX);
        assertThat(brain.avoid).isSameAs(wolf);
        assertThat(body.log).contains("move 1.2999999523162842");

        // healed: the avoid activity ends and the stored command's activity is restored on the next think
        dog.setHealth(20);
        now += 10;
        align(dog, PetGoal.THINK);
        g[0].tick();
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(brain.activity).isEqualTo(PetActivity.IDLE_AT_HOME);
        assertThat(brain.avoid).isNull();
    }

    @Test
    void aHuntingDogTakesWhatTheOwnerFightsAndBitesIt() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        Mob rabbit = pet("tfc:rabbit", 1, 0.5f);
        Mob cow = pet("tfc:cow", 3, 0.5f);
        hunted.add(rabbit);
        Goal<Mob>[] g = holder();
        PetBrain brain = brainOf(dog, g);
        Player owner = server.addPlayer();
        owner.teleport(new Location(world, 2, 64, 0));
        brain.ownerId = owner.getUniqueId();
        module.command(owner, dog, PetCommand.HUNT);
        // the owner hits a cow (not hunted by dogs) and a rabbit (hunted): only the rabbit is taken
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(owner, cow, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1));
        align(dog, PetGoal.THINK);
        g[0].start();
        assertThat(brain.target).isNull();
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(owner, rabbit, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1));
        now += 20;
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(brain.target).isSameAs(rabbit);
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(brain.activity).isEqualTo(PetActivity.FIGHT);
        // in reach (1 block): one bite, then the 40-tick cooldown
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(body.log.stream().filter(s -> s.startsWith("attack")).count()).isEqualTo(1);
        assertThat(body.log).contains("attack tfp_animals:entity.dog.attack");
        now += 10;
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(body.log.stream().filter(s -> s.startsWith("attack")).count()).isEqualTo(1);
        // the target dies: the fight ends and the command is restored
        rabbit.setHealth(0);
        now += 40;
        align(dog, PetGoal.THINK);
        g[0].tick();
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(brain.target).isNull();
        assertThat(brain.activity).isEqualTo(PetActivity.HUNT);
    }

    @Test
    void anIdleAtHomePetRestsTwelveThousandTicksAfterItLastSleptAndWakesAfterTwoThousand() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        Goal<Mob>[] g = holder();
        PetBrain brain = brainOf(dog, g);
        brain.setState(new PetState(PetCommand.RELAX, false, false, PetState.NO_SIT_TICK,
            new PetState.Home("minecraft:overworld", 0, 64, 0)));
        brain.setActivity(PetActivity.IDLE_AT_HOME);
        brain.needsCommandUpdate = false;
        brain.lastSlept = now - 12_001;
        align(dog, PetGoal.SLOW);
        g[0].start();
        assertThat(brain.activity).isEqualTo(PetActivity.REST);
        // the bed spot is its own block: reached at once, it falls asleep and records the tick
        align(dog, PetGoal.THINK);
        g[0].tick();
        assertThat(body.log).contains("bed 12 5");
        assertThat(brain.state.sleeping()).isTrue();
        assertThat(brain.lastSlept).isEqualTo(now);
        assertThat(body.log).contains("pose false true");
        assertThat(PetPdc.read(dog.getPersistentDataContainer()).sleeping()).isTrue();

        now += 2001;
        align(dog, PetGoal.SLOW);
        g[0].tick();
        assertThat(brain.state.sleeping()).isFalse();
        assertThat(brain.activity).isEqualTo(PetActivity.IDLE_AT_HOME);
        assertThat(body.log).contains("pose false false");
    }

    @Test
    void theSavedStateIsRestoredWhenThePetEntersTheWorldAgain() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        PetBrain first = brainOf(dog, holder());
        Player owner = server.addPlayer();
        first.ownerId = owner.getUniqueId();
        first.setState(new PetState(PetCommand.SIT, true, false, 4242L, new PetState.Home("minecraft:overworld", 1, 2, 3)));
        first.lastSlept = 99L;
        first.save();

        PetBrain again = brainOf(dog, holder());
        assertThat(again).isNotSameAs(first);
        assertThat(again.ownerId).isEqualTo(owner.getUniqueId());
        assertThat(again.state).isEqualTo(first.state);
        assertThat(again.lastSlept).isEqualTo(99L);
        // readAdditionalSaveData: the command's activity comes back on the next think, and the pose at once
        assertThat(again.needsCommandUpdate).isTrue();
        assertThat(body.log).contains("pose true false");
    }

    @Test
    void aDyeChangesTheCollarAndIsConsumedExceptInCreative() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        brainOf(dog, holder());
        Player player = server.addPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.RED_DYE, 2));
        click(player, dog);
        assertThat(PetPdc.collar(dog.getPersistentDataContainer())).isEqualTo(org.bukkit.DyeColor.RED.ordinal());
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        // the same colour again is not consumed
        click(player, dog);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BLUE_DYE, 1));
        click(player, dog);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(PetPdc.collar(dog.getPersistentDataContainer())).isEqualTo(org.bukkit.DyeColor.BLUE.ordinal());
    }

    @Test
    void theOwnerIsToldWhenTheirPetDies() {
        Mob dog = pet("tfc:dog", 0, 0.5f);
        PetBrain brain = brainOf(dog, holder());
        Player owner = server.addPlayer();
        brain.ownerId = owner.getUniqueId();
        dog.setHealth(0);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(dog,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>()));
        assertThat(messages).contains("pet.died");
    }

    @Test
    void theSpeciesIsReadFromTheRecord() {
        Mob cat = pet("tfc:cat", 0, 0.5f);
        assertThat(brainOf(cat, holder()).species).isEqualTo(PetSpecies.CAT);
        assertThat(PetModule.speciesOf("tfc:dog")).isEqualTo(PetSpecies.DOG);
    }
}
