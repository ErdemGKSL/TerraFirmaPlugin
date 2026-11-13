/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.health.HealthRegen;
import dev.tfp.core.health.IncomingDamage;
import dev.tfp.core.health.PhysicalResistances;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-SURV-05: TFC's {@code onLivingHurt} and passive regeneration on a MockBukkit server. */
class PhysicalDamageListenerTest extends MockServerTest {
    /** A CraftEngine steel chestplate stands in as a vanilla iron chestplate here (no CraftEngine in MockBukkit). */
    private static final Map<Material, String> CUSTOM = Map.of(Material.IRON_CHESTPLATE, "tfp:metal/chestplate/steel");

    private static final DamageResistances TABLES = new DamageResistances(
            List.of(new DamageResistances.EntityEntry("tfp:zombies", "minecraft:zombies", PhysicalResistances.ZOMBIES)),
            List.of(new DamageResistances.ItemEntry("tfp:steel_armor", new Ingredient.Item("tfp:metal/chestplate/steel"),
                    PhysicalResistances.ARMOR.get("steel"))));

    private static final Map<String, Set<String>> TAGS = Map.of(
            "minecraft:zombies", Set.of("minecraft:zombie"),
            IncomingDamage.ITEM_CRUSHING, Set.of("minecraft:iron_shovel"),
            IncomingDamage.ITEM_SLASHING, Set.of("minecraft:iron_sword"));

