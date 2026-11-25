/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/TrueAmphibiousMoveControl.java @ c3b4d9536 (tick),
 * aquatic/AmphibiousAnimal.java (travel), predator/AmphibiousPredator.java (travel), same commit
 */
package dev.tfp.paper.amphibian;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.tfp.core.amphibian.AmphibiousMove;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Swimming for carriers that only float (the penguin's chicken, the crocodile's polar bear): in water the mob loses gravity
 * and its velocity follows {@link AmphibiousMove#waterTick} toward the pathfinder's next point, so it dives and swims as
 * TFC's {@code TrueAmphibiousMoveControl} does; out of water gravity comes back and the carrier walks as usual. The
 * gravity switch is remembered in the PDC so a restart in water cannot leave the mob floating. Owning thread only.
 */
final class AmphibiousMover {
    static final NamespacedKey NO_GRAVITY = new NamespacedKey("tfp", "amphibious_no_gravity");

    private AmphibiousMover() {
    }

    static void step(Mob mob, double speedModifier, double movementSpeed, boolean playingDead) {
        var pdc = mob.getPersistentDataContainer();
        if (!mob.isInWater()) {
            if (pdc.has(NO_GRAVITY, PersistentDataType.BYTE)) {
                pdc.remove(NO_GRAVITY);
                mob.setGravity(true);
            }
            return;
        }
        if (mob.hasGravity()) {
            mob.setGravity(false);
            pdc.set(NO_GRAVITY, PersistentDataType.BYTE, (byte) 1);
        }
        Pathfinder.PathResult path = mob.getPathfinder().getCurrentPath();
        Location next = path == null ? null : path.getNextPoint();
        Location at = mob.getLocation();
        Vector v = mob.getVelocity();
        boolean has = next != null && next.getWorld() != null && next.getWorld().equals(at.getWorld());
        // the node centre, as Path.getNextEntityPos
        double dx = has ? next.getBlockX() + 0.5 - at.getX() : 0;
        double dy = has ? next.getBlockY() - at.getY() : 0;
        double dz = has ? next.getBlockZ() + 0.5 - at.getZ() : 0;
        AmphibiousMove.State s = AmphibiousMove.waterTick(
                new AmphibiousMove.State(at.getYaw(), at.getPitch(), v.getX(), v.getY(), v.getZ()),
                dx, dy, dz, has, speedModifier, movementSpeed, playingDead);
        mob.setVelocity(new Vector(s.vx(), s.vy(), s.vz()));
        mob.setRotation(s.yRot(), s.xRot());
    }

    /** The {@code MOVEMENT_SPEED} attribute, 0.3 if the carrier has none. */
    static double movementSpeed(Mob mob) {
        AttributeInstance i = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        return i == null ? 0.3 : i.getValue();
    }
}
