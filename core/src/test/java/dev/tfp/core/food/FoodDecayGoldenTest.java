/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * M3-SURV-01 golden values. Every row below was produced by TFC's own food arithmetic from TFC at c3b4d9536
 * ({@code FoodCapability.getDecayDateModifier/getLifeTime/isRotten/getRoundedCreationDate/calculateNewCreationDate},
 * copied verbatim into a scratch oracle with stubbed config and calendar; default config: foodDecayModifier 1,
 * foodDecayStackTicks 7200, trait modifiers from {@code ServerConfig}).
 *
 * <p>Rows:
 * <ul>
 *   <li>{@code G|food decay_modifier|traits|decayDateModifier float bits|lifeTime|rottenDate|rotten at rottenDate-1|rotten at rottenDate}
 *       for a food created at tick 1 234 567 (-1 = never rots). Decay modifiers are TFC food data values, e.g.
 *       sealed_preserves 0.1, barley_grain 0.25, cheese 0.3, potato 0.666, barley_bread 1.0, cooked_beef 1.5, squash 1.67,
 *       beef 2.0, cooked_arctic_char 2.25, green_bell_pepper 2.7, cod 3.0, barley_bread_sandwich 4.5, blueberry 4.9,
 *       jam 5.0, rotten_flesh infinity, and 0 (no decay_modifier).</li>
 *   <li>{@code R|tick|rounded creation date}</li>
 *   <li>{@code T|creation date|now|trait|creation date after applyTrait|creation date after removeTrait}</li>
 *   <li>{@code P|old creation date|new rounded creation date}: updateFoodFromPrevious, beef (2.0) to wood-grilled
 *       cooked beef (1.5) at tick 2 000 000.</li>
 * </ul>
 */
class FoodDecayGoldenTest {
    private static final long CREATED = 1_234_567L;

