/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vanilla.VanillaRules;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.vanilla.VanillaRulesModule.Gear;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-DATA-10: each rule is cancelled by default and released by its option. */
class VanillaRulesModuleMockBukkitTest extends MockServerTest {
    private static VanillaRules withGolemsAndHorses() {
        return new VanillaRules(false, true, true, true, false, false, false, false, false, false, true);
    }

    private static CreatureSpawnEvent spawn(WorldMock w, EntityType type, SpawnReason reason) {
        var entity = (org.bukkit.entity.LivingEntity) w.spawnEntity(new Location(w, 0, 64, 0), type);
        return new CreatureSpawnEvent(entity, reason);
    }

    @Test
    void golemsAndTrapHorsesAreCancelledUnlessEnabled() {
        WorldMock w = server.addSimpleWorld("w");
        VanillaRulesModule off = new VanillaRulesModule(() -> VanillaRules.DEFAULT, b -> true);
        CreatureSpawnEvent golem = spawn(w, EntityType.IRON_GOLEM, SpawnReason.BUILD_IRONGOLEM);
        off.onSpawn(golem);
        assertThat(golem.isCancelled()).isTrue();
        CreatureSpawnEvent horse = spawn(w, EntityType.SKELETON_HORSE, SpawnReason.TRAP);
        off.onSpawn(horse);
        assertThat(horse.isCancelled()).isTrue();

        VanillaRules on = withGolemsAndHorses();
        VanillaRulesModule enabled = new VanillaRulesModule(() -> on, b -> true);
        CreatureSpawnEvent golem2 = spawn(w, EntityType.IRON_GOLEM, SpawnReason.BUILD_IRONGOLEM);
        enabled.onSpawn(golem2);
        assertThat(golem2.isCancelled()).isFalse();
    }

    @Test
    void monstersAreDeniedWhenOffAndNotForSpawnerSpawns() {
        WorldMock w = server.addSimpleWorld("w2");
        VanillaRules none = new VanillaRules(false, false, false, false, false, false, false, false, false, false, true);
        VanillaRulesModule m = new VanillaRulesModule(() -> none, b -> true);
        CreatureSpawnEvent natural = spawn(w, EntityType.ZOMBIE, SpawnReason.NATURAL);
        m.onSpawn(natural);
        assertThat(natural.isCancelled()).isTrue();
        CreatureSpawnEvent egg = spawn(w, EntityType.ZOMBIE, SpawnReason.SPAWNER_EGG);
        m.onSpawn(egg);
        assertThat(egg.isCancelled()).isFalse();
    }

