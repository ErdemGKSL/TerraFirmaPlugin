/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vanilla.VanillaRules;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-DATA-10: each rule is cancelled by default and released by its option. */
class VanillaRulesModuleMockBukkitTest extends MockServerTest {
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

        VanillaRules on = new VanillaRules(false, true, true, true, false, false, false);
        VanillaRulesModule enabled = new VanillaRulesModule(() -> on, b -> true);
        CreatureSpawnEvent golem2 = spawn(w, EntityType.IRON_GOLEM, SpawnReason.BUILD_IRONGOLEM);
        enabled.onSpawn(golem2);
        assertThat(golem2.isCancelled()).isFalse();
    }

    @Test
    void monstersAreDeniedWhenOffAndNotForSpawnerSpawns() {
        WorldMock w = server.addSimpleWorld("w2");
        VanillaRules none = new VanillaRules(false, false, false, false, false, false, false);
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
        VanillaRulesModule on = new VanillaRulesModule(() -> new VanillaRules(false, false, false, true, false, true, false),
                b -> true);
        ProjectileLaunchEvent e2 = new ProjectileLaunchEvent(egg);
        on.onLaunch(e2);
        assertThat(e2.isCancelled()).isFalse();
    }
}
