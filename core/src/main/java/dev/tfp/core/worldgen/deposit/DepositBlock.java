/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.deposit;

/**
 * One block a soil disc replaces. Immutable.
 *
 * @param x        the block x
 * @param y        the block y
 * @param z        the block z
 * @param kind     what replaces the block
 * @param replaced what the block was
 * @param mineral  the deposit's mineral ({@code native_copper}, {@code cassiterite}, ...) for {@link DepositKind#ORE_DEPOSIT},
 *                 else null; the rock of the deposit block is the rock at the position
 * @param feature  the placed feature that wrote it
 */
public record DepositBlock(int x, int y, int z, DepositKind kind, DepositMaterial replaced, String mineral, String feature) {}
