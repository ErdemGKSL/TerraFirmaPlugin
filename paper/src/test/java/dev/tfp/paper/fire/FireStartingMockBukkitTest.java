/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fire;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.fuel.FireStartPlan;
import dev.tfp.core.fuel.FirepitPile;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-HEAT-05 on MockBukkit: the firestarter's use duration finishing (a cancelled consume) costs durability and calls the
 * {@link FireLightable} SPI with a strong start at the targeted block; flint and pyrite; the fluid gate; the firepit built
 * from three sticks and a log through the {@link FirepitFactory}; TNT; creative mode. Steps run directly (a direct
 * {@link FireStarting.Runner}); the world writes of the fire tail go to a recorder.
 *
 * <p>Stand-ins: blaze rod = {@code tfp:firestarter}, flint = {@code tfp:flint_and_pyrite}, stick = firepit stick, paper =
 * kindling, oak log = firepit log.
 */
class FireStartingMockBukkitTest extends MockServerTest {
    /** Records the tool effects; targets a fixed block face. */
    static final class Tools implements FireTools {
        Target target;
        int damage;
        int cooldowns;
        int sounds;

        @Override
        public Optional<Target> target(Player player) {
            return Optional.ofNullable(target);
        }

        @Override
        public void damage(Player player, EquipmentSlot hand) {
            damage++;
        }

        @Override
        public void offhandCooldown(Player player, int ticks) {
            cooldowns++;
        }

        @Override
        public void strikeSound(Block block) {
            sounds++;
        }
    }

    /** The real fluid and item reads; the fire tail recorded. */
    static final class World implements FireWorld {
        final PaperFireWorld real = new PaperFireWorld();
        final List<Block> fires = new ArrayList<>();
        final List<Item> removed = new ArrayList<>();

        @Override
        public boolean hasFluid(Block block) {
            return real.hasFluid(block);
        }

        @Override
        public List<Item> groundItems(Block at) {
            return real.groundItems(at);
        }

        @Override
        public void remove(Item item) {
            removed.add(item);
            item.remove();
        }

        @Override
        public boolean placeFire(Block clicked, BlockFace face, Player player) {
            fires.add(clicked.getRelative(face));
            return true;
        }
    }

    /** A firepit factory that records what it builds. */
    static final class Firepits implements FirepitFactory {
        boolean survives = true;
        Block at;
        Axis axis;
        List<ItemStack> logs;

        @Override
        public boolean canSurvive(Block block) {
            return survives;
        }

        @Override
        public boolean build(Block block, Axis a, List<ItemStack> l, Player player) {
            at = block;
            axis = a;
            logs = l;
            return true;
        }
    }

    private static final FireStarting.Runner DIRECT = new FireStarting.Runner() {
        @Override
        public void region(Block block, Runnable task) {
            task.run();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }
    };

