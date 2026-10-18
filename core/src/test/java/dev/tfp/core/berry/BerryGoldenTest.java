/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (golden oracle: tick, growAndPropagate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (golden oracle: onUpdate),
 *       src/main/java/net/dries007/tfc/util/climate/ClimateRange.java @ c3b4d9536 (golden oracle: check)
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-FARM-03 golden values from TFC at c3b4d9536. The rows are constants written out from a scratch oracle that transcribes TFC's
 * arithmetic literally: the season and lifecycle tables, the climate check with inclusive bounds, and {@code randomTick} with its
 * recursive {@code tick} and cycle scan, run at one tick per row. Rows, in order:
 *
 * <ul>
 *   <li>{@code SEASON}: {@code hemisphere|calendarMonth|season}</li>
 *   <li>{@code LIFECYCLE}: {@code species|hemisphere|calendarMonth|lifecycle}</li>
 *   <li>{@code WITHER}: {@code species|hydration|temperature|inRange}</li>
 *   <li>{@code TICK}: {@code species|month|stage|lifecycle0|pick|elapsed|growths0|lifecycle'|stage'|growths'|sinceUpdate'|props},
 *       where {@code props} is {@code count:cycles:growths} of the first propagation attempt, or {@code 0}</li>
 * </ul>
 *
 * <p>The tick rows use the clock anchored at {@code NOW} with calendar tick {@code YEAR_OFFSET + month * 8 days + 4.5 days}, the TFC
 * defaults for the growth interval and the pick bloom delay, and a northern hemisphere.
 */
class BerryGoldenTest {
    private static final long NOW = 10_000_000L;
    private static final long CAL_DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static final int DAYS_IN_MONTH = 8;
    private static final long YEAR_OFFSET = 3L * 12 * CAL_DAY * DAYS_IN_MONTH;
    private static final long GROWTH = BerryRules.GROWTH_TICKS;
    private static final long BLOOM = BerryRules.PICK_BLOOM_DELAY_TICKS;
    private static final long EIGHT_MONTHS_PLAYER_TICKS = 1_843_200L;

    private static final String SEASON = """
        north|0|WINTER
        north|1|WINTER
        north|2|SPRING
        north|3|SPRING
        north|4|SPRING
        north|5|SUMMER
        north|6|SUMMER
        north|7|SUMMER
        north|8|FALL
        north|9|FALL
        north|10|FALL
        north|11|WINTER
        south|0|SUMMER
        south|1|SUMMER
        south|2|FALL
        south|3|FALL
        south|4|FALL
        south|5|WINTER
        south|6|WINTER
        south|7|WINTER
        south|8|SPRING
        south|9|SPRING
        south|10|SPRING
        south|11|SUMMER
        """;

    private static final String LIFECYCLE = """
        SNOWBERRY|north|0|DORMANT
        SNOWBERRY|north|1|DORMANT
        SNOWBERRY|north|2|HEALTHY
        SNOWBERRY|north|3|HEALTHY
        SNOWBERRY|north|4|HEALTHY
        SNOWBERRY|north|5|HEALTHY
        SNOWBERRY|north|6|FLOWERING
        SNOWBERRY|north|7|FLOWERING
        SNOWBERRY|north|8|FRUITING
        SNOWBERRY|north|9|DORMANT
        SNOWBERRY|north|10|DORMANT
        SNOWBERRY|north|11|DORMANT
        SNOWBERRY|south|0|FLOWERING
        SNOWBERRY|south|1|FLOWERING
        SNOWBERRY|south|2|FRUITING
        SNOWBERRY|south|3|DORMANT
        SNOWBERRY|south|4|DORMANT
        SNOWBERRY|south|5|DORMANT
        SNOWBERRY|south|6|DORMANT
        SNOWBERRY|south|7|DORMANT
        SNOWBERRY|south|8|HEALTHY
        SNOWBERRY|south|9|HEALTHY
        SNOWBERRY|south|10|HEALTHY
        SNOWBERRY|south|11|HEALTHY
        BUNCHBERRY|north|0|DORMANT
        BUNCHBERRY|north|1|DORMANT
        BUNCHBERRY|north|2|DORMANT
        BUNCHBERRY|north|3|DORMANT
        BUNCHBERRY|north|4|HEALTHY
        BUNCHBERRY|north|5|HEALTHY
        BUNCHBERRY|north|6|HEALTHY
        BUNCHBERRY|north|7|FLOWERING
        BUNCHBERRY|north|8|FLOWERING
        BUNCHBERRY|north|9|FRUITING
        BUNCHBERRY|north|10|DORMANT
        BUNCHBERRY|north|11|DORMANT
        BUNCHBERRY|south|0|HEALTHY
        BUNCHBERRY|south|1|FLOWERING
        BUNCHBERRY|south|2|FLOWERING
        BUNCHBERRY|south|3|FRUITING
        BUNCHBERRY|south|4|DORMANT
        BUNCHBERRY|south|5|DORMANT
        BUNCHBERRY|south|6|DORMANT
        BUNCHBERRY|south|7|DORMANT
        BUNCHBERRY|south|8|DORMANT
        BUNCHBERRY|south|9|DORMANT
        BUNCHBERRY|south|10|HEALTHY
        BUNCHBERRY|south|11|HEALTHY
        GOOSEBERRY|north|0|DORMANT
        GOOSEBERRY|north|1|DORMANT
        GOOSEBERRY|north|2|DORMANT
        GOOSEBERRY|north|3|HEALTHY
        GOOSEBERRY|north|4|HEALTHY
        GOOSEBERRY|north|5|HEALTHY
        GOOSEBERRY|north|6|HEALTHY
        GOOSEBERRY|north|7|FLOWERING
        GOOSEBERRY|north|8|FLOWERING
        GOOSEBERRY|north|9|FRUITING
        GOOSEBERRY|north|10|DORMANT
        GOOSEBERRY|north|11|DORMANT
        GOOSEBERRY|south|0|HEALTHY
        GOOSEBERRY|south|1|FLOWERING
        GOOSEBERRY|south|2|FLOWERING
        GOOSEBERRY|south|3|FRUITING
        GOOSEBERRY|south|4|DORMANT
        GOOSEBERRY|south|5|DORMANT
        GOOSEBERRY|south|6|DORMANT
        GOOSEBERRY|south|7|DORMANT
        GOOSEBERRY|south|8|DORMANT
        GOOSEBERRY|south|9|HEALTHY
        GOOSEBERRY|south|10|HEALTHY
        GOOSEBERRY|south|11|HEALTHY
        CLOUDBERRY|north|0|DORMANT
        CLOUDBERRY|north|1|HEALTHY
        CLOUDBERRY|north|2|HEALTHY
        CLOUDBERRY|north|3|HEALTHY
        CLOUDBERRY|north|4|HEALTHY
        CLOUDBERRY|north|5|FLOWERING
        CLOUDBERRY|north|6|FLOWERING
        CLOUDBERRY|north|7|FLOWERING
        CLOUDBERRY|north|8|FRUITING
        CLOUDBERRY|north|9|DORMANT
        CLOUDBERRY|north|10|DORMANT
        CLOUDBERRY|north|11|DORMANT
        CLOUDBERRY|south|0|FLOWERING
        CLOUDBERRY|south|1|FLOWERING
        CLOUDBERRY|south|2|FRUITING
        CLOUDBERRY|south|3|DORMANT
        CLOUDBERRY|south|4|DORMANT
        CLOUDBERRY|south|5|DORMANT
        CLOUDBERRY|south|6|DORMANT
        CLOUDBERRY|south|7|HEALTHY
        CLOUDBERRY|south|8|HEALTHY
        CLOUDBERRY|south|9|HEALTHY
        CLOUDBERRY|south|10|HEALTHY
        CLOUDBERRY|south|11|FLOWERING
        STRAWBERRY|north|0|FLOWERING
        STRAWBERRY|north|1|FLOWERING
        STRAWBERRY|north|2|FRUITING
        STRAWBERRY|north|3|DORMANT
        STRAWBERRY|north|4|DORMANT
        STRAWBERRY|north|5|DORMANT
        STRAWBERRY|north|6|DORMANT
        STRAWBERRY|north|7|DORMANT
        STRAWBERRY|north|8|DORMANT
        STRAWBERRY|north|9|HEALTHY
        STRAWBERRY|north|10|HEALTHY
        STRAWBERRY|north|11|HEALTHY
        STRAWBERRY|south|0|DORMANT
        STRAWBERRY|south|1|DORMANT
        STRAWBERRY|south|2|DORMANT
        STRAWBERRY|south|3|HEALTHY
        STRAWBERRY|south|4|HEALTHY
        STRAWBERRY|south|5|HEALTHY
        STRAWBERRY|south|6|FLOWERING
        STRAWBERRY|south|7|FLOWERING
        STRAWBERRY|south|8|FRUITING
        STRAWBERRY|south|9|DORMANT
        STRAWBERRY|south|10|DORMANT
        STRAWBERRY|south|11|DORMANT
        WINTERGREEN_BERRY|north|0|DORMANT
        WINTERGREEN_BERRY|north|1|DORMANT
        WINTERGREEN_BERRY|north|2|DORMANT
        WINTERGREEN_BERRY|north|3|DORMANT
        WINTERGREEN_BERRY|north|4|HEALTHY
        WINTERGREEN_BERRY|north|5|HEALTHY
        WINTERGREEN_BERRY|north|6|HEALTHY
        WINTERGREEN_BERRY|north|7|HEALTHY
        WINTERGREEN_BERRY|north|8|HEALTHY
        WINTERGREEN_BERRY|north|9|FLOWERING
        WINTERGREEN_BERRY|north|10|FLOWERING
        WINTERGREEN_BERRY|north|11|FRUITING
        WINTERGREEN_BERRY|south|0|HEALTHY
        WINTERGREEN_BERRY|south|1|HEALTHY
        WINTERGREEN_BERRY|south|2|HEALTHY
        WINTERGREEN_BERRY|south|3|FLOWERING
        WINTERGREEN_BERRY|south|4|FLOWERING
        WINTERGREEN_BERRY|south|5|FRUITING
        WINTERGREEN_BERRY|south|6|DORMANT
        WINTERGREEN_BERRY|south|7|DORMANT
        WINTERGREEN_BERRY|south|8|DORMANT
        WINTERGREEN_BERRY|south|9|DORMANT
        WINTERGREEN_BERRY|south|10|HEALTHY
        WINTERGREEN_BERRY|south|11|HEALTHY
        """;

    private static final String WITHER = """
        SNOWBERRY|0|-20|0
        SNOWBERRY|0|-3|0
        SNOWBERRY|0|-2.8|0
        SNOWBERRY|0|0|0
        SNOWBERRY|0|5|0
        SNOWBERRY|0|6.2|0
        SNOWBERRY|0|6.3|0
        SNOWBERRY|0|10|0
        SNOWBERRY|0|18.8|0
        SNOWBERRY|0|19|0
        SNOWBERRY|0|25|0
        SNOWBERRY|5|-20|0
        SNOWBERRY|5|-3|0
        SNOWBERRY|5|-2.8|0
        SNOWBERRY|5|0|0
        SNOWBERRY|5|5|0
        SNOWBERRY|5|6.2|0
        SNOWBERRY|5|6.3|0
        SNOWBERRY|5|10|0
        SNOWBERRY|5|18.8|0
        SNOWBERRY|5|19|0
        SNOWBERRY|5|25|0
        SNOWBERRY|11|-20|0
        SNOWBERRY|11|-3|0
        SNOWBERRY|11|-2.8|0
        SNOWBERRY|11|0|0
        SNOWBERRY|11|5|0
        SNOWBERRY|11|6.2|0
        SNOWBERRY|11|6.3|0
        SNOWBERRY|11|10|0
        SNOWBERRY|11|18.8|0
        SNOWBERRY|11|19|0
        SNOWBERRY|11|25|0
        SNOWBERRY|17|-20|0
        SNOWBERRY|17|-3|1
        SNOWBERRY|17|-2.8|1
        SNOWBERRY|17|0|1
        SNOWBERRY|17|5|1
        SNOWBERRY|17|6.2|1
        SNOWBERRY|17|6.3|0
        SNOWBERRY|17|10|0
        SNOWBERRY|17|18.8|0
        SNOWBERRY|17|19|0
        SNOWBERRY|17|25|0
        SNOWBERRY|50|-20|0
        SNOWBERRY|50|-3|1
        SNOWBERRY|50|-2.8|1
        SNOWBERRY|50|0|1
        SNOWBERRY|50|5|1
        SNOWBERRY|50|6.2|1
        SNOWBERRY|50|6.3|0
        SNOWBERRY|50|10|0
        SNOWBERRY|50|18.8|0
        SNOWBERRY|50|19|0
        SNOWBERRY|50|25|0
        SNOWBERRY|90|-20|0
        SNOWBERRY|90|-3|1
        SNOWBERRY|90|-2.8|1
        SNOWBERRY|90|0|1
        SNOWBERRY|90|5|1
        SNOWBERRY|90|6.2|1
        SNOWBERRY|90|6.3|0
        SNOWBERRY|90|10|0
        SNOWBERRY|90|18.8|0
        SNOWBERRY|90|19|0
        SNOWBERRY|90|25|0
        SNOWBERRY|95|-20|0
        SNOWBERRY|95|-3|1
        SNOWBERRY|95|-2.8|1
        SNOWBERRY|95|0|1
        SNOWBERRY|95|5|1
        SNOWBERRY|95|6.2|1
        SNOWBERRY|95|6.3|0
        SNOWBERRY|95|10|0
        SNOWBERRY|95|18.8|0
        SNOWBERRY|95|19|0
        SNOWBERRY|95|25|0
        SNOWBERRY|100|-20|0
        SNOWBERRY|100|-3|1
        SNOWBERRY|100|-2.8|1
        SNOWBERRY|100|0|1
        SNOWBERRY|100|5|1
        SNOWBERRY|100|6.2|1
        SNOWBERRY|100|6.3|0
        SNOWBERRY|100|10|0
        SNOWBERRY|100|18.8|0
        SNOWBERRY|100|19|0
        SNOWBERRY|100|25|0
        STRAWBERRY|0|-20|0
        STRAWBERRY|0|-3|0
        STRAWBERRY|0|-2.8|0
        STRAWBERRY|0|0|0
        STRAWBERRY|0|5|0
        STRAWBERRY|0|6.2|0
        STRAWBERRY|0|6.3|0
        STRAWBERRY|0|10|0
        STRAWBERRY|0|18.8|0
        STRAWBERRY|0|19|0
        STRAWBERRY|0|25|0
        STRAWBERRY|5|-20|0
        STRAWBERRY|5|-3|0
        STRAWBERRY|5|-2.8|0
        STRAWBERRY|5|0|0
        STRAWBERRY|5|5|0
        STRAWBERRY|5|6.2|0
        STRAWBERRY|5|6.3|0
        STRAWBERRY|5|10|0
        STRAWBERRY|5|18.8|0
        STRAWBERRY|5|19|0
        STRAWBERRY|5|25|0
        STRAWBERRY|11|-20|0
        STRAWBERRY|11|-3|0
        STRAWBERRY|11|-2.8|1
        STRAWBERRY|11|0|1
        STRAWBERRY|11|5|1
        STRAWBERRY|11|6.2|1
        STRAWBERRY|11|6.3|1
        STRAWBERRY|11|10|1
        STRAWBERRY|11|18.8|1
        STRAWBERRY|11|19|0
        STRAWBERRY|11|25|0
        STRAWBERRY|17|-20|0
        STRAWBERRY|17|-3|0
        STRAWBERRY|17|-2.8|1
        STRAWBERRY|17|0|1
        STRAWBERRY|17|5|1
        STRAWBERRY|17|6.2|1
        STRAWBERRY|17|6.3|1
        STRAWBERRY|17|10|1
        STRAWBERRY|17|18.8|1
        STRAWBERRY|17|19|0
        STRAWBERRY|17|25|0
        STRAWBERRY|50|-20|0
        STRAWBERRY|50|-3|0
        STRAWBERRY|50|-2.8|1
        STRAWBERRY|50|0|1
        STRAWBERRY|50|5|1
        STRAWBERRY|50|6.2|1
        STRAWBERRY|50|6.3|1
        STRAWBERRY|50|10|1
        STRAWBERRY|50|18.8|1
        STRAWBERRY|50|19|0
        STRAWBERRY|50|25|0
        STRAWBERRY|90|-20|0
        STRAWBERRY|90|-3|0
        STRAWBERRY|90|-2.8|1
        STRAWBERRY|90|0|1
        STRAWBERRY|90|5|1
        STRAWBERRY|90|6.2|1
        STRAWBERRY|90|6.3|1
        STRAWBERRY|90|10|1
        STRAWBERRY|90|18.8|1
        STRAWBERRY|90|19|0
        STRAWBERRY|90|25|0
        STRAWBERRY|95|-20|0
        STRAWBERRY|95|-3|0
        STRAWBERRY|95|-2.8|0
        STRAWBERRY|95|0|0
        STRAWBERRY|95|5|0
        STRAWBERRY|95|6.2|0
        STRAWBERRY|95|6.3|0
        STRAWBERRY|95|10|0
        STRAWBERRY|95|18.8|0
        STRAWBERRY|95|19|0
        STRAWBERRY|95|25|0
        STRAWBERRY|100|-20|0
        STRAWBERRY|100|-3|0
        STRAWBERRY|100|-2.8|0
        STRAWBERRY|100|0|0
        STRAWBERRY|100|5|0
        STRAWBERRY|100|6.2|0
        STRAWBERRY|100|6.3|0
        STRAWBERRY|100|10|0
        STRAWBERRY|100|18.8|0
        STRAWBERRY|100|19|0
        STRAWBERRY|100|25|0
        """;

    private static final String TICK = """
        SNOWBERRY|0|0|H|open|g3|24|D|0|24|345600|0
        SNOWBERRY|0|0|H|open|g9|24|D|0|24|1036800|0
        SNOWBERRY|0|0|H|open|mo8|24|D|0|24|1843200|0
        SNOWBERRY|0|0|H|closed|g3|24|D|0|24|345600|0
        SNOWBERRY|0|0|H|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|0|0|H|closed|mo8|24|D|0|24|1843200|0
        SNOWBERRY|0|1|H|open|g3|24|D|1|24|345600|0
        SNOWBERRY|0|1|H|open|g9|24|D|1|24|1036800|0
        SNOWBERRY|0|1|H|open|mo8|24|D|1|24|1843200|0
        SNOWBERRY|0|1|H|closed|g3|24|D|1|24|345600|0
        SNOWBERRY|0|1|H|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|0|1|H|closed|mo8|24|D|1|24|1843200|0
        SNOWBERRY|0|2|H|open|g3|24|D|2|24|345600|0
        SNOWBERRY|0|2|H|open|g9|24|D|2|24|1036800|0
        SNOWBERRY|0|2|H|open|mo8|24|D|2|24|1843200|0
        SNOWBERRY|0|2|H|closed|g3|24|D|2|24|345600|0
        SNOWBERRY|0|2|H|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|0|2|H|closed|mo8|24|D|2|24|1843200|0
        SNOWBERRY|1|0|H|open|g3|24|D|0|24|345600|0
        SNOWBERRY|1|0|H|open|g9|24|D|0|24|1036800|0
        SNOWBERRY|1|0|H|open|mo8|24|D|0|24|1843200|0
        SNOWBERRY|1|0|H|closed|g3|24|D|0|24|345600|0
        SNOWBERRY|1|0|H|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|1|0|H|closed|mo8|24|D|0|24|1843200|0
        SNOWBERRY|1|1|H|open|g3|24|D|1|24|345600|0
        SNOWBERRY|1|1|H|open|g9|24|D|1|24|1036800|0
        SNOWBERRY|1|1|H|open|mo8|24|D|1|24|1843200|0
        SNOWBERRY|1|1|H|closed|g3|24|D|1|24|345600|0
        SNOWBERRY|1|1|H|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|1|1|H|closed|mo8|24|D|1|24|1843200|0
        SNOWBERRY|1|2|H|open|g3|24|D|2|24|345600|0
        SNOWBERRY|1|2|H|open|g9|24|D|2|24|1036800|0
        SNOWBERRY|1|2|H|open|mo8|24|D|2|24|1843200|0
        SNOWBERRY|1|2|H|closed|g3|24|D|2|24|345600|0
        SNOWBERRY|1|2|H|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|1|2|H|closed|mo8|24|D|2|24|1843200|0
        SNOWBERRY|2|0|H|open|g3|24|H|2|22|0|0
        SNOWBERRY|2|0|H|open|g9|24|H|2|22|0|0
        SNOWBERRY|2|0|H|open|mo8|24|H|2|22|0|1:0:21
        SNOWBERRY|2|0|H|closed|g3|24|H|2|22|0|0
        SNOWBERRY|2|0|H|closed|g9|24|H|2|22|0|0
        SNOWBERRY|2|0|H|closed|mo8|24|H|2|22|0|1:0:21
        SNOWBERRY|2|1|H|open|g3|24|H|2|23|0|1:0:22
        SNOWBERRY|2|1|H|open|g9|24|H|2|23|0|1:0:22
        SNOWBERRY|2|1|H|open|mo8|24|H|2|23|0|1:1:22
        SNOWBERRY|2|1|H|closed|g3|24|H|2|23|0|1:0:22
        SNOWBERRY|2|1|H|closed|g9|24|H|2|23|0|1:0:22
        SNOWBERRY|2|1|H|closed|mo8|24|H|2|23|0|1:1:22
        SNOWBERRY|2|2|H|open|g3|24|H|2|24|0|1:1:23
        SNOWBERRY|2|2|H|open|g9|24|H|2|24|0|1:1:23
        SNOWBERRY|2|2|H|open|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|2|2|H|closed|g3|24|H|2|24|0|1:1:23
        SNOWBERRY|2|2|H|closed|g9|24|H|2|24|0|1:1:23
        SNOWBERRY|2|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|3|0|H|open|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|3|0|H|open|g9|24|H|2|22|0|1:1:21
        SNOWBERRY|3|0|H|open|mo8|24|H|2|22|0|1:2:21
        SNOWBERRY|3|0|H|closed|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|3|0|H|closed|g9|24|H|2|22|0|1:1:21
        SNOWBERRY|3|0|H|closed|mo8|24|H|2|22|0|1:2:21
        SNOWBERRY|3|1|H|open|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|3|1|H|open|g9|24|H|2|23|0|1:2:22
        SNOWBERRY|3|1|H|open|mo8|24|H|2|23|0|1:3:22
        SNOWBERRY|3|1|H|closed|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|3|1|H|closed|g9|24|H|2|23|0|1:2:22
        SNOWBERRY|3|1|H|closed|mo8|24|H|2|23|0|1:3:22
        SNOWBERRY|3|2|H|open|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|3|2|H|open|g9|24|H|2|24|0|1:3:23
        SNOWBERRY|3|2|H|open|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|3|2|H|closed|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|3|2|H|closed|g9|24|H|2|24|0|1:3:23
        SNOWBERRY|3|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|4|0|H|open|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|4|0|H|open|g9|24|H|2|22|0|1:3:21
        SNOWBERRY|4|0|H|open|mo8|24|H|2|22|0|1:4:21
        SNOWBERRY|4|0|H|closed|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|4|0|H|closed|g9|24|H|2|22|0|1:3:21
        SNOWBERRY|4|0|H|closed|mo8|24|H|2|22|0|1:4:21
        SNOWBERRY|4|1|H|open|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|4|1|H|open|g9|24|H|2|23|0|1:4:22
        SNOWBERRY|4|1|H|open|mo8|24|H|2|23|0|1:5:22
        SNOWBERRY|4|1|H|closed|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|4|1|H|closed|g9|24|H|2|23|0|1:4:22
        SNOWBERRY|4|1|H|closed|mo8|24|H|2|23|0|1:5:22
        SNOWBERRY|4|2|H|open|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|4|2|H|open|g9|24|H|2|24|0|1:5:23
        SNOWBERRY|4|2|H|open|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|4|2|H|closed|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|4|2|H|closed|g9|24|H|2|24|0|1:5:23
        SNOWBERRY|4|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|5|0|H|open|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|5|0|H|open|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|5|0|H|open|mo8|24|H|2|22|0|1:5:21
        SNOWBERRY|5|0|H|closed|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|5|0|H|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|5|0|H|closed|mo8|24|H|2|22|0|1:5:21
        SNOWBERRY|5|1|H|open|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|5|1|H|open|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|5|1|H|open|mo8|24|H|2|23|0|1:6:22
        SNOWBERRY|5|1|H|closed|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|5|1|H|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|5|1|H|closed|mo8|24|H|2|23|0|1:6:22
        SNOWBERRY|5|2|H|open|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|5|2|H|open|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|5|2|H|open|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|5|2|H|closed|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|5|2|H|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|5|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|6|0|H|open|g3|24|F|2|22|0|1:0:21
        SNOWBERRY|6|0|H|open|g9|24|F|2|22|0|1:5:21
        SNOWBERRY|6|0|H|open|mo8|24|F|2|22|0|1:5:21
        SNOWBERRY|6|0|H|closed|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|6|0|H|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|6|0|H|closed|mo8|24|H|2|22|0|1:5:21
        SNOWBERRY|6|1|H|open|g3|24|F|2|23|0|1:1:22
        SNOWBERRY|6|1|H|open|g9|24|F|2|23|0|1:6:22
        SNOWBERRY|6|1|H|open|mo8|24|F|2|23|0|1:6:22
        SNOWBERRY|6|1|H|closed|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|6|1|H|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|6|1|H|closed|mo8|24|H|2|23|0|1:6:22
        SNOWBERRY|6|2|H|open|g3|24|F|2|24|0|1:2:23
        SNOWBERRY|6|2|H|open|g9|24|F|2|24|0|1:7:23
        SNOWBERRY|6|2|H|open|mo8|24|F|2|24|0|1:7:23
        SNOWBERRY|6|2|H|closed|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|6|2|H|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|6|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|7|0|H|open|g3|24|F|2|22|0|1:0:21
        SNOWBERRY|7|0|H|open|g9|24|F|2|22|0|1:5:21
        SNOWBERRY|7|0|H|open|mo8|24|F|2|22|0|1:5:21
        SNOWBERRY|7|0|H|closed|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|7|0|H|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|7|0|H|closed|mo8|24|H|2|22|0|1:5:21
        SNOWBERRY|7|1|H|open|g3|24|F|2|23|0|1:1:22
        SNOWBERRY|7|1|H|open|g9|24|F|2|23|0|1:6:22
        SNOWBERRY|7|1|H|open|mo8|24|F|2|23|0|1:6:22
        SNOWBERRY|7|1|H|closed|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|7|1|H|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|7|1|H|closed|mo8|24|H|2|23|0|1:6:22
        SNOWBERRY|7|2|H|open|g3|24|F|2|24|0|1:2:23
        SNOWBERRY|7|2|H|open|g9|24|F|2|24|0|1:7:23
        SNOWBERRY|7|2|H|open|mo8|24|F|2|24|0|1:7:23
        SNOWBERRY|7|2|H|closed|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|7|2|H|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|7|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|8|0|H|open|g3|24|R|2|22|0|1:0:21
        SNOWBERRY|8|0|H|open|g9|24|R|2|22|0|1:5:21
        SNOWBERRY|8|0|H|open|mo8|24|R|2|22|0|1:5:21
        SNOWBERRY|8|0|H|closed|g3|24|H|2|22|0|1:0:21
        SNOWBERRY|8|0|H|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|8|0|H|closed|mo8|24|H|2|22|0|1:5:21
        SNOWBERRY|8|1|H|open|g3|24|R|2|23|0|1:1:22
        SNOWBERRY|8|1|H|open|g9|24|R|2|23|0|1:6:22
        SNOWBERRY|8|1|H|open|mo8|24|R|2|23|0|1:6:22
        SNOWBERRY|8|1|H|closed|g3|24|H|2|23|0|1:1:22
        SNOWBERRY|8|1|H|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|8|1|H|closed|mo8|24|H|2|23|0|1:6:22
        SNOWBERRY|8|2|H|open|g3|24|F|2|24|0|1:2:23
        SNOWBERRY|8|2|H|open|g9|24|F|2|24|0|1:7:23
        SNOWBERRY|8|2|H|open|mo8|24|F|2|24|0|1:7:23
        SNOWBERRY|8|2|H|closed|g3|24|H|2|24|0|1:2:23
        SNOWBERRY|8|2|H|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|8|2|H|closed|mo8|24|H|2|24|0|1:7:23
        SNOWBERRY|9|0|H|open|g3|24|D|0|24|345600|0
        SNOWBERRY|9|0|H|open|g9|24|D|0|24|1036800|0
        SNOWBERRY|9|0|H|open|mo8|24|D|0|24|1843200|0
        SNOWBERRY|9|0|H|closed|g3|24|D|0|24|345600|0
        SNOWBERRY|9|0|H|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|9|0|H|closed|mo8|24|D|0|24|1843200|0
        SNOWBERRY|9|1|H|open|g3|24|D|1|24|345600|0
        SNOWBERRY|9|1|H|open|g9|24|D|1|24|1036800|0
        SNOWBERRY|9|1|H|open|mo8|24|D|1|24|1843200|0
        SNOWBERRY|9|1|H|closed|g3|24|D|1|24|345600|0
        SNOWBERRY|9|1|H|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|9|1|H|closed|mo8|24|D|1|24|1843200|0
        SNOWBERRY|9|2|H|open|g3|24|D|2|24|345600|0
        SNOWBERRY|9|2|H|open|g9|24|D|2|24|1036800|0
        SNOWBERRY|9|2|H|open|mo8|24|D|2|24|1843200|0
        SNOWBERRY|9|2|H|closed|g3|24|D|2|24|345600|0
        SNOWBERRY|9|2|H|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|9|2|H|closed|mo8|24|D|2|24|1843200|0
        SNOWBERRY|10|0|H|open|g3|24|D|0|24|345600|0
        SNOWBERRY|10|0|H|open|g9|24|D|0|24|1036800|0
        SNOWBERRY|10|0|H|open|mo8|24|D|0|24|1843200|0
        SNOWBERRY|10|0|H|closed|g3|24|D|0|24|345600|0
        SNOWBERRY|10|0|H|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|10|0|H|closed|mo8|24|D|0|24|1843200|0
        SNOWBERRY|10|1|H|open|g3|24|D|1|24|345600|0
        SNOWBERRY|10|1|H|open|g9|24|D|1|24|1036800|0
        SNOWBERRY|10|1|H|open|mo8|24|D|1|24|1843200|0
        SNOWBERRY|10|1|H|closed|g3|24|D|1|24|345600|0
        SNOWBERRY|10|1|H|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|10|1|H|closed|mo8|24|D|1|24|1843200|0
        SNOWBERRY|10|2|H|open|g3|24|D|2|24|345600|0
        SNOWBERRY|10|2|H|open|g9|24|D|2|24|1036800|0
        SNOWBERRY|10|2|H|open|mo8|24|D|2|24|1843200|0
        SNOWBERRY|10|2|H|closed|g3|24|D|2|24|345600|0
        SNOWBERRY|10|2|H|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|10|2|H|closed|mo8|24|D|2|24|1843200|0
        SNOWBERRY|11|0|H|open|g3|24|D|0|24|345600|0
        SNOWBERRY|11|0|H|open|g9|24|D|0|24|1036800|0
        SNOWBERRY|11|0|H|open|mo8|24|D|0|24|1843200|0
        SNOWBERRY|11|0|H|closed|g3|24|D|0|24|345600|0
        SNOWBERRY|11|0|H|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|11|0|H|closed|mo8|24|D|0|24|1843200|0
        SNOWBERRY|11|1|H|open|g3|24|D|1|24|345600|0
        SNOWBERRY|11|1|H|open|g9|24|D|1|24|1036800|0
        SNOWBERRY|11|1|H|open|mo8|24|D|1|24|1843200|0
        SNOWBERRY|11|1|H|closed|g3|24|D|1|24|345600|0
        SNOWBERRY|11|1|H|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|11|1|H|closed|mo8|24|D|1|24|1843200|0
        SNOWBERRY|11|2|H|open|g3|24|D|2|24|345600|0
        SNOWBERRY|11|2|H|open|g9|24|D|2|24|1036800|0
        SNOWBERRY|11|2|H|open|mo8|24|D|2|24|1843200|0
        SNOWBERRY|11|2|H|closed|g3|24|D|2|24|345600|0
        SNOWBERRY|11|2|H|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|11|2|H|closed|mo8|24|D|2|24|1843200|0
        SNOWBERRY|0|0|D|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|0|0|H|open|g9|1|D|0|1|1036800|0
        SNOWBERRY|0|1|D|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|0|1|H|open|g9|1|D|1|1|1036800|0
        SNOWBERRY|0|2|D|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|0|2|H|open|g9|1|D|2|1|1036800|0
        SNOWBERRY|1|0|D|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|1|0|H|open|g9|1|D|0|1|1036800|0
        SNOWBERRY|1|1|D|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|1|1|H|open|g9|1|D|1|1|1036800|0
        SNOWBERRY|1|2|D|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|1|2|H|open|g9|1|D|2|1|1036800|0
        SNOWBERRY|2|0|D|closed|g9|24|H|2|22|0|0
        SNOWBERRY|2|0|H|open|g9|1|H|1|0|115200|0
        SNOWBERRY|2|1|D|closed|g9|24|H|2|23|0|1:0:22
        SNOWBERRY|2|1|H|open|g9|1|H|2|0|115200|0
        SNOWBERRY|2|2|D|closed|g9|24|H|2|24|0|1:1:23
        SNOWBERRY|2|2|H|open|g9|1|H|2|1|0|1:1:0
        SNOWBERRY|3|0|D|closed|g9|24|H|2|22|0|1:1:21
        SNOWBERRY|3|0|H|open|g9|1|H|1|0|345600|0
        SNOWBERRY|3|1|D|closed|g9|24|H|2|23|0|1:2:22
        SNOWBERRY|3|1|H|open|g9|1|H|2|0|345600|0
        SNOWBERRY|3|2|D|closed|g9|24|H|2|24|0|1:3:23
        SNOWBERRY|3|2|H|open|g9|1|H|2|1|0|1:3:0
        SNOWBERRY|4|0|D|closed|g9|24|H|2|22|0|1:3:21
        SNOWBERRY|4|0|H|open|g9|1|H|1|0|576000|0
        SNOWBERRY|4|1|D|closed|g9|24|H|2|23|0|1:4:22
        SNOWBERRY|4|1|H|open|g9|1|H|2|0|576000|0
        SNOWBERRY|4|2|D|closed|g9|24|H|2|24|0|1:5:23
        SNOWBERRY|4|2|H|open|g9|1|H|2|1|0|1:5:0
        SNOWBERRY|5|0|D|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|5|0|H|open|g9|1|H|1|0|806400|0
        SNOWBERRY|5|1|D|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|5|1|H|open|g9|1|H|2|0|806400|0
        SNOWBERRY|5|2|D|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|5|2|H|open|g9|1|H|2|1|0|1:7:0
        SNOWBERRY|6|0|D|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|6|0|H|open|g9|1|F|1|0|806400|0
        SNOWBERRY|6|1|D|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|6|1|H|open|g9|1|F|2|0|806400|0
        SNOWBERRY|6|2|D|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|6|2|H|open|g9|1|F|2|1|0|1:7:0
        SNOWBERRY|7|0|D|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|7|0|H|open|g9|1|F|1|0|806400|0
        SNOWBERRY|7|1|D|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|7|1|H|open|g9|1|F|2|0|806400|0
        SNOWBERRY|7|2|D|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|7|2|H|open|g9|1|F|2|1|0|1:7:0
        SNOWBERRY|8|0|D|closed|g9|24|H|2|22|0|1:5:21
        SNOWBERRY|8|0|H|open|g9|1|R|1|0|806400|0
        SNOWBERRY|8|1|D|closed|g9|24|H|2|23|0|1:6:22
        SNOWBERRY|8|1|H|open|g9|1|R|2|0|806400|0
        SNOWBERRY|8|2|D|closed|g9|24|H|2|24|0|1:7:23
        SNOWBERRY|8|2|H|open|g9|1|F|2|1|0|1:7:0
        SNOWBERRY|9|0|D|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|9|0|H|open|g9|1|D|0|1|1036800|0
        SNOWBERRY|9|1|D|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|9|1|H|open|g9|1|D|1|1|1036800|0
        SNOWBERRY|9|2|D|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|9|2|H|open|g9|1|D|2|1|1036800|0
        SNOWBERRY|10|0|D|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|10|0|H|open|g9|1|D|0|1|1036800|0
        SNOWBERRY|10|1|D|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|10|1|H|open|g9|1|D|1|1|1036800|0
        SNOWBERRY|10|2|D|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|10|2|H|open|g9|1|D|2|1|1036800|0
        SNOWBERRY|11|0|D|closed|g9|24|D|0|24|1036800|0
        SNOWBERRY|11|0|H|open|g9|1|D|0|1|1036800|0
        SNOWBERRY|11|1|D|closed|g9|24|D|1|24|1036800|0
        SNOWBERRY|11|1|H|open|g9|1|D|1|1|1036800|0
        SNOWBERRY|11|2|D|closed|g9|24|D|2|24|1036800|0
        SNOWBERRY|11|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|0|0|H|open|g3|24|F|2|22|0|1:0:21
        STRAWBERRY|0|0|H|open|g9|24|F|2|22|0|1:5:21
        STRAWBERRY|0|0|H|open|mo8|24|F|2|22|0|1:5:21
        STRAWBERRY|0|0|H|closed|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|0|0|H|closed|g9|24|H|2|22|0|1:5:21
        STRAWBERRY|0|0|H|closed|mo8|24|H|2|22|0|1:5:21
        STRAWBERRY|0|1|H|open|g3|24|F|2|23|0|1:1:22
        STRAWBERRY|0|1|H|open|g9|24|F|2|23|0|1:6:22
        STRAWBERRY|0|1|H|open|mo8|24|F|2|23|0|1:6:22
        STRAWBERRY|0|1|H|closed|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|0|1|H|closed|g9|24|H|2|23|0|1:6:22
        STRAWBERRY|0|1|H|closed|mo8|24|H|2|23|0|1:6:22
        STRAWBERRY|0|2|H|open|g3|24|F|2|24|0|1:2:23
        STRAWBERRY|0|2|H|open|g9|24|F|2|24|0|1:7:23
        STRAWBERRY|0|2|H|open|mo8|24|F|2|24|0|1:7:23
        STRAWBERRY|0|2|H|closed|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|0|2|H|closed|g9|24|H|2|24|0|1:7:23
        STRAWBERRY|0|2|H|closed|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|1|0|H|open|g3|24|F|2|22|0|1:0:21
        STRAWBERRY|1|0|H|open|g9|24|F|2|22|0|1:5:21
        STRAWBERRY|1|0|H|open|mo8|24|F|2|22|0|1:5:21
        STRAWBERRY|1|0|H|closed|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|1|0|H|closed|g9|24|H|2|22|0|1:5:21
        STRAWBERRY|1|0|H|closed|mo8|24|H|2|22|0|1:5:21
        STRAWBERRY|1|1|H|open|g3|24|F|2|23|0|1:1:22
        STRAWBERRY|1|1|H|open|g9|24|F|2|23|0|1:6:22
        STRAWBERRY|1|1|H|open|mo8|24|F|2|23|0|1:6:22
        STRAWBERRY|1|1|H|closed|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|1|1|H|closed|g9|24|H|2|23|0|1:6:22
        STRAWBERRY|1|1|H|closed|mo8|24|H|2|23|0|1:6:22
        STRAWBERRY|1|2|H|open|g3|24|F|2|24|0|1:2:23
        STRAWBERRY|1|2|H|open|g9|24|F|2|24|0|1:7:23
        STRAWBERRY|1|2|H|open|mo8|24|F|2|24|0|1:7:23
        STRAWBERRY|1|2|H|closed|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|1|2|H|closed|g9|24|H|2|24|0|1:7:23
        STRAWBERRY|1|2|H|closed|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|2|0|H|open|g3|24|R|2|22|0|1:0:21
        STRAWBERRY|2|0|H|open|g9|24|R|2|22|0|1:5:21
        STRAWBERRY|2|0|H|open|mo8|24|R|2|22|0|1:5:21
        STRAWBERRY|2|0|H|closed|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|2|0|H|closed|g9|24|H|2|22|0|1:5:21
        STRAWBERRY|2|0|H|closed|mo8|24|H|2|22|0|1:5:21
        STRAWBERRY|2|1|H|open|g3|24|R|2|23|0|1:1:22
        STRAWBERRY|2|1|H|open|g9|24|R|2|23|0|1:6:22
        STRAWBERRY|2|1|H|open|mo8|24|R|2|23|0|1:6:22
        STRAWBERRY|2|1|H|closed|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|2|1|H|closed|g9|24|H|2|23|0|1:6:22
        STRAWBERRY|2|1|H|closed|mo8|24|H|2|23|0|1:6:22
        STRAWBERRY|2|2|H|open|g3|24|F|2|24|0|1:2:23
        STRAWBERRY|2|2|H|open|g9|24|F|2|24|0|1:7:23
        STRAWBERRY|2|2|H|open|mo8|24|F|2|24|0|1:7:23
        STRAWBERRY|2|2|H|closed|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|2|2|H|closed|g9|24|H|2|24|0|1:7:23
        STRAWBERRY|2|2|H|closed|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|3|0|H|open|g3|24|D|0|24|345600|0
        STRAWBERRY|3|0|H|open|g9|24|D|0|24|1036800|0
        STRAWBERRY|3|0|H|open|mo8|24|D|0|24|1843200|0
        STRAWBERRY|3|0|H|closed|g3|24|D|0|24|345600|0
        STRAWBERRY|3|0|H|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|3|0|H|closed|mo8|24|D|0|24|1843200|0
        STRAWBERRY|3|1|H|open|g3|24|D|1|24|345600|0
        STRAWBERRY|3|1|H|open|g9|24|D|1|24|1036800|0
        STRAWBERRY|3|1|H|open|mo8|24|D|1|24|1843200|0
        STRAWBERRY|3|1|H|closed|g3|24|D|1|24|345600|0
        STRAWBERRY|3|1|H|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|3|1|H|closed|mo8|24|D|1|24|1843200|0
        STRAWBERRY|3|2|H|open|g3|24|D|2|24|345600|0
        STRAWBERRY|3|2|H|open|g9|24|D|2|24|1036800|0
        STRAWBERRY|3|2|H|open|mo8|24|D|2|24|1843200|0
        STRAWBERRY|3|2|H|closed|g3|24|D|2|24|345600|0
        STRAWBERRY|3|2|H|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|3|2|H|closed|mo8|24|D|2|24|1843200|0
        STRAWBERRY|4|0|H|open|g3|24|D|0|24|345600|0
        STRAWBERRY|4|0|H|open|g9|24|D|0|24|1036800|0
        STRAWBERRY|4|0|H|open|mo8|24|D|0|24|1843200|0
        STRAWBERRY|4|0|H|closed|g3|24|D|0|24|345600|0
        STRAWBERRY|4|0|H|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|4|0|H|closed|mo8|24|D|0|24|1843200|0
        STRAWBERRY|4|1|H|open|g3|24|D|1|24|345600|0
        STRAWBERRY|4|1|H|open|g9|24|D|1|24|1036800|0
        STRAWBERRY|4|1|H|open|mo8|24|D|1|24|1843200|0
        STRAWBERRY|4|1|H|closed|g3|24|D|1|24|345600|0
        STRAWBERRY|4|1|H|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|4|1|H|closed|mo8|24|D|1|24|1843200|0
        STRAWBERRY|4|2|H|open|g3|24|D|2|24|345600|0
        STRAWBERRY|4|2|H|open|g9|24|D|2|24|1036800|0
        STRAWBERRY|4|2|H|open|mo8|24|D|2|24|1843200|0
        STRAWBERRY|4|2|H|closed|g3|24|D|2|24|345600|0
        STRAWBERRY|4|2|H|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|4|2|H|closed|mo8|24|D|2|24|1843200|0
        STRAWBERRY|5|0|H|open|g3|24|D|0|24|345600|0
        STRAWBERRY|5|0|H|open|g9|24|D|0|24|1036800|0
        STRAWBERRY|5|0|H|open|mo8|24|D|0|24|1843200|0
        STRAWBERRY|5|0|H|closed|g3|24|D|0|24|345600|0
        STRAWBERRY|5|0|H|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|5|0|H|closed|mo8|24|D|0|24|1843200|0
        STRAWBERRY|5|1|H|open|g3|24|D|1|24|345600|0
        STRAWBERRY|5|1|H|open|g9|24|D|1|24|1036800|0
        STRAWBERRY|5|1|H|open|mo8|24|D|1|24|1843200|0
        STRAWBERRY|5|1|H|closed|g3|24|D|1|24|345600|0
        STRAWBERRY|5|1|H|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|5|1|H|closed|mo8|24|D|1|24|1843200|0
        STRAWBERRY|5|2|H|open|g3|24|D|2|24|345600|0
        STRAWBERRY|5|2|H|open|g9|24|D|2|24|1036800|0
        STRAWBERRY|5|2|H|open|mo8|24|D|2|24|1843200|0
        STRAWBERRY|5|2|H|closed|g3|24|D|2|24|345600|0
        STRAWBERRY|5|2|H|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|5|2|H|closed|mo8|24|D|2|24|1843200|0
        STRAWBERRY|6|0|H|open|g3|24|D|0|24|345600|0
        STRAWBERRY|6|0|H|open|g9|24|D|0|24|1036800|0
        STRAWBERRY|6|0|H|open|mo8|24|D|0|24|1843200|0
        STRAWBERRY|6|0|H|closed|g3|24|D|0|24|345600|0
        STRAWBERRY|6|0|H|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|6|0|H|closed|mo8|24|D|0|24|1843200|0
        STRAWBERRY|6|1|H|open|g3|24|D|1|24|345600|0
        STRAWBERRY|6|1|H|open|g9|24|D|1|24|1036800|0
        STRAWBERRY|6|1|H|open|mo8|24|D|1|24|1843200|0
        STRAWBERRY|6|1|H|closed|g3|24|D|1|24|345600|0
        STRAWBERRY|6|1|H|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|6|1|H|closed|mo8|24|D|1|24|1843200|0
        STRAWBERRY|6|2|H|open|g3|24|D|2|24|345600|0
        STRAWBERRY|6|2|H|open|g9|24|D|2|24|1036800|0
        STRAWBERRY|6|2|H|open|mo8|24|D|2|24|1843200|0
        STRAWBERRY|6|2|H|closed|g3|24|D|2|24|345600|0
        STRAWBERRY|6|2|H|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|6|2|H|closed|mo8|24|D|2|24|1843200|0
        STRAWBERRY|7|0|H|open|g3|24|D|0|24|345600|0
        STRAWBERRY|7|0|H|open|g9|24|D|0|24|1036800|0
        STRAWBERRY|7|0|H|open|mo8|24|D|0|24|1843200|0
        STRAWBERRY|7|0|H|closed|g3|24|D|0|24|345600|0
        STRAWBERRY|7|0|H|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|7|0|H|closed|mo8|24|D|0|24|1843200|0
        STRAWBERRY|7|1|H|open|g3|24|D|1|24|345600|0
        STRAWBERRY|7|1|H|open|g9|24|D|1|24|1036800|0
        STRAWBERRY|7|1|H|open|mo8|24|D|1|24|1843200|0
        STRAWBERRY|7|1|H|closed|g3|24|D|1|24|345600|0
        STRAWBERRY|7|1|H|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|7|1|H|closed|mo8|24|D|1|24|1843200|0
        STRAWBERRY|7|2|H|open|g3|24|D|2|24|345600|0
        STRAWBERRY|7|2|H|open|g9|24|D|2|24|1036800|0
        STRAWBERRY|7|2|H|open|mo8|24|D|2|24|1843200|0
        STRAWBERRY|7|2|H|closed|g3|24|D|2|24|345600|0
        STRAWBERRY|7|2|H|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|7|2|H|closed|mo8|24|D|2|24|1843200|0
        STRAWBERRY|8|0|H|open|g3|24|D|0|24|345600|0
        STRAWBERRY|8|0|H|open|g9|24|D|0|24|1036800|0
        STRAWBERRY|8|0|H|open|mo8|24|D|0|24|1843200|0
        STRAWBERRY|8|0|H|closed|g3|24|D|0|24|345600|0
        STRAWBERRY|8|0|H|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|8|0|H|closed|mo8|24|D|0|24|1843200|0
        STRAWBERRY|8|1|H|open|g3|24|D|1|24|345600|0
        STRAWBERRY|8|1|H|open|g9|24|D|1|24|1036800|0
        STRAWBERRY|8|1|H|open|mo8|24|D|1|24|1843200|0
        STRAWBERRY|8|1|H|closed|g3|24|D|1|24|345600|0
        STRAWBERRY|8|1|H|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|8|1|H|closed|mo8|24|D|1|24|1843200|0
        STRAWBERRY|8|2|H|open|g3|24|D|2|24|345600|0
        STRAWBERRY|8|2|H|open|g9|24|D|2|24|1036800|0
        STRAWBERRY|8|2|H|open|mo8|24|D|2|24|1843200|0
        STRAWBERRY|8|2|H|closed|g3|24|D|2|24|345600|0
        STRAWBERRY|8|2|H|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|8|2|H|closed|mo8|24|D|2|24|1843200|0
        STRAWBERRY|9|0|H|open|g3|24|H|2|22|0|0
        STRAWBERRY|9|0|H|open|g9|24|H|2|22|0|0
        STRAWBERRY|9|0|H|open|mo8|24|H|2|22|0|1:0:21
        STRAWBERRY|9|0|H|closed|g3|24|H|2|22|0|0
        STRAWBERRY|9|0|H|closed|g9|24|H|2|22|0|0
        STRAWBERRY|9|0|H|closed|mo8|24|H|2|22|0|1:0:21
        STRAWBERRY|9|1|H|open|g3|24|H|2|23|0|1:0:22
        STRAWBERRY|9|1|H|open|g9|24|H|2|23|0|1:0:22
        STRAWBERRY|9|1|H|open|mo8|24|H|2|23|0|1:1:22
        STRAWBERRY|9|1|H|closed|g3|24|H|2|23|0|1:0:22
        STRAWBERRY|9|1|H|closed|g9|24|H|2|23|0|1:0:22
        STRAWBERRY|9|1|H|closed|mo8|24|H|2|23|0|1:1:22
        STRAWBERRY|9|2|H|open|g3|24|H|2|24|0|1:1:23
        STRAWBERRY|9|2|H|open|g9|24|H|2|24|0|1:1:23
        STRAWBERRY|9|2|H|open|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|9|2|H|closed|g3|24|H|2|24|0|1:1:23
        STRAWBERRY|9|2|H|closed|g9|24|H|2|24|0|1:1:23
        STRAWBERRY|9|2|H|closed|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|10|0|H|open|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|10|0|H|open|g9|24|H|2|22|0|1:1:21
        STRAWBERRY|10|0|H|open|mo8|24|H|2|22|0|1:2:21
        STRAWBERRY|10|0|H|closed|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|10|0|H|closed|g9|24|H|2|22|0|1:1:21
        STRAWBERRY|10|0|H|closed|mo8|24|H|2|22|0|1:2:21
        STRAWBERRY|10|1|H|open|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|10|1|H|open|g9|24|H|2|23|0|1:2:22
        STRAWBERRY|10|1|H|open|mo8|24|H|2|23|0|1:3:22
        STRAWBERRY|10|1|H|closed|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|10|1|H|closed|g9|24|H|2|23|0|1:2:22
        STRAWBERRY|10|1|H|closed|mo8|24|H|2|23|0|1:3:22
        STRAWBERRY|10|2|H|open|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|10|2|H|open|g9|24|H|2|24|0|1:3:23
        STRAWBERRY|10|2|H|open|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|10|2|H|closed|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|10|2|H|closed|g9|24|H|2|24|0|1:3:23
        STRAWBERRY|10|2|H|closed|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|11|0|H|open|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|11|0|H|open|g9|24|H|2|22|0|1:3:21
        STRAWBERRY|11|0|H|open|mo8|24|H|2|22|0|1:4:21
        STRAWBERRY|11|0|H|closed|g3|24|H|2|22|0|1:0:21
        STRAWBERRY|11|0|H|closed|g9|24|H|2|22|0|1:3:21
        STRAWBERRY|11|0|H|closed|mo8|24|H|2|22|0|1:4:21
        STRAWBERRY|11|1|H|open|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|11|1|H|open|g9|24|H|2|23|0|1:4:22
        STRAWBERRY|11|1|H|open|mo8|24|H|2|23|0|1:5:22
        STRAWBERRY|11|1|H|closed|g3|24|H|2|23|0|1:1:22
        STRAWBERRY|11|1|H|closed|g9|24|H|2|23|0|1:4:22
        STRAWBERRY|11|1|H|closed|mo8|24|H|2|23|0|1:5:22
        STRAWBERRY|11|2|H|open|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|11|2|H|open|g9|24|H|2|24|0|1:5:23
        STRAWBERRY|11|2|H|open|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|11|2|H|closed|g3|24|H|2|24|0|1:2:23
        STRAWBERRY|11|2|H|closed|g9|24|H|2|24|0|1:5:23
        STRAWBERRY|11|2|H|closed|mo8|24|H|2|24|0|1:7:23
        STRAWBERRY|0|0|D|closed|g9|24|H|2|22|0|1:5:21
        STRAWBERRY|0|0|H|open|g9|1|F|1|0|806400|0
        STRAWBERRY|0|1|D|closed|g9|24|H|2|23|0|1:6:22
        STRAWBERRY|0|1|H|open|g9|1|F|2|0|806400|0
        STRAWBERRY|0|2|D|closed|g9|24|H|2|24|0|1:7:23
        STRAWBERRY|0|2|H|open|g9|1|F|2|1|0|1:7:0
        STRAWBERRY|1|0|D|closed|g9|24|H|2|22|0|1:5:21
        STRAWBERRY|1|0|H|open|g9|1|F|1|0|806400|0
        STRAWBERRY|1|1|D|closed|g9|24|H|2|23|0|1:6:22
        STRAWBERRY|1|1|H|open|g9|1|F|2|0|806400|0
        STRAWBERRY|1|2|D|closed|g9|24|H|2|24|0|1:7:23
        STRAWBERRY|1|2|H|open|g9|1|F|2|1|0|1:7:0
        STRAWBERRY|2|0|D|closed|g9|24|H|2|22|0|1:5:21
        STRAWBERRY|2|0|H|open|g9|1|R|1|0|806400|0
        STRAWBERRY|2|1|D|closed|g9|24|H|2|23|0|1:6:22
        STRAWBERRY|2|1|H|open|g9|1|R|2|0|806400|0
        STRAWBERRY|2|2|D|closed|g9|24|H|2|24|0|1:7:23
        STRAWBERRY|2|2|H|open|g9|1|F|2|1|0|1:7:0
        STRAWBERRY|3|0|D|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|3|0|H|open|g9|1|D|0|1|1036800|0
        STRAWBERRY|3|1|D|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|3|1|H|open|g9|1|D|1|1|1036800|0
        STRAWBERRY|3|2|D|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|3|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|4|0|D|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|4|0|H|open|g9|1|D|0|1|1036800|0
        STRAWBERRY|4|1|D|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|4|1|H|open|g9|1|D|1|1|1036800|0
        STRAWBERRY|4|2|D|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|4|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|5|0|D|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|5|0|H|open|g9|1|D|0|1|1036800|0
        STRAWBERRY|5|1|D|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|5|1|H|open|g9|1|D|1|1|1036800|0
        STRAWBERRY|5|2|D|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|5|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|6|0|D|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|6|0|H|open|g9|1|D|0|1|1036800|0
        STRAWBERRY|6|1|D|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|6|1|H|open|g9|1|D|1|1|1036800|0
        STRAWBERRY|6|2|D|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|6|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|7|0|D|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|7|0|H|open|g9|1|D|0|1|1036800|0
        STRAWBERRY|7|1|D|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|7|1|H|open|g9|1|D|1|1|1036800|0
        STRAWBERRY|7|2|D|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|7|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|8|0|D|closed|g9|24|D|0|24|1036800|0
        STRAWBERRY|8|0|H|open|g9|1|D|0|1|1036800|0
        STRAWBERRY|8|1|D|closed|g9|24|D|1|24|1036800|0
        STRAWBERRY|8|1|H|open|g9|1|D|1|1|1036800|0
        STRAWBERRY|8|2|D|closed|g9|24|D|2|24|1036800|0
        STRAWBERRY|8|2|H|open|g9|1|D|2|1|1036800|0
        STRAWBERRY|9|0|D|closed|g9|24|H|2|22|0|0
        STRAWBERRY|9|0|H|open|g9|1|H|1|0|115200|0
        STRAWBERRY|9|1|D|closed|g9|24|H|2|23|0|1:0:22
        STRAWBERRY|9|1|H|open|g9|1|H|2|0|115200|0
        STRAWBERRY|9|2|D|closed|g9|24|H|2|24|0|1:1:23
        STRAWBERRY|9|2|H|open|g9|1|H|2|1|0|1:1:0
        STRAWBERRY|10|0|D|closed|g9|24|H|2|22|0|1:1:21
        STRAWBERRY|10|0|H|open|g9|1|H|1|0|345600|0
        STRAWBERRY|10|1|D|closed|g9|24|H|2|23|0|1:2:22
        STRAWBERRY|10|1|H|open|g9|1|H|2|0|345600|0
        STRAWBERRY|10|2|D|closed|g9|24|H|2|24|0|1:3:23
        STRAWBERRY|10|2|H|open|g9|1|H|2|1|0|1:3:0
        STRAWBERRY|11|0|D|closed|g9|24|H|2|22|0|1:3:21
        STRAWBERRY|11|0|H|open|g9|1|H|1|0|576000|0
        STRAWBERRY|11|1|D|closed|g9|24|H|2|23|0|1:4:22
        STRAWBERRY|11|1|H|open|g9|1|H|2|0|576000|0
        STRAWBERRY|11|2|D|closed|g9|24|H|2|24|0|1:5:23
        STRAWBERRY|11|2|H|open|g9|1|H|2|1|0|1:5:0
        """;

    private static List<String> rows(String block) {
        return block.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private static String letter(Lifecycle lifecycle) {
        return switch (lifecycle) {
            case DORMANT -> "D";
            case HEALTHY -> "H";
            case FLOWERING -> "F";
            case FRUITING -> "R";
        };
    }

    private static Lifecycle lifecycleOf(String letter) {
        return switch (letter) {
            case "D" -> Lifecycle.DORMANT;
            case "H" -> Lifecycle.HEALTHY;
            case "F" -> Lifecycle.FLOWERING;
            case "R" -> Lifecycle.FRUITING;
            default -> throw new IllegalArgumentException(letter);
        };
    }

    @Test
    void seasonOfEveryMonthMatchesTfcInBothHemispheres() {
        List<String> rows = rows(SEASON);
        assertThat(rows).hasSize(24);
        for (String row : rows) {
            String[] f = row.split("\\|");
            Month month = Month.valueOf(Integer.parseInt(f[1]));
            assertThat(BerryRules.season(month, f[0].equals("north")).name()).as(row).isEqualTo(f[2]);
        }
    }

    @Test
    void lifecycleTablesMatchTfcInBothHemispheres() {
        List<String> rows = rows(LIFECYCLE);
        assertThat(rows).hasSize(144);
        for (String row : rows) {
            String[] f = row.split("\\|");
            Lifecycle actual = BerryRules.lifecycleAt(BushSpecies.valueOf(f[0]), Month.valueOf(Integer.parseInt(f[2])), f[1].equals("north"));
            assertThat(actual.name()).as(row).isEqualTo(f[3]);
        }
    }

    @Test
    void climateRangeMatchesTfcWithInclusiveBounds() {
        List<String> rows = rows(WITHER);
        assertThat(rows).hasSize(176);
        for (String row : rows) {
            String[] f = row.split("\\|");
            boolean inRange = BerryRules.inClimate(BushSpecies.valueOf(f[0]),
                new BerryRules.Sample(Integer.parseInt(f[1]), Float.parseFloat(f[2])));
            assertThat(inRange).as(row).isEqualTo(f[3].equals("1"));
        }
    }

    @Test
    void randomTickMatchesTfcOverMonthsStagesPickPhasesAndElapsedTimes() {
        List<String> rows = rows(TICK);
        assertThat(rows).hasSize(576);
        for (String row : rows) {
            String[] f = row.split("\\|");
            BushSpecies species = BushSpecies.valueOf(f[0]);
            int month = Integer.parseInt(f[1]);
            int stage = Integer.parseInt(f[2]);
            long pickedAt = f[4].equals("open") ? NOW - (BLOOM + 1) : NOW - 1000;
            long elapsed = switch (f[5]) {
                case "g3" -> 3 * GROWTH;
                case "g9" -> 9 * GROWTH;
                case "mo8" -> EIGHT_MONTHS_PLAYER_TICKS;
                default -> throw new IllegalArgumentException(f[5]);
            };
            BerryBush bush = new BerryBush(lifecycleOf(f[3]), stage, Integer.parseInt(f[6]), NOW - elapsed, pickedAt);
            BerryClock clock = new BerryClock(NOW, YEAR_OFFSET + month * CAL_DAY * DAYS_IN_MONTH + 4 * CAL_DAY + CAL_DAY / 2,
                DAYS_IN_MONTH, true);

            BerryRules.Tick tick = BerryRules.randomTick(species, bush, NOW, clock, BerryRules.Settings.DEFAULT);

            String props = tick.propagations().isEmpty() ? "0"
                : tick.propagations().size() + ":" + tick.propagations().get(0).bankedCycles() + ":"
                    + tick.propagations().get(0).growthsBeforeDraw();
            String actual = letter(tick.bush().lifecycle()) + "|" + tick.bush().stage() + "|" + tick.bush().growthsRemaining() + "|"
                + (NOW - tick.bush().lastUpdateTick()) + "|" + props;
            assertThat(actual).as(row).isEqualTo(String.join("|", List.of(f[7], f[8], f[9], f[10], f[11])));
        }
    }
}
