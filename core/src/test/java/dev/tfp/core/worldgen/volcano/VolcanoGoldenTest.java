/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Golden values of the volcano and hot spring decisions (M2-GEN-14). They come from TFC at c3b4d9536: an oracle compiled
 * TFC's own {@code CenteredFeatureNoise} (the cinder sampler), {@code HotSpringFeature}, {@code FissureFeature},
 * {@code FloodFillLakeFeature} and their configs unchanged (codecs removed, the fissure's {@code min_depth} resolved to
 * -48, the lava spring's replacer map in its config order) with Minecraft 1.21.11's {@code Mth} and random sources and stub
 * blocks over {@link VolcanoTestView}'s world, and printed the lines below. Each feature line is the final world minus the
 * terrain: the count, a hash over the changed positions sorted by (x, y, z) with their block names, and the count per block
 * family. The cone lines hash every sampled height (raw double bits), easing (raw float bits) and center, and the
 * {@code tfc:volcano} center of every chunk in a 400 x 400 chunk square ({@code CenterOrDistanceToPlacement}).
 * No server was started.
 */
class VolcanoGoldenTest {
    static final List<String> GOLDEN = List.of(
        "CONE_HEIGHT seed=1 present=41904 h=-7234779237009667056 samples=[-2048,-1672=4635017196446922812, -2048,-1632=4635663433846908744, -2048,-1592=4634774540284336634, -2048,-1472=4635239487917457932, -2048,-1432=4637115916839767269, -2048,-992=4634766966517661696]",
        "CONE_CENTER seed=1 centers=21041 h=-4987061625826675951 samples=[-4096,-3712->-4066,-3684, -4096,-3456->-4048,-3446, -4096,-2608->-4045,-2537, -4096,-2592->-4075,-2522]",
        "CONE_PLACE seed=1 placed=485 h=4747793480263461165 samples=[-200,-59->-3198,-931, -199,-124->-3181,-1973, -199,26->-3181,417, -199,180->-3173,2887]",
        "CONE_HEIGHT seed=42 present=42393 h=2582425367384135744 samples=[-2048,-2032=4635230037213950584, -2048,-1232=4634918694639571009, -2048,-1192=4635541926797882060, -2048,1768=4635917931976780684, -2048,1808=4635467839675665075, -2048,1848=4634766966517661696]",
        "CONE_CENTER seed=42 centers=87739 h=-8623846701698091207 samples=[-4096,-4032->-4077,-3965, -4096,-4000->-4077,-3965, -4096,-3968->-4077,-3965, -4096,-3936->-4072,-3918]",
        "CONE_PLACE seed=42 placed=557 h=8516188339257164097 samples=[-200,-137->-3191,-2184, -200,-134->-3187,-2144, -200,116->-3185,1857, -199,181->-3176,2905]",
        "CONE_HEIGHT seed=-9876543210 present=44443 h=-3957335106720212761 samples=[-2048,-1112=4634757683107136531, -2048,-1072=4635079319716894772, -2048,-1032=4634766352782566147, -2048,-992=4634766966517661696, -2048,-952=4634766966517661696, -2048,168=4634762524890692171]",
        "CONE_CENTER seed=-9876543210 centers=0 h=-8221218415215224825 samples=[]",
        "CONE_PLACE seed=-9876543210 placed=0 h=17 samples=[]",
        "SPRING HOT_SPRING 3,-64,5 seed=7 ret=true n=2457 h=1961153388632138162 kinds={air=8, hardened=2044, rgravel=76, spring_water=329}",
        "SPRING HOT_SPRING 3,-64,5 seed=1234567 ret=true n=1938 h=465083958006292602 kinds={air=8, hardened=1588, rgravel=24, spring_water=318}",
        "SPRING HOT_SPRING 3,-64,5 seed=-42 ret=true n=2542 h=-3302022880379220750 kinds={air=5, hardened=2064, rgravel=37, spring_water=436}",
        "SPRING HOT_SPRING 3,-64,5 seed=99 ret=true n=1767 h=-8456473479056295541 kinds={air=6, hardened=1499, rgravel=58, spring_water=204}",
        "SPRING HOT_SPRING -70,-64,-133 seed=7 ret=true n=2304 h=-5183439426677565872 kinds={air=9, hardened=1919, rgravel=85, spring_water=291}",
        "SPRING HOT_SPRING -70,-64,-133 seed=1234567 ret=true n=2079 h=8574905562320079917 kinds={air=7, hardened=1697, rgravel=26, spring_water=349}",
        "SPRING HOT_SPRING -70,-64,-133 seed=-42 ret=true n=1476 h=-7187142907583302813 kinds={air=2, hardened=1213, rgravel=66, spring_water=195}",
        "SPRING HOT_SPRING -70,-64,-133 seed=99 ret=true n=1657 h=1457572443177345463 kinds={air=6, hardened=1401, rgravel=64, spring_water=186}",
        "SPRING HOT_SPRING 200,-64,-41 seed=7 ret=true n=1740 h=7276083933239346486 kinds={air=6, hardened=1490, rgravel=88, spring_water=156}",
        "SPRING HOT_SPRING 200,-64,-41 seed=1234567 ret=true n=1122 h=2903235218446618939 kinds={air=6, hardened=965, rgravel=40, spring_water=111}",
        "SPRING HOT_SPRING 200,-64,-41 seed=-42 ret=true n=2053 h=-2210867894415314324 kinds={air=5, hardened=1693, rgravel=36, spring_water=319}",
        "SPRING HOT_SPRING 200,-64,-41 seed=99 ret=true n=1475 h=-4095990498995318137 kinds={air=4, hardened=1265, rgravel=67, spring_water=139}",
        "SPRING EMPTY 3,-64,5 seed=7 ret=true n=2457 h=-1841457488896894179 kinds={air=337, hardened=2044, rgravel=76}",
        "SPRING EMPTY 3,-64,5 seed=1234567 ret=true n=1615 h=3529850199503114513 kinds={air=243, hardened=1347, rgravel=25}",
        "SPRING EMPTY 3,-64,5 seed=-42 ret=true n=1886 h=491815365153063480 kinds={air=292, hardened=1551, rgravel=43}",
        "SPRING EMPTY 3,-64,5 seed=99 ret=true n=1767 h=1450191771262649837 kinds={air=210, hardened=1499, rgravel=58}",
        "SPRING EMPTY -70,-64,-133 seed=7 ret=true n=2304 h=-23638600135211187 kinds={air=300, hardened=1919, rgravel=85}",
        "SPRING EMPTY -70,-64,-133 seed=1234567 ret=true n=1561 h=-719976767571166601 kinds={air=229, hardened=1303, rgravel=29}",
        "SPRING EMPTY -70,-64,-133 seed=-42 ret=true n=1476 h=3529385118547281472 kinds={air=197, hardened=1213, rgravel=66}",
        "SPRING EMPTY -70,-64,-133 seed=99 ret=true n=1657 h=8982539832219484111 kinds={air=192, hardened=1401, rgravel=64}",
        "SPRING EMPTY 200,-64,-41 seed=7 ret=true n=1740 h=-8676718661608284724 kinds={air=162, hardened=1490, rgravel=88}",
        "SPRING EMPTY 200,-64,-41 seed=1234567 ret=true n=1122 h=-2693359728087913758 kinds={air=117, hardened=965, rgravel=40}",
        "SPRING EMPTY 200,-64,-41 seed=-42 ret=true n=1426 h=509481162169165690 kinds={air=182, hardened=1201, rgravel=43}",
        "SPRING EMPTY 200,-64,-41 seed=99 ret=true n=1475 h=4404611169607946088 kinds={air=143, hardened=1265, rgravel=67}",
        "SPRING SAPPHIRE 3,-64,5 seed=7 ret=true n=1725 h=-1155702581213512119 kinds={air=8, hardened=1474, ore=8, rgravel=84, spring_water=151}",
        "SPRING SAPPHIRE 3,-64,5 seed=1234567 ret=true n=2056 h=4984810270712862476 kinds={air=8, hardened=1624, ore=64, rgravel=24, spring_water=336}",
        "SPRING SAPPHIRE 3,-64,5 seed=-42 ret=true n=1253 h=-3065481046916881840 kinds={air=5, hardened=1053, ore=21, rgravel=48, spring_water=126}",
        "SPRING SAPPHIRE 3,-64,5 seed=99 ret=true n=1739 h=-4096995306446130747 kinds={air=6, hardened=1460, ore=16, rgravel=58, spring_water=199}",
        "SPRING SAPPHIRE -70,-64,-133 seed=7 ret=true n=1519 h=-8787184753194235532 kinds={air=9, hardened=1314, rgravel=92, spring_water=104}",
        "SPRING SAPPHIRE -70,-64,-133 seed=1234567 ret=true n=839 h=4747295022506319590 kinds={air=7, hardened=750, ore=2, rgravel=38, spring_water=42}",
        "SPRING SAPPHIRE -70,-64,-133 seed=-42 ret=true n=992 h=-7737964946268117875 kinds={air=2, hardened=842, ore=2, rgravel=70, spring_water=76}",
        "SPRING SAPPHIRE -70,-64,-133 seed=99 ret=true n=1681 h=4139674382246372645 kinds={air=6, hardened=1411, ore=11, rgravel=64, spring_water=189}",
        "SPRING SAPPHIRE 200,-64,-41 seed=7 ret=true n=1793 h=-4753928503987024891 kinds={air=6, hardened=1494, ore=49, rgravel=88, spring_water=156}",
        "SPRING SAPPHIRE 200,-64,-41 seed=1234567 ret=true n=1163 h=-6899672981041943479 kinds={air=6, hardened=943, ore=65, rgravel=40, spring_water=109}",
        "SPRING SAPPHIRE 200,-64,-41 seed=-42 ret=true n=2173 h=-4972937743288889921 kinds={air=5, hardened=1651, ore=152, rgravel=38, spring_water=327}",
        "SPRING SAPPHIRE 200,-64,-41 seed=99 ret=true n=1475 h=4370645368036790344 kinds={air=4, hardened=1260, ore=1, rgravel=68, spring_water=142}",
        "SPRING EMERALD 3,-64,5 seed=7 ret=true n=1725 h=6878845240085155141 kinds={air=8, hardened=1474, ore=8, rgravel=84, spring_water=151}",
        "SPRING EMERALD 3,-64,5 seed=1234567 ret=true n=2056 h=2637072068739987090 kinds={air=8, hardened=1624, ore=64, rgravel=24, spring_water=336}",
        "SPRING EMERALD 3,-64,5 seed=-42 ret=true n=1253 h=-8269096513416790500 kinds={air=5, hardened=1053, ore=21, rgravel=48, spring_water=126}",
        "SPRING EMERALD 3,-64,5 seed=99 ret=true n=1739 h=4068766723234265731 kinds={air=6, hardened=1460, ore=16, rgravel=58, spring_water=199}",
        "SPRING EMERALD -70,-64,-133 seed=7 ret=true n=1519 h=-8787184753194235532 kinds={air=9, hardened=1314, rgravel=92, spring_water=104}",
        "SPRING EMERALD -70,-64,-133 seed=1234567 ret=true n=839 h=-9031076866402760042 kinds={air=7, hardened=750, ore=2, rgravel=38, spring_water=42}",
        "SPRING EMERALD -70,-64,-133 seed=-42 ret=true n=992 h=-6164332757609978253 kinds={air=2, hardened=842, ore=2, rgravel=70, spring_water=76}",
        "SPRING EMERALD -70,-64,-133 seed=99 ret=true n=1681 h=-8327043262947769861 kinds={air=6, hardened=1411, ore=11, rgravel=64, spring_water=189}",
        "SPRING EMERALD 200,-64,-41 seed=7 ret=true n=1793 h=-3768318068188004981 kinds={air=6, hardened=1494, ore=49, rgravel=88, spring_water=156}",
        "SPRING EMERALD 200,-64,-41 seed=1234567 ret=true n=1163 h=377489613606341901 kinds={air=6, hardened=943, ore=65, rgravel=40, spring_water=109}",
        "SPRING EMERALD 200,-64,-41 seed=-42 ret=true n=2173 h=734046313529166731 kinds={air=5, hardened=1651, ore=152, rgravel=38, spring_water=327}",
        "SPRING EMERALD 200,-64,-41 seed=99 ret=true n=1475 h=3263839704513997958 kinds={air=4, hardened=1260, ore=1, rgravel=68, spring_water=142}",
        "SPRING SAPPHIRE_EMPTY 3,-64,5 seed=7 ret=true n=2637 h=4890005010430145331 kinds={air=374, hardened=2172, ore=13, rgravel=78}",
        "SPRING SAPPHIRE_EMPTY 3,-64,5 seed=1234567 ret=true n=1216 h=-6924470634639205031 kinds={air=154, hardened=1034, rgravel=28}",
        "SPRING SAPPHIRE_EMPTY 3,-64,5 seed=-42 ret=true n=1471 h=1176789114552837151 kinds={air=188, hardened=1224, ore=12, rgravel=47}",
        "SPRING SAPPHIRE_EMPTY 3,-64,5 seed=99 ret=true n=1492 h=159291358554013287 kinds={air=153, hardened=1281, rgravel=58}",
        "SPRING SAPPHIRE_EMPTY -70,-64,-133 seed=7 ret=true n=1511 h=-3928650708903786007 kinds={air=109, hardened=1308, rgravel=94}",
        "SPRING SAPPHIRE_EMPTY -70,-64,-133 seed=1234567 ret=true n=1595 h=2899746055678754189 kinds={air=234, hardened=1313, ore=19, rgravel=29}",
        "SPRING SAPPHIRE_EMPTY -70,-64,-133 seed=-42 ret=true n=990 h=4313900408551178808 kinds={air=77, hardened=843, rgravel=70}",
        "SPRING SAPPHIRE_EMPTY -70,-64,-133 seed=99 ret=true n=1665 h=1152295257927512454 kinds={air=191, hardened=1405, ore=5, rgravel=64}",
        "SPRING SAPPHIRE_EMPTY 200,-64,-41 seed=7 ret=true n=1764 h=-8597231672804497761 kinds={air=163, hardened=1495, ore=18, rgravel=88}",
        "SPRING SAPPHIRE_EMPTY 200,-64,-41 seed=1234567 ret=true n=1167 h=-3599146053715678459 kinds={air=121, hardened=981, ore=25, rgravel=40}",
        "SPRING SAPPHIRE_EMPTY 200,-64,-41 seed=-42 ret=true n=2259 h=8492624259672517446 kinds={air=365, hardened=1803, ore=57, rgravel=34}",
        "SPRING SAPPHIRE_EMPTY 200,-64,-41 seed=99 ret=true n=1492 h=-581582751100569384 kinds={air=146, hardened=1278, rgravel=68}",
        "SPRING EMERALD_EMPTY 3,-64,5 seed=7 ret=true n=2637 h=4960176002529868313 kinds={air=374, hardened=2172, ore=13, rgravel=78}",
        "SPRING EMERALD_EMPTY 3,-64,5 seed=1234567 ret=true n=1216 h=-6924470634639205031 kinds={air=154, hardened=1034, rgravel=28}",
        "SPRING EMERALD_EMPTY 3,-64,5 seed=-42 ret=true n=1471 h=-9181102254961675663 kinds={air=188, hardened=1224, ore=12, rgravel=47}",
        "SPRING EMERALD_EMPTY 3,-64,5 seed=99 ret=true n=1492 h=159291358554013287 kinds={air=153, hardened=1281, rgravel=58}",
        "SPRING EMERALD_EMPTY -70,-64,-133 seed=7 ret=true n=1511 h=-3928650708903786007 kinds={air=109, hardened=1308, rgravel=94}",
        "SPRING EMERALD_EMPTY -70,-64,-133 seed=1234567 ret=true n=1595 h=5768744078548393517 kinds={air=234, hardened=1313, ore=19, rgravel=29}",
        "SPRING EMERALD_EMPTY -70,-64,-133 seed=-42 ret=true n=990 h=4313900408551178808 kinds={air=77, hardened=843, rgravel=70}",
        "SPRING EMERALD_EMPTY -70,-64,-133 seed=99 ret=true n=1665 h=5634906065833830052 kinds={air=191, hardened=1405, ore=5, rgravel=64}",
        "SPRING EMERALD_EMPTY 200,-64,-41 seed=7 ret=true n=1764 h=4594062449767292885 kinds={air=163, hardened=1495, ore=18, rgravel=88}",
        "SPRING EMERALD_EMPTY 200,-64,-41 seed=1234567 ret=true n=1167 h=-8236616462179869381 kinds={air=121, hardened=981, ore=25, rgravel=40}",
        "SPRING EMERALD_EMPTY 200,-64,-41 seed=-42 ret=true n=2259 h=-3735809679479880300 kinds={air=365, hardened=1803, ore=57, rgravel=34}",
        "SPRING EMERALD_EMPTY 200,-64,-41 seed=99 ret=true n=1492 h=-581582751100569384 kinds={air=146, hardened=1278, rgravel=68}",
        "SPRING LAVA 3,-64,5 seed=7 ret=true n=2380 h=4937665382942500507 kinds={air=7, hardened=1939, lava=386, magma=15, rgravel=33}",
        "SPRING LAVA 3,-64,5 seed=1234567 ret=true n=621 h=4449027821818115443 kinds={air=1, hardened=551, lava=43, magma=6, rgravel=20}",
        "SPRING LAVA 3,-64,5 seed=-42 ret=true n=817 h=-2059085759589663046 kinds={air=2, hardened=706, lava=68, magma=11, rgravel=30}",
        "SPRING LAVA 3,-64,5 seed=99 ret=true n=1869 h=5422825807665139945 kinds={air=3, hardened=1524, lava=303, magma=14, rgravel=25}",
        "SPRING LAVA -70,-64,-133 seed=7 ret=true n=774 h=-7188747497997015465 kinds={air=4, hardened=706, lava=5, magma=12, rgravel=47}",
        "SPRING LAVA -70,-64,-133 seed=1234567 ret=true n=600 h=2498733927804349853 kinds={air=3, hardened=538, lava=43, magma=2, rgravel=14}",
        "SPRING LAVA -70,-64,-133 seed=-42 ret=true n=703 h=2970785134398018210 kinds={hardened=619, lava=45, magma=9, rgravel=30}",
        "SPRING LAVA -70,-64,-133 seed=99 ret=true n=2136 h=3887037597525590722 kinds={air=1, hardened=1732, lava=372, magma=12, rgravel=19}",
        "SPRING LAVA 200,-64,-41 seed=7 ret=true n=2269 h=8021205398061152461 kinds={air=11, hardened=1850, lava=360, magma=15, rgravel=33}",
        "SPRING LAVA 200,-64,-41 seed=1234567 ret=true n=586 h=770112519954087870 kinds={air=4, hardened=515, lava=43, magma=3, rgravel=21}",
        "SPRING LAVA 200,-64,-41 seed=-42 ret=true n=821 h=8195175813090149135 kinds={air=1, hardened=713, lava=68, magma=9, rgravel=30}",
        "SPRING LAVA 200,-64,-41 seed=99 ret=true n=1804 h=-833413403519574495 kinds={air=1, hardened=1473, lava=294, magma=12, rgravel=24}",
        "FISSURE null 24,65,24 seed=5 n=416 h=3873916175980250901 kinds={lava=94, raw=322}",
        "FISSURE null 24,65,24 seed=-77 n=797 h=-4568618680590050848 kinds={lava=176, raw=621}",
        "FISSURE null 24,65,24 seed=31337 n=263 h=5092623483649726242 kinds={lava=57, raw=206}",
        "FISSURE null -115,76,70 seed=5 n=416 h=-2221574724868239830 kinds={lava=94, raw=322}",
        "FISSURE null -115,76,70 seed=-77 n=332 h=5971676474854087258 kinds={lava=70, raw=262}",
        "FISSURE null -115,76,70 seed=31337 n=33 h=6308174442888075945 kinds={lava=7, raw=26}",
        "FISSURE topaz 24,65,24 seed=5 n=756 h=9118733438504461015 kinds={lava=138, ore=157, raw=461}",
        "FISSURE topaz 24,65,24 seed=-77 n=1027 h=4481949702673150121 kinds={lava=196, ore=202, raw=629}",
        "FISSURE topaz 24,65,24 seed=31337 n=294 h=-3126895713779117415 kinds={lava=54, ore=61, raw=179}",
        "FISSURE topaz -115,76,70 seed=5 n=17 h=3987587246635856633 kinds={lava=2, ore=4, raw=11}",
        "FISSURE topaz -115,76,70 seed=-77 n=41 h=-46599591472466377 kinds={lava=8, ore=6, raw=27}",
        "FISSURE topaz -115,76,70 seed=31337 n=286 h=2056501276288773036 kinds={lava=54, ore=53, raw=179}",
        "FISSURE diamond 24,65,24 seed=5 n=756 h=-1166103828194687091 kinds={lava=138, ore=157, raw=461}",
        "FISSURE diamond 24,65,24 seed=-77 n=1027 h=-8483295155846982607 kinds={lava=196, ore=202, raw=629}",
        "FISSURE diamond 24,65,24 seed=31337 n=294 h=-5224802551302318709 kinds={lava=54, ore=61, raw=179}",
        "FISSURE diamond -115,76,70 seed=5 n=17 h=-2458648457153899475 kinds={lava=2, ore=4, raw=11}",
        "FISSURE diamond -115,76,70 seed=-77 n=41 h=5553195224682299143 kinds={lava=8, ore=6, raw=27}",
        "FISSURE diamond -115,76,70 seed=31337 n=286 h=2635847938606021614 kinds={lava=54, ore=53, raw=179}",
        "CALDERA 24,65,24 ret=true n=727 h=-2053992724839025389 kinds={lava=727}",
        "CALDERA -24,65,-24 ret=true n=727 h=-6878738934011113017 kinds={lava=727}",
        "CALDERA 72,65,-24 ret=true n=727 h=7077286936116411754 kinds={lava=727}",
        "CALDERA 26,65,22 ret=true n=727 h=-2053992724839025389 kinds={lava=727}",
        "CALDERA 10,67,10 ret=false n=0 h=17 kinds={}",
        "CALDERA -100,67,37 ret=false n=0 h=17 kinds={}"
    );

    private static final VolcanoTestView VIEW = new VolcanoTestView();
    private static final IntBinaryOperator BIOME_AT = VolcanoTestView::biomeAt;

    @Test
    void everyOracleLineIsReproduced() {
        final List<String> actual = generate();
        assertThat(actual).hasSameSizeAs(GOLDEN);
        for (int i = 0; i < GOLDEN.size(); i++) {
            assertThat(actual.get(i)).as("line %d", i).isEqualTo(GOLDEN.get(i));
        }
    }

    /** The oracle's {@code main}, line for line, on the ported classes. */
    static List<String> generate() {
        final List<String> out = new ArrayList<>();
        for (long seed : new long[] {1L, 42L, -9876543210L}) {
            final CinderCones s = new CinderCones(seed);
            long h = 17;
            int present = 0;
            final List<String> samples = new ArrayList<>();
            for (int x = -2048; x < 2048; x += 8) {
                for (int z = -2048; z < 2048; z += 8) {
                    final double in = 64 + Math.floorMod(x * 7 + z * 13, 20);
                    final double v = s.sampleHeight(in, x, z, BIOME_AT);
                    h = h * 1000003L + Double.doubleToLongBits(v);
                    if (v != Integer.MIN_VALUE) {
                        present++;
                        if (samples.size() < 6 && Math.floorMod(x + z, 40) == 0) {
                            samples.add(x + "," + z + "=" + Double.doubleToLongBits(v));
                        }
                    }
                }
            }
            out.add("CONE_HEIGHT seed=" + seed + " present=" + present + " h=" + h + " samples=" + samples);
            long he = 17;
            int centers = 0;
            final List<String> cs = new ArrayList<>();
            for (int x = -4096; x < 4096; x += 16) {
                for (int z = -4096; z < 4096; z += 16) {
                    final float f = (Math.floorMod(x + z, 32) == 0) ? 1f / 2f : 1f / 6f;
                    he = he * 1000003L + Float.floatToIntBits(s.easing(x, z, f));
                    final long c = s.center(x, z, f);
                    if (c != Long.MIN_VALUE) {
                        centers++;
                        he = he * 1000003L + CinderCones.unpackX(c) * 31L + CinderCones.unpackZ(c);
                        if (cs.size() < 4) {
                            cs.add(x + "," + z + "->" + CinderCones.unpackX(c) + "," + CinderCones.unpackZ(c));
                        }
                    }
                }
            }
            out.add("CONE_CENTER seed=" + seed + " centers=" + centers + " h=" + he + " samples=" + cs);
            final Volcanoes volcanoes = new Volcanoes(seed);
            long hp = 17;
            int placed = 0;
            final List<String> ps = new ArrayList<>();
            for (int cx = -200; cx < 200; cx++) {
                for (int cz = -200; cz < 200; cz++) {
                    final long c = volcanoes.volcanoCenter(cx, cz, VIEW);
                    if (c != Long.MIN_VALUE) {
                        placed++;
                        hp = hp * 1000003L + CinderCones.unpackX(c) * 31L + CinderCones.unpackZ(c);
                        if (ps.size() < 4) {
                            ps.add(cx + "," + cz + "->" + CinderCones.unpackX(c) + "," + CinderCones.unpackZ(c));
                        }
                    }
                }
            }
            out.add("CONE_PLACE seed=" + seed + " placed=" + placed + " h=" + hp + " samples=" + ps);
        }
        final String[] names = {"HOT_SPRING", "EMPTY", "SAPPHIRE", "EMERALD", "SAPPHIRE_EMPTY", "EMERALD_EMPTY", "LAVA"};
        final HotSpring.Config[] configs = {HotSpring.Config.HOT_SPRING, HotSpring.Config.EMPTY, HotSpring.Config.SAPPHIRE,
            HotSpring.Config.EMERALD, HotSpring.Config.SAPPHIRE_EMPTY, HotSpring.Config.EMERALD_EMPTY, HotSpring.Config.LAVA};
        final int[][] origins = {{3, -64, 5}, {-70, -64, -133}, {200, -64, -41}};
        for (int c = 0; c < configs.length; c++) {
            for (int[] o : origins) {
                for (long seed : new long[] {7L, 1234567L, -42L, 99L}) {
                    final VolcanoLevel level = new VolcanoLevel(VIEW);
                    final boolean r = HotSpring.place(level, new XoroshiroRandomSource(seed), o[0], o[1], o[2], configs[c]);
                    out.add("SPRING " + names[c] + " " + o[0] + "," + o[1] + "," + o[2] + " seed=" + seed + " ret=" + r + " " + result(level.blocks()));
                }
            }
        }
        final Gem[] gems = {null, Gem.TOPAZ, Gem.DIAMOND};
        final int[][] fo = {{24, 0, 24}, {-115, 0, 70}};
        for (Gem gem : gems) {
            for (int[] o : fo) {
                for (long seed : new long[] {5L, -77L, 31337L}) {
                    final int y = VIEW.worldSurface(o[0], o[2]);
                    final List<VolcanoBlock> blocks = Volcanoes.runVolcanoFissure(VIEW,
                        gem == null ? null : new Fissure.Decoration(gem, 3, 3, 6), new XoroshiroRandomSource(seed), o[0], y, o[2]);
                    out.add("FISSURE " + (gem == null ? "null" : gem.id()) + " " + o[0] + "," + y + "," + o[2] + " seed=" + seed + " " + result(blocks));
                }
            }
        }
        final int[][] co = {{24, 24}, {-24, -24}, {72, -24}, {26, 22}, {10, 10}, {-100, 37}};
        for (int[] o : co) {
            final VolcanoLevel level = new VolcanoLevel(VIEW);
            final int y = VIEW.worldSurface(o[0], o[1]);
            final boolean r = Caldera.place(level, o[0], y, o[1]);
            out.add("CALDERA " + o[0] + "," + y + "," + o[1] + " ret=" + r + " " + result(level.blocks()));
        }
        return out;
    }

    /** The oracle's {@code Level.result}: the placed blocks that differ from the terrain, sorted, hashed and counted. */
    static String result(List<VolcanoBlock> blocks) {
        final List<VolcanoBlock> changed = new ArrayList<>();
        for (VolcanoBlock b : blocks) {
            if (!VolcanoTestView.name(b).equals(VolcanoTestView.terrain(b.x(), b.y(), b.z()))) {
                changed.add(b);
            }
        }
        changed.sort(Comparator.comparingInt(VolcanoBlock::x).thenComparingInt(VolcanoBlock::y).thenComparingInt(VolcanoBlock::z));
        long h = 17;
        final Map<String, Integer> kinds = new TreeMap<>();
        for (VolcanoBlock b : changed) {
            final String n = VolcanoTestView.name(b);
            h = h * 1000003L + (b.x() + "," + b.y() + "," + b.z() + "," + n).hashCode();
            kinds.merge(n.contains("/") ? n.substring(0, n.indexOf('/')) : n, 1, Integer::sum);
        }
        return "n=" + changed.size() + " h=" + h + " kinds=" + kinds;
    }
}
