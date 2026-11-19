/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.perf;

import dev.tfp.core.rotation.NodeKind;
import dev.tfp.core.rotation.RotationNetworks;
import dev.tfp.core.rotation.RotationNode;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** PERF-ROT-01: loading and solving one rotation network, a source plus a 64-axle line. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RotationBenchmark {
    private List<RotationNode> line;

    @Setup
    public void setup() {
        line = new ArrayList<>();
        line.add(RotationNode.source(new BlockPos(0, 0, 0), Set.of(Direction.EAST), Direction.UP, 0.2f));
        for (int x = 1; x <= 64; x++) {
            line.add(RotationNode.of(new BlockPos(x, 0, 0), NodeKind.AXLE, Set.of(Direction.EAST, Direction.WEST)));
        }
    }

    @Benchmark
    public int loadLine() {
        RotationNetworks networks = new RotationNetworks();
        networks.load(line);
        return networks.networkCount();
    }
}
