/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.CatchUp;
import dev.tfp.core.persist.VersionedCodec;
import org.bukkit.inventory.ItemStack;

/**
 * The framework's own test device (PAR-INFRA-02), used by the tests and the self-test {@code device.roundtrip}
 * (PAR-INFRA-18). Not a TFC block: it has no content of its own and is inert unless a CraftEngine block
 * {@code tfp:device/probe} with behaviour {@code tfp:device} exists.
 *
 * <ul>
 *   <li>State: player ticks simulated so far and the number of right clicks.</li>
 *   <li>Catch-up: adds the elapsed ticks.</li>
 *   <li>Right click with an item: one of it goes into the first slot that takes it (consumed).</li>
 *   <li>Right click otherwise: counts the click (consumed).</li>
 *   <li>Four slots, any item, dropped on break.</li>
 * </ul>
 */
public final class ProbeDevice {
    public static final String ID = "tfp:device/probe";

    /** @param simulatedTicks ticks handed to catch-up in total; @param uses right clicks without an item */
    public record State(long simulatedTicks, int uses) {
    }

    public static final VersionedCodec<State> CODEC = VersionedCodec.<State>builder("device_probe", 1)
            .build((s, out) -> out.putLong("ticks", s.simulatedTicks()).putInt("uses", s.uses()),
                    in -> new State(in.requireLong("ticks"), in.requireInt("uses")));

    public static final CatchUp.Advance<State> ADVANCE = (rec, from, to) ->
            rec.withState(new State(rec.state().simulatedTicks() + (to - from), rec.state().uses()));

    private ProbeDevice() {
    }

    public static DeviceKind<State> kind() {
        return DeviceKind.builder(ID, CODEC, new State(0, 0))
                .slots(4)
                .advance(ADVANCE)
                .removal(DeviceKind.RemoveBehaviour.DROP)
                .handler(new DeviceHandler<>() {
                    @Override
                    public DeviceResult<State> useWithItem(DeviceContext<State> ctx) {
                        ItemStack one = ctx.item().clone();
                        one.setAmount(1);
                        DeviceInventory.Insert<State> r = ctx.inventory().insert(one);
                        if (!StackCodec.isEmpty(r.remainder())) {
                            return DeviceResult.pass();
                        }
                        if (ctx.player() != null && ctx.hand() != null) {
                            ItemStack held = ctx.item().clone();
                            held.setAmount(held.getAmount() - 1);
                            ctx.player().getInventory().setItem(ctx.hand(), held.getAmount() <= 0 ? null : held);
                        }
                        return DeviceResult.consume(r.inventory().record());
                    }

                    @Override
                    public DeviceResult<State> use(DeviceContext<State> ctx) {
                        State s = ctx.deviceState();
                        return DeviceResult.consume(ctx.record().withState(new State(s.simulatedTicks(), s.uses() + 1)));
                    }
                })
                .build();
    }
}
