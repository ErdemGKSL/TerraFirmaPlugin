/*
 * TerraFirmaPlugin
 * Copyright (c) 2026 TerraFirmaPlugin contributors
 * Licensed under the EUPL-1.2 (see LICENSE).
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
