/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import dev.tfp.core.tag.TagType;
import org.bukkit.Bukkit;
import org.bukkit.Fluid;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

/**
 * The server's own (vanilla and datapack) tags, for vanilla ids (PAR-INFRA-07). Any thread: it only reads the
 * server's tag and registry maps.
 */
public interface ServerTags {
    /** Whether the server's tag {@code tag} of {@code type} contains vanilla {@code id}; false for unknown tags/ids. */
    boolean isTagged(TagType type, String tag, String id);

    /** No server tags (tests, and before the server is up). */
    ServerTags NONE = (type, tag, id) -> false;

    /** Bukkit's {@link Bukkit#getTag} over the material, fluid and entity type registries. */
    ServerTags BUKKIT = new ServerTags() {
        @Override
        public boolean isTagged(TagType type, String tag, String id) {
            NamespacedKey tagKey = NamespacedKey.fromString(tag);
            NamespacedKey idKey = NamespacedKey.fromString(id);
            if (tagKey == null || idKey == null) {
                return false;
            }
            return switch (type) {
                case BLOCK -> material(Tag.REGISTRY_BLOCKS, tagKey, idKey, true);
                case ITEM -> material(Tag.REGISTRY_ITEMS, tagKey, idKey, false);
                case FLUID -> tagged(Tag.REGISTRY_FLUIDS, tagKey, Fluid.class, Registry.FLUID.get(idKey));
                case ENTITY_TYPE -> tagged(Tag.REGISTRY_ENTITY_TYPES, tagKey, EntityType.class,
                        Registry.ENTITY_TYPE.get(idKey));
            };
        }

        private boolean material(String registry, NamespacedKey tagKey, NamespacedKey idKey, boolean block) {
            Material m = Registry.MATERIAL.get(idKey);
            if (m == null || (block ? !m.isBlock() : !m.isItem())) {
                return false;
            }
            return tagged(registry, tagKey, Material.class, m);
        }

        private <T extends Keyed> boolean tagged(String registry, NamespacedKey tagKey, Class<T> cls, T value) {
            if (value == null) {
                return false;
            }
            Tag<T> t = Bukkit.getTag(registry, tagKey, cls);
            return t != null && t.isTagged(value);
        }
    };
}
