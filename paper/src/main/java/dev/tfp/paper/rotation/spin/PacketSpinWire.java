/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.packet.display.OverlayDisplayPackets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SpinWire} over PacketEvents (PAR-MECH-12): {@code ADD_ENTITY} of an {@code item_display} at the block centre and
 * {@code SET_ENTITY_DATA} with the interpolation delay and duration, the left and right rotation of {@link SpinQuaternions},
 * no translation, unit scale, the axle's render item ({@code paper} with {@code item_model} = the CraftEngine render item id,
 * the same item the static element drew) in display context none, and the view range of the old element. Sent with
 * {@code sendPacketSilently}, so our own packet listeners do not see them. Users are learnt from the chunk packets
 * {@link SpinPacketHandler} sees.
 *
 * <p>Display metadata indices of 1.21.4 to 1.21.11 (as the ore overlays', S1 spike): 8 interpolation delay, 9 transformation
 * interpolation duration, 11 translation, 12 scale, 13 left rotation, 14 right rotation, 17 view range, 23 item, 24 item
 * display context. 26.x is assumed to keep them (the in-game check covers it).
 *
 * <p>PAR-MECH-26: the crankshaft's parts ({@link CrankWire}) go out the same way, with a full transformation (translation,
 * left rotation, scale, right rotation) per packet.
 */
public final class PacketSpinWire implements SpinWire, CrankWire {
    public static final int INTERPOLATION_DELAY = 8;
    public static final int INTERPOLATION_DURATION = 9;
    public static final int TRANSLATION = OverlayDisplayPackets.TRANSLATION;
    public static final int SCALE = OverlayDisplayPackets.SCALE;
    public static final int LEFT_ROTATION = OverlayDisplayPackets.LEFT_ROTATION;
    public static final int RIGHT_ROTATION = OverlayDisplayPackets.RIGHT_ROTATION;
    public static final int VIEW_RANGE = OverlayDisplayPackets.VIEW_RANGE;
    public static final int ITEM = OverlayDisplayPackets.ITEM;
    public static final int DISPLAY_CONTEXT = OverlayDisplayPackets.DISPLAY_CONTEXT;
    /** The view range of the axle's old static element ({@code RenderedTemplates.CUBE_VIEW_RANGE}). */
    public static final float VIEW_RANGE_VALUE = 4.0f;

    private final Map<UUID, User> users = new ConcurrentHashMap<>();

    /** Remembers the connection of a viewer (any thread). */
    public void user(UUID id, User user) {
        if (id != null && user != null) {
            users.put(id, user);
        }
    }

    @Override
    public void forget(UUID viewer) {
        users.remove(viewer);
    }

    @Override
    public void spawn(UUID viewer, int entityId, BlockPos pos, String item, RotationAxis axis, double halfAngle) {
        User u = users.get(viewer);
        if (u == null) {
            return;
        }
        u.sendPacketSilently(OverlayDisplayPackets.spawn(entityId, new Vector3d(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5)));
        List<EntityData<?>> data = new ArrayList<>(full(axis, halfAngle));
        data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, item(item)));
        u.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, data));
    }

    @Override
    public void turn(UUID viewer, int entityId, RotationAxis axis, double halfAngle, int duration) {
        User u = users.get(viewer);
        if (u != null) {
            u.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, segment(axis, halfAngle, duration)));
        }
    }

    @Override
    public void remove(UUID viewer, int... entityIds) {
        User u = users.get(viewer);
        if (u != null && entityIds.length > 0) {
            u.sendPacketSilently(OverlayDisplayPackets.remove(entityIds));
        }
    }

    @Override
    public void spawnPart(UUID viewer, int entityId, BlockPos pos, String item, CrankParts.Pose pose) {
        User u = users.get(viewer);
        if (u == null) {
            return;
        }
        u.sendPacketSilently(OverlayDisplayPackets.spawn(entityId, new Vector3d(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5)));
        List<EntityData<?>> data = new ArrayList<>(pose(pose, 0));
        data.add(new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, VIEW_RANGE_VALUE));
        data.add(new EntityData<>(DISPLAY_CONTEXT, EntityDataTypes.BYTE, (byte) 0));
        data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, item(item)));
        u.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, data));
    }

    @Override
    public void movePart(UUID viewer, int entityId, CrankParts.Pose pose, int duration) {
        User u = users.get(viewer);
        if (u != null) {
            u.sendPacketSilently(new WrapperPlayServerEntityMetadata(entityId, pose(pose, duration)));
        }
    }

    @Override
    public void removeParts(UUID viewer, int... entityIds) {
        remove(viewer, entityIds);
    }

    /** PAR-MECH-26: a part's transformation reached after {@code duration} ticks (interpolation delay 0). */
    public static List<EntityData<?>> pose(CrankParts.Pose pose, int duration) {
        return List.of(
            new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0),
            new EntityData<>(INTERPOLATION_DURATION, EntityDataTypes.INT, duration),
            new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, vector(pose.translation())),
            new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, vector(pose.scale())),
            new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, quaternion(pose.left())),
            new EntityData<>(RIGHT_ROTATION, EntityDataTypes.QUATERNION, quaternion(pose.right())));
    }

    private static Vector3f vector(float[] v) {
        return new Vector3f(v[0], v[1], v[2]);
    }

    /** A segment: interpolation delay 0, {@code duration} ticks, both rotations at {@code halfAngle}. */
    public static List<EntityData<?>> segment(RotationAxis axis, double halfAngle, int duration) {
        return List.of(
            new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0),
            new EntityData<>(INTERPOLATION_DURATION, EntityDataTypes.INT, duration),
            new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, quaternion(SpinQuaternions.left(axis, halfAngle))),
            new EntityData<>(RIGHT_ROTATION, EntityDataTypes.QUATERNION, quaternion(SpinQuaternions.right(axis, halfAngle))));
    }

    /** The spawn metadata other than the item: no interpolation, the transformation, view range and display context. */
    public static List<EntityData<?>> full(RotationAxis axis, double halfAngle) {
        List<EntityData<?>> data = new ArrayList<>(segment(axis, halfAngle, 0));
        data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)));
        data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(1f, 1f, 1f)));
        data.add(new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, VIEW_RANGE_VALUE));
        data.add(new EntityData<>(DISPLAY_CONTEXT, EntityDataTypes.BYTE, (byte) 0));
        return data;
    }

    /** The render item: {@code paper} with {@code item_model} = the CraftEngine item id. */
    static ItemStack item(String itemId) {
        int colon = itemId.indexOf(':');
        ResourceLocation model = colon < 0 ? new ResourceLocation("minecraft", itemId)
            : new ResourceLocation(itemId.substring(0, colon), itemId.substring(colon + 1));
        return ItemStack.builder().type(ItemTypes.PAPER).component(ComponentTypes.ITEM_MODEL, new ItemModel(model)).build();
    }

    private static Quaternion4f quaternion(float[] q) {
        return new Quaternion4f(q[0], q[1], q[2], q[3]);
    }
}
