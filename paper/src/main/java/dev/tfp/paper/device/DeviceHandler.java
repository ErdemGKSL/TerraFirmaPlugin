/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

/**
 * A device kind's reactions (PAR-INFRA-02). Every callback runs on the region thread that owns the block, after the
 * record was caught up to {@link DeviceContext#now()}; keep them short. Work that scans neighbours or cascades is
 * snapshotted and handed to an {@code AsyncDecisionPipeline} ("decide async, apply sync"); the callback only answers
 * from the record. Defaults do nothing and pass.
 *
 * <p><b>Never change other blocks from a handler</b> (no {@code setType}, no CraftEngine placement, no neighbour
 * writes): a handler only returns the device's new record. Changes to the world around the device are decided async
 * and applied by the region commit queue (ARCHITECTURE.md §7.1).
 *
 * <p>Order of a right click, as vanilla's {@code ServerPlayerGameMode.useItemOn}: {@link #useWithItem} (TFC
 * {@code useItemOn}) runs for the clicking hand, also when that hand is empty; if it passes and the hand is the main
 * hand, {@link #use} (TFC {@code useWithoutItem}) runs. An off-hand click never reaches {@link #use}. CraftEngine skips
 * both while the player sneaks with an item in either hand, as vanilla.
 */
public interface DeviceHandler<S> {
    /**
     * TFC {@code useItemOn}: a right click with {@link DeviceContext#hand()}, holding {@link DeviceContext#item()},
     * which may be empty (as TFC; for example {@code BarrelBlock} toggles the seal on an empty hand + sneak here).
     */
    default DeviceResult<S> useWithItem(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /**
     * TFC {@code useWithoutItem}: a main-hand right click that {@link #useWithItem} passed (the hand may hold an item).
     */
    default DeviceResult<S> use(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /**
     * TFC {@code attack}: a left click on the device (it is not broken yet). Not called in creative mode or when the
     * click was denied (protection plugins), as vanilla.
     */
    default DeviceResult<S> attack(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /**
     * TFC {@code setPlacedBy}: a player placed the device; runs after the placement event, on the block's region.
     * {@link DeviceContext#item()} is a copy of the placed stack (before it was used up) and {@link DeviceContext#hand()}
     * the hand it came from.
     */
    default DeviceResult<S> placed(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /** TFC {@code neighborChanged}: a neighbour block changed. */
    default DeviceResult<S> neighbourChanged(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /**
     * TFC {@code DeviceBlock.onRemove} before {@code beforeRemove}: the device is gone (broken, exploded, replaced). The
     * block in {@link DeviceContext#block()} already holds the new state; the record is the caught-up last one. The
     * kind's {@link DeviceKind.RemoveBehaviour} (inventory drop) runs after this.
     */
    default void removed(DeviceContext<S> ctx) {
    }
}
