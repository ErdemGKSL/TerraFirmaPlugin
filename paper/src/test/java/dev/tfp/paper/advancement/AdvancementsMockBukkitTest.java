/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.advancement.CriterionRef;
import dev.tfp.core.advancement.ItemCriteria;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-02: the trigger API and the find_rock item criterion, with a fake server-side advancement store. */
class AdvancementsMockBukkitTest extends MockServerTest {
    private static final CriterionRef FIND_ROCK = CriterionRef.parse("tfp:story/find_rock#rock_knapping");

    /** The datapack's criteria, and the awarded ones per player. */
    static final class FakeGrants implements AdvancementGrants {
        final Set<CriterionRef> loaded = new HashSet<>();
        final Map<UUID, Set<CriterionRef>> awarded = new HashMap<>();

        @Override
        public boolean exists(CriterionRef criterion) {
            return loaded.contains(criterion);
        }

        @Override
        public boolean isAwarded(Player player, CriterionRef criterion) {
            return !loaded.contains(criterion) || of(player).contains(criterion);
        }

        @Override
        public boolean award(Player player, CriterionRef criterion) {
            return loaded.contains(criterion) && of(player).add(criterion);
        }

        Set<CriterionRef> of(Player player) {
            return awarded.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        }
    }

    private final FakeGrants grants = new FakeGrants();
    private final Queue<Runnable> playerTasks = new ArrayDeque<>();
    private final List<String> warnings = new ArrayList<>();
    private final FakeCustomItems items = new FakeCustomItems()
        .define("tfp:rock/loose/granite", n -> new ItemStack(Material.STONE, n))
        .define("tfp:groundcover/stick", n -> new ItemStack(Material.STICK, n));
    private Advancements advancements;
    private ItemCriteriaListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        grants.loaded.add(FIND_ROCK);
        advancements = new Advancements(grants, (p, task) -> playerTasks.add(task), warnings::add);
        // #tfp:rock_knapping holds the loose rocks; the fake tag runtime asks the CE id like Tags::is does.
        listener = new ItemCriteriaListener(ItemCriteria.DEFAULT, (stack, tag) -> tag.equals("tfp:rock_knapping")
            && items.idOf(stack).filter(id -> id.startsWith("tfp:rock/loose/")).isPresent(), advancements);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void drain() {
        Runnable r;
        while ((r = playerTasks.poll()) != null) {
            r.run();
        }
    }

    private void slot(ItemStack stack) {
        // PlayerInventorySlotChangeEvent cannot be built on MockBukkit (its view does not convert slots); the handler only
        // forwards the player and the new stack to onItem.
        listener.onItem(player, stack);
    }

    @Test
    void fireAwardsOnThePlayersThreadOnly() {
        advancements.fire(player, "tfp:story/find_rock#rock_knapping");
        assertThat(grants.of(player)).as("queued, not run inline").isEmpty();
        drain();
        assertThat(grants.of(player)).containsExactly(FIND_ROCK);
        assertThat(advancements.awardNow(player, FIND_ROCK)).as("second award changes nothing").isFalse();
    }

    @Test
    void pickingUpALooseRockGrantsFindRock() {
        slot(items.create("tfp:rock/loose/granite", 1).orElseThrow());
        drain();
        assertThat(grants.of(player)).containsExactly(FIND_ROCK);
        // Earned: later rocks queue nothing.
        slot(items.create("tfp:rock/loose/granite", 2).orElseThrow());
        assertThat(playerTasks).isEmpty();
    }

    @Test
    void otherItemsAndEmptySlotsGrantNothing() {
        slot(items.create("tfp:groundcover/stick", 1).orElseThrow());
        slot(new ItemStack(Material.STONE));
        slot(new ItemStack(Material.AIR));
        drain();
        assertThat(grants.of(player)).isEmpty();
    }

    @Test
    void creativeAlsoEarnsItAsInVanilla() {
        player.setGameMode(GameMode.CREATIVE);
        slot(items.create("tfp:rock/loose/granite", 1).orElseThrow());
        drain();
        assertThat(grants.of(player)).containsExactly(FIND_ROCK);
    }

    @Test
    void criterionMissingFromTheServerIsReportedOnceAndNotAwarded() {
        CriterionRef missing = CriterionRef.parse("tfp:story/nope#x");
        advancements.fire(player, missing);
        advancements.fire(player, missing);
        drain();
        assertThat(grants.of(player)).isEmpty();
        assertThat(warnings).hasSize(1).first().asString().contains("tfp:story/nope#x");
    }

    @Test
    void playerWhoLeftBeforeTheTaskRanIsSkipped() {
        advancements.fire(player, FIND_ROCK);
        player.disconnect();
        drain();
        assertThat(grants.of(player)).isEmpty();
    }
}
