/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement,
 *       canSurvive, updateShape),
 *       src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut, the tank),
 *       src/main/java/net/dries007/tfc/common/items/LampBlockItem.java @ c3b4d9536 (the fuel travels with the item),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a lamp with fuel lights)
 */
package dev.tfp.paper.lamp;

import dev.tfp.core.barrel.BarrelFluidTransfer;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.fluid.FluidTransferRules;
import dev.tfp.core.lamp.LampData;
import dev.tfp.core.lamp.LampFuel;
import dev.tfp.core.lamp.LampRules;
import dev.tfp.paper.advancement.criteria.AdvancementTriggerEvent;
import dev.tfp.paper.advancement.criteria.CriteriaMap;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-19: the nine metal lamps as device kinds ({@code tfp:metal/lamp/<metal>}), TFC's {@code LampBlock} and
 * {@code LampBlockEntity} on the device framework.
 *
 * <p>The fuel burns lazily: a lit lamp is never ticked, the kind's catch-up runs TFC's {@code checkHasRanOut} once over the
 * whole gap ({@link LampRules#advance}) whenever the lamp is looked at, a chunk loads, or the callback queued for the tick it
 * runs dry fires ({@link LampEffects#callbackIn}, which also catches a calendar jump within a second). The block state
 * ({@code lit}) follows the record after every access. The device is removed as {@code SAVE}: the fuel goes onto the dropped
 * lamp and back into the lamp placed from it (TFC {@code CopyFluidFunction} and {@code LampBlockItem}).
 *
 * <p>Right click, in TFC's {@code useItemOn} order: an empty hand while sneaking puts a lit lamp out; a fluid container is
 * emptied into the lamp or filled from it (only a fuel of the lamp goes in; a lamp filled with lava lights itself); a held
 * container with fluid in it is otherwise swallowed, so the fluid is not dumped.
 */
public final class LampDevice {
    public static final String PREFIX = "tfp:metal/lamp/";
    public static final String HANGING = "hanging";
    public static final String LIT = "lit";

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param tanks   the fluid handler of a held container
     * @param effects the world effects
     * @param support whether a block {@code hanging} (or not) has what it needs to stay: a sturdy centre on the face of the
     *                block above (hanging) or below (standing); region thread of the block
     */
    public record Services(BarrelItemTanks tanks, LampEffects effects, BiPredicate<Block, Boolean> support) {
        public Services {
            Objects.requireNonNull(tanks, "tanks");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(support, "support");
        }
    }

    private final Services services;
    private final CustomBlocks blocks;

    public LampDevice(Services services, CustomBlocks blocks) {
        this.services = Objects.requireNonNull(services, "services");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    public static String id(String metal) {
        return PREFIX + metal;
    }

    /** The metal of a lamp block id, or null for any other id. */
    public static String metal(String blockId) {
        return blockId != null && blockId.startsWith(PREFIX) ? blockId.substring(PREFIX.length()) : null;
    }

    /** One kind per metal: the fuels a lamp takes depend on it (lava only in red and blue steel). */
    public List<DeviceKind<LampData>> kinds(List<String> metals) {
        return metals.stream().map(metal -> DeviceKind.builder(id(metal), LampData.CODEC, LampData.FRESH)
            .advance((record, from, to) -> record.withState(LampRules.advance(record.state(), metal, to - from)))
            .removal(DeviceKind.RemoveBehaviour.SAVE)
            .handler(new Handler(metal))
            .build()).toList();
    }

    /** The block state that shows {@code data} on a lamp in {@code state}. */
    static BlockStateRef shown(BlockStateRef state, LampData data) {
        return state.with(LIT, Boolean.toString(data.lit()));
    }

    /** True when the lamp state hangs. */
    static boolean hanging(BlockStateRef state) {
        return "true".equals(state.properties().get(HANGING));
    }

    /**
     * TFC {@code ForgeEventHandler.onFireStart}: a fire start on an unlit lamp lights it when it holds a fuel and cancels the
     * start either way. Region thread of {@code block}. Returns whether the start was handled.
     */
    public boolean light(Block block, DeviceService devices, List<DeviceKind<LampData>> kinds) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || metal(state.get().id()) == null) {
            return false;
        }
        DeviceKind<LampData> kind = kinds.stream().filter(k -> k.id().equals(state.get().id())).findFirst().orElse(null);
        if (kind == null) {
            return false;
        }
        String metal = metal(kind.id());
        boolean[] wasLit = {false};
        Optional<DeviceRecord<LampData>> after = devices.edit(block, kind, (record, now) -> {
            wasLit[0] = record.state().lit();
            return record.withState(LampRules.light(record.state(), metal));
        });
        after.ifPresent(r -> sync(block, state.get(), r.state(), metal));
        return after.isPresent() && !wasLit[0];
    }

    /**
     * The block state follows the record; a lit lamp queues the callback for the tick it runs dry. Called after every access.
     */
    void sync(Block block, BlockStateRef current, LampData data, String metal) {
        BlockStateRef wanted = shown(current, data);
        if ("true".equals(current.properties().get(LIT)) && !data.lit()) {
            services.effects().extinguished(block);
        }
        if (!wanted.equals(current)) {
            services.effects().show(block, wanted);
        }
        LampRules.ticksUntilOut(data, metal).ifPresent(t -> services.effects().callbackIn(block, t));
    }

    private final class Handler implements DeviceHandler<LampData> {
        private final String metal;

        Handler(String metal) {
            this.metal = metal;
        }

        private DeviceResult<LampData> finish(DeviceContext<LampData> ctx, DeviceRecord<LampData> record, boolean consumed) {
            sync(ctx.block(), ctx.state(), record.state(), metal);
            if (consumed) {
                return DeviceResult.consume(record);
            }
            return record.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(record);
        }

        @Override
        public DeviceResult<LampData> useWithItem(DeviceContext<LampData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            LampData data = ctx.deviceState();
            ItemStack stack = ctx.item();
            boolean empty = StackCodec.isEmpty(stack);
            if (empty && player.isSneaking() && data.lit()) {
                return finish(ctx, ctx.record().withState(LampRules.quench(data)), true);
            }
            if (empty) {
                return finish(ctx, ctx.record(), false);
            }
            Optional<BarrelFluidTransfer.ItemTank> tank = services.tanks().tank(stack);
            if (tank.isEmpty()) {
                return finish(ctx, ctx.record(), false);
            }
            Optional<BarrelFluidTransfer.Result> moved = BarrelFluidTransfer.transfer(data.tank(), false,
                fluid -> LampRules.accepts(fluid, metal), LampRules.CAPACITY, tank.get());
            if (moved.isPresent()) {
                BarrelFluidTransfer.Result r = moved.get();
                ItemStack container = services.tanks().withContent(stack, r.item());
                services.effects().transfer(ctx.block(), r.moved(), r.direction() == FluidTransferRules.BlockTransfer.FILL_ITEM);
                updateHand(player, ctx.hand(), stack, container, player.getGameMode() == GameMode.CREATIVE);
                LampData filled = LampRules.afterFill(data.withTank(r.barrel()), metal, true);
                if (filled.lit() && LampRules.fuel(filled, metal).filter(LampFuel::endless).isPresent()) {
                    AdvancementTriggerEvent.publish(player, CriteriaMap.LAVA_LAMP); // TFC LAVA_LAMP
                }
                return finish(ctx, ctx.record().withState(filled), true);
            }
            if (!tank.get().content().isEmpty()) {
                return finish(ctx, ctx.record(), true); // the player must not dump what they think is lamp fuel
            }
            return finish(ctx, ctx.record(), false);
        }

        /**
         * TFC {@code setPlacedBy}: the counter is reset, and {@code getStateForPlacement}: the lamp hangs when the player
         * looks up. The fuel came with the item; the lamp is never lit by placing it.
         */
        @Override
        public DeviceResult<LampData> placed(DeviceContext<LampData> ctx) {
            LampData data = ctx.deviceState();
            LampData unlit = new LampData(data.tank(), false, 0);
            boolean hangs = ctx.player() != null && lookingUp(ctx.player());
            BlockStateRef wanted = shown(ctx.state().with(HANGING, Boolean.toString(hangs)), unlit);
            if (!wanted.equals(ctx.state())) {
                services.effects().show(ctx.block(), wanted);
            }
            return DeviceResult.update(ctx.record().withState(unlit));
        }

        /** TFC {@code updateShape}: a lamp whose support goes breaks off; otherwise the state follows the record. */
        @Override
        public DeviceResult<LampData> neighbourChanged(DeviceContext<LampData> ctx) {
            if (!services.support().test(ctx.block(), hanging(ctx.state()))) {
                services.effects().breakBlock(ctx.block());
                return DeviceResult.pass();
            }
            return finish(ctx, ctx.record(), false);
        }
    }

    /** Vanilla {@code orderedByNearest}: the first vertical direction is up when the pitch is negative. */
    static boolean lookingUp(Player player) {
        return player.getLocation().getPitch() < 0f;
    }

    /** TFC {@code FluidHelpers}: the held container replaced by its filled or emptied self. */
    static void updateHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack container, boolean creative) {
        if (creative) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, container);
            return;
        }
        ItemStack rest = held.clone();
        rest.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, rest);
        for (ItemStack r : player.getInventory().addItem(container).values()) {
            player.getWorld().dropItem(player.getLocation(), r, e -> e.setPickupDelay(0));
        }
    }

    /**
     * TFC {@code getStateForPlacement}: the lamp is not placed when the direction it would take has no support (the state is
     * {@code null}). The placement event is cancelled for that case. The state itself is written by the kind's placement
     * handler one tick later.
     */
    public final class Placement implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block placed = event.getBlockPlaced();
            Optional<BlockStateRef> state = blocks.stateAt(placed);
            if (state.isEmpty() || metal(state.get().id()) == null) {
                return;
            }
            if (!services.support().test(placed, lookingUp(event.getPlayer()))) {
                event.setCancelled(true);
            }
        }
    }
}