    // from TFC at c3b4d9536
    private static final String GOLDEN = """
            G|0.1f||1092616192|5280000|6514567|false|true
            G|0.1f|salted|1101004800|10560000|11794567|false|true
            G|0.1f|brined|1092616192|5280000|6514567|false|true
            G|0.1f|pickled|1101004800|10560000|11794567|false|true
            G|0.1f|preserved|1101004800|10560000|11794567|false|true
            G|0.1f|brined,pickled|1101004800|10560000|11794567|false|true
            G|0.1f|salted,brined,pickled|1109393408|21120000|22354567|false|true
            G|0.1f|vinegar|1120403455|52799996|54034563|false|true
            G|0.1f|wood_grilled|1095237631|6599999|7834566|false|true
            G|0.1f|charcoal_grilled|1090519040|4224000|5458567|false|true
            G|0.1f|burnt_to_a_crisp|1082130432|2112000|3346567|false|true
            G|0.1f|wild|1101004800|10560000|11794567|false|true
            G|0.1f|canned|2139095040|-1|-1|false|false
            G|0.1f|salted,preserved|1109393408|21120000|22354567|false|true
            G|0.25f||1082130432|2112000|3346567|false|true
            G|0.25f|salted|1090519040|4224000|5458567|false|true
            G|0.25f|brined|1082130432|2112000|3346567|false|true
            G|0.25f|pickled|1090519040|4224000|5458567|false|true
            G|0.25f|preserved|1090519040|4224000|5458567|false|true
            G|0.25f|brined,pickled|1090519040|4224000|5458567|false|true
            G|0.25f|salted,brined,pickled|1098907648|8448000|9682567|false|true
            G|0.25f|vinegar|1109393408|21120000|22354567|false|true
            G|0.25f|wood_grilled|1084227584|2640000|3874567|false|true
            G|0.25f|charcoal_grilled|1078774989|1689600|2924167|false|true
            G|0.25f|burnt_to_a_crisp|1070386381|844800|2079367|false|true
            G|0.25f|wild|1090519040|4224000|5458567|false|true
            G|0.25f|canned|2139095040|-1|-1|false|false
            G|0.25f|salted,preserved|1098907648|8448000|9682567|false|true
            G|0.3f||1079334229|1760000|2994567|false|true
            G|0.3f|salted|1087722837|3520000|4754567|false|true
            G|0.3f|brined|1079334229|1760000|2994567|false|true
            G|0.3f|pickled|1087722837|3520000|4754567|false|true
            G|0.3f|preserved|1087722837|3520000|4754567|false|true
            G|0.3f|brined,pickled|1087722837|3520000|4754567|false|true
            G|0.3f|salted,brined,pickled|1096111445|7040000|8274567|false|true
            G|0.3f|vinegar|1107645781|17600000|18834567|false|true
            G|0.3f|wood_grilled|1082479957|2200000|3434567|false|true
            G|0.3f|charcoal_grilled|1076538027|1408000|2642567|false|true
            G|0.3f|burnt_to_a_crisp|1068149419|704000|1938567|false|true
            G|0.3f|wild|1087722837|3520000|4754567|false|true
            G|0.3f|canned|2139095040|-1|-1|false|false
            G|0.3f|salted,preserved|1096111445|7040000|8274567|false|true
            G|0.666f||1069560115|792792|2027359|false|true
            G|0.666f|salted|1077948723|1585585|2820152|false|true
            G|0.666f|brined|1069560115|792792|2027359|false|true
            G|0.666f|pickled|1077948723|1585585|2820152|false|true
            G|0.666f|preserved|1077948723|1585585|2820152|false|true
            G|0.666f|brined,pickled|1077948723|1585585|2820152|false|true
            G|0.666f|salted,brined,pickled|1086337331|3171171|4405738|false|true
            G|0.666f|vinegar|1097874816|7927927|9162494|false|true
            G|0.666f|wood_grilled|1072708992|990990|2225557|false|true
            G|0.666f|charcoal_grilled|1067041014|634234|1868801|false|true
            G|0.666f|burnt_to_a_crisp|1058652406|317117|1551684|false|true
            G|0.666f|wild|1077948723|1585585|2820152|false|true
            G|0.666f|canned|2139095040|-1|-1|false|false
            G|0.666f|salted,preserved|1086337331|3171171|4405738|false|true
            G|1.0f||1065353216|528000|1762567|false|true
            G|1.0f|salted|1073741824|1056000|2290567|false|true
            G|1.0f|brined|1065353216|528000|1762567|false|true
            G|1.0f|pickled|1073741824|1056000|2290567|false|true
            G|1.0f|preserved|1073741824|1056000|2290567|false|true
            G|1.0f|brined,pickled|1073741824|1056000|2290567|false|true
            G|1.0f|salted,brined,pickled|1082130432|2112000|3346567|false|true
            G|1.0f|vinegar|1092616192|5280000|6514567|false|true
            G|1.0f|wood_grilled|1067450368|660000|1894567|false|true
            G|1.0f|charcoal_grilled|1061997773|422400|1656967|false|true
            G|1.0f|burnt_to_a_crisp|1053609165|211200|1445767|false|true
            G|1.0f|wild|1073741824|1056000|2290567|false|true
            G|1.0f|canned|2139095040|-1|-1|false|false
            G|1.0f|salted,preserved|1082130432|2112000|3346567|false|true
            G|1.5f||1059760811|352000|1586567|false|true
            G|1.5f|salted|1068149419|704000|1938567|false|true
            G|1.5f|brined|1059760811|352000|1586567|false|true
            G|1.5f|pickled|1068149419|704000|1938567|false|true
            G|1.5f|preserved|1068149419|704000|1938567|false|true
            G|1.5f|brined,pickled|1068149419|704000|1938567|false|true
            G|1.5f|salted,brined,pickled|1076538027|1408000|2642567|false|true
            G|1.5f|vinegar|1087722837|3520000|4754567|false|true
            G|1.5f|wood_grilled|1062557013|440000|1674567|false|true
            G|1.5f|charcoal_grilled|1057523849|281600|1516167|false|true
            G|1.5f|burnt_to_a_crisp|1049135241|140800|1375367|false|true
            G|1.5f|wild|1068149419|704000|1938567|false|true
            G|1.5f|canned|2139095040|-1|-1|false|false
            G|1.5f|salted,preserved|1076538027|1408000|2642567|false|true
            G|1.67f||1058622237|316167|1550734|false|true
            G|1.67f|salted|1067010845|632335|1866902|false|true
            G|1.67f|brined|1058622237|316167|1550734|false|true
            G|1.67f|pickled|1067010845|632335|1866902|false|true
            G|1.67f|preserved|1067010845|632335|1866902|false|true
            G|1.67f|brined,pickled|1067010845|632335|1866902|false|true
            G|1.67f|salted,brined,pickled|1075399453|1264670|2499237|false|true
            G|1.67f|vinegar|1086299621|3161676|4396243|false|true
            G|1.67f|wood_grilled|1061133797|395209|1629776|false|true
            G|1.67f|charcoal_grilled|1056261373|252934|1487501|false|true
            G|1.67f|burnt_to_a_crisp|1047872765|126467|1361034|false|true
            G|1.67f|wild|1067010845|632335|1866902|false|true
            G|1.67f|canned|2139095040|-1|-1|false|false
            G|1.67f|salted,preserved|1075399453|1264670|2499237|false|true
            G|2.0f||1056964608|264000|1498567|false|true
            G|2.0f|salted|1065353216|528000|1762567|false|true
            G|2.0f|brined|1056964608|264000|1498567|false|true
            G|2.0f|pickled|1065353216|528000|1762567|false|true
            G|2.0f|preserved|1065353216|528000|1762567|false|true
            G|2.0f|brined,pickled|1065353216|528000|1762567|false|true
            G|2.0f|salted,brined,pickled|1073741824|1056000|2290567|false|true
            G|2.0f|vinegar|1084227584|2640000|3874567|false|true
            G|2.0f|wood_grilled|1059061760|330000|1564567|false|true
            G|2.0f|charcoal_grilled|1053609165|211200|1445767|false|true
            G|2.0f|burnt_to_a_crisp|1045220557|105600|1340167|false|true
            G|2.0f|wild|1065353216|528000|1762567|false|true
            G|2.0f|canned|2139095040|-1|-1|false|false
            G|2.0f|salted,preserved|1073741824|1056000|2290567|false|true
            G|2.25f||1055100473|234666|1469233|false|true
            G|2.25f|salted|1063489081|469333|1703900|false|true
            G|2.25f|brined|1055100473|234666|1469233|false|true
            G|2.25f|pickled|1063489081|469333|1703900|false|true
            G|2.25f|preserved|1063489081|469333|1703900|false|true
            G|2.25f|brined,pickled|1063489081|469333|1703900|false|true
            G|2.25f|salted,brined,pickled|1071877689|938666|2173233|false|true
            G|2.25f|vinegar|1083062499|2346666|3581233|false|true
            G|2.25f|wood_grilled|1057896675|293333|1527900|false|true
            G|2.25f|charcoal_grilled|1052117857|187733|1422300|false|true
            G|2.25f|burnt_to_a_crisp|1043729249|93866|1328433|false|true
            G|2.25f|wild|1063489081|469333|1703900|false|true
            G|2.25f|canned|2139095040|-1|-1|false|false
            G|2.25f|salted,preserved|1071877689|938666|2173233|false|true
            G|2.7f||1052614959|195555|1430122|false|true
            G|2.7f|salted|1061003567|391111|1625678|false|true
            G|2.7f|brined|1052614959|195555|1430122|false|true
            G|2.7f|pickled|1061003567|391111|1625678|false|true
            G|2.7f|preserved|1061003567|391111|1625678|false|true
            G|2.7f|brined,pickled|1061003567|391111|1625678|false|true
            G|2.7f|salted,brined,pickled|1069392175|782222|2016789|false|true
            G|2.7f|vinegar|1080887675|1955555|3190122|false|true
            G|2.7f|wood_grilled|1055721851|244444|1479011|false|true
            G|2.7f|charcoal_grilled|1050129446|156444|1391011|false|true
            G|2.7f|burnt_to_a_crisp|1041740838|78222|1312789|false|true
            G|2.7f|wild|1061003567|391111|1625678|false|true
            G|2.7f|canned|2139095040|-1|-1|false|false
            G|2.7f|salted,preserved|1069392175|782222|2016789|false|true
            G|3.0f||1051372203|176000|1410567|false|true
            G|3.0f|salted|1059760811|352000|1586567|false|true
            G|3.0f|brined|1051372203|176000|1410567|false|true
            G|3.0f|pickled|1059760811|352000|1586567|false|true
            G|3.0f|preserved|1059760811|352000|1586567|false|true
            G|3.0f|brined,pickled|1059760811|352000|1586567|false|true
            G|3.0f|salted,brined,pickled|1068149419|704000|1938567|false|true
            G|3.0f|vinegar|1079334229|1760000|2994567|false|true
            G|3.0f|wood_grilled|1054168405|220000|1454567|false|true
            G|3.0f|charcoal_grilled|1049135241|140800|1375367|false|true
            G|3.0f|burnt_to_a_crisp|1040746633|70400|1304967|false|true
            G|3.0f|wild|1059760811|352000|1586567|false|true
            G|3.0f|canned|2139095040|-1|-1|false|false
            G|3.0f|salted,preserved|1068149419|704000|1938567|false|true
            G|4.5f||1046711865|117333|1351900|false|true
            G|4.5f|salted|1055100473|234666|1469233|false|true
            G|4.5f|brined|1046711865|117333|1351900|false|true
            G|4.5f|pickled|1055100473|234666|1469233|false|true
            G|4.5f|preserved|1055100473|234666|1469233|false|true
            G|4.5f|brined,pickled|1055100473|234666|1469233|false|true
            G|4.5f|salted,brined,pickled|1063489081|469333|1703900|false|true
            G|4.5f|vinegar|1074673891|1173333|2407900|false|true
            G|4.5f|wood_grilled|1049508067|146666|1381233|false|true
            G|4.5f|charcoal_grilled|1043729249|93866|1328433|false|true
            G|4.5f|burnt_to_a_crisp|1035340641|46933|1281500|false|true
            G|4.5f|wild|1055100473|234666|1469233|false|true
            G|4.5f|canned|2139095040|-1|-1|false|false
            G|4.5f|salted,preserved|1063489081|469333|1703900|false|true
            G|4.9f||1045494470|107755|1342322|false|true
            G|4.9f|salted|1053883078|215510|1450077|false|true
            G|4.9f|brined|1045494470|107755|1342322|false|true
            G|4.9f|pickled|1053883078|215510|1450077|false|true
            G|4.9f|preserved|1053883078|215510|1450077|false|true
            G|4.9f|brined,pickled|1053883078|215510|1450077|false|true
            G|4.9f|salted,brined,pickled|1062271686|431020|1665587|false|true
            G|4.9f|vinegar|1073913020|1077551|2312118|false|true
            G|4.9f|wood_grilled|1048747196|134693|1369260|false|true
            G|4.9f|charcoal_grilled|1042755333|86204|1320771|false|true
            G|4.9f|burnt_to_a_crisp|1034366725|43102|1277669|false|true
            G|4.9f|wild|1053883078|215510|1450077|false|true
            G|4.9f|canned|2139095040|-1|-1|false|false
            G|4.9f|salted,preserved|1062271686|431020|1665587|false|true
            G|5.0f||1045220557|105600|1340167|false|true
            G|5.0f|salted|1053609165|211200|1445767|false|true
            G|5.0f|brined|1045220557|105600|1340167|false|true
            G|5.0f|pickled|1053609165|211200|1445767|false|true
            G|5.0f|preserved|1053609165|211200|1445767|false|true
            G|5.0f|brined,pickled|1053609165|211200|1445767|false|true
            G|5.0f|salted,brined,pickled|1061997773|422400|1656967|false|true
            G|5.0f|vinegar|1073741824|1056000|2290567|false|true
            G|5.0f|wood_grilled|1048576000|132000|1366567|false|true
            G|5.0f|charcoal_grilled|1042536202|84480|1319047|false|true
            G|5.0f|burnt_to_a_crisp|1034147594|42240|1276807|false|true
            G|5.0f|wild|1053609165|211200|1445767|false|true
            G|5.0f|canned|2139095040|-1|-1|false|false
            G|5.0f|salted,preserved|1061997773|422400|1656967|false|true
            G|INFf||0|0|1234567|true|true
            G|INFf|salted|0|0|1234567|true|true
            G|INFf|brined|0|0|1234567|true|true
            G|INFf|pickled|0|0|1234567|true|true
            G|INFf|preserved|0|0|1234567|true|true
            G|INFf|brined,pickled|0|0|1234567|true|true
            G|INFf|salted,brined,pickled|0|0|1234567|true|true
            G|INFf|vinegar|0|0|1234567|true|true
            G|INFf|wood_grilled|0|0|1234567|true|true
            G|INFf|charcoal_grilled|0|0|1234567|true|true
            G|INFf|burnt_to_a_crisp|0|0|1234567|true|true
            G|INFf|wild|0|0|1234567|true|true
            G|INFf|canned|-4194304|0|1234567|false|true
            G|INFf|salted,preserved|0|0|1234567|true|true
            G|0.0f||2139095040|-1|-1|false|false
            G|0.0f|salted|2139095040|-1|-1|false|false
            G|0.0f|brined|2139095040|-1|-1|false|false
            G|0.0f|pickled|2139095040|-1|-1|false|false
            G|0.0f|preserved|2139095040|-1|-1|false|false
            G|0.0f|brined,pickled|2139095040|-1|-1|false|false
            G|0.0f|salted,brined,pickled|2139095040|-1|-1|false|false
            G|0.0f|vinegar|2139095040|-1|-1|false|false
            G|0.0f|wood_grilled|2139095040|-1|-1|false|false
            G|0.0f|charcoal_grilled|2139095040|-1|-1|false|false
            G|0.0f|burnt_to_a_crisp|2139095040|-1|-1|false|false
            G|0.0f|wild|2139095040|-1|-1|false|false
            G|0.0f|canned|2139095040|-1|-1|false|false
            G|0.0f|salted,preserved|2139095040|-1|-1|false|false
            R|0|7200
            R|1|7200
            R|7199|7200
            R|7200|7200
            R|7201|14400
            R|14399|14400
            R|14400|14400
            R|1234567|1238400
            R|999999999|1000000800
            T|1000000|1200000|salted|800000|1100000
            T|1000000|1200000|pickled|800000|1100000
            T|1000000|1200000|vinegar|-800000|1180000
            T|1000000|1200000|wood_grilled|950000|1040000
            T|1000000|1200000|burnt_to_a_crisp|1120000|700000
            T|7200|400000|salted|-385600|203600
            T|7200|400000|pickled|-385600|203600
            T|7200|400000|vinegar|-3528000|360720
            T|7200|400000|wood_grilled|-91000|85759
            T|7200|400000|burnt_to_a_crisp|242880|-582000
            T|50000000|50300000|salted|49700000|50150000
            T|50000000|50300000|pickled|49700000|50150000
            T|50000000|50300000|vinegar|47300000|50270000
            T|50000000|50300000|wood_grilled|49925000|50060000
            T|50000000|50300000|burnt_to_a_crisp|50180000|49550000
            T|123456789|123700000|salted|123213584|123578400
            T|123456789|123700000|pickled|123213584|123578400
            T|123456789|123700000|vinegar|121267968|123675680
            T|123456789|123700000|wood_grilled|123395992|123505432
            T|123456789|123700000|burnt_to_a_crisp|123602720|123091984
            P|1900000|1836000
            """;

