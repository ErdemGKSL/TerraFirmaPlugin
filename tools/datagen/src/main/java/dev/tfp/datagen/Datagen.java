/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import dev.tfp.datagen.visuals.VisualDemand;
import dev.tfp.datagen.visuals.VisualOutputs;
import java.nio.file.Path;
import java.util.List;

/**
 * Datagen CLI: {@code datagen <tfc-checkout> <content-dir> [--check]}.
 *
 * <p>Reads the TFC checkout (which must be at {@link TfcCheckout#PINNED_COMMIT}) and produces the datagen-owned
 * part of {@code <content-dir>} ({@link OutputTree#OWNED}). In the repository the content dir is
 * {@code content/generated/content}, shipped as {@code /content} in the plugin jar. With {@code --check} nothing
 * is written; the run fails if the directory differs from a fresh generation (CI gate).
 */
public final class Datagen {
    private Datagen() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--check"))) {
            System.err.println("usage: datagen <tfc-checkout> <content-dir> [--check]");
            System.exit(2);
        }
        try {
            Path content = Path.of(args[1]).toAbsolutePath().normalize();
            Result r = generate(Path.of(args[0]).toAbsolutePath().normalize(), TfcCheckout.PINNED_COMMIT);
            if (args.length == 3) {
                List<String> diff = r.tree().diff(content);
                if (!diff.isEmpty()) {
                    System.err.println(content + " is out of date (" + diff.size() + " files). Run "
                        + "`./gradlew :tools:datagen:generateContent`. First differences:");
                    diff.stream().limit(20).forEach(d -> System.err.println("  " + d));
                    System.exit(1);
                }
                System.out.println(content + " is up to date (" + r.tree().files().size() + " files).");
            } else {
                r.tree().writeTo(content);
                System.out.println("wrote " + r.tree().files().size() + " files to " + content + ": "
                    + r.report().emittedCount() + " CE blocks, " + r.report().skippedCount() + " family blocks skipped"
                    + " (see reports/datagen-summary.json)");
            }
        } catch (DatagenException e) {
            System.err.println("datagen: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Model id → JSON text in the pack (or the id itself for a model we do not ship, e.g. vanilla). */
    static String modelContent(OutputTree out, String model) {
        if (model.startsWith(Json.TO_NS + ":")) {
            byte[] b = out.get(AssetPipeline.RP + "models/" + model.substring(Json.TO_NS.length() + 1) + ".json");
            if (b != null) {
                return new String(b, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return model;
    }

    public record Result(OutputTree tree, Report report) {
    }

    /** Runs every step in memory. Deterministic: same checkout + same generator → same bytes. */
    public static Result generate(Path checkout, String pinnedCommit) {
        TfcCheckout tfc = TfcCheckout.open(checkout, pinnedCommit);
        OutputTree out = new OutputTree();
        Report report = new Report();
        report.data(DataExport.run(tfc, out));
        List<VisualDemand> demand = AssetPipeline.run(tfc, out, report);
        if (!report.missing().isEmpty()) {
            throw new DatagenException("unresolved model/texture references: " + report.missing());
        }
        VisualOutputs.run(out, demand, model -> modelContent(out, model));
        BetterHudPack.write(out);
        out.put("third-party.json", "[]\n"); // no non-EUPL file is copied yet (checked by :checkNotice)
        out.put("reports/datagen-summary.json", Json.write(report.toJson()));
        out.put(Manifest.FILE, Manifest.build(tfc.head(), tfc.inputs(), out.files()));
        return new Result(out, report);
    }
}
