/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCDamageTypes.java @ c3b4d9536 (the five types, dehydration's hit),
 * src/generated/resources/data/tfc/damage_type/*.json @ c3b4d9536 (their values, expected here)
 * and src/generated/resources/data/minecraft/tags/damage_type/no_knockback.json @ c3b4d9536
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageScaling;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.damage.DeathMessageType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.tag.DamageTypeTags;

/**
 * {@code dehydration} (PAR-SURV-12, the server half of PAR-SURV-10): the bootstrap datapack puts TFC's five damage types
 * into the server's {@code damage_type} registry as {@code tfp:*} with TFC's values ({@code message_id}, scaling,
 * exhaustion, the burning effect of grill and pot), and {@code minecraft:no_knockback} holds all five.
 *
 * <p>Then the hit itself, the way {@code ThirstModule.DEHYDRATION} deals it ({@code DamageSource.builder(tfp:dehydration)},
 * no attacker): with no player online, two adult zombies at spawn, one in full iron armour, each take the same
 * {@value #AMOUNT} damage. The damage event names {@code tfp:dehydration}, both lose health, and the armoured one loses
 * less: in TFC dehydration is not in {@code minecraft:bypasses_armor}, so armour reduces it.
 */
public final class DehydrationDamageCase implements SelfTestCase {
    /** Large enough that full iron (15 points) cuts it clearly: about 3.94 unarmoured (2 natural points), 1.6 armoured. */
    static final double AMOUNT = 4.0;
    /** Full iron armour's points (2 + 6 + 5 + 2), above the zombie's natural 2. */
    static final double IRON_ARMOUR = 15;
    private static final Duration STEP = Duration.ofSeconds(20);
    private static final String NS = "tfp";

    /**
     * TFC's {@code data/tfc/damage_type/<name>.json}, renamed: message_id, scaling, exhaustion, effects ({@code burning}
     * or the default {@code hurt}; a flag, since {@link DamageEffect}'s constants need a running server).
     */
    record Expected(String name, String messageId, DamageScaling scaling, float exhaustion, boolean burning) {
    }

    static final List<Expected> TYPES = List.of(
            new Expected("dehydration", "tfc.dehydration", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0f,
                    false),
            new Expected("grill", "tfc.grill", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f, true),
            new Expected("lobster_trap", "tfc.lobster_trap", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0f,
                    false),
            new Expected("pluck", "tfc.pluck", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0f, false),
            new Expected("pot", "tfc.pot", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f, true));

    @Override
    public String name() {
        return "dehydration: tfp:dehydration is registered and armour reduces its damage (PAR-SURV-12)";
    }

    @Override
    public Duration timeout() {
        return STEP.multipliedBy(3);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        Scheduler s = ctx.tfp().scheduler();
        return OnThread.script("dehydration", () -> {
            List<String> log = new ArrayList<>();
            Registry<DamageType> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE);
            for (Expected e : TYPES) {
                log.add(checkType(registry, e));
            }
            DamageType dehydration = registry.get(Key.key(NS, "dehydration"));
            log.add(hit(s, dehydration));
            return String.join("; ", log);
        });
    }

    private static String checkType(Registry<DamageType> registry, Expected e) {
        Key key = Key.key(NS, e.name());
        DamageType t = registry.get(key);
        if (t == null) {
            throw new AssertionError(key + " is not in the damage_type registry (the bootstrap datapack "
                    + "tfp_seasonal_biomes did not load, or datagen did not ship it)");
        }
        List<String> wrong = new ArrayList<>();
        if (!e.messageId().equals(t.getTranslationKey())) {
            wrong.add("message_id " + t.getTranslationKey() + " (TFC: " + e.messageId() + ")");
        }
        if (t.getDamageScaling() != e.scaling()) {
            wrong.add("scaling " + t.getDamageScaling() + " (TFC: " + e.scaling() + ")");
        }
        if (Math.abs(t.getExhaustion() - e.exhaustion()) > 1e-6f) {
            wrong.add("exhaustion " + t.getExhaustion() + " (TFC: " + e.exhaustion() + ")");
        }
        DamageEffect effect = e.burning() ? DamageEffect.BURNING : DamageEffect.HURT;
        if (!t.getDamageEffect().getSound().equals(effect.getSound())) {
            wrong.add("effects " + effectName(t.getDamageEffect()) + " (TFC: " + effectName(effect) + ")");
        }
        if (t.getDeathMessageType() != DeathMessageType.DEFAULT) {
            wrong.add("death_message_type " + t.getDeathMessageType() + " (TFC: default)");
        }
        if (!DamageTypeTags.NO_KNOCKBACK.isTagged(t)) {
            wrong.add("not in minecraft:no_knockback");
        }
        if (!wrong.isEmpty()) {
            throw new AssertionError(key + ": " + String.join(", ", wrong));
        }
        return e.name();
    }

    private static String effectName(DamageEffect effect) {
        return effect.getSound().equals(DamageEffect.BURNING.getSound()) ? "burning" : "hurt";
    }

    /** Hurts a bare and an armoured zombie with {@code type} for {@link #AMOUNT}; the armoured one must lose less. */
    private static String hit(Scheduler s, DamageType type) throws Exception {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            throw new Skip("a player is online; the case needs an empty server so nothing else hurts the zombies");
        }
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = spawn.getBlockX() >> 4;
        int cz = spawn.getBlockZ() >> 4;
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        // force-loaded so the chunk stays loaded and its entities tick with no player online
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        try {
            return hitIn(s, type, world, cx, cz);
        } finally {
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static String hitIn(Scheduler s, DamageType type, World world, int cx, int cz) throws Exception {
        Zombie[] zombies = OnThread.region(s, world, cx, cz, STEP, () -> {
            int x = (cx << 4) + 8;
            int z = (cz << 4) + 8;
            Location at = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            return new Zombie[] {spawn(world, at, false), spawn(world, at.clone().add(2, 0, 0), true)};
        });
        double[] lost;
        try {
            String worn = OnThread.region(s, world, cx, cz, STEP, () -> zombies[0].isValid() && zombies[1].isValid()
                    && armour(zombies[1]) >= armour(zombies[0]) + IRON_ARMOUR ? null
                    : "valid " + zombies[0].isValid() + "/" + zombies[1].isValid() + ", armour " + armour(zombies[1])
                            + " vs " + armour(zombies[0]));
            if (worn != null) {
                throw new AssertionError("the iron armour is not worn: " + worn);
            }
            lost = OnThread.region(s, world, cx, cz, STEP, () -> new double[] {hurt(zombies[0], type),
                hurt(zombies[1], type)});
        } finally {
            OnThread.region(s, world, cx, cz, STEP, () -> {
                zombies[0].remove();
                zombies[1].remove();
                return null;
            });
        }
        if (!(lost[0] > 0 && lost[1] > 0)) {
            throw new AssertionError("a zombie took no damage: bare lost " + lost[0] + ", armoured lost " + lost[1]);
        }
        if (!(lost[1] < lost[0])) {
            throw new AssertionError("armour did not reduce tfp:dehydration: bare lost " + lost[0] + ", iron-armoured lost "
                    + lost[1]);
        }
        return String.format(Locale.ROOT, "%.1f dehydration: bare zombie lost %.3f, iron-armoured lost %.3f", AMOUNT,
                lost[0], lost[1]);
    }

    /** An adult zombie with no AI and no random gear; {@code iron}: full iron armour. */
    private static Zombie spawn(World world, Location at, boolean iron) {
        return world.spawn(at, Zombie.class, false, z -> {
            z.setAdult();
            z.setAI(false);
            z.setSilent(true);
            z.setPersistent(true);
            z.setCanPickupItems(false);
            EntityEquipment eq = z.getEquipment();
            eq.clear();
            if (iron) {
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                wear(z);
            }
        });
    }

    /**
     * Applies the worn items' attribute modifiers now. Vanilla does it in the mob's next active tick
     * ({@code LivingEntity.detectEquipmentUpdates}), and with no player online Paper's activation range keeps every mob
     * inactive, so it would never happen during the case.
     */
    private static void wear(Zombie zombie) {
        EntityEquipment eq = zombie.getEquipment();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemType item = eq.getItem(slot).getType().asItemType();
            item.getDefaultAttributeModifiers(slot).forEach((attribute, modifier) -> {
                AttributeInstance instance = zombie.getAttribute(attribute);
                if (instance != null && instance.getModifier(modifier.getKey()) == null) {
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }

    private static double armour(Zombie zombie) {
        return zombie.getAttribute(Attribute.ARMOR).getValue();
    }

    /** One {@code type} hit with no attacker (the {@code ThirstModule.DEHYDRATION} path); the health lost. */
    private static double hurt(Zombie zombie, DamageType type) {
        zombie.setFireTicks(0); // a zombie in daylight may have burnt while the armour applied
        zombie.setNoDamageTicks(0);
        zombie.setHealth(zombie.getAttribute(Attribute.MAX_HEALTH).getValue());
        double before = zombie.getHealth();
        zombie.damage(AMOUNT, DamageSource.builder(type).build());
        EntityDamageEvent cause = zombie.getLastDamageCause();
        if (cause == null || !cause.getDamageSource().getDamageType().getKey().equals(type.getKey())) {
            throw new AssertionError("the zombie's last damage is "
                    + (cause == null ? "none" : cause.getDamageSource().getDamageType().getKey()) + ", not tfp:dehydration");
        }
        return before - zombie.getHealth();
    }
}
