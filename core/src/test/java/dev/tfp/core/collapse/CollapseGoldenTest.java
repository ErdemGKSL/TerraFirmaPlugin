/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.collapse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * M3-PHYS-01 golden values. Every row below was produced by TFC's own collapse decisions at c3b4d9536, ported line by line into a
 * scratch oracle (CollapseRecipe.canStartCollapse, collapseBlock, tryTriggerCollapse, Support.findUnsupportedPositions,
 * TFCFallingBlockEntity.canFallThrough). The oracle's only change is that candidates are taken in sorted (x, y, z) order, since
 * TFC's hash-set order is not reproducible; the Java rules use the same order.
 *
 * <p>Randomness is a fixed 64-bit LCG shared by the oracle and this test ({@code state = state * 6364136223846793005 + 1442695040888963407};
 * {@code nextInt(b) = (state >>> 33) % b}; {@code nextFloat = (state >>> 40) / 2^24}). Only the logic is golden, not Minecraft's RNG.
 *
 * <p>Block codes: A air, R raw granite, S smooth granite, L slab, O poor hematite granite, B bedrock, P oak planks, K horizontal oak
 * support, Y synthetic support (reach 4/1/1), T spike (in {@code can_collapse} only), C granite cobble.
 */
class CollapseGoldenTest {
    static final String AIR = "minecraft:air";
    static final String RAW = "tfc:rock/raw/granite";
    static final String SMOOTH = "tfc:rock/smooth/granite";
    static final String SLAB = "minecraft:stone_slab";
    static final String ORE = "tfc:ore/poor_hematite/granite";
    static final String PLANK = "minecraft:oak_planks";
    static final String OAK = "tfc:wood/horizontal_support/oak";
    static final String SYN = "test:synthetic_support";
    static final String SPIKE = "tfc:rock/spike/granite";
    static final String COBBLE = "tfc:rock/cobble/granite";
    static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    /** Codes, in the order of the oracle's {@code ALL} list. */
    static final String ALL_CODES = "ARSLOBPKYTC";
    static final Map<Character, String> KIND = Map.ofEntries(
        Map.entry('A', AIR), Map.entry('R', RAW), Map.entry('S', SMOOTH), Map.entry('L', SLAB), Map.entry('O', ORE),
        Map.entry('B', CollapseKinds.BEDROCK), Map.entry('P', PLANK), Map.entry('K', OAK), Map.entry('Y', SYN),
        Map.entry('T', SPIKE), Map.entry('C', COBBLE));
    static final String FULL_CODES = "RSOBPC";

    // from TFC at c3b4d9536: support sweep, one beam at each offset dx[-6,6] dy[-3,3] dz[-6,6] (1 = origin supported)
    static final String SUPPORT_SWEEP_OAK = """
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000001111111110000111111111000011111111100001111111110000111111111000000000000000000000000000000
            111111111000011111111100001111111110000111111111000011111111100000000000000000000000000000011111
            111100001111111110000111111111000011111111100001111111110000000000000000000000000000001111111110
            000111111111000011111111100001111111110000111111111000000000000000000000000000000111111111000011
            111111100001111111110000111111111000011111111100000000000000000000000000000011111111100001111111
            110000111111111000011111111100001111111110000000000000000000000000000001111111110000111111111000
            011111111100001111111110000111111111000000000000000000000000000000111111111000011111111100001111
            111110000111111111000011111111100000000000000000000000000000011111111100001111111110000111111111
            000011111111100001111111110000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            0000000000000000000000000000000
            """;
    static final String SUPPORT_SWEEP_SYN = """
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000111000000000011100000000001110000000000111000000000011100000000000000000000000000000000000
            011100000000001110000000000111000000000011100000000001110000000000000000000000000000000000001110
            000000000111000000000011100000000001110000000000111000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
            0000000000000000000000000000000
            """;
    // from TFC at c3b4d9536: 300 random worlds of 0..3 beams at random offsets (OAK or SYN)
    static final String SUPPORT_PAIRS = """
            011000011100101010100110100100110010000010000000100000100100000000000010100000100110001010001000
            100000000000000001100000001000101001001000101001101000000000100001010000000001000000000011010000
            010011010011010011000000010000000000011000010000000110110001110111110100000110100100010100010100
            100100000000
            """;
    // from TFC at c3b4d9536: findUnsupportedPositions over the box (-2,-1,-2)..(2,1,2) on 120 worlds, "count:hash" of the sorted list
    static final String UNSUPPORTED = """
            75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076
            0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0
            75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076
            0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0
            75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076
            0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0
            75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076
            0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0
            75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076
            0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0
            75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076
            0:0 0:0 75:-58498076 75:-58498076 0:0 0:0 75:-58498076 75:-58498076 0:0 0:0
            """;

