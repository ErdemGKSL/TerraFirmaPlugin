/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.hitbox.HitboxAllocation.Assignment;
import dev.tfp.datagen.hitbox.HitboxAllocation.Demand;
import dev.tfp.datagen.hitbox.HitboxAllocation.Mode;
import dev.tfp.datagen.hitbox.HitboxAllocation.Result;
import dev.tfp.datagen.hitbox.VanillaCandidates.Presence;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-21: the torch carriers. The lit torches take vanilla's torch states (the only light-14 candidates, so no other
 * demand can take them), the dead standing torch TFC's model on a free state of light 0, and adding them moves no other
 * carrier. PAR-HEAT-29: over the widened dump the dead wall torch draws TFC's model on the fully tilted big dripleaf of its
 * facing (outline 6 x 13 x 6 by the wall, score 0.55) instead of the pressed wall button hitbox-only (6 x 4 x 1, 0.39), the
 * dead standing torch on torchflower_crop[age=1] (6 x 10 x 6, 0.67) instead of the red mushroom (6 x 6 x 6, 0.53), and no
 * torch-shaped state can carry a dead torch (they emit light and make client flame particles, or players craft them).
 */
class TorchCarrierTest {
    private static final List<String> TORCH_IDS = List.of(HitboxDemands.TORCH_ID, HitboxDemands.WALL_TORCH_ID,
        HitboxDemands.DEAD_TORCH_ID, HitboxDemands.DEAD_WALL_TORCH_ID);

    @Test
    void theLitTorchesKeepVanillasTorchStates() {
        Assignment standing = HitboxAllocation.agreed(HitboxDemands.TORCH_ID, HitboxDemands.TORCH_STANDING);
        assertThat(standing.state()).isEqualTo("minecraft:torch");
        assertThat(standing.mode()).isEqualTo(Mode.VANILLA);
        assertThat(standing.hideTarget()).isNull();
        assertThat(standing.carrier().score()).isEqualTo(1.0);
        for (String f : HitboxDemands.FACINGS) {
            Assignment wall = HitboxAllocation.agreed(HitboxDemands.WALL_TORCH_ID, f);
            assertThat(wall.state()).isEqualTo("minecraft:wall_torch[facing=" + f + "]");
            assertThat(wall.mode()).isEqualTo(Mode.VANILLA);
            assertThat(wall.carrier().score()).as(f).isEqualTo(1.0);
        }
    }

    @Test
    void theDeadTorchesTakeNoLightAndNoUnpressedButton() {
        VanillaCandidates v = VanillaCandidates.load();
        Assignment dead = HitboxAllocation.agreed(HitboxDemands.DEAD_TORCH_ID, HitboxDemands.TORCH_STANDING);
        assertThat(dead.mode()).isEqualTo(Mode.MODEL);
        assertThat(dead.demand().look()).isEqualTo("tfp:block/dead_torch");
        assertThat(dead.state()).isEqualTo("minecraft:torchflower_crop[age=1]");
        assertThat(dead.carrier().score()).isGreaterThan(0.66);
        assertThat(dead.hideTarget()).isNull();
        for (String f : HitboxDemands.FACINGS) {
            Assignment wall = HitboxAllocation.agreed(HitboxDemands.DEAD_WALL_TORCH_ID, f);
            assertThat(wall.mode()).as(f).isEqualTo(Mode.MODEL);
            assertThat(wall.state()).isEqualTo("minecraft:big_dripleaf[facing=" + f + ",tilt=full,waterlogged=false]");
            assertThat(wall.demand().look()).isEqualTo(HitboxDemands.deadTorchLook(f));
            assertThat(wall.hideTarget()).isNull();
            assertThat(wall.carrier().score()).as(f).isGreaterThan(HitboxCarrier.MIN_SCORE);
            assertThat(v.excludedState("minecraft:oak_button[face=wall,facing=" + f + ",powered=false]")).contains("PAR-HEAT-21");
        }
        // The new carriers are reserved for the torch classes (no other class may take them).
        for (String b : List.of("minecraft:big_dripleaf", "minecraft:big_dripleaf_stem")) {
            assertThat(v.policy(b).presence()).isEqualTo(Presence.ABSENT);
            assertThat(v.policy(b).onlyClass()).isEqualTo("wall_torch");
        }
        assertThat(v.policy("minecraft:torchflower_crop").onlyClass()).isEqualTo("torch");
        for (String version : v.versions()) {
            for (String id : List.of(dead.state(), HitboxAllocation.agreed(HitboxDemands.DEAD_WALL_TORCH_ID, "north").state())) {
                assertThat(v.get(version, id).light()).as(version + " " + id).isZero();
            }
        }
        // The unlit redstone torches have the torch outline but are crafted in TFC: never a carrier.
        assertThat(v.policy("minecraft:redstone_torch").presence()).isEqualTo(Presence.PLAYER);
        assertThat(v.policy("minecraft:redstone_wall_torch").presence()).isEqualTo(Presence.PLAYER);
        // PAR-HEAT-29 (owner request): the soul torches have the exact outline and no recipe in TFC, but emit light 10 and the
        // client draws their flame and smoke particles (TorchBlock.animateTick) whatever the model; copper torches are crafted.
        for (String version : v.versions()) {
            assertThat(v.get(version, "minecraft:soul_torch").light()).isEqualTo(10);
            assertThat(v.get(version, "minecraft:soul_wall_torch[facing=north]").light()).isEqualTo(10);
        }
        assertThat(v.policy("minecraft:copper_torch").presence()).isEqualTo(Presence.PLAYER);
        // The closest free outlines that are ruled out by a client-side effect, not by the selector's own filters.
        assertThat(v.policy("minecraft:lever").presence()).isEqualTo(Presence.PLAYER);
        assertThat(HitboxDemands.deadTorchLook("east")).isEqualTo("tfp:block/dead_wall_torch");
        assertThat(HitboxDemands.deadTorchLook("north")).isEqualTo("tfp:block/dead_wall_torch@y270");
    }

