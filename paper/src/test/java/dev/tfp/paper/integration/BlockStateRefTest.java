/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockStateRefTest {
    @Test
    void parsesAndPrintsCanonically() {
        BlockStateRef r = BlockStateRef.parse("tfp:crop/wheat[stage=3, age=1]");
        assertThat(r.id()).isEqualTo("tfp:crop/wheat");
        assertThat(r.namespace()).isEqualTo("tfp");
        assertThat(r.properties()).containsExactly(Map.entry("age", "1"), Map.entry("stage", "3"));
        assertThat(r).hasToString("tfp:crop/wheat[age=1,stage=3]");
        assertThat(BlockStateRef.parse(r.toString())).isEqualTo(r);
        assertThat(BlockStateRef.parse("tfp:rock/raw/granite")).isEqualTo(BlockStateRef.of("tfp:rock/raw/granite"));
        assertThat(BlockStateRef.parse("tfp:a[]")).isEqualTo(BlockStateRef.of("tfp:a"));
    }

    @Test
    void equalityIgnoresPropertyOrder() {
        assertThat(BlockStateRef.of("tfp:a").with("b", "1").with("a", "2"))
                .isEqualTo(BlockStateRef.of("tfp:a", Map.of("a", "2", "b", "1")));
    }

    @Test
    void rejectsMalformed() {
        assertThatThrownBy(() -> BlockStateRef.of("noNamespace")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockStateRef.of("a:b:c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockStateRef.parse("tfp:a[x=1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockStateRef.parse("tfp:a[x]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockStateRef.parse("tfp:a[x=1,x=2]")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propertiesAreImmutable() {
        BlockStateRef r = BlockStateRef.parse("tfp:a[x=1]");
        assertThatThrownBy(() -> r.properties().put("y", "2")).isInstanceOf(UnsupportedOperationException.class);
    }
}