    // from TFC at c3b4d9536: canStartCollapse, "<kind at pos><kind below><1 startable|0>"
    static final String CAN_START = """
            AA0 AR0 AS0 AL0 AO0 AB0 AP0 AK0 AY0 AT0
            AC0 RA1 RR0 RS1 RL1 RO0 RB0 RP0 RK1 RY1
            RT1 RC0 SA0 SR0 SS0 SL0 SO0 SB0 SP0 SK0
            SY0 ST0 SC0 LA0 LR0 LS0 LL0 LO0 LB0 LP0
            LK0 LY0 LT0 LC0 OA1 OR0 OS1 OL1 OO0 OB0
            OP0 OK1 OY1 OT1 OC0 BA0 BR0 BS0 BL0 BO0
            BB0 BP0 BK0 BY0 BT0 BC0 PA0 PR0 PS0 PL0
            PO0 PB0 PP0 PK0 PY0 PT0 PC0 KA0 KR0 KS0
            KL0 KO0 KB0 KP0 KK0 KY0 KT0 KC0 YA0 YR0
            YS0 YL0 YO0 YB0 YP0 YK0 YY0 YT0 YC0 TA0
            TR0 TS0 TL0 TO0 TB0 TP0 TK0 TY0 TT0 TC0
            CA0 CR0 CS0 CL0 CO0 CB0 CP0 CK0 CY0 CT0
            CC0
            """;

    // from TFC at c3b4d9536: collapseBlock, "<kind><kind below><destroyBlockBelow>:<result or ->" + "<breakBelow>"
    static final String COLLAPSE = """
            AA0:- AA1:- AR0:- AR1:- AS0:- AS1:- AL0:- AL1:- AO0:- AO1:-
            AB0:- AB1:- AP0:- AP1:- AK0:- AK1:- AY0:- AY1:- AT0:- AT1:-
            AC0:- AC1:- RA0:C0 RA1:C0 RR0:C0 RR1:C1 RS0:C0 RS1:C1 RL0:C0 RL1:C0
            RO0:C0 RO1:C1 RB0:C0 RB1:C1 RP0:C0 RP1:C1 RK0:C0 RK1:C0 RY0:C0 RY1:C0
            RT0:C0 RT1:C0 RC0:C0 RC1:C1 SA0:C0 SA1:C0 SR0:C0 SR1:C1 SS0:C0 SS1:C1
            SL0:C0 SL1:C0 SO0:C0 SO1:C1 SB0:C0 SB1:C1 SP0:C0 SP1:C1 SK0:C0 SK1:C0
            SY0:C0 SY1:C0 ST0:C0 ST1:C0 SC0:C0 SC1:C1 LA0:- LA1:- LR0:- LR1:-
            LS0:- LS1:- LL0:- LL1:- LO0:- LO1:- LB0:- LB1:- LP0:- LP1:-
            LK0:- LK1:- LY0:- LY1:- LT0:- LT1:- LC0:- LC1:- OA0:C0 OA1:C0
            OR0:C0 OR1:C1 OS0:C0 OS1:C1 OL0:C0 OL1:C0 OO0:C0 OO1:C1 OB0:C0 OB1:C1
            OP0:C0 OP1:C1 OK0:C0 OK1:C0 OY0:C0 OY1:C0 OT0:C0 OT1:C0 OC0:C0 OC1:C1
            BA0:- BA1:- BR0:- BR1:- BS0:- BS1:- BL0:- BL1:- BO0:- BO1:-
            BB0:- BB1:- BP0:- BP1:- BK0:- BK1:- BY0:- BY1:- BT0:- BT1:-
            BC0:- BC1:- PA0:- PA1:- PR0:- PR1:- PS0:- PS1:- PL0:- PL1:-
            PO0:- PO1:- PB0:- PB1:- PP0:- PP1:- PK0:- PK1:- PY0:- PY1:-
            PT0:- PT1:- PC0:- PC1:- KA0:- KA1:- KR0:- KR1:- KS0:- KS1:-
            KL0:- KL1:- KO0:- KO1:- KB0:- KB1:- KP0:- KP1:- KK0:- KK1:-
            KY0:- KY1:- KT0:- KT1:- KC0:- KC1:- YA0:- YA1:- YR0:- YR1:-
            YS0:- YS1:- YL0:- YL1:- YO0:- YO1:- YB0:- YB1:- YP0:- YP1:-
            YK0:- YK1:- YY0:- YY1:- YT0:- YT1:- YC0:- YC1:- TA0:T0 TA1:T0
            TR0:T0 TR1:T1 TS0:T0 TS1:T1 TL0:T0 TL1:T0 TO0:T0 TO1:T1 TB0:T0 TB1:T1
            TP0:T0 TP1:T1 TK0:T0 TK1:T0 TY0:T0 TY1:T0 TT0:T0 TT1:T0 TC0:T0 TC1:T1
            CA0:- CA1:- CR0:- CR1:- CS0:- CS1:- CL0:- CL1:- CO0:- CO1:-
            CB0:- CB1:- CP0:- CP1:- CK0:- CK1:- CY0:- CY1:- CT0:- CT1:-
            CC0:- CC1:-
            """;

