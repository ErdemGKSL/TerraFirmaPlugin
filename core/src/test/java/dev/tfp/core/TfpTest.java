/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TfpTest {
    @Test
    void qualifiesIds() {
        assertThat(Tfp.id("rock/raw/granite")).isEqualTo("tfp:rock/raw/granite");
    }

    @Test
    void rejectsQualifiedOrEmptyPaths() {
        assertThatThrownBy(() -> Tfp.id("minecraft:stone")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tfp.id("")).isInstanceOf(IllegalArgumentException.class);
    }
}