    private static List<String[]> rows(String kind) {
        List<String[]> out = new ArrayList<>();
        for (String line : GOLDEN.strip().split("\n")) {
            String[] f = line.strip().split("\\|", -1);
            if (f[0].equals(kind)) {
                out.add(f);
            }
        }
        return out;
    }

    private static float food(String s) {
        return s.equals("INFf") ? Float.POSITIVE_INFINITY : Float.parseFloat(s);
    }

    private static List<FoodTrait> traits(String s) {
        if (s.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(s.split(",")).map(FoodDecayGoldenTest::trait).toList();
    }

    private static FoodTrait trait(String name) {
        FoodTrait t = FoodTrait.byId("tfp:" + name);
        assertThat(t).as(name).isNotNull();
        return t;
    }

    @Test
    void goldenTableIsComplete() {
        assertThat(rows("G")).hasSize(224);
        assertThat(rows("R")).hasSize(9);
        assertThat(rows("T")).hasSize(20);
        assertThat(rows("P")).hasSize(1);
    }

    @TestFactory
    Stream<DynamicTest> decayDates() {
        return rows("G").stream().map(r -> DynamicTest.dynamicTest(r[1] + " [" + r[2] + "]", () -> {
            float food = food(r[1]);
            List<FoodTrait> traits = traits(r[2]);
            float m = FoodDecay.decayDateModifier(food, traits);
            assertThat(Float.floatToRawIntBits(m)).as("decayDateModifier bits").isEqualTo(Integer.parseInt(r[3]));
            long life = Long.parseLong(r[4]);
            long rottenDate = Long.parseLong(r[5]);
            boolean before = Boolean.parseBoolean(r[6]);
            boolean at = Boolean.parseBoolean(r[7]);
            if (life < 0) {
                assertThat(m).isEqualTo(Float.POSITIVE_INFINITY);
                assertThat(FoodDecay.stage(CREATED, Long.MAX_VALUE / 2, food, traits)).isEqualTo(FoodStage.NEVER_ROTS);
                assertThat(FoodDecay.isRotten(CREATED, m, Long.MAX_VALUE / 2)).isEqualTo(at);
                return;
            }
            assertThat(FoodDecay.lifeTime(m)).isEqualTo(life);
            assertThat(FoodDecay.rottenDate(CREATED, m)).isEqualTo(rottenDate);
            assertThat(FoodDecay.isRotten(CREATED, m, rottenDate - 1)).isEqualTo(before);
            assertThat(FoodDecay.isRotten(CREATED, m, rottenDate)).isEqualTo(at);
            assertThat(FoodDecay.stage(CREATED, rottenDate - 1, food, traits))
                    .isEqualTo(before ? FoodStage.ROTTEN : FoodStage.FRESH);
            assertThat(FoodDecay.stage(CREATED, rottenDate, food, traits)).isEqualTo(FoodStage.ROTTEN);
            // the same through the stored state
            FoodState state = new FoodState(CREATED, traits);
            assertThat(state.stage(food, rottenDate, FoodConfig.DEFAULT)).isEqualTo(FoodStage.ROTTEN);
        }));
    }

    @TestFactory
    Stream<DynamicTest> roundedCreationDates() {
        return rows("R").stream().map(r -> DynamicTest.dynamicTest("round " + r[1], () -> {
            long tick = Long.parseLong(r[1]);
            long rounded = FoodDecay.roundedCreationDate(tick, FoodDecay.DEFAULT_STACK_TICKS);
            assertThat(rounded).isEqualTo(Long.parseLong(r[2])).isGreaterThanOrEqualTo(tick);
        }));
    }

    @TestFactory
    Stream<DynamicTest> traitCreationDates() {
        return rows("T").stream().map(r -> DynamicTest.dynamicTest(r[3] + " at " + r[2], () -> {
            long ci = Long.parseLong(r[1]);
            long now = Long.parseLong(r[2]);
            FoodTrait t = trait(r[3]);
            long stack = FoodDecay.DEFAULT_STACK_TICKS;
            assertThat(FoodDecay.newCreationDate(ci, 1f / t.decayModifier(), now, stack)).as("apply").isEqualTo(Long.parseLong(r[4]));
            assertThat(FoodDecay.newCreationDate(ci, t.decayModifier(), now, stack)).as("remove").isEqualTo(Long.parseLong(r[5]));
        }));
    }

    @Test
    void inheritedCreationDate() {
        String[] r = rows("P").getFirst();
        float oldM = FoodDecay.decayDateModifier(2.0f, List.of());
        float newM = FoodDecay.decayDateModifier(1.5f, List.of(FoodTrait.WOOD_GRILLED));
        assertThat(FoodDecay.newRoundedCreationDate(Long.parseLong(r[1]), newM / oldM, 2_000_000L, FoodDecay.DEFAULT_STACK_TICKS))
                .isEqualTo(Long.parseLong(r[2]));
    }

    @Test
    void applyingATraitThroughTheStateMatchesTheGolden() {
        // beef (2.0), not rotten at 1 200 000 (rots at 1 264 000): salted -> TFC date 800 000 (row T|1000000|1200000|salted)
        FoodState beef = new FoodState(1_000_000L, List.of());
        FoodState salted = beef.withTraitApplied(FoodTrait.SALTED, 2.0f, 1_200_000L, FoodConfig.DEFAULT);
        assertThat(salted).isEqualTo(new FoodState(800_000L, List.of(FoodTrait.SALTED)));
        // the remaining fraction is kept: 64 000 of 264 000 left before, 128 000 of 528 000 after
        assertThat(FoodDecay.rottenDate(salted.creationDate(), salted.decayDateModifier(2.0f, FoodConfig.DEFAULT)))
                .isEqualTo(1_328_000L);
        // applying again is a no-op; removing restores TFC's removeTrait date for 800 000 at that tick
        assertThat(salted.withTraitApplied(FoodTrait.SALTED, 2.0f, 1_200_000L, FoodConfig.DEFAULT)).isEqualTo(salted);
        assertThat(salted.withTraitRemoved(FoodTrait.SALTED, 2.0f, 1_200_000L, FoodConfig.DEFAULT))
                .isEqualTo(new FoodState(1_000_000L, List.of()));
        // rotten food takes no new trait
        FoodState rotten = beef.withTraitApplied(FoodTrait.SALTED, 2.0f, 1_264_000L, FoodConfig.DEFAULT);
        assertThat(rotten).isEqualTo(new FoodState(FoodDecay.ROTTEN_FLAG, List.of()));
    }
}
