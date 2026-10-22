/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.ItemBlob;
import org.bukkit.inventory.ItemStack;

/**
 * How a device slot's {@link ItemStack} becomes an {@link ItemBlob} and back (PAR-INFRA-02). The server uses Paper's
 * data-version-aware item serializer ({@link #PAPER}); MockBukkit tests pass their own because MockBukkit cannot write
 * every item meta.
 */
public interface StackCodec {
    StackCodec PAPER = new StackCodec() {
        @Override
        public ItemBlob encode(ItemStack stack) {
            return isEmpty(stack) ? ItemBlob.EMPTY : ItemBlob.of(stack.serializeAsBytes());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : ItemStack.deserializeBytes(blob.bytes());
        }
    };

    /** An empty stack (null, air or count 0) encodes to {@link ItemBlob#EMPTY}. */
    ItemBlob encode(ItemStack stack);

    /** {@link ItemBlob#EMPTY} decodes to {@link ItemStack#empty()}. Throws on malformed bytes. */
    ItemStack decode(ItemBlob blob);

    static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
