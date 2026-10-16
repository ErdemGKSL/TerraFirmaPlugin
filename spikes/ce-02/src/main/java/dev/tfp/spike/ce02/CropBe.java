package dev.tfp.spike.ce02;

import java.util.concurrent.atomic.AtomicLong;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/** Variant (a): a CE block-entity controller holding the planted tick. No ticker. */
final class CropBe extends BukkitBlockBehavior implements EntityBlock {
    static final AtomicLong SAVE_NANOS = new AtomicLong(), SAVES = new AtomicLong();
    static final AtomicLong LOAD_NANOS = new AtomicLong(), LOADS = new AtomicLong();
    static final AtomicLong CONSTRUCTED = new AtomicLong(), REMOVED = new AtomicLong(), ONLOAD = new AtomicLong();

    private CropBe(net.momirealms.craftengine.core.block.BlockDefinition def) {
        super(def);
    }

    static void register() {
        BlockBehaviors.register(Key.of("tfpspike2", "crop_be"), (block, section) -> new CropBe(block));
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity be) {
        return new Controller(be);
    }

    @Override
    public void initControllerId(int id) {
    }

    static final class Controller extends BlockEntityController {
        static final long UNSET = Long.MIN_VALUE;
        volatile long planted = UNSET;

        Controller(BlockEntity be) {
            super(be);
            CONSTRUCTED.incrementAndGet();
        }

        /** Region thread. */
        void plant(long tick) {
            planted = tick;
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }

        @Override
        public void saveCustomData(CompoundTag tag) {
            long s = System.nanoTime();
            CompoundTag d = new CompoundTag();
            d.putLong("planted", planted);
            tag.put("tfpspike2:crop", d);
            SAVE_NANOS.addAndGet(System.nanoTime() - s);
            SAVES.incrementAndGet();
        }

        @Override
        public void loadCustomData(CompoundTag tag) {
            long s = System.nanoTime();
            CompoundTag d = tag.getCompound("tfpspike2:crop");
            if (d != null) {
                planted = d.getLong("planted", UNSET);
            }
            LOAD_NANOS.addAndGet(System.nanoTime() - s);
            LOADS.incrementAndGet();
        }

        @Override
        public void onLoad() {
            ONLOAD.incrementAndGet();
        }

        @Override
        public void onRemove() {
            REMOVED.incrementAndGet();
        }
    }
}
