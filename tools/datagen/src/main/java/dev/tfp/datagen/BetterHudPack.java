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
 * temperature line below it (same font, same tick). Both texts carry a black shadow ({@code outline: true}) so they
 * read over a bright sky or snow (2026-10-22 screenshot review). The HUD is not {@code default}, so
 * it is shown to nobody until a {@code default-hud} edit lists it (the M1 decision keeps the default HUD empty).
 * Every id has the {@code tfp_} prefix; the font is BetterHud's default font (no {@code file}).
 */
final class BetterHudPack {
    static final String DIR = "betterhud/tfp/";
    /** The layout ids that {@link #LAYOUTS} writes. {@link HudLayouts} skips the catalogue readouts they duplicate. */
    static final Set<String> LAYOUT_IDS = Set.of("tfp_date", "tfp_ambient_temp");

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
              texts:
                1:
                  name: tfp_font
                  pattern: "[tfp_date]  ([tfp_season])"
                  align: left
                  x: 2
                  y: 2
                  scale: 0.5
                  outline: true
                  tick: 20
            tfp_ambient_temp:
              texts:
                1:
                  name: tfp_font
                  pattern: "[tfp_ambient_temp]"
                  align: left
                  x: 2
                  y: 12
                  scale: 0.5
                  outline: true
                  tick: 20
            """;

    static final String HUDS = """
            tfp_date_hud:
              layouts:
                1:
                  name: tfp_date
                  gui:
                    x: 0
                    y: 0
                2:
                  name: tfp_ambient_temp
                  gui:
                    x: 0
                    y: 0
            """;
}