    @Test
    void eggsAreNotThrownUnlessEnabled() {
        WorldMock w = server.addSimpleWorld("w3");
        var egg = w.spawnEntity(new Location(w, 0, 64, 0), EntityType.EGG);
        VanillaRulesModule off = new VanillaRulesModule(() -> VanillaRules.DEFAULT, b -> true);
        ProjectileLaunchEvent e = new ProjectileLaunchEvent(egg);
        off.onLaunch(e);
        assertThat(e.isCancelled()).isTrue();
        VanillaRulesModule on = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, true, false, false, false, false, true),
                b -> true);
        ProjectileLaunchEvent e2 = new ProjectileLaunchEvent(egg);
        on.onLaunch(e2);
        assertThat(e2.isCancelled()).isFalse();
    }

    private static final VanillaRules DEF = VanillaRules.DEFAULT;

    private static VanillaRulesModule.Gear gear() {
        return new VanillaRulesModule.Gear(tag -> switch (tag) {
            case "tfc:mob_head_armor" -> List.of("minecraft:copper_helmet");
            case "tfc:skeleton_weapons" -> List.of("minecraft:stick");
            default -> List.of();
        }, id -> {
            var key = org.bukkit.NamespacedKey.fromString(id);
            var m = org.bukkit.Registry.MATERIAL.get(key);
            return m == null ? Optional.empty() : Optional.of(new ItemStack(m));
        }, (stack, tag) -> tag.equals("tfc:disabled_monster_held_items") && stack.getType() == Material.IRON_SWORD,
                stack -> false);
    }

    @Test
    void zombieVanillaArmourBecomesTfcArmourOrNothing() {
        WorldMock w = server.addSimpleWorld("g1");
        VanillaRulesModule m = new VanillaRulesModule(() -> DEF, b -> true, gear(), (t, f, s, d) -> d.accept(false), b -> false, null);
        Zombie z = (Zombie) w.spawnEntity(new Location(w, 0, 64, 0), EntityType.ZOMBIE);
        z.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        z.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        m.onSpawn(new CreatureSpawnEvent(z, SpawnReason.NATURAL));
        assertThat(z.getEquipment().getHelmet().getType()).isEqualTo(Material.COPPER_HELMET);
        assertThat(z.getEquipment().getChestplate().getType()).as("empty tag: no vanilla piece either").isEqualTo(Material.AIR);
        assertThat(z.getEquipment().getItemInMainHand().getType()).as("disabled held item").isEqualTo(Material.AIR);
    }

    @Test
    void vanillaEquipmentOptionKeepsVanillaGearAndCustomReasonIsUntouched() {
        WorldMock w = server.addSimpleWorld("g2");
        VanillaRules keep = new VanillaRules(false, false, false, true, false, false, false, true, true, false, true);
        VanillaRulesModule m = new VanillaRulesModule(() -> keep, b -> true, gear(), (t, f, s, d) -> d.accept(false), b -> false, null);
        Zombie z = (Zombie) w.spawnEntity(new Location(w, 0, 64, 0), EntityType.ZOMBIE);
        z.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        m.onSpawn(new CreatureSpawnEvent(z, SpawnReason.NATURAL));
        assertThat(z.getEquipment().getHelmet().getType()).isEqualTo(Material.IRON_HELMET);
        VanillaRulesModule off = new VanillaRulesModule(() -> DEF, b -> true, gear(), (t, f, s, d) -> d.accept(false), b -> false, null);
        off.onSpawn(new CreatureSpawnEvent(z, SpawnReason.CUSTOM));
        assertThat(z.getEquipment().getHelmet().getType()).isEqualTo(Material.IRON_HELMET);
    }

    @Test
    void spawnEnchantmentsAreStrippedUnlessEnabled() {
        WorldMock w = server.addSimpleWorld("g3");
        VanillaRulesModule m = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, false, false,
                false, true, false, true), b -> true, gear(), (t, f, s, d) -> d.accept(false), b -> false, null);
        Zombie z = (Zombie) w.spawnEntity(new Location(w, 0, 64, 0), EntityType.ZOMBIE);
        ItemStack bow = new ItemStack(Material.IRON_SWORD);
        bow.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        z.getEquipment().setItemInOffHand(bow);
        m.onSpawn(new CreatureSpawnEvent(z, SpawnReason.NATURAL));
        assertThat(z.getEquipment().getItemInOffHand().getEnchantments()).isEmpty();
        VanillaRulesModule on = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, false, false,
                true, true, false, true), b -> true, gear(), (t, f, s, d) -> d.accept(false), b -> false, null);
        z.getEquipment().setItemInOffHand(bow);
        on.onSpawn(new CreatureSpawnEvent(z, SpawnReason.NATURAL));
        assertThat(z.getEquipment().getItemInOffHand().getEnchantments()).isNotEmpty();
    }

    @Test
    void skeletonSpawnsWithATfcWeaponOrABow() {
        WorldMock w = server.addSimpleWorld("g4");
        VanillaRulesModule m = new VanillaRulesModule(() -> DEF, b -> true, gear(), (t, f, s, d) -> d.accept(false), b -> false, null);
        var s = (org.bukkit.entity.LivingEntity) w.spawnEntity(new Location(w, 0, 64, 0), EntityType.SKELETON);
        m.onSpawn(new CreatureSpawnEvent(s, SpawnReason.NATURAL));
        assertThat(s.getEquipment().getItemInMainHand().getType()).isEqualTo(Material.STICK);
        VanillaRulesModule none = new VanillaRulesModule(() -> DEF, b -> true);
        none.onSpawn(new CreatureSpawnEvent(s, SpawnReason.NATURAL));
        assertThat(s.getEquipment().getItemInMainHand().getType()).as("empty tag: bow").isEqualTo(Material.BOW);
    }

    @Test
    void chickenJockiesAreCancelledUnlessEnabled() {
        WorldMock w = server.addSimpleWorld("g5");
        Chicken c = (Chicken) w.spawnEntity(new Location(w, 0, 64, 0), EntityType.CHICKEN);
        c.setIsChickenJockey(true);
        VanillaRulesModule off = new VanillaRulesModule(() -> DEF, b -> true);
        CreatureSpawnEvent e = new CreatureSpawnEvent(c, SpawnReason.JOCKEY);
        off.onSpawn(e);
        assertThat(e.isCancelled()).isTrue();
        VanillaRulesModule on = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, false, false,
                false, false, true, true), b -> true);
        CreatureSpawnEvent e2 = new CreatureSpawnEvent(c, SpawnReason.JOCKEY);
        on.onSpawn(e2);
        assertThat(e2.isCancelled()).isFalse();
    }

    private Block dispenser(WorldMock w, int x, BlockFace face) {
        Block b = w.getBlockAt(x, 64, 0);
        b.setType(Material.DISPENSER);
        Directional d = (Directional) b.getBlockData();
        d.setFacing(face);
        b.setBlockData(d);
        return b;
    }

    @Test
    void dispenserBoneMealIsRefusedUnlessEnabled() {
        WorldMock w = server.addSimpleWorld("g6");
        Block b = dispenser(w, 0, BlockFace.EAST);
        VanillaRulesModule off = new VanillaRulesModule(() -> DEF, x -> true);
        BlockDispenseEvent e = new BlockDispenseEvent(b, new ItemStack(Material.BONE_MEAL), new Vector(1, 0, 0));
        off.onDispense(e);
        assertThat(e.isCancelled()).isTrue();
        VanillaRulesModule on = new VanillaRulesModule(() -> new VanillaRules(true, false, false, true, false, false, false,
                false, false, false, true), x -> true);
        BlockDispenseEvent e2 = new BlockDispenseEvent(b, new ItemStack(Material.BONE_MEAL), new Vector(1, 0, 0));
        on.onDispense(e2);
        assertThat(e2.isCancelled()).isFalse();
    }

    @Test
    void dispenserDropsAnEggInsteadOfThrowingIt() {
        WorldMock w = server.addSimpleWorld("g7");
        Block b = dispenser(w, 0, BlockFace.EAST);
        ((Dispenser) b.getState()).getInventory().addItem(new ItemStack(Material.EGG, 3));
        VanillaRulesModule off = new VanillaRulesModule(() -> DEF, x -> true);
        BlockDispenseEvent e = new BlockDispenseEvent(b, new ItemStack(Material.EGG), new Vector(1, 0, 0));
        off.onDispense(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(w.getEntitiesByClass(org.bukkit.entity.Item.class)).hasSize(1);
        assertThat(((Dispenser) b.getState()).getInventory().getItem(0).getAmount()).isEqualTo(2);
    }

    @Test
    void dispenserFlintAndSteelLightsATfcBlockOnly() {
        WorldMock w = server.addSimpleWorld("g8");
        Block b = dispenser(w, 0, BlockFace.EAST);
        List<BlockFace> faces = new ArrayList<>();
        VanillaRulesModule m = new VanillaRulesModule(() -> DEF, x -> true, Gear.NONE, (t, f, s, d) -> {
            faces.add(f);
            d.accept(true);
        }, x -> x.getX() == 1, null);
        BlockDispenseEvent vanillaTarget = new BlockDispenseEvent(dispenser(w, 5, BlockFace.EAST), new ItemStack(Material.FLINT_AND_STEEL),
                new Vector(1, 0, 0));
        m.onDispense(vanillaTarget);
        assertThat(vanillaTarget.isCancelled()).as("vanilla block in front: vanilla behaviour").isFalse();
        BlockDispenseEvent e = new BlockDispenseEvent(b, new ItemStack(Material.FLINT_AND_STEEL), new Vector(1, 0, 0));
        m.onDispense(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(faces).containsExactly(BlockFace.WEST);
        VanillaRulesModule disabled = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, false,
                false, false, false, false, false), x -> true, Gear.NONE, (t, f, s, d) -> d.accept(true), x -> true, null);
        BlockDispenseEvent e2 = new BlockDispenseEvent(b, new ItemStack(Material.FLINT_AND_STEEL), new Vector(1, 0, 0));
        disabled.onDispense(e2);
        assertThat(e2.isCancelled()).isFalse();
    }

    private static Scheduler capturing(List<Runnable> tasks) {
        return (Scheduler) java.lang.reflect.Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("entity")) {
                        tasks.add((Runnable) args[1]);
                        return true;
                    }
                    return null;
                });
    }

    @Test
    void milkRestoresPotionEffectsUnlessEnabled() {
        server.addSimpleWorld("g9");
        var player = server.addPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
        List<Runnable> tasks = new ArrayList<>();
        VanillaRulesModule m = new VanillaRulesModule(() -> DEF, x -> true, Gear.NONE, (t, f, s, d) -> d.accept(false), x -> false,
                capturing(tasks));
        m.onConsume(new PlayerItemConsumeEvent(player, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
        player.removePotionEffect(PotionEffectType.SPEED); // vanilla clears the effects after the consume event
        assertThat(tasks).hasSize(1);
        tasks.get(0).run();
        assertThat(player.hasPotionEffect(PotionEffectType.SPEED)).isTrue();

        List<Runnable> none = new ArrayList<>();
        VanillaRulesModule vanilla = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, false, true,
                false, false, false, true), x -> true, Gear.NONE, (t, f, s, d) -> d.accept(false), x -> false, capturing(none));
        vanilla.onConsume(new PlayerItemConsumeEvent(player, new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
        assertThat(none).isEmpty();
        VanillaRulesModule other = new VanillaRulesModule(() -> DEF, x -> true, Gear.NONE, (t, f, s, d) -> d.accept(false),
                x -> false, capturing(none));
        other.onConsume(new PlayerItemConsumeEvent(player, new ItemStack(Material.BREAD), EquipmentSlot.HAND));
        assertThat(none).as("only milk").isEmpty();
    }

    @Test
    void bonemealFertilizeByAPlayerIsCancelledUnlessEnabled() {
        WorldMock w = server.addSimpleWorld("g10");
        var player = server.addPlayer();
        Block b = w.getBlockAt(0, 64, 0);
        BlockFertilizeEvent e = new BlockFertilizeEvent(b, player, List.of());
        new VanillaRulesModule(() -> DEF, x -> true).onFertilize(e);
        assertThat(e.isCancelled()).isTrue();
        BlockFertilizeEvent e2 = new BlockFertilizeEvent(b, player, List.of());
        new VanillaRulesModule(() -> new VanillaRules(true, false, false, true, false, false, false, false, false, false, true),
                x -> true).onFertilize(e2);
        assertThat(e2.isCancelled()).isFalse();
    }
}
