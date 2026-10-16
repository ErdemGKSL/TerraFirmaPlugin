/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dotted version with an optional pre-release qualifier, as used by Minecraft ({@code 1.21.11},
 * {@code 26.3}) and the client plugins ({@code 26.9.2}, {@code 2.2.0-SNAPSHOT-449}).
 *
 * <p>Ordering: numeric parts first (missing parts count as 0, so {@code 26.3 == 26.3.0}); then a
 * version without qualifier is newer than the same numbers with one ({@code 2.2.0 > 2.2.0-SNAPSHOT});
 * qualifiers compare token by token (numbers numerically, numbers before words). Build metadata after
 * {@code +} is ignored.
 */
public record Version(List<Integer> parts, String qualifier) implements Comparable<Version> {
    private static final Pattern FORMAT = Pattern.compile("(\\d+(?:\\.\\d+)*)(?:[-.]?([0-9A-Za-z][0-9A-Za-z.\\-]*))?");

    public Version {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a version needs at least one number");
        }
        parts = List.copyOf(parts);
        qualifier = qualifier == null ? "" : qualifier;
    }

    /** @throws IllegalArgumentException if {@code text} does not start with a number */
    public static Version parse(String text) {
        return tryParse(text).orElseThrow(() -> new IllegalArgumentException("not a version: '" + text + "'"));
    }

    public static Optional<Version> tryParse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.strip();
        int plus = s.indexOf('+');
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        Matcher m = FORMAT.matcher(s);
        if (!m.matches()) {
            return Optional.empty();
        }
        List<Integer> parts = new ArrayList<>();
        try {
            for (String p : m.group(1).split("\\.")) {
                parts.add(Integer.parseInt(p));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new Version(parts, m.group(2)));
    }

    /** First number: {@code 1} for 1.21.x, {@code 26} for 26.x. */
    public int major() {
        return parts.getFirst();
    }

    public boolean isPreRelease() {
        return !qualifier.isEmpty();
    }

    public boolean isOlderThan(Version other) {
        return compareTo(other) < 0;
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version o) {
        int n = Math.max(parts.size(), o.parts.size());
        for (int i = 0; i < n; i++) {
            int c = Integer.compare(part(i), o.part(i));
            if (c != 0) {
                return c;
            }
        }
        if (qualifier.isEmpty() || o.qualifier.isEmpty()) {
            return Boolean.compare(qualifier.isEmpty(), o.qualifier.isEmpty());
        }
        String[] a = qualifier.split("[.\\-]");
        String[] b = o.qualifier.split("[.\\-]");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int c = compareToken(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private int part(int i) {
        return i < parts.size() ? parts.get(i) : 0;
    }

    private static int compareToken(String a, String b) {
        boolean na = a.chars().allMatch(Character::isDigit) && !a.isEmpty();
        boolean nb = b.chars().allMatch(Character::isDigit) && !b.isEmpty();
        if (na && nb) {
            return new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));
        }
        if (na != nb) {
            return na ? -1 : 1;
        }
        return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
    }

    /** Equal by ordering: {@code 26.3} equals {@code 26.3.0}. */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        int end = parts.size();
        while (end > 1 && parts.get(end - 1) == 0) {
            end--;
        }
        return parts.subList(0, end).hashCode() * 31 + qualifier.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i));
        }
        if (!qualifier.isEmpty()) {
            sb.append('-').append(qualifier);
        }
        return sb.toString();
    }
}
