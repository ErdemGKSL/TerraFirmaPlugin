/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class VersionTest {
    private static Version v(String s) {
        return Version.parse(s);
    }

    @Test
    void parsesMinecraftAndPluginVersions() {
        assertThat(v("1.21.11").parts()).containsExactly(1, 21, 11);
        assertThat(v("26.3").parts()).containsExactly(26, 3);
        assertThat(v("2.2.0-SNAPSHOT-449").qualifier()).isEqualTo("SNAPSHOT-449");
        assertThat(v("3.5.1+build.7")).isEqualTo(v("3.5.1"));
        assertThat(v("1.21.11-rc1").qualifier()).isEqualTo("rc1");
        assertThat(Version.tryParse("git-abc")).isEmpty();
        assertThat(Version.tryParse(null)).isEmpty();
        assertThatThrownBy(() -> v("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersNumericallyNotLexically() {
        assertThat(v("1.21.11")).isGreaterThan(v("1.21.4"));
        assertThat(v("26.10")).isGreaterThan(v("26.9"));
        assertThat(v("26.1")).isGreaterThan(v("1.21.11"));
        assertThat(v("26.3")).isEqualByComparingTo(v("26.3.0")).isEqualTo(v("26.3.0"));
        assertThat(v("26.3").hashCode()).isEqualTo(v("26.3.0").hashCode());
    }

    @Test
    void preReleaseSortsBeforeRelease() {
        assertThat(v("2.2.0-SNAPSHOT-449")).isLessThan(v("2.2.0"));
        assertThat(v("2.2.0-SNAPSHOT-449")).isGreaterThan(v("2.1.9"));
        assertThat(v("2.2.0-SNAPSHOT-450")).isGreaterThan(v("2.2.0-SNAPSHOT-449"));
        assertThat(v("2.2.0-SNAPSHOT-1000")).isGreaterThan(v("2.2.0-SNAPSHOT-999"));
        assertThat(v("1.21.11-rc1")).isLessThan(v("1.21.11"));
    }

    @Test
    void sortsAList() {
        List<Version> sorted = List.of(v("26.3"), v("1.21.4"), v("26.1"), v("1.21.11")).stream().sorted().toList();
        assertThat(sorted).extracting(Version::toString).containsExactly("1.21.4", "1.21.11", "26.1", "26.3");
    }
}
