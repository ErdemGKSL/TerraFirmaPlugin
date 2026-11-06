/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet.display;

import com.github.retrooper.packetevents.protocol.color.Color;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemCustomModelData;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.tfp.core.visuals.OverlayFace;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The packets of one ore face overlay (PAR-INFRA-16, design §3.1): {@code ADD_ENTITY} of an {@code item_display} at the
 * centre of the open neighbour cell (it takes that cell's light) and {@code SET_ENTITY_DATA} with the translation
 * {@code -normal * 0.49}, the face's fixed left rotation ({@link OverlayFace#rotation()}), the {@code view_range} of the
 * cutoff and the overlay item: {@code paper} with {@code item_model} = the CraftEngine overlay item id and
 * {@code custom_model_data} colour 0 = the vanilla face shade (the quad has {@code shade: false}). Pure builders; the
 * caller sends them.
 *
 * <p>Display metadata indices of 1.21.4 to 1.21.11 (the S1 spike's): 11 translation, 13 left rotation, 17 view range, 23
 * item, 24 item display context (0 = none). 26.x is assumed to keep them; the in-game steps on 26.3 check it.
 */
public final class OverlayDisplayPackets {
    public static final int TRANSLATION = 11;
    public static final int LEFT_ROTATION = 13;
    public static final int VIEW_RANGE = 17;
    public static final int ITEM = 23;
    public static final int DISPLAY_CONTEXT = 24;
    /** Passengers of a falling block ride at its height (0.98, {@code EntityType.FALLING_BLOCK} dimensions). */
    public static final float FALLING_RIDE_HEIGHT = 0.98f;

    private OverlayDisplayPackets() {
    }

    /** What the overlay item carries: the item model and the face shade (colour 0 of {@code custom_model_data}). */
    public record ItemSpec(ResourceLocation model, int shade) {
    }

    /** The item spec of an overlay item id ({@code ns:path}) on a face of {@code shade}. */
    public static ItemSpec spec(String itemModel, int shade) {
        int colon = itemModel.indexOf(':');
        ResourceLocation model = colon < 0 ? new ResourceLocation("minecraft", itemModel)
                : new ResourceLocation(itemModel.substring(0, colon), itemModel.substring(colon + 1));
        return new ItemSpec(model, shade);
    }

    /** The overlay item stack: {@code paper} with the item model and the face shade as colour 0. */
    public static ItemStack item(ItemSpec spec) {
        return ItemStack.builder().type(ItemTypes.PAPER)
                .component(ComponentTypes.ITEM_MODEL, new ItemModel(spec.model()))
                .component(ComponentTypes.CUSTOM_MODEL_DATA_LISTS,
                        new ItemCustomModelData(List.of(), List.of(), List.of(), List.of(new Color(spec.shade()))))
                .build();
    }

    /** The entity position of a face overlay of the block at {@code (x, y, z)}: the open neighbour cell's centre. */
    public static Vector3d position(int x, int y, int z, OverlayFace face) {
        return new Vector3d(x + 0.5 + face.dx(), y + 0.5 + face.dy(), z + 0.5 + face.dz());
    }

    private static UUID uuid() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new UUID((r.nextLong() & ~0xF000L) | 0x4000L, (r.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L);
    }

    public static WrapperPlayServerSpawnEntity spawn(int entityId, Vector3d position) {
        return new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid()), EntityTypes.ITEM_DISPLAY, position, 0f, 0f,
                0f, 0, Optional.empty());
    }

    /** The full metadata of a face overlay placed in the neighbour cell. */
    public static WrapperPlayServerEntityMetadata data(int entityId, OverlayFace face, String itemModel, float viewRange) {
        return data(entityId, face, cellTranslation(face), itemModel, viewRange);
    }

    /** The full metadata of a face overlay riding a falling block. */
    public static WrapperPlayServerEntityMetadata riderData(int entityId, OverlayFace face, String itemModel,
            float viewRange) {
        return data(entityId, face, riderTranslation(face), itemModel, viewRange);
    }

    private static WrapperPlayServerEntityMetadata data(int entityId, OverlayFace face, Vector3f translation,
            String itemModel, float viewRange) {
        List<EntityData<?>> data = new ArrayList<>(transform(face, translation, viewRange));
        data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, item(spec(itemModel, face.shade()))));
        return new WrapperPlayServerEntityMetadata(entityId, data);
    }

    /** The metadata entries other than the item: translation, left rotation, view range, display context. */
    public static List<EntityData<?>> transform(OverlayFace face, Vector3f translation, float viewRange) {
        float[] q = face.rotation();
        return List.of(
                new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, translation),
                new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, new Quaternion4f(q[0], q[1], q[2], q[3])),
                new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, viewRange),
                new EntityData<>(DISPLAY_CONTEXT, EntityDataTypes.BYTE, (byte) 0));
    }

    /** The translation of a face overlay placed in the neighbour cell ({@code -normal * 0.49}). */
    public static Vector3f cellTranslation(OverlayFace face) {
        float[] t = face.translation();
        return new Vector3f(t[0], t[1], t[2]);
    }

    /** The translation of a face overlay riding a falling block (seat 0.98 above its base). */
    public static Vector3f riderTranslation(OverlayFace face) {
        float r = 0.5f + OverlayFace.OFFSET;
        return new Vector3f(face.dx() * r, 0.5f - FALLING_RIDE_HEIGHT + face.dy() * r, face.dz() * r);
    }

    /** Only the item, for a crack-stage swap. */
    public static WrapperPlayServerEntityMetadata morph(int entityId, OverlayFace face, String itemModel) {
        return new WrapperPlayServerEntityMetadata(entityId,
                List.of(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, item(spec(itemModel, face.shade())))));
    }

    public static WrapperPlayServerDestroyEntities remove(int[] ids) {
        return new WrapperPlayServerDestroyEntities(ids);
    }

    public static WrapperPlayServerSetPassengers passengers(int vehicleId, int[] ids) {
        return new WrapperPlayServerSetPassengers(vehicleId, ids);
    }
}
