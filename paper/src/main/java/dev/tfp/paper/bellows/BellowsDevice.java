/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BellowsBlock.java @ c3b4d9536 (useItemOn),
 *       src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (onRightClick, doPush,
 *       afterPush),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/IBellowsConsumer.java @ c3b4d9536 (offsets)
 */
package dev.tfp.paper.bellows;

import dev.tfp.core.bellows.BellowsData;
import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.core.charcoalforge.BellowsRules;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.block.Block;

/**
 * PAR-METAL-11: the bellows as a device kind ({@code tfp:bellows}). A right click presses it (TFC {@code onRightClick}):
 * {@link BellowsRules#press} decides from the 20 tick cadence, the crankshaft behind it and the consumers in reach; a push
 * gives {@link BellowsRules#BELLOWS_AIR} to every consumer that accepts air, stores the push tick and plays the blow effects.
 * Consumers are looked up at the {@link BellowsMotion#DEFAULT_OFFSETS} in front of the bellows.
 *
 * <p>The crankshaft behind the bellows (PAR-MECH-05) is read through {@link #crank}; until it is installed there is none and
 * only hand pushes happen. The pressed look (TFC's block entity animation) is not drawn: the block stays still (ledger).
 */
public final class BellowsDevice {
    public static final String ID = "tfp:bellows";
    public static final String FACING = "facing";

    private final BellowsConsumers consumers;
    private final Function<Block, Optional<String>> blockId;
    private final BellowsEffects effects;
    private volatile Function<Block, BellowsRules.Crank> crank = b -> BellowsRules.Crank.NONE;
    private final DeviceKind<BellowsData> kind;

    public BellowsDevice(BellowsConsumers consumers, Function<Block, Optional<String>> blockId, BellowsEffects effects) {
        this.consumers = Objects.requireNonNull(consumers, "consumers");
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.kind = DeviceKind.builder(ID, BellowsData.CODEC, BellowsData.FRESH)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<BellowsData> kind() {
        return kind;
    }

    /** Installs the crankshaft lookup of the block behind a bellows (PAR-MECH-05). Region thread of the bellows. */
    public void crank(Function<Block, BellowsRules.Crank> lookup) {
        this.crank = Objects.requireNonNull(lookup, "lookup");
    }

    /** A consumer found in front of the bellows. */
    private record Found(Block block, BellowsConsumers.Consumer consumer, boolean accepts) {
    }

    private List<Found> consumersFor(Block bellows, String facing) {
        List<Found> out = new ArrayList<>(BellowsMotion.DEFAULT_OFFSETS.size());
        for (BellowsMotion.Offset offset : BellowsMotion.DEFAULT_OFFSETS) {
            int[] d = offset.delta(facing);
            Block at = bellows.getRelative(d[0], d[1], d[2]);
            if (!effects.owns(at)) {
                continue; // another region's block: not read from here
            }
            blockId.apply(at).flatMap(consumers::find)
                .ifPresent(c -> out.add(new Found(at, c, c.canAcceptAir(at))));
        }
        return out;
    }

    private final class Handler implements DeviceHandler<BellowsData> {
        @Override
        public DeviceResult<BellowsData> useWithItem(DeviceContext<BellowsData> ctx) {
            if (ctx.player() == null) {
                return DeviceResult.pass();
            }
            Block bellows = ctx.block();
            String facing = ctx.state().properties().getOrDefault(FACING, "north");
            List<Found> found = consumersFor(bellows, facing);
            BellowsRules.Press press = BellowsRules.press(ctx.now(), ctx.deviceState().lastPushed(),
                crank.apply(bellows), found.stream().map(Found::accepts).toList());
            switch (press.outcome()) {
                case PASS:
                    return DeviceResult.pass();
                case FAIL, REFUSED:
                    return DeviceResult.consume(); // the arm swings, nothing is pushed
                default:
                    for (Found f : found) {
                        if (f.accepts()) {
                            effects.onRegion(f.block(), () -> f.consumer().intakeAir(f.block(), BellowsRules.BELLOWS_AIR));
                        }
                    }
                    effects.blow(bellows, facing);
                    return DeviceResult.consume(ctx.record().withState(ctx.deviceState().withPush(press.lastPushed())));
            }
        }
    }
}
