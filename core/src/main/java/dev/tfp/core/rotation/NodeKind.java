/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/Node.java @ c3b4d9536 (the node kinds, as subclassed by the block entities
 *       in common/blockentities/rotation/)
 */
package dev.tfp.core.rotation;

/**
 * The four kinds of rotation node, one per TFC node class that the solver distinguishes.
 *
 * <ul>
 *     <li>{@link #SOURCE}: a {@code SourceNode} (water wheel, windmill, creative motor). It emits its own rotation on every face.</li>
 *     <li>{@link #AXLE}: an {@code AxleNode} (axle). It passes the rotation on with one more axle of length, up to five.</li>
 *     <li>{@link #GEAR_BOX}: the anonymous {@code Node} of {@code GearBoxBlockEntity}. It passes speed on and changes handedness.</li>
 *     <li>{@link #SINK}: a {@code SinkNode} (crankshaft, power loom). It has one face and only reads the rotation.</li>
 * </ul>
 *
 * <p>A clutch is an axle whose connections the caller empties while it is powered (TFC {@code ClutchBlockEntity.updateConnections}).
 */
public enum NodeKind {
    SOURCE,
    AXLE,
    GEAR_BOX,
    SINK
}
