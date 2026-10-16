/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

/**
 * Base of every TFC block that keeps per-block state (devices, player-planted crops): the per-block data
 * store decided by M0-CE-02 ({@code docs/spikes/M0-CE-02.md}). CraftEngine persists the data in its chunk
 * records and drops it with the block ({@link #onRemove}), so no orphan data survives explosions, vanilla
 * {@code setType} or pistons.
 *
 * <p>State is one immutable value {@code T} encoded by a {@link VersionedCodec} under our namespaced key
 * ({@code tfp:<name>}: composite controllers share one tag). Old versions are migrated on load; the next
 * save writes the current layout.
 *
 * <p><b>Threads</b> ({@code craftengine-java-api} skill §4): the constructor, {@link #loadCustomData} and
 * {@link #saveCustomData} run on chunk-IO, worldgen, world-save or region threads; {@link #onLoad},
 * {@link #onRemove} and {@link #setData} run on the owning region thread. The value sits in a volatile
 * field and is immutable, so a save on another thread always encodes a whole value. Never call Bukkit
 * world APIs from the load/save hooks.
 *
 * <p><b>Unreadable data</b> (written by a newer plugin version, or corrupt): the raw tag is kept and
 * written back unchanged on save, so a downgrade or a bug never destroys a player's device. The block
 * behaves as if it had the initial value until it is changed in game.
 *
 * <p>No ticker: TFC devices and crops catch up lazily from timestamps in {@code T} (ARCHITECTURE.md §4.1).
 *
 * @param <T> the persisted state, immutable
 */
public abstract class TfpBlockEntityController<T> extends BlockEntityController {
    private static final Logger LOG = Logger.getLogger("TerraFirmaPlugin");

    private final String key;
    private final VersionedCodec<T> codec;
    private volatile T data;
    /** Our tag as found on load when it could not be decoded; written back verbatim until replaced. */
    private volatile Tag unreadable;

    /**
     * @param blockEntity CraftEngine's block entity (null only in unit tests)
     * @param name        our key's path; stored as {@code tfp:<name>}
     * @param initial     the value of a freshly placed block, and of a block without saved data (generated)
     */
    protected TfpBlockEntityController(BlockEntity blockEntity, String name, VersionedCodec<T> codec, T initial) {
        super(blockEntity);
        this.key = CraftEngineIntegration.NAMESPACE + ":" + Objects.requireNonNull(name, "name");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.data = Objects.requireNonNull(initial, "initial");
    }

    /** The current value. Any thread. */
    public final T data() {
        return data;
    }

    /** The NBT key we store under ({@code tfp:<name>}). */
    public final String key() {
        return key;
    }

    /** True if the saved data could not be read and is being preserved as-is. */
    public final boolean hasUnreadableData() {
        return unreadable != null;
    }

    /**
     * Replaces the value and tells CraftEngine the chunk needs saving. Region thread (the block's owner).
     * Replacing the value also drops preserved unreadable data: the player changed the block in game.
     */
    protected final void setData(T value) {
        this.data = Objects.requireNonNull(value, "value");
        this.unreadable = null;
        BlockEntity be = blockEntity;
        if (be != null && be.world != null) {
            be.world.blockEntityChanged(be.pos);
        }
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        Tag keep = unreadable;
        if (keep != null) {
            tag.put(key, keep);
            return;
        }
        tag.put(key, NbtDataMaps.toNbt(codec.encode(data)));
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        Tag raw = tag.get(key);
        if (raw == null) {
            return; // never saved (fresh or generated block): keep the initial value
        }
        if (!(raw instanceof CompoundTag compound)) {
            preserve(raw, new PersistException(key + ": expected a compound tag, found " + raw.getClass().getSimpleName()));
            return;
        }
        try {
            data = codec.decode(NbtDataMaps.fromNbt(compound));
            unreadable = null;
        } catch (PersistException e) {
            preserve(compound.deepClone(), e);
        }
    }

    private void preserve(Tag raw, PersistException e) {
        unreadable = raw;
        LOG.log(e instanceof PersistException.NewerVersion ? Level.WARNING : Level.SEVERE,
                "Keeping unreadable block data " + key + (blockEntity != null ? " at " + blockEntity.pos : "")
                        + " unchanged: " + e.getMessage());
    }
}
