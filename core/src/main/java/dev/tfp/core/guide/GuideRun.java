/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import java.util.Objects;

/** One piece of laid-out field guide text (PAR-DATA-04): styled text, a key binding name, a line break or a page break. */
public sealed interface GuideRun {
    /** Styled literal text (no line breaks). */
    record Text(String text, GuideStyle style) implements GuideRun {
        public Text {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
        }
    }

    /** The client's name of a key binding (e.g. {@code key.use}), drawn by the client in its own language. */
    record Keybind(String key, GuideStyle style) implements GuideRun {
        public Keybind {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(style, "style");
        }
    }

    /** Ends the current line. */
    record Break() implements GuideRun {
    }

    /** Ends the current page (no-op at the top of a page). */
    record PageBreak() implements GuideRun {
    }

    GuideRun BREAK = new Break();
    GuideRun PAGE_BREAK = new PageBreak();
}
