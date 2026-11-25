/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.crankshaft;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.crankshaft.CrankshaftRules.Part;
import dev.tfp.core.crankshaft.CrankshaftRules.Piece;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PAR-MECH-05: the crankshaft against TFC. Oracle: TFC's {@code calculateShaftMovement}, {@code calculateRealRotationAngle},
 * {@code getExtensionLength}, {@code BellowsBlockEntity.tickBoth} and {@code Rotation.Tickable.tick} / {@code Rotation.angle}
 * copied from c3b4d9536 into a scratch harness with Minecraft 1.21.1's {@code Mth} (sine table): 6284 shaft movements hashed
 * (FNV-1a 64 of the float bits), eight verbatim, and 2000 ticks of a powered bellows per speed, hand and facing (the push count
 * and the first six push ticks).
 */
class CrankshaftGoldenTest {
    /** speed, axis step of the rotation's direction, facing (0 N, 1 E, 2 S, 3 W), n=pushes, first=ticks. */
    private static final String[] PUSHES = {
        "0.15707964 1 0 n=50 first=21,56,96,136,176,216,",
        "0.15707964 1 1 n=50 first=21,56,96,136,176,216,",
        "0.15707964 1 2 n=50 first=21,56,96,136,176,216,",
        "0.15707964 1 3 n=50 first=21,56,96,136,176,216,",
        "0.15707964 -1 0 n=50 first=21,56,96,136,176,216,",
        "0.15707964 -1 1 n=50 first=21,56,96,136,176,216,",
        "0.15707964 -1 2 n=50 first=21,56,96,136,176,216,",
        "0.15707964 -1 3 n=50 first=21,56,96,136,176,216,",
        "0.07853982 1 0 n=25 first=32,112,192,272,352,432,",
        "0.07853982 1 1 n=25 first=32,112,192,272,352,432,",
        "0.07853982 1 2 n=25 first=32,112,192,272,352,432,",
        "0.07853982 1 3 n=25 first=32,112,192,272,352,432,",
        "0.07853982 -1 0 n=25 first=32,112,192,272,352,432,",
        "0.07853982 -1 1 n=25 first=32,112,192,272,352,432,",
        "0.07853982 -1 2 n=25 first=32,112,192,272,352,432,",
        "0.07853982 -1 3 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 1 0 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 1 1 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 1 2 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 1 3 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 -1 0 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 -1 1 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 -1 2 n=25 first=32,112,192,272,352,432,",
        "-0.07853982 -1 3 n=25 first=32,112,192,272,352,432,",
        "0.015707964 1 0 n=5 first=164,564,964,1364,1764,",
        "0.015707964 1 1 n=5 first=164,564,964,1364,1764,",
        "0.015707964 1 2 n=5 first=164,564,964,1364,1764,",
        "0.015707964 1 3 n=5 first=164,564,964,1364,1764,",
        "0.015707964 -1 0 n=5 first=164,564,964,1364,1764,",
        "0.015707964 -1 1 n=5 first=164,564,964,1364,1764,",
        "0.015707964 -1 2 n=5 first=164,564,964,1364,1764,",
        "0.015707964 -1 3 n=5 first=164,564,964,1364,1764,",
        "0.5 1 0 n=79 first=30,55,80,105,130,155,",
        "0.5 1 1 n=79 first=30,55,80,105,130,155,",
        "0.5 1 2 n=79 first=30,55,80,105,130,155,",
        "0.5 1 3 n=79 first=30,55,80,105,130,155,",
        "0.5 -1 0 n=79 first=30,55,80,105,130,155,",
        "0.5 -1 1 n=79 first=30,55,80,105,130,155,",
        "0.5 -1 2 n=79 first=30,55,80,105,130,155,",
        "0.5 -1 3 n=79 first=30,55,80,105,130,155,",
    };

