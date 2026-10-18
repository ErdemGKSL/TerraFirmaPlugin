/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The rules that are not golden rows: ids, the vanilla damage tags, and the special cases of TFC's code. */
class HealthRulesTest {
    @Test
    void tfcDamageTypesKeepTheirExhaustionAndBurning() {
        assertThat(TfpDamageType.GRILL.id()).isEqualTo("tfp:grill");
        assertThat(TfpDamageType.GRILL.exhaustion()).isEqualTo(0.1f);
        assertThat(TfpDamageType.GRILL.burning()).isTrue();
        assertThat(TfpDamageType.POT.exhaustion()).isEqualTo(0.1f);
        assertThat(TfpDamageType.POT.burning()).isTrue();
        for (TfpDamageType t : List.of(TfpDamageType.DEHYDRATION, TfpDamageType.PLUCK, TfpDamageType.LOBSTER_TRAP)) {
            assertThat(t.exhaustion()).as(t.id()).isZero();
            assertThat(t.burning()).as(t.id()).isFalse();
        }
        assertThat(TfpDamageType.LOBSTER_TRAP.id()).isEqualTo("tfp:lobster_trap");
    }

    @Test
    void vanillaDamageTypesMapByTfcTags() {
        // tfc:is_piercing and tfc:is_crushing from the generated data at c3b4d9536
        assertThat(PhysicalDamageType.fromVanillaDamageType("minecraft:arrow")).contains(PhysicalDamageType.PIERCING);
        assertThat(PhysicalDamageType.fromVanillaDamageType("cactus")).contains(PhysicalDamageType.PIERCING);
        assertThat(PhysicalDamageType.fromVanillaDamageType("minecraft:falling_anvil")).contains(PhysicalDamageType.CRUSHING);
        assertThat(PhysicalDamageType.fromVanillaDamageType("minecraft:fall")).contains(PhysicalDamageType.CRUSHING);
        assertThat(PhysicalDamageType.fromVanillaDamageType("minecraft:player_attack")).isEmpty();
        assertThat(PhysicalDamageType.fromVanillaDamageType("minecraft:lava")).isEmpty();
    }

    @Test
    void typelessSourceUsesTheHighestResistance() {
        PhysicalResistance r = new PhysicalResistance(1f, 7f, 3f);
        assertThat(r.value(Optional.empty())).isEqualTo(7f);
        assertThat(r.value(Optional.of(PhysicalDamageType.CRUSHING))).isEqualTo(1f);
        assertThat(r.value(Optional.of(PhysicalDamageType.SLASHING))).isEqualTo(3f);
        assertThat(r.value(Optional.of(PhysicalDamageType.PIERCING))).isEqualTo(7f);
    }

    @Test
    void naturalEntityResistanceNeedsATypedSource() {
        // a typeless punch on a skeleton is not made immune by its 1.0E9 piercing
        float typeless = PhysicalResistances.multiplier(Optional.empty(), PhysicalResistances.SKELETONS, List.of());
        assertThat(typeless).isEqualTo(1f); // no armour, no type: nothing resists
        float piercing = PhysicalResistances.multiplier(Optional.of(PhysicalDamageType.PIERCING),
                PhysicalResistances.SKELETONS, List.of());
        assertThat(piercing).isZero();
    }

    @Test
    void unlimitedDamageIsNotScaled() {
        assertThat(HealthScale.playerDamage(Float.MAX_VALUE, 0.5f, 0.85f)).isEqualTo(Float.MAX_VALUE);
    }
}
