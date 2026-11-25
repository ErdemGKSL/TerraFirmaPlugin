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
import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.device.automation.TfcSideTables;
import dev.tfp.core.powderkeg.PowderkegState;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * PAR-INFRA-60: hopper automation of device blocks. Provides {@link DeviceAutomation} (the registration API for device
 * modules), registers TFC's sides for the barrel, firepit, grill, pot, quern and powder keg, and listens to Paper's
 * {@code HopperInventorySearchEvent} ({@link HopperAutomationListener}) and to droppers facing a device ({@link DropperAutomationListener}, PAR-INFRA-63). The composter keeps its own hopper-below rule
 * (PAR-PROC-11: its output is a count, not a slot); its TFC table is {@link TfcSideTables#composter()}.
 *
 * <p>The {@code *EnableAutomation} switches are read once at enable from {@code config.yml} ({@link AutomationRules#KEYS},
 * optional, TFC default {@code true}).
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
        AutomationRules.Switches switches = switches(context.config().file());
        DeviceAutomation automation = new DeviceAutomation();
        registerDefaults(automation, switches, registrations);
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
    }

    /** TFC's switches from {@code config.yml}, TFC's defaults for missing keys or a missing file. */
    static AutomationRules.Switches switches(Path file) {
        File f = file == null ? null : file.toFile();
        if (f == null || !f.isFile()) {
            return AutomationRules.Switches.TFC_DEFAULTS;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
        List<String> k = AutomationRules.KEYS;
        return new AutomationRules.Switches(yaml.getBoolean(k.get(0), true), yaml.getBoolean(k.get(1), true),
            yaml.getBoolean(k.get(2), true), yaml.getBoolean(k.get(3), true), yaml.getBoolean(k.get(4), true));
    }

    /** The done devices with TFC's sides, switches and {@code canModify} gates. */
    static void registerDefaults(DeviceAutomation automation, AutomationRules.Switches s, List<AutoCloseable> out) {
        DeviceAutomation.SlotFix cascade = slots -> AutomationRules.cascadeFuel(slots, TfcSideTables.FUEL_CONSUME,
            TfcSideTables.FUEL_INPUT);
        out.add(automation.register(DeviceAutomation.Spec.of(BARREL,
                state -> TfcSideTables.barrel(Face.parse(state.properties().get("facing")).orElse(Face.UP)))
            .withEnabled(s::barrel)
            .withLocked(state -> state instanceof BarrelData b && b.sealed())));
        out.add(automation.register(DeviceAutomation.Spec.of(FIREPIT, state -> TfcSideTables.firepit())
            .withEnabled(s::firePit).withAfterMove(cascade)));
        out.add(automation.register(DeviceAutomation.Spec.of(GRILL, state -> TfcSideTables.grill())
            .withEnabled(s::firePit).withAfterMove(cascade)));
        out.add(automation.register(DeviceAutomation.Spec.of(POT, state -> TfcSideTables.pot())
            .withEnabled(s::firePit).withAfterMove(cascade)));
        out.add(automation.register(DeviceAutomation.Spec.of(QUERN, state -> TfcSideTables.quern())
            .withEnabled(s::quern)));
        out.add(automation.register(DeviceAutomation.Spec.of(POWDERKEG, state -> TfcSideTables.powderkeg())
            .withEnabled(s::powderKeg)
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
