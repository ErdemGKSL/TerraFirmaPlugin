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
import dev.tfp.paper.barrel.BarrelDevice;
import dev.tfp.paper.barrel.BarrelScreenLink;
import dev.tfp.paper.barrel.gui.BarrelScreens;
import dev.tfp.paper.firepit.FirepitScreenLink;
import dev.tfp.paper.firepit.gui.FirepitScreens;
import dev.tfp.paper.grill.gui.GrillScreens;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.pot.PotScreenLink;
import dev.tfp.paper.pot.gui.PotScreens;
import dev.tfp.paper.powderkeg.PowderkegLink;
import dev.tfp.paper.powderkeg.PowderkegScreens;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
        registerDefaults(automation, () -> switches(config.current().automation()), registrations,
            screens(context), barrelSettle(context.services()));
        context.services().provide(DeviceAutomation.class, automation);
        context.listen(new HopperAutomationListener(automation, new HopperMover(devices),
            context.integrations().customBlocks(), context.scheduler(), HopperAutomationListener.HopperPort::of,
            context.logger()));
        context.listen(new DropperAutomationListener(automation, new HopperMover(devices),
            context.integrations().customBlocks(), context.scheduler(),
            DropperAutomationListener::liveInventory, item -> {
                Inventory hand = Bukkit.createInventory(null, InventoryType.HOPPER);
                hand.setItem(0, item);
                return hand;
            }, context.logger()));
        context.listen(new HopperMinecartAutomation(automation, new HopperMover(devices),
            context.integrations().customBlocks(), context.scheduler(),
            context.logger()));
    }

    /** The barrel's after-move settle, provided by the barrel module (looked up at each move: it may enable later). */
    static DeviceAutomation.Settle barrelSettle(ModuleServices services) {
        return (record, now, block) -> {
            Optional<BarrelDevice.HopperSettle> settle = services.get(BarrelDevice.HopperSettle.class);
            return settle.isPresent() ? settle.get().apply(record, now, block) : record;
        };
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
        registerDefaults(automation, switches, out, kindId -> DeviceAutomation.ScreenSync.NONE,
            DeviceAutomation.Settle.NONE);
    }

    /**
     * The screens of the devices that have one (PAR-INFRA-66), looked up when a move happens (the screen modules enable
     * in any order): the open screen's copy is committed before and reloaded after a hopper move.
     */
    static Function<String, DeviceAutomation.ScreenSync> screens(ModuleContext context) {
        ModuleServices services = context.services();
        Scheduler scheduler = context.scheduler();
        Map<String, DeviceAutomation.ScreenSync> byKind = Map.of(
            BARREL, new ViewerSync(b -> services.get(BarrelScreens.class)
                    .flatMap(s -> s.viewer(BarrelScreenLink.Key.of(b))).map(GuiSession::viewer),
                b -> services.get(BarrelScreens.class).ifPresent(s -> s.edit(BarrelScreenLink.Key.of(b))),
                b -> services.get(BarrelScreens.class).ifPresent(s -> s.reload(BarrelScreenLink.Key.of(b))), scheduler),
            FIREPIT, new ViewerSync(b -> services.get(FirepitScreens.class)
                    .flatMap(s -> s.viewer(FirepitScreenLink.Key.of(b))).map(GuiSession::viewer),
                b -> services.get(FirepitScreens.class).ifPresent(s -> s.edit(FirepitScreenLink.Key.of(b))),
                b -> services.get(FirepitScreens.class).ifPresent(s -> s.reload(FirepitScreenLink.Key.of(b))), scheduler),
            POT, new ViewerSync(b -> services.get(PotScreens.class)
                    .flatMap(s -> s.viewer(PotScreenLink.Key.of(b))).map(GuiSession::viewer),
                b -> services.get(PotScreens.class).ifPresent(s -> s.edit(PotScreenLink.Key.of(b))),
                b -> services.get(PotScreens.class).ifPresent(s -> s.reload(PotScreenLink.Key.of(b))), scheduler),
            POWDERKEG, new ViewerSync(b -> services.get(PowderkegScreens.class)
                    .flatMap(s -> s.viewer(PowderkegLink.Key.of(b))).map(GuiSession::viewer),
                b -> services.get(PowderkegScreens.class).ifPresent(s -> s.edit(PowderkegLink.Key.of(b))),
                b -> services.get(PowderkegScreens.class).ifPresent(s -> s.reload(PowderkegLink.Key.of(b))), scheduler),
            GRILL, new ViewerSync(b -> services.get(GrillScreens.class)
                    .flatMap(s -> s.keyAt(b).flatMap(s::viewer)).map(GuiSession::viewer),
                b -> services.get(GrillScreens.class).ifPresent(s -> s.keyAt(b).ifPresent(s::edit)),
                b -> services.get(GrillScreens.class).ifPresent(s -> s.keyAt(b).ifPresent(s::reload)), scheduler));
        return kindId -> byKind.getOrDefault(kindId, DeviceAutomation.ScreenSync.NONE);
    }

    static void registerDefaults(DeviceAutomation automation, Supplier<AutomationRules.Switches> switches,
            List<AutoCloseable> out, Function<String, DeviceAutomation.ScreenSync> screens,
            DeviceAutomation.Settle barrelSettle) {
        DeviceAutomation.SlotFix cascade = slots -> AutomationRules.cascadeFuel(slots, TfcSideTables.FUEL_CONSUME,
            TfcSideTables.FUEL_INPUT);
        out.add(automation.register(DeviceAutomation.Spec.of(BARREL,
                state -> TfcSideTables.barrel(Face.parse(state.properties().get("facing")).orElse(Face.UP)))
            .withEnabled(() -> switches.get().barrel())
            .withLocked(state -> state instanceof BarrelData b && b.sealed())
            .withScreens(screens.apply(BARREL)).withSettle(barrelSettle)));
        out.add(automation.register(DeviceAutomation.Spec.of(FIREPIT, state -> TfcSideTables.firepit())
            .withEnabled(() -> switches.get().firePit()).withAfterMove(cascade)
            .withScreens(screens.apply(FIREPIT))));
        out.add(automation.register(DeviceAutomation.Spec.of(GRILL, state -> TfcSideTables.grill())
            .withEnabled(() -> switches.get().firePit()).withAfterMove(cascade)
            .withScreens(screens.apply(GRILL))));
        out.add(automation.register(DeviceAutomation.Spec.of(POT, state -> TfcSideTables.pot())
            .withEnabled(() -> switches.get().firePit()).withAfterMove(cascade)
            .withScreens(screens.apply(POT))));
        out.add(automation.register(DeviceAutomation.Spec.of(QUERN, state -> TfcSideTables.quern())
            .withEnabled(() -> switches.get().quern()).withScreens(screens.apply(QUERN))));
        out.add(automation.register(DeviceAutomation.Spec.of(POWDERKEG, state -> TfcSideTables.powderkeg())
            .withEnabled(() -> switches.get().powderKeg())
            .withLocked(state -> state instanceof PowderkegState k && k.sealed())
            .withScreens(screens.apply(POWDERKEG))));
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
