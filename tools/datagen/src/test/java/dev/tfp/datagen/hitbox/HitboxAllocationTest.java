/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation.Assignment;
import dev.tfp.datagen.hitbox.HitboxAllocation.Demand;
import dev.tfp.datagen.hitbox.HitboxAllocation.Mode;
import dev.tfp.datagen.hitbox.HitboxAllocation.Result;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-13: the budget pass over every demand, per profile x version family. */
class HitboxAllocationTest {
    @Test
    void everyProfileAndFamilyFitsWithOneLookPerState() {
        Map<String, Result> all = HitboxAllocation.standard();
        assertThat(all).hasSize(VersionFamily.values().length * Profile.values().length);
        for (Result r : all.values()) {
            Map<String, String> looks = new java.util.HashMap<>();
            for (Assignment a : r.assignments()) {
                if (a.pool()) {
                    continue;
                }
                String look = a.mode() == Mode.HITBOX_ONLY ? HitboxAllocation.TRANSPARENT : a.demand().look();
                String prev = looks.putIfAbsent(a.state(), look);
                assertThat(prev == null || prev.equals(look)).as(a.state() + " carries " + prev + " and " + look).isTrue();
                VanillaCandidates.BlockPolicy p = VanillaCandidates.load().policy(a.carrier().block());
                if (p.presence() == VanillaCandidates.Presence.NATURAL && a.mode() != Mode.VANILLA) {
                    assertThat(a.hideTarget()).as(a.state() + " is natural: real instances need a hide mapping").isNotNull();
                }
            }
            for (String target : r.hideMappings().values()) {
                assertThat(r.lookByState()).as("one hop: a hide target is never a carrier").doesNotContainKey(target);
            }
        }
    }

    @Test
    void deviceCarriers() {
        Assignment forge = HitboxAllocation.agreed("tfp:charcoal_forge", HitboxDemands.FORGE_APPEARANCE);
        assertThat(forge.state()).isEqualTo("minecraft:snow[layers=7]");
        assertThat(forge.mode()).isEqualTo(Mode.MODEL);
        assertThat(forge.hideTarget()).isEqualTo("minecraft:snow[layers=8]");
        for (String device : List.of("firepit", "grill", "pot")) {
            Assignment lit = HitboxAllocation.agreed("tfp:" + device, "lit");
            assertThat(lit.mode()).isEqualTo(Mode.VANILLA);
            assertThat(lit.hideTarget()).as("a vanilla look needs no hide mapping").isNull();
            assertThat(HitboxAllocation.agreed("tfp:" + device, "unlit").state()).isEqualTo(lit.state());
        }
        for (String f : HitboxDemands.FACINGS) {
            Assignment bellows = HitboxAllocation.agreed("tfp:bellows", f);
            assertThat(bellows.mode()).isEqualTo(Mode.VANILLA);
            assertThat(bellows.pool()).as("a vanilla look cannot use a CE pool (no model)").isFalse();
            assertThat(bellows.carrier().score()).isGreaterThanOrEqualTo(HitboxCarrier.MIN_SCORE);
        }
    }

    @Test
    void aTakenStateGoesToTheHeavierDemandAndTheOtherTakesItsNextBest() {
        List<Demand> demands = List.of(
            new Demand("tfp:a", "x", "tfc:charcoal_forge", 0, "tfp:block/a", false, 2),
            new Demand("tfp:b", "x", "tfc:charcoal_forge", 0, "tfp:block/b", false, 1),
            new Demand("tfp:c", "x", "tfc:charcoal_forge", 0, "tfp:block/a", false, 0));
        Result r = HitboxAllocation.allocate(demands, Profile.LEVEL_0, VersionFamily.V1_21, HitboxCarrier.standardContext());
        assertThat(r.get("tfp:a", "x").state()).isEqualTo("minecraft:snow[layers=7]");
        assertThat(r.get("tfp:b", "x").state()).isNotEqualTo("minecraft:snow[layers=7]").isNotEqualTo("minecraft:snow[layers=8]");
        assertThat(r.get("tfp:c", "x").state()).as("the same model shares the state").isEqualTo("minecraft:snow[layers=7]");
    }

    @Test
    void overflowThrows() {
        // The placed-item class has one owner carrier: a second, different look cannot get it.
        List<Demand> demands = List.of(
            new Demand("tfp:placed_item", "a", "tfc:placed_item[item_0=true,item_1=false,item_2=false,item_3=false]", 0,
                HitboxAllocation.TRANSPARENT, true, 1),
            new Demand("tfp:other", "a", "tfc:placed_item[item_0=true,item_1=false,item_2=false,item_3=false]", 0,
                "tfp:block/other", false, 0));
        assertThatThrownBy(() -> HitboxAllocation.allocate(demands, Profile.LEVEL_0, VersionFamily.V1_21,
            HitboxCarrier.standardContext())).isInstanceOf(DatagenException.class).hasMessageContaining("never degrade silently");
        // A rare demand with nothing above 0.5 takes hitbox-only mode instead.
        Result r = HitboxAllocation.allocate(List.of(new Demand("tfp:barrel", "open", "tfc:wood/barrel[axis=y,sealed=false]", 0,
            "tfp:block/barrel", true, 1)), Profile.LEVEL_0, VersionFamily.V1_21, HitboxCarrier.standardContext());
        assertThat(r.get("tfp:barrel", "open").mode()).isEqualTo(Mode.HITBOX_ONLY);
    }

    @Test
    void neverSharesAStateWithTheFullCubeAllocation() throws Exception {
        SortedMap<String, byte[]> files = Datagen.generate(RealCheckout.open().root(), TfcCheckout.PINNED_COMMIT).tree().files();
        Pattern state = Pattern.compile("state: '?(minecraft:[a-z0-9_]+(\\[[^\\]']*])?)'?");
        Set<String> carriers = new HashSet<>();
        HitboxAllocation.standard().values().forEach(r -> carriers.addAll(r.lookByState().keySet()));
        HitboxAllocation.standard().values().forEach(r -> carriers.addAll(r.hideMappings().values()));
        int checked = 0;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith("visuals/") || !e.getKey().endsWith("visual_templates.yml")) {
                continue;
            }
            Matcher m = state.matcher(new String(e.getValue(), StandardCharsets.UTF_8));
            while (m.find()) {
                checked++;
                assertThat(carriers).as(e.getKey() + " uses " + m.group(1)).doesNotContain(m.group(1));
            }
        }
        assertThat(checked).isPositive();
        for (VersionFamily f : VersionFamily.values()) {
            assertThat(files).containsKey(HitboxReport.PREFIX + f.id() + ".md");
            String report = new String(files.get(HitboxReport.PREFIX + f.id() + ".md"), StandardCharsets.UTF_8);
            assertThat(report).contains("tfp:charcoal_forge#heat_*", "`minecraft:snow[layers=7]`", "tfc:placed_item",
                "## Hide mappings");
        }
    }
}
