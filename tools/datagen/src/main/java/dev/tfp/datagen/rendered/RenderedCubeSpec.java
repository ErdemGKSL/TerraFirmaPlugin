/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (BlockType.blockProperties: strength, sound, requiresCorrectToolForDrops)
 */
package dev.tfp.datagen.rendered;

import com.google.gson.JsonElement;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceSound;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One renderer-drawn full block (PAR-INFRA-17): a barrier carrier ({@link RenderedTemplates#CARRIER}, full collision and
 * outline, no model) drawn by one {@code item_display} cube, with CraftEngine {@code destroy_stages} cracks, TFC's
 * {@code SoundType} (ids, volume and pitch: {@link RenderedSound}), hardness, resistance and tool, and break particles of {@link #particleItem}.
 *
 * <p>The carrier is fixed: PAR-INFRA-13's selector ranks vanilla states for model-drawn blocks, and a full cube drawn by
 * an entity needs the one full-collision state that has no model, does not occlude its neighbours and passes light.
 *
 * @param id the block id (also the placing item's id)
 * @param renderItem the display-only item drawn as the cube ({@code tfp:item/render})
 * @param renderModel the model of {@link #renderItem}
 * @param settings TFC hardness, resistance, sound and {@code minecraft:mineable/<tool>} tag
 * @param requiresCorrectTool TFC's {@code requiresCorrectToolForDrops}: drops (and full speed) need the tool type
 * @param particleItem the item whose model's particle texture the break particles show (normally {@link #renderItem})
 * @param itemLangKey the placing item's translation key
 */
public record RenderedCubeSpec(String id, String renderItem, String renderModel, DeviceBlockSpec.Settings settings,
                               boolean requiresCorrectTool, String particleItem, String itemLangKey) {
    static final String METAL_JAVA = "src/main/java/net/dries007/tfc/util/Metal.java";
    static final String MINEABLE = TfcCheckout.GENERATED + "data/minecraft/tags/block/mineable/";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_/]+");
    private static final Pattern METAL_PROPERTIES = Pattern.compile(
        "Properties blockProperties\\(RegistryMetal metal\\)\\s*\\{\\s*return ([^;]+);");
    private static final Pattern STRENGTH = Pattern.compile("\\.strength\\(([0-9.]+)[fF]?,\\s*([0-9.]+)[fF]?\\)");
    private static final Pattern SOUND = Pattern.compile("\\.sound\\((SoundType\\.[A-Z_]+)\\)");

    public RenderedCubeSpec {
        for (String s : new String[] {id, renderItem, renderModel, particleItem}) {
            if (s == null || !ID.matcher(s).matches()) {
                throw new DatagenException("rendered cube id " + s + " is not <namespace>:<path>");
            }
        }
        if (settings == null || itemLangKey == null || itemLangKey.isBlank()) {
            throw new DatagenException("rendered cube " + id + " needs settings and an item lang key");
        }
    }

    /** {@code <ns>:<path>} drawn by {@code <ns>:render/<path>} with model {@code renderModel}; particles of the cube. */
    public static RenderedCubeSpec of(String id, String renderModel, DeviceBlockSpec.Settings settings, boolean requiresCorrectTool,
                                      String itemLangKey) {
        String render = id.substring(0, id.indexOf(':') + 1) + "render/" + id.substring(id.indexOf(':') + 1);
        return new RenderedCubeSpec(id, render, renderModel, settings, requiresCorrectTool, render, itemLangKey);
    }

    /**
     * TFC's metal block ({@code tfc:metal/block/<metal>}): the settings of {@code Metal.BlockType.blockProperties} (strength
     * 5/6, {@code SoundType.METAL}, requires the correct tool) and the tool of TFC's generated mineable tag.
     */
    public static DeviceBlockSpec.Settings tfcMetalBlock(TfcCheckout tfc, String metal) {
        String src = tfc.readString(METAL_JAVA);
        Matcher props = METAL_PROPERTIES.matcher(src);
        if (!props.find()) {
            throw new DatagenException(METAL_JAVA + ": no blockProperties(RegistryMetal)");
        }
        String chain = props.group(1);
        Matcher st = STRENGTH.matcher(chain);
        Matcher so = SOUND.matcher(chain);
        if (!st.find() || !so.find() || !chain.contains(".requiresCorrectToolForDrops()")) {
            throw new DatagenException(METAL_JAVA + ": blockProperties lacks strength, sound or requiresCorrectToolForDrops");
        }
        return DeviceBlockSpec.Settings.of(st.group(1), st.group(2), DeviceSound.ofJava(so.group(1)),
            tool(tfc, "metal/block/" + metal));
    }

    /** The one {@code minecraft:mineable/<tool>} tag of TFC's generated data that holds {@code tfc:<block>}. */
    static String tool(TfcCheckout tfc, String block) {
        String found = null;
        for (String t : List.of("axe", "hoe", "pickaxe", "shovel")) {
            String src = MINEABLE + t + ".json";
            for (JsonElement v : Json.parse(tfc.readString(src), src).getAsJsonObject().getAsJsonArray("values")) {
                if (v.isJsonPrimitive() && v.getAsString().equals(Json.FROM_NS + ":" + block)) {
                    if (found != null) {
                        throw new DatagenException("tfc:" + block + " is in two mineable tags: " + found + ", " + t);
                    }
                    found = t;
                }
            }
        }
        if (found == null) {
            throw new DatagenException("tfc:" + block + " is in no minecraft:mineable tag");
        }
        return found;
    }

    /** The tool name of the mineable tag ({@code pickaxe}). */
    public String tool() {
        return settings.toolTag().substring("minecraft:mineable/".length());
    }

    /** The template arguments of the block entry. */
    public Map<String, Object> arguments() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(RenderedTemplates.ARG_HARDNESS, settings.hardness());
        args.put(RenderedTemplates.ARG_RESISTANCE, settings.resistance());
        DeviceSound s = settings.sound();
        RenderedSound vp = RenderedSound.of(s);
        Map<String, String> ids = Map.of("break", s.breakSound(), "step", s.step(), "place", s.place(), "hit", s.hit(),
            "fall", s.fall());
        for (String e : RenderedTemplates.SOUND_KEYS) {
            args.put(RenderedTemplates.soundArg(e), ids.get(e));
            args.put(RenderedTemplates.soundArg(e) + "_volume", vp.volume(e));
            args.put(RenderedTemplates.soundArg(e) + "_pitch", vp.pitch(e));
        }
        args.put(RenderedTemplates.ARG_TOOL_TAG, settings.toolTag());
        args.put(RenderedTemplates.ARG_ITEM, renderItem);
        args.put(RenderedTemplates.ARG_PARTICLE_ITEM, particleItem);
        return args;
    }

    /**
     * {@code blocks: <id>: {template: tfp:block/rendered_cube, arguments, settings.correct_tools?, loot}} and
     * {@code items}: the placing item and the render item.
     */
    public Map<String, Object> yaml() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("template", RenderedTemplates.CUBE);
        block.put("arguments", arguments());
        if (requiresCorrectTool) {
            // CE expands a tag to its vanilla members plus the CE items whose settings.tags list it; the TFC metal tools
            // carry only tfp:tools/<tool> (as tfp:settings/rock, PAR-INFRA-27).
            block.put("settings", Map.of("correct_tools", List.of("#minecraft:" + tool() + "s", "#tfp:tools/" + tool())));
        }
        block.put("loot", Map.of("template", "tfp:loot_table/self"));
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put(id, block);
        Map<String, Object> place = new LinkedHashMap<>();
        place.put("template", "tfp:item/block_item");
        place.put("arguments", Map.of("item_lang_key", itemLangKey));
        Map<String, Object> render = new LinkedHashMap<>();
        render.put("template", "tfp:item/render");
        render.put("arguments", Map.of("render_model", renderModel));
        Map<String, Object> items = new LinkedHashMap<>();
        items.put(id, place);
        items.put(renderItem, render);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("blocks", blocks);
        root.put("items", items);
        return root;
    }

    /** The YAML file text of {@link #yaml()} under {@code header}. */
    public String write(String header) {
        return Yaml.write(header, yaml());
    }
}
