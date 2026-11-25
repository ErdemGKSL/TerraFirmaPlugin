/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (*EnableAutomation, default true),
 *       src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (BarrelInventory.canModify),
 *       src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (canModify)
 */
package dev.tfp.paper.device.automation;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.TfcSideTables;
import dev.tfp.core.powderkeg.PowderkegState;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * PAR-INFRA-60: hopper automation of device blocks. Provides {@link DeviceAutomation} (the registration API for device
 * modules), registers TFC's sides for the barrel, firepit, grill, pot, quern and powder keg, and listens to Paper's
 * {@code HopperInventorySearchEvent} ({@link HopperAutomationListener}) and to droppers facing a device ({@link DropperAutomationListener}, PAR-INFRA-63). The composter keeps its own hopper-below rule
 * (PAR-PROC-11: its output is a count, not a slot); its TFC table is {@link TfcSideTables#composter()}.
 *
 * <p>The {@code *EnableAutomation} switches are the typed {@code automation} section of {@code config.yml}
 * ({@link TfpConfig.Automation}, TFC default {@code true}); every move reads the current config, so
 * {@code /tfp reload config} applies a change at once (PAR-INFRA-64). Hopper minecarts over a device empty it too
 * ({@link HopperMinecartAutomation}).
 */
public final class DeviceAutomationModule implements FeatureModule {
    public static final String ID = "device_automation";
    /** Device kind ids (the device classes' {@code ID} constants). */
    static final String BARREL = "tfp:wood/barrel";
    static final String FIREPIT = "tfp:firepit";
    static final String GRILL = "tfp:grill";
    static final String POT = "tfp:pot";
    static final String QUERN = "tfp:quern";
    static final String POWDERKEG = "tfp:powderkeg";

    private final List<AutoCloseable> registrations = new ArrayList<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        ConfigService config = context.config();
        DeviceAutomation automation = new DeviceAutomation();
        registerDefaults(automation, () -> switches(config.current().automation()), registrations);
        context.services().provide(DeviceAutomation.class, automation);
        context.listen(new HopperAutomationListener(automation, new HopperMover(devices),
            context.integrations().customBlocks(), context.scheduler(), HopperAutomationListener.HopperPort::of,
            HopperAutomationListener::screenNearby, context.logger()));
        context.listen(new DropperAutomationListener(automation, new HopperMover(devices),
            context.integrations().customBlocks(), context.scheduler(), HopperAutomationListener::screenNearby,
            DropperAutomationListener::liveInventory, item -> {
                Inventory hand = Bukkit.createInventory(null, InventoryType.HOPPER);
                hand.setItem(0, item);
                return hand;
            }, context.logger()));
        context.listen(new HopperMinecartAutomation(automation, new HopperMover(devices),
            context.integrations().customBlocks(), context.scheduler(), HopperAutomationListener::screenNearby,
            context.logger()));
    }

    /** The {@code automation} section of the config as the rules' switches. */
    static AutomationRules.Switches switches(TfpConfig.Automation a) {
        return new AutomationRules.Switches(a.barrel(), a.firePit(), a.quern(), a.composter(), a.powderKeg());
    }

    /**
     * The done devices with TFC's sides, switches and {@code canModify} gates. {@code switches} is read at every move (a
     * reload of {@code config.yml} replaces what it returns).
     */
    static void registerDefaults(DeviceAutomation automation, Supplier<AutomationRules.Switches> switches,
            List<AutoCloseable> out) {
        DeviceAutomation.SlotFix cascade = slots -> AutomationRules.cascadeFuel(slots, TfcSideTables.FUEL_CONSUME,
            TfcSideTables.FUEL_INPUT);
        out.add(automation.register(DeviceAutomation.Spec.of(BARREL,
                state -> TfcSideTables.barrel(Face.parse(state.properties().get("facing")).orElse(Face.UP)))
            .withEnabled(() -> switches.get().barrel())
            .withLocked(state -> state instanceof BarrelData b && b.sealed())));
        out.add(automation.register(DeviceAutomation.Spec.of(FIREPIT, state -> TfcSideTables.firepit())
            .withEnabled(() -> switches.get().firePit()).withAfterMove(cascade)));
        out.add(automation.register(DeviceAutomation.Spec.of(GRILL, state -> TfcSideTables.grill())
            .withEnabled(() -> switches.get().firePit()).withAfterMove(cascade)));
        out.add(automation.register(DeviceAutomation.Spec.of(POT, state -> TfcSideTables.pot())
            .withEnabled(() -> switches.get().firePit()).withAfterMove(cascade)));
        out.add(automation.register(DeviceAutomation.Spec.of(QUERN, state -> TfcSideTables.quern())
            .withEnabled(() -> switches.get().quern())));
        out.add(automation.register(DeviceAutomation.Spec.of(POWDERKEG, state -> TfcSideTables.powderkeg())
            .withEnabled(() -> switches.get().powderKeg())
            .withLocked(state -> state instanceof PowderkegState k && k.sealed())));
    }

    @Override
    public void disable() {
        for (AutoCloseable c : registrations) {
            try {
                c.close();
            } catch (Exception ignored) {
                // map removal does not throw
            }
        }
        registrations.clear();
    }
}
