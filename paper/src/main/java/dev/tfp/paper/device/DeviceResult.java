/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.DeviceRecord;
import java.util.Optional;

/**
 * What a {@link DeviceHandler} callback answers: whether it consumed the interaction (the click is cancelled, no vanilla
 * item use follows; TFC {@code InteractionResult.SUCCESS}/{@code CONSUME} vs {@code PASS}) and the record to store, if
 * it changed one. The caught-up record is stored either way.
 *
 * @param consumed whether the interaction was handled
 * @param record   the new record, or {@code null} to keep the caught-up one
 */
public record DeviceResult<S>(boolean consumed, DeviceRecord<S> record) {
    public static <S> DeviceResult<S> pass() {
        return new DeviceResult<>(false, null);
    }

    public static <S> DeviceResult<S> consume() {
        return new DeviceResult<>(true, null);
    }

    public static <S> DeviceResult<S> consume(DeviceRecord<S> record) {
        return new DeviceResult<>(true, record);
    }

    /** Not consumed, but the record changed (a neighbour update, a placement stamp). */
    public static <S> DeviceResult<S> update(DeviceRecord<S> record) {
        return new DeviceResult<>(false, record);
    }

    public Optional<DeviceRecord<S>> updated() {
        return Optional.ofNullable(record);
    }
}