    // from TFC at c3b4d9536: the rubble yield of each kind (recipe result, or the kind itself for can_collapse, or -)
    static final String RUBBLE = """
            A:- R:C S:C L:- O:C B:- P:- K:- Y:- T:T
            C:-
            """;

    // from TFC at c3b4d9536: 200 mining events on random worlds (mined block raw or ore, split into 4 beam densities).
    // N = nothing, Rx,y,z = real collapse at x,y,z, Fn;... = fake collapse with n startable positions and the displayed sample.
    static final String TRIGGER = """
            R-2,0,-2 N N N
            N N N N
            F45;-1,2,-1;4,2,1;-2,0,-2;-3,-1,-3;-2,1,1;-4,2,0;-1,2,-3;3,0,3;4,-1,-3;-3,1,-4;-1,1,-4;-2,2,-3 R-4,2,-1 N N
            N N N N
            N F14;-4,0,1;-3,0,0;-3,1,1;1,1,1;4,1,0;4,1,-1 R-4,-2,-1 N
            F26;-1,1,3;0,1,-4;2,2,1;-1,0,-4;1,-1,0;-2,0,3;2,0,3;-2,-1,-2;-2,2,-1;0,0,-3;-1,-2,2;-1,-2,4 N N N
            N N N N
            F14;1,0,-3;-2,0,-4;-1,1,1;1,1,-1 R-3,1,1 F4;-2,0,1;-1,1,2;-1,1,-1 N
            N N N N
            N N R-3,0,1 N
            F19;-1,2,2;0,-1,-1;-2,1,-3;1,1,-1;1,1,2;1,-2,-3;-2,2,-1;0,2,-3;-2,0,3;2,1,-3 F44;-3,0,1;2,0,2;-4,1,-1;4,-2,0;4,0,3;1,1,2;-1,0,-4;3,1,-1;-1,1,-2;0,1,1;-2,-2,4;0,1,-3 N N
            N N N N
            N R-4,-2,0 F12;-1,0,-2;2,1,1;2,0,-2;-1,1,-1;0,0,1;0,1,2;2,1,-1;-2,1,2;0,1,-1;-2,1,1 N
            F10;-2,1,3;1,0,2;1,0,0 N N N
            N N R-4,0,-3 N
            F12;3,1,2;3,0,3;2,1,2;-1,0,1;-3,0,3;1,1,-2 F10;-1,0,-2;1,0,3;1,1,-1;-2,0,2;-2,0,3;2,1,-2;-1,1,-1 F19;1,2,-3;2,0,2;-2,0,3;-2,0,-3;-1,-2,-2;-1,-1,-1;2,2,1;0,2,3;1,2,-1;-1,2,2 N
            N N N N
            N N F22;4,0,-4;-3,0,3;4,1,-1;-1,1,1;0,-1,2;2,1,-3;1,1,1;0,-1,0;-1,1,0;-1,1,-4;3,1,2;3,1,-4 N
            F5;1,0,0;-2,1,0;1,-1,-1 F9;2,1,2;2,1,-2;-2,1,0;-2,-1,2;0,0,-3;-1,-1,0;-2,1,1 N N
            N N R-2,1,-4 N
            N F23;-1,1,-1;-3,2,-1;-3,1,0;-1,1,0;-1,0,-2;0,1,-1;1,2,-2;-1,0,2;2,0,-1;3,1,2;-3,-2,-1;-2,2,2 F22;1,2,1;0,0,1;-1,0,2 N
            F14;2,2,2;3,-2,-1;0,-2,-2 N N N
            N N N N
            F11;3,0,2;2,0,0;1,0,-1;2,0,-2;0,0,-1;2,1,1;-2,1,-1;3,1,1 F18;-2,1,-2;2,1,2;-3,1,1;-3,0,2;2,1,-2;-3,1,-2;3,1,-2;3,0,0;3,0,1;-3,0,0;-1,1,-2;1,0,2 F21;-3,0,3;-1,0,0;1,1,3;1,1,-3;0,0,-1;-3,-1,0;2,0,-2;-2,1,2;-2,0,-1;-1,1,1;2,0,3;1,0,-2 N
            N N R-4,1,3 N
            N N F24;3,1,-1;-2,2,0;0,-1,-2;1,2,-2;-1,0,-1;-2,0,-2;1,2,3;-3,2,2;-3,0,-1;1,2,0;1,2,2;2,2,-1 N
            F31;-3,2,2;1,-2,-2;0,2,1;2,2,3;-4,-2,-2;2,1,0;4,1,3;3,2,2;1,0,3;-1,2,-3;3,1,3;-3,1,1 F12;3,0,2;-2,0,1;2,1,-2 N N
            N N N N
            N F13;1,1,2;-4,1,0;-1,0,1;4,1,1 F14;-2,0,-1;-2,-1,1;2,0,-1;-4,0,-1;4,1,1;2,0,1;-1,1,-2;2,0,-2;0,0,1;-1,1,0;4,1,-2;-4,1,1 N
            N N N N
            N N N N
            F10;2,0,-1;1,1,-1;-2,1,-3;-2,1,1;2,0,2 F13;2,1,-2;0,1,1;1,0,3;0,1,-2 F9;1,0,1;0,0,-1;-2,1,0 N
            N N N N
            N N F5;2,0,-2;-1,0,-1;0,1,1 N
            R-3,2,1 F22;-4,1,3;1,0,0;4,0,-2;-3,0,0;3,1,2;-1,0,1;-1,1,3;0,0,1;1,1,-1;4,0,3;-2,0,-3;-2,1,3 N N
            N N N N
            F18;3,2,-1;-1,-2,-2;1,-2,-3;-1,0,2;-2,1,-3;0,-2,-3;0,1,-2;0,1,-3;-1,0,1 F23;3,1,-3;2,0,3;0,2,0;-3,2,1;0,2,-3;1,1,0;-2,1,1;1,2,2;3,0,2;3,2,3;-1,2,-1;-1,-2,0 F19;-4,1,2;1,0,1;3,0,-3;4,1,2;2,-1,-2;-3,-1,1;4,-1,0;-3,0,-3;4,1,3;-4,-1,3;3,1,0;3,0,1 N
            N N N N
            N N N N
            R-1,0,1 F12;-2,1,2;0,0,-1;-2,1,0 F25;-2,-2,0;2,0,3;-2,1,0 N
            N N N N
            N N F13;-2,0,0;0,1,3;2,-1,-3;-1,1,-3 N
            F16;2,1,2;1,0,1;0,-1,3;0,0,-2;-1,0,-2;1,0,0;-1,0,3;0,1,1;1,1,-2;-2,0,-1;2,0,1;-2,1,-3 N N N
            N N N N
            R-3,0,-1 F17;2,2,0;-1,-2,-2;-3,1,-1;-1,2,0;-2,0,-1;0,1,1;-2,1,-2;-2,2,1;0,-1,-1;0,0,-2;2,2,2;2,2,1 F14;0,0,-2;2,-2,-2;3,1,-2;-3,0,0;1,0,-1;-1,1,0;-3,0,2;2,-2,2 N
            N N N N
            N R-3,0,-1 N N
            F15;1,1,0;1,1,-2;-3,0,-3;3,1,1;2,1,-3 F21;-2,0,0;1,0,1;-2,-1,2;2,1,-2;-3,0,2;2,0,1 F14;-3,1,-2;1,1,0;-2,-1,2;-1,0,1;2,0,-1;2,0,1;0,0,2;-2,0,0;1,0,1;1,1,2;3,0,-2 N
            N N N N
            R-2,0,1 N F22;3,2,2;2,2,-2;3,0,2;-2,2,1;-3,1,-1;2,0,-2;1,2,-1;0,2,1;-1,0,-2;-3,2,0;-1,2,-1;-3,1,1 N
            """;

