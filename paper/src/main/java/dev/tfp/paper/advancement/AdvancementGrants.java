/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement;

import dev.tfp.core.advancement.CriterionRef;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

/**
 * PAR-DATA-02: the server side of an advancement criterion. {@link #BUKKIT} asks the server's loaded advancements (our
 * datapack's {@code data/tfp/advancement/**}); tests use a fake.
 *
 * <p>Threads: the thread that owns the player.
 */
public interface AdvancementGrants {
    /** Whether the server knows the advancement and it has the criterion. */
    boolean exists(CriterionRef criterion);

    /** Whether {@code player} has already earned the criterion (true for an unknown one, so it is never retried). */
    boolean isAwarded(Player player, CriterionRef criterion);

    /** Awards the criterion; false when it was already awarded or the server does not know it. */
    boolean award(Player player, CriterionRef criterion);

    AdvancementGrants BUKKIT = new AdvancementGrants() {
        @Override
        public boolean exists(CriterionRef criterion) {
            Advancement a = advancement(criterion);
            return a != null && a.getCriteria().contains(criterion.criterion());
        }

        @Override
        public boolean isAwarded(Player player, CriterionRef criterion) {
            AdvancementProgress p = progress(player, criterion);
            return p == null || p.isDone() || !p.getRemainingCriteria().contains(criterion.criterion());
        }

        @Override
        public boolean award(Player player, CriterionRef criterion) {
            AdvancementProgress p = progress(player, criterion);
            return p != null && p.awardCriteria(criterion.criterion());
        }

        private AdvancementProgress progress(Player player, CriterionRef criterion) {
            Advancement a = advancement(criterion);
            return a == null ? null : player.getAdvancementProgress(a);
        }

        private Advancement advancement(CriterionRef criterion) {
            NamespacedKey key = NamespacedKey.fromString(criterion.advancement());
            return key == null ? null : Bukkit.getAdvancement(key);
        }
    };
}
