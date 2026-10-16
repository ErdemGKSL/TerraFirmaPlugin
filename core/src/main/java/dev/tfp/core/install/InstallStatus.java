/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import dev.tfp.core.install.ConfigEdit.Effect;
import dev.tfp.core.install.ConfigEdit.Outcome;
import java.util.ArrayList;
import java.util.List;

/**
 * What the install step means for this server run ({@code dependency-bootstrap} §6): restart banner,
 * degraded mode and pack rebuild. {@link #decide} is pure.
 *
 * @param restartRequired a restart-only key was changed now: it takes effect after the next restart
 * @param degraded        TFC worlds must not be generated in this run (a mandatory edit is not in effect)
 * @param packRebuild     the CraftEngine pack must be rebuilt (our content or a pack setting changed)
 * @param banner          lines to log (empty when there is nothing to say)
 */
public record InstallStatus(boolean restartRequired, boolean degraded, boolean packRebuild, List<String> banner) {
    public static final InstallStatus NOTHING = new InstallStatus(false, false, false, List.of());
    /** First banner line when required setup is not in effect (the owner must act; not just a restart). */
    public static final String SETUP_INCOMPLETE = "==================== TerraFirmaPlugin: SETUP INCOMPLETE ====================";

    public InstallStatus {
        banner = List.copyOf(banner);
    }

    /** One edit's result, as the paper installer saw it. */
    public record EditResult(ConfigEdit edit, Outcome outcome, String before, String after, String detail) {
    }

    /**
     * @param edits              every edit's result (empty if {@code auto-install.config-edits} is off)
     * @param craftEnginePresent CraftEngine is installed (mandatory edits only matter then)
     * @param ceContentChanged   the installer wrote or removed files in CraftEngine's {@code resources/tfp}
     * @param failures           content installs that failed (each one line); our blocks may be missing
     */
    public static InstallStatus decide(List<EditResult> edits, boolean craftEnginePresent, boolean ceContentChanged,
            List<String> failures) {
        List<String> restartLines = new ArrayList<>();
        List<String> blockingLines = new ArrayList<>();
        for (String f : failures) {
            blockingLines.add("  " + f);
        }
        boolean packRebuild = ceContentChanged;
        for (EditResult r : edits) {
            ConfigEdit e = r.edit();
            boolean ceRestartKey = e.effect() == Effect.RESTART && e.mandatory() && craftEnginePresent;
            switch (r.outcome()) {
                case APPLIED -> {
                    if (e.effect() == Effect.RESTART) {
                        restartLines.add("  " + describe(r) + ": " + r.before() + " -> " + r.after() + " (" + e.why() + ")");
                    } else if (e.effect() == Effect.PACK_REBUILD) {
                        packRebuild = true;
                    }
                }
                case OWNER_KEPT, FAILED -> {
                    if (ceRestartKey) {
                        blockingLines.add("  " + describe(r) + " is " + r.before() + ", we need it changed ("
                                + e.why() + "): " + (r.detail().isEmpty() ? r.outcome().name().toLowerCase(java.util.Locale.ROOT) : r.detail()));
                    }
                }
                case FILE_MISSING -> {
                    if (ceRestartKey && e.target() == ConfigEdit.Target.CRAFTENGINE_CONFIG) {
                        blockingLines.add("  " + e.target().path() + " is missing: cannot check " + e.key());
                    }
                }
                case UNCHANGED -> {
                }
            }
        }
        boolean restart = !restartLines.isEmpty();
        boolean degraded = restart || !blockingLines.isEmpty();
        List<String> banner = new ArrayList<>();
        if (restart) {
            banner.add("==================== TerraFirmaPlugin: RESTART REQUIRED ====================");
            banner.add("Changed settings that only take effect after a restart:");
            banner.addAll(restartLines);
        }
        if (!blockingLines.isEmpty()) {
            banner.add(SETUP_INCOMPLETE);
            banner.add("Required setup is not in effect (fix it, or delete plugins/TerraFirmaPlugin/install/edits.json to let us manage the settings again):");
            banner.addAll(blockingLines);
        }
        if (degraded) {
            banner.add("Until then TerraFirmaPlugin runs in degraded mode: TFC worlds are not generated.");
            banner.add("============================================================================");
        }
        return new InstallStatus(restart, degraded, packRebuild, banner);
    }

    /** True when the banner reports setup the owner must fix, not only a pending restart. */
    public boolean setupIncomplete() {
        return banner.contains(SETUP_INCOMPLETE);
    }

    private static String describe(EditResult r) {
        return r.edit().target().path() + " " + r.edit().key();
    }
}
