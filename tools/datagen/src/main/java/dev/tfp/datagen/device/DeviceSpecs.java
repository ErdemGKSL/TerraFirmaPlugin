/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/blockstates/firepit.json @ c3b4d9536 (the lit appearance split),
 *       src/main/resources/assets/tfc/blockstates/charcoal_forge.json and bellows.json @ c3b4d9536 (one look per level / facing)
 */
package dev.tfp.datagen.device;

import dev.tfp.datagen.DatagenException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * The device block specs of the devices generated before the shared spec existed (PAR-INFRA-08): the firepit family
 * (firepit, grill, pot), the charcoal forge and the bellows. Their generators ({@code Firepit}, {@code Grill},
 * {@code Pot}, {@code ForgeDevices}) still read and check TFC's sources; these factories turn what they read into a
 * {@link DeviceBlockSpec}, and {@link DeviceYaml#literal} writes it byte for byte as those generators do today
 * ({@code DeviceSpecParityTest}). Carriers and models come in as arguments: choosing them is the generators' (and later
 * the hitbox carrier selector's, PAR-INFRA-13) job, not the spec's.
 */
public final class DeviceSpecs {
    /** The firepit family's two appearances: TFC's blockstate draws {@code lit} and {@code axis}, the smoke is particles. */
    public static final String LIT = "lit";
    public static final String UNLIT = "unlit";

    private DeviceSpecs() {
    }

    /**
     * A firepit-family block (firepit, grill, pot): the given properties (which must contain {@code lit}), one appearance
     * per lit value on its carrier, no settings or loot yet (placeholder entries outside {@code configuration/}).
     */
    public static DeviceBlockSpec firepitFamily(String id, List<DeviceProperty> props, DeviceAppearance.Carrier lit,
            DeviceAppearance.Carrier unlit) {
        if (props.stream().noneMatch(p -> p.name().equals(LIT) && p.kind() == DeviceProperty.Kind.BOOLEAN)) {
            throw new DatagenException(id + ": a firepit-family block needs the boolean property lit");
        }
        return DeviceBlockSpec.builder(id)
            .properties(props)
            .appearance(DeviceAppearance.vanilla(LIT, lit))
            .appearance(DeviceAppearance.vanilla(UNLIT, unlit))
            .everyState(s -> "true".equals(s.get(LIT)) ? LIT : UNLIT, null)
            .build();
    }

    /**
     * The charcoal forge: one appearance {@code heat_N} per level of {@code heat}, every one on {@code carrier} with the
     * cold level's model (one vanilla state carries one model); each lit level adds a display of its own TFC model
     * (item {@code renderPrefix + "heat_N"}, scaled by {@code overlayScale}, block light = the level's light, sky 15).
     * Each level emits {@code light.applyAsInt(level)}. {@code models}: level to model id ({@code tfp:block/...}).
     */
    public static DeviceBlockSpec charcoalForge(String id, DeviceProperty heat, Map<Integer, String> models,
            DeviceAppearance.Carrier carrier, BigDecimal overlayScale, String renderPrefix, IntUnaryOperator light,
            BigDecimal hardness, BigDecimal resistance, Map<String, String> stateMappings) {
        if (heat.kind() != DeviceProperty.Kind.INT) {
            throw new DatagenException(id + ": the heat property must be an int range");
        }
        int min = Integer.parseInt(heat.values().getFirst());
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(heat);
        String cold = model(id, models, min);
        for (String v : heat.values()) {
            int level = Integer.parseInt(v);
            String name = "heat_" + level;
            DeviceAppearance a = DeviceAppearance.modelled(name, carrier, cold);
            if (level > min) {
                String item = renderPrefix + name;
                b.renderItem(item, model(id, models, level));
                a = a.withOverlay(List.of(DeviceAppearance.Renderer.item(item).scaled(overlayScale)
                    .lit(Math.min(15, light.applyAsInt(level)), 15)));
            }
            b.appearance(a);
        }
        b.everyState(s -> "heat_" + s.get(heat.name()), s -> light.applyAsInt(Integer.parseInt(s.get(heat.name()))))
            .settings(hardness, resistance)
            .lootSelf();
        stateMappings.forEach(b::stateMapping);
        return b.build();
    }

    /**
     * A horizontal-facing device drawn with the carrier of each facing (the bellows: one appearance per facing, named
     * after it), placed by its own block item.
     */
    public static DeviceBlockSpec facing(String id, DeviceProperty facing, UnaryOperator<String> carrierByFacing,
            String itemLangKey, BigDecimal hardness, BigDecimal resistance) {
        DeviceBlockSpec.Builder b = DeviceBlockSpec.builder(id).property(facing);
        for (String f : facing.values()) {
            b.appearance(DeviceAppearance.vanilla(f, DeviceAppearance.Carrier.state(carrierByFacing.apply(f))));
        }
        return b.everyState(s -> s.get(facing.name()), null)
            .settings(hardness, resistance)
            .lootSelf()
            .blockItem(itemLangKey)
            .build();
    }

    private static String model(String id, Map<Integer, String> models, int level) {
        String m = models.get(level);
        if (m == null) {
            throw new DatagenException(id + " has no model for level " + level);
        }
        return m;
    }
}
