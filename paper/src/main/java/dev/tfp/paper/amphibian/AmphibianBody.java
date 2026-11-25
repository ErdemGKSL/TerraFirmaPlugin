/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.amphibian;

import dev.tfp.core.amphibian.AmphibianIdle;
import dev.tfp.core.amphibian.AmphibiousEscape;
import dev.tfp.core.aquatic.PlayDeadTimer;
import dev.tfp.core.predator.PredatorMelee;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * What the amphibian brain asks of and does to the world, in one seam: the server implementation is
 * {@link PaperAmphibianBody}, tests use a recorder. Every call runs on the mob's owning thread.
 */
interface AmphibianBody {
    /** In water or a bubble column ({@code isInWaterOrBubble}). */
    boolean inWater(Mob mob);

    /** {@code EnvironmentHelpers.isWaterAt}: the block at {@code at} holds water. */
    boolean waterAt(Location at);

    /** Living entities within {@code range} blocks that the mob can see, owned by the current region. */
    List<LivingEntity> visible(Mob mob, double range);

    boolean moveTo(Mob mob, Location to, double speed);

    boolean moveTo(Mob mob, LivingEntity to, double speed);

    void stop(Mob mob);

    /** A walk target is set (the pathfinder has a path). */
    boolean hasPath(Mob mob);

    void lookAt(Mob mob, Entity at);

    /** {@code BehaviorUtils.getRandomSwimmablePos(mob, 10, 7)}. */
    Optional<Location> randomSwim(Mob mob);

    /** {@code LandRandomPos.getPos(mob, 10, 7)}. */
    Optional<Location> randomStroll(Mob mob);

    /** One {@code DefaultRandomPos.getPosAway(mob, 10, 7, from)} candidate, classified; null when none was found. */
    AmphibiousEscape.Candidate awayFrom(Mob mob, Location from);

    /** The world around the mob's block for {@code TryFindWater}. */
    AmphibianIdle.WaterProbe waterProbe(Mob mob);

    PredatorMelee.Box box(Entity e);

    /** {@code doHurtTarget}: swing and hit with the attack damage attribute. */
    void attack(Mob mob, LivingEntity target);

    /** {@code AmphibianPlayDeadBehavior.start}: the effects, hidden particles. */
    void effects(Mob mob, List<PlayDeadTimer.StatusEffect> effects);

    /** The playing-dead pose on or off (model animation). */
    void playDeadPose(Mob mob, boolean dead);
}
