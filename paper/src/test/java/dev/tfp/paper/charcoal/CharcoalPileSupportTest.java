/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.neighbour.Neighbourhood;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** PAR-PROC-37: TFC CharcoalPileBlock.canSurvive on the neighbour runner. */
class CharcoalPileSupportTest {
    static final BlockStateRef PILE = BlockStateRef.of(CharcoalPitDevice.CHARCOAL_PILE, Map.of("layers", "3"));
    static final BlockStateRef AIR = BlockStateRef.of("minecraft:air");

    /** Full tops for stone and dirt, none for air and slabs, unknown for CraftEngine states. */
    static final CharcoalPileSupport RULE = new CharcoalPileSupport(s -> s.namespace().equals("minecraft")
        ? Optional.of(s.id().equals("minecraft:stone") || s.id().equals("minecraft:dirt")) : Optional.empty());

    static Neighbourhood<BlockStateRef> on(BlockStateRef below) {
        return Neighbourhood.of((dx, dy, dz) -> dx == 0 && dz == 0 && dy == -1 ? below
            : dx == 0 && dy == 0 && dz == 0 ? PILE : AIR);
    }

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFullTopFaceBelowHoldsThePile() {
        assertThat(RULE.decide(on(BlockStateRef.of("minecraft:stone")))).isNull();
        assertThat(RULE.decide(on(BlockStateRef.of("minecraft:dirt")))).isNull();
    }

    @Test
    void noFullTopFaceBelowTurnsThePileToAir() {
        assertThat(RULE.decide(on(AIR))).isEqualTo(AIR);
        assertThat(RULE.decide(on(BlockStateRef.parse("minecraft:oak_slab[type=bottom,waterlogged=false]")))).isEqualTo(AIR);
        // Idempotent: the decided air is no charcoal pile any more.
        Neighbourhood<BlockStateRef> gone = Neighbourhood.of((dx, dy, dz) -> AIR);
        assertThat(RULE.decide(gone)).isNull();
    }

    @Test
    void aPileBelowHoldsIt() {
        // TFC: an eight-layer pile below holds it; fewer layers are the merge case (the listener's), never removed here.
        assertThat(RULE.decide(on(BlockStateRef.of(CharcoalPitDevice.CHARCOAL_PILE, Map.of("layers", "8"))))).isNull();
        assertThat(RULE.decide(on(BlockStateRef.of(CharcoalPitDevice.CHARCOAL_PILE, Map.of("layers", "2"))))).isNull();
    }

    @Test
    void unknownBelowKeepsThePile() {
        // A CraftEngine block's collision is unknown off the region thread; an unloaded neighbour too.
        assertThat(RULE.decide(on(BlockStateRef.of("tfp:rock/raw/granite")))).isNull();
        Neighbourhood<BlockStateRef> unloaded = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0 ? PILE : null);
        assertThat(RULE.decide(unloaded)).isNull();
    }

    @Test
    void appliesOnlyToTheCharcoalPile() {
        assertThat(RULE.blockIds()).containsExactly("tfp:charcoal_pile");
        assertThat(RULE.appliesTo(PILE)).isTrue();
        assertThat(RULE.appliesTo(BlockStateRef.of(CharcoalPitDevice.LOG_PILE))).isFalse();
        Neighbourhood<BlockStateRef> logPile = Neighbourhood.of((dx, dy, dz) -> dx == 0 && dy == 0 && dz == 0
            ? BlockStateRef.of(CharcoalPitDevice.LOG_PILE) : AIR);
        assertThat(RULE.decide(logPile)).isNull();
    }

    @Test
    void theVanillaLookupParsesEachStateStringOnce() {
        AtomicInteger parses = new AtomicInteger();
        CharcoalPileSupport.VanillaTop top = new CharcoalPileSupport.VanillaTop(s -> {
            parses.incrementAndGet();
            return s.startsWith("minecraft:stone");
        });
        assertThat(top.apply(BlockStateRef.of("minecraft:stone"))).contains(true);
        assertThat(top.apply(BlockStateRef.of("minecraft:stone"))).contains(true);
        assertThat(top.apply(BlockStateRef.of("minecraft:air"))).contains(false);
        assertThat(top.apply(BlockStateRef.of("tfp:rock/raw/granite"))).isEmpty();
        assertThat(parses.get()).isEqualTo(2);
        assertThat(top.size()).isEqualTo(2);
        CharcoalPileSupport.VanillaTop bad = new CharcoalPileSupport.VanillaTop(s -> {
            throw new IllegalArgumentException(s);
        });
        assertThat(bad.apply(BlockStateRef.of("minecraft:nope"))).isEmpty();
    }

    @Test
    void theModuleIsLoadedAfterTheNeighbourModule() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Path.of("src/main/resources/META-INF/services/dev.tfp.paper.module.FeatureModule");
        java.util.List<String> lines = java.nio.file.Files.readAllLines(file).stream().map(String::trim)
            .filter(l -> !l.isEmpty()).toList();
        int module = lines.indexOf(CharcoalPileSupport.Module.class.getName());
        assertThat(module).isPositive();
        assertThat(lines.indexOf("dev.tfp.paper.neighbour.NeighbourModule")).isBetween(0, module - 1);
        assertThat(new CharcoalPileSupport.Module().id()).isEqualTo("charcoal_pile_support");
    }

    @Test
    void theRealLookupReadsBukkitBlockData() {
        // MockBukkit's block data: stone has a sturdy full top, air has none.
        Optional<Boolean> stone;
        Optional<Boolean> air;
        try {
            stone = CharcoalPileSupport.VANILLA_TOP.apply(BlockStateRef.of("minecraft:stone"));
            air = CharcoalPileSupport.VANILLA_TOP.apply(AIR);
        } catch (UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("MockBukkit has no isFaceSturdy: " + e.getMessage());
            return;
        }
        assertThat(stone).contains(true);
        assertThat(air).contains(false);
    }
}
