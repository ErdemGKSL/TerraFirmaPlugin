/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java @ c3b4d9536 (properties, shapes; likewise
 *       AnemometerBlock, VaneBlock and CalendarClockBlock),
 *       src/main/java/net/dries007/tfc/client/model/entity/{AnemometerModel,VaneModel,CalendarClockModel}.java @ c3b4d9536 (the
 *       cube lists), src/main/java/net/dries007/tfc/client/render/blockentity/{Anemometer,Vane,CalendarClock}BlockEntityRenderer.java
 *       @ c3b4d9536 (the render transforms), src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536,
 *       assets/tfc/blockstates/{thermometer,anemometer,vane,calendar_clock}.json, models/block/thermometer/*.json, models/item,
 *       textures/entity/{anemometer,vane,calendar_clock,calendar_clock_month}.png, textures/block/devices/thermometer/*.png
 *       and data/tfc/loot_table/blocks/*.json @ c3b4d9536 (copied or converted)
 */
package dev.tfp.datagen.instrument;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.LootTableExport;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceAppearance;
import dev.tfp.datagen.device.DeviceBlockSpec;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-CLIM-27: the thermometer, anemometer, weather vane and calendar clock as CraftEngine blocks
 * ({@code craftengine/tfp/configuration/instrument/*.yml}). The device kinds of PAR-CLIM-09 run them.
 *
 * <ul>
 *   <li>{@code tfp:thermometer}: {@code power} 0..15, {@code facing}, {@code attached}; the needle model is TFC's blockstate
 *       choice per power (11 models) turned per facing.</li>
 *   <li>{@code tfp:anemometer} and {@code tfp:vane}: {@code attached_wind_devices}; {@code tfp:calendar_clock}: {@code mode}
 *       (hour, month, timer), {@code facing} (six).</li>
 *   <li>TFC draws the anemometer, vane and clock with Java entity models, which a vanilla client has not. Their cube lists
 *       are read from the pinned sources and converted to block models ({@link EntityBoxes}), with TFC's render transform
 *       per facing baked in; the moving parts (cups, vane arrow, hands) are drawn at their rest angle (ledger).</li>
 *   <li>Every state is an item display on the hitbox-only carrier of its TFC outline (collision as TFC's {@code getShape});
 *       settings, tool tag and sounds from TFC's registration, loot is TFC's table.</li>
 * </ul>
 */
public final class InstrumentBlocks {
    static final String D = "src/main/java/net/dries007/tfc/common/blocks/devices/";
    static final String M = "src/main/java/net/dries007/tfc/client/model/entity/";
    static final String BLOCKSTATES = TfcCheckout.ASSETS + "blockstates/";
    static final String LOOT = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/loot_table/blocks/";
    static final String LANG = TfcCheckout.ASSETS + "lang/";
    static final String PACK = "craftengine/tfp/";
    public static final String OUTPUT_DIR = PACK + "configuration/instrument/";
    static final String RP = PACK + "resourcepack/assets/" + Json.TO_NS + "/";
    static final String LANG_NS = "tfp_instrument";
    static final String LANG_RP = PACK + "resourcepack/assets/" + LANG_NS + "/lang/";
    static final String ROTATION = "0,180,0";
    public static final List<String> CLOCK_FACINGS = HitboxDemands.CLOCK_FACINGS;

    private InstrumentBlocks() {
    }

    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(D + "ThermometerBlock.java") && tfc.exists(M + "VaneModel.java")
            && tfc.exists(BLOCKSTATES + "thermometer.json");
    }

    static String name(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    static String renderItem(String model) {
        return Json.TO_NS + ":render/" + model.substring(model.indexOf(":block/") + ":block/".length());
    }

    /** The appearance of one outline and its displays. */
    private static DeviceAppearance appearance(String id, String pattern, String name, List<String> models, List<String> rotations,
            DeviceBlockSpec.Builder b, Set<String> renderItems, Map<String, String> hidden) {
        return appearance(id, pattern, name, models, rotations, null, b, renderItems, hidden);
    }

    /** {@code translations}: per model the display translation (blocks), or null for none (the moving parts have one). */
    private static DeviceAppearance appearance(String id, String pattern, String name, List<String> models, List<String> rotations,
            List<String> translations, DeviceBlockSpec.Builder b, Set<String> renderItems, Map<String, String> hidden) {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, pattern);
        if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
            throw new DatagenException(id + "#" + name + " got a " + a.mode() + " carrier; InstrumentBlocks draws item displays");
        }
        List<DeviceAppearance.Renderer> renderers = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            String item = renderItem(models.get(i));
            if (renderItems.add(item)) {
                b.renderItem(item, models.get(i));
            }
            DeviceAppearance.Renderer r = DeviceAppearance.Renderer.item(item).rotated(rotations.get(i));
            renderers.add(translations == null || translations.get(i) == null ? r : r.translated(translations.get(i)));
        }
        if (a.hideTarget() != null && !a.pool() && hidden.putIfAbsent(a.state(), a.hideTarget()) == null) {
            b.stateMapping(a.state(), a.hideTarget());
        }
        return DeviceAppearance.rendered(name, DeviceAppearance.Carrier.state(a.state()), renderers);
    }

    /** One block's spec and the TFC block models it needs copied. */
    record Built(DeviceBlockSpec spec, Set<String> blockModels) {
    }

    static DeviceBlockSpec.Builder base(TfcCheckout tfc, String id) {
        return DeviceBlockSpec.builder(id).settings(DeviceBlockSpec.Settings.fromTfc(tfc, name(id)))
            .deviceBehaviour().blockItem("item." + Json.TO_NS + "." + name(id));
    }

    static Built thermometer(TfcCheckout tfc) {
        String id = HitboxDemands.THERMOMETER_ID;
        String src = BLOCKSTATES + "thermometer.json";
        JsonObject blockstate = Json.parse(tfc.readString(src), src).getAsJsonObject();
        List<DeviceProperty> props = List.of(DeviceProperty.range("power", 0, 15, 0),
            DeviceProperty.names("facing", HitboxDemands.FACINGS, "north"), DeviceProperty.bool("attached", false));
        DeviceBlockSpec.Builder b = base(tfc, id).properties(props);
        Map<String, DeviceAppearance> apps = new LinkedHashMap<>();
        Map<String, String> byState = new LinkedHashMap<>();
        Set<String> models = new TreeSet<>();
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (Map<String, String> state : DeviceBlockSpec.combinations(props)) {
            CropBlocks.Part part = CropBlocks.look(blockstate, src, state).getFirst();
            String f = state.get("facing");
            String model = part.model();
            String name = f + "_" + model.substring(model.lastIndexOf('_') + 1) + "_" + part.y();
            byState.put(state.toString(), name);
            if (!apps.containsKey(name)) {
                models.add(model);
                apps.put(name, appearance(id, f + "_*", name, List.of(model), List.of(CropBlocks.rotation(part.y())), b,
                    renderItems, hidden));
            }
        }
        apps.values().forEach(b::appearance);
        b.everyState(s -> byState.get(s.toString()), null);
        return new Built(b.build(), models);
    }

    /** The vane arrow's resting steps: {@code wind} 0..15, step {@code n} points {@code n * 22.5} degrees from the model's rest. */
    public static final int WIND_STEPS = 16;
    /** The clock dial: hour/timer mode {@code dial} 0..47 is the quarter hour of the 12 hour face, month mode 0..11 the month. */
    public static final int DIAL_STEPS = 48;
    public static final int MONTH_STEPS = 12;

    static Built windDevice(TfcCheckout tfc, String id, EntityBoxes boxes, OutputTree out, boolean vane) {
        DeviceBlockSpec.Builder b = base(tfc, id).property(DeviceProperty.bool("attached_wind_devices", false));
        if (vane) {
            b.property(DeviceProperty.range("wind", 0, WIND_STEPS - 1, 0));
        }
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        String plain = boxes.model(out, name(id), vane ? List.of("base", "static_parts") : List.of("post", "base", "spinny"),
            EntityBoxes.Pose.IDENTITY, 0);
        if (!vane) {
            b.appearance(appearance(id, "base", "base", List.of(plain), List.of(ROTATION), b, renderItems, hidden));
            b.appearance(appearance(id, "attached", "attached", List.of(plain), List.of(ROTATION), b, renderItems, hidden));
            b.everyState(s -> s.get("attached_wind_devices").equals("true") ? "attached" : "base", null);
            return new Built(b.build(), Set.of());
        }
        String attached = boxes.model(out, name(id) + "_attached", List.of("static_parts"), EntityBoxes.Pose.IDENTITY, 9);
        EntityBoxes.Moving arrow = boxes.moving(out, name(id) + "_arrow", "spinny", EntityBoxes.Pose.IDENTITY, 0);
        EntityBoxes.Moving arrowAttached = boxes.moving(out, name(id) + "_arrow_attached", "spinny", EntityBoxes.Pose.IDENTITY, 9);
        for (int w = 0; w < WIND_STEPS; w++) {
            for (String kind : List.of("base", "attached")) {
                boolean att = kind.equals("attached");
                EntityBoxes.Moving m = att ? arrowAttached : arrow;
                // VaneModel: spinny.yRot = angle + PI / 2
                String rot = m.quaternion(90 + w * 360.0 / WIND_STEPS);
                b.appearance(appearance(id, kind, kind + "_" + w, List.of(att ? attached : plain, m.model()),
                    List.of(ROTATION, rot), java.util.Arrays.asList(null, m.translation()), b, renderItems, hidden));
            }
        }
        b.everyState(s -> (s.get("attached_wind_devices").equals("true") ? "attached" : "base") + "_" + s.get("wind"), null);
        return new Built(b.build(), Set.of());
    }

    static Built clock(TfcCheckout tfc, EntityBoxes plain, EntityBoxes month, OutputTree out) {
        String id = HitboxDemands.CLOCK_ID;
        List<DeviceProperty> props = List.of(DeviceProperty.names("mode", List.of("hour", "month", "timer"), "hour"),
            DeviceProperty.names("facing", CLOCK_FACINGS, "up"), DeviceProperty.range("dial", 0, DIAL_STEPS - 1, 0));
        DeviceBlockSpec.Builder b = base(tfc, id).properties(props);
        Set<String> renderItems = new LinkedHashSet<>();
        Map<String, String> hidden = new TreeMap<>();
        for (String f : CLOCK_FACINGS) {
            for (String tex : List.of("plain", "month")) {
                boolean m = tex.equals("month");
                EntityBoxes boxes = m ? month : plain;
                EntityBoxes.Pose pose = EntityBoxes.Pose.clock(f);
                String suffix = "calendar_clock_" + f + (m ? "_month" : "");
                String model = boxes.model(out, suffix, List.of("static_parts"), pose, 0);
                EntityBoxes.Moving hour = boxes.moving(out, suffix + "_hour", "hour_hand", pose, 0);
                EntityBoxes.Moving minute = boxes.moving(out, suffix + "_minute", "minute_hand", pose, 0);
                EntityBoxes.Moving monthHand = boxes.moving(out, suffix + "_month_hand", "month_hand", pose, 0);
                // CalendarClockModel: <hand>.yRot = angle + PI; the hour hand makes two turns a day (12 hour face)
                int steps = m ? MONTH_STEPS : DIAL_STEPS;
                for (int d = 0; d < steps; d++) {
                    String hourRot = hour.quaternion(180 + (m ? 0 : d * 360.0 / DIAL_STEPS));
                    String minuteRot = minute.quaternion(180 + (m ? 0 : (d % 4) * 90));
                    String monthRot = monthHand.quaternion(180 + (m ? d * 360.0 / MONTH_STEPS : 0));
                    b.appearance(appearance(id, f + "_*", f + "_" + tex + "_" + d,
                        List.of(model, hour.model(), minute.model(), monthHand.model()),
                        List.of(ROTATION, hourRot, minuteRot, monthRot),
                        java.util.Arrays.asList(null, hour.translation(), minute.translation(), monthHand.translation()), b,
                        renderItems, hidden));
                }
            }
        }
        b.everyState(s -> {
            boolean m = s.get("mode").equals("month");
            int d = Integer.parseInt(s.get("dial"));
            return s.get("facing") + "_" + (m ? "month_" + d % MONTH_STEPS : "plain_" + d);
        }, null);
        return new Built(b.build(), Set.of());
    }

    /** Writes the four entries, models, textures, loot, item models and names. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        int states = 0;
        Set<String> copied = new TreeSet<>();

        Built thermo = thermometer(tfc);
        states += (int) thermo.spec().stateCount();
        for (String m : thermo.blockModels()) {
            CropBlocks.copyModel(tfc, out, m, copied);
        }
        write(out, thermo.spec());

        EntityBoxes anemometerBoxes = EntityBoxes.read(tfc, M + "AnemometerModel.java", "anemometer", "tfp:block/instrument/anemometer");
        Built anemometer = windDevice(tfc, HitboxDemands.ANEMOMETER_ID, anemometerBoxes, out, false);
        states += (int) anemometer.spec().stateCount();
        write(out, anemometer.spec());
        anemometerBoxes.copyTexture(tfc, out);

        EntityBoxes vaneBoxes = EntityBoxes.read(tfc, M + "VaneModel.java", "vane", "tfp:block/instrument/vane");
        Built vane = windDevice(tfc, HitboxDemands.VANE_ID, vaneBoxes, out, true);
        states += (int) vane.spec().stateCount();
        write(out, vane.spec());
        vaneBoxes.copyTexture(tfc, out);

        EntityBoxes plainClock = EntityBoxes.read(tfc, M + "CalendarClockModel.java", "calendar_clock", "tfp:block/instrument/calendar_clock");
        EntityBoxes monthClock = EntityBoxes.read(tfc, M + "CalendarClockModel.java", "calendar_clock_month",
            "tfp:block/instrument/calendar_clock_month");
        Built clock = clock(tfc, plainClock, monthClock, out);
        states += (int) clock.spec().stateCount();
        write(out, clock.spec());
        plainClock.copyTexture(tfc, out);
        monthClock.copyTexture(tfc, out);

        for (String n : List.of("thermometer", "anemometer", "vane", "calendar_clock")) {
            String loot = LOOT + n + ".json";
            LootTableExport.delegateBlock(out, n, Json.rename(Json.parse(tfc.readString(loot), loot)), named -> { });
            copyItem(tfc, out, n);
        }
        writeLang(tfc, out);
        return states;
    }

    private static void write(OutputTree out, DeviceBlockSpec spec) {
        out.put(OUTPUT_DIR + name(spec.id()) + ".yml", Yaml.write(DeviceYaml.HEADER, DeviceYaml.templated(spec)));
    }

    private static void copyItem(TfcCheckout tfc, OutputTree out, String n) {
        String src = TfcCheckout.ASSETS + "models/item/" + n + ".json";
        JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
        String ref = model.getAsJsonObject("textures").get("layer0").getAsString();
        String tex = ref.substring(Json.FROM_NS.length() + 1);
        String png = TfcCheckout.ASSETS + "textures/" + tex + ".png";
        if (!model.get("parent").getAsString().equals("item/generated") || !tfc.exists(png)) {
            throw new DatagenException(src + " is not a generated item model with an existing texture");
        }
        out.put(RP + "textures/" + tex + ".png", tfc.read(png));
        out.put(RP + "models/item/" + n + ".json", Json.write(Json.rename(model)));
    }

    /** TFC's registrations and block classes the generator depends on. */
    static void checkSources(TfcCheckout tfc) {
        String blocks = tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java").replaceAll("\\s+", " ");
        for (String n : List.of("vane", "anemometer", "calendar_clock", "thermometer")) {
            Matcher m = Pattern.compile("register\\(\"" + n + "\", \\(\\) -> new \\w+Block\\(ExtendedProperties.of\\(MapColor.METAL\\)"
                + ".strength\\(3\\).sound\\(SoundType.METAL\\)").matcher(blocks);
            if (!m.find()) {
                throw new DatagenException("TFCBlocks no longer registers " + n + " with strength(3) and SoundType.METAL");
            }
        }
        String thermo = tfc.readString(D + "ThermometerBlock.java");
        for (String needle : List.of("public static IntegerProperty POWER = BlockStateProperties.POWER;",
            "public static BooleanProperty ATTACHED = TFCBlockStateProperties.THERMOMETER_ATTACHED;",
            "public static DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;",
            "builder.add(POWER).add(FACING).add(ATTACHED);")) {
            if (!thermo.contains(needle)) {
                throw new DatagenException(D + "ThermometerBlock.java no longer contains " + needle);
            }
        }
        String clock = tfc.readString(D + "CalendarClockBlock.java");
        for (String needle : List.of("public static EnumProperty<Mode> MODE = TFCBlockStateProperties.CLOCK_MODE;",
            "public static DirectionProperty FACING = BlockStateProperties.FACING;", "builder.add(MODE).add(FACING);")) {
            if (!clock.contains(needle)) {
                throw new DatagenException(D + "CalendarClockBlock.java no longer contains " + needle);
            }
        }
        for (String n : List.of("AnemometerBlock", "VaneBlock")) {
            String s = tfc.readString(D + n + ".java");
            if (!s.contains("public static BooleanProperty ATTACHED_WIND_DEVICES = TFCBlockStateProperties.ATTACHED_WIND_DEVICES;")) {
                throw new DatagenException(D + n + ".java no longer has ATTACHED_WIND_DEVICES");
            }
        }
    }

    private static void writeLang(TfcCheckout tfc, OutputTree out) {
        for (String f : tfc.list(LANG)) {
            if (!f.endsWith(".json")) {
                continue;
            }
            String locale = f.substring(LANG.length(), f.length() - ".json".length());
            JsonObject src = Json.parse(tfc.readString(f), f).getAsJsonObject();
            TreeMap<String, String> keys = new TreeMap<>();
            for (String n : List.of("thermometer", "anemometer", "vane", "calendar_clock")) {
                String tfcKey = "block." + Json.FROM_NS + "." + n;
                if (src.has(tfcKey)) {
                    keys.put("item." + Json.TO_NS + "." + n, src.get(tfcKey).getAsString());
                } else if (locale.equals("en_us")) {
                    throw new DatagenException("TFC has no English name " + tfcKey);
                }
            }
            if (!keys.isEmpty()) {
                JsonObject o = new JsonObject();
                keys.forEach(o::addProperty);
                out.put(LANG_RP + locale + ".json", Json.write(o));
            }
        }
    }
}
