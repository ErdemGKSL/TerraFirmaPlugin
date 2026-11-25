/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement;

import dev.tfp.core.advancement.CriterionRef;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * PAR-DATA-02: the trigger API. Our datapack holds TFC's advancement tree with {@code minecraft:impossible} criteria (the
 * tab roots keep {@code minecraft:tick}); features award a criterion by name with {@link #fire}, and the vanilla server
 * does the rest: progress, parents, the toast, the chat announcement, the advancements screen. Provided as a module
 * service ({@code context.services().get(Advancements.class)}).
 *
 * <p>Threads: any. The award runs on the thread that owns the player ({@code onPlayer}, the entity scheduler); a call on
 * that thread is queued as well, so callers never hold a lock or an event across it.
 */
public final class Advancements {
    private final AdvancementGrants grants;
    private final BiConsumer<Player, Runnable> onPlayer;
    private final Consumer<String> warn;
    /** Criteria already reported as missing from the server (each one is logged once). */
    private final Set<CriterionRef> reportedMissing = ConcurrentHashMap.newKeySet();

    /**
     * @param onPlayer runs a task on the thread that owns the player (drops it when the player is gone)
     * @param warn     told once per criterion the server does not know (datapack missing or stale)
     */
    public Advancements(AdvancementGrants grants, BiConsumer<Player, Runnable> onPlayer, Consumer<String> warn) {
        this.grants = Objects.requireNonNull(grants, "grants");
        this.onPlayer = Objects.requireNonNull(onPlayer, "onPlayer");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /** Awards {@code criterion} to {@code player}, e.g. {@code fire(p, "tfp:story/find_rock#rock_knapping")}. Any thread. */
    public void fire(Player player, String criterion) {
        fire(player, CriterionRef.parse(criterion));
    }

    /** Awards {@code criterion} to {@code player}. Any thread; a no-op when it is already awarded. */
    public void fire(Player player, CriterionRef criterion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(criterion, "criterion");
        onPlayer.accept(player, () -> awardNow(player, criterion));
    }

    /** Whether the player already has the criterion. Thread that owns the player. */
    public boolean isAwarded(Player player, CriterionRef criterion) {
        return grants.isAwarded(player, criterion);
    }

    /** The award itself. Thread that owns the player. Returns whether it changed the player's progress. */
    boolean awardNow(Player player, CriterionRef criterion) {
        if (!player.isOnline()) {
            return false;
        }
        if (!grants.exists(criterion)) {
            if (reportedMissing.add(criterion)) {
                warn.accept("Advancement criterion " + criterion + " is not loaded (is the tfp_seasonal_biomes datapack on?)");
            }
            return false;
        }
        return grants.award(player, criterion);
    }
}
