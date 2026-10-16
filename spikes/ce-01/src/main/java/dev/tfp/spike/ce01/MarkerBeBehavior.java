package dev.tfp.spike.ce01;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;

/** A block-entity marker. The controller remembers the thread that first constructed it. */
public final class MarkerBeBehavior extends BukkitBlockBehavior implements EntityBlock {
    static final Key TYPE = Key.of("tfpspike", "marker_be");

    /** thread-name prefix -> count, per callback. Filled from any thread. */
    static final Map<String, AtomicInteger> CONSTRUCTED_ON = new ConcurrentHashMap<>();
    static final Map<String, AtomicInteger> SAVED_ON = new ConcurrentHashMap<>();
    static final Map<String, AtomicInteger> ONLOAD_ON = new ConcurrentHashMap<>();

    private MarkerBeBehavior(BlockDefinition block) {
        super(block);
    }

    static void register() {
        BlockBehaviors.register(TYPE, (block, section) -> new MarkerBeBehavior(block));
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new MarkerController(blockEntity);
    }

    @Override
    public void initControllerId(int id) {
        // single behaviour: always 0
    }

    static void count(Map<String, AtomicInteger> map) {
        map.computeIfAbsent(threadKind(Thread.currentThread().getName()), k -> new AtomicInteger()).incrementAndGet();
    }

    /** Strips trailing numbers so "Worldgen Worker #3" and "#4" share a bucket. */
    static String threadKind(String name) {
        return name.replaceAll("[ #-]*\\d+$", "").replaceAll("\\d+", "N");
    }

    public static final class MarkerController extends BlockEntityController {
        volatile String createdOn;
        volatile int loads;

        MarkerController(BlockEntity blockEntity) {
            super(blockEntity);
            this.createdOn = Thread.currentThread().getName();
            count(CONSTRUCTED_ON);
        }

        @Override
        public void loadCustomData(CompoundTag tag) {
            CompoundTag d = tag.getCompound("tfpspike:marker");
            if (d != null) {
                createdOn = d.getString("created");
                loads = d.getInt("loads");
            }
        }

        @Override
        public void saveCustomData(CompoundTag tag) {
            count(SAVED_ON);
            CompoundTag d = new CompoundTag();
            d.putString("created", createdOn);
            d.putInt("loads", loads);
            tag.put("tfpspike:marker", d);
        }

        @Override
        public void onLoad() {
            count(ONLOAD_ON);
            loads++;
            blockEntity.world.blockEntityChanged(blockEntity.pos);
        }
    }
}
