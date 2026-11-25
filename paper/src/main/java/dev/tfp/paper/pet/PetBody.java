/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pet;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * What the pet goals ask of and do to the world, in one seam: the server implementation is {@link PaperPetBody}, tests use a
 * recorder. Every call runs on the pet's owning thread.
 */
interface PetBody {
    /** Living entities within {@code range} blocks (a cube) other than the pet, owned by the current region. */
    List<LivingEntity> nearby(Mob mob, double range);

    /** Whether {@code mob} has line of sight to {@code target}. */
    boolean sees(Mob mob, LivingEntity target);

    /** A standable block position at the pet's block plus the offset, or null (the pet's land random position). */
    Location standable(Mob mob, int dx, int dy, int dz);

    /** A standable position around {@code center} within the offsets, or null. */
    Location standableAround(Mob mob, Location center, int dx, int dy, int dz);

    boolean moveTo(Mob mob, Location to, double speed);

    boolean moveTo(Mob mob, LivingEntity to, double speed);

    void stop(Mob mob);

    boolean hasPath(Mob mob);

    void lookAt(Mob mob, Entity at);

    boolean inWater(Mob mob);

    /** The dimension key of the pet's world ({@code minecraft:overworld}). */
    String dimension(Mob mob);

    /** The sitting or sleeping pose on or off (model animations, sitting carrier). */
    void pose(Mob mob, boolean sitting, boolean sleeping);

    /**
     * TFC {@code TamableAi.TamableFindSleepPos}: a block of the bed tag with a pathable top and air above, tried at up to 10
     * random spots within 12 blocks horizontally and 5 vertically; null if none.
     */
    Location findBed(Mob mob, int horizontal, int vertical, Predicate<Block> sitsOn);

    /** {@code doHurtTarget}: swing, hit with the attack damage attribute, attack sound. */
    void attack(Mob mob, LivingEntity target, String soundKey);

    /** A sound key at the pet. */
    void sound(Mob mob, String key, float volume, float pitch);

    /** Seven heart particles around the pet (TFC {@code spawnTamingParticles}). */
    void hearts(Mob mob);
}
