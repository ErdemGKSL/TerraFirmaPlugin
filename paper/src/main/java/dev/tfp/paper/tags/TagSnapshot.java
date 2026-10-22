/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import dev.tfp.core.Tfp;
import dev.tfp.core.json.Ids;
import dev.tfp.core.tag.TagRegistry;
import dev.tfp.core.tag.TagType;

/**
 * One consistent view of the tags (PAR-INFRA-07): our resolved {@link TagRegistry} of one registry snapshot, the
 * nesting {@link TagRules} and the server's tags for vanilla ids. Immutable apart from the server side; take one per
 * operation from {@link Tags#snapshot()} when asking several questions.
 *
 * <p>An id is in a tag when our registry lists it (CraftEngine ids and vanilla ids TFC names directly), or, for an
 * id outside our namespace, when a server tag or a common ({@code c:}) tag of {@link TagRules}' table reached from
 * the tag contains it (vanilla and common tags TFC includes or adds to), minus the tag's {@code remove} entries.
 *
 * @param registryVersion the {@link dev.tfp.core.registry.Registries#version()} the registry side came from
 */
public record TagSnapshot(long registryVersion, TagRegistry registry, TagRules rules, ServerTags server) {
    /** Whether {@code id} ({@code ns:path}) is in {@code tag} ({@code ns:path}, {@code #} and {@code tfc:} accepted). */
    public boolean is(TagType type, String id, String tag) {
        String t = normalise(tag);
        boolean ours = Ids.namespace(id).equals(Tfp.NAMESPACE);
        if (registry.contains(type, t, id)) {
            // Our ids: the registry knows every member and applied every remove. A vanilla id the registry lists may
            // still be removed through a server or common tag it cannot see (TagRules#removedByForeign).
            return ours || !rules.removedByForeign(type, t, id, members());
        }
        if (ours) {
            return false; // our ids are never in server tags: CraftEngine content is not in vanilla registries
        }
        return rules.viaServer(type, t, id, members());
    }

    private TagRules.Members members() {
        return new TagRules.Members() {
            @Override
            public boolean ours(TagType ty, String tg, String i) {
                return registry.contains(ty, tg, i);
            }

            @Override
            public boolean server(TagType ty, String tg, String i) {
                return server.isTagged(ty, tg, i);
            }
        };
    }

    /** Whether the tag is defined by our data or known to exist outside it. */
    public boolean exists(TagType type, String tag) {
        return registry.exists(type, normalise(tag));
    }

    /**
     * {@code #tfc:foo} / {@code tfc:foo} / {@code tfp:foo} → {@code tfp:foo}: datagen renames TFC's namespace, and
     * ported code may quote TFC's tag names ({@code TFCTags}).
     */
    public static String normalise(String tag) {
        String t = Ids.parse(tag.startsWith("#") ? tag.substring(1) : tag);
        return Ids.namespace(t).equals(Ids.TFC) ? Tfp.NAMESPACE + ':' + Ids.path(t) : t;
    }
}
