/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/compat/patchouli/ConfigTextFunction.java @ c3b4d9536 (the $(cfg:...) values),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (their default values),
 *       src/main/java/net/dries007/tfc/config/CommonConfig.java @ c3b4d9536 (defaultCalendarDayLength 24),
 *       src/main/java/net/dries007/tfc/common/component/heat/Heat.java @ c3b4d9536 (heat colours),
 *       src/generated/resources/data/tfc/tfc/climate_range/ @ c3b4d9536
 */
package dev.tfp.datagen.guide;

import com.google.gson.JsonObject;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves TFC's {@code $(cfg:<name>)} book values at datagen time, the way TFC's own book generation does: with TFC's
 * default server config (PAR-DATA-04). {@code temperature:<c>} is a climate temperature ({@code %.1f°C}),
 * {@code heat:<c>} a heat level name in its colour, {@code climate.<plant>.temperature|hydration} a crop or fruit plant's
 * climate range, and the named values are durations, temperatures, capacities and decay factors. An unknown name is an
 * error (TFC writes "[UNKNOWN CONFIG]").
 */
public final class GuideConfig {
    static final String CLIMATE = TfcCheckout.GENERATED + "data/tfc/tfc/climate_range/";
    private static final Pattern REFERENCE = Pattern.compile("\\$\\(cfg:([^)]*)\\)");
    /** TFC {@code Calendar}: {@code calendarTickRate = 20f / defaultCalendarDayLength (24)}. */
    private static final float CALENDAR_TICK_RATE = 20f / 24;
    private static final int TICKS_IN_HOUR = 1000;
    private static final int TICKS_IN_DAY = 24 * TICKS_IN_HOUR;

    /** {@code ServerConfig} defaults of the player-tick durations. */
    private static final Map<String, Integer> DURATIONS = Map.of(
        "candleTicks", 288000, "torchTicks", 86400, "jackOLanternTicks", 108000, "pitKilnTicks", 7200,
        "composterTicks", 288000);
    private static final int PIT_KILN_TEMPERATURE = 1400;
    private static final int JUG_CAPACITY = 100;
    private static final int BUCKET_VOLUME = 1000;
    private static final int BLOOMERY_CAPACITY = 16;
    private static final int BLOOMERY_MAX_CHIMNEY_HEIGHT = 3;
    private static final Map<String, Double> DECAY = Map.of(
        "traitPreservedModifier", 0.5, "traitWoodGrilledModifier", 0.8, "traitCharcoalGrilledModifier", 1.25);

    private final TfcCheckout tfc;
    private final GuideNames names;

    public GuideConfig(TfcCheckout tfc, GuideNames names) {
        this.tfc = tfc;
        this.names = names;
    }

    /** {@code text} with every {@code $(cfg:...)} replaced; {@code source} names the file for errors. */
    public String substitute(String text, String source) {
        Matcher m = REFERENCE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(resolve(m.group(1), source)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    String resolve(String param, String source) {
        if (param.startsWith("temperature:")) {
            return climateTemperature(number(param.substring("temperature:".length()), source));
        }
        if (param.startsWith("heat:")) {
            return heat((int) number(param.substring("heat:".length()), source));
        }
        if (param.startsWith("climate.")) {
            return climate(param, source);
        }
        if (DURATIONS.containsKey(param)) {
            return duration(DURATIONS.get(param));
        }
        if (DECAY.containsKey(param)) {
            return decayMultiplier(DECAY.get(param));
        }
        return switch (param) {
            case "pitKilnTemperature" -> temperature(PIT_KILN_TEMPERATURE);
            case "pitKilnHeat" -> heat(PIT_KILN_TEMPERATURE);
            case "jugCapacity" -> fluidUnits(JUG_CAPACITY);
            case "woodenBucketCapacity", "metalBucketCapacity" -> fluidUnits(BUCKET_VOLUME);
            case "bloomeryCapacity" -> String.valueOf(BLOOMERY_CAPACITY);
            case "bloomeryTotalCapacity" -> String.valueOf(BLOOMERY_CAPACITY * BLOOMERY_MAX_CHIMNEY_HEIGHT);
            default -> throw new DatagenException(source + ": unknown field guide config value $(cfg:" + param + ")");
        };
    }

    private static float number(String s, String source) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            throw new DatagenException(source + ": $(cfg:...) value " + s + " is not a number");
        }
    }

