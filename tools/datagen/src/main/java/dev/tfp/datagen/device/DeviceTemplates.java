/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.Yaml;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared CraftEngine templates of device blocks (PAR-INFRA-08), written to
 * {@code craftengine/tfp/configuration/templates/device.yml}:
 *
 * <ul>
 *   <li>{@code tfp:block/device}: the block settings TFC gives the device (hardness, resistance, the five sound events
 *       and the {@code minecraft:mineable/<tool>} tag, all from the caller's {@code device_*} arguments, see
 *       {@link DeviceBlockSpec.Settings}) and the {@code tfp:device} behaviour (PAR-INFRA-02: the block entity record, use
 *       and neighbour routing, inventory drop on removal). The templates hold no loot: TFC's device loot tables differ per
 *       device (wood ash, the grill or pot item, 7 charcoal, the block itself), so the caller writes its {@code loot}
 *       ({@link DeviceLoot}) and its {@code states} beside the template call; CraftEngine deep-merges them in.</li>
 *   <li>{@code tfp:block/device_rendered}: the same for devices whose look is drawn by {@code entity_renderer} display
 *       entities over a hitbox carrier. The carrier's model may be empty, so the server must not treat the block as
 *       occluding, suffocating or view-blocking: {@code can_occlude}, {@code is_suffocating} and
 *       {@code is_view_blocking} are false (CraftEngine otherwise derives them from the vanilla carrier state).</li>
 * </ul>
 *
 * <p>Both templates are written out in full and call no other template, so each expands in one step. Argument names
 * carry the {@code device_} prefix because the caller's argument always wins in CraftEngine templates.
 */
public final class DeviceTemplates {
    public static final String OUTPUT = "craftengine/tfp/configuration/templates/device.yml";
    public static final String DEVICE = "tfp:block/device";
    public static final String DEVICE_RENDERED = "tfp:block/device_rendered";
    static final String BLOCK_ITEM = "tfp:item/block_item";
    static final String RENDER_ITEM = "tfp:item/render";
    static final String BEHAVIOUR = "tfp:device";
    static final String ARG_HARDNESS = "device_hardness";
    static final String ARG_RESISTANCE = "device_resistance";
    static final String ARG_TOOL_TAG = "device_tool_tag";
    /** {@code device_sound_break}, ... in {@link DeviceSound#yaml()} key order. */
    static final List<String> SOUND_KEYS = List.of("break", "step", "place", "hit", "fall");

    static final String HEADER = DeviceYaml.HEADER + "\n"
        + "tfp: device block templates (PAR-INFRA-08). Call with device_hardness, device_resistance, device_sound_<event>\n"
        + "(break, step, place, hit, fall) and device_tool_tag; write the loot and the states beside the call.\n"
        + "tfp:block/device: settings and the tfp:device behaviour. tfp:block/device_rendered: the same, not occluding,\n"
        + "suffocating or view-blocking (entity_renderer looks).";

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

    /** The template argument name of a sound event key. */
    static String soundArg(String event) {
        return "device_sound_" + event;
    }

    /** The arguments a templated entry passes for {@code settings}. */
    static Map<String, Object> arguments(DeviceBlockSpec.Settings settings) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ARG_HARDNESS, settings.hardness());
        args.put(ARG_RESISTANCE, settings.resistance());
        settings.sound().yaml().forEach((k, v) -> args.put(soundArg(k), v));
        args.put(ARG_TOOL_TAG, settings.toolTag());
        return args;
    }

    private static Map<String, Object> body(boolean rendered) {
        Map<String, Object> sounds = new LinkedHashMap<>();
        SOUND_KEYS.forEach(k -> sounds.put(k, "${" + soundArg(k) + "}"));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("hardness", "${" + ARG_HARDNESS + "}");
        settings.put("resistance", "${" + ARG_RESISTANCE + "}");
        settings.put("sounds", sounds);
        settings.put("tags", List.of("${" + ARG_TOOL_TAG + "}"));
        if (rendered) {
            settings.put("can_occlude", false);
            settings.put("is_suffocating", false);
            settings.put("is_view_blocking", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", settings);
        body.put("behavior", Map.of("type", BEHAVIOUR));
        return body;
    }
}