    /** The ingredients of TFC's data/tfc/recipe/collapse/rock/cobble/granite.json, all resolved to the granite cobble result. */
    static final String GRANITE_COBBLE_INGREDIENTS = """
            tfc:rock/raw/granite tfc:rock/hardened/granite tfc:rock/smooth/granite
            tfc:rock/cracked_bricks/granite tfc:ore/poor_native_copper/granite tfc:ore/poor_native_gold/granite
            tfc:ore/poor_hematite/granite tfc:ore/poor_native_silver/granite tfc:ore/poor_cassiterite/granite
            tfc:ore/poor_bismuthinite/granite tfc:ore/poor_garnierite/granite tfc:ore/poor_malachite/granite
            tfc:ore/poor_magnetite/granite tfc:ore/poor_limonite/granite tfc:ore/poor_sphalerite/granite
            tfc:ore/poor_tetrahedrite/granite tfc:ore/gypsum/granite tfc:ore/cinnabar/granite
            tfc:ore/cryolite/granite tfc:ore/borax/granite tfc:ore/graphite/granite
            tfc:ore/saltpeter/granite tfc:ore/sulfur/granite tfc:ore/sylvite/granite
            tfc:ore/amethyst/granite tfc:ore/diamond/granite tfc:ore/emerald/granite
            tfc:ore/lapis_lazuli/granite tfc:ore/opal/granite tfc:ore/pyrite/granite
            tfc:ore/ruby/granite tfc:ore/sapphire/granite tfc:ore/topaz/granite
            """;

