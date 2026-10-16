/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.json.JsonDataException;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.recipe.block.BlockIngredient;
import dev.tfp.core.recipe.block.BlockStateRef;
import dev.tfp.core.recipe.fluid.FluidAmount;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Codecs and matching logic of every ingredient type (JSON shapes taken from TFC data). */
class IngredientTest {
    static final TagLookup TAGS = new TagLookup() {
        final Map<String, Set<String>> items = Map.of(
                "c:foods/fruit", Set.of("tfc:food/banana", "tfc:food/cherry"),
                "c:ingots", Set.of("tfc:metal/ingot/copper", "tfc:brick/x"),
                "c:bricks", Set.of("tfc:brick/x"));
        final Map<String, Set<String>> fluids = Map.of("tfc:alcohols", Set.of("tfc:beer", "tfc:cider"));

        @Override
        public boolean contains(TagType type, String tag, String id) {
            Map<String, Set<String>> m = type == TagType.ITEM ? items : type == TagType.FLUID ? fluids : Map.of();
            return m.getOrDefault(tag, Set.of()).contains(id);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return true;
        }
    };
    static final IngredientContext<TestStack> CTX = TestStack.context(TAGS);

    static Ingredient parse(String json) {
        return Ingredient.CODEC.decode(StrictJson.parse(json, "t"), "t.json");
    }

    @Test
    void vanillaItemTagAndList() {
        assertThat(parse("{\"item\": \"tfc:food/banana\"}")).isEqualTo(new Ingredient.Item("tfc:food/banana"));
        Ingredient fruit = parse("{\"tag\": \"c:foods/fruit\"}");
        assertThat(fruit.test(TestStack.of("tfc:food/cherry"), CTX)).isTrue();
        assertThat(fruit.test(TestStack.of("tfc:food/beef"), CTX)).isFalse();
        Ingredient any = parse("[{\"item\": \"tfc:a\"}, {\"tag\": \"c:foods/fruit\"}]");
        assertThat(any).isInstanceOf(Ingredient.AnyOf.class);
        assertThat(any.test(TestStack.of("tfc:a"), CTX)).isTrue();
        assertThat(any.test(TestStack.of("tfc:food/banana"), CTX)).isTrue();
        assertThat(any.test(TestStack.of("tfc:b"), CTX)).isFalse();
    }

    @Test
    void andNotRottenTraits() {
        // recipe/barrel/pickled.json input_item (without count)
        Ingredient pickled = parse("""
                {"type": "tfc:and", "children": [
                  [{"tag": "c:foods/fruit"}, {"tag": "c:foods/vegetable"}],
                  {"type": "tfc:not_rotten"},
                  {"type": "tfc:has_trait", "trait": "tfc:brined"},
                  {"type": "tfc:lacks_trait", "trait": "tfc:pickled"}]}""");
        TestStack banana = TestStack.of("tfc:food/banana");
        assertThat(pickled.test(banana.traits("tfc:brined"), CTX)).isTrue();
        assertThat(pickled.test(banana, CTX)).as("not brined").isFalse();
        assertThat(pickled.test(banana.traits("tfc:brined", "tfc:pickled"), CTX)).as("already pickled").isFalse();
        assertThat(pickled.test(banana.traits("tfc:brined").rotten(true), CTX)).as("rotten").isFalse();
        assertThat(pickled.test(TestStack.of("tfc:food/beef").traits("tfc:brined"), CTX)).as("not fruit").isFalse();
        assertThat(parse("{\"type\": \"tfc:rotten\"}").test(banana.rotten(true), CTX)).isTrue();
        assertThat(parse("{\"type\": \"tfc:not_rotten\"}").precise()).isTrue();
    }

    @Test
    void heatBoundsAreInclusiveAndOneIsRequired() {
        Ingredient hot = parse("{\"type\": \"tfc:heat\", \"min\": 1.0}");
        assertThat(hot).isEqualTo(new Ingredient.Heat(1f, Float.POSITIVE_INFINITY));
        assertThat(hot.test(TestStack.of("x").heat(1f), CTX)).isTrue();
        assertThat(hot.test(TestStack.of("x").heat(0.5f), CTX)).isFalse();
        Ingredient band = parse("{\"type\": \"tfc:heat\", \"min\": 100, \"max\": 200}");
        assertThat(band.test(TestStack.of("x").heat(200f), CTX)).isTrue();
        assertThat(band.test(TestStack.of("x").heat(200.5f), CTX)).isFalse();
        assertThatThrownBy(() -> parse("{\"type\": \"tfc:heat\"}")).hasMessageContaining("one of min or max");
        assertThatThrownBy(() -> parse("{\"type\": \"tfc:heat\", \"min\": -5}")).hasMessageContaining("positive");
    }

    @Test
    void fluidContent() {
        // recipe/crafting/clay_ball_2.json
        Ingredient water = parse("{\"type\": \"tfc:fluid_content\", \"fluid\": {\"amount\": 1000, \"fluid\": \"minecraft:water\"}}");
        TestStack bucket = TestStack.of("tfc:wooden_bucket");
        assertThat(water.test(bucket.fluid("minecraft:water", 1000), CTX)).isTrue();
        assertThat(water.test(bucket.fluid("minecraft:water", 999), CTX)).isFalse();
        assertThat(water.test(bucket.fluid("tfc:salt_water", 1000), CTX)).isFalse();
        assertThat(water.test(bucket, CTX)).isFalse();
        Ingredient alcohol = parse("{\"type\": \"tfc:fluid_content\", \"fluid\": {\"tag\": \"tfc:alcohols\"}}");
        assertThat(alcohol.test(bucket.fluid("tfc:beer", 1000), CTX)).as("default amount is a bucket").isTrue();
        assertThat(alcohol.test(bucket.fluid("tfc:beer", 500), CTX)).isFalse();
    }

