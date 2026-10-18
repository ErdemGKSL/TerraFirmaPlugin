/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.collapse.CollapseKinds;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * M3-PHYS-02 golden values. Every row was produced by a scratch oracle that ports TFC's landslide decision at c3b4d9536:
 * LandslideRecipe.getLandslidePos and isSupportedOnSide, TFCFallingBlockEntity.canFallThrough and getBlockToughness, and
 * Support.isSupported. The toughness of each palette kind comes from TFC's toughness_1..3 tags, resolved through the #c: tags.
 *
 * <p>Each row is a 3 x 3 x 3 box of codes around the origin in index order (dy, then dz, then dx, each from -1 to 1), a space, and
 * eight decisions for the seeds 0 to 7. A decision is '-' for none, 'F' for a fall, and 'n', 'e', 's', 'w' for a slide north, east,
 * south or west. The random draws use a 64-bit LCG shared with the oracle: state = state * 6364136223846793005 + 1442695040888963407,
 * nextInt(b) = (state >>> 33) % b. Only the logic is golden, not Minecraft's generator.
 *
 * <p>Rows: a sweep over the ten neighbours that decide a landslide (each set to each palette code) over an air box with a dirt centre,
 * the same sweep over a box with stone below, then 120 seeded scenarios. The codes are in {@link #PALETTE}.
 */
class LandslideGoldenTest {
    static final Direction[] FULL = Direction.values();
    static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    static final int ROW_COUNT = 440;

    /** Rows of "box decisions" as described above. */
    static final String ROWS = """
        .............D............. FFFFFFFF
        .............D........D.... FFFFFFFF
        .............D........G.... FFFFFFFF
        .............D........Q.... FFFFFFFF
        .............D........V.... FFFFFFFF
        .............D........S.... FFFFFFFF
        .............D........C.... FFFFFFFF
        .............D........R.... FFFFFFFF
        .............D........L.... FFFFFFFF
        .............D........T.... FFFFFFFF
        .............D........F.... FFFFFFFF
        .............D........P.... FFFFFFFF
        .............D........B.... FFFFFFFF
        .............D........O.... FFFFFFFF
        .............D........X.... FFFFFFFF
        .............D........Y.... --------
        .............D............. FFFFFFFF
        ....D........D............. wsnwsnws
        ....G........D............. wsnwsnws
        ....Q........D............. wsnwsnws
        ....V........D............. wsnwsnws
        ....S........D............. wsnwsnws
        ....C........D............. wsnwsnws
        ....R........D............. wsnwsnws
        ....L........D............. FFFFFFFF
        ....T........D............. wsnwsnws
        ....F........D............. FFFFFFFF
        ....P........D............. FFFFFFFF
        ....B........D............. wsnwsnws
        ....O........D............. FFFFFFFF
        ....X........D............. wsnwsnws
        ....Y........D............. --------
        .............D............. FFFFFFFF
        ............DD............. FFFFFFFF
        ............GD............. FFFFFFFF
        ............QD............. FFFFFFFF
        ............VD............. FFFFFFFF
        ............SD............. FFFFFFFF
        ............CD............. FFFFFFFF
        ............RD............. FFFFFFFF
        ............LD............. FFFFFFFF
        ............TD............. FFFFFFFF
        ............FD............. FFFFFFFF
        ............PD............. FFFFFFFF
        ............BD............. FFFFFFFF
        ............OD............. FFFFFFFF
        ............XD............. FFFFFFFF
        ............YD............. --------
        .............D............. FFFFFFFF
        .............DD............ FFFFFFFF
        .............DG............ FFFFFFFF
        .............DQ............ FFFFFFFF
        .............DV............ FFFFFFFF
        .............DS............ FFFFFFFF
        .............DC............ FFFFFFFF
        .............DR............ FFFFFFFF
        .............DL............ FFFFFFFF
        .............DT............ FFFFFFFF
        .............DF............ FFFFFFFF
        .............DP............ FFFFFFFF
        .............DB............ FFFFFFFF
        .............DO............ FFFFFFFF
        .............DX............ FFFFFFFF
        .............DY............ --------
        .............D............. FFFFFFFF
        ..........D..D............. FFFFFFFF
        ..........G..D............. FFFFFFFF
        ..........Q..D............. FFFFFFFF
        ..........V..D............. FFFFFFFF
        ..........S..D............. FFFFFFFF
        ..........C..D............. FFFFFFFF
        ..........R..D............. FFFFFFFF
        ..........L..D............. FFFFFFFF
        ..........T..D............. FFFFFFFF
        ..........F..D............. FFFFFFFF
        ..........P..D............. FFFFFFFF
        ..........B..D............. FFFFFFFF
        ..........O..D............. FFFFFFFF
        ..........X..D............. FFFFFFFF
        ..........Y..D............. --------
        .............D............. FFFFFFFF
        .............D..D.......... FFFFFFFF
        .............D..G.......... FFFFFFFF
        .............D..Q.......... FFFFFFFF
        .............D..V.......... FFFFFFFF
        .............D..S.......... FFFFFFFF
        .............D..C.......... FFFFFFFF
        .............D..R.......... FFFFFFFF
        .............D..L.......... FFFFFFFF
        .............D..T.......... FFFFFFFF
        .............D..F.......... FFFFFFFF
        .............D..P.......... FFFFFFFF
        .............D..B.......... FFFFFFFF
        .............D..O.......... FFFFFFFF
        .............D..X.......... FFFFFFFF
        .............D..Y.......... --------
        .............D............. FFFFFFFF
        ...D.........D............. FFFFFFFF
        ...G.........D............. FFFFFFFF
        ...Q.........D............. FFFFFFFF
        ...V.........D............. FFFFFFFF
        ...S.........D............. FFFFFFFF
        ...C.........D............. FFFFFFFF
        ...R.........D............. FFFFFFFF
        ...L.........D............. FFFFFFFF
        ...T.........D............. FFFFFFFF
        ...F.........D............. FFFFFFFF
        ...P.........D............. FFFFFFFF
        ...B.........D............. FFFFFFFF
        ...O.........D............. FFFFFFFF
        ...X.........D............. FFFFFFFF
        ...Y.........D............. --------
        .............D............. FFFFFFFF
        .....D.......D............. FFFFFFFF
        .....G.......D............. FFFFFFFF
        .....Q.......D............. FFFFFFFF
        .....V.......D............. FFFFFFFF
        .....S.......D............. FFFFFFFF
        .....C.......D............. FFFFFFFF
        .....R.......D............. FFFFFFFF
        .....L.......D............. FFFFFFFF
        .....T.......D............. FFFFFFFF
        .....F.......D............. FFFFFFFF
        .....P.......D............. FFFFFFFF
        .....B.......D............. FFFFFFFF
        .....O.......D............. FFFFFFFF
        .....X.......D............. FFFFFFFF
        .....Y.......D............. --------
        .............D............. FFFFFFFF
        .D...........D............. FFFFFFFF
        .G...........D............. FFFFFFFF
        .Q...........D............. FFFFFFFF
        .V...........D............. FFFFFFFF
        .S...........D............. FFFFFFFF
        .C...........D............. FFFFFFFF
        .R...........D............. FFFFFFFF
        .L...........D............. FFFFFFFF
        .T...........D............. FFFFFFFF
        .F...........D............. FFFFFFFF
        .P...........D............. FFFFFFFF
        .B...........D............. FFFFFFFF
        .O...........D............. FFFFFFFF
        .X...........D............. FFFFFFFF
        .Y...........D............. --------
        .............D............. FFFFFFFF
        .......D.....D............. FFFFFFFF
        .......G.....D............. FFFFFFFF
        .......Q.....D............. FFFFFFFF
        .......V.....D............. FFFFFFFF
        .......S.....D............. FFFFFFFF
        .......C.....D............. FFFFFFFF
        .......R.....D............. FFFFFFFF
        .......L.....D............. FFFFFFFF
        .......T.....D............. FFFFFFFF
        .......F.....D............. FFFFFFFF
        .......P.....D............. FFFFFFFF
        .......B.....D............. FFFFFFFF
        .......O.....D............. FFFFFFFF
        .......X.....D............. FFFFFFFF
        .......Y.....D............. --------
        ....R........D............. wsnwsnws
        ....R........D........D.... --------
        ....R........D........G.... --------
        ....R........D........Q.... --------
        ....R........D........V.... --------
        ....R........D........S.... --------
        ....R........D........C.... --------
        ....R........D........R.... --------
        ....R........D........L.... --------
        ....R........D........T.... wsnwsnws
        ....R........D........F.... --------
        ....R........D........P.... --------
        ....R........D........B.... --------
        ....R........D........O.... wsnwsnws
        ....R........D........X.... wsnwsnws
        ....R........D........Y.... --------
        .............D............. FFFFFFFF
        ....D........D............. wsnwsnws
        ....G........D............. wsnwsnws
        ....Q........D............. wsnwsnws
        ....V........D............. wsnwsnws
        ....S........D............. wsnwsnws
        ....C........D............. wsnwsnws
        ....R........D............. wsnwsnws
        ....L........D............. FFFFFFFF
        ....T........D............. wsnwsnws
        ....F........D............. FFFFFFFF
        ....P........D............. FFFFFFFF
        ....B........D............. wsnwsnws
        ....O........D............. FFFFFFFF
        ....X........D............. wsnwsnws
        ....Y........D............. --------
        ....R........D............. wsnwsnws
        ....R.......DD............. ssessess
        ....R.......GD............. ssessess
        ....R.......QD............. ssessess
        ....R.......VD............. ssessess
        ....R.......SD............. ssessess
        ....R.......CD............. ssessess
        ....R.......RD............. ssessess
        ....R.......LD............. wsnwsnws
        ....R.......TD............. wsnwsnws
        ....R.......FD............. ssessess
        ....R.......PD............. ssessess
        ....R.......BD............. ssessess
        ....R.......OD............. wsnwsnws
        ....R.......XD............. ssessess
        ....R.......YD............. --------
        ....R........D............. wsnwsnws
        ....R........DD............ wwswwsww
        ....R........DG............ wwswwsww
        ....R........DQ............ wwswwsww
        ....R........DV............ wwswwsww
        ....R........DS............ wwswwsww
        ....R........DC............ wwswwsww
        ....R........DR............ wwswwsww
        ....R........DL............ wsnwsnws
        ....R........DT............ wsnwsnws
        ....R........DF............ wwswwsww
        ....R........DP............ wwswwsww
        ....R........DB............ wwswwsww
        ....R........DO............ wsnwsnws
        ....R........DX............ wwswwsww
        ....R........DY............ --------
        ....R........D............. wsnwsnws
        ....R.....D..D............. wwswwsww
        ....R.....G..D............. wwswwsww
        ....R.....Q..D............. wwswwsww
        ....R.....V..D............. wwswwsww
        ....R.....S..D............. wwswwsww
        ....R.....C..D............. wwswwsww
        ....R.....R..D............. wwswwsww
        ....R.....L..D............. wsnwsnws
        ....R.....T..D............. wsnwsnws
        ....R.....F..D............. wwswwsww
        ....R.....P..D............. wwswwsww
        ....R.....B..D............. wwswwsww
        ....R.....O..D............. wsnwsnws
        ....R.....X..D............. wwswwsww
        ....R.....Y..D............. --------
        ....R........D............. wsnwsnws
        ....R........D..D.......... wwewweww
        ....R........D..G.......... wwewweww
        ....R........D..Q.......... wwewweww
        ....R........D..V.......... wwewweww
        ....R........D..S.......... wwewweww
        ....R........D..C.......... wwewweww
        ....R........D..R.......... wwewweww
        ....R........D..L.......... wsnwsnws
        ....R........D..T.......... wsnwsnws
        ....R........D..F.......... wwewweww
        ....R........D..P.......... wwewweww
        ....R........D..B.......... wwewweww
        ....R........D..O.......... wsnwsnws
        ....R........D..X.......... wwewweww
        ....R........D..Y.......... --------
        ....R........D............. wsnwsnws
        ...DR........D............. ssessess
        ...GR........D............. ssessess
        ...QR........D............. ssessess
        ...VR........D............. ssessess
        ...SR........D............. ssessess
        ...CR........D............. ssessess
        ...RR........D............. ssessess
        ...LR........D............. wsnwsnws
        ...TR........D............. ssessess
        ...FR........D............. wsnwsnws
        ...PR........D............. wsnwsnws
        ...BR........D............. ssessess
        ...OR........D............. wsnwsnws
        ...XR........D............. ssessess
        ...YR........D............. --------
        ....R........D............. wsnwsnws
        ....RD.......D............. wwswwsww
        ....RG.......D............. wwswwsww
        ....RQ.......D............. wwswwsww
        ....RV.......D............. wwswwsww
        ....RS.......D............. wwswwsww
        ....RC.......D............. wwswwsww
        ....RR.......D............. wwswwsww
        ....RL.......D............. wsnwsnws
        ....RT.......D............. wwswwsww
        ....RF.......D............. wsnwsnws
        ....RP.......D............. wsnwsnws
        ....RB.......D............. wwswwsww
        ....RO.......D............. wsnwsnws
        ....RX.......D............. wwswwsww
        ....RY.......D............. --------
        ....R........D............. wsnwsnws
        .D..R........D............. wwswwsww
        .G..R........D............. wwswwsww
        .Q..R........D............. wwswwsww
        .V..R........D............. wwswwsww
        .S..R........D............. wwswwsww
        .C..R........D............. wwswwsww
        .R..R........D............. wwswwsww
        .L..R........D............. wsnwsnws
        .T..R........D............. wwswwsww
        .F..R........D............. wsnwsnws
        .P..R........D............. wsnwsnws
        .B..R........D............. wwswwsww
        .O..R........D............. wsnwsnws
        .X..R........D............. wwswwsww
        .Y..R........D............. --------
        ....R........D............. wsnwsnws
        ....R..D.....D............. wwewweww
        ....R..G.....D............. wwewweww
        ....R..Q.....D............. wwewweww
        ....R..V.....D............. wwewweww
        ....R..S.....D............. wwewweww
        ....R..C.....D............. wwewweww
        ....R..R.....D............. wwewweww
        ....R..L.....D............. wsnwsnws
        ....R..T.....D............. wwewweww
        ....R..F.....D............. wsnwsnws
        ....R..P.....D............. wsnwsnws
        ....R..B.....D............. wwewweww
        ....R..O.....D............. wsnwsnws
        ....R..X.....D............. wwewweww
        ....R..Y.....D............. --------
        R..VGTCG.V.LRXV......GG.VVQ --------
        L.XD.R.SF..B.GQLRQBR..Y.T.P --------
        .GPTGOX.B....T.GGO.TT.OGPF. eeeeeeee
        .YR..BG.....LXGQ.TT..OS.CSS --------
        D.G.C.FVVX.RCDF.G..OG..Y.LV --------
        BR...YD.BOD..B.O..TDFTS.L.S --------
        ..O...LPF..OOD.DR...QBD.P.. FFFFFFFF
        ...OPBYCR..YOQ.S.S...X...QB --------
        ..Q..LLRY....V..Y..B...V... --------
        B.F.Y.OR...YQV.SR...VT..C.P --------
        .GRS..B.BDCLFQYP.FQB...X..C --------
        VXQ..QPD.DQ.FVG.XOS.GG..PY. --------
        ....S..QP.D.QG..CS....Q.... --------
        SDR.C..S.DLR.S.PC..CB....O. weeweewe
        SLF....G....RS.D.X.Y..QT..C --------
        ..F....T....GC.FY.C...OXBT. --------
        ..X.BP.F.GO..R.....T.SOQC.O wsnwsnws
        .SGRV.....GX.LF..T....D..V. --------
        S...FQ.D....CD...PGD..C.GPC FFFFFFFF
        ..DOL..XVY.G.GSGCCCP.GF...X --------
        .T.RO.....V.QRGY.O.PP..T..G --------
        .XBO.C..C..Q.O.S....QCVR.T. FFFFFFFF
        RC.Y.CBT.S..RR.LB......P.D. --------
        .Q.R.RGXPVT.VGB.LY..R.CY..C --------
        ..F.D.PO.DL.GO..V.Y..TOSR.V --------
        VGF.POXVF....BVGCD...G..YBS --------
        .PG.OGFFO..RVO...X..C...YRQ --------
        LSL.C..S.OYPVCL....GG..S... --------
        F.Q..XDT.P...C.YXDRQC.D.G.. --------
        ...LX.X.GLPY.OY.YS...O.GPV. --------
        .S.B..GT...QYCD...G.DBP.DP. --------
        .YS.....T....CRS.T..QOSR... --------
        P..O..QY.X.TCQ........Y..DR --------
        ....S.L...GQ.B..YX.PV..RB.V --------
        .....Y.X.B.GTBOBCR...CB.V.C --------
        TB.RQ.XRLOGCCGP.OB.BXQT.XLL --------
        GLC..B.RY.QTCVRVG.Q.PL.P... --------
        G...DROSF....DTGYF.RSBLQXR. --------
        .SDF.S..S.V..OV.BTDQRGDYTQY --------
        P..XG.QCV....X.LO..LXROS..Q ennennen
        .DLQC...CTY..D.SGO..BV.FL.F --------
        OP.QSY.XDDT..C..BX..Q.P...S --------
        .TF....L.P.OVRYGTGPB.X.P... --------
        RD.R..D...RRVVY.....RP..VFF --------
        YVXG.T.O..QD.L....VT.VO..C. --------
        P..GQB.P.FX..CY.VD..VT...QL --------
        T....Q.DDO...VSFDXDP.Q..RD. FFFFFFFF
        Y.D..VG.BL..QL.S.G..G..Q.B. --------
        ......V....C.SQO.VY.FFOL.YT --------
        P.XD.SVBS.DX.X..F...OBL.CC. FFFFFFFF
        .P.TDTVT.P.DVBTY..RL.GYQ..P --------
        GL.YSLFYO.G.QVGY.X..V.L.R.X --------
        .T.B..L.....PTR.R.F...L.T.X FFFFFFFF
        DBOS.PLDP..RDS..B.RBOYD..RB --------
        .G.PLQV.Q....SQ..FV.V...OCL FFFFFFFF
        R...D.VOL.ORCDTY..Y.CX..YVP --------
        YPSX.......TDG.ORQO.B.O.RD. --------
        PL.V..Y.CQ...X..Q..TC.Q.... --------
        Q.S...R..L...O...CF....VQGQ FFFFFFFF
        T.CL.Q.....YXR.P..QS.GG..B. --------
        ...SO..PL.SF.BY..VOG....C.F --------
        XRV.YY.....VDL...CB.S..Q.R. --------
        LBXO....SDD..C.G....ODLVY.. --------
        ..XGFPPSPP...S..FBL..Y..... --------
        .YQP.C.RX..RCV..VP..L.S.F.C --------
        ..V..P...GFYOV.V.C..S.VLT.D --------
        Y.CB...G.YY..GO..QLL..T..B. --------
        QRGCQ...QDS.DGPS....D.V.P.Y --------
        ....BTG.SP.VCL.BB..YBXVF... --------
        VY.OFS..BB.G.GQTT.....G.S.Q --------
        D.TOV..VBS.OQXOPF.B......GX --------
        Q.TCC..QVG.GBT.BBQ..P..CB.. --------
        .RGY.V.O.RVOCSVSL.X.....P.V --------
        .VB...V.X.R..OS........R.D. FFFFFFFF
        OS.G.Q.G.X.RGG.Q.V.....QF.T FFFFFFFF
        XVYT..YD..TL.XF.XG...SR..CS --------
        .GCD...LD...GDP...GT..TF..C FFFFFFFF
        .RLB..F..OQ.VOTY.G.Q.XS...Y --------
        .V..S.V..L.RPV.....C..YFVP. --------
        TQQS.YSFRQ..SLR.DLO..GXXY.X --------
        GX..TPPCOC...TXS..SOS..Y..R --------
        ........PRY.LCFS...RL.B..LS --------
        ...FROBGST...TO.LXX..Q..... wwewweww
        .Q..B...D..D.Q.YPT..DD.CVVG --------
        R.PP.PO..F..YBP.VQ..G.PB... --------
        .FB.....XFLL.LXLSVYL..QS.F. --------
        T.....B.Y.F.TBRLDPSVF.FT... --------
        QBFGB.XXQGDDRLS.C.Y..X..QQL --------
        ..SC..S.SC.BXSLFQTL.BOL...X FFFFFFFF
        F..YQF.G...B.G.VF..GR.SRP.B --------
        .DFPSVXORRY.PX..XGOT..RC.T. --------
        ...TDS.PLCF..LG.V...PL.DGQY --------
        V..TBDS...F..RCPXB......FCX --------
        ..QF.TVO.O.DVBRGBTOVFX..BL. FFFFFFFF
        GDP.O..C.XTG.Q.P.P.B....RDY --------
        ...X....D.VY.Q.BDBO.T.O.TCV --------
        OS..TBD..G.XVL.X.DXS.B....Y --------
        D.FRQL......FO..R....BV..LP --------
        .VVX..VGS....VPQCFFDQQ..... FFFFFFFF
        .FDXRRS.F..C.T..DFD.B.C..D. --------
        C.T..LOFT...BB....G.L.S.T.. FFFFFFFF
        Q..C.V...Y..QOYGR.SFDL..L.Y --------
        O.PQSD.P..G.BGL..LD.CB.B... --------
        V....RC..FSB.T.SP..SD..O... FFFFFFFF
        OPTQBY.DQOVPLVP.R.L..V.S..S --------
        .CO......PY..G..Q..SL.....T --------
        .C.GBDS..FY.CL.VL.B.TBS..PP --------
        ...PV.....T..R...VLGSFPQ..X --------
        L..SQSV.LX...D.G..C.L.RR.QG --------
        S......D..Q.TVPB...OR...G.D FFFFFFFF
        P.....RQ....OG.T...PG.SOG.P FFFFFFFF
        ....YT..Y.PXOL.P...C..GD... --------
        ...X......C..T.Q..S....Y..C --------
        .RXL.TSPGP..LV.CQY.OR...SQ. --------
        BG.Q.V.BV....RD..TVDG...TXS FFFFFFFF
        ..PLPLPXS...YGY.O.GR.F.B..T --------
        .DL..RQPB...OO....R..O.D..S FFFFFFFF
        F.RQR.....T..B.PY...BOF..B. --------
        .RPTTR.P.V..GO.GCPPF.VQ.... --------
        S..DX.S.XFVT.OL.G.....VV... --------
        """;

    /** The palette codes of the box. */
    static final Map<Character, LandslideCell> PALETTE = Map.ofEntries(
        Map.entry('.', LandslideCell.of("minecraft:air")),
        Map.entry('D', LandslideCell.of("minecraft:dirt", FULL)),
        Map.entry('G', LandslideCell.of("tfc:grass/fluvisol", FULL)),
        Map.entry('Q', LandslideCell.of("tfc:duff/fluvisol", FULL)),
        Map.entry('V', LandslideCell.of("minecraft:gravel", FULL)),
        Map.entry('S', LandslideCell.of("minecraft:sand", FULL)),
        Map.entry('C', LandslideCell.of("tfc:rock/cobble/granite", FULL)),
        Map.entry('R', LandslideCell.of("tfc:rock/raw/granite", FULL)),
        Map.entry('L', LandslideCell.of("minecraft:stone_slab", Direction.DOWN)),
        Map.entry('T', LandslideCell.of("minecraft:stone_slab", Direction.UP)),
        Map.entry('F', LandslideCell.of("minecraft:farmland")),
        Map.entry('P', LandslideCell.of("minecraft:dirt_path")),
        Map.entry('B', LandslideCell.of("minecraft:bedrock", FULL)),
        Map.entry('O', LandslideCell.of("minecraft:short_grass")),
        Map.entry('X', LandslideCell.of("minecraft:structure_void")),
        Map.entry('Y', LandslideCell.of("test:synthetic_support", FULL))
    );

    /** The block-kind data: TFC toughness of the palette kinds, and the unbreakable structure void. Bedrock is 4 by default. */
    static CollapseKinds blocks() {
        return CollapseKinds.builder()
            .toughness("tfc:rock/cobble/granite", 2)
            .toughness("tfc:rock/raw/granite", 2)
            .unbreakable("minecraft:structure_void")
            .build();
    }

    /** The landslide rules over the TFC table, with the synthetic support of reach 1 / 1 / 1. */
    static LandslideRules rules() {
        SupportTable supports = new SupportTable(List.of(new SupportTable.Support(Set.of("test:synthetic_support"), 1, 1, 1)));
        return new LandslideRules(LandslideSoils.tfc(), supports, blocks());
    }

    /** The view of one box row. Reading outside the box is a bug in the rules and fails the test. */
    static LandslideView view(String box) {
        if (box.length() != 27) {
            throw new IllegalArgumentException("A box has 27 codes: " + box);
        }
        return pos -> {
            if (Math.abs(pos.x()) > 1 || Math.abs(pos.y()) > 1 || Math.abs(pos.z()) > 1) {
                throw new IllegalStateException("Read outside the box at " + pos);
            }
            return PALETTE.get(box.charAt((pos.y() + 1) * 9 + (pos.z() + 1) * 3 + (pos.x() + 1)));
        };
    }

    /** The LCG shared with the oracle. */
    static final class Lcg implements LandslideRandom {
        private long state;

        Lcg(long seed) {
            this.state = seed;
        }

        @Override
        public int nextInt(int bound) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return (int) ((state >>> 33) % bound);
        }
    }

    static char code(LandslideDecision decision) {
        if (decision instanceof LandslideDecision.None) {
            return '-';
        }
        if (decision instanceof LandslideDecision.Fall) {
            return 'F';
        }
        return switch (((LandslideDecision.Slide) decision).side()) {
            case NORTH -> 'n';
            case EAST -> 'e';
            case SOUTH -> 's';
            case WEST -> 'w';
            default -> throw new IllegalStateException("Not a side: " + decision);
        };
    }

    @Test
    void rowsMatchTfcDecisionsForEverySeed() {
        LandslideRules rules = rules();
        List<String> rows = ROWS.lines().toList();
        assertThat(rows).hasSize(ROW_COUNT);
        List<String> failures = new ArrayList<>();
        int checks = 0;
        for (String row : rows) {
            String box = row.substring(0, 27);
            String expected = row.substring(28);
            assertThat(expected).hasSize(8);
            for (int seed = 0; seed < 8; seed++) {
                char got = code(rules.decide(view(box), ORIGIN, new Lcg(seed)));
                checks++;
                if (got != expected.charAt(seed)) {
                    failures.add(box + " seed " + seed + ": expected " + expected.charAt(seed) + ", got " + got);
                }
            }
        }
        assertThat(failures).isEmpty();
        assertThat(checks).isEqualTo(ROW_COUNT * 8);
    }

    @Test
    void rowsCoverEveryDecision() {
        StringBuilder all = new StringBuilder();
        ROWS.lines().forEach(row -> all.append(row.substring(28)));
        for (char c : "-Fnesw".toCharArray()) {
            assertThat(all).contains(String.valueOf(c));
        }
    }

    @Test
    void tfcSoilTableMatchesTheData() {
        LandslideKinds kinds = LandslideSoils.tfc();
        assertThat(LandslideSoils.CAN_LANDSLIDE).hasSize(189);
        assertThat(LandslideSoils.SUPPORTS_LANDSLIDE).hasSize(18);
        assertThat(LandslideSoils.RECIPES).hasSize(21);
        // a recipe kind slides into its result; a tag-only kind slides into itself; raw stone does not slide
        assertThat(kinds.result("tfc:grass/fluvisol")).isEqualTo("tfc:dirt/fluvisol");
        assertThat(kinds.result("tfc:kaolin_clay_grass")).isEqualTo("tfc:red_kaolin_clay");
        assertThat(kinds.result("minecraft:gravel")).isEqualTo("minecraft:gravel");
        assertThat(kinds.canLandslide("tfc:duff/fluvisol")).isTrue();
        assertThat(kinds.canLandslide("tfc:rock/raw/granite")).isFalse();
        assertThat(kinds.supportsLandslide("minecraft:farmland")).isTrue();
        assertThat(kinds.supportsLandslide("tfc:rock/raw/granite")).isFalse();
    }
}
