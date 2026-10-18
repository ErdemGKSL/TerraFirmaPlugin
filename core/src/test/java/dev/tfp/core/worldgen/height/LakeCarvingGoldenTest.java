/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.height;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.river.RiverBlendType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's carved lakes and blended terrain density in chunks (M2-GEN-04). They come from TFC at c3b4d9536:
 * the M2-GEN-06 height oracle (TFC's own {@code ChunkHeightFiller}, {@code BiomeNoise} with {@code undergroundLakes},
 * {@code TFCBiomes}, {@code ChunkBiomeSampler}, the shore and river samplers, the biome layer and region generator) ran each
 * column through {@code sampleColumnHeightAndBiome}, then {@code ChunkNoiseFiller.calculateNoiseAtHeight} copied verbatim
 * over the filler's own state (the column's weighted biome noise samplers, the shore and river blend weights, the surface
 * integrity depth) with {@code heightNoiseValue = (int) height}, as {@code fillColumn} calls it. No server was started.
 *
 * <p>{@code LAKESAMPLER}: the {@code MOUNTAIN_LAKE} sampler alone at 64 columns x y 30..100 (exact bits, and three columns
 * at y 50 and 63). {@code LAKECHUNK}: chunks centred in a carved lake biome (4 per seed), chunks next to one (2) and river
 * chunks (3), each column in TFC's order ({@code z} outer) and y 0..140: {@code weights} hashes the river blend weights the
 * density reads, {@code noise} the biome stage (the weighted sum of the samplers), {@code density} the full density of the
 * columns whose only shore weight is {@code NONE} ({@code densityColumns}), {@code open} the positions at or below the
 * surface whose density is not positive ({@code opened} of them). TFC sums the samplers in the order of an identity hash
 * map, which changes between JVM runs in the last bits, so the hashes use values rounded to 1e-9 and the samples (the
 * diagonal columns 0, 5, 10, 15 at y 55, 63 and 70: biome stage and density, {@code S} for a shore column) compare within 1e-12.
 */
class LakeCarvingGoldenTest {
    static final List<String> SAMPLERS = List.of(
        "LAKESAMPLER seed=42 hash=1995583172144357788L -3000,-4500,50=0.0 -3000,-4500,63=0.4751610620217601 -1081,1229,50=0.0 -1081,1229,63=0.42418434016012097 838,-2042,50=0.10386025911361363 838,-2042,63=0.748042202711734",
        "LAKESAMPLER seed=-7 hash=-2586968715170006472L -3000,-4500,50=0.10298800025862453 -3000,-4500,63=0.7529880099443793 -1081,1229,50=0.0 -1081,1229,63=0.3424335073694229 838,-2042,50=0.0 838,-2042,63=0.3447516792182895",
        "LAKESAMPLER seed=20260917 hash=-6942327101999788747L -3000,-4500,50=0.1364089524210833 -3000,-4500,63=0.6481321684737825 -1081,1229,50=0.0 -1081,1229,63=0.3569927239206587 838,-2042,50=0.08530545907660807 838,-2042,63=0.5268248691134014"
    );

