/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-DECO-03: the sign text value, its editor limits and its round trip. */
class SignTextTest {
    @Test
    void emptySignRoundTrips() {
        assertThat(SignText.decode(SignText.EMPTY.encode())).isEqualTo(SignText.EMPTY);
        assertThat(SignText.EMPTY.front().isEmpty()).isTrue();
    }

    @Test
    void textWithSeparatorsRoundTrips() {
        SignText.Side front = new SignText.Side(List.of("a\\nb", "tab\there", "pipe|bar", "line\nbreak"), "red", true);
        SignText t = new SignText(front, SignText.Side.EMPTY.withLine(2, "back").withColor("blue"), true);
        SignText back = SignText.decode(t.encode());
        assertThat(back).isEqualTo(t);
        assertThat(back.front().lines().get(0)).isEqualTo("a\\nb");
        assertThat(back.front().lines().get(3)).isEqualTo("line\nbreak");
        assertThat(back.waxed()).isTrue();
        assertThat(back.back().color()).isEqualTo("blue");
    }

    @Test
    void sanitizeCutsToNinetyPixelsAndStripsControls() {
        List<String> out = SignText.sanitize(List.of("abcdefghijklmnop", "x\u0007y", "\u0080z"), s -> s.length() * 6);
        assertThat(out).hasSize(4);
        assertThat(out.get(0)).isEqualTo("abcdefghijklmno"); // 15 chars x 6 = 90
        assertThat(out.get(1)).isEqualTo("xy");
        assertThat(out.get(2)).isEqualTo("z");
        assertThat(out.get(3)).isEmpty();
    }

    @Test
    void sanitizeDropsExtraLinesAndNulls() {
        List<String> raw = new java.util.ArrayList<>(List.of("1", "2", "3", "4", "5"));
        raw.set(1, null);
        assertThat(SignText.sanitize(raw, String::length)).containsExactly("1", "", "3", "4");
    }

    @Test
    void sidesAreIndependent() {
        SignText t = SignText.EMPTY.withSide(true, SignText.Side.EMPTY.withLine(0, "front"));
        assertThat(t.side(true).lines().get(0)).isEqualTo("front");
        assertThat(t.side(false).isEmpty()).isTrue();
    }

    @Test
    void rejectsBadValues() {
        assertThatThrownBy(() -> new SignText.Side(List.of("a"), "black", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SignText.Side.EMPTY.withColor("mauve")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SignText.decode("x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SignText.COLORS).hasSize(16).contains(SignText.DEFAULT_COLOR);
    }
}
