/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-09 golden rows: TFC's own tables (c3b4d9536, {@link TfcLootFixtures}) evaluated with scripted draws. Each row
 * pins the drops and, through {@link ScriptedRandom}, the exact order and number of random draws that vanilla 1.21.1's loot
 * engine and TFC's conditions, functions and number providers make. One row (at least) per TFC condition and function.
 */
class LootEvaluatorGoldenTest {

    private static List<LootDrop> roll(String json, LootContext context, Object... draws) {
        ScriptedRandom random = ScriptedRandom.of(draws);
        List<LootDrop> drops = LootEvaluator.evaluate(LootTableParser.parse("test:table", json), context, random);
        random.assertExhausted();
        return drops;
    }

    private static LootContext.Builder block(String id, Map<String, String> state) {
        return LootContext.builder().block(id, state);
    }

    private static LootContext.Tool tool(String id, Set<String> tags, Map<String, Integer> enchantments) {
        return LootContext.Tool.of(id, enchantments, tags::contains);
    }

    private static final LootContext.Tool SHEARS = tool("minecraft:shears", Set.of("c:tools/shear"), Map.of());
    private static final LootContext.Tool KNIFE = tool("tfc:stone/knife/sedimentary", Set.of("tfc:tools/sharp"), Map.of());
    private static final LootContext.Tool SILK_PICK = tool("minecraft:iron_pickaxe", Set.of(),
            Map.of("minecraft:silk_touch", 1));

    // ---- blocks/rock/loose: set_count with block_state_property, explosion_decay, survives_explosion ----

    @Test
    void looseRockCountsFromItsState() {
        String id = "tfc:rock/loose/granite";
        assertThat(roll(TfcLootFixtures.LOOSE_GRANITE, block(id, Map.of("count", "1")).build()))
                .containsExactly(LootDrop.of(id, 1));
        assertThat(roll(TfcLootFixtures.LOOSE_GRANITE, block(id, Map.of("count", "2")).build()))
                .containsExactly(LootDrop.of(id, 2));
        assertThat(roll(TfcLootFixtures.LOOSE_GRANITE, block(id, Map.of("count", "3")).build()))
                .containsExactly(LootDrop.of(id, 3));
    }

    @Test
    void looseRockInAnExplosionSurvivesThenDecaysPerItem() {
        String id = "tfc:rock/loose/granite";
        LootContext boom = block(id, Map.of("count", "3")).explosionRadius(4f).build();
        // pool survives_explosion: 0.1 <= 1/4; explosion_decay: one draw per item, kept when <= 0.25
        assertThat(roll(TfcLootFixtures.LOOSE_GRANITE, boom, 0.1f, 0.2f, 0.5f, 0.25f))
                .containsExactly(LootDrop.of(id, 2));
        // the pool fails survives_explosion: nothing else is drawn
        assertThat(roll(TfcLootFixtures.LOOSE_GRANITE, boom, 0.3f)).isEmpty();
        // every item decays away: the stack is dropped entirely
        assertThat(roll(TfcLootFixtures.LOOSE_GRANITE, boom, 0.0f, 0.9f, 0.9f, 0.9f)).isEmpty();
    }

    // ---- tfc:is_isolated, uniform set_count ----

    @Test
    void rawRockDropsItselfOnlyWhenIsolated() {
        LootContext isolated = block("tfc:rock/raw/granite", Map.of()).isolated(true).build();
        assertThat(roll(TfcLootFixtures.RAW_GRANITE, isolated)).containsExactly(LootDrop.of("tfc:rock/raw/granite", 1));
        LootContext mined = block("tfc:rock/raw/granite", Map.of()).build();
        // uniform 1..4: nextInt(4) = 2 -> 3
        assertThat(roll(TfcLootFixtures.RAW_GRANITE, mined, 2)).containsExactly(LootDrop.of("tfc:rock/loose/granite", 3));
    }

    // ---- leaves: match_tool (item tag and enchantment), inverted, any_of, random_chance, alternatives ----

