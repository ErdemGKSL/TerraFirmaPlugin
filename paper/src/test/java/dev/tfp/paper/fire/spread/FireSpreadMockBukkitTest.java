/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fire.spread;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-13: fire arrows start fires when enabled; water potions douse. */
class FireSpreadMockBukkitTest extends MockServerTest {
    private final List<String> ignited = new ArrayList<>();
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    /** Owner of every chunk: tasks run at once. */
    private final Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
        new Class<?>[] {Scheduler.class}, (p, m, a) -> switch (m.getName()) {
            case "isOwner" -> true;
            case "region" -> {
                ((Runnable) a[a.length - 1]).run();
                yield null;
            }
            default -> throw new UnsupportedOperationException(m.getName());
        });

    private FireSpreadListener listener() {
        return new FireSpreadListener(scheduler, enabled::get, (b, f) -> ignited.add(b.getX() + "," + b.getY() + "," + f));
    }

    private Arrow arrow(World world, boolean burning) {
        Arrow arrow = (Arrow) world.spawnEntity(world.getBlockAt(0, 70, 0).getLocation(), EntityType.ARROW);
        arrow.setFireTicks(burning ? 100 : 0);
        return arrow;
    }

    @Test
    void aBurningArrowThatHitsABlockStartsAFire() {
        World world = server.addSimpleWorld("w");
        Block target = world.getBlockAt(2, 64, 2);
        listener().onHit(new ProjectileHitEvent(arrow(world, true), null, target, BlockFace.UP));
        assertThat(ignited).containsExactly("2,64,UP");
    }

    @Test
    void aColdArrowOrTheSwitchOffStartsNothing() {
        World world = server.addSimpleWorld("w2");
        Block target = world.getBlockAt(2, 64, 2);
        listener().onHit(new ProjectileHitEvent(arrow(world, false), null, target, BlockFace.UP));
        enabled.set(false);
        listener().onHit(new ProjectileHitEvent(arrow(world, true), null, target, BlockFace.UP));
        listener().onHit(new ProjectileHitEvent(arrow(world, true), null, null, null));
        assertThat(ignited).isEmpty();
    }

    /** The potion entity itself is not implemented by MockBukkit; the block part of the douse is tested directly. */
    @Test
    void douseExtinguishesFireAndALitCampfireAndNothingElse() {
        World world = server.addSimpleWorld("w3");
        Block fire = world.getBlockAt(1, 64, 0);
        fire.setType(Material.FIRE);
        Block camp = world.getBlockAt(-1, 64, 0);
        camp.setType(Material.CAMPFIRE);
        Campfire data = (Campfire) camp.getBlockData();
        data.setLit(true);
        camp.setBlockData(data);
        Block stone = world.getBlockAt(0, 64, 1);
        stone.setType(Material.STONE);

        assertThat(FireSpreadListener.extinguish(fire)).isTrue();
        assertThat(fire.getType()).isEqualTo(Material.AIR);
        assertThat(FireSpreadListener.extinguish(camp)).isTrue();
        assertThat(((Campfire) camp.getBlockData()).isLit()).isFalse();
        assertThat(FireSpreadListener.extinguish(camp)).as("already out").isFalse();
        assertThat(FireSpreadListener.extinguish(stone)).isFalse();
        assertThat(stone.getType()).isEqualTo(Material.STONE);
    }
}
