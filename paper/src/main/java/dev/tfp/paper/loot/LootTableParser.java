/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads vanilla/TFC loot table JSON into a {@link LootTable}. Strict: an unknown condition, function, entry or number type,
 * or an unknown key in a {@code match_tool} predicate, is an error naming the JSON path, so a table never evaluates with a
 * silently ignored rule. Types may be written with or without the {@code minecraft:} namespace, and TFC's types as
 * {@code tfc:} or (renamed by datagen) {@code tfp:}.
 */
public final class LootTableParser {
    private LootTableParser() {}

    /** Thrown for a table that cannot be read; the message starts with the table id and the JSON path. */
    public static final class LootParseException extends RuntimeException {
        public LootParseException(String message) {
            super(message);
        }
    }

    public static LootTable parse(String id, String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new LootParseException(id + ": malformed JSON: " + e.getMessage());
        }
        return parse(id, root);
    }

    public static LootTable parse(String id, JsonElement root) {
        return new Reader(id).table(root);
    }

    private static final class Reader {
        private final String id;

        Reader(String id) {
            this.id = id;
        }

        private LootParseException error(String path, String message) {
            return new LootParseException(id + " at " + path + ": " + message);
        }

        private JsonObject object(JsonElement e, String path) {
            if (e == null || !e.isJsonObject()) {
                throw error(path, "expected an object");
            }
            return e.getAsJsonObject();
        }

        private JsonArray array(JsonObject o, String key, String path) {
            JsonElement e = o.get(key);
            if (e == null) {
                return new JsonArray();
            }
            if (!e.isJsonArray()) {
                throw error(path + "." + key, "expected an array");
            }
            return e.getAsJsonArray();
        }

        private String string(JsonObject o, String key, String path) {
            JsonElement e = o.get(key);
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                throw error(path + "." + key, "expected a string");
            }
            return e.getAsString();
        }

        /** A type id: no namespace means {@code minecraft:}; our datagen's {@code tfp:} rename of TFC's types reads as {@code tfc:}. */
        private static String type(String raw) {
            if (raw.indexOf(':') < 0) {
                return "minecraft:" + raw;
            }
            return raw.startsWith("tfp:") ? "tfc:" + raw.substring(4) : raw;
        }

        LootTable table(JsonElement root) {
            JsonObject o = object(root, "$");
            String type = o.has("type") ? type(string(o, "type", "$")) : "minecraft:generic";
            List<LootTable.Pool> pools = new ArrayList<>();
            JsonArray arr = array(o, "pools", "$");
            for (int i = 0; i < arr.size(); i++) {
                pools.add(pool(arr.get(i), "$.pools[" + i + "]"));
            }
            return new LootTable(id, type, pools, functions(o, "$"));
        }

        LootTable.Pool pool(JsonElement e, String path) {
            JsonObject o = object(e, path);
            LootNumber rolls = o.has("rolls") ? number(o.get("rolls"), path + ".rolls") : LootNumber.constant(1);
            LootNumber bonus = o.has("bonus_rolls") ? number(o.get("bonus_rolls"), path + ".bonus_rolls")
                    : LootNumber.constant(0);
            List<LootEntry> entries = new ArrayList<>();
            JsonArray arr = array(o, "entries", path);
            for (int i = 0; i < arr.size(); i++) {
                entries.add(entry(arr.get(i), path + ".entries[" + i + "]"));
            }
            return new LootTable.Pool(rolls, bonus, entries, conditions(o, path), functions(o, path));
        }

        LootEntry entry(JsonElement e, String path) {
            JsonObject o = object(e, path);
            String type = type(string(o, "type", path));
            List<LootCondition> conditions = conditions(o, path);
            int weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
            int quality = o.has("quality") ? o.get("quality").getAsInt() : 0;
            return switch (type) {
                case "minecraft:item" -> new LootEntry.Item(string(o, "name", path), weight, quality, conditions,
                        functions(o, path));
                case "minecraft:empty" -> new LootEntry.Empty(weight, quality, conditions, functions(o, path));
                case "minecraft:alternatives", "minecraft:group", "minecraft:sequence" -> {
                    List<LootEntry> children = new ArrayList<>();
                    JsonArray arr = array(o, "children", path);
                    for (int i = 0; i < arr.size(); i++) {
                        children.add(entry(arr.get(i), path + ".children[" + i + "]"));
                    }
                    LootEntry.Kind kind = switch (type) {
                        case "minecraft:alternatives" -> LootEntry.Kind.ALTERNATIVES;
                        case "minecraft:group" -> LootEntry.Kind.GROUP;
                        default -> LootEntry.Kind.SEQUENCE;
                    };
                    yield new LootEntry.Composite(kind, children, conditions);
                }
                default -> throw error(path + ".type", "unsupported entry type " + type);
            };
        }

        List<LootCondition> conditions(JsonObject o, String path) {
            List<LootCondition> out = new ArrayList<>();
            JsonArray arr = array(o, "conditions", path);
            for (int i = 0; i < arr.size(); i++) {
                out.add(condition(arr.get(i), path + ".conditions[" + i + "]"));
            }
            return out;
        }

        LootCondition condition(JsonElement e, String path) {
            JsonObject o = object(e, path);
            String type = type(string(o, "condition", path));
            return switch (type) {
                case "minecraft:survives_explosion" -> new LootCondition.SurvivesExplosion();
                case "minecraft:random_chance" -> new LootCondition.RandomChance(number(o.get("chance"), path + ".chance"));
                case "minecraft:match_tool" -> matchTool(o.has("predicate") ? object(o.get("predicate"), path + ".predicate")
                        : new JsonObject(), path + ".predicate");
                case "minecraft:block_state_property" -> blockState(o, path);
                case "minecraft:inverted" -> new LootCondition.Inverted(condition(o.get("term"), path + ".term"));
                case "minecraft:any_of", "minecraft:alternative" -> new LootCondition.AnyOf(terms(o, path));
                case "minecraft:all_of" -> new LootCondition.AllOf(terms(o, path));
                case "minecraft:table_bonus" -> {
                    List<Float> chances = new ArrayList<>();
                    for (JsonElement c : array(o, "chances", path)) {
                        chances.add(c.getAsFloat());
                    }
                    yield new LootCondition.TableBonus(string(o, "enchantment", path), chances);
                }
                case "minecraft:killed_by_player" -> new LootCondition.KilledByPlayer();
                case "minecraft:damage_source_properties" -> damageSource(o, path);
                case "tfc:is_isolated" -> new LootCondition.IsIsolated();
                case "tfc:is_burnt_out" -> new LootCondition.IsBurntOut();
                case "tfc:is_sluice" -> new LootCondition.IsSluice();
                case "tfc:is_male" -> new LootCondition.IsMale();
                case "tfc:not_predated" -> new LootCondition.NotPredated();
                case "tfc:always_true" -> new LootCondition.AlwaysTrue();
                default -> throw error(path + ".condition", "unsupported condition " + type);
            };
        }

        List<LootCondition> terms(JsonObject o, String path) {
            List<LootCondition> out = new ArrayList<>();
            JsonArray arr = array(o, "terms", path);
            for (int i = 0; i < arr.size(); i++) {
                out.add(condition(arr.get(i), path + ".terms[" + i + "]"));
            }
            return out;
        }

        private static final Set<String> TOOL_KEYS = Set.of("items", "predicates");

        LootCondition matchTool(JsonObject p, String path) {
            for (String key : p.keySet()) {
                if (!TOOL_KEYS.contains(key)) {
                    throw error(path + "." + key, "unsupported item predicate key");
                }
            }
            List<String> items = null;
            if (p.has("items")) {
                items = ids(p.get("items"), path + ".items");
            }
            List<LootCondition.EnchantmentTest> enchantments = new ArrayList<>();
            if (p.has("predicates")) {
                JsonObject preds = object(p.get("predicates"), path + ".predicates");
                for (String key : preds.keySet()) {
                    if (!key.equals("minecraft:enchantments")) {
                        throw error(path + ".predicates." + key, "unsupported item sub-predicate");
                    }
                    JsonElement list = preds.get(key);
                    String lp = path + ".predicates." + key;
                    if (!list.isJsonArray()) {
                        throw error(lp, "expected an array");
                    }
                    for (int i = 0; i < list.getAsJsonArray().size(); i++) {
                        JsonObject ench = object(list.getAsJsonArray().get(i), lp + "[" + i + "]");
                        List<String> ids = ench.has("enchantments") ? ids(ench.get("enchantments"), lp + "[" + i + "]")
                                : null;
                        LootCondition.IntRange levels = ench.has("levels") ? range(ench.get("levels")) : null;
                        enchantments.add(new LootCondition.EnchantmentTest(ids, levels));
                    }
                }
            }
            return new LootCondition.MatchTool(items, enchantments);
        }

        /** A holder set: one id, one {@code #tag}, or a list of ids. */
        List<String> ids(JsonElement e, String path) {
            if (e.isJsonPrimitive()) {
                return List.of(e.getAsString());
            }
            if (e.isJsonArray()) {
                List<String> out = new ArrayList<>();
                for (JsonElement x : e.getAsJsonArray()) {
                    out.add(x.getAsString());
                }
                return out;
            }
            throw error(path, "expected an id, a #tag or a list of ids");
        }

        LootCondition.IntRange range(JsonElement e) {
            if (e.isJsonPrimitive()) {
                int v = e.getAsInt();
                return new LootCondition.IntRange(v, v);
            }
            JsonObject o = e.getAsJsonObject();
            return new LootCondition.IntRange(o.has("min") ? o.get("min").getAsInt() : null,
                    o.has("max") ? o.get("max").getAsInt() : null);
        }

        LootCondition blockState(JsonObject o, String path) {
            Map<String, LootCondition.ValueMatch> props = new LinkedHashMap<>();
            if (o.has("properties")) {
                JsonObject p = object(o.get("properties"), path + ".properties");
                for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                    JsonElement v = e.getValue();
                    if (v.isJsonPrimitive()) {
                        props.put(e.getKey(), new LootCondition.ValueMatch(v.getAsString(), null, null));
                    } else {
                        JsonObject r = object(v, path + ".properties." + e.getKey());
                        props.put(e.getKey(), new LootCondition.ValueMatch(null,
                                r.has("min") ? r.get("min").getAsString() : null,
                                r.has("max") ? r.get("max").getAsString() : null));
                    }
                }
            }
            return new LootCondition.BlockStateProperty(string(o, "block", path), props);
        }

        LootCondition damageSource(JsonObject o, String path) {
            JsonObject p = object(o.get("predicate"), path + ".predicate");
            Map<String, Boolean> expected = new LinkedHashMap<>();
            for (String key : p.keySet()) {
                if (!key.equals("tags")) {
                    throw error(path + ".predicate." + key, "unsupported damage source predicate key");
                }
            }
            JsonArray tags = array(p, "tags", path + ".predicate");
            for (int i = 0; i < tags.size(); i++) {
                JsonObject t = object(tags.get(i), path + ".predicate.tags[" + i + "]");
                expected.put(string(t, "id", path + ".predicate.tags[" + i + "]"), t.get("expected").getAsBoolean());
            }
            return new LootCondition.DamageSourceTags(expected);
        }

        List<LootFunction> functions(JsonObject o, String path) {
            List<LootFunction> out = new ArrayList<>();
            JsonArray arr = array(o, "functions", path);
            for (int i = 0; i < arr.size(); i++) {
                out.add(function(arr.get(i), path + ".functions[" + i + "]"));
            }
            return out;
        }

        LootFunction function(JsonElement e, String path) {
            JsonObject o = object(e, path);
            String type = type(string(o, "function", path));
            List<LootCondition> conditions = conditions(o, path);
            return switch (type) {
                case "minecraft:set_count" -> new LootFunction.SetCount(number(o.get("count"), path + ".count"),
                        o.has("add") && o.get("add").getAsBoolean(), conditions);
                case "minecraft:explosion_decay" -> new LootFunction.ExplosionDecay(conditions);
                case "minecraft:limit_count" -> {
                    JsonObject limit = object(o.get("limit"), path + ".limit");
                    yield new LootFunction.LimitCount(
                            limit.has("min") ? number(limit.get("min"), path + ".limit.min") : null,
                            limit.has("max") ? number(limit.get("max"), path + ".limit.max") : null, conditions);
                }
                case "minecraft:copy_components" -> {
                    List<String> include = new ArrayList<>();
                    for (JsonElement x : array(o, "include", path)) {
                        include.add(x.getAsString());
                    }
                    yield new LootFunction.Data(new LootFunction.StackOp.CopyComponents(string(o, "source", path),
                            include), conditions);
                }
                case "tfc:copy_fluid" -> new LootFunction.Data(new LootFunction.StackOp.CopyFluid(), conditions);
                case "tfc:rotten" -> new LootFunction.Data(new LootFunction.StackOp.Rotten(), conditions);
                case "tfc:apply_stack_size" -> new LootFunction.Data(new LootFunction.StackOp.ApplyStackSize(),
                        conditions);
                default -> throw error(path + ".function", "unsupported function " + type);
            };
        }

        LootNumber number(JsonElement e, String path) {
            if (e == null) {
                throw error(path, "missing number");
            }
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return LootNumber.constant(e.getAsFloat());
            }
            JsonObject o = object(e, path);
            String type = o.has("type") ? type(string(o, "type", path)) : "minecraft:uniform";
            return switch (type) {
                case "minecraft:constant" -> LootNumber.constant(o.get("value").getAsFloat());
                case "minecraft:uniform" -> new LootNumber.Uniform(number(o.get("min"), path + ".min"),
                        number(o.get("max"), path + ".max"));
                case "minecraft:binomial" -> new LootNumber.Binomial(number(o.get("n"), path + ".n"),
                        number(o.get("p"), path + ".p"));
                case "tfc:crop_yield_uniform" -> new LootNumber.CropYield(number(o.get("min"), path + ".min"),
                        number(o.get("max"), path + ".max"));
                case "tfc:animal_yield" -> new LootNumber.AnimalYield(number(o.get("min"), path + ".min"),
                        number(o.get("max"), path + ".max"));
                default -> throw error(path + ".type", "unsupported number provider " + type);
            };
        }
    }
}
