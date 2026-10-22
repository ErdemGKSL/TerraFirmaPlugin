/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.DatagenException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One CraftEngine appearance of a device block (PAR-INFRA-08): the vanilla state that carries it (its hitbox, its
 * collision and, unless the look is {@code transparent}, its model) and the display entities drawn on top of it.
 *
 * <p>Three appearance strategies ({@link #strategy()}):
 * <ul>
 *   <li>{@link Strategy#REPURPOSED_STATE}: an explicit vanilla state that never occurs in a TFC world, optionally with our
 *       own model (the charcoal forge on {@code snow[layers=7]}, the bellows on a smoker). Display entities may be added
 *       as an overlay (the forge's lit levels).</li>
 *   <li>{@link Strategy#AUTO_STATE}: a free state from one of CraftEngine's {@code auto_state} pools, with our model.</li>
 *   <li>{@link Strategy#ENTITY_RENDERER}: the carrier is drawn empty ({@code transparent: true}) and the whole look comes
 *       from {@code entity_renderer} elements; the carrier only gives the hitbox. For rare blocks whose look varies
 *       beyond what one block model per state can show.</li>
 * </ul>
 *
 * @param name       the appearance name, referenced by the variants
 * @param carrier    the vanilla state that carries the appearance (hitbox and collision)
 * @param model      our block model id, or null to keep the carrier's vanilla look (or for a transparent carrier)
 * @param transparent whether the carrier is drawn with the empty model
 * @param renderers  the display entities drawn over the carrier, in order
 */
public record DeviceAppearance(String name, Carrier carrier, String model, boolean transparent, List<Renderer> renderers) {
    /** The appearance strategy, see the class comment. */
    public enum Strategy {
        REPURPOSED_STATE, AUTO_STATE, ENTITY_RENDERER
    }

    /**
     * The hitbox carrier: exactly one of an explicit vanilla state ({@code minecraft:snow[layers=7]}) or an
     * {@code auto_state} pool (with an optional share key: appearances with the same key share one pool state).
     */
    public record Carrier(String state, String autoStatePool, String autoStateId) {
        public Carrier {
            if ((state == null) == (autoStatePool == null)) {
                throw new DatagenException("a device carrier is either an explicit state or an auto_state pool, not "
                    + (state == null ? "neither" : "both"));
            }
            if (state != null && !state.matches("minecraft:[a-z0-9_]+(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?")) {
                throw new DatagenException("device carrier " + state + " is not a full vanilla block state");
            }
            if (autoStatePool != null && !autoStatePool.matches("[a-z_]+")) {
                throw new DatagenException("device carrier auto_state pool " + autoStatePool + " is not a pool name");
            }
            if (autoStateId != null && autoStatePool == null) {
                throw new DatagenException("an auto_state share key needs an auto_state pool");
            }
        }

        public static Carrier state(String state) {
            return new Carrier(state, null, null);
        }

        public static Carrier pool(String pool) {
            return new Carrier(null, pool, null);
        }

        public static Carrier pool(String pool, String shareKey) {
            return new Carrier(null, pool, shareKey);
        }

        void into(Map<String, Object> appearance) {
            if (state != null) {
                appearance.put("state", state);
            } else if (autoStateId == null) {
                appearance.put("auto_state", autoStatePool);
            } else {
                Map<String, Object> auto = new LinkedHashMap<>();
                auto.put("type", autoStatePool);
                auto.put("id", autoStateId);
                appearance.put("auto_state", auto);
            }
        }
    }

    /**
     * One {@code item_display} element of an {@code entity_renderer}: a display-only item (declared by the spec, drawn
     * with {@code tfp:item/render}) with an optional transform and brightness.
     *
     * @param item        the display item id
     * @param scale       uniform scale, or null for 1
     * @param translation {@code "x,y,z"} in blocks, or null
     * @param rotation    {@code "x,y,z"} in degrees, or null
     * @param blockLight  the fixed block light 0 to 15, or null to take the world's light
     * @param skyLight    the fixed sky light 0 to 15 (needs {@code blockLight})
     */
    public record Renderer(String item, BigDecimal scale, String translation, String rotation, Integer blockLight,
            Integer skyLight) {
        public Renderer {
            if (item == null || !item.startsWith("tfp:")) {
                throw new DatagenException("device renderer item " + item + " is not one of our items");
            }
            if ((blockLight == null) != (skyLight == null)) {
                throw new DatagenException("device renderer " + item + ": brightness needs both block and sky light");
            }
            if (blockLight != null && (blockLight < 0 || blockLight > 15 || skyLight < 0 || skyLight > 15)) {
                throw new DatagenException("device renderer " + item + ": brightness outside 0 to 15");
            }
        }

        public static Renderer item(String item) {
            return new Renderer(item, null, null, null, null, null);
        }

        public Renderer scaled(BigDecimal s) {
            return new Renderer(item, s, translation, rotation, blockLight, skyLight);
        }

        public Renderer translated(String t) {
            return new Renderer(item, scale, t, rotation, blockLight, skyLight);
        }

        public Renderer rotated(String r) {
            return new Renderer(item, scale, translation, r, blockLight, skyLight);
        }

        public Renderer lit(int block, int sky) {
            return new Renderer(item, scale, translation, rotation, block, sky);
        }

        Map<String, Object> yaml() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", item);
            if (scale != null) {
                m.put("scale", scale);
            }
            if (translation != null) {
                m.put("translation", translation);
            }
            if (rotation != null) {
                m.put("rotation", rotation);
            }
            if (blockLight != null) {
                Map<String, Object> brightness = new LinkedHashMap<>();
                brightness.put("block_light", blockLight);
                brightness.put("sky_light", skyLight);
                m.put("brightness", brightness);
            }
            return m;
        }
    }

    public DeviceAppearance {
        if (name == null || !name.matches("[a-z0-9_]+")) {
            throw new DatagenException("device appearance name " + name + " is not a lower-case identifier");
        }
        if (carrier == null) {
            throw new DatagenException("device appearance " + name + " has no carrier");
        }
        renderers = List.copyOf(renderers);
        if (transparent && model != null) {
            throw new DatagenException("device appearance " + name + " is transparent and has a model");
        }
        if (transparent && renderers.isEmpty()) {
            throw new DatagenException("device appearance " + name + " is transparent and draws nothing");
        }
        if (carrier.autoStatePool() != null && model == null) {
            throw new DatagenException("device appearance " + name + " takes an auto_state without a model: the pool"
                + " state's vanilla look would show");
        }
    }

    /** The carrier's vanilla look, no displays. */
    public static DeviceAppearance vanilla(String name, Carrier carrier) {
        return new DeviceAppearance(name, carrier, null, false, List.of());
    }

    /** The carrier drawn with our block model. */
    public static DeviceAppearance modelled(String name, Carrier carrier, String model) {
        return new DeviceAppearance(name, carrier, model, false, List.of());
    }

    /** The carrier drawn empty, the look made of display entities. */
    public static DeviceAppearance rendered(String name, Carrier carrier, List<Renderer> renderers) {
        return new DeviceAppearance(name, carrier, null, true, renderers);
    }

    /** This appearance with display entities drawn over its carrier's model. */
    public DeviceAppearance withOverlay(List<Renderer> overlay) {
        List<Renderer> all = new ArrayList<>(renderers);
        all.addAll(overlay);
        return new DeviceAppearance(name, carrier, model, transparent, all);
    }

    public Strategy strategy() {
        if (transparent) {
            return Strategy.ENTITY_RENDERER;
        }
        return carrier.autoStatePool() != null ? Strategy.AUTO_STATE : Strategy.REPURPOSED_STATE;
    }

    /** The CraftEngine {@code appearances.<name>} map: carrier, model or transparent, entity_renderer. */
    Map<String, Object> yaml() {
        Map<String, Object> m = new LinkedHashMap<>();
        carrier.into(m);
        if (model != null) {
            m.put("model", model);
        }
        if (transparent) {
            m.put("transparent", true);
        }
        if (!renderers.isEmpty()) {
            List<Object> elements = new ArrayList<>();
            renderers.forEach(r -> elements.add(r.yaml()));
            m.put("entity_renderer", elements);
        }
        return m;
    }
}
