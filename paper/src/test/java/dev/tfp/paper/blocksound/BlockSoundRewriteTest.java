/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.blocksound.BlockSoundRewrite.Decision;
import dev.tfp.paper.blocksound.BlockSoundRewrite.Kind;
import dev.tfp.paper.integration.craftengine.CeBlockSoundTable;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-33: the per-packet rewrite. Copper (TFC {@code SoundType.METAL}, 1.0/1.5): CraftEngine's mining-loop hit
 * at 0.5/0.5 becomes 0.25/0.75, its level-event break at 1.0/0.8 becomes 1.0/1.2, the breaker's second break sound is
 * dropped, and a position without one of our blocks is untouched.
 */
class BlockSoundRewriteTest {
    static final String COPPER = "tfps2:metal/block/copper";
    static final UUID WORLD = UUID.randomUUID();
    static final UUID BREAKER = UUID.randomUUID();
    static final UUID VIEWER = UUID.randomUUID();
    static final long T0 = 1_000_000_000_000L;

    TrackedBlocks tracked;
    BlockSoundRewrite rewrite;

    @BeforeEach
    void setUp() throws Exception {
        tracked = new TrackedBlocks();
        rewrite = new BlockSoundRewrite(tracked);
        rewrite.table(CeBlockSoundTable.parse(java.nio.file.Files.readString(
            CeBlockSoundTableTest.repo().resolve("spikes/s2-destroy-stages/pack/configuration/s2.yml"))));
        tracked.setWorld(BREAKER, WORLD);
        tracked.setWorld(VIEWER, WORLD);
        tracked.track(new TrackedBlocks.Pos(WORLD, 10, 64, -3), COPPER, TrackedBlocks.MINING_NANOS, T0);
    }

    @Test
    void copperHitBecomesTfcMetalHit() {
        // CraftEngine sends the hit at the block's corner (x, y, z), not its centre.
        Decision d = rewrite.decide(BREAKER, "minecraft:block.metal.hit", 10, 64, -3, 0.5f, 0.5f, T0 + 1);
        assertThat(d).isEqualTo(new Decision(Kind.SET, 0.25f, 0.75f));
        assertThat(rewrite.decide(BREAKER, "minecraft:replaced.block.metal.hit", 10, 64, -3, 0.5f, 0.5f, T0 + 2).kind())
            .isEqualTo(Kind.SET);
        assertThat(rewrite.decide(BREAKER, "minecraft:block.metal.hit", 10, 64, -3, 0.25f, 0.75f, T0 + 3))
            .as("already right").isEqualTo(Decision.PASS);
    }

    @Test
    void levelEventBreakBecomesTfcMetalBreakForEveryViewer() {
        Decision d = rewrite.decide(VIEWER, "minecraft:replaced.block.metal.break", 10.5, 64.5, -2.5, 1.0f, 0.8f, T0 + 1);
        assertThat(d).isEqualTo(new Decision(Kind.SET, 1.0f, 1.2f));
    }

    @Test
    void theBreakerHearsOneBreakSound() {
        // CraftEngine's own break sound at the configured values, then the level-event one at 1.0/0.8 (either order).
        assertThat(rewrite.decide(BREAKER, "minecraft:replaced.block.metal.break", 10.5, 64.5, -2.5, 1.0f, 1.2f, T0 + 1))
            .isEqualTo(Decision.PASS);
        assertThat(rewrite.decide(BREAKER, "minecraft:replaced.block.metal.break", 10.5, 64.5, -2.5, 1.0f, 0.8f, T0 + 2))
            .isEqualTo(Decision.DROP);
        assertThat(rewrite.decide(VIEWER, "minecraft:replaced.block.metal.break", 10.5, 64.5, -2.5, 1.0f, 0.8f, T0 + 3).kind())
            .as("another player's first break sound").isEqualTo(Kind.SET);
        long later = T0 + BlockSoundRewrite.DUPLICATE_NANOS + 10;
        assertThat(rewrite.decide(BREAKER, "minecraft:replaced.block.metal.break", 10.5, 64.5, -2.5, 1.0f, 0.8f, later).kind())
            .as("the next break of a block placed there again").isEqualTo(Kind.SET);
    }

    @Test
    void aPositionWithoutOurBlockIsUntouched() {
        assertThat(rewrite.decide(BREAKER, "minecraft:block.metal.hit", 11, 64, -3, 0.5f, 0.5f, T0)).isEqualTo(Decision.PASS);
        assertThat(rewrite.decide(VIEWER, "minecraft:replaced.block.metal.break", 11.5, 64.5, -2.5, 1.0f, 0.8f, T0))
            .isEqualTo(Decision.PASS);
        assertThat(rewrite.decide(BREAKER, "minecraft:block.metal.step", 10, 64, -3, 0.15f, 1.5f, T0))
            .as("other events of the block").isEqualTo(Decision.PASS);
        assertThat(rewrite.decide(UUID.randomUUID(), "minecraft:block.metal.hit", 10, 64, -3, 0.5f, 0.5f, T0))
            .as("a player whose world is unknown").isEqualTo(Decision.PASS);
        tracked.setWorld(VIEWER, UUID.randomUUID());
        assertThat(rewrite.decide(VIEWER, "minecraft:replaced.block.metal.break", 10.5, 64.5, -2.5, 1.0f, 0.8f, T0))
            .as("the same coordinates in another world").isEqualTo(Decision.PASS);
    }

    @Test
    void trackedPositionsExpire() {
        long expired = T0 + TrackedBlocks.MINING_NANOS + 1;
        assertThat(rewrite.decide(BREAKER, "minecraft:block.metal.hit", 10, 64, -3, 0.5f, 0.5f, expired)).isEqualTo(Decision.PASS);
        assertThat(tracked.isEmpty()).isTrue();
        assertThat(rewrite.active()).isFalse();
    }

    @Test
    void negativeCoordinatesFloor() {
        tracked.track(new TrackedBlocks.Pos(WORLD, -5, -10, -1), COPPER, TrackedBlocks.BROKEN_NANOS, T0);
        assertThat(rewrite.decide(VIEWER, "minecraft:replaced.block.metal.break", -4.5, -9.5, -0.5, 1.0f, 0.8f, T0).kind())
            .isEqualTo(Kind.SET);
        assertThat(rewrite.decide(BREAKER, "minecraft:block.metal.hit", -5, -10, -1, 0.5f, 0.5f, T0).kind()).isEqualTo(Kind.SET);
    }
}
