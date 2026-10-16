/*
 * TerraFirmaPlugin
 * Copyright (c) 2026 TerraFirmaPlugin contributors
 * Licensed under the EUPL-1.2 (see LICENSE).
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
