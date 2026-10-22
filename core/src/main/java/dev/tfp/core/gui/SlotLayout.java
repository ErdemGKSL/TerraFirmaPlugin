/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PAR-INFRA-03 {@code fw-slot-layouts}: where a TFC screen's slots land in a chest. A vanilla client draws container
 * slots only on the chest grid ({@link ChestGeometry}), so a TFC screen is moved by a vertical offset {@code dy} (the
 * datagen moves its art by the same offset) and every TFC slot must then sit exactly on a grid cell. TFC's slot order
 * (its block entity slot index) is kept: entry {@code i} of {@link #chestSlots} is TFC slot {@code i}.
 *
 * @param chest      the chest the screen uses
 * @param dy         added to every TFC y (window pixels)
 * @param chestSlots chest slot index of each TFC slot, in TFC order
 */
public record SlotLayout(ChestGeometry chest, int dy, List<Integer> chestSlots) {
    public SlotLayout {
        chestSlots = List.copyOf(chestSlots);
        if (chestSlots.stream().distinct().count() != chestSlots.size()) {
            throw new IllegalArgumentException("two TFC slots share a chest slot: " + chestSlots);
        }
    }

    /** One TFC slot position ({@code Slot.x, Slot.y}, the item position). */
    public record Point(int x, int y) {
    }

    /**
     * The layout of TFC slots {@code tfcSlots} moved by {@code dy}. Fails when a moved slot is off the chest grid: such a
     * screen needs its art re-laid out slot by slot, which no screen needs yet.
     */
    public static SlotLayout of(ChestGeometry chest, int dy, List<Point> tfcSlots) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < tfcSlots.size(); i++) {
            Point p = tfcSlots.get(i);
            int index = chest.slotAt(p.x(), p.y() + dy);
            if (index < 0) {
                throw new IllegalArgumentException("TFC slot " + i + " at (" + p.x() + ", " + p.y() + ") + dy " + dy
                    + " is off the " + chest.rows() + "-row chest grid");
            }
            out.add(index);
        }
        return new SlotLayout(chest, dy, out);
    }

    /**
     * The {@code dy} that puts TFC slot {@code p} on chest row {@code row}: {@code 18 + 18 * row - p.y}.
     */
    public static int dyFor(Point p, int row) {
        return ChestGeometry.SLOT_Y0 + ChestGeometry.SLOT * row - p.y();
    }

    /** The chest slot under a TFC widget (button) rectangle: the slot whose 16 x 16 interior it covers most, or -1. */
    public int slotUnder(int x, int y, int width, int height) {
        int best = -1;
        int bestArea = 0;
        for (int i = 0; i < chest.size(); i++) {
            int sx = chest.slotX(i);
            int sy = chest.slotY(i);
            int w = Math.min(sx + 16, x + width) - Math.max(sx, x);
            int h = Math.min(sy + 16, y + dy + height) - Math.max(sy, y + dy);
            int area = w > 0 && h > 0 ? w * h : 0;
            if (area > bestArea) {
                bestArea = area;
                best = i;
            }
        }
        return best;
    }

    /** The chest slot of TFC slot {@code tfcSlot}. */
    public int chestSlot(int tfcSlot) {
        return chestSlots.get(tfcSlot);
    }

    /** The TFC slot index of chest slot {@code chestSlot}, or -1 for a chest slot no TFC slot uses. */
    public int tfcSlot(int chestSlot) {
        return chestSlots.indexOf(chestSlot);
    }

    /** The chest slots in ascending TFC order, the order TFC's {@code moveItemStackTo} fills them in. */
    public List<Integer> fillOrder() {
        return Collections.unmodifiableList(chestSlots);
    }
}
