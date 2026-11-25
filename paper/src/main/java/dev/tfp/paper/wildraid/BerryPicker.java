/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (getProductItem, stateAfterPicking)
 */
package dev.tfp.paper.wildraid;

import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.paper.berry.BerryBushDevice;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Picks one product from a stationary berry bush for an animal ({@code SeasonalPlantBlock.getProductItem} then
 * {@code stateAfterPicking}), through the berry module's public pieces: the bush's device record is settled to now
 * ({@link BerryBushDevice#settle}), a fruiting one takes the product and turns healthy with its last-picked tick set
 * ({@link BerryRules#pick}), and the visual state is written ({@link BerryBushDevice#apply}) exactly as a player's click does
 * ({@code BerryBushDevice}'s {@code useWithItem}, without the player). Region thread of the bush.
 */
final class BerryPicker {
    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final BerryBushDevice.Services services;

    BerryPicker(DeviceService devices, CustomBlocks blocks, BerryBushDevice.Services services) {
        this.devices = devices;
        this.blocks = blocks;
        this.services = services;
    }

    /** True for the id of a fruiting stationary bush block state. */
    static boolean fruiting(BlockStateRef state) {
        return BerryBushDevice.species(state.id()).isPresent()
            && "fruiting".equals(state.properties().get(BerryBushDevice.LIFECYCLE));
    }

    @SuppressWarnings("unchecked")
    Optional<ItemStack> pick(Block bush) {
        Optional<BlockStateRef> state = blocks.stateAt(bush);
        if (state.isEmpty() || !fruiting(state.get())) {
            return Optional.empty();
        }
        BushSpecies species = BerryBushDevice.species(state.get().id()).orElseThrow();
        Optional<DeviceKind<?>> kind = devices.kinds().find(state.get().id());
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        AtomicReference<ItemStack> taken = new AtomicReference<>();
        AtomicReference<BerryBushDevice.Settled> result = new AtomicReference<>();
        Optional<?> stored = devices.edit(bush, (DeviceKind<BerryState>) kind.get(), (record, now) -> {
            BerryBushDevice.Settled settled = BerryBushDevice.settle(services, bush, species, record.state(), now);
            BerryState s = settled.state();
            Optional<ItemStack> product = services.effects().product(species);
            if (s.dead() || s.bush().lifecycle() != Lifecycle.FRUITING || product.isEmpty()) {
                result.set(settled);
                return record.withState(s);
            }
            BerryRules.Pick pick = BerryRules.pick(s.bush(), now);
            BerryBushDevice.Settled picked = new BerryBushDevice.Settled(s.withBush(pick.bush()), species, settled.step(), now);
            result.set(picked);
            taken.set(product.get());
            return record.withState(picked.state());
        });
        if (stored.isPresent() && result.get() != null) {
            BerryBushDevice.apply(services, bush, result.get());
        }
        ItemStack product = taken.get();
        if (product != null) {
            services.effects().pickSound(bush);
        }
        return Optional.ofNullable(product);
    }

    /** The effects of the picker's services, for tests. */
    BerryEffects effects() {
        return services.effects();
    }
}