    static final List<String> CHUNKS = List.of(
        "LAKECHUNK seed=42 chunk=10,20 lakeColumns=0 densityColumns=256 multiRiver=256 opened=1 weights=-6897211772339764897L noise=-4708107211269053423L density=1720274642200113141L open=17005107L samples=",
        "LAKECHUNK seed=42 chunk=15,35 lakeColumns=0 densityColumns=256 multiRiver=256 opened=129 weights=-7365359572273666517L noise=-4708107211269053423L density=-3204351988712329041L open=-660013771247892239L samples=",
        "LAKECHUNK seed=42 chunk=50,0 lakeColumns=256 densityColumns=256 multiRiver=25 opened=3275 weights=2914474652736747186L noise=5393668652056477905L density=3978123967099602682L open=-8537842186612325202L samples=0,55=0.3530693797040797:-0.3061387594107896 0,63=0.5602235329648356:-0.7204470659338446 0,70=0.2544364913700776:-0.1088729827420507 5,55=0.2624933371287882:-0.12498667425953192 5,63=0.6508200624934734:-0.9016401249917952 5,70=0.3637339575781321:-0.327467915158974 10,55=0.0:0.4 10,63=0.168093603411552:0.06381279317564381 10,70=0.0:0.4 15,55=0.0:0.4 15,63=0.3098029616738147:-1.0 15,70=9.632579965200961E-4:-0.535590382684283",
        "LAKECHUNK seed=42 chunk=55,-5 lakeColumns=256 densityColumns=256 multiRiver=31 opened=1974 weights=3105451714747679457L noise=2732022178376008845L density=-1178991326592949921L open=-7097119014968566633L samples=0,55=0.07385831251249349:0.25228337497446285 0,63=0.18787947823312992:0.024241043532340578 0,70=0.1138845223523128:0.172230955294526 5,55=0.0:0.4 5,63=0.0754198049644831:0.24916039007047203 5,70=0.0:0.4 10,55=0.0:0.4 10,63=0.1968367151607226:0.0063265696770883895 10,70=0.10425641795758532:0.19148716408405267 15,55=0.14690876649083182:0.10618246701669476 15,63=0.5079868142007012:-0.6159736284070788 15,70=0.375118195391388:-0.35023639078696767",
        "LAKECHUNK seed=42 chunk=55,0 lakeColumns=256 densityColumns=256 multiRiver=224 opened=2741 weights=-8266481458287547511L noise=8587859080801125537L density=-4537393873117555589L open=2315611257499592674L samples=0,55=0.38981123645376914:-0.37962247291189444 0,63=0.6419580207028636:-0.8839160414129011 0,70=0.29195801548615335:-0.1839160309755694 5,55=0.2938653987819856:-0.206090530709083 5,63=0.601445133936043:-0.8132528261936945 5,70=0.2605452445957098:-0.1403165573635783 10,55=0.0:0.4 10,63=0.15520821329406762:0.02072075084940106 10,70=0.0:0.31848544973514614 15,55=0.16198196473987675:0.07603607051843636 15,63=0.5052359381100063:-0.6748650682717027 15,70=0.33876443615212665:-0.36553708186721323",
        "LAKECHUNK seed=42 chunk=60,-60 lakeColumns=0 densityColumns=256 multiRiver=0 opened=0 weights=3287218962338315281L noise=-4708107211269053423L density=4049385554241407931L open=17L samples=",
        "LAKECHUNK seed=42 chunk=60,-5 lakeColumns=256 densityColumns=256 multiRiver=256 opened=5130 weights=-1575037407278999191L noise=-721562709238653271L density=8215634620456266182L open=-5689267455692952795L samples=0,55=0.18016994786803617:-0.01461097744299994 0,63=0.4351964066956314:-0.6750967612341275 0,70=0.31546199216391824:-0.4790237384946725 5,55=0.24071251373632352:-0.1028453710567735 5,63=0.5542707194680739:-0.8048655410677882 5,70=0.38129487200592255:-0.49629470471210735 10,55=0.287407537501136:-0.17481507500548377 10,63=0.6503705058740206:-0.9331141130631665 10,70=0.42195689368892814:-0.49743629722624316 15,55=0.14487868456038885:0.11024263087760322 15,63=0.5175979997287576:-0.6406198314769297 15,70=0.17153317975588603:0.05886225427987707",
        "LAKECHUNK seed=42 chunk=95,75 lakeColumns=256 densityColumns=256 multiRiver=0 opened=1317 weights=-4116997435589107227L noise=-4243517480708804479L density=6947699286302790833L open=9028139421818308392L samples=0,55=0.0:0.4 0,63=0.031588804662112044:0.33682239067542297 0,70=0.01402239299022744:0.37195521401938847 5,55=0.053316427213751745:0.2933671455719007 5,63=0.1652211907864414:0.06955761842527086 5,70=0.09961318373140819:0.20077363253607045 10,55=0.07875802952305336:0.24318085910300596 10,63=0.2805437468163607:-0.15860500353030993 10,70=0.14119599832992263:0.11885742583604653 15,55=0.14412356553588693:0.11175286892715253 15,63=0.435790236549812:-0.47158047310287055 15,70=0.3292738166045068:-0.25854763321146645",
        "LAKECHUNK seed=42 chunk=60,105 lakeColumns=256 densityColumns=256 multiRiver=0 opened=122 weights=7731538218572741649L noise=5522860524143241234L density=-3412193506464776510L open=-2685130271553776771L samples=0,55=0.06518000483265562:0.2696399903342032 0,63=0.1698017320594504:0.06039653587983429 0,70=0.08895057042190964:0.2220988591555181 5,55=0.04683206604451185:0.30633586791062745 5,63=0.14560595619365618:0.10878808761160297 5,70=0.06297375470165932:0.2740524905962123 10,55=0.07723019901076104:0.2455396019779026 10,63=0.17422682597315914:0.05154634805238384 10,70=0.08755880471427778:0.22488239057079218 15,55=0.08178848656406083:0.2364230268712691 15,63=0.18763845934390547:0.024723081310791317 15,70=0.08512677834438725:0.22974644331059138",
        "LAKECHUNK seed=-7 chunk=-55,-35 lakeColumns=0 densityColumns=16 multiRiver=256 opened=34 weights=-7862540533185662283L noise=-4708107211269053423L density=6610209535558814653L open=8002059234077433660L samples=",
        "LAKECHUNK seed=-7 chunk=-65,-35 lakeColumns=0 densityColumns=256 multiRiver=256 opened=297 weights=-4148906676192266299L noise=-4708107211269053423L density=3798495453391945061L open=-6594957244404438726L samples=",
        "LAKECHUNK seed=-7 chunk=-70,-35 lakeColumns=0 densityColumns=256 multiRiver=256 opened=112 weights=-3956754479550238195L noise=-4708107211269053423L density=-3685754803101066706L open=-666603396338457670L samples=",
        "LAKECHUNK seed=-7 chunk=-100,-50 lakeColumns=256 densityColumns=256 multiRiver=0 opened=0 weights=-6435954321603929551L noise=647303133050517016L density=3259984413832583134L open=17L samples=0,55=0.05137083516058916:0.2972583296782476 0,63=0.12075285866663042:0.15849428266538979 0,70=0.06097527386966481:-1.0 5,55=0.022599208477684332:0.3548015830443788 5,63=0.10818496024099525:0.18363007951680055 5,70=0.05013315928039995:-1.0 10,55=0.05639378438740773:0.2872124312247644 10,63=0.1293429443977332:0.14131411120357001 10,70=0.03829818372639174:-0.67659638235423 15,55=0.09191641758650332:0.21616716482630866 15,63=0.17932798775767067:0.04134402448332275 15,70=0.06998185378163477:-0.33996371650448753",
        "LAKECHUNK seed=-7 chunk=-120,-105 lakeColumns=256 densityColumns=256 multiRiver=80 opened=2956 weights=-1658495061366721719L noise=8103947139780047349L density=6322089992367091492L open=-6327001478692083756L samples=0,55=0.2154864063990535:-0.020285143098928815 0,63=0.5633887993966951:-0.7527463953198145 0,70=0.3267024714409312:-0.29197446867499977 5,55=0.0:0.4 5,63=0.24114215507697453:-0.08635601529187903 5,70=0.027997846063853395:0.339412288707659 10,55=0.046032769562509755:0.3079344608744661 10,63=0.4314748701443625:-0.46294974029354674 10,70=0.1507050052492246:0.09858998949986669 15,55=0.27261491228791296:-0.14522982457887257 15,63=0.5404085873542597:-0.6808171747145589 15,70=0.19691171361735882:0.006176572763081756",
        "LAKECHUNK seed=-7 chunk=-120,-100 lakeColumns=256 densityColumns=256 multiRiver=0 opened=3633 weights=7731538218572741649L noise=-627604248956632207L density=-872545731233284399L open=3440020087135083696L samples=0,55=0.384029039504007:-0.36805807901087506 0,63=0.7495914302733362:-1.0 0,70=0.39959142505662604:-0.399182850116229 5,55=0.25819036898396774:-0.11638073796985904 5,63=0.6581903749459223:-0.9163807498967481 5,70=0.38436753875708013:-0.36873507751702383 10,55=0.0:0.4 10,63=0.1501675837002902:0.09966483259830083 10,70=0.0:0.4 15,55=0.0:0.4 15,63=0.16535061634175366:0.06929876731526091 15,70=0.0:0.4",
        "LAKECHUNK seed=-7 chunk=-125,-105 lakeColumns=256 densityColumns=256 multiRiver=42 opened=330 weights=6030900550650023183L noise=-385429252127605472L density=505418330202937853L open=-6152303808364056390L samples=0,55=0.04828635994432624:0.3034272801109878 0,63=0.10603658223998486:0.18792683551924033 0,70=0.062215744544426216:-1.0 5,55=0.07070086079962176:0.25859827840022975 5,63=0.13116748184426436:0.1376650363104941 5,70=0.062179188309731:-0.7243583915210864 10,55=0.08610503492116016:0.22778993015703822 10,63=0.16841486986907026:0.0631702602606048 10,70=0.07995742928335019:-0.9599148764486894 15,55=0.11198049204240729:0.17603901591435123 15,63=0.2182122638041684:-0.03642452760996234 15,70=0.11262776070389814:-0.6252555333295642",
        "LAKECHUNK seed=-7 chunk=-185,-160 lakeColumns=256 densityColumns=256 multiRiver=0 opened=2809 weights=7731538218572741649L noise=-5736529997897597175L density=2937430107925965991L open=2282230508283852095L samples=0,55=0.09030314763320511:0.21939370473291703 0,63=0.48659944983625264:-0.5731988996761305 0,70=0.33833844845903227:-0.27667689692058517 5,55=0.19873408022342612:0.002531839551667303 5,63=0.5967499591716804:-0.7934999183478063 5,70=0.3249156359287012:-0.2498312718598229 10,55=0.0:0.4 10,63=0.3580497393423557:-0.31609947868737887 10,70=0.008049734125645478:0.3839005317486491 15,55=0.24685388082522347:-0.093707761652286 15,63=0.6468538867871779:-0.8937077735791749 15,70=0.32706186169982193:-0.25412372340208045",
        "LAKECHUNK seed=-7 chunk=-185,-155 lakeColumns=256 densityColumns=256 multiRiver=0 opened=2874 weights=7731538218572741649L noise=272577357362630249L density=4664516057938477719L open=-8427491966626390026L samples=0,55=0.34050840348889705:-0.2810168069803308 0,63=0.7405084094508516:-1.0 0,70=0.45695268894831703:-0.7139053808802706 5,55=0.266818864133512:-0.13363772826901188 5,63=0.6668188700954665:-0.9336377401959007 5,70=0.5015445813619305:-1.0 10,55=0.26103955300308473:-0.12207910600811422 10,63=0.645214781841279:-0.8904295636873648 10,70=0.29521477662456885:-1.0 15,55=0.3433713450052914:-0.2867426900131409 15,63=0.6644181015280514:-0.9288362030610527 15,70=0.3144180963113411:-1.0",
        "LAKECHUNK seed=20260917 chunk=50,45 lakeColumns=0 densityColumns=256 multiRiver=256 opened=725 weights=-6986919089091872132L noise=-4708107211269053423L density=-787847993005851488L open=707563813973394238L samples=",
        "LAKECHUNK seed=20260917 chunk=50,55 lakeColumns=0 densityColumns=256 multiRiver=256 opened=515 weights=930645336615993592L noise=-4708107211269053423L density=6930889344087998658L open=6748772905815985228L samples=",
        "LAKECHUNK seed=20260917 chunk=55,0 lakeColumns=0 densityColumns=256 multiRiver=0 opened=0 weights=3287218962338315281L noise=-4708107211269053423L density=-3871872201468850361L open=17L samples=",
        "LAKECHUNK seed=20260917 chunk=60,110 lakeColumns=256 densityColumns=256 multiRiver=0 opened=2885 weights=7731538218572741649L noise=-863818363315878384L density=-7685895001598959102L open=-6781362224877409992L samples=0,55=0.0:0.4 0,63=0.2550966408833556:-0.11019328176861165 0,70=0.20977154051123215:-0.019543081024027076 5,55=0.0:0.4 5,63=0.1859046360134929:0.028190727971629304 5,70=0.0:0.4 10,55=0.39364208122701166:-0.38728416245695596 10,63=0.6294307400224817:-0.8588614800496527 10,70=0.27943073480577146:-0.15886146961362468 15,55=0.3734883274671687:-0.34697665493711993 15,63=0.7489045105739193:-1.0 15,70=0.3989045053572091:-0.39780901071739005",
        "LAKECHUNK seed=20260917 chunk=65,115 lakeColumns=256 densityColumns=256 multiRiver=0 opened=2875 weights=4945989069424200345L noise=-1871050352100188226L density=5899444024417557644L open=-163325964931262557L samples=0,55=0.22215577099042091:-0.04431154198332443 0,63=0.6221557769523754:-0.8443115539117038 0,70=0.32308250551169015:-0.24616501102699073 5,55=0.1707545784253564:0.058589659454338805 5,63=0.5573913840810965:-0.7144602037100393 5,70=0.20759392516370148:-0.015067714956363276 10,55=0.0:0.4 10,63=0.19896260867549503:0.011943959664493797 10,70=0.0:0.4 15,55=0.1851987382989269:0.07255681079272802 15,63=0.39775929982957275:-0.3032638281953214 15,70=0.1289369578598435:0.17203122939118093",
        "LAKECHUNK seed=20260917 chunk=85,180 lakeColumns=256 densityColumns=256 multiRiver=0 opened=1349 weights=3914422309438909617L noise=316589076333855767L density=-729949556606914677L open=2654396890715119718L samples=0,55=0.025385347883776225:0.3492293042321639 0,63=0.047536321692887004:0.3049273566136948 0,70=0.023230765775059913:0.35353846844962056 5,55=0.09472883069186044:0.21054233861522054 5,63=0.16487794670143255:0.07024410659529245 5,70=0.07510653332636284:0.249786933346435 10,55=0.12310280913346226:0.1586352590081167 10,63=0.29747219509922607:-0.18324663610698155 10,70=0.11529626645798877:0.17394136099044402 15,55=0.08890761633577958:0.24161860806765503 15,63=0.40147375856067985:-0.31519151368319187 15,70=0.19507938990077495:0.052482581547253215",
        "LAKECHUNK seed=20260917 chunk=90,180 lakeColumns=256 densityColumns=256 multiRiver=0 opened=4640 weights=7731538218572741649L noise=1492650983126657999L density=-7776686562093992985L open=3671066423170821159L samples=0,55=0.21123455183636178:-0.022469103675084034 0,63=0.6010671436968057:-0.8021342874003284 0,70=0.25106713848009554:-0.10213427696299682 5,55=0.40792800200030166:-0.415856004005162 5,63=0.6230167064543431:-0.8460334129156485 5,70=0.27301670123763305:-0.1460334024783171 10,55=0.4039774090659379:-0.40795481813639023 10,63=0.567327421418864:-0.7346548428440681 10,70=0.21732741620215387:-0.03465483240673639 15,55=0.2984391823189524:-0.19687836464123976 15,63=0.6829757762424126:-0.9659515524924575 15,70=0.3329757710257024:-0.26595154205512583",
        "LAKECHUNK seed=20260917 chunk=95,180 lakeColumns=256 densityColumns=256 multiRiver=0 opened=2363 weights=260772831106080593L noise=8919167435442585683L density=-5414299047145196412L open=-5193766703529433271L samples=0,55=0.26116966188296153:-0.06032880219696202 0,63=0.5661961214558011:-0.5979581109046045 0,70=0.311327460219431:-0.14873523908016595 5,55=0.0:0.4 5,63=0.1053666760100755:0.20093485751211637 5,70=0.0:0.4 10,55=0.0:0.4 10,63=0.25872142876970305:-0.10721891219236013 10,70=0.05384788870010836:0.29443214015860975 15,55=0.31325207126297505:-0.2243935238283823 15,63=0.7105569713646535:-1.0 15,70=0.3653706869917254:-0.3282795923249969",
        "LAKECHUNK seed=20260917 chunk=-25,185 lakeColumns=256 densityColumns=256 multiRiver=0 opened=1561 weights=7731538218572741649L noise=-8401195122676301953L density=-4782381961692366487L open=-2592101449528334617L samples=0,55=0.0:0.4 0,63=0.011785674483691673:0.37642865103252887 0,70=0.0:0.4 5,55=0.03631998092348004:0.3273600381527694 5,63=0.1255799970134543:0.1488400059721559 5,70=0.03971483369660679:0.3205703326064906 10,55=0.18538969705284672:0.029220605892925522 10,63=0.3622321338923193:-0.32446426778733706 10,70=0.1926719461798589:0.014656107638846894 15,55=0.2509736009293658:-0.10194720186060124 15,63=0.4711032174890921:-0.5422064349816937 15,70=0.21253839882039818:-0.02507679764237969"
    );

