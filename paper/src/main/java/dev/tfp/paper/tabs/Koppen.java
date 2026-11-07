/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/KoppenClimateClassification.java @ c3b4d9536
 */
package dev.tfp.paper.tabs;

import java.util.Locale;

/**
 * PAR-SURV-07: TFC's Koppen climate class, shown at the top of the climate page. Purely decorative in TFC too. Platform
 * free; kept in the tabs package because nothing else uses it.
 */
public enum Koppen {
    AF, AM, AW, AS,
    BWH, BWK, BSH, BSK,
    CSA, CSB, CSC, CWA, CWB, CWC, CFA, CFB, CFC,
    DSA, DSB, DSC, DSD, DWA, DWB, DWC, DWD, DFA, DFB, DFC, DFD,
    ET, EF;

    /** TFC's serialized name, the lang key suffix. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** TFC {@code KoppenClimateClassification.classify}, same comparisons and float arithmetic. */
    public static Koppen classify(float averageTemperature, float rainfall, float rainVar, boolean isInNorthernHemisphere) {
        if (!isInNorthernHemisphere) {
            rainVar = -rainVar;
        }
        if (averageTemperature < -17f + 0.006 * rainfall) {
            return EF;
        } else if (averageTemperature <= -12f) {
            return ET;
        } else if (rainfall < 75f) {
            return averageTemperature > 18f ? BWH : BWK;
        } else if (rainfall < 150f) {
            return averageTemperature > 18 ? BSH : BSK;
        } else if (averageTemperature > 21f) {
            if (rainfall * (1 + rainVar) > 600f) {
                return AM;
            } else if (rainVar > 0.5f) {
                return AW;
            } else if (rainVar < -0.5f) {
                return AS;
            } else {
                return AF;
            }
        } else if (averageTemperature > 8f) {
            if (averageTemperature > 17f) {
                return split(rainVar, CWA, CSA, CFA);
            } else if (averageTemperature > 12f) {
                return split(rainVar, CWB, CSB, CFB);
            } else {
                return split(rainVar, CWC, CSC, CFC);
            }
        } else if (averageTemperature > 3f) {
            return split(rainVar, DWA, DSA, DFA);
        } else if (averageTemperature > -2f) {
            return split(rainVar, DWB, DSB, DFB);
        } else if (averageTemperature > -8f) {
            return split(rainVar, DWC, DSC, DFC);
        }
        return split(rainVar, DWD, DSD, DFD);
    }

    private static Koppen split(float rainVar, Koppen wet, Koppen dry, Koppen even) {
        if (rainVar > 0.5f) {
            return wet;
        } else if (rainVar < -0.5f) {
            return dry;
        }
        return even;
    }
}
