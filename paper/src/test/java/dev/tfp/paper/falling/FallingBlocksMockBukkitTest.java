/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.falling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.falling.FallCell;
import dev.tfp.core.falling.FallRules;
import dev.tfp.core.falling.Landing;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-PHYS-01 on MockBukkit. A small simulation stands in for vanilla's {@code FallingBlockEntity.tick} (gravity, move
 * against the fake collision heights, drag, fall distance; a landing fires vanilla's {@code EntityChangeBlockEvent}),
 * and our per-entity task runs before it each tick, as on the server. The fake world gives stone toughness 2 (as
 * {@code #c:stones}), a bottom slab and a torch toughness 0, gravel (the custom block) toughness 0.
 */
class FallingBlocksMockBukkitTest extends MockServerTest {
    private static final String GRAVEL = "tfp:rock/gravel/granite";
    private static final BlockStateRef GRAVEL_STATE = BlockStateRef.of(GRAVEL);

    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(GRAVEL);
    private final FallScheduler scheduler = new FallScheduler();
    private final FakeWorld fake = new FakeWorld();
    private FallingBlocks falling;
    private WorldMock world;
    private final List<FallingBlocks.Landed> landed = new ArrayList<>();

    record Hurt(LivingEntity target, float amount) {
    }

    record Drop(Location at, BlockStateRef state) {
    }

    /** The fake server: cells and collision by material; the custom gravel is a full block. */
    final class FakeWorld implements FallWorld {
        final List<Hurt> hurts = new ArrayList<>();
        final List<Drop> drops = new ArrayList<>();
        final List<Block> destroyed = new ArrayList<>();
        boolean survives = true;

        @Override
        public FallCell cell(Block block) {
            if (custom.stateAt(block).isPresent()) {
                return FallCell.SOLID;
            }
            return switch (block.getType()) {
                case AIR, CAVE_AIR -> FallCell.AIR;
                case WATER -> new FallCell(true, false, 0, false, true);
                case STONE -> new FallCell(false, true, 2, false, false);
                case STONE_SLAB, TORCH -> new FallCell(false, false, 0, false, false);
                case BEDROCK -> new FallCell(false, true, 4, true, false);
                case SHORT_GRASS -> new FallCell(false, false, 0, false, true);
                default -> FallCell.SOLID;
            };
        }

        @Override
        public int toughness(BlockStateRef state) {
            return 0;
        }

        @Override
        public boolean survives(BlockStateRef state, Block at) {
            return survives;
        }

        @Override
        public boolean isMovingPiston(Block block) {
            return block.getType() == Material.MOVING_PISTON;
        }

        @Override
        public double[][] collisionHeights(Block block) {
            if (custom.stateAt(block).isPresent()) {
                return new double[][] {{0, 1}};
            }
            return switch (block.getType()) {
                case STONE, BEDROCK, DIRT -> new double[][] {{0, 1}};
                case STONE_SLAB -> new double[][] {{0, 0.5}};
                default -> new double[0][];
            };
        }

        @Override
        public Optional<BlockData> blockData(BlockStateRef state) {
            return custom.exists(state.id()) ? Optional.of(Material.GRAVEL.createBlockData()) : Optional.empty();
        }

        @Override
        public BlockStateRef stateAt(Block block) {
            return custom.stateAt(block).orElse(BlockStateRef.of(block.getType().getKey().toString()));
        }

        @Override
        public void destroy(Block block) {
            destroyed.add(block);
            remove(block);
        }

        @Override
        public void remove(Block block) {
            custom.remove(block);
            block.setType(Material.AIR);
        }

        @Override
        public void hurt(LivingEntity target, float amount, FallingBlock source) {
            hurts.add(new Hurt(target, amount));
        }

        @Override
        public boolean place(Block block, BlockStateRef state) {
            block.setType(Material.GRAVEL); // the carrier
            return custom.place(block, state, true);
        }

        @Override
        public void dropAsItem(Location at, BlockStateRef state) {
            drops.add(new Drop(at, state));
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("falling");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        falling = new FallingBlocks(fake, scheduler);
        falling.onLanded(landed::add);
        server.getPluginManager().registerEvents(new FallingListener(falling), plugin());
        // clear a column area and lay a stone floor at y = 59
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 50; y < 90; y++) {
                    world.getBlockAt(x, y, z).setType(y == 59 ? Material.STONE : Material.AIR);
                }
            }
        }
    }

    private FallingBlock spawn(int y) {
        return falling.spawn(world, 0, y, 0, GRAVEL_STATE, FallRules.LANDSLIDE_DAMAGE_PER_BLOCK,
                FallRules.LANDSLIDE_MAX_DAMAGE).orElseThrow();
    }

    /** Vanilla's tick: gravity, move with collision, landing (fires the placement event), drag, fall distance. */
    private void vanillaTick(FallingBlock e) {
        Location loc = e.getLocation();
        double y = loc.getY();
        double vy = e.getVelocity().getY() - FallRules.GRAVITY;
        List<Double> tops = new ArrayList<>();
        List<Double> bottoms = new ArrayList<>();
        for (int by = (int) Math.floor(y); by >= (int) Math.floor(y + vy) - 1; by--) {
            for (double[] box : fake.collisionHeights(world.getBlockAt(loc.getBlockX(), by, loc.getBlockZ()))) {
                tops.add(by + box[1]);
                bottoms.add(by + box[0]);
            }
        }
        OptionalDouble top = FallRules.restingTop(y, vy, tops.stream().mapToDouble(d -> d).toArray(),
                bottoms.stream().mapToDouble(d -> d).toArray());
        if (top.isPresent()) {
            e.teleport(new Location(world, loc.getX(), top.getAsDouble(), loc.getZ()));
            Block at = world.getBlockAt(loc.getBlockX(), (int) Math.floor(top.getAsDouble()), loc.getBlockZ());
            EntityChangeBlockEvent event = new EntityChangeBlockEvent(e, at, e.getBlockData());
            server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                e.remove();
            } else {
                at.setBlockData(e.getBlockData());
                e.remove();
            }
            return;
        }
        e.teleport(new Location(world, loc.getX(), y + vy, loc.getZ()));
        e.setFallDistance((float) (e.getFallDistance() - vy));
        e.setVelocity(new Vector(0, vy * FallRules.DRAG, 0));
    }

    /** Runs server ticks (our task first, then vanilla) until no falling block of ours is left. */
    private void runUntilSettled() {
        for (int tick = 0; tick < 700; tick++) {
            List<FallingBlock> alive = new ArrayList<>();
            for (var entity : world.getEntities()) {
                if (entity instanceof FallingBlock fb && fb.isValid()) {
                    alive.add(fb);
                }
            }
            if (alive.isEmpty()) {
                return;
            }
            for (FallingBlock fb : alive) {
                FallScheduler.Repeating r = scheduler.repeating.get(fb.getUniqueId());
                if (r != null) {
                    r.task().run();
                }
                if (fb.isValid()) {
                    vanillaTick(fb);
                }
            }
        }
        throw new AssertionError("falling blocks did not settle");
    }

    @Test
    void ceGravelFallsAndLandsAsItself() {
        FallingBlock e = spawn(70);
        assertThat(e.getBlockData().getMaterial()).as("carries the server block data").isEqualTo(Material.GRAVEL);
        assertThat(e.getDropItem()).as("vanilla drops nothing for a custom state").isFalse();
        assertThat(e.getPersistentDataContainer().get(FallingBlocks.STATE_KEY, PersistentDataType.STRING))
                .isEqualTo(GRAVEL);
        assertThat(falling.isTracked(e)).isTrue();
        runUntilSettled();
        Block landing = world.getBlockAt(0, 60, 0);
        assertThat(custom.stateAt(landing)).contains(GRAVEL_STATE);
        assertThat(landed).singleElement().satisfies(l -> {
            assertThat(l.landing()).isEqualTo(Landing.PLACE_AT);
            assertThat(l.block()).isEqualTo(landing);
        });
        assertThat(falling.placed()).isEqualTo(1);
        assertThat(falling.tracking()).isZero();
        assertThat(fake.drops).isEmpty();
    }

    @Test
    void fallTurnsAnExistingBlockIntoAFallingOne() {
        Block block = world.getBlockAt(0, 65, 0);
        block.setType(Material.GRAVEL);
        custom.place(block, GRAVEL_STATE, false);
        FallingBlock e = falling.fall(block, 0f, 0).orElseThrow();
        assertThat(block.getType()).isEqualTo(Material.AIR);
        assertThat(custom.stateAt(block)).isEmpty();
        assertThat(e.getLocation().getY()).isEqualTo(65.0);
        runUntilSettled();
        assertThat(custom.stateAt(world.getBlockAt(0, 60, 0))).contains(GRAVEL_STATE);
    }

    @Test
    void breaksATorchAndASlabOnTheWayAndLandsOnStone() {
        world.getBlockAt(0, 61, 0).setType(Material.STONE_SLAB); // a bottom slab: up face not sturdy
        world.getBlockAt(0, 60, 0).setType(Material.TORCH); // under the slab, no collision
        spawn(70);
        runUntilSettled();
        assertThat(fake.destroyed).extracting(Block::getY).containsExactly(61, 60);
        assertThat(custom.stateAt(world.getBlockAt(0, 60, 0))).contains(GRAVEL_STATE);
        assertThat(falling.broken()).isEqualTo(2);
    }

    @Test
    void tougherStoneIsNotBrokenAndBedrockNeither() {
        spawn(70);
        runUntilSettled();
        assertThat(fake.destroyed).isEmpty();
        assertThat(world.getBlockAt(0, 59, 0).getType()).isEqualTo(Material.STONE);
    }

    @Test
    void vanillaPlacementIsCancelledAndPlacesTheCustomState() {
        FallingBlock e = spawn(60);
        // vanilla lands it without our prediction (e.g. on an entity): its placement event
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(e, world.getBlockAt(0, 60, 0), e.getBlockData());
        server.getPluginManager().callEvent(event);
        assertThat(event.isCancelled()).isTrue();
        assertThat(custom.stateAt(world.getBlockAt(0, 60, 0))).contains(GRAVEL_STATE);
        assertThat(world.getBlockAt(0, 60, 0).getType()).isEqualTo(Material.GRAVEL);
        assertThat(fake.hurts).as("vanilla already dealt the damage").isEmpty();
    }

    @Test
    void foreignFallingBlocksAreLeftToVanilla() {
        FallingBlock sand = world.spawn(new Location(world, 3.5, 70, 3.5), FallingBlock.class,
                fb -> fb.setBlockData(Material.SAND.createBlockData()));
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(sand, world.getBlockAt(3, 60, 3),
                sand.getBlockData());
        server.getPluginManager().callEvent(event);
        assertThat(event.isCancelled()).isFalse();
        assertThat(falling.isTracked(sand)).isFalse();
    }

    @Test
    void cannotSurviveAndBlockedAboveDropsAsItem() {
        fake.survives = false;
        world.getBlockAt(0, 61, 0).setType(Material.BEDROCK);
        // falls from 60.9 inside the cell under bedrock: lands at y 60, cannot survive, above is bedrock: drop
        spawn(60);
        runUntilSettled();
        assertThat(fake.drops).singleElement().satisfies(d -> assertThat(d.state()).isEqualTo(GRAVEL_STATE));
        assertThat(landed).singleElement().satisfies(l -> assertThat(l.landing()).isEqualTo(Landing.DROP));
        assertThat(custom.placed()).isEmpty();
    }

    @Test
    void damagesSurvivalAndAdventurePlayersButNotCreativeOrSpectator() {
        PlayerMock survival = server.addPlayer();
        PlayerMock adventure = server.addPlayer();
        PlayerMock creative = server.addPlayer();
        PlayerMock spectator = server.addPlayer();
        survival.setGameMode(GameMode.SURVIVAL);
        adventure.setGameMode(GameMode.ADVENTURE);
        creative.setGameMode(GameMode.CREATIVE);
        spectator.setGameMode(GameMode.SPECTATOR);
        for (PlayerMock p : List.of(survival, adventure, creative, spectator)) {
            p.teleport(new Location(world, 0.5, 60, 0.5));
        }
        spawn(70);
        runUntilSettled();
        // fell 10 blocks (from 70 to 60): fall distance just under 10 -> ceil(9.x - 1) = 9 -> floor(9 * 0.8) = 7
        assertThat(fake.hurts).extracting(Hurt::target).containsExactlyInAnyOrder(survival, adventure);
        assertThat(fake.hurts).allSatisfy(h -> assertThat(h.amount()).isEqualTo(7f));
    }

    @Test
    void noDamageWithoutDamageSettings() {
        PlayerMock survival = server.addPlayer();
        survival.setGameMode(GameMode.SURVIVAL);
        survival.teleport(new Location(world, 0.5, 60, 0.5));
        falling.spawn(world, 0, 70, 0, GRAVEL_STATE, 0f, 0);
        runUntilSettled();
        assertThat(fake.hurts).isEmpty();
    }

    @Test
    void expiresAfterSixHundredTicksAsAnItem() {
        // no floor in this column: stays in the air (the simulation keeps it at the same height by cancelling motion)
        for (int y = 50; y < 90; y++) {
            world.getBlockAt(0, y, 0).setType(Material.AIR);
        }
        FallingBlock e = spawn(75);
        FallScheduler.Repeating r = scheduler.repeating.get(e.getUniqueId());
        for (int t = 0; t < 600; t++) {
            e.setVelocity(new Vector(0, FallRules.GRAVITY, 0)); // hovering: the predicted move is zero
            r.task().run();
            assertThat(e.isValid()).as("tick %d", t + 1).isTrue();
        }
        e.setVelocity(new Vector(0, FallRules.GRAVITY, 0));
        r.task().run();
        assertThat(e.isValid()).isFalse();
        assertThat(fake.drops).hasSize(1);
    }

    @Test
    void unknownStateSpawnsNothing() {
        assertThat(falling.spawn(world, 0, 70, 0, BlockStateRef.of("tfp:nope"), 0f, 0)).isEmpty();
        assertThat(falling.spawned()).isZero();
    }

    @Test
    void resumesFromItsPdcAfterAChunkLoad() {
        FallingBlock e = spawn(70);
        // forget it, as an unload retires the task
        scheduler.repeating.remove(e.getUniqueId()).retired().run();
        assertThat(falling.isTracked(e)).isFalse();
        assertThat(falling.resume(e)).isTrue();
        assertThat(falling.isTracked(e)).isTrue();
        assertThat(falling.resume(e)).as("once").isFalse();
        runUntilSettled();
        assertThat(custom.stateAt(world.getBlockAt(0, 60, 0))).contains(GRAVEL_STATE);
    }
}
