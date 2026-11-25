/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fruittree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.banana.BananaRules;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FARM-28: the splicing rule, action and listener, and the hoe overlay lines of the fruit trees and the banana. */
class SplicTest extends MockServerTest {
    static final Messages MESSAGES = Messages.fromJar();
    static final BiFunction<String, Object[], String> EN = (k, a) -> MESSAGES.get("en_us", k, a);
    static final boolean[] NO_FLAGS = new boolean[6];

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private World world;
    private PlayerMock player;
    private boolean knife = true;

    private final FruitTreeEffects fx = new FruitTreeEffects() {
        @Override
        public BerryEffects.Environment environment(Block stem) {
            return new BerryEffects.Environment(50, 5f, true);
        }

        @Override
        public Optional<BlockStateRef> blockAt(Block block) {
            return blocks.stateAt(block);
        }

        @Override
        public boolean isAir(Block block) {
            return block.getType().isAir() && blocks.stateAt(block).isEmpty();
        }

        @Override
        public boolean plantable(Block block) {
            return false;
        }

        @Override
        public boolean inBuildHeight(Block block) {
            return true;
        }

        @Override
        public void show(Block block, BlockStateRef wanted, FruitTreeState state) {
            blocks.place(block, wanted, false);
        }

        @Override
        public void place(Block target, BlockStateRef ref, FruitTreeState state) {
            blocks.place(target, ref, false);
        }

        @Override
        public void store(Block block, FruitTreeState state) {
        }

        @Override
        public void replaceWithDeadBush(Block block) {
        }

        @Override
        public void remove(Block block) {
        }

        @Override
        public void track(Block block) {
        }

        @Override
        public Optional<ItemStack> product(String itemId) {
            return Optional.empty();
        }

        @Override
        public void give(Player p, ItemStack stack) {
        }

        @Override
        public void pickSound(Block block) {
        }

        @Override
        public RandomGenerator random() {
            return RandomGenerator.getDefault();
        }
    };

