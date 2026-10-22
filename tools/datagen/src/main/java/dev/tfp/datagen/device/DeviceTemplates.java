/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.Yaml;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared CraftEngine templates of device blocks (PAR-INFRA-08), written to
 * {@code craftengine/tfp/configuration/templates/device.yml}:
 *
 * <ul>
 *   <li>{@code tfp:block/device}: the block settings ({@code tfp:settings/device} with the caller's
 *       {@code device_hardness} and {@code device_resistance}), the self loot ({@code tfp:loot_table/self}: TFC device
 *       blocks drop themselves) and the {@code tfp:device} behaviour (PAR-INFRA-02: the block entity record, use and
 *       neighbour routing, inventory drop on removal). The caller writes its {@code states} beside the template call;
 *       CraftEngine deep-merges them in.</li>
 *   <li>{@code tfp:block/device_rendered}: the same for devices whose look is drawn by {@code entity_renderer} display
 *       entities over a hitbox carrier. The carrier's model may be empty, so the server must not treat the block as
 *       occluding, suffocating or view-blocking: {@code can_occlude}, {@code is_suffocating} and
 *       {@code is_view_blocking} are false (CraftEngine otherwise derives them from the vanilla carrier state).</li>
 * </ul>
 *
 * <p>Both templates are written out in full rather than one calling the other, so each expands in one step. Argument
 * names carry the {@code device_} prefix because the caller's argument always wins in CraftEngine templates.
 */
public final class DeviceTemplates {
    public static final String OUTPUT = "craftengine/tfp/configuration/templates/device.yml";
    public static final String DEVICE = "tfp:block/device";
    public static final String DEVICE_RENDERED = "tfp:block/device_rendered";
    static final String SETTINGS = "tfp:settings/device";
    static final String LOOT_SELF = "tfp:loot_table/self";
    static final String BLOCK_ITEM = "tfp:item/block_item";
    static final String RENDER_ITEM = "tfp:item/render";
    static final String BEHAVIOUR = "tfp:device";
    static final String ARG_HARDNESS = "device_hardness";
    static final String ARG_RESISTANCE = "device_resistance";

    static final String HEADER = DeviceYaml.HEADER + "\n"
        + "tfp: device block templates (PAR-INFRA-08). Call with device_hardness and device_resistance and write the\n"
        + "states beside the call. tfp:block/device: settings, self loot, the tfp:device behaviour.\n"
        + "tfp:block/device_rendered: the same, not occluding, suffocating or view-blocking (entity_renderer looks).";

    private DeviceTemplates() {
    }

    /** Writes the device templates. */
    public static void run(OutputTree out) {
        out.put(OUTPUT, Yaml.write(HEADER, yaml()));
    }

    /** {@code templates#device: {tfp:block/device, tfp:block/device_rendered}}. */
    public static Map<String, Object> yaml() {
        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put(DEVICE, body(false));
        templates.put(DEVICE_RENDERED, body(true));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("templates#device", templates);
        return root;
    }

    private static Map<String, Object> body(boolean rendered) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ARG_HARDNESS, "${" + ARG_HARDNESS + "}");
        args.put(ARG_RESISTANCE, "${" + ARG_RESISTANCE + "}");
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("template", SETTINGS);
        settings.put("arguments", args);
        if (rendered) {
            settings.put("can_occlude", false);
            settings.put("is_suffocating", false);
            settings.put("is_view_blocking", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", settings);
        body.put("loot", Map.of("template", LOOT_SELF));
        body.put("behavior", Map.of("type", BEHAVIOUR));
        return body;
    }
}
