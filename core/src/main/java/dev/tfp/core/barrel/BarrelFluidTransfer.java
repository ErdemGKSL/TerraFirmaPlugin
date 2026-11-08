/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferBetweenBlockHandlerAndItem: the
 * direction; transferBetweenItemAndOther: the optimistic attempt, then the whole item with the rest wasted),
 * net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper (a vanilla bucket fills and drains only whole
 * buckets), net.neoforged.neoforge.fluids.capability.templates.FluidTank (the barrel's and the containers' tanks)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.fluid.FluidTransferRules;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A held fluid container used on the barrel (TFC {@code FluidHelpers.transferBetweenBlockEntityAndItem}). The item gives to
 * the barrel when it holds fluid the barrel takes, otherwise it takes from the barrel. A move first tries the most the
 * receiver accepts; when the giver cannot give exactly that (a vanilla bucket only gives whole buckets), the whole content
 * goes and what does not fit is lost, as in TFC.
 */
public final class BarrelFluidTransfer {

    private BarrelFluidTransfer() {
    }

    /** How a container item holds fluid. */
    public enum Mode {
        /** A NeoForge fluid tank (TFC's buckets, jugs, glass bottles): any amount up to the capacity. */
        TANK,
        /** A vanilla bucket item ({@code FluidBucketWrapper}): empty or exactly one full bucket. */
        BUCKET
    }

    /**
     * The container item's side.
     *
     * @param content    the fluid it holds
     * @param capacity   its capacity in mB
     * @param mode       how it holds fluid
     * @param canContain whether it may hold a fluid (a tank's {@code tfc:usable_in_*} tag; a bucket: a filled bucket exists)
     */
    public record ItemTank(FluidAmount content, int capacity, Mode mode, Predicate<String> canContain) {
        public ItemTank {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(canContain, "canContain");
        }
    }

    /**
     * A move that happened.
     *
     * @param barrel    the barrel's tank after it
     * @param item      the item's content after it
     * @param moved     what left the giver (the receiver may have taken less)
     * @param direction who gave
     */
    public record Result(FluidAmount barrel, FluidAmount item, FluidAmount moved, FluidTransferRules.BlockTransfer direction) {
    }

    /**
     * The transfer between the barrel's tank and a held container.
     *
     * @param barrel   the barrel's tank
     * @param sealed   whether the barrel is sealed (TFC {@code canModify}: nothing moves)
     * @param usable   the barrel's validator ({@code tfc:usable_in_barrel})
     * @param capacity the barrel's capacity
     * @param item     the container
     * @return the move, or empty when nothing can move
     */
    public static Optional<Result> transfer(FluidAmount barrel, boolean sealed, Predicate<String> usable, int capacity,
                                            ItemTank item) {
        final Tank block = new Tank(barrel, capacity, Mode.TANK, f -> !sealed && usable.test(f));
        final Tank held = new Tank(item.content(), item.capacity(), item.mode(), item.canContain());
        if (sealed) {
            return Optional.empty();
        }
        final FluidAmount aggressive = held.drain(Integer.MAX_VALUE, false);
        final FluidTransferRules.BlockTransfer direction = FluidTransferRules.blockTransfer(aggressive.isEmpty(),
            aggressive.isEmpty() ? 0 : block.fill(aggressive, false));
        final boolean toItem = direction == FluidTransferRules.BlockTransfer.FILL_ITEM;
        final Tank from = toItem ? block : held;
        final Tank to = toItem ? held : block;
        final Optional<FluidAmount> moved = transfer(from, to);
        return moved.map(m -> new Result(block.content, held.content, m, direction));
    }

    /** TFC {@code transferBetweenItemAndOther}. */
    private static Optional<FluidAmount> transfer(Tank from, Tank to) {
        final FluidAmount aggressive = from.drain(Integer.MAX_VALUE, false);
        final int maximum = to.fill(aggressive, false);
        if (maximum <= 0) {
            return Optional.empty();
        }
        final FluidAmount optimistic = from.drain(maximum, false);
        if (optimistic.amount() > 0 && to.fill(optimistic, false) > 0) {
            to.fill(from.drain(optimistic.amount(), true), true);
            return Optional.of(optimistic);
        }
        to.fill(from.drain(Integer.MAX_VALUE, true), true);
        return Optional.of(aggressive);
    }

    /** A NeoForge {@code FluidTank} or {@code FluidBucketWrapper}, mutable inside one transfer only. */
    private static final class Tank {
        FluidAmount content;
        final int capacity;
        final Mode mode;
        final Predicate<String> valid;

        Tank(FluidAmount content, int capacity, Mode mode, Predicate<String> valid) {
            this.content = content;
            this.capacity = capacity;
            this.mode = mode;
            this.valid = valid;
        }

        int fill(FluidAmount offered, boolean execute) {
            if (offered.isEmpty() || !valid.test(offered.fluid())) {
                return 0;
            }
            if (mode == Mode.BUCKET) {
                if (!content.isEmpty() || offered.amount() < capacity) {
                    return 0;
                }
                if (execute) {
                    content = new FluidAmount(offered.fluid(), capacity);
                }
                return capacity;
            }
            final int accepted;
            if (content.isEmpty()) {
                accepted = Math.min(capacity, offered.amount());
            } else if (content.fluid().equals(offered.fluid())) {
                accepted = Math.max(0, Math.min(capacity - content.amount(), offered.amount()));
            } else {
                accepted = 0;
            }
            if (execute && accepted > 0) {
                content = new FluidAmount(offered.fluid(), content.amount() + accepted);
            }
            return accepted;
        }

        FluidAmount drain(int max, boolean execute) {
            if (content.isEmpty() || max <= 0) {
                return FluidAmount.EMPTY;
            }
            if (mode == Mode.BUCKET && max < content.amount()) {
                return FluidAmount.EMPTY;
            }
            final int drained = Math.min(content.amount(), max);
            final FluidAmount out = new FluidAmount(content.fluid(), drained);
            if (execute) {
                content = drained == content.amount() ? FluidAmount.EMPTY
                    : new FluidAmount(content.fluid(), content.amount() - drained);
            }
            return out;
        }
    }
}