    /** The oak sapling item stands for the cherry sapling item; the stick for the knife. */
    private final CustomItems items = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return true;
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            return Optional.empty();
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return stack.getType() == Material.OAK_SAPLING ? Optional.of("tfp:plant/cherry_sapling")
                : stack.getType() == Material.SPRUCE_SAPLING ? Optional.of("tfp:plant/plum_sapling") : Optional.empty();
        }
    };

    @BeforeEach
    void setUp() {
        blocks.known(FruitTreeDevice.allIdsForTest());
        world = server.addSimpleWorld("splice");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private SplicListener listener() {
        return new SplicListener(fx, items, stack -> knife && stack.getType() == Material.STICK);
    }

    private Block sapling(int y, int saplings, FruitTree tree) {
        Block b = world.getBlockAt(0, y, 0);
        blocks.place(b, FruitTreeDevice.sapling(tree, saplings), false);
        return b;
    }

    private int saplings(Block b) {
        return Integer.parseInt(blocks.stateAt(b).orElseThrow().properties().get("saplings"));
    }

    private PlayerInteractEvent click(Block b) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), b, BlockFace.UP,
            EquipmentSlot.HAND);
    }

    private void hold(Material main, int amount) {
        player.getInventory().setItemInMainHand(new ItemStack(main, amount));
        player.getInventory().setItemInOffHand(new ItemStack(Material.STICK));
    }

    // ---- the rule ---------------------------------------------------------------------------------------------------------------

    @Test
    void theRuleFollowsMaySplice() {
        assertThat(SplicRules.maySplice(true, 3, false, false)).isTrue(); // on the ground: up to 4
        assertThat(SplicRules.maySplice(true, 4, false, false)).isFalse();
        assertThat(SplicRules.maySplice(true, 2, true, false)).isTrue(); // on a branch: up to 3
        assertThat(SplicRules.maySplice(true, 3, true, false)).isFalse();
        assertThat(SplicRules.maySplice(false, 0, true, true)).isTrue(); // a fresh sapling above a branch
        assertThat(SplicRules.maySplice(false, 0, true, false)).isFalse();
        assertThat(SplicRules.maySplice(false, 0, false, true)).isFalse();
    }

    // ---- the action -------------------------------------------------------------------------------------------------------------

    @Test
    void splicingASaplingBlockRaisesItsSaplingsCount() {
        Block b = sapling(64, 1, FruitTree.CHERRY);
        assertThat(SplicAction.splice(fx, b, FruitTree.CHERRY)).isEqualTo(SplicAction.Result.SPLICED);
        assertThat(saplings(b)).isEqualTo(2);
        assertThat(blocks.stateAt(b).orElseThrow().id()).isEqualTo("tfp:plant/cherry_sapling");
    }

    @Test
    void aSaplingOnTheGroundStopsAtFourAndOneOnABranchAtThree() {
        Block ground = sapling(64, 4, FruitTree.CHERRY);
        assertThat(SplicAction.splice(fx, ground, FruitTree.CHERRY)).isEqualTo(SplicAction.Result.FULL);
        assertThat(saplings(ground)).isEqualTo(4);
        blocks.place(world.getBlockAt(0, 69, 0), FruitTreeDevice.branch(FruitTree.CHERRY, 2, NO_FLAGS), false);
        Block above = sapling(70, 3, FruitTree.CHERRY);
        assertThat(SplicAction.splice(fx, above, FruitTree.CHERRY)).isEqualTo(SplicAction.Result.FULL);
        Block lower = world.getBlockAt(1, 70, 0);
        blocks.place(world.getBlockAt(1, 69, 0), FruitTreeDevice.growing(FruitTree.CHERRY, 0, 2, true, NO_FLAGS), false);
        blocks.place(lower, FruitTreeDevice.sapling(FruitTree.CHERRY, 2), false);
        assertThat(SplicAction.splice(fx, lower, FruitTree.CHERRY)).isEqualTo(SplicAction.Result.SPLICED);
        assertThat(saplings(lower)).isEqualTo(3);
    }

    @Test
    void anotherTreesSaplingOrAnotherBlockIsNotSpliced() {
        Block b = sapling(64, 1, FruitTree.PLUM);
        assertThat(SplicAction.splice(fx, b, FruitTree.CHERRY)).isEqualTo(SplicAction.Result.OTHER_TREE);
        assertThat(SplicAction.splice(fx, world.getBlockAt(5, 64, 5), FruitTree.CHERRY)).isEqualTo(SplicAction.Result.NOT_A_SAPLING);
        assertThat(saplings(b)).isEqualTo(1);
    }

    @Test
    void theAirAboveABranchMaySpliceAFreshSapling() {
        blocks.place(world.getBlockAt(0, 64, 0), FruitTreeDevice.branch(FruitTree.CHERRY, 1, NO_FLAGS), false);
        assertThat(SplicAction.maySplice(fx, world.getBlockAt(0, 65, 0))).isTrue();
        assertThat(SplicAction.maySplice(fx, world.getBlockAt(0, 66, 0))).isFalse();
    }

    // ---- the listener -----------------------------------------------------------------------------------------------------------

    @Test
    void aKnifeInTheOffHandAndTheSaplingInTheMainHandSplicesAndUsesTheItem() {
        Block b = sapling(64, 1, FruitTree.CHERRY);
        hold(Material.OAK_SAPLING, 3);
        PlayerInteractEvent event = click(b);
        listener().onInteract(event);
        assertThat(event.isCancelled()).isTrue();
        assertThat(saplings(b)).isEqualTo(2);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    @Test
    void creativeKeepsTheItem() {
        player.setGameMode(GameMode.CREATIVE);
        Block b = sapling(64, 1, FruitTree.CHERRY);
        hold(Material.OAK_SAPLING, 1);
        listener().onInteract(click(b));
        assertThat(saplings(b)).isEqualTo(2);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
    }

    @Test
    void theLastItemIsUsedUp() {
        Block b = sapling(64, 1, FruitTree.CHERRY);
        hold(Material.OAK_SAPLING, 1);
        listener().onInteract(click(b));
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.AIR);
    }

    @Test
    void withoutAKnifeTheWrongTreeOrAFullSaplingTheClickIsLeftAlone() {
        Block b = sapling(64, 1, FruitTree.CHERRY);
        hold(Material.OAK_SAPLING, 2);
        knife = false;
        PlayerInteractEvent noKnife = click(b);
        listener().onInteract(noKnife);
        assertThat(noKnife.isCancelled()).isFalse();
        knife = true;
        hold(Material.SPRUCE_SAPLING, 2); // the plum's item on the cherry
        PlayerInteractEvent other = click(b);
        listener().onInteract(other);
        assertThat(other.isCancelled()).isFalse();
        Block full = sapling(70, 4, FruitTree.CHERRY);
        hold(Material.OAK_SAPLING, 2);
        PlayerInteractEvent fullClick = click(full);
        listener().onInteract(fullClick);
        assertThat(fullClick.isCancelled()).isFalse();
        assertThat(saplings(b)).isEqualTo(1);
        assertThat(saplings(full)).isEqualTo(4);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    // ---- the overlay lines ------------------------------------------------------------------------------------------------------

    @Test
    void theHydrationAndTemperatureLinesNameTheRangeWhenOutsideIt() {
        FruitTree cherry = FruitTree.CHERRY;
        String ok = SplicLines.hydration(EN, cherry.climate(), 50);
        assertThat(ok).contains("Hydration").contains("50%").contains("Good");
        String low = SplicLines.hydration(EN, cherry.climate(), 1);
        assertThat(low).contains("Too low").contains(cherry.climate().getMinHydration(false) + "%");
        String high = SplicLines.hydration(EN, cherry.climate(), 100);
        assertThat(high).contains("Too high").contains(cherry.climate().getMaxHydration(false) + "%");
        String cold = SplicLines.temperature(EN, cherry.climate(), -30f, ClimateTemperatureStyle.DEFAULT);
        assertThat(cold).contains("Avg. Temp").contains("Too low").contains(ClimateTemperatureStyle.DEFAULT.format(cherry.climate().getMinTemperature(false)));
        String hot = SplicLines.temperature(EN, cherry.climate(), 40f, ClimateTemperatureStyle.DEFAULT);
        assertThat(hot).contains("Too high");
        assertThat(SplicLines.temperature(EN, cherry.climate(), 5f, ClimateTemperatureStyle.DEFAULT)).contains("Good");
    }

    @Test
    void eachBlockFamilyHasItsTfcLines() {
        var range = FruitTree.CHERRY.climate();
        var style = ClimateTemperatureStyle.DEFAULT;
        List<String> sapling = SplicLines.tree(EN, FruitTreeDevice.Part.SAPLING, range, 50, 5f, style, true, true);
        assertThat(sapling).hasSize(4);
        assertThat(sapling.get(2)).contains("could grow");
        assertThat(sapling.get(3)).isEqualTo("May be spliced");
        assertThat(SplicLines.tree(EN, FruitTreeDevice.Part.SAPLING, range, 50, 5f, style, false, false).get(2)).contains("Wrong season");
        List<String> growing = SplicLines.tree(EN, FruitTreeDevice.Part.GROWING, range, 50, 5f, style, true, true);
        assertThat(growing).hasSize(4);
        assertThat(growing.get(2)).isEqualTo("May be spliced");
        assertThat(growing.get(3)).contains("could grow");
        List<String> branch = SplicLines.tree(EN, FruitTreeDevice.Part.BRANCH, range, 0, 0f, style, false, true);
        assertThat(branch).containsExactly("Check leaves or topmost branch for climate information", "This block is done growing");
        assertThat(SplicLines.tree(EN, FruitTreeDevice.Part.LEAVES, range, 50, 5f, style, true, true)).hasSize(2);
        assertThat(SplicLines.banana(EN, BananaRules.CLIMATE, 10, 20f, style)).hasSize(2);
        assertThat(SplicLines.banana(EN, BananaRules.CLIMATE, 10, 20f, style).get(0)).contains("Too low").contains(BananaRules.CLIMATE.getMinHydration(false) + "%");
    }

    @Test
    void theTurkishLinesExist() {
        BiFunction<String, Object[], String> tr = (k, a) -> MESSAGES.get("tr_tr", k, a);
        assertThat(SplicLines.hydration(tr, FruitTree.CHERRY.climate(), 1)).doesNotContain("fruit_tree.");
        assertThat(SplicLines.tree(tr, FruitTreeDevice.Part.BRANCH, FruitTree.CHERRY.climate(), 0, 0f, ClimateTemperatureStyle.DEFAULT, true, true))
            .noneMatch(l -> l.startsWith("fruit_tree."));
    }
}