    static final String GOLDEN_RECIPE_RESULT = "tfc:rock/cobble/granite";

    static SupportTable supports() {
        return new SupportTable(List.of(
            new SupportTable.Support(Set.of(OAK), 2, 2, 4),
            new SupportTable.Support(Set.of(SYN), 4, 1, 1)));
    }

    static CollapseKinds kinds() {
        return CollapseKinds.builder()
            .canStart(RAW, ORE)
            .canTrigger(RAW, SMOOTH, ORE)
            .notSolidSupporting(SMOOTH)
            .canCollapse(RAW, SMOOTH, ORE, SPIKE)
            .toughness(RAW, 2)
            .toughness(SMOOTH, 2)
            .toughness(COBBLE, 2)
            .recipe(COBBLE, RAW, SMOOTH, ORE)
            .build();
    }

    static CollapseRules rules() {
        return new CollapseRules(kinds(), supports(), CollapseRules.DEFAULT_TRIGGER_CHANCE, CollapseRules.DEFAULT_FAKE_TRIGGER_CHANCE);
    }

    static CollapseCell cell(char code) {
        boolean full = FULL_CODES.indexOf(code) >= 0;
        return new CollapseCell(KIND.get(code), full, full);
    }

    static CollapseView view(Map<BlockPos, Character> blocks) {
        return pos -> cell(blocks.getOrDefault(pos, 'A'));
    }

