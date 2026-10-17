/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/SoilSurfaceState.java @ c3b4d9536 (golden values), with SurfaceStates.java,
 * SurfaceState.java, builder/NormalSurfaceBuilder.java, builder/SimpleSurfaceBuilder.java, builder/ShoreAndOceanSurfaceBuilder.java
 * (the shore branch), SurfaceBuilderContext.java (calculateAltitudeSlopeSurfaceDepth) and SurfaceManager.java
 */
package dev.tfp.core.worldgen.surface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.noise.Noise2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Golden surface kinds. Each row is one column: the family (the TFC builder of the biome), the level seed, the position, the
 * rock top, the slope, the sea-level temperature, the rainfall, the base groundwater, the rain variance, whether the sea-level
 * rock is mafic, the shore tide noise, then the kind of each height from the top down to {@code top - 19}, one char each: R rock
 * (the surface keeps the rock), G grass, D dirt, C coarse dirt, M mud, V gravel, S sand, T sandstone, N snow, I packed ice, and
 * '.' where the surface leaves the block alone.
 * <p>
 * The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit). An oracle harness compiled TFC's
 * {@code SoilSurfaceState}, {@code SurfaceStates}, {@code SurfaceState}, {@code NormalSurfaceBuilder} and {@code SimpleSurfaceBuilder}
 * unchanged, with Minecraft stubs whose {@code Mth} keeps Minecraft's overloads (clampedMap takes doubles only). Its
 * {@code SurfaceBuilderContext} keeps TFC's {@code calculateAltitudeSlopeSurfaceDepth} and its getters, and runs the column pass
 * of {@code SurfaceManager}: the heightmaps are the rock top and the water top. The shore branch of
 * {@code ShoreAndOceanSurfaceBuilder} is TFC's, with its tide noise from TFC's {@code BiomeNoise.shoreTideLevelNoise} (the
 * frozen ocean extension and the lava flows are left out, see the ledger). The rows are 300 columns from a fixed random draw
 * (30 per family, two thirds of them starting above sea level), and the 16 noise lines are the patch noise, the beach noise and
 * the shore tide at fixed points. The doubles are {@link Double#toString}, which round-trips exactly.
 */
class SurfaceGoldenTest {
    private static final Pattern ROW = Pattern.compile("fam=(\\S+) seed=(-?\\d+) x=(-?\\d+) z=(-?\\d+) top=(-?\\d+) slope=(\\S+)"
        + " temp=(\\S+) rain=(\\S+) gw=(\\S+) var=(\\S+) mafic=([01]) tide=(\\S+) kinds=([RGDCMVSTNI.]+)");
    private static final Pattern NOISE = Pattern.compile("noise seed=(-?\\d+) x=(-?\\d+) z=(-?\\d+) patch=(\\S+) beach=(\\S+) tide=(\\S+)");

    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;

    private static final String[] GOLDEN = {
        "fam=NORMAL seed=7 x=-5268 z=-5776 top=74 slope=10.0 temp=-3.359188 rain=35.0 gw=10.0 var=-0.21938634 mafic=1 tide=63.6253245472908 kinds=V...................",
        "fam=NORMAL seed=42 x=8714 z=-8381 top=77 slope=15.0 temp=6.121975 rain=89.796486 gw=79.29577 var=-0.07735205 mafic=1 tide=65.84938395023346 kinds=V...................",
        "fam=NORMAL seed=42 x=4937 z=-141 top=49 slope=14.0 temp=-15.5 rain=300.0 gw=130.1756 var=0.4229462 mafic=1 tide=63.33312526345253 kinds=V...................",
        "fam=NORMAL seed=7 x=-6774 z=-6629 top=66 slope=0.5 temp=13.0 rain=145.0 gw=175.92445 var=0.75650394 mafic=1 tide=62.896032858639956 kinds=GDDDDVVVV...........",
        "fam=NORMAL seed=42 x=-8747 z=5700 top=78 slope=0.5 temp=-12.0 rain=246.07059 gw=134.19017 var=-0.7934109 mafic=0 tide=63.775214314460754 kinds=CCCDVVV.............",
        "fam=NORMAL seed=42 x=-9538 z=-4757 top=78 slope=22.0 temp=-28.967073 rain=300.0 gw=98.72293 var=0.5046401 mafic=0 tide=62.7665893137455 kinds=R...................",
        "fam=NORMAL seed=7 x=2922 z=162 top=77 slope=10.0 temp=9.107044 rain=460.0 gw=29.609894 var=-0.077860236 mafic=0 tide=65.3074466586113 kinds=G...................",
        "fam=NORMAL seed=7 x=7719 z=-1103 top=64 slope=6.0 temp=-0.24495697 rain=35.0 gw=34.05341 var=-0.23861217 mafic=0 tide=63.53360998630524 kinds=CVVVVV..............",
        "fam=NORMAL seed=7 x=9 z=-9118 top=66 slope=22.0 temp=19.526363 rain=35.0 gw=135.42303 var=-0.06670344 mafic=0 tide=63.34088081121445 kinds=V...................",
        "fam=NORMAL seed=42 x=-4526 z=1167 top=71 slope=6.0 temp=24.955353 rain=268.16757 gw=191.45796 var=0.45167804 mafic=1 tide=63.16747131943703 kinds=GDDVV...............",
        "fam=NORMAL seed=42 x=8466 z=4977 top=77 slope=6.0 temp=25.0 rain=58.58132 gw=177.8096 var=0.9649569 mafic=1 tide=62.198030054569244 kinds=GDDVV...............",
        "fam=NORMAL seed=42 x=-9494 z=5473 top=45 slope=0.5 temp=16.0 rain=460.0 gw=0.0 var=0.5933546 mafic=1 tide=64.17272505164146 kinds=VVVVVVVVVVV.........",
        "fam=NORMAL seed=42 x=7203 z=-4066 top=78 slope=0.5 temp=-6.3440857 rain=363.34512 gw=33.032764 var=-0.618024 mafic=1 tide=62.274234652519226 kinds=GDDDVVV.............",
        "fam=NORMAL seed=42 x=66 z=-3053 top=73 slope=0.0 temp=-30.0 rain=416.65237 gw=149.19316 var=-0.8574008 mafic=1 tide=63.36264696717262 kinds=NIIIIRRRR...........",
        "fam=NORMAL seed=42 x=1274 z=4320 top=68 slope=22.0 temp=10.807991 rain=466.8085 gw=150.0 var=-0.688349 mafic=1 tide=63.63675743341446 kinds=V...................",
        "fam=NORMAL seed=7 x=3686 z=8279 top=64 slope=2.0 temp=25.0 rain=111.881676 gw=22.634638 var=-0.13059151 mafic=0 tide=65.49305406212807 kinds=GDDDDVVVV...........",
        "fam=NORMAL seed=7 x=-1521 z=-2568 top=75 slope=22.0 temp=9.0 rain=453.48007 gw=104.885376 var=-0.33256328 mafic=1 tide=64.22595373541117 kinds=V...................",
        "fam=NORMAL seed=42 x=1994 z=-360 top=64 slope=6.0 temp=-26.723621 rain=277.54343 gw=10.0 var=-0.32334852 mafic=0 tide=63.39424782991409 kinds=NIIRRR..............",
        "fam=NORMAL seed=7 x=1297 z=2779 top=71 slope=15.0 temp=-8.757549 rain=174.7959 gw=169.1854 var=0.18289435 mafic=0 tide=62.73182235658169 kinds=V...................",
        "fam=NORMAL seed=42 x=-2028 z=-4810 top=70 slope=0.0 temp=-7.2835236 rain=109.10058 gw=91.46623 var=-0.26852274 mafic=0 tide=63.67859035730362 kinds=GDDDDDVVVVV.........",
        "fam=NORMAL seed=7 x=5512 z=-4105 top=50 slope=10.0 temp=-26.287611 rain=463.70102 gw=52.99933 var=-0.8549539 mafic=0 tide=63.304586589336395 kinds=VVVV................",
        "fam=NORMAL seed=42 x=7180 z=8822 top=77 slope=15.0 temp=-16.6 rain=430.7627 gw=111.22744 var=-0.23725891 mafic=0 tide=62.31711369752884 kinds=R...................",
        "fam=NORMAL seed=7 x=-9773 z=3609 top=56 slope=22.0 temp=-12.308521 rain=55.0 gw=10.0 var=-0.84943116 mafic=0 tide=64.68235069513321 kinds=V...................",
        "fam=NORMAL seed=7 x=9739 z=-4675 top=57 slope=6.0 temp=-10.318745 rain=484.02753 gw=10.0 var=-0.9725493 mafic=1 tide=63.88774245977402 kinds=VVVVVVVVV...........",
        "fam=NORMAL seed=7 x=7328 z=2064 top=65 slope=15.0 temp=-14.0 rain=247.25792 gw=85.350655 var=-0.734272 mafic=0 tide=63.305745501071215 kinds=V...................",
        "fam=NORMAL seed=7 x=2313 z=1889 top=71 slope=22.0 temp=-3.8989449 rain=479.5175 gw=69.85857 var=-0.008496404 mafic=1 tide=66.00887641310692 kinds=V...................",
        "fam=NORMAL seed=7 x=4401 z=1432 top=73 slope=14.0 temp=28.937912 rain=55.0 gw=83.3637 var=-0.8354639 mafic=0 tide=63.21925950050354 kinds=C...................",
        "fam=NORMAL seed=7 x=-5785 z=1859 top=65 slope=22.0 temp=4.0 rain=35.960915 gw=156.41257 var=-0.47252154 mafic=1 tide=64.92667829990387 kinds=V...................",
        "fam=NORMAL seed=42 x=-6857 z=-5666 top=59 slope=2.0 temp=0.9428787 rain=35.0 gw=16.80672 var=0.70594823 mafic=0 tide=63.505017042160034 kinds=VVVVVVVVVVV.........",
        "fam=NORMAL seed=42 x=3684 z=-1851 top=42 slope=2.0 temp=25.140858 rain=200.0 gw=30.0 var=-0.67905426 mafic=1 tide=63.528911143541336 kinds=VVVVVVVVVVV.........",
        "fam=ROCKY seed=7 x=-7921 z=1475 top=49 slope=0.5 temp=16.0 rain=157.41629 gw=0.0 var=-0.109622955 mafic=0 tide=63.093636214733124 kinds=VVVVVVVVVVV.........",
        "fam=ROCKY seed=42 x=-507 z=-4293 top=68 slope=14.0 temp=8.459854 rain=186.24765 gw=150.0 var=-0.2935313 mafic=0 tide=64.28956539928913 kinds=....................",
        "fam=ROCKY seed=7 x=-1215 z=6279 top=74 slope=0.5 temp=15.420189 rain=209.5294 gw=194.94414 var=-0.04744029 mafic=0 tide=63.80209744069725 kinds=GDDDVVV.............",
        "fam=ROCKY seed=42 x=-2715 z=873 top=75 slope=0.5 temp=28.81342 rain=81.33623 gw=30.0 var=0.92867434 mafic=1 tide=65.16194705665112 kinds=CCCCVVV.............",
        "fam=ROCKY seed=42 x=4319 z=4013 top=68 slope=15.0 temp=4.0 rain=416.88202 gw=86.261215 var=-0.41027212 mafic=1 tide=63.42827343940735 kinds=....................",
        "fam=ROCKY seed=42 x=3716 z=-206 top=73 slope=0.0 temp=1.3986359 rain=467.1077 gw=92.45828 var=-0.9602504 mafic=0 tide=62.38779479265213 kinds=GDDDDVVVV...........",
        "fam=ROCKY seed=7 x=7875 z=8958 top=74 slope=0.5 temp=-27.492157 rain=200.0 gw=150.45193 var=0.92276704 mafic=1 tide=64.27533656358719 kinds=NIIIRRR.............",
        "fam=ROCKY seed=7 x=2035 z=2768 top=42 slope=6.0 temp=17.636944 rain=392.5036 gw=0.0 var=-0.4065069 mafic=0 tide=63.851299211382866 kinds=VVVVVVVVV...........",
        "fam=ROCKY seed=7 x=1193 z=-7820 top=66 slope=15.0 temp=-29.63439 rain=141.69728 gw=0.0 var=-0.9810183 mafic=1 tide=63.50727035244927 kinds=....................",
        "fam=ROCKY seed=42 x=-8988 z=-3923 top=77 slope=6.0 temp=25.935432 rain=382.91098 gw=0.0 var=-0.13763022 mafic=1 tide=62.80258096754551 kinds=GDVV................",
        "fam=ROCKY seed=42 x=-2956 z=-2756 top=64 slope=15.0 temp=6.3482246 rain=35.0 gw=59.516026 var=-0.21263659 mafic=1 tide=63.71147548407316 kinds=....................",
        "fam=ROCKY seed=7 x=2295 z=-7116 top=65 slope=10.0 temp=28.0 rain=55.0 gw=190.48892 var=0.4664116 mafic=1 tide=63.38729366660118 kinds=G...................",
        "fam=ROCKY seed=7 x=7942 z=319 top=71 slope=2.0 temp=-21.499977 rain=110.65525 gw=102.40648 var=-0.7462338 mafic=0 tide=62.7895085811615 kinds=NIIIRRR.............",
        "fam=ROCKY seed=7 x=-215 z=-2164 top=67 slope=0.5 temp=8.628845 rain=371.6744 gw=195.67693 var=-0.3664708 mafic=1 tide=63.099392004311085 kinds=GDDDDVVVV...........",
        "fam=ROCKY seed=42 x=5439 z=-5978 top=72 slope=14.0 temp=1.6512032 rain=200.0 gw=187.02666 var=0.9407811 mafic=0 tide=62.78450357913971 kinds=....................",
        "fam=ROCKY seed=42 x=-3201 z=8715 top=66 slope=14.0 temp=-10.756477 rain=136.5923 gw=25.0 var=0.4758824 mafic=0 tide=63.345191687345505 kinds=....................",
        "fam=ROCKY seed=42 x=-8065 z=7745 top=74 slope=6.0 temp=-28.301006 rain=238.1266 gw=55.250538 var=0.96574664 mafic=1 tide=63.30304238200188 kinds=NIRR................",
        "fam=ROCKY seed=42 x=-5641 z=-2454 top=76 slope=15.0 temp=-6.800995 rain=251.28546 gw=198.20082 var=-0.073853016 mafic=0 tide=64.13141426444054 kinds=....................",
        "fam=ROCKY seed=7 x=-8129 z=-1813 top=70 slope=0.5 temp=-23.11478 rain=470.27667 gw=54.645695 var=0.024390578 mafic=0 tide=63.15093205869198 kinds=NIIIIRRRR...........",
        "fam=ROCKY seed=7 x=-1513 z=6211 top=69 slope=22.0 temp=25.0 rain=407.37128 gw=168.4381 var=-0.31566024 mafic=1 tide=62.54432889819145 kinds=....................",
        "fam=ROCKY seed=7 x=-2031 z=2228 top=65 slope=10.0 temp=20.0 rain=444.71948 gw=39.637543 var=-0.5150542 mafic=1 tide=65.17711606621742 kinds=G...................",
        "fam=ROCKY seed=7 x=598 z=8347 top=68 slope=0.5 temp=13.955772 rain=426.07132 gw=25.0 var=-0.37240875 mafic=1 tide=64.8445712774992 kinds=GDDDDVVVV...........",
        "fam=ROCKY seed=42 x=2912 z=7396 top=65 slope=0.0 temp=-23.650723 rain=313.07526 gw=146.71584 var=0.059869528 mafic=1 tide=62.66556540131569 kinds=NIIIIIRRRRR.........",
        "fam=ROCKY seed=7 x=1264 z=-1755 top=78 slope=10.0 temp=9.513618 rain=72.769585 gw=10.0 var=0.6728858 mafic=0 tide=62.83966560661793 kinds=C...................",
        "fam=ROCKY seed=7 x=6539 z=3231 top=72 slope=6.0 temp=12.685299 rain=51.594822 gw=74.71159 var=-0.025087833 mafic=1 tide=64.13250301778316 kinds=CDVV................",
        "fam=ROCKY seed=7 x=-7442 z=-2130 top=76 slope=15.0 temp=-29.25741 rain=420.0 gw=143.12982 var=0.6911069 mafic=1 tide=64.50456875562668 kinds=....................",
        "fam=ROCKY seed=7 x=-8893 z=-4334 top=69 slope=6.0 temp=-7.860218 rain=167.62877 gw=129.68935 var=-0.91264164 mafic=1 tide=63.54897737503052 kinds=GDVVV...............",
        "fam=ROCKY seed=42 x=6587 z=6422 top=67 slope=15.0 temp=4.463669 rain=50.65468 gw=147.00586 var=-0.521767 mafic=0 tide=62.604424834251404 kinds=....................",
        "fam=ROCKY seed=42 x=-5716 z=-686 top=74 slope=0.0 temp=-15.5 rain=93.56347 gw=150.0 var=0.22970891 mafic=0 tide=62.95660030096769 kinds=VVVVVVVVV...........",
        "fam=ROCKY seed=42 x=2347 z=7433 top=73 slope=0.0 temp=-8.761049 rain=341.2398 gw=19.489754 var=-0.6815022 mafic=1 tide=64.58554597198963 kinds=GDDDDVVVV...........",
        "fam=VOLCANIC_SOIL seed=42 x=-5960 z=2148 top=65 slope=0.5 temp=-26.978033 rain=113.07097 gw=17.199087 var=0.22850573 mafic=1 tide=63.98421531915665 kinds=NIIIIIIII...........",
        "fam=VOLCANIC_SOIL seed=42 x=-6227 z=-5198 top=73 slope=10.0 temp=4.1979675 rain=69.97246 gw=59.906208 var=-0.2592249 mafic=0 tide=62.26249372959137 kinds=C...................",
        "fam=VOLCANIC_SOIL seed=42 x=1736 z=-9307 top=65 slope=0.0 temp=14.280529 rain=59.860886 gw=10.0 var=0.9708352 mafic=0 tide=64.9672360420227 kinds=CVVVCVVCVCC.........",
        "fam=VOLCANIC_SOIL seed=7 x=-6184 z=4208 top=51 slope=10.0 temp=-24.570763 rain=102.01809 gw=186.19092 var=-0.31686914 mafic=0 tide=63.47577768564224 kinds=VVVV................",
        "fam=VOLCANIC_SOIL seed=7 x=1348 z=5999 top=61 slope=10.0 temp=-8.663002 rain=35.0 gw=63.08987 var=-0.19259965 mafic=1 tide=65.25585547089577 kinds=VVV.................",
        "fam=VOLCANIC_SOIL seed=42 x=8027 z=7728 top=50 slope=14.0 temp=25.0 rain=55.0 gw=0.0 var=0.54493034 mafic=0 tide=64.11726997792721 kinds=V...................",
        "fam=VOLCANIC_SOIL seed=42 x=8226 z=-3587 top=67 slope=0.5 temp=-8.0 rain=145.0 gw=87.78619 var=-0.9211645 mafic=1 tide=63.22460001707077 kinds=GDDDDDDDD...........",
        "fam=VOLCANIC_SOIL seed=42 x=2694 z=8030 top=73 slope=6.0 temp=-30.0 rain=296.38345 gw=127.79002 var=-0.7816489 mafic=0 tide=66.58195281028748 kinds=NIII................",
        "fam=VOLCANIC_SOIL seed=42 x=-4196 z=852 top=78 slope=10.0 temp=-8.678194 rain=429.11014 gw=8.673727 var=0.06277537 mafic=0 tide=63.77619385719299 kinds=G...................",
        "fam=VOLCANIC_SOIL seed=42 x=-2658 z=-2815 top=41 slope=6.0 temp=16.797821 rain=55.0 gw=76.95929 var=-0.0573411 mafic=1 tide=63.24529492110014 kinds=VVVVVVVVV...........",
        "fam=VOLCANIC_SOIL seed=7 x=9394 z=-9912 top=74 slope=0.0 temp=19.797184 rain=121.7418 gw=196.89868 var=-0.061511874 mafic=0 tide=63.182460367679596 kinds=GDDDDDDDD...........",
        "fam=VOLCANIC_SOIL seed=42 x=-5403 z=-1077 top=78 slope=14.0 temp=-15.5 rain=263.90048 gw=58.30201 var=0.09990394 mafic=0 tide=63.161629527807236 kinds=....................",
        "fam=VOLCANIC_SOIL seed=7 x=2599 z=-129 top=72 slope=6.0 temp=-7.4102325 rain=184.99077 gw=48.16767 var=0.072761536 mafic=0 tide=63.79115803539753 kinds=GDDD................",
        "fam=VOLCANIC_SOIL seed=7 x=4943 z=-1903 top=68 slope=0.0 temp=-27.171295 rain=420.0 gw=151.87529 var=0.3483292 mafic=1 tide=63.534971475601196 kinds=NIIIIIIIIII.........",
        "fam=VOLCANIC_SOIL seed=7 x=-6561 z=-6564 top=75 slope=22.0 temp=3.9981842 rain=200.19875 gw=78.76811 var=-0.9918897 mafic=0 tide=63.20882934331894 kinds=....................",
        "fam=VOLCANIC_SOIL seed=7 x=-3128 z=5957 top=65 slope=2.0 temp=-22.54455 rain=414.7209 gw=88.52303 var=0.8419857 mafic=1 tide=62.43440139293671 kinds=NIIIIIII............",
        "fam=VOLCANIC_SOIL seed=42 x=2078 z=-2685 top=77 slope=2.0 temp=11.016567 rain=300.0 gw=10.0 var=-0.21114302 mafic=0 tide=66.1831641793251 kinds=GDDDDDD.............",
        "fam=VOLCANIC_SOIL seed=7 x=5514 z=-4795 top=75 slope=15.0 temp=-14.4195595 rain=432.90988 gw=0.0 var=0.8778676 mafic=1 tide=62.47249408066273 kinds=....................",
        "fam=VOLCANIC_SOIL seed=7 x=4645 z=8924 top=78 slope=2.0 temp=-20.929508 rain=168.19412 gw=0.0 var=-0.15734637 mafic=0 tide=63.9595921933651 kinds=NIIIIII.............",
        "fam=VOLCANIC_SOIL seed=42 x=-1628 z=2936 top=44 slope=22.0 temp=-24.656292 rain=48.185795 gw=19.099749 var=0.9050524 mafic=1 tide=62.294725239276886 kinds=V...................",
        "fam=VOLCANIC_SOIL seed=42 x=-2962 z=-65 top=40 slope=15.0 temp=-14.1034775 rain=35.0 gw=158.61119 var=-0.46132708 mafic=0 tide=64.41008031368256 kinds=V...................",
        "fam=VOLCANIC_SOIL seed=42 x=5838 z=-3395 top=70 slope=10.0 temp=-8.0 rain=145.0 gw=150.0 var=-0.72260404 mafic=0 tide=63.64977914839983 kinds=G...................",
        "fam=VOLCANIC_SOIL seed=7 x=79 z=2132 top=64 slope=0.5 temp=24.164524 rain=293.84692 gw=58.453167 var=0.10690558 mafic=1 tide=62.699451595544815 kinds=GDDDDDDDD...........",
        "fam=VOLCANIC_SOIL seed=7 x=3312 z=-3289 top=47 slope=14.0 temp=29.116646 rain=341.80905 gw=158.80177 var=-0.1204648 mafic=1 tide=62.41000413894653 kinds=V...................",
        "fam=VOLCANIC_SOIL seed=42 x=-9197 z=-3300 top=51 slope=10.0 temp=6.9809303 rain=0.0 gw=150.0 var=-0.992599 mafic=0 tide=63.674381241202354 kinds=VVVV................",
        "fam=VOLCANIC_SOIL seed=7 x=-2183 z=656 top=70 slope=0.0 temp=-15.5 rain=420.0 gw=92.41587 var=0.34632397 mafic=1 tide=65.67669723927975 kinds=NVVVCCCCCCC.........",
        "fam=VOLCANIC_SOIL seed=7 x=2107 z=-743 top=48 slope=0.0 temp=-12.918627 rain=121.20927 gw=30.0 var=0.6092546 mafic=1 tide=63.65241318941116 kinds=VVVVVVVVVVV.........",
        "fam=VOLCANIC_SOIL seed=42 x=4549 z=-2480 top=67 slope=22.0 temp=-3.5854664 rain=55.0 gw=87.961876 var=-0.8368739 mafic=1 tide=62.93337391316891 kinds=....................",
        "fam=VOLCANIC_SOIL seed=7 x=8605 z=-339 top=71 slope=10.0 temp=-2.1191711 rain=468.27548 gw=92.8859 var=-0.45415473 mafic=0 tide=63.46399599313736 kinds=G...................",
        "fam=VOLCANIC_SOIL seed=7 x=4655 z=1555 top=71 slope=10.0 temp=7.587982 rain=275.95288 gw=142.27446 var=0.7071309 mafic=0 tide=65.23452708125114 kinds=G...................",
        "fam=OCEAN seed=42 x=3292 z=7082 top=55 slope=6.0 temp=-15.5 rain=479.25967 gw=94.971016 var=-0.9318844 mafic=1 tide=64.4890661239624 kinds=VVVVVVVVV...........",
        "fam=OCEAN seed=7 x=-6338 z=-5747 top=75 slope=10.0 temp=9.33931 rain=90.0 gw=51.30626 var=0.090313435 mafic=1 tide=62.98871811572462 kinds=M...................",
        "fam=OCEAN seed=7 x=5997 z=9903 top=77 slope=2.0 temp=-6.6870327 rain=148.9912 gw=10.0 var=-0.32175386 mafic=0 tide=62.340392112731934 kinds=MMMMMMM.............",
        "fam=OCEAN seed=7 x=-3917 z=5938 top=64 slope=0.5 temp=20.0 rain=400.8369 gw=23.565948 var=-0.5911639 mafic=0 tide=66.66785096749663 kinds=SSSSSTTTT...........",
        "fam=OCEAN seed=7 x=-9774 z=-8974 top=64 slope=22.0 temp=1.801857 rain=210.44421 gw=150.0 var=0.19225323 mafic=0 tide=62.80193783342838 kinds=R...................",
        "fam=OCEAN seed=7 x=-9672 z=-9234 top=65 slope=14.0 temp=16.364063 rain=349.28934 gw=72.405266 var=0.20136178 mafic=1 tide=63.48601633310318 kinds=S...................",
        "fam=OCEAN seed=7 x=4535 z=3591 top=59 slope=2.0 temp=-13.211407 rain=477.4993 gw=25.0 var=-0.10398221 mafic=1 tide=63.63258820772171 kinds=VVVVVVVVVVV.........",
        "fam=OCEAN seed=7 x=107 z=-4651 top=75 slope=0.5 temp=-13.696968 rain=217.45598 gw=163.06815 var=0.17342365 mafic=0 tide=63.51703064888716 kinds=MMMMMMM.............",
        "fam=OCEAN seed=42 x=3890 z=-3356 top=67 slope=14.0 temp=22.576256 rain=73.46174 gw=55.976772 var=0.7128819 mafic=1 tide=63.70140731334686 kinds=S...................",
        "fam=OCEAN seed=7 x=-1135 z=-1440 top=77 slope=22.0 temp=-21.278519 rain=113.455475 gw=133.73247 var=0.7539463 mafic=0 tide=62.782026797533035 kinds=M...................",
        "fam=OCEAN seed=42 x=1216 z=1864 top=65 slope=14.0 temp=-16.6 rain=441.6123 gw=82.30241 var=-0.7760893 mafic=0 tide=62.85781487822533 kinds=V...................",
        "fam=OCEAN seed=42 x=1407 z=4254 top=79 slope=0.0 temp=-27.47083 rain=457.5363 gw=88.62737 var=0.10425091 mafic=1 tide=63.21850606799126 kinds=MMMMMMMMM...........",
        "fam=OCEAN seed=42 x=-6813 z=6526 top=56 slope=14.0 temp=1.0285206 rain=482.50998 gw=10.0 var=0.24956083 mafic=0 tide=63.252647906541824 kinds=V...................",
        "fam=OCEAN seed=42 x=1803 z=-6838 top=49 slope=6.0 temp=17.602406 rain=461.7314 gw=61.82802 var=-0.31583214 mafic=0 tide=65.53102833032608 kinds=SSSSSSSSS...........",
        "fam=OCEAN seed=42 x=-8160 z=-2678 top=75 slope=6.0 temp=21.25845 rain=140.4736 gw=10.0 var=0.63300025 mafic=0 tide=62.79280677437782 kinds=MMMMM...............",
        "fam=OCEAN seed=42 x=1304 z=4454 top=67 slope=22.0 temp=28.0 rain=178.46764 gw=0.0 var=-0.6272609 mafic=1 tide=65.08501785993576 kinds=T...................",
        "fam=OCEAN seed=42 x=990 z=-8696 top=79 slope=10.0 temp=-17.966015 rain=62.102825 gw=132.06688 var=0.32354558 mafic=0 tide=65.78720879554749 kinds=M...................",
        "fam=OCEAN seed=7 x=552 z=4875 top=48 slope=15.0 temp=-12.0 rain=420.0 gw=30.0 var=-0.9853083 mafic=0 tide=64.04766482114792 kinds=V...................",
        "fam=OCEAN seed=7 x=1824 z=6356 top=65 slope=10.0 temp=-18.71373 rain=483.49805 gw=10.0 var=-0.8497052 mafic=1 tide=63.4488770365715 kinds=VVR.................",
        "fam=OCEAN seed=7 x=-9884 z=3620 top=72 slope=0.5 temp=-1.7020683 rain=332.52985 gw=25.0 var=0.7844058 mafic=0 tide=65.1020937897265 kinds=MMMMMMM.............",
        "fam=OCEAN seed=42 x=-309 z=-2925 top=74 slope=22.0 temp=-7.0304203 rain=422.8392 gw=199.77972 var=0.451393 mafic=1 tide=62.97519770823419 kinds=M...................",
        "fam=OCEAN seed=7 x=-9906 z=8606 top=67 slope=15.0 temp=4.0 rain=460.0 gw=104.56468 var=0.18626475 mafic=1 tide=63.300725758075714 kinds=T...................",
        "fam=OCEAN seed=7 x=8988 z=-8908 top=50 slope=0.5 temp=16.0 rain=81.2037 gw=59.188046 var=-0.52235425 mafic=1 tide=63.41301131248474 kinds=SSSSSSSSSSS.........",
        "fam=OCEAN seed=7 x=9871 z=5894 top=53 slope=0.5 temp=23.440662 rain=35.49841 gw=106.22233 var=-0.80243087 mafic=1 tide=65.71944072842598 kinds=SSSSSSSSSSS.........",
        "fam=OCEAN seed=7 x=5545 z=9306 top=67 slope=15.0 temp=25.0 rain=99.148186 gw=25.0 var=0.05826497 mafic=1 tide=64.11059936881065 kinds=T...................",
        "fam=OCEAN seed=7 x=8547 z=-137 top=78 slope=2.0 temp=7.98695 rain=223.19427 gw=30.426657 var=0.95488656 mafic=0 tide=63.23717591166496 kinds=MMMMMMM.............",
        "fam=OCEAN seed=7 x=7457 z=-2302 top=44 slope=2.0 temp=17.34779 rain=109.04774 gw=30.0 var=-0.9169669 mafic=0 tide=63.616353034973145 kinds=SSSSSSSSSSS.........",
        "fam=OCEAN seed=42 x=-5879 z=-4138 top=40 slope=14.0 temp=-19.778492 rain=320.25885 gw=80.14566 var=0.5278151 mafic=1 tide=63.943729653954506 kinds=V...................",
        "fam=OCEAN seed=42 x=-2843 z=4709 top=74 slope=0.5 temp=-29.648708 rain=235.055 gw=114.56468 var=-0.055491686 mafic=0 tide=63.3997800052166 kinds=MMMMMMM.............",
        "fam=OCEAN seed=42 x=-2575 z=-188 top=74 slope=14.0 temp=25.0 rain=119.14333 gw=30.0 var=-0.6495788 mafic=0 tide=62.79283744096756 kinds=M...................",
        "fam=OCEAN_RIDGE seed=7 x=-3346 z=-4095 top=65 slope=22.0 temp=15.562317 rain=62.585384 gw=0.0 var=0.065191746 mafic=0 tide=63.12095283716917 kinds=T...................",
        "fam=OCEAN_RIDGE seed=42 x=6790 z=9655 top=74 slope=0.0 temp=5.1186295 rain=341.23492 gw=24.0587 var=0.5185884 mafic=0 tide=65.67939579486847 kinds=RRRRRRRRR...........",
        "fam=OCEAN_RIDGE seed=42 x=7329 z=-8063 top=67 slope=14.0 temp=20.068077 rain=358.4062 gw=85.65042 var=-0.7792642 mafic=1 tide=63.28110831975937 kinds=S...................",
        "fam=OCEAN_RIDGE seed=42 x=-6206 z=-9874 top=74 slope=10.0 temp=25.77462 rain=0.0 gw=111.12025 var=-0.26654446 mafic=0 tide=63.92032354325056 kinds=R...................",
        "fam=OCEAN_RIDGE seed=7 x=1173 z=-2626 top=45 slope=0.0 temp=14.5 rain=318.85468 gw=2.3869038 var=0.61793303 mafic=0 tide=65.24305260181427 kinds=SSSSSSSSSSS.........",
        "fam=OCEAN_RIDGE seed=42 x=-6984 z=-1562 top=75 slope=15.0 temp=13.0 rain=159.9864 gw=25.519884 var=0.5930301 mafic=0 tide=64.19639889895916 kinds=....................",
        "fam=OCEAN_RIDGE seed=42 x=3149 z=874 top=79 slope=0.5 temp=-27.19077 rain=447.76294 gw=55.300045 var=0.9957696 mafic=1 tide=65.50649644434452 kinds=RRRRRRR.............",
        "fam=OCEAN_RIDGE seed=42 x=5731 z=8950 top=79 slope=2.0 temp=-25.324594 rain=177.04163 gw=77.27202 var=-0.7842779 mafic=1 tide=62.59375378489494 kinds=RRRRRRR.............",
        "fam=OCEAN_RIDGE seed=42 x=5977 z=-5360 top=69 slope=10.0 temp=1.7079563 rain=274.84506 gw=163.00755 var=0.6334065 mafic=0 tide=63.16072854399681 kinds=R...................",
        "fam=OCEAN_RIDGE seed=42 x=1141 z=-2597 top=66 slope=15.0 temp=26.50636 rain=391.2582 gw=162.26085 var=-0.075547576 mafic=1 tide=62.66977074742317 kinds=T...................",
        "fam=OCEAN_RIDGE seed=42 x=2319 z=-2686 top=60 slope=6.0 temp=17.79536 rain=469.31506 gw=43.78607 var=0.6584512 mafic=0 tide=63.251307904720306 kinds=SSSSSSS.............",
        "fam=OCEAN_RIDGE seed=7 x=-2685 z=-9768 top=57 slope=14.0 temp=-12.0 rain=306.537 gw=10.0 var=-0.37457705 mafic=0 tide=62.86308339238167 kinds=V...................",
        "fam=OCEAN_RIDGE seed=42 x=2832 z=6103 top=79 slope=6.0 temp=14.586037 rain=49.240707 gw=48.27677 var=-0.116060734 mafic=0 tide=62.736440539360046 kinds=RRRR................",
        "fam=OCEAN_RIDGE seed=7 x=5238 z=-9818 top=53 slope=0.5 temp=-0.5649662 rain=122.062805 gw=150.0 var=-0.6273917 mafic=0 tide=62.75458787381649 kinds=VVVVVVVVVVV.........",
        "fam=OCEAN_RIDGE seed=7 x=-1666 z=1335 top=67 slope=6.0 temp=17.330162 rain=143.68805 gw=135.4555 var=0.33846223 mafic=0 tide=63.80473756790161 kinds=SSSTTT..............",
        "fam=OCEAN_RIDGE seed=42 x=8884 z=1739 top=70 slope=10.0 temp=25.202442 rain=166.43971 gw=30.0 var=0.8023062 mafic=1 tide=64.01241743564606 kinds=R...................",
        "fam=OCEAN_RIDGE seed=7 x=-6207 z=4990 top=66 slope=10.0 temp=-3.757925 rain=308.33682 gw=25.0 var=-0.69002104 mafic=1 tide=63.04883845150471 kinds=VVR.................",
        "fam=OCEAN_RIDGE seed=42 x=3636 z=-3982 top=41 slope=2.0 temp=-13.766024 rain=134.05478 gw=10.0 var=0.13017356 mafic=0 tide=62.94690366834402 kinds=VVVVVVVVVVV.........",
        "fam=OCEAN_RIDGE seed=7 x=-4991 z=-6078 top=79 slope=22.0 temp=-14.0 rain=394.4106 gw=7.434416 var=0.062939286 mafic=0 tide=63.34982866048813 kinds=....................",
        "fam=OCEAN_RIDGE seed=42 x=-2456 z=-4353 top=71 slope=2.0 temp=19.232342 rain=127.1438 gw=148.49521 var=-0.7016393 mafic=0 tide=62.202522695064545 kinds=RRRRRRR.............",
        "fam=OCEAN_RIDGE seed=7 x=9734 z=1018 top=51 slope=0.0 temp=23.023884 rain=211.10826 gw=106.23938 var=0.2170583 mafic=0 tide=62.33285665512085 kinds=SSSSSSSSSSS.........",
        "fam=OCEAN_RIDGE seed=42 x=3723 z=-5018 top=67 slope=0.5 temp=-0.9425602 rain=61.506035 gw=15.002501 var=-0.19136786 mafic=0 tide=65.67121090367436 kinds=VVVVVRRRR...........",
        "fam=OCEAN_RIDGE seed=42 x=1619 z=9347 top=65 slope=2.0 temp=-28.489998 rain=231.07854 gw=44.987442 var=0.2448101 mafic=0 tide=63.417058765888214 kinds=VVVVVRRRR...........",
        "fam=OCEAN_RIDGE seed=42 x=-2605 z=-8129 top=77 slope=6.0 temp=25.0 rain=414.22845 gw=57.74695 var=0.5385115 mafic=0 tide=63.259945183992386 kinds=RRRR................",
        "fam=OCEAN_RIDGE seed=7 x=-9751 z=-8663 top=42 slope=2.0 temp=9.478313 rain=261.86777 gw=21.3418 var=0.64758897 mafic=1 tide=65.37566637992859 kinds=SSSSSSSSSSS.........",
        "fam=OCEAN_RIDGE seed=42 x=-1478 z=-4513 top=67 slope=0.0 temp=17.810284 rain=90.0 gw=43.468082 var=-0.734985 mafic=1 tide=63.379192888736725 kinds=SSSSSSTTTTT.........",
        "fam=OCEAN_RIDGE seed=7 x=2529 z=8615 top=67 slope=0.0 temp=-2.0 rain=230.89659 gw=127.51043 var=-0.55458415 mafic=0 tide=63.847782373428345 kinds=VVVVVVRRRRR.........",
        "fam=OCEAN_RIDGE seed=42 x=9134 z=-1394 top=69 slope=2.0 temp=-9.334553 rain=424.96326 gw=82.7796 var=0.16171467 mafic=0 tide=62.9486443400383 kinds=RRRRRRRR............",
        "fam=OCEAN_RIDGE seed=42 x=-8950 z=-2252 top=72 slope=2.0 temp=4.0 rain=11.782289 gw=21.068346 var=0.8436837 mafic=1 tide=62.73978188633919 kinds=RRRRRRR.............",
        "fam=OCEAN_RIDGE seed=42 x=5184 z=-3304 top=51 slope=22.0 temp=14.5 rain=35.0 gw=47.04839 var=0.73071146 mafic=1 tide=62.666051141917706 kinds=S...................",
        "fam=SHORE_SANDY seed=42 x=8329 z=-6589 top=72 slope=6.0 temp=-0.6063614 rain=55.0 gw=109.95473 var=-0.1518749 mafic=1 tide=66.59997622668743 kinds=GDVV................",
        "fam=SHORE_SANDY seed=42 x=6064 z=3338 top=71 slope=6.0 temp=-24.142918 rain=354.33832 gw=19.582272 var=-0.38363552 mafic=0 tide=64.25751560926437 kinds=NIRR................",
        "fam=SHORE_SANDY seed=7 x=8598 z=2350 top=75 slope=15.0 temp=-19.142553 rain=62.723995 gw=82.02669 var=0.55509615 mafic=0 tide=63.74043647944927 kinds=....................",
        "fam=SHORE_SANDY seed=7 x=222 z=-1245 top=64 slope=22.0 temp=-29.796736 rain=478.92584 gw=38.750435 var=-0.24916244 mafic=1 tide=62.89437912404537 kinds=T...................",
        "fam=SHORE_SANDY seed=7 x=7867 z=-9 top=74 slope=2.0 temp=-25.486343 rain=200.0 gw=159.05217 var=0.5612898 mafic=0 tide=62.76796905696392 kinds=NIIIRRR.............",
        "fam=SHORE_SANDY seed=7 x=6485 z=5069 top=68 slope=10.0 temp=13.0 rain=145.0 gw=99.65179 var=0.12494242 mafic=1 tide=62.52035003900528 kinds=G...................",
        "fam=SHORE_SANDY seed=42 x=-4497 z=2419 top=78 slope=6.0 temp=21.630676 rain=198.51321 gw=66.91487 var=-0.02824223 mafic=0 tide=63.350156024098396 kinds=GDVV................",
        "fam=SHORE_SANDY seed=42 x=9434 z=-1010 top=42 slope=2.0 temp=9.0 rain=167.97346 gw=55.941296 var=0.077409506 mafic=0 tide=63.200985074043274 kinds=SSSSSSSSSSS.........",
        "fam=SHORE_SANDY seed=7 x=-6246 z=-2740 top=64 slope=2.0 temp=4.6621056 rain=249.53017 gw=32.029736 var=-0.26197302 mafic=0 tide=63.42420753836632 kinds=SSSSSTTTT...........",
        "fam=SHORE_SANDY seed=42 x=9466 z=-8857 top=66 slope=0.5 temp=-8.534403 rain=420.0 gw=103.05481 var=-0.13864756 mafic=0 tide=64.23389925062656 kinds=SSSSSTTTT...........",
        "fam=SHORE_SANDY seed=42 x=6026 z=-6774 top=66 slope=10.0 temp=25.0 rain=199.5689 gw=4.3492436 var=-0.72671616 mafic=0 tide=65.8309528157115 kinds=SST.................",
        "fam=SHORE_SANDY seed=7 x=-2343 z=-7104 top=71 slope=0.5 temp=25.0 rain=199.62617 gw=73.848366 var=-0.01816082 mafic=0 tide=64.12173530459404 kinds=GDDDVVV.............",
        "fam=SHORE_SANDY seed=42 x=5370 z=8278 top=51 slope=15.0 temp=11.589199 rain=462.71133 gw=107.573715 var=0.5569582 mafic=0 tide=63.19063697755337 kinds=S...................",
        "fam=SHORE_SANDY seed=7 x=-7426 z=69 top=68 slope=2.0 temp=0.40006828 rain=157.7028 gw=179.96945 var=-0.39959657 mafic=0 tide=65.75556881725788 kinds=SSSSSTTTT...........",
        "fam=SHORE_SANDY seed=7 x=650 z=7876 top=53 slope=15.0 temp=-16.6 rain=200.0 gw=86.8323 var=-0.8117924 mafic=1 tide=62.809369042515755 kinds=S...................",
        "fam=SHORE_SANDY seed=42 x=1507 z=8058 top=79 slope=0.5 temp=-16.6 rain=203.95657 gw=25.0 var=0.46203005 mafic=0 tide=62.535492330789566 kinds=NIIIRRR.............",
        "fam=SHORE_SANDY seed=7 x=1122 z=6579 top=76 slope=22.0 temp=-9.067171 rain=432.5112 gw=94.622925 var=-0.6478267 mafic=0 tide=63.51187914609909 kinds=....................",
        "fam=SHORE_SANDY seed=7 x=-415 z=-5746 top=65 slope=15.0 temp=16.0 rain=420.0 gw=98.32734 var=0.20241487 mafic=1 tide=63.254705011844635 kinds=T...................",
        "fam=SHORE_SANDY seed=7 x=-7447 z=7675 top=78 slope=15.0 temp=15.062721 rain=145.0 gw=180.5065 var=0.54359674 mafic=0 tide=64.15701600909233 kinds=....................",
        "fam=SHORE_SANDY seed=7 x=-414 z=2230 top=75 slope=6.0 temp=-15.5 rain=292.16867 gw=53.435898 var=0.5166228 mafic=0 tide=63.40305599570274 kinds=VVVV................",
        "fam=SHORE_SANDY seed=42 x=9514 z=139 top=75 slope=6.0 temp=-2.0 rain=420.0 gw=54.14128 var=0.61232936 mafic=1 tide=64.09162110090256 kinds=GDVV................",
        "fam=SHORE_SANDY seed=7 x=9767 z=-6271 top=77 slope=10.0 temp=-10.085253 rain=55.0 gw=74.43451 var=-0.91306317 mafic=0 tide=62.45669603347778 kinds=C...................",
        "fam=SHORE_SANDY seed=7 x=-7794 z=4756 top=79 slope=6.0 temp=12.451149 rain=460.0 gw=121.78589 var=0.5610932 mafic=0 tide=63.80644732713699 kinds=GDVV................",
        "fam=SHORE_SANDY seed=7 x=-7103 z=-8020 top=72 slope=15.0 temp=-2.0 rain=52.345543 gw=30.456734 var=0.043700457 mafic=1 tide=62.25751346349716 kinds=....................",
        "fam=SHORE_SANDY seed=42 x=-1732 z=4495 top=65 slope=15.0 temp=-26.061474 rain=318.2229 gw=25.0 var=-0.812065 mafic=1 tide=63.85827213525772 kinds=T...................",
        "fam=SHORE_SANDY seed=42 x=1365 z=-5959 top=50 slope=0.5 temp=4.0 rain=470.22675 gw=26.458 var=0.32809258 mafic=1 tide=63.586519956588745 kinds=SSSSSSSSSSS.........",
        "fam=SHORE_SANDY seed=42 x=7990 z=-1633 top=67 slope=2.0 temp=-17.005192 rain=264.63492 gw=51.152027 var=-0.38169086 mafic=0 tide=64.71696224808693 kinds=SSSSSTTTT...........",
        "fam=SHORE_SANDY seed=42 x=9519 z=5256 top=47 slope=6.0 temp=-0.53554344 rain=185.73833 gw=25.0 var=0.9935311 mafic=0 tide=63.37583684921265 kinds=SSSSSSSSS...........",
        "fam=SHORE_SANDY seed=42 x=-2634 z=-9861 top=78 slope=15.0 temp=-14.505236 rain=376.39914 gw=30.0 var=0.85251045 mafic=0 tide=63.17272163927555 kinds=....................",
        "fam=SHORE_SANDY seed=42 x=465 z=1098 top=71 slope=0.0 temp=20.0 rain=132.96994 gw=17.42202 var=0.17006588 mafic=1 tide=64.18442204594612 kinds=GDDDDVVVV...........",
        "fam=SEA_CLIFFS seed=42 x=-5330 z=5171 top=75 slope=6.0 temp=-23.47086 rain=183.70578 gw=103.346756 var=0.3449049 mafic=1 tide=62.8239441215992 kinds=NIRR................",
        "fam=SEA_CLIFFS seed=42 x=284 z=5430 top=58 slope=14.0 temp=-8.732304 rain=77.038734 gw=10.0 var=0.16469228 mafic=0 tide=63.00838592182845 kinds=V...................",
        "fam=SEA_CLIFFS seed=42 x=-7195 z=532 top=46 slope=15.0 temp=-30.0 rain=137.4572 gw=130.49107 var=0.3574556 mafic=1 tide=64.93720373511314 kinds=V...................",
        "fam=SEA_CLIFFS seed=42 x=420 z=-702 top=79 slope=22.0 temp=14.033714 rain=430.60165 gw=24.866127 var=0.6147715 mafic=0 tide=62.97617141902447 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=7317 z=-6157 top=69 slope=2.0 temp=-16.6 rain=251.80295 gw=135.88898 var=-0.7394395 mafic=0 tide=63.53137946873903 kinds=NVVVVVVV............",
        "fam=SEA_CLIFFS seed=42 x=-1799 z=-5801 top=69 slope=6.0 temp=-14.159757 rain=22.25229 gw=30.0 var=0.48018515 mafic=0 tide=63.55576711893082 kinds=VVVVV...............",
        "fam=SEA_CLIFFS seed=7 x=8490 z=8299 top=69 slope=22.0 temp=11.354103 rain=46.744526 gw=188.70866 var=-0.6979022 mafic=0 tide=62.41258269548416 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=-3687 z=9801 top=77 slope=0.0 temp=-21.204659 rain=223.76239 gw=188.57513 var=0.274765 mafic=0 tide=63.27482686471194 kinds=NIIIIRRRR...........",
        "fam=SEA_CLIFFS seed=7 x=-3907 z=9675 top=69 slope=15.0 temp=-10.491898 rain=469.76215 gw=34.28855 var=-0.3125521 mafic=1 tide=63.65067332983017 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=8827 z=-6326 top=57 slope=10.0 temp=-27.13977 rain=252.34094 gw=156.80475 var=-0.28508687 mafic=1 tide=62.781474471092224 kinds=VVVV................",
        "fam=SEA_CLIFFS seed=42 x=-6217 z=-2279 top=79 slope=10.0 temp=3.0111809 rain=228.18068 gw=51.367676 var=0.5095321 mafic=0 tide=62.681186847388744 kinds=G...................",
        "fam=SEA_CLIFFS seed=42 x=-4898 z=-3326 top=72 slope=0.5 temp=-12.0 rain=344.7003 gw=145.79851 var=-0.087041974 mafic=0 tide=64.12328451871872 kinds=CCDCVVV.............",
        "fam=SEA_CLIFFS seed=42 x=202 z=3252 top=61 slope=6.0 temp=9.0 rain=17.506123 gw=25.0 var=-0.6173018 mafic=1 tide=63.67034935951233 kinds=SSSSSSS.............",
        "fam=SEA_CLIFFS seed=42 x=9175 z=-4411 top=75 slope=10.0 temp=-16.6 rain=0.0 gw=30.0 var=0.46816516 mafic=0 tide=63.22958408296108 kinds=N...................",
        "fam=SEA_CLIFFS seed=7 x=-7343 z=1089 top=67 slope=14.0 temp=28.033344 rain=184.93596 gw=28.785526 var=-0.84449244 mafic=0 tide=63.881472177803516 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=3521 z=4808 top=65 slope=2.0 temp=-14.0 rain=248.65144 gw=165.13489 var=0.0067288876 mafic=1 tide=62.504989475011826 kinds=CDDDVVVV............",
        "fam=SEA_CLIFFS seed=7 x=-6288 z=-2739 top=72 slope=0.5 temp=-15.5 rain=238.57144 gw=25.0 var=-0.76324916 mafic=0 tide=62.364780843257904 kinds=VVVVRVV.............",
        "fam=SEA_CLIFFS seed=7 x=2086 z=729 top=78 slope=0.5 temp=-15.025381 rain=58.066578 gw=47.675873 var=0.6845095 mafic=1 tide=63.49085810780525 kinds=VVVVVVV.............",
        "fam=SEA_CLIFFS seed=42 x=9711 z=-1166 top=73 slope=0.5 temp=17.343857 rain=145.0 gw=174.01152 var=-0.35824573 mafic=1 tide=64.15831311047077 kinds=GDDDVVV.............",
        "fam=SEA_CLIFFS seed=42 x=-7373 z=7442 top=66 slope=0.5 temp=4.0 rain=29.249727 gw=135.76492 var=-0.7224748 mafic=1 tide=64.70109063386917 kinds=GDDDDVVVV...........",
        "fam=SEA_CLIFFS seed=42 x=-4232 z=-4096 top=75 slope=15.0 temp=-3.369381 rain=133.32977 gw=34.309387 var=0.47136176 mafic=0 tide=62.67625707387924 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=7521 z=-2222 top=72 slope=15.0 temp=19.395428 rain=33.323826 gw=0.0 var=0.22376955 mafic=0 tide=63.353072106838226 kinds=....................",
        "fam=SEA_CLIFFS seed=7 x=7287 z=2370 top=73 slope=10.0 temp=-14.0 rain=0.0 gw=0.0 var=-0.86321616 mafic=1 tide=65.4714887291193 kinds=V...................",
        "fam=SEA_CLIFFS seed=7 x=-2201 z=8272 top=79 slope=0.0 temp=-17.4 rain=107.76487 gw=57.973705 var=0.41807902 mafic=0 tide=65.18927383422852 kinds=NIIIIRRRR...........",
        "fam=SEA_CLIFFS seed=42 x=1594 z=5188 top=72 slope=0.0 temp=13.0 rain=176.19553 gw=54.072666 var=-0.95356333 mafic=0 tide=63.74514761567116 kinds=GDDDDVVVV...........",
        "fam=SEA_CLIFFS seed=7 x=2085 z=-4171 top=64 slope=0.0 temp=27.821003 rain=90.0 gw=0.0 var=-0.13448238 mafic=1 tide=63.12249654531479 kinds=CCCCCCVVVVV.........",
        "fam=SEA_CLIFFS seed=42 x=-5626 z=7445 top=61 slope=22.0 temp=-17.4 rain=463.98245 gw=0.0 var=-0.17365909 mafic=1 tide=62.309310257434845 kinds=V...................",
        "fam=SEA_CLIFFS seed=7 x=8690 z=-6867 top=78 slope=22.0 temp=11.267662 rain=437.70697 gw=113.91171 var=0.22786474 mafic=1 tide=64.31767076253891 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=7458 z=7317 top=70 slope=15.0 temp=-27.092903 rain=0.0 gw=64.367035 var=-0.5522506 mafic=0 tide=65.339430809021 kinds=....................",
        "fam=SEA_CLIFFS seed=42 x=-1842 z=-3423 top=72 slope=22.0 temp=26.97865 rain=145.0 gw=4.892385 var=0.70579123 mafic=0 tide=64.11783985048532 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=7 x=2472 z=8885 top=67 slope=14.0 temp=-15.5 rain=488.7271 gw=192.27324 var=0.22232091 mafic=1 tide=62.629002541303635 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=42 x=-1845 z=-7380 top=61 slope=10.0 temp=-24.392513 rain=420.0 gw=74.30113 var=0.033447266 mafic=1 tide=62.79448023438454 kinds=VVV.................",
        "fam=MOUNTAIN_SHORE seed=42 x=1421 z=-8834 top=74 slope=14.0 temp=-17.4 rain=418.09958 gw=194.86041 var=-0.32200742 mafic=1 tide=64.83145251870155 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=42 x=6910 z=-657 top=67 slope=0.0 temp=-8.0 rain=131.84247 gw=170.3774 var=0.16349888 mafic=0 tide=63.04125344008207 kinds=GDDDDDVVVVV.........",
        "fam=MOUNTAIN_SHORE seed=7 x=-7718 z=-7848 top=72 slope=2.0 temp=-14.576673 rain=299.75653 gw=0.0 var=-0.626891 mafic=0 tide=62.930980533361435 kinds=VVVVVVV.............",
        "fam=MOUNTAIN_SHORE seed=42 x=-6133 z=75 top=55 slope=0.5 temp=-18.54668 rain=23.997158 gw=117.30201 var=0.62388575 mafic=0 tide=63.720483645796776 kinds=VVVVVVVVVVV.........",
        "fam=MOUNTAIN_SHORE seed=7 x=1868 z=4784 top=71 slope=6.0 temp=-14.0 rain=181.04791 gw=76.55458 var=-0.8884988 mafic=0 tide=65.82983492314816 kinds=CVVV................",
        "fam=MOUNTAIN_SHORE seed=7 x=3606 z=-2317 top=72 slope=6.0 temp=-23.773735 rain=84.44142 gw=150.61505 var=-0.23875165 mafic=0 tide=63.06661058962345 kinds=NIRR................",
        "fam=MOUNTAIN_SHORE seed=42 x=590 z=-6658 top=70 slope=14.0 temp=14.5 rain=307.63312 gw=72.310844 var=0.49993992 mafic=1 tide=63.93574137613177 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=7 x=3949 z=-4157 top=67 slope=15.0 temp=16.04752 rain=295.93964 gw=100.01081 var=-0.02762866 mafic=0 tide=63.666951928287745 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=42 x=-6018 z=-2391 top=73 slope=14.0 temp=24.556702 rain=158.13356 gw=52.933216 var=0.8983774 mafic=0 tide=63.023436130955815 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=42 x=-3270 z=345 top=61 slope=10.0 temp=-14.661512 rain=442.4464 gw=104.50329 var=-0.51990044 mafic=0 tide=63.518609285354614 kinds=VVV.................",
        "fam=MOUNTAIN_SHORE seed=7 x=2534 z=3920 top=50 slope=6.0 temp=-14.0 rain=251.4765 gw=178.87903 var=0.45301473 mafic=1 tide=63.90132647752762 kinds=VVVVVVVVV...........",
        "fam=MOUNTAIN_SHORE seed=42 x=6414 z=9094 top=73 slope=6.0 temp=13.30917 rain=55.0 gw=25.0 var=-0.25620675 mafic=1 tide=63.375604063272476 kinds=CCVV................",
        "fam=MOUNTAIN_SHORE seed=42 x=8217 z=-6737 top=56 slope=22.0 temp=-14.0 rain=25.179386 gw=25.0 var=0.8166393 mafic=1 tide=65.86631760001183 kinds=V...................",
        "fam=MOUNTAIN_SHORE seed=7 x=4144 z=-5687 top=64 slope=22.0 temp=18.989117 rain=215.56705 gw=170.40256 var=-0.18999505 mafic=1 tide=62.79824051260948 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=7 x=-490 z=4226 top=73 slope=0.5 temp=20.561497 rain=429.2787 gw=10.0 var=0.43775356 mafic=1 tide=62.791311860084534 kinds=GDDDVVV.............",
        "fam=MOUNTAIN_SHORE seed=7 x=4620 z=-8415 top=71 slope=0.0 temp=-26.401627 rain=491.46646 gw=144.6372 var=0.0039435625 mafic=1 tide=65.42246792465448 kinds=NIIIIRRRR...........",
        "fam=MOUNTAIN_SHORE seed=42 x=-5695 z=-2446 top=65 slope=22.0 temp=-20.672014 rain=145.0 gw=167.94022 var=0.87140274 mafic=1 tide=64.75371366553009 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=42 x=-4448 z=-9180 top=45 slope=2.0 temp=-21.47747 rain=371.79376 gw=171.34297 var=-0.37253094 mafic=1 tide=65.6282410621643 kinds=VVVVVVVVVVV.........",
        "fam=MOUNTAIN_SHORE seed=42 x=7204 z=6035 top=70 slope=15.0 temp=14.66357 rain=25.910646 gw=150.0 var=-0.29429686 mafic=0 tide=64.77956625819206 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=7 x=9033 z=1976 top=70 slope=15.0 temp=4.0 rain=379.20065 gw=71.428406 var=0.6495886 mafic=1 tide=65.53696092963219 kinds=....................",
        "fam=MOUNTAIN_SHORE seed=7 x=-562 z=-6834 top=76 slope=10.0 temp=-16.001905 rain=448.4529 gw=130.20529 var=0.5280001 mafic=1 tide=64.23757550120354 kinds=N...................",
        "fam=MOUNTAIN_SHORE seed=7 x=-9677 z=-4859 top=76 slope=0.0 temp=-21.757748 rain=154.51003 gw=30.0 var=-0.27077687 mafic=0 tide=62.84324796497822 kinds=NIIIIRRRR...........",
        "fam=MOUNTAIN_SHORE seed=42 x=4682 z=-7639 top=67 slope=10.0 temp=-8.0 rain=408.5552 gw=134.42822 var=-0.66374063 mafic=1 tide=62.296698689460754 kinds=G...................",
        "fam=MOUNTAIN_SHORE seed=7 x=-4633 z=-7667 top=64 slope=14.0 temp=-0.046188354 rain=145.0 gw=141.89107 var=0.8696457 mafic=1 tide=65.40617601573467 kinds=V...................",
        "fam=MOUNTAIN_SHORE seed=42 x=-4632 z=-7129 top=76 slope=0.5 temp=-19.674004 rain=305.43488 gw=0.0 var=-0.7586734 mafic=0 tide=62.7616019397974 kinds=NIIIRRR.............",
        "fam=MOUNTAIN_SHORE seed=7 x=-7625 z=-414 top=56 slope=14.0 temp=-17.4 rain=0.85520744 gw=52.607597 var=0.23547757 mafic=0 tide=62.79354339838028 kinds=V...................",
        "fam=MOUNTAIN_SHORE seed=7 x=1490 z=4790 top=72 slope=2.0 temp=16.510658 rain=185.57248 gw=63.169624 var=-0.14549589 mafic=1 tide=62.3673979640007 kinds=GDDDVVV.............",
        "fam=MOUNTAIN_SHORE seed=7 x=4012 z=2074 top=77 slope=10.0 temp=-1.6008244 rain=200.0 gw=39.4665 var=0.42622566 mafic=0 tide=62.83296225965023 kinds=G...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=-1780 z=-2420 top=67 slope=2.0 temp=4.8661118 rain=282.50696 gw=104.95855 var=0.9065981 mafic=0 tide=62.60115613043308 kinds=GDDDDDDD............",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=1400 z=2633 top=67 slope=0.5 temp=-14.0 rain=166.79057 gw=30.0 var=-0.8514205 mafic=0 tide=63.23886589705944 kinds=VCCCCDCCD...........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=4215 z=6486 top=46 slope=0.5 temp=-1.6943913 rain=460.0 gw=148.256 var=0.23709834 mafic=0 tide=64.99902246892452 kinds=VVVVVVVVVVV.........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-6210 z=5299 top=47 slope=0.0 temp=-14.0 rain=300.0 gw=48.74208 var=0.7103114 mafic=1 tide=63.790491327643394 kinds=VVVVVVVVVVV.........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=8552 z=8182 top=73 slope=15.0 temp=-20.757599 rain=456.10767 gw=86.356964 var=0.0070955753 mafic=0 tide=65.0201353430748 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-5465 z=-7388 top=67 slope=14.0 temp=-14.0 rain=360.12366 gw=139.36494 var=-0.88733184 mafic=1 tide=65.73530235886574 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=7977 z=-5202 top=74 slope=15.0 temp=-20.309572 rain=57.484 gw=168.17442 var=0.8523122 mafic=0 tide=62.63534024357796 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=7879 z=7910 top=65 slope=10.0 temp=19.437222 rain=473.6764 gw=13.759804 var=-0.61963737 mafic=1 tide=63.443293519318104 kinds=G...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=-6727 z=-8250 top=60 slope=10.0 temp=10.223381 rain=391.96674 gw=82.55967 var=0.57235694 mafic=1 tide=62.31041347980499 kinds=VVVV................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=9013 z=-1162 top=61 slope=22.0 temp=-6.895956 rain=362.92047 gw=167.86435 var=-0.13281238 mafic=1 tide=64.96364087238908 kinds=V...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=8904 z=6767 top=72 slope=6.0 temp=-16.6 rain=0.0 gw=140.88779 var=-0.93808496 mafic=0 tide=64.25836622714996 kinds=NVVV................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=-3887 z=-2405 top=73 slope=0.5 temp=-27.461512 rain=234.47131 gw=36.01787 var=-0.066848755 mafic=1 tide=65.01628375053406 kinds=NIIIIII.............",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=4179 z=-3669 top=78 slope=22.0 temp=-25.417353 rain=495.68915 gw=31.846237 var=0.5856755 mafic=0 tide=63.654593884944916 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-3177 z=-4420 top=64 slope=0.5 temp=-17.425858 rain=289.28625 gw=142.46564 var=-0.97811806 mafic=1 tide=62.403622686862946 kinds=NVVIIIIVI...........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-7770 z=-1934 top=46 slope=22.0 temp=-28.344604 rain=55.0 gw=74.380196 var=-0.41821265 mafic=0 tide=64.32499265670776 kinds=V...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-3862 z=9780 top=79 slope=15.0 temp=-18.0 rain=32.90951 gw=199.70776 var=0.7846453 mafic=1 tide=63.68070665001869 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-9865 z=-4870 top=77 slope=0.5 temp=-7.311569 rain=90.0 gw=3.2932281 var=0.1926471 mafic=1 tide=63.63024611026049 kinds=CCCCCCC.............",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=6786 z=4205 top=67 slope=10.0 temp=-2.0791378 rain=273.42236 gw=118.92439 var=-0.39683926 mafic=0 tide=63.05936351418495 kinds=G...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-1464 z=9243 top=74 slope=10.0 temp=-15.593094 rain=35.0 gw=74.77417 var=0.25475872 mafic=1 tide=62.93320859223604 kinds=V...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-8289 z=-7761 top=42 slope=22.0 temp=-18.889275 rain=284.37845 gw=13.487614 var=-0.6452205 mafic=0 tide=63.280269891023636 kinds=V...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=-7043 z=-4620 top=64 slope=14.0 temp=25.0 rain=87.98215 gw=185.68903 var=-0.03709519 mafic=0 tide=63.4108612537384 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=8303 z=9083 top=64 slope=10.0 temp=-7.981346 rain=16.165167 gw=10.0 var=-0.668506 mafic=0 tide=62.848570719361305 kinds=V...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-5180 z=3478 top=64 slope=0.5 temp=28.0 rain=139.40099 gw=42.2866 var=-0.36668468 mafic=1 tide=63.418636068701744 kinds=GDDDDDDDD...........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=3203 z=2880 top=41 slope=14.0 temp=29.367641 rain=337.5009 gw=103.599785 var=-0.746539 mafic=0 tide=62.82347737252712 kinds=V...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=-3541 z=-6075 top=64 slope=0.5 temp=16.0 rain=236.27919 gw=94.33818 var=-0.454934 mafic=1 tide=65.28839771449566 kinds=VVVVVRRRR...........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=7 x=-7465 z=-2768 top=76 slope=22.0 temp=24.929642 rain=420.0 gw=180.5581 var=-0.21324575 mafic=1 tide=62.5137537419796 kinds=....................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=3554 z=6909 top=78 slope=0.5 temp=-17.140997 rain=90.0 gw=144.86139 var=-0.29853833 mafic=0 tide=63.43610516190529 kinds=NIIIIII.............",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=1927 z=3992 top=46 slope=6.0 temp=11.945087 rain=163.25897 gw=182.39944 var=0.9561788 mafic=1 tide=63.50263011455536 kinds=VVVVVVVVV...........",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=666 z=5600 top=72 slope=10.0 temp=-15.5 rain=276.45285 gw=150.53433 var=-0.6696892 mafic=1 tide=63.45875504612923 kinds=N...................",
        "fam=VOLCANIC_MOUNTAIN_SHORE seed=42 x=471 z=-2222 top=77 slope=15.0 temp=26.170681 rain=420.0 gw=91.81597 var=-0.49680364 mafic=1 tide=64.17405043542385 kinds=....................",
        "fam=ROCKY_COAST seed=7 x=702 z=2129 top=77 slope=10.0 temp=0.0 rain=45.81851 gw=10.0 var=-0.815837 mafic=0 tide=63.482506621629 kinds=R...................",
        "fam=ROCKY_COAST seed=42 x=9666 z=-7711 top=76 slope=6.0 temp=13.0 rain=35.0 gw=49.367523 var=0.33609855 mafic=0 tide=62.84035821259022 kinds=RRRR................",
        "fam=ROCKY_COAST seed=42 x=9430 z=-540 top=68 slope=0.5 temp=-2.0 rain=240.97221 gw=13.6763935 var=0.2028991 mafic=0 tide=63.74867745488882 kinds=RRRRRRRRR...........",
        "fam=ROCKY_COAST seed=42 x=9227 z=577 top=79 slope=2.0 temp=-6.999218 rain=373.3965 gw=195.0225 var=-0.3919754 mafic=1 tide=65.44959516823292 kinds=RRRRRRR.............",
        "fam=ROCKY_COAST seed=7 x=3587 z=1917 top=66 slope=6.0 temp=27.74033 rain=322.34866 gw=30.0 var=0.19943202 mafic=0 tide=64.20974662899971 kinds=RRRRRR..............",
        "fam=ROCKY_COAST seed=42 x=-1942 z=2400 top=66 slope=15.0 temp=20.286732 rain=200.0 gw=158.44017 var=-0.91445947 mafic=0 tide=63.68144953995943 kinds=R...................",
        "fam=ROCKY_COAST seed=7 x=-6563 z=954 top=67 slope=14.0 temp=-20.037186 rain=193.27962 gw=164.94713 var=-0.89367485 mafic=0 tide=65.36615380644798 kinds=R...................",
        "fam=ROCKY_COAST seed=42 x=-8683 z=52 top=68 slope=14.0 temp=-11.234772 rain=0.0 gw=2.8823495 var=-0.12273264 mafic=0 tide=63.379556089639664 kinds=....................",
        "fam=ROCKY_COAST seed=7 x=1009 z=-3977 top=66 slope=14.0 temp=22.922283 rain=145.0 gw=150.6028 var=-0.4977311 mafic=0 tide=63.49596148729324 kinds=R...................",
        "fam=ROCKY_COAST seed=7 x=5719 z=-5646 top=40 slope=10.0 temp=3.6923447 rain=265.76553 gw=105.99421 var=0.41325438 mafic=1 tide=62.7632872313261 kinds=RRRR................",
        "fam=ROCKY_COAST seed=42 x=7454 z=-139 top=72 slope=0.0 temp=-25.762 rain=263.51718 gw=0.0 var=0.9916992 mafic=1 tide=63.51497679948807 kinds=RRRRRRRRR...........",
        "fam=ROCKY_COAST seed=7 x=-8810 z=8800 top=66 slope=6.0 temp=-22.890057 rain=420.0 gw=160.90836 var=-0.9700576 mafic=0 tide=63.52446519955993 kinds=RRRRRR..............",
        "fam=ROCKY_COAST seed=42 x=6326 z=5567 top=52 slope=0.5 temp=-29.762 rain=262.4345 gw=0.0 var=-0.7486998 mafic=0 tide=65.14126913249493 kinds=RRRRRRRRRRR.........",
        "fam=ROCKY_COAST seed=42 x=901 z=-5616 top=73 slope=2.0 temp=1.9789772 rain=35.0 gw=0.0 var=0.44491994 mafic=0 tide=63.39848303794861 kinds=RRRRRRR.............",
        "fam=ROCKY_COAST seed=7 x=9434 z=1874 top=69 slope=22.0 temp=-14.0 rain=408.81314 gw=27.901672 var=0.7403362 mafic=1 tide=65.02169752120972 kinds=R...................",
        "fam=ROCKY_COAST seed=7 x=7122 z=3346 top=72 slope=2.0 temp=-17.4 rain=310.32 gw=163.7734 var=-0.28373754 mafic=1 tide=64.12679915130138 kinds=RRRRRRR.............",
        "fam=ROCKY_COAST seed=7 x=2551 z=-6113 top=69 slope=6.0 temp=-20.194183 rain=278.321 gw=57.36853 var=-0.90189564 mafic=1 tide=64.54331731796265 kinds=RRRRR...............",
        "fam=ROCKY_COAST seed=42 x=-4366 z=6092 top=41 slope=0.0 temp=17.624825 rain=470.128 gw=43.549217 var=0.48281944 mafic=0 tide=63.43542370200157 kinds=RRRRRRRRRRR.........",
        "fam=ROCKY_COAST seed=7 x=8037 z=-6602 top=45 slope=14.0 temp=9.0 rain=145.0 gw=0.0 var=-0.1345787 mafic=0 tide=62.53745612502098 kinds=R...................",
        "fam=ROCKY_COAST seed=7 x=2031 z=8849 top=65 slope=2.0 temp=7.7601433 rain=462.3067 gw=134.68579 var=-0.02210331 mafic=0 tide=63.93058231472969 kinds=RRRRRRRRR...........",
        "fam=ROCKY_COAST seed=42 x=-730 z=9705 top=43 slope=14.0 temp=24.043777 rain=409.88312 gw=182.99094 var=-0.5441456 mafic=1 tide=62.45230406522751 kinds=R...................",
        "fam=ROCKY_COAST seed=7 x=4250 z=-9710 top=63 slope=15.0 temp=0.0 rain=292.52652 gw=25.0 var=-0.3033595 mafic=0 tide=65.91189515590668 kinds=R...................",
        "fam=ROCKY_COAST seed=42 x=-1884 z=7251 top=68 slope=22.0 temp=10.773666 rain=55.0 gw=25.0 var=0.19801557 mafic=0 tide=63.2917417883873 kinds=....................",
        "fam=ROCKY_COAST seed=7 x=3893 z=9885 top=75 slope=2.0 temp=2.6090698 rain=21.908373 gw=100.93642 var=0.96806717 mafic=1 tide=64.04803389310837 kinds=RRRRRRR.............",
        "fam=ROCKY_COAST seed=42 x=-8628 z=605 top=48 slope=10.0 temp=8.2938 rain=177.07541 gw=25.0 var=-0.95195687 mafic=0 tide=65.39968271553516 kinds=RRRR................",
        "fam=ROCKY_COAST seed=7 x=3009 z=-9143 top=70 slope=14.0 temp=-5.34153 rain=59.283882 gw=185.81699 var=-0.079113126 mafic=1 tide=63.08243328332901 kinds=....................",
        "fam=ROCKY_COAST seed=42 x=1359 z=-5436 top=76 slope=15.0 temp=-9.425165 rain=276.24194 gw=133.85857 var=0.3505504 mafic=0 tide=65.65138694643974 kinds=....................",
        "fam=ROCKY_COAST seed=42 x=6798 z=2707 top=58 slope=2.0 temp=-2.7663345 rain=132.50648 gw=191.14871 var=-0.5834707 mafic=0 tide=62.541240498423576 kinds=RRRRRRRRRRR.........",
        "fam=ROCKY_COAST seed=7 x=6943 z=6273 top=65 slope=10.0 temp=16.0 rain=316.68533 gw=0.0 var=0.26970434 mafic=1 tide=66.3702238202095 kinds=RRR.................",
        "fam=ROCKY_COAST seed=42 x=8040 z=2032 top=64 slope=10.0 temp=-22.93843 rain=35.0 gw=53.63853 var=0.02538824 mafic=1 tide=64.93480044603348 kinds=RRR.................",
    };

    private static final String[] NOISES = {
        "noise seed=42 x=656 z=4463 patch=-0.4910687208175659 beach=-4.0120765333995223E-4 tide=63.134307503700256",
        "noise seed=7 x=3469 z=9530 patch=-0.0919409990310669 beach=0.014326978474855423 tide=63.305271714925766",
        "noise seed=42 x=-625 z=4409 patch=0.17047104239463806 beach=-0.009242718107998371 tide=64.45445922017097",
        "noise seed=7 x=-814 z=-1319 patch=0.3246491551399231 beach=-0.0043260217644274235 tide=65.04126951098442",
        "noise seed=42 x=6238 z=1190 patch=0.16926348209381104 beach=0.04094327986240387 tide=62.56079316139221",
        "noise seed=7 x=-9458 z=7939 patch=-0.2549569308757782 beach=-0.053921379148960114 tide=63.44589111208916",
        "noise seed=42 x=3440 z=-4259 patch=0.46484845876693726 beach=0.02818167209625244 tide=63.26193058490753",
        "noise seed=7 x=-6529 z=6565 patch=0.5510279536247253 beach=-0.043671879917383194 tide=66.28857405669987",
        "noise seed=42 x=-345 z=-1130 patch=-0.01579788327217102 beach=-0.0010773800313472748 tide=62.591569662094116",
        "noise seed=7 x=135 z=610 patch=-0.4634483754634857 beach=1.9614753546193242E-4 tide=62.47435349225998",
        "noise seed=42 x=-5626 z=-8463 patch=0.03464944660663605 beach=-0.023470193147659302 tide=65.3458760380745",
        "noise seed=7 x=244 z=-4264 patch=-0.14898476004600525 beach=0.006560513749718666 tide=64.3965071439743",
        "noise seed=42 x=-9771 z=4580 patch=-0.1906517595052719 beach=-0.06396600604057312 tide=62.4478765130043",
        "noise seed=7 x=-912 z=-4831 patch=-0.21852713823318481 beach=-0.0010334218386560678 tide=63.389195919036865",
        "noise seed=42 x=-7151 z=-9268 patch=-0.3886341154575348 beach=-0.027523037046194077 tide=62.08671283721924",
        "noise seed=7 x=-4153 z=5589 patch=-0.19494308531284332 beach=-0.03181720897555351 tide=62.767276018857956",
    };

    @Test
    void columnsMatchTfcSurfaceKinds() {
        for (String line : GOLDEN) {
            final Matcher m = ROW.matcher(line);
            assertThat(m.matches()).as(line).isTrue();
            final SurfaceFamily family = SurfaceFamily.valueOf(m.group(1));
            final long seed = Long.parseLong(m.group(2));
            final int x = Integer.parseInt(m.group(3));
            final int z = Integer.parseInt(m.group(4));
            final int top = Integer.parseInt(m.group(5));
            final double tideRaw = Double.parseDouble(m.group(12));
            assertThat(SurfaceNoise.shoreTide(seed).noise(x, z)).as(line).isEqualTo(tideRaw);
            final SurfaceColumn column = new SurfaceColumn(x, z, SEA_LEVEL, Double.parseDouble(m.group(6)),
                Float.parseFloat(m.group(7)), Float.parseFloat(m.group(8)), Float.parseFloat(m.group(9)), Float.parseFloat(m.group(10)),
                (int) tideRaw, m.group(11).equals("1"));
            final ColumnSurface surface = ColumnSurface.build(family.builder(), column, top, MIN_Y);

            assertThat(surface.endY()).as(line).isEqualTo(top - 19);
            final StringBuilder kinds = new StringBuilder();
            for (int y = top; y >= top - 19; y--) {
                kinds.append(code(surface.kind(y)));
            }
            assertThat(kinds.toString()).as(line).isEqualTo(m.group(13));
        }
    }

    @Test
    void noisesMatchTfc() {
        for (String line : NOISES) {
            final Matcher m = NOISE.matcher(line);
            assertThat(m.matches()).as(line).isTrue();
            final long seed = Long.parseLong(m.group(1));
            final int x = Integer.parseInt(m.group(2));
            final int z = Integer.parseInt(m.group(3));
            assertThat(SurfaceNoise.PATCH.noise(x, z)).as(line).isEqualTo(Double.parseDouble(m.group(4)));
            assertThat(SurfaceNoise.SAND_GRAVEL_BEACH.noise(x, z)).as(line).isEqualTo(Double.parseDouble(m.group(5)));
            final Noise2D tide = SurfaceNoise.shoreTide(seed);
            assertThat(tide.noise(x, z)).as(line).isEqualTo(Double.parseDouble(m.group(6)));
        }
    }

    /** The char of a kind, as in the golden rows; '.' for a block the surface leaves alone. */
    static char code(SurfaceKind kind) {
        if (kind == null) {
            return '.';
        }
        return switch (kind) {
            case ROCK_SURFACE -> 'R';
            case GRASS -> 'G';
            case DIRT -> 'D';
            case COARSE_DIRT -> 'C';
            case MUD -> 'M';
            case GRAVEL -> 'V';
            case SAND -> 'S';
            case SANDSTONE -> 'T';
            case SNOW -> 'N';
            case PACKED_ICE -> 'I';
        };
    }
}
