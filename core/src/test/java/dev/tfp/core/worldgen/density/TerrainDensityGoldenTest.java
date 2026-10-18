/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.density;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.height.BiomeWeights;
import dev.tfp.core.worldgen.height.HeightBiomes;
import dev.tfp.core.worldgen.height.ShoreBlendType;
import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.noise.Noise2D;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.volcano.CinderCones;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's per-block terrain density (from TFC at c3b4d9536). A scratch oracle compiled TFC's own
 * {@code ChunkHeightFiller}, {@code BiomeNoise} (with {@code cenotes} and {@code undergroundLakes}), {@code TFCBiomes},
 * {@code ChunkBiomeSampler}, {@code shore/*} (with every sampler's {@code noise(y, noiseIn)}), the river samplers, the cinder
 * cone sampler, the biome layers and the region generator (the M2-GEN-06 height oracle), ran each column through
 * {@code sampleColumnHeightAndBiome}, then {@code ChunkNoiseFiller.calculateNoiseAtHeight} copied verbatim over the filler's own
 * state with {@code heightNoiseValue = (int) height}, as {@code fillColumn} calls it. Solid is a density above 0
 * ({@code TFCAquifer.sampleState}). No server was started.
 *
 * <p>{@code CENOTES}: one cenote biome's sampler alone ({@code setColumn}, {@code noise(y)}) over a 96 x 96 grid of columns
 * (every second block from -96 to 94) and y 0..140: seed, biome, the count of values above {@code AIR_THRESHOLD}, a hash of the
 * values rounded to 1e-9, and three exact samples ({@code x,y,z=value}).
 *
 * <p>{@code CHUNKS}: the first chunk (scanning rings of 3 chunks outward from 0, 0, so negative coordinates too) centred in each
 * shore biome with a density and the ice sheet shore, and two plain chunks, per seed. Each line: seed, chunk x and z, the
 * biome at its centre, the columns with a shore weight other than {@code NONE}, then over every column ({@code z} outer) and
 * y 0..150: the solid blocks, the solid blocks above the column's surface, the open blocks at or below it, a hash of the
 * solid/air decisions ({@code h * 31 + solid}), a hash of the densities rounded to 1e-6, and samples {@code local,y=density} of
 * the diagonal columns 0 and 5. TFC sums the biome samplers in identity-hash order, so the densities may differ in the last
 * bits: the decisions are exact, the samples are compared within 1e-9.
 */
class TerrainDensityGoldenTest {
    // from TFC at c3b4d9536
    static final List<String> CENOTES = List.of(
        "42 80 32902 1488197081942127524L -96,44,-96=0.9319327157543654 -96,45,-96=1.5007901971313242 -96,46,-96=2.069647678508283",
        "42 84 33535 -6975092253267839212L -96,37,-96=0.5862127993729785 -96,38,-96=1.1550702807499373 -96,39,-96=1.7239277621268962",
        "42 87 36122 -4452697092666808310L -96,70,-96=0.8280723094451886 -96,71,-96=1.3969297908221479 -96,72,-96=1.9657872721991065",
        "-7 80 32493 -6564909995372027334L -96,46,-92=0.5019368579753073 -96,47,-92=0.7070134786463983 -96,48,-92=0.9120900993174895",
        "-7 84 34611 -9212248679962363391L -96,44,-92=0.595418868360254 -96,45,-92=0.8004954890313453 -96,46,-92=1.0055721097024362",
        "-7 87 39872 -4083073091109369313L -96,66,-92=0.527258110512144 -96,67,-92=0.7323347311832352 -96,68,-92=0.9374113518543262",
        "20260917 80 27993 4066158647977917520L -96,42,-96=0.9937522834502427 -96,43,-96=2.679050088959852 -96,44,-96=4.3643478944694625",
        "20260917 84 27997 -1053085231623776076L -96,40,-96=1.6434732796853966 -96,41,-96=3.3287710851950076 -96,42,-96=5.014068890704617",
        "20260917 87 28108 -7042571602976290847L -96,58,-96=0.7126793597283753 -96,59,-96=2.3979771652379847 -96,60,-96=4.0832749707475955"
    );

    // from TFC at c3b4d9536
    static final List<String> CHUNKS = List.of(
        "42 0 0 39 256 17336 131 186 7741918163254642133L 1103985412247845528L 0,55=1.0 0,65=0.5641685749123933 0,75=-0.2799306379719291 0,85=-1.0 5,55=1.0 5,65=1.0 5,75=-1.0 5,85=-1.0",
        "42 3 3 31 256 17758 228 1 8780914034683502603L -6247365993642777799L 0,55=0.7841269841284149 0,65=0.7841269841284149 0,75=-0.6481072289679173 0,85=-1.0 5,55=0.7625806051600807 5,65=0.7625806051600807 5,75=-0.9736648824424518 5,85=-1.0",
        "42 -12 -9 17 256 19289 256 0 -5463907588647278574L -1032654062523138287L 0,55=0.974603174605315 0,65=0.974603174605315 0,75=-5.960464455334602E-9 0,85=-1.0 5,55=0.9412929730356507 5,65=0.9412929730356507 5,75=0.19999999701976778 5,85=-1.0",
        "42 -12 -6 17 256 19767 256 0 -3227007234025189718L 6577959667393494479L 0,55=0.5444444444449825 0,65=0.5444444444449825 0,75=0.4 0,85=-1.0 5,55=0.535097346230662 5,65=0.535097346230662 5,75=0.535097346230662 5,85=-1.0",
        "42 18 -6 36 256 15920 19 264 1341272576028709879L 3383367541474267146L 0,55=0.8234481221457877 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=0.8840084757786878 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "42 -33 27 32 256 16392 256 0 -7054049125939921971L -7518465941966655081L 0,55=0.4 0,65=0.16395276521864433 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.2336365022606645 5,75=-1.0 5,85=-1.0",
        "42 -30 33 37 256 16949 256 0 -1046265206265885486L -2212603953737129046L 0,55=0.4 0,65=0.19810484963007485 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=0.19915656094010314 5,75=-1.0 5,85=-1.0",
        "42 111 -57 38 256 17878 201 0 -3381498396492584373L 8841605960066910916L 0,55=0.4 0,65=0.18925392500171082 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=0.23679732080210034 5,75=-1.0 5,85=-1.0",
        "42 207 -210 34 256 17931 0 1261 -6890560777570256492L -6404130643762051068L 0,55=0.4 0,65=0.4 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=0.4 5,75=-1.0 5,85=-1.0",
        "42 210 -210 35 256 18921 38 283 2142518418165932106L -8872204760478546415L 0,55=0.4 0,65=0.4 0,75=-0.9273358169573678 0,85=-1.0 5,55=0.4 5,65=0.4 5,75=-0.6513678489543319 5,85=-1.0",
        "42 231 -48 93 256 18802 806 0 -490636664501773241L 5794469015362318152L 0,55=0.6111111111118975 0,65=0.6111111111118975 0,75=-0.6462963082181427 0,85=-1.0 5,55=0.6101190476198304 5,65=0.6101190476198304 5,75=-0.3823633254967016 5,85=-1.0",
        "42 168 285 33 256 15631 23 1895 1673243018801304400L -9142039035516972750L 0,55=0.4 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.9904058413514653 5,75=-1.0 5,85=-1.0",
        "-7 0 0 0 0 11103 256 0 5026811573998965268L -5400729043374225839L 0,55=-1.0 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=-1.0 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "-7 -12 -12 0 0 10974 256 0 7751021483312983891L 3665645094265766801L 0,55=-1.0 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=-1.0 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "-7 15 15 37 256 16128 256 0 4372360742718967825L -5977536117568508911L 0,55=0.4 0,65=-0.40000001192092893 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.40000001192092893 5,75=-1.0 5,85=-1.0",
        "-7 -33 6 31 256 15115 256 0 5642637129735637532L -6457148552821946926L 0,55=0.4 0,65=-0.6000000149011612 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "-7 -45 -36 39 256 17429 256 0 -2828517379661607790L 1194673003193863163L 0,55=0.6274614869398962 0,65=0.6274614869398962 0,75=-1.0 0,85=-1.0 5,55=0.6160680330486562 5,65=0.3876642669636812 5,75=-1.0 5,85=-1.0",
        "-7 -45 -24 32 256 15928 256 0 9012871134180845453L 3932603371719472097L 0,55=0.4 0,65=-0.40000001192092893 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.6000000149011612 5,75=-1.0 5,85=-1.0",
        "-7 -39 -69 36 256 16464 0 850 -7108685239953265101L -5774912590952034756L 0,55=0.4 0,65=-0.41377101681799744 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.4330058929524907 5,75=-1.0 5,85=-1.0",
        "-7 -66 -255 33 256 14166 0 691 -3352938264330705359L -2300676518079395644L 0,55=-1.0 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=-0.8000000029839572 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "-7 -315 -138 35 256 18479 0 321 1554238904465071700L -4263069205289290906L 0,55=0.4 0,65=0.39066299984105585 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=0.3823629215330938 5,75=-1.0 5,85=-1.0",
        "-7 -318 -138 34 256 17658 0 1425 6535476480378870265L 1680282640547620891L 0,55=0.4545914274255658 0,65=0.2549089690333177 0,75=-1.0 0,85=-1.0 5,55=0.4361707252708956 5,65=0.29733686003234133 5,75=-1.0 5,85=-1.0",
        "-7 -324 -141 93 256 19400 187 152 -5276697714239977715L 5565297409719682874L 0,55=0.8860863605227152 0,65=0.8860863605227152 0,75=0.19999999701976778 0,85=-1.0 5,55=0.9089647297191112 5,65=0.9089647297191112 5,75=0.19999999701976778 5,85=-1.0",
        "-7 -246 336 94 256 13706 256 0 1886140728300999917L -8309660094852956655L 0,55=-1.0 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=-0.40000001192092893 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "20260917 0 0 89 0 21003 256 0 -64563796078215498L 4504541460400244433L 0,55=0.4 0,65=0.4 0,75=0.19999999701976778 0,85=-1.0 5,55=0.4 5,65=0.4 5,75=0.4 5,85=-1.0",
        "20260917 -9 -9 94 256 17015 256 0 8786992198687705356L -5158294403011375023L 0,55=0.4 0,65=0.19999999701976778 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=0.4 5,75=-1.0 5,85=-1.0",
        "20260917 -12 -12 0 0 12220 256 0 5617275100670146223L 9042306074404439441L 0,55=-1.0 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=-1.0 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "20260917 6 -12 39 256 16564 0 21 7516986460436819823L -2023541385237306521L 0,55=0.8450793650810231 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=0.879430471724236 5,65=-0.7671538906655058 5,75=-1.0 5,85=-1.0",
        "20260917 15 -18 38 256 15568 0 667 4988908056700245065L 8755267411871363397L 0,55=0.7000000000011175 0,65=-0.9162764454050681 0,75=-1.0 0,85=-1.0 5,55=0.6973493303582504 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "20260917 -3 36 31 256 16406 256 0 -542996610036996585L 2115555901716660369L 0,55=0.4 0,65=-0.6000000149011612 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.2000000089406967 5,75=-1.0 5,85=-1.0",
        "20260917 -3 39 32 256 16131 256 0 -2334723677019590124L -8569148401216908851L 0,55=0.4 0,65=-0.2000000089406967 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.43466883381388244 5,75=-1.0 5,85=-1.0",
        "20260917 48 -36 37 256 15314 0 849 7079924770318518713L -8853356116949980730L 0,55=0.4 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.830874004886426 5,75=-1.0 5,85=-1.0",
        "20260917 -24 72 33 256 15523 0 3505 -534257338388679246L -8794883819737242458L 0,55=0.4 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "20260917 90 -84 93 256 16634 153 170 8790465227153614099L -523971828497973081L 0,55=1.0 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=1.0 5,65=-1.0 5,75=-1.0 5,85=-1.0",
        "20260917 -66 114 36 256 17344 0 0 8989365125739807577L -3726812004316478447L 0,55=0.4 0,65=0.4 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=0.4 5,75=-1.0 5,85=-1.0",
        "20260917 -69 120 34 256 16281 0 2209 2585057046046857498L 8766205882178318181L 0,55=0.4 0,65=-1.0 0,75=-1.0 0,85=-1.0 5,55=0.4 5,65=-0.07369077956785797 5,75=-1.0 5,85=-1.0"
    );

    private static final Map<Long, SurfaceHeights> HEIGHTS = new ConcurrentHashMap<>();

    static SurfaceHeights heights(long seed) {
        return HEIGHTS.computeIfAbsent(seed, s -> {
            final Seed stream = Seed.of(s);
            final RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
            new ChunkDataGenerator(regions, stream);
            return SurfaceHeights.create(s, regions, stream, new CinderCones(s));
        });
    }

    @Test
    void cenoteSamplersMatchTfc() {
        for (String line : CENOTES) {
            final String[] p = line.split(" ");
            final long seed = Long.parseLong(p[0]);
            final int biome = Integer.parseInt(p[1]);
            final CenoteCarving carving = new CenoteCarving(seed);
            final Noise2D height = HeightBiomes.get(biome).height().apply(seed);
            final BiomeWeights weights = new BiomeWeights();
            weights.put(biome, 1.0);
            long h = 17;
            int air = 0;
            for (int gx = -96; gx < 96; gx += 2) {
                for (int gz = -96; gz < 96; gz += 2) {
                    final CenoteCarving.Column column = carving.column(weights, gx, gz, id -> height);
                    for (int y = 0; y <= 140; y++) {
                        final double v = column.noise(y);
                        h = h * 1000003L + Math.round(v * 1e9);
                        if (v > 0.4) {
                            air++;
                        }
                    }
                }
            }
            assertThat(air).as(line).isEqualTo(Integer.parseInt(p[2]));
            assertThat(h).as(line).isEqualTo(Long.parseLong(p[3].replace("L", "")));
            for (int i = 4; i < p.length; i++) {
                final String[] kv = p[i].split("=");
                final String[] xyz = kv[0].split(",");
                final CenoteCarving.Column column = carving.column(weights, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[2]), id -> height);
                assertThat(column.noise(Integer.parseInt(xyz[1]))).as(p[i]).isCloseTo(Double.parseDouble(kv[1]), within(1e-12));
            }
        }
    }

    @Test
    void chunkDensitiesMatchTfc() {
        int arches = 0;
        for (String line : CHUNKS) {
            final String[] p = line.split(" ");
            final long seed = Long.parseLong(p[0]);
            final int cx = Integer.parseInt(p[1]);
            final int cz = Integer.parseInt(p[2]);
            final SurfaceHeights.Chunk chunk = heights(seed).chunk(cx, cz);
            int shoreColumns = 0, solid = 0, above = 0, opened = 0;
            long solidHash = 17, densityHash = 17;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    final SurfaceHeights.Column column = chunk.column(lx, lz);
                    for (int t = 1; t < ShoreBlendType.SIZE; t++) {
                        if (column.shoreWeights()[t] > 0) {
                            shoreColumns++;
                            break;
                        }
                    }
                    final int surface = column.surfaceHeight();
                    for (int y = 0; y <= 150; y++) {
                        final double d = column.density(y);
                        final boolean s = TerrainDensity.isSolid(d);
                        solidHash = solidHash * 31L + (s ? 1 : 0);
                        densityHash = densityHash * 1000003L + Math.round(d * 1e6);
                        if (s) {
                            solid++;
                            if (y > surface) {
                                above++;
                            }
                        } else if (y <= surface) {
                            opened++;
                            assertThat(column.opens(y)).as("opens agrees with the density").isTrue();
                        }
                    }
                }
            }
            assertThat(chunk.column(0, 0).biome() >= 0).isTrue();
            assertThat(shoreColumns).as(line).isEqualTo(Integer.parseInt(p[4]));
            assertThat(solid).as(line).isEqualTo(Integer.parseInt(p[5]));
            assertThat(above).as(line).isEqualTo(Integer.parseInt(p[6]));
            assertThat(opened).as(line).isEqualTo(Integer.parseInt(p[7]));
            assertThat(solidHash).as(line).isEqualTo(Long.parseLong(p[8].replace("L", "")));
            assertThat(densityHash).as(line).isEqualTo(Long.parseLong(p[9].replace("L", "")));
            for (int i = 10; i < p.length; i++) {
                final String[] kv = p[i].split("=");
                final String[] ly = kv[0].split(",");
                final int local = Integer.parseInt(ly[0]);
                assertThat(chunk.column(local, local).density(Integer.parseInt(ly[1]))).as(line + " " + p[i])
                    .isCloseTo(Double.parseDouble(kv[1]), within(1e-9));
            }
            if (opened > 0) {
                arches++;
            }
        }
        assertThat(arches).as("chunks where the density opens blocks below the surface").isGreaterThanOrEqualTo(10);
    }
}