    private static float f(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    /** {@code ClimateTemperatureDisplayStyle.CELSIUS}: {@code %.1f°C}. */
    public static String climateTemperature(float c) {
        return String.format(Locale.ROOT, "%.1f", c) + "°C";
    }

    /** {@code ItemTemperatureDisplayStyle.CELSIUS}: {@code %.0f°C}. */
    public static String temperature(int c) {
        return c > 0 ? String.format(Locale.ROOT, "%.0f", (float) c) + "°C" : String.valueOf(c);
    }

    /** {@code Heat.getHeat(c)} by name, with its chat colour as a Patchouli code; empty for no heat. */
    public String heat(int c) {
        return HeatLevel.of(c).map(level -> "$(" + heatColor(level) + ")"
            + names(level)).orElse("");
    }

    private String names(HeatLevel level) {
        String key = "tfc.enum.heat." + level.name().toLowerCase(Locale.ROOT);
        String v = names.key(key);
        return v != null ? v : GuideNames.humanize(level.name().toLowerCase(Locale.ROOT));
    }

    /** TFC {@code Heat}'s {@code ChatFormatting} as a colour code. */
    static char heatColor(HeatLevel level) {
        return switch (level) {
            case WARMING, HOT, VERY_HOT -> '7';
            case FAINT_RED, DARK_RED -> '4';
            case BRIGHT_RED -> 'c';
            case ORANGE -> '6';
            case YELLOW, YELLOW_WHITE -> 'e';
            case WHITE, BRILLIANT_WHITE -> 'f';
        };
    }

    /** {@code ConfigTextFunction.duration}: player ticks as calendar days (or hours under one day), rounded. */
    public static String duration(int playerTicks) {
        if (playerTicks <= 0) {
            return "an indefinite amount of time";
        }
        long calendarTicks = (long) ((double) CALENDAR_TICK_RATE * playerTicks);
        return calendarTicks >= TICKS_IN_DAY
            ? Math.round((double) calendarTicks / TICKS_IN_DAY) + " day(s)"
            : Math.round((double) calendarTicks / TICKS_IN_HOUR) + " hour(s)";
    }

    static String fluidUnits(int mb) {
        return mb + " mB";
    }

    /** {@code ConfigTextFunction.decayMultiplier}: 0.8 is "1.25" (times longer). */
    static String decayMultiplier(double modifier) {
        if (modifier <= 0) {
            return "∞";
        }
        double factor = 1 / modifier;
        return factor == Math.rint(factor) ? String.valueOf((long) factor)
            : String.format(Locale.ROOT, "%.2f", factor).replaceAll("0+$", "");
    }

    /** {@code climate.<name>.temperature|hydration}: a crop ({@code crop/<name>}) or a tree or bush ({@code plant/<name>_tree|_bush}). */
    private String climate(String param, String source) {
        String rest = param.substring("climate.".length());
        int dot = rest.lastIndexOf('.');
        if (dot <= 0) {
            throw new DatagenException(source + ": malformed $(cfg:" + param + ")");
        }
        String name = rest.substring(0, dot);
        String what = rest.substring(dot + 1);
        String file = null;
        for (String candidate : new String[] {"crop/" + name, "plant/" + name + "_tree", "plant/" + name + "_bush"}) {
            if (tfc.exists(CLIMATE + candidate + ".json")) {
                file = CLIMATE + candidate + ".json";
                break;
            }
        }
        if (file == null) {
            throw new DatagenException(source + ": no climate range for $(cfg:" + param + ")");
        }
        JsonObject o = Json.parse(tfc.readString(file), file).getAsJsonObject();
        // ClimateRange's codec defaults: hydration 0..100, temperature unbounded
        return switch (what) {
            case "temperature" -> climateTemperature(f(o, "min_temperature", Float.NEGATIVE_INFINITY)) + " - "
                + climateTemperature(f(o, "max_temperature", Float.POSITIVE_INFINITY));
            case "hydration" -> (int) f(o, "min_hydration", 0) + " - " + (int) f(o, "max_hydration", 100);
            default -> throw new DatagenException(source + ": unknown climate value $(cfg:" + param + ")");
        };
    }
}