    @Test
    void leavesWithShearsDropThemselvesAndRollTheSapling() {
        LootContext c = block("tfc:wood/leaves/oak", Map.of()).tool(SHEARS).build();
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.5f)).containsExactly(LootDrop.of("tfc:wood/leaves/oak", 1));
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.012f))
                .containsExactly(LootDrop.of("tfc:wood/leaves/oak", 1), LootDrop.of("tfc:wood/sapling/oak", 1));
    }

    @Test
    void leavesWithSilkTouchDropThemselves() {
        LootContext c = block("tfc:wood/leaves/oak", Map.of()).tool(SILK_PICK).build();
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.9f)).containsExactly(LootDrop.of("tfc:wood/leaves/oak", 1));
    }

    @Test
    void leavesWithASharpToolRollSticksAtTwentyPercent() {
        LootContext c = block("tfc:wood/leaves/oak", Map.of()).tool(KNIFE).build();
        // sapling 0.01 < 0.013; sharp stick 0.1 < 0.2; count uniform 1..2: nextInt(2) = 1 -> 2
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.01f, 0.1f, 1))
                .containsExactly(LootDrop.of("tfc:wood/sapling/oak", 1), LootDrop.of("minecraft:stick", 2));
        // sharp stick fails (0.3), the plain 5% child is tried next (0.04 < 0.05)
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.5f, 0.3f, 0.04f, 0))
                .containsExactly(LootDrop.of("minecraft:stick", 1));
    }

    @Test
    void leavesByHandSkipTheToolChildWithoutDrawing() {
        LootContext c = block("tfc:wood/leaves/oak", Map.of()).build();
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.5f, 0.06f)).isEmpty();
        assertThat(roll(TfcLootFixtures.OAK_LEAVES, c, 0.5f, 0.04f, 0)).containsExactly(LootDrop.of("minecraft:stick", 1));
    }

    // ---- gravel: table_bonus ----

    @Test
    void gravelFlintChanceFollowsFortuneLevel() {
        String id = "tfc:rock/gravel/granite";
        LootContext hand = block(id, Map.of()).build();
        assertThat(roll(TfcLootFixtures.GRANITE_GRAVEL, hand, 0.05f)).containsExactly(LootDrop.of("minecraft:flint", 1));
        assertThat(roll(TfcLootFixtures.GRANITE_GRAVEL, hand, 0.1f)).containsExactly(LootDrop.of(id, 1));
        LootContext fortune2 = block(id, Map.of())
                .tool(tool("minecraft:iron_shovel", Set.of(), Map.of("minecraft:fortune", 2))).build();
        assertThat(roll(TfcLootFixtures.GRANITE_GRAVEL, fortune2, 0.2f)).containsExactly(LootDrop.of("minecraft:flint", 1));
        LootContext fortune9 = block(id, Map.of())
                .tool(tool("minecraft:iron_shovel", Set.of(), Map.of("minecraft:fortune", 9))).build();
        assertThat(roll(TfcLootFixtures.GRANITE_GRAVEL, fortune9, 0.99f)).containsExactly(LootDrop.of("minecraft:flint", 1));
        LootContext silk = block(id, Map.of()).tool(SILK_PICK).build();
        assertThat(roll(TfcLootFixtures.GRANITE_GRAVEL, silk)).containsExactly(LootDrop.of(id, 1));
    }

    // ---- slabs: constant set_count with a block_state_property condition ----

    @Test
    void doubleSlabDropsTwo() {
        String id = "tfc:cut_sandstone/black_slab";
        assertThat(roll(TfcLootFixtures.BLACK_SANDSTONE_SLAB, block(id, Map.of("type", "double")).build()))
                .containsExactly(LootDrop.of(id, 2));
        assertThat(roll(TfcLootFixtures.BLACK_SANDSTONE_SLAB, block(id, Map.of("type", "bottom")).build()))
                .containsExactly(LootDrop.of(id, 1));
        // the condition names the block: another block id fails it
        assertThat(roll(TfcLootFixtures.BLACK_SANDSTONE_SLAB, block("tfc:other", Map.of("type", "double")).build()))
                .containsExactly(LootDrop.of(id, 1));
    }

    // ---- tfc:is_burnt_out ----

    @Test
    void burntOutTorchDropsStickOrAshByChance() {
        LootContext burnt = block("tfc:torch", Map.of()).burntOut(true).build();
        assertThat(roll(TfcLootFixtures.TORCH, burnt, 0.3f, 0.2f)).containsExactly(LootDrop.of("tfc:powder/wood_ash", 1));
        assertThat(roll(TfcLootFixtures.TORCH, burnt, 0.1f)).containsExactly(LootDrop.of("minecraft:stick", 1));
        assertThat(roll(TfcLootFixtures.TORCH, burnt, 0.3f, 0.3f)).isEmpty();
        assertThat(roll(TfcLootFixtures.TORCH, block("tfc:torch", Map.of()).build()))
                .containsExactly(LootDrop.of("tfc:torch", 1));
    }

    // ---- tfc:crop_yield_uniform ----

    @Test
    void matureCropYieldLerpsByTheCropEntityYield() {
        String id = "tfc:crop/green_bean";
        Map<String, String> ripe = Map.of("age", "7", "part", "bottom", "stick", "false");
        // max = uniform(6, 10).getFloat = 0.5 * 4 + 6 = 8; lerp(0.5, 0, 8) = 4
        assertThat(roll(TfcLootFixtures.GREEN_BEAN, block(id, ripe).cropYield(0.5f).build(), 0.5f))
                .containsExactly(LootDrop.of("tfc:food/green_bean", 4), LootDrop.of("tfc:seeds/green_bean", 1));
        // max = 7; lerp(0.3, 0, 7) = 2.1 -> 2
        assertThat(roll(TfcLootFixtures.GREEN_BEAN, block(id, ripe).cropYield(0.3f).build(), 0.25f))
                .containsExactly(LootDrop.of("tfc:food/green_bean", 2), LootDrop.of("tfc:seeds/green_bean", 1));
        // no crop block entity: min (0), no draw; a zero count drops nothing
        assertThat(roll(TfcLootFixtures.GREEN_BEAN, block(id, ripe).build()))
                .containsExactly(LootDrop.of("tfc:seeds/green_bean", 1));
        Map<String, String> withStick = Map.of("age", "3", "part", "bottom", "stick", "true");
        assertThat(roll(TfcLootFixtures.GREEN_BEAN, block(id, withStick).build()))
                .containsExactly(LootDrop.of("tfc:seeds/green_bean", 1), LootDrop.of("minecraft:stick", 1));
        assertThat(roll(TfcLootFixtures.GREEN_BEAN, block(id, Map.of("age", "7", "part", "top", "stick", "true")).build()))
                .isEmpty();
    }

    // ---- copy_components, tfc:apply_stack_size, tfc:copy_fluid, tfc:rotten: kept as stack ops ----

    @Test
    void itemDataFunctionsBecomeOpsInOrder() {
        LootContext sealed = block("tfc:ceramic/large_vessel", Map.of("sealed", "true")).blockEntity(true).build();
        assertThat(roll(TfcLootFixtures.LARGE_VESSEL, sealed)).containsExactly(new LootDrop("tfc:ceramic/large_vessel", 1,
                List.of(new LootFunction.StackOp.CopyComponents("block_entity", List.of("minecraft:custom_name",
                        "tfc:contents")), new LootFunction.StackOp.ApplyStackSize())));
        LootContext open = block("tfc:ceramic/large_vessel", Map.of("sealed", "false")).build();
        assertThat(roll(TfcLootFixtures.LARGE_VESSEL, open)).containsExactly(LootDrop.of("tfc:ceramic/large_vessel", 1));
        assertThat(roll(TfcLootFixtures.RED_STEEL_LAMP, block("tfc:metal/lamp/red_steel", Map.of()).build()))
                .containsExactly(new LootDrop("tfc:metal/lamp/red_steel", 1,
                        List.of(new LootFunction.StackOp.CopyFluid())));
        assertThat(roll(TfcLootFixtures.ROTTEN_MELON, block("tfc:rotten_melon", Map.of()).build()))
                .containsExactly(new LootDrop("tfc:melon", 1, List.of(new LootFunction.StackOp.Rotten())));
    }

    // ---- tfc:is_sluice; draw order inside a conditions list ----

    @Test
    void depositRollsDrawBeforeTheSluiceCheck() {
        LootContext panned = LootContext.builder().build();
        // small ore 0.7 fails; sluice child draws 0.05 (< 0.1) then fails is_sluice; loose rock 0.4 < 0.5
        assertThat(roll(TfcLootFixtures.CASSITERITE_ANDESITE, panned, 0.7f, 0.05f, 0.4f))
                .containsExactly(LootDrop.of("tfc:rock/loose/andesite", 1));
        LootContext sluiced = LootContext.builder().sluice(true).build();
        assertThat(roll(TfcLootFixtures.CASSITERITE_ANDESITE, sluiced, 0.7f, 0.05f))
                .containsExactly(LootDrop.of("tfc:ore/small_cassiterite", 1));
        assertThat(roll(TfcLootFixtures.CASSITERITE_ANDESITE, panned, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f)).isEmpty();
    }

    // ---- tfc:animal_yield, tfc:not_predated, tfc:is_male ----

    @Test
    void cowYieldScalesWithSizeAttackAndFamiliarity() {
        LootContext byPlayer = LootContext.builder().animal(new LootContext.Animal(16f, 0f, false))
                .lastDamagePlayer(1.0).attacker(LootContext.Attacker.PLAYER).build();
        // size 16 + map(1 attack: 0..16 -> 1..8) = 17.4375; scaled (17.4375-1)/45; max uniform(17,23) at 0.5 = 20;
        // lerp -> 11.11 -> 11 beef; hide; bones uniform 1..4 nextInt(4) = 3 -> 4
        assertThat(roll(TfcLootFixtures.COW, byPlayer, 0.5f, 3)).containsExactly(LootDrop.of("tfc:food/beef", 11),
                LootDrop.of("tfc:large_raw_hide", 1), LootDrop.of("minecraft:bone", 4));
        LootContext best = LootContext.builder().animal(new LootContext.Animal(32f, 1f, false))
                .lastDamagePlayer(16.0).attacker(LootContext.Attacker.PLAYER).build();
        assertThat(roll(TfcLootFixtures.COW, best, 0.0f, 0)).containsExactly(LootDrop.of("tfc:food/beef", 17),
                LootDrop.of("tfc:large_raw_hide", 1), LootDrop.of("minecraft:bone", 1));
        LootContext eaten = LootContext.builder().animal(new LootContext.Animal(1f, 0f, false))
                .attacker(LootContext.Attacker.PREDATOR).build();
        // size 1 -> scaled 0 -> lerp = min (6); max is still drawn (0.9); the predated pools fail
        assertThat(roll(TfcLootFixtures.COW, eaten, 0.9f)).containsExactly(LootDrop.of("tfc:food/beef", 6));
        // not a TFC animal: min, no draw
        assertThat(roll(TfcLootFixtures.COW, LootContext.builder().thisEntity(true).build(), 1))
                .containsExactly(LootDrop.of("tfc:food/beef", 6), LootDrop.of("tfc:large_raw_hide", 1),
                        LootDrop.of("minecraft:bone", 2));
    }

    @Test
    void onlyMaleGoatsDropAHorn() {
        LootContext male = LootContext.builder().animal(new LootContext.Animal(1f, 0f, true)).build();
        assertThat(roll(TfcLootFixtures.GOAT, male, 0.5f, 0)).contains(LootDrop.of("tfc:goat_horn", 1));
        LootContext female = LootContext.builder().animal(new LootContext.Animal(1f, 0f, false)).build();
        assertThat(roll(TfcLootFixtures.GOAT, female, 0.5f, 0)).doesNotContain(LootDrop.of("tfc:goat_horn", 1));
    }

    // ---- killed_by_player, damage_source_properties ----

    @Test
    void lobsterNeedsAPlayerKillOrAPlayerKillDamageType() {
        assertThat(roll(TfcLootFixtures.LOBSTER, LootContext.builder().lastDamagePlayer(1.0).build()))
                .containsExactly(LootDrop.of("tfc:food/shellfish", 1));
        assertThat(roll(TfcLootFixtures.LOBSTER, LootContext.builder()
                .damageTags(Set.of("tfc:counts_as_player_kill")).build()))
                .containsExactly(LootDrop.of("tfc:food/shellfish", 1));
        assertThat(roll(TfcLootFixtures.LOBSTER, LootContext.builder().damageTags(Set.of("minecraft:is_fire")).build()))
                .isEmpty();
        assertThat(roll(TfcLootFixtures.LOBSTER, LootContext.builder().build())).isEmpty();
    }

    // ---- vanilla mechanics TFC's tables do not exercise ----

    @Test
    void weightedPickDrawsOnceAmongSeveralCandidates() {
        String json = "{\"pools\":[{\"rolls\":2,\"entries\":[{\"type\":\"item\",\"name\":\"a:x\",\"weight\":3},"
                + "{\"type\":\"empty\",\"weight\":1},{\"type\":\"item\",\"name\":\"a:y\",\"weight\":0}]}]}";
        LootContext c = LootContext.builder().build();
        assertThat(roll(json, c, 2, 3)).containsExactly(LootDrop.of("a:x", 1));
    }

    @Test
    void groupAndSequenceExpandLikeVanilla() {
        String json = "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"sequence\",\"children\":["
                + "{\"type\":\"item\",\"name\":\"a:x\"},"
                + "{\"type\":\"item\",\"name\":\"a:y\",\"conditions\":[{\"condition\":\"tfc:is_sluice\"}]},"
                + "{\"type\":\"item\",\"name\":\"a:z\"}]}]},"
                + "{\"rolls\":1,\"entries\":[{\"type\":\"group\",\"children\":[{\"type\":\"item\",\"name\":\"a:x\","
                + "\"conditions\":[{\"condition\":\"tfc:is_sluice\"}]},{\"type\":\"item\",\"name\":\"a:z\"}]}]}],"
                + "\"functions\":[{\"function\":\"set_count\",\"count\":5},{\"function\":\"limit_count\",\"limit\":{\"max\":4}}]}";
        // pool 1: the sequence stops at a:y, so only a:x is a candidate; pool 2: the group gives a:z only
        assertThat(roll(json, LootContext.builder().build()))
                .containsExactly(LootDrop.of("a:x", 4), LootDrop.of("a:z", 4));
    }

    @Test
    void randomChanceEvaluatesItsChanceBeforeTheDraw() {
        // vanilla LootItemRandomChanceCondition: chance.getFloat(ctx) first, then nextFloat()
        String json = "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"item\",\"name\":\"a:x\"}],"
                + "\"conditions\":[{\"condition\":\"random_chance\","
                + "\"chance\":{\"type\":\"uniform\",\"min\":0,\"max\":1}}]}]}";
        // chance 0.2, roll 0.5: fails (the reversed order would read roll 0.2 < chance 0.5)
        assertThat(roll(json, LootContext.builder().build(), 0.2f, 0.5f)).isEmpty();
        assertThat(roll(json, LootContext.builder().build(), 0.5f, 0.2f)).containsExactly(LootDrop.of("a:x", 1));
    }

    @Test
    void limitCountClampsLikeMthClamp() {
        // Mth.clamp(v, min, max) = min(max(v, min), max): with min > max the max wins
        String json = "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"item\",\"name\":\"a:x\"}]}],"
                + "\"functions\":[{\"function\":\"limit_count\",\"limit\":{\"min\":5,\"max\":3}}]}";
        assertThat(roll(json, LootContext.builder().build())).containsExactly(LootDrop.of("a:x", 3));
    }

    @Test
    void tfpRenamedTypesReadAsTfc() {
        String json = "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"tfp:rock/raw/granite\","
                + "\"conditions\":[{\"condition\":\"tfp:is_isolated\"}]}]}]}";
        assertThat(roll(json, LootContext.builder().isolated(true).build()))
                .containsExactly(LootDrop.of("tfp:rock/raw/granite", 1));
    }

    @Test
    void unknownRulesAreParseErrorsWithTheirPath() {
        assertThatThrownBy(() -> LootTableParser.parse("t:x",
                "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"item\",\"name\":\"a:b\","
                        + "\"conditions\":[{\"condition\":\"minecraft:weather_check\"}]}]}]}"))
                .isInstanceOf(LootTableParser.LootParseException.class)
                .hasMessageContaining("t:x at $.pools[0].entries[0].conditions[0].condition")
                .hasMessageContaining("minecraft:weather_check");
        assertThatThrownBy(() -> LootTableParser.parse("t:x",
                "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"item\",\"name\":\"a:b\",\"conditions\":"
                        + "[{\"condition\":\"match_tool\",\"predicate\":{\"count\":{\"min\":2}}}]}]}]}"))
                .hasMessageContaining("predicate.count");
        assertThatThrownBy(() -> LootTableParser.parse("t:x", "{\"pools\":[{\"entries\":[{\"type\":\"tag\"}]}]}"))
                .hasMessageContaining("unsupported entry type minecraft:tag");
    }
}