    @Test
    void tfcsTorchOutlinesAreVanillasInEveryResearchedVersion() {
        VanillaCandidates v = VanillaCandidates.load();
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (String version : v.versions()) {
            for (String block : List.of("torch", "dead_torch")) {
                TfcShapes.Row r = rows.get("tfc:" + block);
                assertThat(r.selection()).as(version + " " + block).isEqualTo(v.get(version, "minecraft:torch").selection());
                assertThat(r.collision().isEmpty()).isTrue();
            }
            for (String f : HitboxDemands.FACINGS) {
                for (String block : List.of("wall_torch", "dead_wall_torch")) {
                    TfcShapes.Row r = rows.get("tfc:" + block + "[facing=" + f + "]");
                    assertThat(r.selection()).as(version + " " + block + " " + f)
                        .isEqualTo(v.get(version, "minecraft:wall_torch[facing=" + f + "]").selection());
                    assertThat(r.collision().isEmpty()).isTrue();
                }
            }
            assertThat(v.get(version, "minecraft:torch").light()).isEqualTo(HitboxDemands.TORCH_LIGHT);
        }
    }

    /**
     * The 2026-11-17 side check: the dead wall torch outline (pressed button, 6x4x1) is much smaller than TFC's. Pin TFC's
     * shapes (TFC TorchBlock / WallTorchBlock via vanilla: box(6,0,6,10,10,10) and, facing north, box(5.5,3,11,10.5,13,16),
     * no collision) and prove that every dumped state with a closer outline is ruled out for a dead torch: it emits light,
     * has a collision box (TFC's torches have none: a box at the wall blocks one-wide tunnels), is crafted or placed by
     * players in TFC, or makes client particles. PAR-HEAT-29 widened the dump ({@link #theDumpHoldsTheWidenedCandidates}):
     * the guard now covers every small no-collision wall and floor block near the torch outline.
     */
    @Test
    void noFreeCarrierHasACloserDeadTorchOutline() {
        VanillaCandidates v = VanillaCandidates.load();
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get("tfc:dead_torch").selection()).isEqualTo(Shape.box(6, 0, 6, 10, 10, 10));
        assertThat(rows.get("tfc:dead_wall_torch[facing=north]").selection()).isEqualTo(Shape.box(5.5, 3, 11, 10.5, 13, 16));
        for (String f : HitboxDemands.FACINGS) {
            assertThat(rows.get("tfc:dead_wall_torch[facing=" + f + "]").collision().isEmpty()).isTrue();
        }
        for (String version : v.versions()) {
            for (String appearance : List.of(HitboxDemands.TORCH_STANDING, "north", "east", "south", "west")) {
                boolean standing = appearance.equals(HitboxDemands.TORCH_STANDING);
                String id = standing ? HitboxDemands.DEAD_TORCH_ID : HitboxDemands.DEAD_WALL_TORCH_ID;
                Shape tfc = rows.get(standing ? "tfc:dead_torch" : "tfc:dead_wall_torch[facing=" + appearance + "]").selection();
                VanillaCandidates.VState chosen = v.get(version, HitboxAllocation.agreed(id, appearance).state());
                assertThat(chosen.light()).isZero();
                assertThat(chosen.collision().isEmpty()).as(version + " " + chosen.state()).isTrue();
                double chosenIou = Shape.iou(tfc, chosen.selection());
                for (String s : v.allStates()) {
                    VanillaCandidates.VState c = v.get(version, s);
                    if (c == null || Shape.iou(tfc, c.selection()) <= chosenIou) {
                        continue;
                    }
                    VanillaCandidates.BlockPolicy p = v.policy(c.block());
                    boolean ruledOut = c.light() > 0 || !c.collision().isEmpty() || v.hardBan(c.block()) != null
                        || v.litParticles(c.block()) || v.excludedState(s) != null || c.flag("fluid")
                        || (p != null && p.presence() != Presence.ABSENT);
                    assertThat(ruledOut).as(version + " " + appearance + ": " + s + " outlines the dead torch better than "
                        + chosen.state() + " and is free").isTrue();
                }
            }
        }
    }

    /** PAR-HEAT-29: the blocks the item asked to dump are in every researched table (copper torches from 1.21.9 on). */
    @Test
    void theDumpHoldsTheWidenedCandidates() {
        VanillaCandidates v = VanillaCandidates.load();
        List<String> states = List.of("minecraft:soul_torch", "minecraft:soul_wall_torch[facing=north]",
            "minecraft:lever[face=wall,facing=north,powered=true]", "minecraft:tripwire_hook[attached=false,facing=north,powered=false]",
            "minecraft:dead_tube_coral_wall_fan[facing=north,waterlogged=false]", "minecraft:tube_coral_fan[waterlogged=true]",
            "minecraft:oak_wall_sign[facing=north,waterlogged=false]",
            "minecraft:oak_wall_hanging_sign[facing=north,waterlogged=false]", "minecraft:white_wall_banner[facing=north]",
            "minecraft:vine[east=false,north=true,south=false,up=false,west=false]",
            "minecraft:glow_lichen[down=false,east=false,north=true,south=false,up=false,waterlogged=false,west=false]",
            "minecraft:cocoa[age=0,facing=north]",
            "minecraft:sculk_vein[down=false,east=false,north=true,south=false,up=false,waterlogged=false,west=false]",
            "minecraft:hanging_roots[waterlogged=false]", "minecraft:dead_bush", "minecraft:poppy", "minecraft:torchflower",
            "minecraft:big_dripleaf[facing=north,tilt=full,waterlogged=false]",
            "minecraft:big_dripleaf_stem[facing=north,waterlogged=false]", "minecraft:torchflower_crop[age=1]");
        for (String version : v.versions()) {
            for (String s : states) {
                assertThat(v.get(version, s)).as(version + " " + s).isNotNull();
            }
            boolean copper = !version.equals("1.21.4");
            assertThat(v.get(version, "minecraft:copper_wall_torch[facing=north]") != null).as(version).isEqualTo(copper);
        }
        VanillaCandidates.VState stem = v.get("26.3", "minecraft:big_dripleaf_stem[facing=north,waterlogged=false]");
        assertThat(stem.selection()).isEqualTo(Shape.box(5, 0, 9, 11, 16, 15));
        assertThat(stem.collision().isEmpty()).isTrue();
        assertThat(stem.light()).isZero();
    }

    @Test
    void addingTheTorchesMovesNoOtherCarrier() {
        List<Demand> all = HitboxDemands.all();
        List<Demand> without = all.stream().filter(d -> !TORCH_IDS.contains(d.blockId())).toList();
        assertThat(all.size() - without.size()).isEqualTo(10);
        for (VersionFamily f : VersionFamily.values()) {
            for (Profile p : Profile.values()) {
                Result before = HitboxAllocation.allocate(without, p, f, HitboxCarrier.standardContext());
                Result after = HitboxAllocation.standard().get(f.id() + "/" + p.id());
                for (Assignment a : before.assignments()) {
                    Assignment b = after.get(a.demand().blockId(), a.demand().appearance());
                    assertThat(b.state()).as(f.id() + " " + a.demand().id()).isEqualTo(a.state());
                    assertThat(b.mode()).isEqualTo(a.mode());
                    assertThat(b.hideTarget()).isEqualTo(a.hideTarget());
                }
            }
        }
    }
}
