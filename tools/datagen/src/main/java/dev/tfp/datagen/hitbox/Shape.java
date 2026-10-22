/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.tfp.datagen.DatagenException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * A block shape (a VoxelShape) as a union of axis-aligned boxes in pixels (1/16 block, 0 to 16), immutable and in a
 * canonical form: the union is cut on the grid of all its box coordinates and the filled cells are merged greedily
 * (x, then z, then y), so two equal unions always have the same boxes in the same order.
 *
 * <p>All metrics are exact: volumes, intersections, connected components and centroids are computed on the
 * coordinate-compressed grid of the shapes involved, so 0.25 px and 0.5 px boxes need no finer voxel grid.
 */
public final class Shape {
    /** One box, {@code (x1, y1, z1)} to {@code (x2, y2, z2)} in pixels, min below max on every axis. */
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2) {
        public Box {
            if (!(x1 < x2 && y1 < y2 && z1 < z2)) {
                throw new DatagenException("empty or inverted box " + x1 + "," + y1 + "," + z1 + " " + x2 + "," + y2 + "," + z2);
            }
        }

        double volume() {
            return (x2 - x1) * (y2 - y1) * (z2 - z1);
        }

        boolean contains(double x, double y, double z) {
            return x > x1 && x < x2 && y > y1 && y < y2 && z > z1 && z < z2;
        }
    }

    public static final Shape EMPTY = new Shape(List.of());
    public static final Shape FULL = of(new Box(0, 0, 0, 16, 16, 16));

    private final List<Box> boxes;

    private Shape(List<Box> canonicalBoxes) {
        this.boxes = List.copyOf(canonicalBoxes);
    }

    public static Shape of(Box... boxes) {
        return canonical(Arrays.asList(boxes));
    }

    public static Shape of(List<Box> boxes) {
        return canonical(boxes);
    }

    /** {@code Block.box(x1, y1, z1, x2, y2, z2)}: pixel coordinates. */
    public static Shape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return of(new Box(x1, y1, z1, x2, y2, z2));
    }

    public List<Box> boxes() {
        return boxes;
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    public Shape union(Shape other) {
        List<Box> all = new ArrayList<>(boxes);
        all.addAll(other.boxes);
        return canonical(all);
    }

    /** {@code Shapes.join(this, other, BooleanOp.ONLY_FIRST)}. */
    public Shape minus(Shape other) {
        Grid g = new Grid(this, other);
        boolean[] cells = new boolean[g.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = g.a[i] && !g.b[i];
        }
        return canonical(g.merge(cells).boxes);
    }

    public double volume() {
        double v = 0;
        for (Box b : boxes) {
            v += b.volume(); // canonical boxes never overlap
        }
        return v;
    }

    /** The bounding box, or null for the empty shape. */
    public Box bounds() {
        if (boxes.isEmpty()) {
            return null;
        }
        double x1 = 16, y1 = 16, z1 = 16, x2 = 0, y2 = 0, z2 = 0;
        for (Box b : boxes) {
            x1 = Math.min(x1, b.x1);
            y1 = Math.min(y1, b.y1);
            z1 = Math.min(z1, b.z1);
            x2 = Math.max(x2, b.x2);
            y2 = Math.max(y2, b.y2);
            z2 = Math.max(z2, b.z2);
        }
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    /** Top of the shape in pixels (0 for the empty shape). */
    public double height() {
        Box b = bounds();
        return b == null ? 0 : b.y2;
    }

    /** Outline volume divided by the volume of its bounding box (1 for one box, 0 for the empty shape). */
    public double fill() {
        Box b = bounds();
        return b == null ? 0 : volume() / b.volume();
    }

    /** Volume-weighted centroid {x, y, z} in pixels, or null for the empty shape. */
    public double[] centroid() {
        if (boxes.isEmpty()) {
            return null;
        }
        double v = 0, x = 0, y = 0, z = 0;
        for (Box b : boxes) {
            double bv = b.volume();
            v += bv;
            x += bv * (b.x1 + b.x2) / 2;
            y += bv * (b.y1 + b.y2) / 2;
            z += bv * (b.z1 + b.z2) / 2;
        }
        return new double[] {x / v, y / v, z / v};
    }

    /** The 6-neighbour connected parts of the shape (touching faces connect; edges and corners do not). */
    public List<Shape> components() {
        Grid g = new Grid(this, EMPTY);
        int n = g.size();
        int[] label = new int[n];
        Arrays.fill(label, -1);
        List<Shape> out = new ArrayList<>();
        for (int start = 0; start < n; start++) {
            if (!g.a[start] || label[start] >= 0) {
                continue;
            }
            boolean[] part = new boolean[n];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            label[start] = out.size();
            while (!queue.isEmpty()) {
                int c = queue.poll();
                part[c] = true;
                for (int nb : g.neighbours(c)) {
                    if (g.a[nb] && label[nb] < 0) {
                        label[nb] = out.size();
                        queue.add(nb);
                    }
                }
            }
            out.add(canonical(g.merge(part).boxes));
        }
        return out;
    }

    /** Intersection over union of two shapes; two empty shapes are equal (1), empty against non-empty is 0. */
    public static double iou(Shape a, Shape b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty() ? 1 : 0;
        }
        Grid g = new Grid(a, b);
        double inter = 0, union = 0;
        for (int i = 0; i < g.size(); i++) {
            if (g.a[i] || g.b[i]) {
                double v = g.cellVolume(i);
                union += v;
                if (g.a[i] && g.b[i]) {
                    inter += v;
                }
            }
        }
        return inter / union;
    }

    /** This shape rotated about the block's vertical centre axis: clockwise quarter turns seen from above. */
    public Shape rotateY(int quarterTurns) {
        List<Box> out = new ArrayList<>();
        for (Box b : boxes) {
            Box r = b;
            for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
                r = new Box(16 - r.z2, r.y1, r.x1, 16 - r.z1, r.y2, r.x2);
            }
            out.add(r);
        }
        return canonical(out);
    }

    /** {@code [[x1,y1,z1,x2,y2,z2], ...]}. */
    public JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (Box b : boxes) {
            JsonArray box = new JsonArray();
            for (double d : new double[] {b.x1, b.y1, b.z1, b.x2, b.y2, b.z2}) {
                box.add(number(d));
            }
            a.add(box);
        }
        return a;
    }

    static Number number(double d) {
        if (d == Math.rint(d)) {
            return (long) d;
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros();
    }

    public static Shape fromJson(JsonElement e) {
        List<Box> out = new ArrayList<>();
        for (JsonElement box : e.getAsJsonArray()) {
            JsonArray b = box.getAsJsonArray();
            if (b.size() != 6) {
                throw new DatagenException("a shape box has 6 numbers, not " + b);
            }
            out.add(new Box(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble()));
        }
        return canonical(out);
    }

    /** {@code w x h x d} of the bounding box and the box count, for reports. */
    public String describe() {
        if (boxes.isEmpty()) {
            return "none";
        }
        Box b = bounds();
        String s = fmt(b.x2 - b.x1) + "x" + fmt(b.y2 - b.y1) + "x" + fmt(b.z2 - b.z1);
        if (b.y1 > 0) {
            s += " from y" + fmt(b.y1);
        }
        return boxes.size() == 1 ? s : s + " (" + boxes.size() + " boxes)";
    }

    static String fmt(double d) {
        return number(d).toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Shape s && s.boxes.equals(boxes);
    }

    @Override
    public int hashCode() {
        return boxes.hashCode();
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    private static Shape canonical(List<Box> boxes) {
        if (boxes.isEmpty()) {
            return EMPTY != null ? EMPTY : new Shape(List.of());
        }
        Grid g = new Grid(new Shape(boxes), new Shape(List.of()));
        return g.merge(g.a);
    }

    /** The coordinate-compressed grid of two shapes: which cells each one fills. */
    private static final class Grid {
        final double[] xs, ys, zs;
        final boolean[] a, b;

        Grid(Shape sa, Shape sb) {
            xs = coords(sa, sb, 0);
            ys = coords(sa, sb, 1);
            zs = coords(sa, sb, 2);
            a = fill(sa);
            b = fill(sb);
        }

        int nx() {
            return Math.max(0, xs.length - 1);
        }

        int ny() {
            return Math.max(0, ys.length - 1);
        }

        int nz() {
            return Math.max(0, zs.length - 1);
        }

        int size() {
            return nx() * ny() * nz();
        }

        int index(int i, int j, int k) {
            return (j * nz() + k) * nx() + i;
        }

        double cellVolume(int c) {
            int i = c % nx(), k = (c / nx()) % nz(), j = c / (nx() * nz());
            return (xs[i + 1] - xs[i]) * (ys[j + 1] - ys[j]) * (zs[k + 1] - zs[k]);
        }

        List<Integer> neighbours(int c) {
            int i = c % nx(), k = (c / nx()) % nz(), j = c / (nx() * nz());
            List<Integer> out = new ArrayList<>(6);
            if (i > 0) out.add(index(i - 1, j, k));
            if (i < nx() - 1) out.add(index(i + 1, j, k));
            if (j > 0) out.add(index(i, j - 1, k));
            if (j < ny() - 1) out.add(index(i, j + 1, k));
            if (k > 0) out.add(index(i, j, k - 1));
            if (k < nz() - 1) out.add(index(i, j, k + 1));
            return out;
        }

        private static double[] coords(Shape sa, Shape sb, int axis) {
            TreeSet<Double> s = new TreeSet<>();
            for (Shape sh : new Shape[] {sa, sb}) {
                for (Box bx : sh.boxes) {
                    s.add(axis == 0 ? bx.x1 : axis == 1 ? bx.y1 : bx.z1);
                    s.add(axis == 0 ? bx.x2 : axis == 1 ? bx.y2 : bx.z2);
                }
            }
            double[] out = new double[s.size()];
            int i = 0;
            for (double d : s) {
                out[i++] = d;
            }
            return out;
        }

        private boolean[] fill(Shape s) {
            boolean[] f = new boolean[size()];
            for (int j = 0; j < ny(); j++) {
                double y = (ys[j] + ys[j + 1]) / 2;
                for (int k = 0; k < nz(); k++) {
                    double z = (zs[k] + zs[k + 1]) / 2;
                    for (int i = 0; i < nx(); i++) {
                        double x = (xs[i] + xs[i + 1]) / 2;
                        for (Box bx : s.boxes) {
                            if (bx.contains(x, y, z)) {
                                f[index(i, j, k)] = true;
                                break;
                            }
                        }
                    }
                }
            }
            return f;
        }

        /** Greedy merge of the filled cells into boxes: along x, then z, then y. Deterministic. */
        Shape merge(boolean[] cells) {
            boolean[] left = cells.clone();
            List<Box> out = new ArrayList<>();
            for (int j = 0; j < ny(); j++) {
                for (int k = 0; k < nz(); k++) {
                    for (int i = 0; i < nx(); i++) {
                        if (!left[index(i, j, k)]) {
                            continue;
                        }
                        int i2 = i;
                        while (i2 + 1 < nx() && left[index(i2 + 1, j, k)]) {
                            i2++;
                        }
                        int k2 = k;
                        while (k2 + 1 < nz() && row(left, i, i2, j, k2 + 1)) {
                            k2++;
                        }
                        int j2 = j;
                        while (j2 + 1 < ny() && slab(left, i, i2, j2 + 1, k, k2)) {
                            j2++;
                        }
                        for (int jj = j; jj <= j2; jj++) {
                            for (int kk = k; kk <= k2; kk++) {
                                for (int ii = i; ii <= i2; ii++) {
                                    left[index(ii, jj, kk)] = false;
                                }
                            }
                        }
                        out.add(new Box(xs[i], ys[j], zs[k], xs[i2 + 1], ys[j2 + 1], zs[k2 + 1]));
                    }
                }
            }
            return new Shape(out);
        }

        private boolean row(boolean[] c, int i1, int i2, int j, int k) {
            for (int i = i1; i <= i2; i++) {
                if (!c[index(i, j, k)]) {
                    return false;
                }
            }
            return true;
        }

        private boolean slab(boolean[] c, int i1, int i2, int j, int k1, int k2) {
            for (int k = k1; k <= k2; k++) {
                if (!row(c, i1, i2, j, k)) {
                    return false;
                }
            }
            return true;
        }
    }
}