    @Test
    void neoforgeCompoundDifferenceIntersection() {
        // tfc/item_size/ingots.json
        Ingredient ingots = parse("{\"type\": \"neoforge:difference\", \"base\": {\"tag\": \"c:ingots\"}, \"subtracted\": {\"tag\": \"c:bricks\"}}");
        assertThat(ingots.test(TestStack.of("tfc:metal/ingot/copper"), CTX)).isTrue();
        assertThat(ingots.test(TestStack.of("tfc:brick/x"), CTX)).isFalse();
        Ingredient compound = parse("{\"type\": \"neoforge:compound\", \"children\": [{\"item\": \"tfc:powder/lime\"}, {\"item\": \"tfc:powder/flux\"}]}");
        assertThat(compound.test(TestStack.of("tfc:powder/flux"), CTX)).isTrue();
        Ingredient both = parse("{\"type\": \"neoforge:intersection\", \"children\": [{\"tag\": \"c:ingots\"}, {\"tag\": \"c:bricks\"}]}");
        assertThat(both.test(TestStack.of("tfc:brick/x"), CTX)).isTrue();
        assertThat(both.test(TestStack.of("tfc:metal/ingot/copper"), CTX)).isFalse();
    }

    @Test
    void sizedIngredientsAddCount() {
        // recipe/barrel/limewater.json input_item
        SizedIngredient lime = SizedIngredient.CODEC.decode(StrictJson.parse(
                "{\"type\": \"neoforge:compound\", \"children\": [{\"item\": \"tfc:powder/lime\"}], \"count\": 2}", "t"), "t");
        assertThat(lime.count()).isEqualTo(2);
        assertThat(lime.test(TestStack.of("tfc:powder/lime"), 2, CTX)).isTrue();
        assertThat(lime.test(TestStack.of("tfc:powder/lime"), 1, CTX)).isFalse();
        assertThat(SizedIngredient.CODEC.decode(StrictJson.parse("{\"tag\": \"c:x\"}", "t"), "t").count()).isEqualTo(1);
        assertThatThrownBy(() -> parse("{\"item\": \"tfc:a\", \"count\": 2}")).hasMessageContaining("unknown key(s) [count]");
        SizedFluidIngredient f = SizedFluidIngredient.CODEC.decode(StrictJson.parse("{\"fluid\": \"tfc:vinegar\", \"amount\": 125}", "t"), "t");
        assertThat(f.test(new FluidAmount("tfc:vinegar", 125), TAGS)).isTrue();
    }

    @Test
    void blockIngredientsAndStates() {
        BlockIngredient list = BlockIngredient.CODEC.decode(StrictJson.parse("[\"tfc:rock/raw/tuff\", \"tfc:rock/hardened/tuff\"]", "t"), "t");
        assertThat(list.test("tfc:rock/raw/tuff", TAGS)).isTrue();
        BlockIngredient tag = BlockIngredient.CODEC.decode(StrictJson.parse("\"#tfc:lamps\"", "t"), "t");
        assertThat(tag.tag()).contains("tfc:lamps");
        assertThatThrownBy(() -> BlockIngredient.CODEC.decode(StrictJson.parse("\"tfc:lamps\"", "t"), "t")).hasMessageContaining("'#tag'");
        BlockStateRef state = BlockStateRef.CODEC.decode(StrictJson.parse("\"tfc:wood/log/oak[axis=y,natural=true]\"", "t"), "t");
        assertThat(state).isEqualTo(new BlockStateRef("tfc:wood/log/oak", Map.of("axis", "y", "natural", "true")));
        assertThat(BlockStateRef.CODEC.decode(StrictJson.parse("\"tfc:rock/cobble/tuff\"", "t"), "t").properties()).isEmpty();
        assertThatThrownBy(() -> BlockStateRef.CODEC.decode(StrictJson.parse("\"tfc:x[axis]\"", "t"), "t")).hasMessageContaining("malformed");
    }

    @Test
    void unknownOrMalformedIngredientsFail() {
        assertThatThrownBy(() -> parse("{\"type\": \"tfc:magic\"}"))
                .isInstanceOf(JsonDataException.class)
                .hasMessageStartingWith("t.json $.type: unknown ingredient type 'tfc:magic'");
        assertThatThrownBy(() -> parse("{\"type\": \"tfc:and\", \"children\": [{\"type\": \"tfc:not_rotten\", \"x\": 1}]}"))
                .hasMessage("t.json $.children[0]: unknown key(s) [x]");
        assertThatThrownBy(() -> parse("{}")).hasMessageContaining("'item', 'tag' or 'type'");
        assertThatThrownBy(() -> parse("[]")).hasMessageContaining("at least 1");
        assertThatThrownBy(() -> parse("{\"item\": \"tfc:a\", \"tag\": \"c:b\"}")).hasMessageContaining("unknown key(s) [tag]");
    }

    @Test
    void anyOfOnlyListsChildren() {
        assertThat(((Ingredient.AnyOf) parse("[{\"item\": \"tfc:a\"}]")).children()).containsExactly(new Ingredient.Item("tfc:a"));
        assertThat(Optional.of(parse("{\"type\": \"tfc:lacks_trait\", \"trait\": \"tfc:canned\"}")))
                .contains(new Ingredient.LacksTrait("tfc:canned"));
    }
}