    static char codeOf(String kind) {
        for (Map.Entry<Character, String> e : KIND.entrySet()) {
            if (e.getValue().equals(kind)) {
                return e.getKey();
            }
        }
        throw new AssertionError("no code for " + kind);
    }

    static List<String> tokens(String text) {
        return List.of(text.strip().split("\\s+"));
    }

    static String bits(String text) {
        return text.replaceAll("\\s", "");
    }

    static String pos(BlockPos p) {
        return p.x() + "," + p.y() + "," + p.z();
    }

    static String format(TriggerDecision d) {
        if (d instanceof TriggerDecision.Real r) {
            return "R" + pos(r.start());
        }
        if (d instanceof TriggerDecision.Fake f) {
            List<String> parts = new ArrayList<>();
            for (BlockPos p : f.display()) {
                parts.add(pos(p));
            }
            return "F" + f.starts().size() + ";" + String.join(";", parts);
        }
        return "N";
    }

    /** The oracle's LCG. Shared by the scenario generators below. */
    static final class Lcg implements CollapseRandom {
        private long state;

        Lcg(long seed) {
            this.state = seed;
        }

        private long next() {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return state;
        }

        @Override
        public int nextInt(int bound) {
            return (int) ((next() >>> 33) % bound);
        }

        @Override
        public float nextFloat() {
            return (next() >>> 40) / 16777216.0f;
        }
    }

