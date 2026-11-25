/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick, getRepresentativeState,
 * isFluidValid, getWaterOutputPos, getWaterInputPos)
 */
package dev.tfp.paper.sluice;

import dev.tfp.core.pan.PanDeposits;
import dev.tfp.core.pan.SluiceRules;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.sluice.SluiceState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * PAR-PROC-13: the sluice. Registers the {@link SluiceDevice} kind with the device framework and the listener that takes
 * thrown soil in ({@link SluiceIntake}); the block {@code tfp:sluice} is written by datagen
 * ({@code configuration/sluice/sluice.yml}). Needs the device, loot and pan data, so it is listed after {@code pan}.
 */
public final class SluiceModule implements FeatureModule {
    public static final String ID = "sluice";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        LootService loot = context.services().require(LootService.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        CustomItems items = context.integrations().customItems();
        Supplier<PanDeposits> deposits = versioned(() -> context.registryService().registries());
        PaperSluiceEffects effects = new PaperSluiceEffects(context.scheduler(), blocks, loot, deposits,
            () -> context.integrations().blockVisuals(), devices::neighbourChanged);
        SluiceDevice.Services services = new SluiceDevice.Services(
            stack -> items.idOf(stack).filter(deposits.get()::isDeposit), SluiceRules.DEFAULT_CYCLE_TICKS, effects,
            devices.stacks());
        DeviceKind<SluiceState> kind = devices.register(SluiceDevice.kind(services));
        context.listen(new SluiceIntake(devices, kind, blocks, services, context.scheduler()));
        context.listen(new SluiceListener(blocks, effects, context.scheduler()));
    }

    /** The deposit table rebuilt when the registries change version. Thread-safe. */
    static Supplier<PanDeposits> versioned(Supplier<Registries> registries) {
        record Entry(long version, PanDeposits value) {
        }
        PanDeposits empty = PanDeposits.of(java.util.Map.of());
        AtomicReference<Entry> cache = new AtomicReference<>(new Entry(0, empty));
        return () -> {
            Registries r = registries.get();
            if (r == null || r.version() == 0) {
                return empty;
            }
            Entry e = cache.get();
            if (e.version() == r.version()) {
                return e.value();
            }
            RegistrySnapshot snapshot = r.get();
            PanDeposits value = PanDeposits.fromRegistry(snapshot);
            cache.set(new Entry(r.version(), value));
            return value;
        };
    }

}