    private static String field(String line, String key) {
        for (String part : line.split(" ")) {
            if (part.startsWith(key + "=")) {
                return part.substring(key.length() + 1);
            }
        }
        throw new IllegalArgumentException(key + " not in " + line);
    }

    private static long hashField(String line, String key) {
        final String v = field(line, key);
        return Long.parseLong(v.endsWith("L") ? v.substring(0, v.length() - 1) : v);
    }

    @Test
    void lakeSamplerMatchesTfc() {
        for (String line : SAMPLERS) {
            final long seed = Long.parseLong(field(line, "seed"));
            final LakeCarving lakes = new LakeCarving(seed);
            final BiomeWeights weights = new BiomeWeights();
            weights.put(TfcBiomes.MOUNTAIN_LAKE, 1.0);
            long h = 17;
            for (int i = 0; i < 64; i++) {
                final int x = (i * 7919) % 6000 - 3000;
                final int z = (i * 104729) % 9000 - 4500;
                final LakeCarving.Column column = lakes.column(weights, x, z);
                for (int y = 30; y <= 100; y++) {
                    h = h * 1000003L + Double.doubleToLongBits(column.noise(y));
                }
            }
            assertThat(h).as(line).isEqualTo(hashField(line, "hash"));
            final String[] parts = line.split(" ");
            for (int p = 3; p < parts.length; p++) {
                final String[] kv = parts[p].split("=");
                final String[] xyz = kv[0].split(",");
                final LakeCarving.Column column = lakes.column(weights, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]));
                assertThat(column.noise(Integer.parseInt(xyz[2]))).as(parts[p]).isEqualTo(Double.parseDouble(kv[1]));
            }
        }
    }

    @Test
    void chunkDensityMatchesTfc() {
        int lakeChunks = 0;
        for (String line : CHUNKS) {
            final long seed = Long.parseLong(field(line, "seed"));
            final String[] chunk = field(line, "chunk").split(",");
            final int cx = Integer.parseInt(chunk[0]);
            final int cz = Integer.parseInt(chunk[1]);
            final SurfaceHeights.Chunk c = SurfaceHeightsGoldenTest.heights(seed).chunk(cx, cz);
            int lakeColumns = 0;
            int densityColumns = 0;
            int multiRiver = 0;
            int opened = 0;
            long weightHash = 17;
            long noiseHash = 17;
            long densityHash = 17;
            long openHash = 17;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    final SurfaceHeights.Column column = c.column(lx, lz);
                    int types = 0;
                    for (RiverBlendType type : RiverBlendType.ALL) {
                        final double w = column.river().weight(type);
                        if (type != RiverBlendType.NONE && w > 0) {
                            types++;
                        }
                        weightHash = weightHash * 1000003L + Math.round(w * 1e9);
                    }
                    if (column.river().hasRiver() && types >= 2) {
                        multiRiver++;
                    }
                    if (column.lake().present()) {
                        lakeColumns++;
                    }
                    boolean noShore = true;
                    for (int t = 1; t < ShoreBlendType.SIZE; t++) {
                        if (column.shoreWeights()[t] != 0) {
                            noShore = false;
                        }
                    }
                    if (noShore) {
                        densityColumns++;
                    }
                    final int surface = column.surfaceHeight();
                    for (int y = 0; y <= 140; y++) {
                        final double noise = column.lake().noise(y);
                        noiseHash = noiseHash * 1000003L + Math.round(noise * 1e9);
                        if (noShore) {
                            final double d = column.density(y);
                            densityHash = densityHash * 1000003L + Math.round(d * 1e9);
                            if (y <= surface && d <= 0) {
                                opened++;
                                openHash = openHash * 1000003L + (y + 1000L * (lx + 16 * lz));
                                assertThat(column.opens(y)).as("opens agrees with the density").isTrue();
                            }
                        }
                    }
                }
            }
            if (lakeColumns > 0) {
                lakeChunks++;
            }
            assertThat(lakeColumns).as(line).isEqualTo(Integer.parseInt(field(line, "lakeColumns")));
            assertThat(densityColumns).as(line).isEqualTo(Integer.parseInt(field(line, "densityColumns")));
            assertThat(multiRiver).as(line).isEqualTo(Integer.parseInt(field(line, "multiRiver")));
            assertThat(weightHash).as("river blend weights " + line).isEqualTo(hashField(line, "weights"));
            assertThat(noiseHash).as("biome stage " + line).isEqualTo(hashField(line, "noise"));
            assertThat(densityHash).as("density " + line).isEqualTo(hashField(line, "density"));
            assertThat(opened).as(line).isEqualTo(Integer.parseInt(field(line, "opened")));
            assertThat(openHash).as("opened positions " + line).isEqualTo(hashField(line, "open"));

            final String samples = field(line, "samples");
            if (!samples.isEmpty()) {
                for (String sample : line.substring(line.indexOf("samples=") + 8).split(" ")) {
                    final String[] kv = sample.split("=");
                    final String[] xy = kv[0].split(",");
                    final int l = Integer.parseInt(xy[0]);
                    final int y = Integer.parseInt(xy[1]);
                    final String[] values = kv[1].split(":");
                    final SurfaceHeights.Column column = c.column(l, l);
                    assertThat(column.lake().noise(y)).as(sample).isCloseTo(Double.parseDouble(values[0]), within(1e-12));
                    if (!values[1].equals("S")) {
                        assertThat(column.density(y)).as(sample).isCloseTo(Double.parseDouble(values[1]), within(1e-12));
                    }
                }
            }
        }
        assertThat(lakeChunks).as("chunks with carved lake weights").isGreaterThanOrEqualTo(15);
    }
}
