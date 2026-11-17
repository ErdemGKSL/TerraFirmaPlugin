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
 * demand can take them), the dead standing torch TFC's model on a free state of light 0, the dead wall torch the pressed
 * wall buttons hitbox-only (an unpressed button predicts its press client-side), and adding them moves no other carrier.
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
        assertThat(dead.state()).isEqualTo("minecraft:red_mushroom");
        assertThat(dead.hideTarget()).isNull();
        for (String f : HitboxDemands.FACINGS) {
            Assignment wall = HitboxAllocation.agreed(HitboxDemands.DEAD_WALL_TORCH_ID, f);
            assertThat(wall.mode()).as(f).isEqualTo(Mode.HITBOX_ONLY);
            assertThat(wall.state()).isEqualTo("minecraft:oak_button[face=wall,facing=" + f + ",powered=true]");
            assertThat(wall.hideTarget()).isNull();
            assertThat(v.excludedState("minecraft:oak_button[face=wall,facing=" + f + ",powered=false]")).contains("PAR-HEAT-21");
        }
        for (String version : v.versions()) {
            for (String id : List.of(dead.state(), HitboxAllocation.agreed(HitboxDemands.DEAD_WALL_TORCH_ID, "north").state())) {
                assertThat(v.get(version, id).light()).as(version + " " + id).isZero();
            }
        }
        // The unlit redstone torches have the torch outline but are crafted in TFC: never a carrier.
        assertThat(v.policy("minecraft:redstone_torch").presence()).isEqualTo(Presence.PLAYER);
        assertThat(v.policy("minecraft:redstone_wall_torch").presence()).isEqualTo(Presence.PLAYER);
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
     * players in TFC, or makes client particles. So the button stays until a vanilla version adds a free unlit torch shape.
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
