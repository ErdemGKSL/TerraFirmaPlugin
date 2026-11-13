/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.itembar;

import java.util.Objects;

/**
 * The bar a stack shows (the outcome of {@link ItemBarRegistry#shown}).
 *
 * @param id    the {@link ItemBar#id()} of the winning bar
 * @param width the width in {@code [0, 13]}
 * @param color the RGB colour TFC draws it in
 */
public record ShownBar(String id, int width, int color) {
    public ShownBar {
        Objects.requireNonNull(id, "id");
        if (width < 0 || width > ItemBar.WIDTH) {
            throw new IllegalArgumentException("width " + width + " outside [0, " + ItemBar.WIDTH + "]");
        }
    }
}
