/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

/** A datagen failure with a message meant for the person running the tool. */
public final class DatagenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatagenException(String message) {
        super(message);
    }
}
