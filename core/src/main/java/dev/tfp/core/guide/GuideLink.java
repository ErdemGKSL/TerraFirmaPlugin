/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

/**
 * Link targets inside the field guide books (PAR-DATA-04), as stored in {@link GuideStyle#link()}.
 *
 * <ul>
 *   <li>{@code index}: the contents book;</li>
 *   <li>{@code entry:<entry id>} or {@code entry:<entry id>@<volume>}: an entry's book (volume 1 when omitted);</li>
 *   <li>{@code url:<https url>}: an external page;</li>
 *   <li>{@code page:<n>}: page {@code n} (1-based) of the open book.</li>
 * </ul>
 */
public final class GuideLink {
    public static final String INDEX = "index";
    public static final String ENTRY = "entry:";
    public static final String URL = "url:";
    public static final String PAGE = "page:";

    private GuideLink() {
    }

    public static String page(int page) {
        return PAGE + page;
    }

    public static String entry(String id) {
        return ENTRY + id;
    }

    public static String entry(String id, int volume) {
        return volume <= 1 ? entry(id) : ENTRY + id + "@" + volume;
    }

    /**
     * A Patchouli link parameter ({@code category/entry}, optionally {@code #anchor}, or an http(s) URL) as a target.
     * Anchors are dropped: a vanilla book always opens at its first page.
     */
    public static String fromPatchouli(String param, String namespace) {
        if (param.startsWith("http://") || param.startsWith("https://")) {
            return URL + param;
        }
        int hash = param.indexOf('#');
        String path = hash >= 0 ? param.substring(0, hash) : param;
        return entry(path.indexOf(':') >= 0 ? path : namespace + ":" + path);
    }

    /** The entry id of an {@code entry:} target, or null. */
    public static String entryId(String target) {
        if (target == null || !target.startsWith(ENTRY)) {
            return null;
        }
        String rest = target.substring(ENTRY.length());
        int at = rest.lastIndexOf('@');
        return at >= 0 ? rest.substring(0, at) : rest;
    }

    /** The volume (1-based) of an {@code entry:} target; 1 when it has none. */
    public static int volume(String target) {
        String rest = target.substring(ENTRY.length());
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(rest.substring(at + 1)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