    private static final Direction[] FACES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @Test
    void shaftMovementMatchesTfc() {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < 6284; i++) {
            CrankshaftRules.ShaftMovement m = CrankshaftRules.shaftMovement(i * 0.001f);
            h = fnv(h, Float.floatToIntBits(m.lengthEH()));
            h = fnv(h, Float.floatToIntBits(m.raiseAngle()));
        }
        assertThat(Long.toHexString(h)).isEqualTo("8453bd611c3b808a");
        assertThat(CrankshaftRules.shaftMovement(0f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.5f, -0.0f));
        assertThat(CrankshaftRules.shaftMovement(1f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.64502275f, -0.28429827f));
        assertThat(CrankshaftRules.shaftMovement(2f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.8893219f, -0.30793965f));
        assertThat(CrankshaftRules.shaftMovement(3f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.99833035f, -0.047030408f));
        assertThat(CrankshaftRules.shaftMovement((float) Math.PI)).isEqualTo(new CrankshaftRules.ShaftMovement(1.0f, 0.0f));
        assertThat(CrankshaftRules.shaftMovement(4f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.93767494f, 0.25501162f));
        assertThat(CrankshaftRules.shaftMovement(5f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.71845305f, 0.3253597f));
        assertThat(CrankshaftRules.shaftMovement(6f)).isEqualTo(new CrankshaftRules.ShaftMovement(0.51321995f, 0.09328211f));
    }

    /** The crank timing golden: the powered bellows pushes on TFC's ticks at every speed, hand and facing. */
    @Test
    void poweredBellowsPushTicksMatchTfc() {
        for (String row : PUSHES) {
            String[] f = row.split(" ");
            float speed = Float.parseFloat(f[0]);
            Direction direction = Integer.parseInt(f[1]) > 0 ? Direction.EAST : Direction.WEST;
            Direction facing = FACES[Integer.parseInt(f[2])];
            int n = Integer.parseInt(f[3].substring(2));
            List<Long> first = new ArrayList<>();
            for (String t : f[4].substring(6).split(",")) {
                if (!t.isEmpty()) {
                    first.add(Long.parseLong(t));
                }
            }
            CrankshaftRules.Run run = CrankshaftRules.run(CrankshaftRules.Drive.FRESH, 0, 2000, direction, speed, facing);
            assertThat(run.pushes()).as(row).hasSize(n);
            assertThat(run.pushes().subList(0, first.size())).as(row).isEqualTo(first);
        }
    }

    /** Running in batches of 20 ticks gives the same pushes as one run (the paper task steps 20 ticks at a time). */
    @Test
    void batchedRunEqualsOneRun() {
        float speed = (float) (2 * Math.PI) / 80;
        CrankshaftRules.Run whole = CrankshaftRules.run(CrankshaftRules.Drive.FRESH, 0, 1000, Direction.SOUTH, speed, Direction.WEST);
        CrankshaftRules.Drive d = CrankshaftRules.Drive.FRESH;
        List<Long> pushes = new ArrayList<>();
        for (long t = 0; t < 1000; t += 20) {
            CrankshaftRules.Run r = CrankshaftRules.run(d, t, t + 20, Direction.SOUTH, speed, Direction.WEST);
            pushes.addAll(r.pushes());
            d = r.drive();
        }
        assertThat(pushes).isEqualTo(whole.pushes());
        assertThat(d).isEqualTo(whole.drive());
    }

    /** CrankshaftBlock: placement, partner, rod use, updateShape; the sink face; getCrankShaftAt. */
    @Test
    void shapeAndLookup() {
        assertThat(CrankshaftRules.placementFacing(Direction.NORTH)).isEqualTo(Direction.EAST);
        assertThat(CrankshaftRules.sinkFace(Direction.EAST)).isEqualTo(Direction.NORTH);
        BlockPos base = new BlockPos(0, 64, 0);
        Piece b = new Piece(Part.BASE, Direction.EAST);
        assertThat(CrankshaftRules.partner(base, b)).isEqualTo(new BlockPos(1, 64, 0));
        assertThat(CrankshaftRules.partner(new BlockPos(1, 64, 0), new Piece(Part.SHAFT, Direction.EAST))).isEqualTo(base);
        assertThat(CrankshaftRules.rodUse(b, true, true)).contains(new Piece(Part.SHAFT, Direction.EAST));
        assertThat(CrankshaftRules.rodUse(b, false, true)).isEmpty();
        assertThat(CrankshaftRules.rodUse(b, true, false)).isEmpty();
        assertThat(CrankshaftRules.rodUse(new Piece(Part.SHAFT, Direction.EAST), true, true)).isEmpty();
        Piece shaft = new Piece(Part.SHAFT, Direction.EAST);
        assertThat(CrankshaftRules.shaftSurvives(shaft, Optional.of(b))).isTrue();
        assertThat(CrankshaftRules.shaftSurvives(shaft, Optional.of(new Piece(Part.BASE, Direction.WEST)))).isFalse();
        assertThat(CrankshaftRules.shaftSurvives(shaft, Optional.empty())).isFalse();

        Map<BlockPos, Piece> world = new HashMap<>();
        world.put(base, b);
        world.put(new BlockPos(1, 64, 0), shaft);
        // a bellows at (2, 64, 0) facing east looks west (its facing's opposite) for the crank
        assertThat(CrankshaftRules.crankAt(new BlockPos(2, 64, 0), Direction.WEST, p -> Optional.ofNullable(world.get(p))))
            .contains(base);
        assertThat(CrankshaftRules.crankAt(new BlockPos(2, 64, 0), Direction.EAST, p -> Optional.ofNullable(world.get(p))))
            .isEmpty();
        world.remove(base);
        assertThat(CrankshaftRules.crankAt(new BlockPos(2, 64, 0), Direction.WEST, p -> Optional.ofNullable(world.get(p))))
            .isEmpty();
        // the stroke stays within [0, 2 R] and the bellows within its extension range
        for (int i = 0; i < 628; i++) {
            float e = CrankshaftRules.extension(i * 0.01f, Direction.SOUTH);
            assertThat(e).isBetween(0f, 2 * CrankshaftRules.WHEEL_RADIUS + 1e-6f);
            assertThat(CrankshaftRules.bellowsExtension(e)).isBetween(0.125f, 0.625f);
        }
    }

    private static long fnv(long h, int v) {
        for (int i = 0; i < 4; i++) {
            h ^= (v >>> (8 * i)) & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }
}