    @Test
    void supportSweepMatchesTfc() {
        CollapseRules rules = rules();
        for (String kind : List.of(OAK, SYN)) {
            StringBuilder sb = new StringBuilder();
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        Map<BlockPos, Character> w = new HashMap<>();
                        w.put(new BlockPos(dx, dy, dz), kind.equals(OAK) ? 'K' : 'Y');
                        sb.append(supports().isSupported(view(w), ORIGIN) ? '1' : '0');
                    }
                }
            }
            assertThat(sb.toString()).isEqualTo(bits(kind.equals(OAK) ? SUPPORT_SWEEP_OAK : SUPPORT_SWEEP_SYN));
        }
    }

    @Test
    void supportPairsMatchTfc() {
        CollapseRules rules = rules();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            Lcg r = new Lcg(0x5EED0000L + i);
            Map<BlockPos, Character> w = new HashMap<>();
            for (int n = r.nextInt(4); n > 0; n--) {
                char kind = r.nextInt(2) == 0 ? 'K' : 'Y';
                BlockPos off = new BlockPos(r.nextInt(13) - 6, r.nextInt(7) - 3, r.nextInt(13) - 6);
                w.put(off, kind);
            }
            sb.append(supports().isSupported(view(w), ORIGIN) ? '1' : '0');
        }
        assertThat(sb.toString()).isEqualTo(bits(SUPPORT_PAIRS));
    }

    @Test
    void unsupportedMatchesTfc() {
        CollapseRules rules = rules();
        String unCodes = "ARLPSOB";
        int[] beamAt14 = {0, 0, 1, 2};
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            Lcg r = new Lcg(0xB0A00000L + i);
            Map<BlockPos, Character> w = new HashMap<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int roll = r.nextInt(14);
                        char code = roll < beamAt14[i % 4] ? (roll % 2 == 0 ? 'K' : 'Y') : unCodes.charAt(roll % 7);
                        w.put(new BlockPos(dx, dy, dz), code);
                    }
                }
            }
            List<BlockPos> unsupported = supports().unsupported(view(w), new BlockPos(-2, -1, -2), new BlockPos(2, 1, 2));
            int h = 0;
            for (BlockPos p : unsupported) {
                h = 31 * h + ((p.x() * 73856093) ^ (p.y() * 19349663) ^ (p.z() * 83492791));
            }
            rows.add(unsupported.size() + ":" + h);
        }
        assertThat(rows).containsExactlyElementsOf(tokens(UNSUPPORTED));
    }

    @Test
    void canStartCollapseMatchesTfc() {
        CollapseRules rules = rules();
        List<String> rows = new ArrayList<>();
        for (char a : ALL_CODES.toCharArray()) {
            for (char b : ALL_CODES.toCharArray()) {
                Map<BlockPos, Character> w = Map.of(ORIGIN, a, new BlockPos(0, -1, 0), b);
                rows.add("" + a + b + (rules.canStartCollapse(view(w), ORIGIN) ? '1' : '0'));
            }
        }
        assertThat(rows).containsExactlyElementsOf(tokens(CAN_START));
    }

    @Test
    void collapseMatchesTfc() {
        CollapseRules rules = rules();
        List<String> rows = new ArrayList<>();
        for (char a : ALL_CODES.toCharArray()) {
            for (char b : ALL_CODES.toCharArray()) {
                for (int f = 0; f <= 1; f++) {
                    Map<BlockPos, Character> w = Map.of(ORIGIN, a, new BlockPos(0, -1, 0), b);
                    Optional<CollapseRules.CollapseStep> step = rules.collapse(view(w), ORIGIN, f == 1);
                    String result = step.map(s -> String.valueOf(codeOf(s.result()))).orElse("-");
                    String brk = step.map(s -> s.breakBelow() ? "1" : "0").orElse("");
                    rows.add("" + a + b + f + ":" + result + brk);
                }
            }
        }
        assertThat(rows).containsExactlyElementsOf(tokens(COLLAPSE));
    }

    @Test
    void rubbleMatchesTfc() {
        CollapseRules rules = rules();
        List<String> rows = new ArrayList<>();
        for (char c : ALL_CODES.toCharArray()) {
            Optional<String> r = rules.rubble(KIND.get(c));
            rows.add(c + ":" + r.map(k -> String.valueOf(codeOf(k))).orElse("-"));
        }
        assertThat(rows).containsExactlyElementsOf(tokens(RUBBLE));
    }

    @Test
    void triggerMatchesTfcOnScriptedWorlds() {
        CollapseRules rules = rules();
        String lower = "RRSOPBRRRP";
        String upper = "AAAARSOLLP";
        int[] beamAt40 = {0, 0, 0, 1};
        List<String> rows = new ArrayList<>();
        for (int c = 0; c < 200; c++) {
            Lcg worldRng = new Lcg(1000003L * c + 17);
            Lcg trigRng = new Lcg(1000003L * c + 29);
            Map<BlockPos, Character> w = new HashMap<>();
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        int r = worldRng.nextInt(40);
                        char code = r < beamAt40[c % 4] ? (r % 2 == 0 ? 'K' : 'Y') : (dy <= -2 ? lower : upper).charAt(r % 10);
                        w.put(new BlockPos(dx, dy, dz), code);
                    }
                }
            }
            w.put(ORIGIN, c % 2 == 0 ? 'R' : 'O');
            rows.add(format(rules.trigger(view(w), ORIGIN, trigRng)));
        }
        assertThat(rows).containsExactlyElementsOf(tokens(TRIGGER));
    }

    @Test
    void granitePackRecipeResolvesEveryIngredientToCobble() {
        CollapseRules rules = rules();
        CollapseKinds kinds = CollapseKinds.builder()
            .recipe(GOLDEN_RECIPE_RESULT, tokens(GRANITE_COBBLE_INGREDIENTS).toArray(String[]::new))
            .canCollapse(SPIKE)
            .build();
        for (String ingredient : tokens(GRANITE_COBBLE_INGREDIENTS)) {
            assertThat(kinds.rubble(ingredient)).contains(GOLDEN_RECIPE_RESULT);
        }
        assertThat(kinds.rubble(SPIKE)).contains(SPIKE);
        assertThat(kinds.rubble(AIR)).isEmpty();
        assertThat(rules.canCollapse(RAW)).isTrue();
        assertThat(rules.canCollapse(SLAB)).isFalse();
    }
}
