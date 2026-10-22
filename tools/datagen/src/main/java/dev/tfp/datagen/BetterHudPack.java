/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.util.Set;

/**
 * Our BetterHud pack ({@code betterhud} skill, "Our content goes"): the YAML the installer copies into
 * {@code plugins/BetterHud/packs/tfp/}. Written under {@code betterhud/tfp/} of the content tree, which ships
 * as {@code /content/betterhud/tfp} in the jar ({@code dependency-bootstrap} §5).
 *
 * <p>M2-CAL-02: the date and season text, top-left, updated every 20 ticks. M2-CLIM-03 adds the ambient
 * temperature line below it (same font, same tick). Both texts carry a black shadow ({@code outline: true}) and sit on
 * the info plate, the temperature right of the thermometer bar ({@code plans/hud-layered-redesign.md}; plate at 1,1,
 * 124x22; date glyphs at 6,5; thermometer 64x8 at 6,13; temperature text at 73,13). The HUD is not {@code default}, so
 * it is shown to nobody until a {@code default-hud} edit lists it (the M1 decision keeps the default HUD empty).
 * Every id has the {@code tfp_} prefix; the font is BetterHud's default font (no {@code file}).
 */
final class BetterHudPack {
    static final String DIR = "betterhud/tfp/";
    /** The layout ids that {@link #LAYOUTS} writes. {@link HudLayouts} skips the catalogue readouts they duplicate. */
    static final Set<String> LAYOUT_IDS = Set.of("tfp_date", "tfp_ambient_temp");
    /**
     * The layouts {@link HudLayouts} writes for the info HUD (behind and beside these texts): the plate and the
     * thermometer bar. Listed in {@link #HUDS}; {@link HudLayouts} fails if it writes one that is not listed here.
     */
    static final Set<String> INFO_LAYOUTS = Set.of("tfp_m2_cal_02_info_plate", "tfp_m2_clim_03_ambient_temp_bar");

    private BetterHudPack() {
    }

    static void write(OutputTree out) {
        out.put(DIR + "texts/tfp-texts.yml", TEXTS);
        out.put(DIR + "layouts/tfp-layouts.yml", LAYOUTS);
        out.put(DIR + "huds/tfp-huds.yml", HUDS);
    }

    static final String TEXTS = """
            tfp_font:
              merge-default-bitmap: true
              use-unifont: true
              include: []
            """;

    static final String LAYOUTS = """
            tfp_date:
              offset: left
              texts:
                1:
                  name: tfp_font
                  pattern: "[tfp_date]  ([tfp_season])"
                  align: left
                  x: 6
                  y: 5
                  scale: 0.5
                  outline: true
                  layer: 3
                  tick: 20
            tfp_ambient_temp:
              offset: left
              texts:
                1:
                  name: tfp_font
                  pattern: "[tfp_ambient_temp]"
                  align: left
                  x: 73
                  y: 13
                  scale: 0.5
                  outline: true
                  layer: 3
                  tick: 20
            """;

    /** Plate first (behind), then the thermometer, then the two text lines (HudLayouts writes the first two). */
    static final String HUDS = """
            tfp_date_hud:
              layouts:
                1:
                  name: tfp_m2_cal_02_info_plate
                  gui:
                    x: 0
                    y: 0
                2:
                  name: tfp_m2_clim_03_ambient_temp_bar
                  gui:
                    x: 0
                    y: 0
                3:
                  name: tfp_date
                  gui:
                    x: 0
                    y: 0
                4:
                  name: tfp_ambient_temp
                  gui:
                    x: 0
                    y: 0
            """;
}
