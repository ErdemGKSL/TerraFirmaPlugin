/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (getContainedFluid, updateContainerItem),
 * net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper (the vanilla buckets' fluid handler)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelFluidTransfer;
import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.bucket.BucketVisuals;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.fluid.FluidContainerItems;
import dev.tfp.paper.itemdata.ItemFluids;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The fluid handler of a held or slotted item, as the barrel sees it (TFC's {@code Capabilities.FluidHandler.ITEM}): TFC's
 * tank items (buckets, jugs, glass bottles; their fluid on the PDC, PAR-INFRA-04) and the vanilla bucket items, which hold
 * exactly one bucket ({@code FluidBucketWrapper}): {@code minecraft:bucket} empty, the water, lava and milk buckets and TFC's
 * per-fluid buckets {@code tfp:bucket/<fluid>}.
 *
 * <p>Threads: the stack's owner thread; the lookups are thread-safe.
 */
public final class BarrelItemTanks {
    /** The prefix of TFC's per-fluid vanilla buckets. */
    public static final String FLUID_BUCKET_PREFIX = "tfp:bucket/";
    static final String EMPTY_BUCKET = "minecraft:bucket";
    private static final Map<String, String> VANILLA_BUCKETS = Map.of(
        "minecraft:water_bucket", TfcFluids.WATER,
        "minecraft:lava_bucket", TfcFluids.LAVA,
        "minecraft:milk_bucket", TfcFluids.MILK);

    /**
     * @param itemId     the item id of a stack (CraftEngine id, else the material key)
     * @param usable     whether a tank item kind may hold a fluid (its {@code tfc:usable_in_*} tag)
     * @param create     one new stack of an item id (CraftEngine items; vanilla ids are made here)
     * @param itemExists whether an item id exists (for {@code tfp:bucket/<fluid>})
     */
    public record Services(Function<ItemStack, Optional<String>> itemId, BiPredicate<FluidContainerKind, String> usable,
            Function<String, Optional<ItemStack>> create, Predicate<String> itemExists) {
        public Services {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(usable, "usable");
            Objects.requireNonNull(create, "create");
            Objects.requireNonNull(itemExists, "itemExists");
        }
    }

    private final Services services;

    public BarrelItemTanks(Services services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** The fluid handler of one item of {@code stack}, empty when the item holds no fluid. */
    public Optional<BarrelFluidTransfer.ItemTank> tank(ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return Optional.empty();
        }
        Optional<String> id = services.itemId().apply(stack);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Optional<FluidContainerKind> kind = FluidContainerItems.kindOf(id.get());
        if (kind.isPresent()) {
            FluidContainerKind k = kind.get();
            ItemStack one = stack.clone();
            one.setAmount(1);
            return Optional.of(new BarrelFluidTransfer.ItemTank(ItemFluids.content(one), k.defaultCapacity(),
                BarrelFluidTransfer.Mode.TANK, fluid -> services.usable().test(k, fluid)));
        }
        Optional<String> bucketFluid = bucketFluid(id.get());
        if (id.get().equals(EMPTY_BUCKET) || bucketFluid.isPresent()) {
            FluidAmount content = bucketFluid.map(f -> new FluidAmount(f, BUCKET)).orElse(FluidAmount.EMPTY);
            return Optional.of(new BarrelFluidTransfer.ItemTank(content, BUCKET, BarrelFluidTransfer.Mode.BUCKET,
                fluid -> bucketItem(fluid).isPresent()));
        }
        return Optional.empty();
    }

    /** TFC {@code FluidHelpers.getContainedFluid}: the fluid one item holds, empty when none. */
    public FluidAmount content(ItemStack stack) {
        return tank(stack).map(BarrelFluidTransfer.ItemTank::content).orElse(FluidAmount.EMPTY);
    }

    /**
     * One item of {@code stack} holding {@code content} afterwards (TFC {@code IFluidHandlerItem.getContainer()}): a tank
     * item keeps its id; a vanilla bucket becomes the bucket item of its fluid or the empty bucket.
     */
    public ItemStack withContent(ItemStack stack, FluidAmount content) {
        Optional<String> id = services.itemId().apply(stack);
        if (id.isPresent() && FluidContainerItems.kindOf(id.get()).isPresent()) {
            ItemStack one = stack.clone();
            one.setAmount(1);
            ItemFluids.drain(one, Integer.MAX_VALUE);
            if (!content.isEmpty()) {
                ItemFluids.fill(one, content, content.amount(), true);
            }
            BucketVisuals.sync(one);
            return one;
        }
        if (content.isEmpty()) {
            return new ItemStack(Material.BUCKET);
        }
        return bucketItem(content.fluid()).flatMap(this::create).orElseGet(() -> new ItemStack(Material.BUCKET));
    }

    /** The bucket item that holds {@code fluid}, empty when none exists. */
    Optional<String> bucketItem(String fluid) {
        for (Map.Entry<String, String> e : VANILLA_BUCKETS.entrySet()) {
            if (e.getValue().equals(fluid)) {
                return Optional.of(e.getKey());
            }
        }
        if (fluid.startsWith("tfp:")) {
            String id = FLUID_BUCKET_PREFIX + fluid.substring("tfp:".length());
            return services.itemExists().test(id) ? Optional.of(id) : Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<String> bucketFluid(String itemId) {
        String vanilla = VANILLA_BUCKETS.get(itemId);
        if (vanilla != null) {
            return Optional.of(vanilla);
        }
        if (itemId.startsWith(FLUID_BUCKET_PREFIX)) {
            return Optional.of("tfp:" + itemId.substring(FLUID_BUCKET_PREFIX.length()));
        }
        return Optional.empty();
    }

    private Optional<ItemStack> create(String id) {
        if (id.startsWith("minecraft:")) {
            Material m = Material.matchMaterial(id);
            return m == null ? Optional.empty() : Optional.of(new ItemStack(m));
        }
        return services.create().apply(id);
    }

    /** NeoForge {@code FluidType.BUCKET_VOLUME}. */
    static final int BUCKET = 1000;
}
