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
 * <p>Order of a right click, as TFC's {@code Block.useItemOn} then {@code useWithoutItem}: with a non-empty hand
 * {@link #useWithItem} runs first; if it passes, {@link #use} runs.
 */
public interface DeviceHandler<S> {
    /** TFC {@code useItemOn}: a right click holding {@link DeviceContext#item()} (not empty). */
    default DeviceResult<S> useWithItem(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /** TFC {@code useWithoutItem}: a right click that no item handled (the hand may be empty or not). */
    default DeviceResult<S> use(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /** TFC {@code attack}: a left click on the device (it is not broken yet). */
    default DeviceResult<S> attack(DeviceContext<S> ctx) {
        return DeviceResult.pass();
    }

    /** TFC {@code setPlacedBy}: a player placed the device; runs on the tick after the placement. */
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