    private org.bukkit.World world;
    private PlayerMock player;
    private Tools tools;
    private World fireWorld;
    private Firepits firepits;
    private FireStarting fire;
    private final List<FireStarting.Result> results = new ArrayList<>();
    private boolean raining;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 0.5, 65, 0.5));
        tools = new Tools();
        fireWorld = new World();
        firepits = new Firepits();
        fire = new FireStarting(DIRECT, new FireStarting.Rain() {
            @Override
            public boolean reaches(Block pos) {
                return true;
            }

            @Override
            public boolean raining(org.bukkit.World w, int x, int y, int z, boolean reaches) {
                return raining;
            }
        }, fireWorld, stack -> switch (stack.getType()) {
            case STICK -> FirepitPile.Kind.STICK;
            case PAPER -> FirepitPile.Kind.KINDLING;
            case OAK_LOG -> FirepitPile.Kind.LOG;
            default -> FirepitPile.Kind.OTHER;
        });
        FireStarterListener listener = new FireStarterListener(stack -> switch (stack.getType()) {
            case BLAZE_ROD -> Optional.of(FireStarterListener.FIRESTARTER);
            case FLINT -> Optional.of(FireStarterListener.FLINT_AND_PYRITE);
            default -> Optional.empty();
        }, fire, tools, 0.5, 0.25, results::add);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private Block ground() {
        Block b = world.getBlockAt(0, 63, 0);
        b.setType(Material.STONE);
        world.getBlockAt(0, 64, 0).setType(Material.AIR);
        return b;
    }

    private PlayerItemConsumeEvent finishUsing(ItemStack stack) {
        player.getInventory().setItemInMainHand(stack);
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(player, stack, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void finishedUseIsNotEatenCostsDurabilityAndCallsTheLightables() {
        Block target = ground();
        tools.target = new FireTools.Target(target, BlockFace.UP);
        List<FireStartContext> seen = new ArrayList<>();
        fire.register(ctx -> {
            seen.add(ctx);
            return true;
        });
        PlayerItemConsumeEvent event = finishUsing(new ItemStack(Material.BLAZE_ROD));
        assertThat(event.isCancelled()).as("the firestarter is not consumed").isTrue();
        assertThat(tools.damage).isEqualTo(1);
        assertThat(tools.cooldowns).as("the firestarter did not break").isZero();
        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().block()).isEqualTo(target);
        assertThat(seen.getFirst().face()).isEqualTo(BlockFace.UP);
        assertThat(seen.getFirst().strong()).isTrue();
        assertThat(seen.getFirst().player()).isSameAs(player);
        assertThat(results).containsExactly(FireStarting.Result.LIT);
        assertThat(fireWorld.fires).isEmpty();
    }

    @Test
    void theLightablesAreAskedInOrderUntilOneHandlesIt() {
        tools.target = new FireTools.Target(ground(), BlockFace.UP);
        AtomicInteger first = new AtomicInteger();
        AtomicInteger third = new AtomicInteger();
        fire.register(ctx -> first.incrementAndGet() < 0);
        fire.register(ctx -> true);
        fire.register(ctx -> third.incrementAndGet() > 0);
        finishUsing(new ItemStack(Material.BLAZE_ROD));
        assertThat(first).hasValue(1);
        assertThat(third).hasValue(0);
    }

    @Test
    void noTargetDoesNothingAndCreativeCostsNoDurability() {
        tools.target = null;
        PlayerItemConsumeEvent event = finishUsing(new ItemStack(Material.BLAZE_ROD));
        assertThat(event.isCancelled()).isTrue();
        assertThat(tools.damage).isZero();
        assertThat(results).isEmpty();

        player.setGameMode(GameMode.CREATIVE);
        tools.target = new FireTools.Target(ground(), BlockFace.UP);
        finishUsing(new ItemStack(Material.BLAZE_ROD));
        assertThat(tools.damage).isZero();
        assertThat(results).containsExactly(FireStarting.Result.FIRE);
    }

    @Test
    void aBrokenFirestarterPutsTheOffhandOnCooldown() {
        tools.target = new FireTools.Target(ground(), BlockFace.UP);
        FireTools breaking = new FireTools() {
            @Override
            public Optional<Target> target(Player p) {
                return tools.target(p);
            }

            @Override
            public void damage(Player p, EquipmentSlot hand) {
                p.getInventory().setItem(hand, null);
            }

            @Override
            public void offhandCooldown(Player p, int ticks) {
                assertThat(ticks).isEqualTo(FireStarterListener.BREAK_COOLDOWN_TICKS);
                tools.cooldowns++;
            }

            @Override
            public void strikeSound(Block block) {
            }
        };
        FireStarterListener listener = new FireStarterListener(s -> Optional.of(FireStarterListener.FIRESTARTER), fire,
            breaking, 0.5, 0.25, results::add);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
        assertThat(listener.finishFirestarter(player, EquipmentSlot.HAND, new ItemStack(Material.BLAZE_ROD))).isTrue();
        assertThat(tools.cooldowns).isEqualTo(1);
    }

    @Test
    void otherConsumablesAreLeftAlone() {
        PlayerItemConsumeEvent event = finishUsing(new ItemStack(Material.APPLE));
        assertThat(event.isCancelled()).isFalse();
        assertThat(results).isEmpty();
    }

    @Test
    void aFluidInFrontOfTheFaceStopsBeforeTheLightables() {
        Block target = ground();
        world.getBlockAt(0, 64, 0).setType(Material.WATER);
        AtomicInteger asked = new AtomicInteger();
        fire.register(ctx -> asked.incrementAndGet() > 0);
        tools.target = new FireTools.Target(target, BlockFace.UP);
        finishUsing(new ItemStack(Material.BLAZE_ROD));
        assertThat(results).containsExactly(FireStarting.Result.FLUID);
        assertThat(asked).hasValue(0);
    }

    @Test
    void threeSticksAndALogBecomeAFirepit() {
        Block target = ground();
        fire.firepitFactory(firepits);
        Location on = new Location(world, 0.5, 64.1, 0.5);
        Item sticks = world.dropItem(on, new ItemStack(Material.STICK, 3));
        Item log = world.dropItem(on, new ItemStack(Material.OAK_LOG, 1));
        Item dirt = world.dropItem(on, new ItemStack(Material.DIRT, 1));
        FireStartContext ctx = new FireStartContext(target, BlockFace.UP, player, new ItemStack(Material.BLAZE_ROD),
            FireStart.Strength.STRONG);
        // roll 0.49 < 0.5: built
        FireStartPlan plan = new FireStartPlan(FireStart.Strength.STRONG, 0.5, 0.49f);
        assertThat(fire.apply(ctx, plan, Axis.Z)).isEqualTo(FireStarting.Result.FIREPIT);
        assertThat(firepits.at).isEqualTo(world.getBlockAt(0, 64, 0));
        assertThat(firepits.axis).isEqualTo(Axis.Z);
        assertThat(firepits.logs).hasSize(1);
        assertThat(firepits.logs.getFirst().getType()).isEqualTo(Material.OAK_LOG);
        assertThat(fireWorld.removed).containsExactlyInAnyOrder(sticks, log);
        assertThat(dirt.isDead()).isFalse();
        assertThat(fireWorld.fires).isEmpty();
    }

    @Test
    void aFailedFirepitRollMakesNoFireAndTooFewSticksMakeAFire() {
        Block target = ground();
        fire.firepitFactory(firepits);
        Location on = new Location(world, 0.5, 64.1, 0.5);
        world.dropItem(on, new ItemStack(Material.STICK, 3));
        world.dropItem(on, new ItemStack(Material.OAK_LOG, 1));
        FireStartContext ctx = new FireStartContext(target, BlockFace.UP, player, ItemStack.empty(),
            FireStart.Strength.STRONG);
        assertThat(fire.apply(ctx, new FireStartPlan(FireStart.Strength.STRONG, 0.5, 0.5f), Axis.X))
            .isEqualTo(FireStarting.Result.FIREPIT_MISSED);
        assertThat(firepits.at).isNull();
        assertThat(fireWorld.fires).isEmpty();
        // a starter that never builds firepits places a fire on the pile
        assertThat(fire.apply(ctx, new FireStartPlan(FireStart.Strength.STRONG, FireStart.NO_FIREPIT, 0f), Axis.X))
            .isEqualTo(FireStarting.Result.FIRE);
        // the firepit cannot stand there: a fire
        firepits.survives = false;
        assertThat(fire.apply(ctx, new FireStartPlan(FireStart.Strength.STRONG, 0.5, 0f), Axis.X))
            .isEqualTo(FireStarting.Result.FIRE);
        assertThat(firepits.at).isNull();
    }

    @Test
    void flintAndPyriteCostsDurabilityPlaysTheStrikeAndWeakStartsDoNothing() {
        Block target = ground();
        List<FireStartContext> seen = new ArrayList<>();
        fire.register(ctx -> {
            seen.add(ctx);
            return false;
        });
        FireStarterListener listener = new FireStarterListener(s -> Optional.of(FireStarterListener.FLINT_AND_PYRITE),
            fire, tools, 0.5, 0.0, results::add); // chance 0: always weak
        listener.strike(player, EquipmentSlot.HAND, target, BlockFace.UP, new ItemStack(Material.FLINT));
        assertThat(tools.damage).isEqualTo(1);
        assertThat(tools.sounds).isEqualTo(1);
        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().strong()).isFalse();
        assertThat(results).containsExactly(FireStarting.Result.WEAK);
        assertThat(fireWorld.fires).isEmpty();

        FireStarterListener always = new FireStarterListener(s -> Optional.of(FireStarterListener.FLINT_AND_PYRITE),
            fire, tools, 0.5, 1.0, results::add); // chance 1: always strong
        always.strike(player, EquipmentSlot.HAND, target, BlockFace.UP, new ItemStack(Material.FLINT));
        assertThat(results).containsExactly(FireStarting.Result.WEAK, FireStarting.Result.FIRE);
        assertThat(fireWorld.fires).containsExactly(world.getBlockAt(0, 64, 0));
    }

    @Test
    void tntIsPrimedEvenByAWeakStart() {
        Block tnt = world.getBlockAt(0, 63, 0);
        tnt.setType(Material.TNT);
        world.getBlockAt(0, 64, 0).setType(Material.AIR);
        fire.register(new TntLightable());
        FireStartContext ctx = new FireStartContext(tnt, BlockFace.UP, player, ItemStack.empty(), FireStart.Strength.WEAK);
        assertThat(fire.apply(ctx, new FireStartPlan(FireStart.Strength.WEAK, 0.25, 0f), Axis.X))
            .isEqualTo(FireStarting.Result.LIT);
        assertThat(tnt.getType()).isEqualTo(Material.AIR);
    }

    @Test
    void axisFollowsThePlayersFacing() {
        assertThat(FireStarting.axisOf(BlockFace.EAST)).isEqualTo(Axis.X);
        assertThat(FireStarting.axisOf(BlockFace.WEST)).isEqualTo(Axis.X);
        assertThat(FireStarting.axisOf(BlockFace.NORTH)).isEqualTo(Axis.Z);
        assertThat(FireStarting.axisOf(BlockFace.SOUTH)).isEqualTo(Axis.Z);
        assertThat(FireStarting.axisOfYaw(0f)).as("south").isEqualTo(Axis.Z);
        assertThat(FireStarting.axisOfYaw(90f)).as("west").isEqualTo(Axis.X);
        assertThat(FireStarting.axisOfYaw(-90f)).as("east").isEqualTo(Axis.X);
        assertThat(FireStarting.axisOfYaw(180f)).as("north").isEqualTo(Axis.Z);
        assertThat(FireStarting.axisOfYaw(44f)).isEqualTo(Axis.Z);
        assertThat(FireStarting.axisOfYaw(46f)).isEqualTo(Axis.X);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theGeneratedFirestarterUsesForTfcsUseDuration() throws Exception {
        Path here = Path.of("").toAbsolutePath();
        Path repo = Files.isDirectory(here.resolve("spikes")) ? here : here.getParent();
        Path yml = repo.resolve("content/generated/content/craftengine/tfp/configuration/fire_starters.yml");
        Assumptions.assumeTrue(Files.isRegularFile(yml), "no generated content at " + yml);
        Map<String, Object> root = new Yaml().load(Files.readString(yml, StandardCharsets.UTF_8));
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) items.get(FireStarterListener.FIRESTARTER))
            .get("data");
        Map<String, Object> consumable = (Map<String, Object>) data.get("consumable");
        assertThat(((Number) consumable.get("consume_seconds")).doubleValue())
            .isEqualTo(FireStarterListener.USE_TICKS / 20.0);
        assertThat(consumable.get("animation")).isEqualTo("bow");
        assertThat(data.get("max_damage")).isEqualTo(8);
        assertThat(((Map<String, Object>) ((Map<String, Object>) items.get(FireStarterListener.FLINT_AND_PYRITE))
            .get("data")).get("max_damage")).isEqualTo(128);
    }
}
