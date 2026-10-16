/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

/** Datagen CLI entry point. Steps are added in M1-DGN-*. */
public final class Datagen {
    private Datagen() {
    }

    public static void main(String[] args) {
        System.out.println("usage: datagen <tfc-checkout> <content-dir>");
    }
}