    private static final TagLookup TAG_LOOKUP = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return TAGS.getOrDefault(tag, Set.of()).contains(id);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return TAGS.containsKey(tag);
        }
    };

    private World world;
    private PlayerMock bot;
    private Zombie zombie;
    private final List<String> log = new ArrayList<>();
    private boolean ownsAttacker = true;
    private PhysicalDamageListener listener;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        bot = server.addPlayer("Bot");
        zombie = world.spawn(new Location(world, 0, 64, 2), Zombie.class);
        zombie.getEquipment().setItemInMainHand(null);
        listener = new PhysicalDamageListener(() -> TABLES, () -> TAG_LOOKUP,
                stack -> Optional.of(CUSTOM.getOrDefault(stack.getType(), stack.getType().getKey().toString())),
                id -> Optional.empty(), () -> PhysicalDamageListener.WeaponDamageBonus.NONE, e -> ownsAttacker,
                () -> log::add);
    }

    private static float exp(float resistance) {
        return (float) Math.pow(Math.E, -0.01 * resistance);
    }

    private static float modifier() {
        return PhysicalDamageListener.healthModifier(Optional.empty(), 20);
    }

    private EntityDamageEvent hit(Entity target, DamageType type, Entity attacker, double amount) {
        DamageSource.Builder b = DamageSource.builder(type);
        if (attacker != null) {
            b = b.withCausingEntity(attacker).withDirectEntity(attacker);
        }
        EntityDamageEvent event = new EntityDamageEvent(target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, b.build(),
                amount);
        listener.onDamage(event);
        return event;
    }

    @Test
    void zombieHitOnSteelChestplateIsReducedByTfcResistance() {
        float bare = (float) hit(bot, DamageType.MOB_ATTACK, zombie, 3).getDamage();
        bot.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        float armoured = (float) hit(bot, DamageType.MOB_ATTACK, zombie, 3).getDamage();
        assertThat(bare).isCloseTo(3f / modifier(), within(1e-5f));
        // untyped source: the highest of the chestplate's three resistances (piercing 30)
        assertThat(armoured).isCloseTo(3f * exp(30f) / modifier(), within(1e-5f));
        assertThat(armoured / bare).isCloseTo(0.7408182f, within(1e-5f));
        assertThat(log.getLast()).contains("minecraft:zombie -> minecraft:player").contains("type none")
                .contains("resistance 30.00").contains("factor 0.7408");
    }

    @Test
    void steelChestplateVsCrushAndPierce() {
        bot.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
        assertThat((float) hit(bot, DamageType.MOB_ATTACK, zombie, 10).getDamage())
                .isCloseTo(10f * exp(25f) / modifier(), within(1e-5f));
        assertThat((float) hit(bot, DamageType.ARROW, zombie, 10).getDamage()).as("arrow tag beats the weapon")
                .isCloseTo(10f * exp(30f) / modifier(), within(1e-5f));
    }

    @Test
    void entityResistanceOfTheTarget() {
        bot.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
        assertThat((float) hit(zombie, DamageType.PLAYER_ATTACK, bot, 10).getDamage())
                .isCloseTo(10f * exp(50f), within(1e-5f));
        bot.getInventory().setItemInMainHand(null);
        assertThat(hit(zombie, DamageType.PLAYER_ATTACK, bot, 10).getDamage()).as("a fist is untyped").isEqualTo(10.0);
    }

    @Test
    void attackerInAnotherRegionIsNotRead() {
        ownsAttacker = false;
        bot.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
        assertThat(hit(zombie, DamageType.PLAYER_ATTACK, bot, 10).getDamage()).isEqualTo(10.0);
    }

    @Test
    void killAndCancelledAreLeftAlone() {
        bot.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        assertThat(hit(bot, DamageType.GENERIC_KILL, null, Float.MAX_VALUE).getDamage()).isEqualTo(Float.MAX_VALUE);
        assertThat(log).isEmpty();
    }

    @Test
    void registeredListenerSeesRealEvents() {
        server.getPluginManager().registerEvents(listener, plugin);
        bot.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        EntityDamageEvent event = new EntityDamageEvent(bot, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(zombie).withDirectEntity(zombie).build(), 4);
        server.getPluginManager().callEvent(event);
        assertThat((float) event.getDamage()).isCloseTo(4f * exp(30f) / modifier(), within(1e-5f));
    }

    @Test
    void vanillaRegenerationIsSuppressedForPlayers() {
        assertThat(HealthRegenerator.suppressed(EntityRegainHealthEvent.RegainReason.SATIATED)).isTrue();
        assertThat(HealthRegenerator.suppressed(EntityRegainHealthEvent.RegainReason.REGEN)).isTrue();
        assertThat(HealthRegenerator.suppressed(EntityRegainHealthEvent.RegainReason.MAGIC_REGEN)).isFalse();
        assertThat(HealthRegenerator.suppressed(EntityRegainHealthEvent.RegainReason.CUSTOM)).isFalse();
        HealthRegenerator regen = new HealthRegenerator(HealthConfig.DEFAULT, p -> Optional.of(PlayerData.NEW),
                (p, a) -> { });
        EntityRegainHealthEvent food = new EntityRegainHealthEvent(bot, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
        regen.onRegain(food);
        assertThat(food.isCancelled()).isTrue();
        EntityRegainHealthEvent mob = new EntityRegainHealthEvent(zombie, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
        regen.onRegain(mob);
        assertThat(mob.isCancelled()).as("only players").isFalse();
    }

    @Test
    void tfcRegenerationStep() {
        List<Double> healed = new ArrayList<>();
        HealthRegenerator regen = new HealthRegenerator(HealthConfig.DEFAULT, p -> Optional.of(PlayerData.NEW),
                (p, a) -> healed.add(a));
        bot.setHealth(20);
        assertThat(regen.step(bot)).as("not hurt").isZero();
        bot.setHealth(10);
        bot.setFoodLevel(20);
        double full = regen.step(bot);
        assertThat(full).isCloseTo(HealthRegen.healPerStep(20, PlayerData.MAX_THIRST, HealthConfig.DEFAULT), within(1e-7));
        assertThat(full).as("multiplier 3 x 0.004").isCloseTo(0.012, within(1e-6));
        bot.setFoodLevel(3);
        assertThat(regen.step(bot)).as("food below 4").isZero();
        bot.setFoodLevel(20);
        HealthRegenerator thirsty = new HealthRegenerator(HealthConfig.DEFAULT,
                p -> Optional.of(PlayerData.NEW.withThirst(20f)), (p, a) -> healed.add(a));
        assertThat(thirsty.step(bot)).as("thirst not above 20").isZero();
        HealthRegenerator unloaded = new HealthRegenerator(HealthConfig.DEFAULT, p -> Optional.empty(), (p, a) -> { });
        assertThat(unloaded.step(bot)).isZero();
        bot.setGameMode(GameMode.CREATIVE);
        assertThat(regen.step(bot)).as("TFC heals in every game mode").isPositive();
        assertThat(healed).hasSize(2);
    }
}
