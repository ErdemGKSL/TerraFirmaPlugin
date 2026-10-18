/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (getReadyTicks, randomTick,
 * use, extract, getRottenCount, finishUse, getCompost), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536
 * (composterTicks)
 *
 * The expected values below were worked out by a scratch mirror of TFC's composter arithmetic at c3b4d9536 (float32 where TFC
 * uses float, long truncation where TFC casts). They are pasted here as constants and are not computed by this code.
 * Codes: state N = NORMAL, Y = READY, X = ROTTEN.
 */
package dev.tfp.core.compost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compost.CompostBin.Outcome;
import dev.tfp.core.compost.CompostBin.Type;
import dev.tfp.core.compost.CompostAddition.Kind;
import dev.tfp.core.compost.CompostAddition.Tier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompostGoldenTest {

    /** readyTicks rows: base, rainfall, adjacent composters, expected. */
    private static final String READY_TICKS = """
        288000 0.0 0 1152000
        288000 0.0 1 1209600
        288000 0.0 2 1270080
        288000 0.0 3 1333584
        288000 0.0 4 1400263
        288000 1.0 0 864000
        288000 1.0 1 907199
        288000 1.0 2 952558
        288000 1.0 3 1000185
        288000 1.0 4 1050194
        288000 49.5 0 864000
        288000 49.5 1 907199
        288000 49.5 2 952558
        288000 49.5 3 1000185
        288000 49.5 4 1050194
        288000 50.0 0 864000
        288000 50.0 1 907199
        288000 50.0 2 952558
        288000 50.0 3 1000185
        288000 50.0 4 1050194
        288000 100.0 0 576000
        288000 100.0 1 604800
        288000 100.0 2 635040
        288000 100.0 3 666792
        288000 100.0 4 700131
        288000 149.0 0 288000
        288000 149.0 1 302400
        288000 149.0 2 317520
        288000 149.0 3 333396
        288000 149.0 4 350065
        288000 150.0 0 288000
        288000 150.0 1 302400
        288000 150.0 2 317520
        288000 150.0 3 333396
        288000 150.0 4 350065
        288000 151.0 0 288000
        288000 151.0 1 302400
        288000 151.0 2 317520
        288000 151.0 3 333396
        288000 151.0 4 350065
        288000 199.9 0 288000
        288000 199.9 1 302400
        288000 199.9 2 317520
        288000 199.9 3 333396
        288000 199.9 4 350065
        288000 250.0 0 288000
        288000 250.0 1 302400
        288000 250.0 2 317520
        288000 250.0 3 333396
        288000 250.0 4 350065
        288000 350.0 0 288000
        288000 350.0 1 302400
        288000 350.0 2 317520
        288000 350.0 3 333396
        288000 350.0 4 350065
        288000 351.0 0 288000
        288000 351.0 1 302400
        288000 351.0 2 317520
        288000 351.0 3 333396
        288000 351.0 4 350065
        288000 400.0 0 576000
        288000 400.0 1 604800
        288000 400.0 2 635040
        288000 400.0 3 666792
        288000 400.0 4 700131
        288000 499.5 0 864000
        288000 499.5 1 907199
        288000 499.5 2 952558
        288000 499.5 3 1000185
        288000 499.5 4 1050194
        288000 500.0 0 1152000
        288000 500.0 1 1209600
        288000 500.0 2 1270080
        288000 500.0 3 1333584
        288000 500.0 4 1400263
        288000 1000.0 0 4032000
        288000 1000.0 1 4233600
        288000 1000.0 2 4445280
        288000 1000.0 3 4667544
        288000 1000.0 4 4900921
        1000 0.0 0 4000
        1000 0.0 1 4200
        1000 0.0 2 4410
        1000 0.0 3 4630
        1000 0.0 4 4861
        1000 1.0 0 3000
        1000 1.0 1 3149
        1000 1.0 2 3306
        1000 1.0 3 3471
        1000 1.0 4 3644
        1000 49.5 0 3000
        1000 49.5 1 3149
        1000 49.5 2 3306
        1000 49.5 3 3471
        1000 49.5 4 3644
        1000 50.0 0 3000
        1000 50.0 1 3149
        1000 50.0 2 3306
        1000 50.0 3 3471
        1000 50.0 4 3644
        1000 100.0 0 2000
        1000 100.0 1 2100
        1000 100.0 2 2205
        1000 100.0 3 2315
        1000 100.0 4 2430
        1000 149.0 0 1000
        1000 149.0 1 1050
        1000 149.0 2 1102
        1000 149.0 3 1157
        1000 149.0 4 1214
        1000 150.0 0 1000
        1000 150.0 1 1050
        1000 150.0 2 1102
        1000 150.0 3 1157
        1000 150.0 4 1214
        1000 151.0 0 1000
        1000 151.0 1 1050
        1000 151.0 2 1102
        1000 151.0 3 1157
        1000 151.0 4 1214
        1000 199.9 0 1000
        1000 199.9 1 1050
        1000 199.9 2 1102
        1000 199.9 3 1157
        1000 199.9 4 1214
        1000 250.0 0 1000
        1000 250.0 1 1050
        1000 250.0 2 1102
        1000 250.0 3 1157
        1000 250.0 4 1214
        1000 350.0 0 1000
        1000 350.0 1 1050
        1000 350.0 2 1102
        1000 350.0 3 1157
        1000 350.0 4 1214
        1000 351.0 0 1000
        1000 351.0 1 1050
        1000 351.0 2 1102
        1000 351.0 3 1157
        1000 351.0 4 1214
        1000 400.0 0 2000
        1000 400.0 1 2100
        1000 400.0 2 2205
        1000 400.0 3 2315
        1000 400.0 4 2430
        1000 499.5 0 3000
        1000 499.5 1 3149
        1000 499.5 2 3306
        1000 499.5 3 3471
        1000 499.5 4 3644
        1000 500.0 0 4000
        1000 500.0 1 4200
        1000 500.0 2 4410
        1000 500.0 3 4630
        1000 500.0 4 4861
        1000 1000.0 0 14000
        1000 1000.0 1 14699
        1000 1000.0 2 15433
        1000 1000.0 3 16204
        1000 1000.0 4 17014
        20 0.0 0 80
        20 0.0 1 84
        20 0.0 2 88
        20 0.0 3 92
        20 0.0 4 96
        20 1.0 0 60
        20 1.0 1 62
        20 1.0 2 65
        20 1.0 3 68
        20 1.0 4 71
        20 49.5 0 60
        20 49.5 1 62
        20 49.5 2 65
        20 49.5 3 68
        20 49.5 4 71
        20 50.0 0 60
        20 50.0 1 62
        20 50.0 2 65
        20 50.0 3 68
        20 50.0 4 71
        20 100.0 0 40
        20 100.0 1 42
        20 100.0 2 44
        20 100.0 3 46
        20 100.0 4 48
        20 149.0 0 20
        20 149.0 1 21
        20 149.0 2 22
        20 149.0 3 23
        20 149.0 4 24
        20 150.0 0 20
        20 150.0 1 21
        20 150.0 2 22
        20 150.0 3 23
        20 150.0 4 24
        20 151.0 0 20
        20 151.0 1 21
        20 151.0 2 22
        20 151.0 3 23
        20 151.0 4 24
        20 199.9 0 20
        20 199.9 1 21
        20 199.9 2 22
        20 199.9 3 23
        20 199.9 4 24
        20 250.0 0 20
        20 250.0 1 21
        20 250.0 2 22
        20 250.0 3 23
        20 250.0 4 24
        20 350.0 0 20
        20 350.0 1 21
        20 350.0 2 22
        20 350.0 3 23
        20 350.0 4 24
        20 351.0 0 20
        20 351.0 1 21
        20 351.0 2 22
        20 351.0 3 23
        20 351.0 4 24
        20 400.0 0 40
        20 400.0 1 42
        20 400.0 2 44
        20 400.0 3 46
        20 400.0 4 48
        20 499.5 0 60
        20 499.5 1 62
        20 499.5 2 65
        20 499.5 3 68
        20 499.5 4 71
        20 500.0 0 80
        20 500.0 1 84
        20 500.0 2 88
        20 500.0 3 92
        20 500.0 4 96
        20 1000.0 0 280
        20 1000.0 1 294
        20 1000.0 2 308
        20 1000.0 3 323
        20 1000.0 4 339
        """;

    /**
     * Settle rows: g b state out readyTicks elapsed | g' b' state' out' lastUpdate' ripenedAt. The bin was last updated at
     * tick 1000 and is settled at 1000 + elapsed. ripenedAt is the first tick the full normal bin ripens ('-' otherwise).
     */
    private static final String SETTLE = """
        16 16 N 0 20 19 | 16 16 N 0 1000 1021
        16 16 N 0 20 20 | 16 16 N 0 1000 1021
        16 16 N 0 20 21 | 16 16 Y 1 1000 1021
        16 16 N 0 20 40 | 16 16 Y 1 1000 1021
        16 16 N 0 288000 287999 | 16 16 N 0 1000 289001
        16 16 N 0 288000 288000 | 16 16 N 0 1000 289001
        16 16 N 0 288000 288001 | 16 16 Y 1 1000 289001
        16 16 N 0 288000 576000 | 16 16 Y 1 1000 289001
        16 15 N 0 20 19 | 16 15 N 0 1000 -
        16 15 N 0 20 20 | 16 15 N 0 1000 -
        16 15 N 0 20 21 | 16 15 N 0 1000 -
        16 15 N 0 20 40 | 16 15 N 0 1000 -
        16 15 N 0 288000 287999 | 16 15 N 0 1000 -
        16 15 N 0 288000 288000 | 16 15 N 0 1000 -
        16 15 N 0 288000 288001 | 16 15 N 0 1000 -
        16 15 N 0 288000 576000 | 16 15 N 0 1000 -
        16 16 X 4 20 19 | 16 16 X 4 1000 -
        16 16 X 4 20 20 | 16 16 X 4 1000 -
        16 16 X 4 20 21 | 16 16 X 4 1000 -
        16 16 X 4 20 40 | 16 16 X 4 1000 -
        16 16 X 4 288000 287999 | 16 16 X 4 1000 -
        16 16 X 4 288000 288000 | 16 16 X 4 1000 -
        16 16 X 4 288000 288001 | 16 16 X 4 1000 -
        16 16 X 4 288000 576000 | 16 16 X 4 1000 -
        16 16 Y 1 20 19 | 16 16 Y 1 1000 -
        16 16 Y 1 20 20 | 16 16 Y 1 1000 -
        16 16 Y 1 20 21 | 16 16 Y 1 1000 -
        16 16 Y 1 20 40 | 16 16 Y 1 1000 -
        16 16 Y 1 288000 287999 | 16 16 Y 1 1000 -
        16 16 Y 1 288000 288000 | 16 16 Y 1 1000 -
        16 16 Y 1 288000 288001 | 16 16 Y 1 1000 -
        16 16 Y 1 288000 576000 | 16 16 Y 1 1000 -
        16 16 Y 0 20 19 | 0 0 N 0 1019 -
        16 16 Y 0 20 20 | 0 0 N 0 1020 -
        16 16 Y 0 20 21 | 0 0 N 0 1021 -
        16 16 Y 0 20 40 | 0 0 N 0 1040 -
        16 16 Y 0 288000 287999 | 0 0 N 0 288999 -
        16 16 Y 0 288000 288000 | 0 0 N 0 289000 -
        16 16 Y 0 288000 288001 | 0 0 N 0 289001 -
        16 16 Y 0 288000 576000 | 0 0 N 0 577000 -
        4 16 N 0 20 19 | 4 16 N 0 1000 -
        4 16 N 0 20 20 | 4 16 N 0 1000 -
        4 16 N 0 20 21 | 4 16 N 0 1000 -
        4 16 N 0 20 40 | 4 16 N 0 1000 -
        4 16 N 0 288000 287999 | 4 16 N 0 1000 -
        4 16 N 0 288000 288000 | 4 16 N 0 1000 -
        4 16 N 0 288000 288001 | 4 16 N 0 1000 -
        4 16 N 0 288000 576000 | 4 16 N 0 1000 -
        0 0 N 0 20 19 | 0 0 N 0 1000 -
        0 0 N 0 20 20 | 0 0 N 0 1000 -
        0 0 N 0 20 21 | 0 0 N 0 1000 -
        0 0 N 0 20 40 | 0 0 N 0 1000 -
        0 0 N 0 288000 287999 | 0 0 N 0 1000 -
        0 0 N 0 288000 288000 | 0 0 N 0 1000 -
        0 0 N 0 288000 288001 | 0 0 N 0 1000 -
        0 0 N 0 288000 576000 | 0 0 N 0 1000 -
        8 8 X 2 20 19 | 8 8 X 2 1000 -
        8 8 X 2 20 20 | 8 8 X 2 1000 -
        8 8 X 2 20 21 | 8 8 X 2 1000 -
        8 8 X 2 20 40 | 8 8 X 2 1000 -
        8 8 X 2 288000 287999 | 8 8 X 2 1000 -
        8 8 X 2 288000 288000 | 8 8 X 2 1000 -
        8 8 X 2 288000 288001 | 8 8 X 2 1000 -
        8 8 X 2 288000 576000 | 8 8 X 2 1000 -
        """;

    /**
     * Interaction rows: g b state out | kind amount creative | g' b' state' out' lastUpdate' outcome consumed. Each row is one
     * interaction at tick 5000 on a bin last updated at tick 1000 (already settled).
     */
    private static final String ADD = """
        0 0 N 0 GREEN 1 0 | 1 0 N 0 5000 ADDED_GREEN 1
        0 0 N 0 GREEN 2 0 | 2 0 N 0 5000 ADDED_GREEN 1
        0 0 N 0 GREEN 4 0 | 4 0 N 0 5000 ADDED_GREEN 1
        1 0 N 0 GREEN 1 0 | 2 0 N 0 5000 ADDED_GREEN 1
        1 0 N 0 GREEN 2 0 | 3 0 N 0 5000 ADDED_GREEN 1
        1 0 N 0 GREEN 4 0 | 5 0 N 0 5000 ADDED_GREEN 1
        2 0 N 0 GREEN 1 0 | 3 0 N 0 5000 ADDED_GREEN 1
        2 0 N 0 GREEN 2 0 | 4 0 N 0 5000 ADDED_GREEN 1
        2 0 N 0 GREEN 4 0 | 6 0 N 0 5000 ADDED_GREEN 1
        3 0 N 0 GREEN 1 0 | 4 0 N 0 5000 ADDED_GREEN 1
        3 0 N 0 GREEN 2 0 | 5 0 N 0 5000 ADDED_GREEN 1
        3 0 N 0 GREEN 4 0 | 7 0 N 0 5000 ADDED_GREEN 1
        4 0 N 0 GREEN 1 0 | 5 0 N 0 5000 ADDED_GREEN 1
        4 0 N 0 GREEN 2 0 | 6 0 N 0 5000 ADDED_GREEN 1
        4 0 N 0 GREEN 4 0 | 8 0 N 0 5000 ADDED_GREEN 1
        5 0 N 0 GREEN 1 0 | 6 0 N 0 5000 ADDED_GREEN 1
        5 0 N 0 GREEN 2 0 | 7 0 N 0 5000 ADDED_GREEN 1
        5 0 N 0 GREEN 4 0 | 9 0 N 0 5000 ADDED_GREEN 1
        6 0 N 0 GREEN 1 0 | 7 0 N 0 5000 ADDED_GREEN 1
        6 0 N 0 GREEN 2 0 | 8 0 N 0 5000 ADDED_GREEN 1
        6 0 N 0 GREEN 4 0 | 10 0 N 0 5000 ADDED_GREEN 1
        7 0 N 0 GREEN 1 0 | 8 0 N 0 5000 ADDED_GREEN 1
        7 0 N 0 GREEN 2 0 | 9 0 N 0 5000 ADDED_GREEN 1
        7 0 N 0 GREEN 4 0 | 11 0 N 0 5000 ADDED_GREEN 1
        8 0 N 0 GREEN 1 0 | 9 0 N 0 5000 ADDED_GREEN 1
        8 0 N 0 GREEN 2 0 | 10 0 N 0 5000 ADDED_GREEN 1
        8 0 N 0 GREEN 4 0 | 12 0 N 0 5000 ADDED_GREEN 1
        9 0 N 0 GREEN 1 0 | 10 0 N 0 5000 ADDED_GREEN 1
        9 0 N 0 GREEN 2 0 | 11 0 N 0 5000 ADDED_GREEN 1
        9 0 N 0 GREEN 4 0 | 13 0 N 0 5000 ADDED_GREEN 1
        10 0 N 0 GREEN 1 0 | 11 0 N 0 5000 ADDED_GREEN 1
        10 0 N 0 GREEN 2 0 | 12 0 N 0 5000 ADDED_GREEN 1
        10 0 N 0 GREEN 4 0 | 14 0 N 0 5000 ADDED_GREEN 1
        11 0 N 0 GREEN 1 0 | 12 0 N 0 5000 ADDED_GREEN 1
        11 0 N 0 GREEN 2 0 | 13 0 N 0 5000 ADDED_GREEN 1
        11 0 N 0 GREEN 4 0 | 15 0 N 0 5000 ADDED_GREEN 1
        12 0 N 0 GREEN 1 0 | 13 0 N 0 5000 ADDED_GREEN 1
        12 0 N 0 GREEN 2 0 | 14 0 N 0 5000 ADDED_GREEN 1
        12 0 N 0 GREEN 4 0 | 16 0 N 0 5000 ADDED_GREEN 1
        13 0 N 0 GREEN 1 0 | 14 0 N 0 5000 ADDED_GREEN 1
        13 0 N 0 GREEN 2 0 | 15 0 N 0 5000 ADDED_GREEN 1
        13 0 N 0 GREEN 4 0 | 16 0 N 0 5000 ADDED_GREEN 1
        14 0 N 0 GREEN 1 0 | 15 0 N 0 5000 ADDED_GREEN 1
        14 0 N 0 GREEN 2 0 | 16 0 N 0 5000 ADDED_GREEN 1
        14 0 N 0 GREEN 4 0 | 16 0 N 0 5000 ADDED_GREEN 1
        15 0 N 0 GREEN 1 0 | 16 0 N 0 5000 ADDED_GREEN 1
        15 0 N 0 GREEN 2 0 | 16 0 N 0 5000 ADDED_GREEN 1
        15 0 N 0 GREEN 4 0 | 16 0 N 0 5000 ADDED_GREEN 1
        16 0 N 0 GREEN 1 0 | 16 0 N 0 1000 FULL_GREEN 0
        16 0 N 0 GREEN 2 0 | 16 0 N 0 1000 FULL_GREEN 0
        16 0 N 0 GREEN 4 0 | 16 0 N 0 1000 FULL_GREEN 0
        0 0 N 0 BROWN 1 0 | 0 1 N 0 5000 ADDED_BROWN 1
        0 0 N 0 BROWN 2 0 | 0 2 N 0 5000 ADDED_BROWN 1
        0 0 N 0 BROWN 4 0 | 0 4 N 0 5000 ADDED_BROWN 1
        0 1 N 0 BROWN 1 0 | 0 2 N 0 5000 ADDED_BROWN 1
        0 1 N 0 BROWN 2 0 | 0 3 N 0 5000 ADDED_BROWN 1
        0 1 N 0 BROWN 4 0 | 0 5 N 0 5000 ADDED_BROWN 1
        0 2 N 0 BROWN 1 0 | 0 3 N 0 5000 ADDED_BROWN 1
        0 2 N 0 BROWN 2 0 | 0 4 N 0 5000 ADDED_BROWN 1
        0 2 N 0 BROWN 4 0 | 0 6 N 0 5000 ADDED_BROWN 1
        0 3 N 0 BROWN 1 0 | 0 4 N 0 5000 ADDED_BROWN 1
        0 3 N 0 BROWN 2 0 | 0 5 N 0 5000 ADDED_BROWN 1
        0 3 N 0 BROWN 4 0 | 0 7 N 0 5000 ADDED_BROWN 1
        0 4 N 0 BROWN 1 0 | 0 5 N 0 5000 ADDED_BROWN 1
        0 4 N 0 BROWN 2 0 | 0 6 N 0 5000 ADDED_BROWN 1
        0 4 N 0 BROWN 4 0 | 0 8 N 0 5000 ADDED_BROWN 1
        0 5 N 0 BROWN 1 0 | 0 6 N 0 5000 ADDED_BROWN 1
        0 5 N 0 BROWN 2 0 | 0 7 N 0 5000 ADDED_BROWN 1
        0 5 N 0 BROWN 4 0 | 0 9 N 0 5000 ADDED_BROWN 1
        0 6 N 0 BROWN 1 0 | 0 7 N 0 5000 ADDED_BROWN 1
        0 6 N 0 BROWN 2 0 | 0 8 N 0 5000 ADDED_BROWN 1
        0 6 N 0 BROWN 4 0 | 0 10 N 0 5000 ADDED_BROWN 1
        0 7 N 0 BROWN 1 0 | 0 8 N 0 5000 ADDED_BROWN 1
        0 7 N 0 BROWN 2 0 | 0 9 N 0 5000 ADDED_BROWN 1
        0 7 N 0 BROWN 4 0 | 0 11 N 0 5000 ADDED_BROWN 1
        0 8 N 0 BROWN 1 0 | 0 9 N 0 5000 ADDED_BROWN 1
        0 8 N 0 BROWN 2 0 | 0 10 N 0 5000 ADDED_BROWN 1
        0 8 N 0 BROWN 4 0 | 0 12 N 0 5000 ADDED_BROWN 1
        0 9 N 0 BROWN 1 0 | 0 10 N 0 5000 ADDED_BROWN 1
        0 9 N 0 BROWN 2 0 | 0 11 N 0 5000 ADDED_BROWN 1
        0 9 N 0 BROWN 4 0 | 0 13 N 0 5000 ADDED_BROWN 1
        0 10 N 0 BROWN 1 0 | 0 11 N 0 5000 ADDED_BROWN 1
        0 10 N 0 BROWN 2 0 | 0 12 N 0 5000 ADDED_BROWN 1
        0 10 N 0 BROWN 4 0 | 0 14 N 0 5000 ADDED_BROWN 1
        0 11 N 0 BROWN 1 0 | 0 12 N 0 5000 ADDED_BROWN 1
        0 11 N 0 BROWN 2 0 | 0 13 N 0 5000 ADDED_BROWN 1
        0 11 N 0 BROWN 4 0 | 0 15 N 0 5000 ADDED_BROWN 1
        0 12 N 0 BROWN 1 0 | 0 13 N 0 5000 ADDED_BROWN 1
        0 12 N 0 BROWN 2 0 | 0 14 N 0 5000 ADDED_BROWN 1
        0 12 N 0 BROWN 4 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 13 N 0 BROWN 1 0 | 0 14 N 0 5000 ADDED_BROWN 1
        0 13 N 0 BROWN 2 0 | 0 15 N 0 5000 ADDED_BROWN 1
        0 13 N 0 BROWN 4 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 14 N 0 BROWN 1 0 | 0 15 N 0 5000 ADDED_BROWN 1
        0 14 N 0 BROWN 2 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 14 N 0 BROWN 4 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 15 N 0 BROWN 1 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 15 N 0 BROWN 2 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 15 N 0 BROWN 4 0 | 0 16 N 0 5000 ADDED_BROWN 1
        0 16 N 0 BROWN 1 0 | 0 16 N 0 1000 FULL_BROWN 0
        0 16 N 0 BROWN 2 0 | 0 16 N 0 1000 FULL_BROWN 0
        0 16 N 0 BROWN 4 0 | 0 16 N 0 1000 FULL_BROWN 0
        0 3 X 0 GREEN 4 0 | 4 3 X 0 5000 ADDED_GREEN 1
        0 3 X 0 BROWN 1 0 | 0 4 X 0 5000 ADDED_BROWN 1
        0 3 X 0 NOTHING 0 0 | 0 3 X 0 1000 ROTTEN_REJECT 0
        1 3 X 0 GREEN 4 0 | 5 3 X 0 5000 ADDED_GREEN 1
        1 3 X 0 BROWN 1 0 | 1 4 X 0 5000 ADDED_BROWN 1
        1 3 X 0 NOTHING 0 0 | 1 3 X 0 1000 ROTTEN_REJECT 0
        2 3 X 0 GREEN 4 0 | 6 3 X 0 5000 ADDED_GREEN 1
        2 3 X 0 BROWN 1 0 | 2 4 X 0 5000 ADDED_BROWN 1
        2 3 X 0 NOTHING 0 0 | 2 3 X 0 1000 ROTTEN_REJECT 0
        3 3 X 0 GREEN 4 0 | 7 3 X 0 5000 ADDED_GREEN 1
        3 3 X 0 BROWN 1 0 | 3 4 X 0 5000 ADDED_BROWN 1
        3 3 X 0 NOTHING 0 0 | 3 3 X 0 1000 ROTTEN_REJECT 0
        4 3 X 0 GREEN 4 0 | 8 3 X 0 5000 ADDED_GREEN 1
        4 3 X 0 BROWN 1 0 | 4 4 X 1 5000 ADDED_BROWN 1
        4 3 X 0 NOTHING 0 0 | 4 3 X 0 1000 ROTTEN_REJECT 0
        5 3 X 0 GREEN 4 0 | 9 3 X 0 5000 ADDED_GREEN 1
        5 3 X 0 BROWN 1 0 | 5 4 X 1 5000 ADDED_BROWN 1
        5 3 X 0 NOTHING 0 0 | 5 3 X 0 1000 ROTTEN_REJECT 0
        6 3 X 0 GREEN 4 0 | 10 3 X 0 5000 ADDED_GREEN 1
        6 3 X 0 BROWN 1 0 | 6 4 X 1 5000 ADDED_BROWN 1
        6 3 X 0 NOTHING 0 0 | 6 3 X 0 1000 ROTTEN_REJECT 0
        7 3 X 0 GREEN 4 0 | 11 3 X 0 5000 ADDED_GREEN 1
        7 3 X 0 BROWN 1 0 | 7 4 X 1 5000 ADDED_BROWN 1
        7 3 X 0 NOTHING 0 0 | 7 3 X 0 1000 ROTTEN_REJECT 0
        8 3 X 0 GREEN 4 0 | 12 3 X 0 5000 ADDED_GREEN 1
        8 3 X 0 BROWN 1 0 | 8 4 X 1 5000 ADDED_BROWN 1
        8 3 X 0 NOTHING 0 0 | 8 3 X 0 1000 ROTTEN_REJECT 0
        9 3 X 0 GREEN 4 0 | 13 3 X 0 5000 ADDED_GREEN 1
        9 3 X 0 BROWN 1 0 | 9 4 X 1 5000 ADDED_BROWN 1
        9 3 X 0 NOTHING 0 0 | 9 3 X 0 1000 ROTTEN_REJECT 0
        10 3 X 0 GREEN 4 0 | 14 3 X 0 5000 ADDED_GREEN 1
        10 3 X 0 BROWN 1 0 | 10 4 X 1 5000 ADDED_BROWN 1
        10 3 X 0 NOTHING 0 0 | 10 3 X 0 1000 ROTTEN_REJECT 0
        11 3 X 0 GREEN 4 0 | 15 3 X 0 5000 ADDED_GREEN 1
        11 3 X 0 BROWN 1 0 | 11 4 X 1 5000 ADDED_BROWN 1
        11 3 X 0 NOTHING 0 0 | 11 3 X 0 1000 ROTTEN_REJECT 0
        12 3 X 0 GREEN 4 0 | 16 3 X 0 5000 ADDED_GREEN 1
        12 3 X 0 BROWN 1 0 | 12 4 X 1 5000 ADDED_BROWN 1
        12 3 X 0 NOTHING 0 0 | 12 3 X 0 1000 ROTTEN_REJECT 0
        13 3 X 0 GREEN 4 0 | 16 3 X 0 5000 ADDED_GREEN 1
        13 3 X 0 BROWN 1 0 | 13 4 X 1 5000 ADDED_BROWN 1
        13 3 X 0 NOTHING 0 0 | 13 3 X 0 1000 ROTTEN_REJECT 0
        14 3 X 0 GREEN 4 0 | 16 3 X 0 5000 ADDED_GREEN 1
        14 3 X 0 BROWN 1 0 | 14 4 X 1 5000 ADDED_BROWN 1
        14 3 X 0 NOTHING 0 0 | 14 3 X 0 1000 ROTTEN_REJECT 0
        15 3 X 0 GREEN 4 0 | 16 3 X 0 5000 ADDED_GREEN 1
        15 3 X 0 BROWN 1 0 | 15 4 X 1 5000 ADDED_BROWN 1
        15 3 X 0 NOTHING 0 0 | 15 3 X 0 1000 ROTTEN_REJECT 0
        16 3 X 0 GREEN 4 0 | 16 3 X 0 1000 FULL_GREEN 0
        16 3 X 0 BROWN 1 0 | 16 4 X 1 5000 ADDED_BROWN 1
        16 3 X 0 NOTHING 0 0 | 16 3 X 0 1000 ROTTEN_REJECT 0
        0 0 N 0 POISON 0 0 | 0 0 X 0 1000 POISONED 1
        0 0 N 0 POISON 0 1 | 0 0 X 0 1000 POISONED 0
        3 4 N 0 POISON 0 0 | 3 4 X 0 1000 POISONED 1
        3 4 N 0 POISON 0 1 | 3 4 X 0 1000 POISONED 0
        4 8 N 0 POISON 0 0 | 4 8 X 1 1000 POISONED 1
        4 8 N 0 POISON 0 1 | 4 8 X 1 1000 POISONED 0
        15 16 N 0 POISON 0 0 | 15 16 X 3 1000 POISONED 1
        15 16 N 0 POISON 0 1 | 15 16 X 3 1000 POISONED 0
        16 16 N 0 POISON 0 0 | 16 16 X 4 1000 POISONED 1
        16 16 N 0 POISON 0 1 | 16 16 X 4 1000 POISONED 0
        16 16 Y 1 POISON 0 0 | 16 16 X 4 1000 POISONED 1
        16 16 Y 1 GREEN 4 0 | 16 16 Y 1 1000 FULL_GREEN 0
        16 16 Y 1 BROWN 4 0 | 16 16 Y 1 1000 FULL_BROWN 0
        16 16 X 4 POISON 0 0 | 16 16 X 4 1000 ROTTEN_REJECT 0
        16 16 X 4 NOTHING 0 0 | 16 16 X 4 1000 ROTTEN_REJECT 0
        0 0 N 0 NOTHING 0 0 | 0 0 N 0 1000 PASS 0
        16 0 N 0 GREEN 4 0 | 16 0 N 0 1000 FULL_GREEN 0
        0 16 N 0 BROWN 4 0 | 0 16 N 0 1000 FULL_BROWN 0
        0 0 N 0 GREEN 4 1 | 4 0 N 0 5000 ADDED_GREEN 0
        0 0 N 0 BROWN 2 1 | 0 2 N 0 5000 ADDED_BROWN 0
        """;

    /** Extraction rows: g b state out | compost rotten | g' b' state' out' lastUpdate' (reset at tick 5000). */
    private static final String EXTRACT = """
        16 16 Y 1 | 1 0 | 0 0 N 0 5000
        16 16 N 0 | 0 0 | 0 0 N 0 5000
        16 15 N 0 | 0 0 | 0 0 N 0 5000
        16 16 X 4 | 0 4 | 0 0 N 0 5000
        3 4 X 0 | 0 0 | 0 0 N 0 5000
        8 8 X 2 | 0 2 | 0 0 N 0 5000
        4 4 X 1 | 0 1 | 0 0 N 0 5000
        0 0 N 0 | 0 0 | 0 0 N 0 5000
        12 16 Y 0 | 0 0 | 0 0 N 0 5000
        """;

    /** Stage rows (17 rows, row = green, column = brown), then rotten-count rows (17 rows, same layout). */
    private static final String STAGE_AND_ROTTEN = """
        0 1 1 1 1 1 1 1 2 2 2 2 3 3 3 3 4
        1 1 1 1 1 1 1 2 2 2 2 3 3 3 3 4 4
        1 1 1 1 1 1 2 2 2 2 3 3 3 3 4 4 4
        1 1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4
        1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4 5
        1 1 1 2 2 2 2 3 3 3 3 4 4 4 4 5 5
        1 1 2 2 2 2 3 3 3 3 4 4 4 4 5 5 5
        1 2 2 2 2 3 3 3 3 4 4 4 4 5 5 5 5
        2 2 2 2 3 3 3 3 4 4 4 4 5 5 5 5 6
        2 2 2 3 3 3 3 4 4 4 4 5 5 5 5 6 6
        2 2 3 3 3 3 4 4 4 4 5 5 5 5 6 6 6
        2 3 3 3 3 4 4 4 4 5 5 5 5 6 6 6 6
        3 3 3 3 4 4 4 4 5 5 5 5 6 6 6 6 7
        3 3 3 4 4 4 4 5 5 5 5 6 6 6 6 7 7
        3 3 4 4 4 4 5 5 5 5 6 6 6 6 7 7 7
        3 4 4 4 4 5 5 5 5 6 6 6 6 7 7 7 7
        4 4 4 4 5 5 5 5 6 6 6 6 7 7 7 7 8
        0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
        0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
        0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
        0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
        0 0 0 0 1 1 1 1 1 1 1 1 1 1 1 1 1
        0 0 0 0 1 1 1 1 1 1 1 1 1 1 1 1 1
        0 0 0 0 1 1 1 1 1 1 1 1 1 1 1 1 1
        0 0 0 0 1 1 1 1 1 1 1 1 1 1 1 1 1
        0 0 0 0 1 1 1 1 2 2 2 2 2 2 2 2 2
        0 0 0 0 1 1 1 1 2 2 2 2 2 2 2 2 2
        0 0 0 0 1 1 1 1 2 2 2 2 2 2 2 2 2
        0 0 0 0 1 1 1 1 2 2 2 2 2 2 2 2 2
        0 0 0 0 1 1 1 1 2 2 2 2 3 3 3 3 3
        0 0 0 0 1 1 1 1 2 2 2 2 3 3 3 3 3
        0 0 0 0 1 1 1 1 2 2 2 2 3 3 3 3 3
        0 0 0 0 1 1 1 1 2 2 2 2 3 3 3 3 3
        0 0 0 0 1 1 1 1 2 2 2 2 3 3 3 3 4
        """;

    /** Classification rows: comma-separated tiers (or '-') rotten | kind amount. */
    private static final String CLASSIFY = """
        POISON 0 | POISON 0
        POISON 1 | POISON 0
        BROWN_LOW 0 | BROWN 1
        BROWN_LOW 1 | BROWN 1
        BROWN_MEDIUM 0 | BROWN 2
        BROWN_MEDIUM 1 | BROWN 2
        BROWN_HIGH 0 | BROWN 4
        BROWN_HIGH 1 | BROWN 2
        GREEN_LOW 0 | GREEN 1
        GREEN_LOW 1 | GREEN 1
        GREEN_MEDIUM 0 | GREEN 2
        GREEN_MEDIUM 1 | GREEN 2
        GREEN_HIGH 0 | GREEN 4
        GREEN_HIGH 1 | GREEN 2
        BROWN_LOW,GREEN_LOW 0 | BROWN 1
        BROWN_LOW,GREEN_LOW 1 | BROWN 1
        BROWN_HIGH,GREEN_HIGH 0 | BROWN 4
        BROWN_HIGH,GREEN_HIGH 1 | BROWN 2
        POISON,GREEN_HIGH 0 | POISON 0
        POISON,GREEN_HIGH 1 | POISON 0
        GREEN_MEDIUM,BROWN_HIGH 0 | BROWN 4
        GREEN_MEDIUM,BROWN_HIGH 1 | BROWN 2
        - 0 | NOTHING 0
        - 1 | NOTHING 0
        """;

    private static final long LAST = 1000L;
    private static final long NOW = 5000L;

    private static List<String[]> rows(String table) {
        return table.lines().map(String::trim).filter(line -> !line.isEmpty())
            .map(line -> Arrays.stream(line.split("\\s+")).filter(token -> !token.equals("|")).toArray(String[]::new))
            .toList();
    }

    private static Type type(String code) {
        return switch (code) {
            case "N" -> Type.NORMAL;
            case "Y" -> Type.READY;
            case "X" -> Type.ROTTEN;
            default -> throw new IllegalArgumentException(code);
        };
    }

    private static String code(Type type) {
        return switch (type) {
            case NORMAL -> "N";
            case READY -> "Y";
            case ROTTEN -> "X";
        };
    }

    private static CompostBin bin(String g, String b, String state, String out, long last) {
        return new CompostBin(Integer.parseInt(g), Integer.parseInt(b), type(state), last, Integer.parseInt(out));
    }

    private static String describe(CompostBin bin) {
        return bin.green() + " " + bin.brown() + " " + code(bin.type()) + " " + bin.output() + " " + bin.lastUpdate();
    }

    /** One line per row: the first token is the row label, so a failing row names its inputs. */
    private static String label(String[] row) {
        return Arrays.toString(row);
    }

    @Test
    void goldenRowCounts() {
        assertThat(rows(READY_TICKS)).hasSize(240);
        assertThat(rows(SETTLE)).hasSize(64);
        assertThat(rows(ADD)).hasSize(173);
        assertThat(rows(EXTRACT)).hasSize(9);
        assertThat(rows(STAGE_AND_ROTTEN)).hasSize(34);
        assertThat(rows(CLASSIFY)).hasSize(24);
    }

    @Test
    void readyTicksMatchesTfcArithmetic() {
        for (String[] r : rows(READY_TICKS)) {
            long base = Long.parseLong(r[0]);
            float rain = Float.parseFloat(r[1]);
            int neighbours = Integer.parseInt(r[2]);
            assertThat(CompostRules.readyTicks(base, rain, neighbours)).as(label(r)).isEqualTo(Long.parseLong(r[3]));
        }
    }

    @Test
    void settleMatchesTfcRandomTickAndTheRipenTick() {
        for (String[] r : rows(SETTLE)) {
            CompostBin start = bin(r[0], r[1], r[2], r[3], LAST);
            long readyTicks = Long.parseLong(r[4]);
            long now = LAST + Long.parseLong(r[5]);
            CompostBin after = start.settle(now, readyTicks);
            assertThat(describe(after)).as(label(r)).isEqualTo(r[6] + " " + r[7] + " " + r[8] + " " + r[9] + " " + r[10]);
            long ripenedAt = start.ripenedAt(readyTicks);
            assertThat(ripenedAt == CompostRules.NOT_RIPENING ? "-" : Long.toString(ripenedAt)).as(label(r)).isEqualTo(r[11]);
        }
    }

    @Test
    void additionsMatchTfcUse() {
        for (String[] r : rows(ADD)) {
            CompostBin start = bin(r[0], r[1], r[2], r[3], LAST);
            CompostAddition addition = new CompostAddition(Kind.valueOf(r[4]), Integer.parseInt(r[5]));
            boolean creative = r[6].equals("1");
            CompostBin.Insert insert = start.add(addition, creative, NOW);
            assertThat(describe(insert.bin())).as(label(r)).isEqualTo(r[7] + " " + r[8] + " " + r[9] + " " + r[10] + " " + r[11]);
            assertThat(insert.outcome()).as(label(r)).isEqualTo(Outcome.valueOf(r[12]));
            assertThat(insert.consumed()).as(label(r)).isEqualTo(Integer.parseInt(r[13]));
        }
    }

    @Test
    void extractionMatchesTfcUse() {
        for (String[] r : rows(EXTRACT)) {
            CompostBin start = bin(r[0], r[1], r[2], r[3], LAST);
            CompostBin.Extraction extraction = start.extract(NOW);
            assertThat(extraction.compost()).as(label(r)).isEqualTo(Integer.parseInt(r[4]));
            assertThat(extraction.rotten()).as(label(r)).isEqualTo(Integer.parseInt(r[5]));
            assertThat(describe(extraction.bin())).as(label(r)).isEqualTo(r[6] + " " + r[7] + " " + r[8] + " " + r[9] + " " + r[10]);
        }
    }

    @Test
    void stageAndRottenCountGrids() {
        List<String[]> grid = rows(STAGE_AND_ROTTEN);
        for (int g = 0; g < 17; g++) {
            String[] stageRow = grid.get(g);
            String[] rottenRow = grid.get(17 + g);
            assertThat(stageRow).hasSize(17);
            for (int b = 0; b < 17; b++) {
                assertThat(CompostRules.stage(g, b)).as("stage " + g + "," + b).isEqualTo(Integer.parseInt(stageRow[b]));
                assertThat(CompostRules.rottenCount(g, b)).as("rotten " + g + "," + b).isEqualTo(Integer.parseInt(rottenRow[b]));
            }
        }
    }

    @Test
    void classificationFollowsTfcTagOrder() {
        for (String[] r : rows(CLASSIFY)) {
            Set<Tier> tiers = EnumSet.noneOf(Tier.class);
            if (!r[0].equals("-")) {
                for (String tag : r[0].split(",")) {
                    tiers.add(Tier.valueOf(tag));
                }
            }
            CompostAddition addition = CompostAddition.classify(tiers, r[1].equals("1"));
            assertThat(addition.kind().name() + " " + addition.amount()).as(label(r)).isEqualTo(r[2] + " " + r[3]);
        }
    }
}
