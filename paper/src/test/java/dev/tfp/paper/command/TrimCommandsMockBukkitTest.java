/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.AddTrimCommand.Failure;
import dev.tfp.core.metal.Metal;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-01: {@code /tfp trim} on the held item and as a metal armour set. */
class TrimCommandsMockBukkitTest extends MockServerTest {
    /** Copper and steel have armour; "tfp:metal/helmet/gold" is missing, as for a metal without all its parts. */
    private final CustomItems items = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return create(id, 1).isPresent();
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            Material m = switch (id) {
                case "tfp:metal/helmet/copper" -> Material.IRON_HELMET;
                case "tfp:metal/chestplate/copper" -> Material.IRON_CHESTPLATE;
                case "tfp:metal/greaves/copper" -> Material.IRON_LEGGINGS;
                case "tfp:metal/boots/copper" -> Material.IRON_BOOTS;
                case "tfp:metal/helmet/gold" -> Material.GOLDEN_HELMET;
                default -> null;
            };
            return Optional.ofNullable(m).map(x -> new ItemStack(x, amount));
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return Optional.empty();
        }
    };
    private TrimCommands commands;
    private PlayerMock alex;

    @BeforeEach
    void setUpPlayer() {
        commands = new TrimCommands(new QueueScheduler(), items);
        alex = new PlayerMock(server, "Alex", UUID.fromString("00000000-0000-0000-0000-00000000a1c2"));
        server.addPlayer(alex);
    }

    @Test
    void theMaterialAndPatternLookupsKnowVanillasIngredientsAndTemplates() {
        assertThat(TrimCommands.VANILLA_MATERIALS).hasSize(11).containsEntry("minecraft:iron_ingot", "iron");
        assertThat(TrimCommands.pattern("minecraft:coast_trim")).isEmpty();
        assertThat(TrimCommands.pattern("tfp:metal/ingot/copper")).isEmpty();
        assertThat(TrimCommands.material("tfp:metal/ingot/copper")).isEmpty();
    }

    @Test
    void aHeldItemThatIsNotRecognisedReportsTheMaterialFirst() {
        alex.getInventory().setItemInMainHand(new ItemStack(Material.IRON_CHESTPLATE));
        TrimCommands.Outcome out = commands.held(alex, "tfp:metal/ingot/copper", "minecraft:nothing").join();
        assertThat(out.failure()).contains(Failure.BAD_MATERIAL);
        assertThat(out.reports()).isEqualTo(1);
        out = commands.held(alex, "minecraft:iron_ingot", "minecraft:nothing").join();
        assertThat(out.failure()).contains(Failure.BAD_TEMPLATE);
    }

    @Test
    void aMetalWithoutAllItsPiecesGivesNothing() {
        TrimCommands.Outcome out = commands.set(alex, "minecraft:iron_ingot", "minecraft:sentry_armor_trim_smithing_template",
                Metal.GOLD).join();
        assertThat(out.failure()).contains(Failure.NOT_ARMOR);
        assertThat(out.given()).isZero();
        assertThat(alex.getInventory().isEmpty()).isTrue();
    }

    @Test
    void anUnrecognisedLookupStillGivesTheFourPiecesUntrimmedAndReportsFourTimes() {
        TrimCommands.Outcome out = commands.set(alex, "tfp:metal/ingot/copper", "minecraft:sentry_armor_trim_smithing_template",
                Metal.COPPER).join();
        assertThat(out.failure()).contains(Failure.BAD_MATERIAL);
        assertThat(out.reports()).isEqualTo(4);
        assertThat(out.given()).isEqualTo(4);
        assertThat(alex.getInventory().all(Material.IRON_HELMET)).hasSize(1);
        for (ItemStack piece : alex.getInventory().getContents()) {
            if (piece != null) {
                assertThat(((ArmorMeta) piece.getItemMeta()).hasTrim()).isFalse();
            }
        }
    }

    @Test
    void aHeldItemThatCannotCarryATrimIsRefused() {
        alex.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        TrimCommands.Outcome out = commands.held(alex, "minecraft:iron_ingot", "minecraft:sentry_armor_trim_smithing_template")
                .join();
        // with a registry (a running server) the lookups hold and the item cannot carry the trim; without one the
        // material is not found: either way nothing is changed
        assertThat(out.given()).isZero();
        assertThat(alex.getInventory().getItemInMainHand().getType()).isEqualTo(Material.STICK);
        assertThat(out.notApplied() || out.failure().isPresent()).isTrue();
    }

    @Test
    void aRecognisedTrimIsAddedToTheHeldArmourAndToEachPieceOfASet() {
        org.junit.jupiter.api.Assumptions.assumeTrue(TrimCommands.material("minecraft:iron_ingot").isPresent(),
                "the mock server has no trim registry");
        alex.getInventory().setItemInMainHand(new ItemStack(Material.IRON_CHESTPLATE));
        TrimCommands.Outcome out = commands.held(alex, "minecraft:iron_ingot", "minecraft:sentry_armor_trim_smithing_template")
                .join();
        assertThat(out.failure()).isEmpty();
        assertThat(((ArmorMeta) alex.getInventory().getItemInMainHand().getItemMeta()).hasTrim()).isTrue();
        alex.getInventory().clear();
        out = commands.set(alex, "minecraft:gold_ingot", "minecraft:sentry_armor_trim_smithing_template", Metal.COPPER).join();
        assertThat(out.given()).isEqualTo(4);
        assertThat(out.failure()).isEmpty();
        for (ItemStack piece : alex.getInventory().getContents()) {
            if (piece != null) {
                assertThat(((ArmorMeta) piece.getItemMeta()).hasTrim()).isTrue();
            }
        }
    }
}
