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
        WoodFamilies.run(tfc, out);
        RockFamilies.run(tfc, out);
        MudFamilies.run(tfc, out);
        MetalFamilies.run(tfc, out);
        FruitFamilies.run(tfc, out);
        Firepit.run(tfc, out);
        Grill.run(tfc, out);
        Pot.run(tfc, out);
        dev.tfp.datagen.device.DeviceTemplates.run(out); // PAR-INFRA-08: tfp:block/device and tfp:block/device_rendered
        dev.tfp.datagen.rendered.RenderedTemplates.run(out); // PAR-INFRA-17: tfp:block/rendered_cube, tfp:settings/destroy_stages
        dev.tfp.datagen.hitbox.HitboxReport.run(out); // PAR-INFRA-13: hitbox carrier allocation reports (fails on overflow)
        if (tfc.exists(ForgeDevices.FORGE_SOURCE)) { // the small fixture checkouts of the tests have no forge; ForgeDevicesTest pins the real one
            ForgeDevices.run(tfc, out);
        }
        if (dev.tfp.datagen.gui.GuiArt.available(tfc)) { // PAR-INFRA-03: GUI images; the small fixture checkouts have no GUI textures
            dev.tfp.datagen.gui.GuiArt.run(tfc, out);
        }
        if (dev.tfp.datagen.knap.KnappingArt.available(tfc)) { // PAR-PROC-01: knapping screen art; fixtures have none
            dev.tfp.datagen.knap.KnappingArt.run(tfc, out);
        }
        if (dev.tfp.datagen.barrelrack.BarrelScreenArt.available(tfc)) { // PAR-PROC-09: barrel screen art and tank item models; fixtures have none
            dev.tfp.datagen.barrelrack.BarrelScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.tabs.PlayerTabArt.available(tfc)) { // PAR-SURV-07: player tab pages, tab column, line fonts; fixtures have none
            dev.tfp.datagen.tabs.PlayerTabArt.run(tfc, out);
        }
        if (dev.tfp.datagen.food.FoodItems.available(tfc)) { // PAR-SURV-01: every TFC food item; fixtures have no food sources
            dev.tfp.datagen.food.FoodItems.run(tfc, out);
        }
        if (dev.tfp.datagen.bucket.BucketItems.available(tfc)) { // PAR-FLUID-02: wooden, red and blue steel buckets, TFC fluid buckets; before the coverage gate
            dev.tfp.datagen.bucket.BucketItems.run(tfc, out);
        }
        if (dev.tfp.datagen.basket.BasketItem.available(tfc)) { // PAR-PROC-22: tfp:basket (TFC bundle item); before the coverage gate
            dev.tfp.datagen.basket.BasketItem.run(tfc, out);
        }
        if (dev.tfp.datagen.fire.FireStarterItems.available(tfc)) { // PAR-HEAT-05: tfp:firestarter, tfp:flint_and_pyrite; before the coverage gate
            dev.tfp.datagen.fire.FireStarterItems.run(tfc, out);
        }
        if (dev.tfp.datagen.material.MaterialItems.available(tfc)) { // PAR-PROC-19: plain TFC material items + TFCItems coverage gate; fixtures have no TFCItems
            dev.tfp.datagen.material.MaterialItems.run(tfc, out);
            dev.tfp.datagen.material.ItemCoverage.run(tfc, out);
        }
        if (dev.tfp.datagen.scraping.ScrapingArt.available(tfc)) { // PAR-PROC-05: scraping surface tiles and item model definitions; fixtures have no scraping recipes
            dev.tfp.datagen.scraping.ScrapingArt.run(tfc, out);
        }
        if (dev.tfp.datagen.scraping.ScrapingBlock.available(tfc)) { // PAR-PROC-29: tfp:scraping block entry; fixtures have no ScrapingBlock
            dev.tfp.datagen.scraping.ScrapingBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.quern.QuernBlock.available(tfc)) { // PAR-PROC-04: tfp:quern block entry, models, textures, names; fixtures have no quern
            dev.tfp.datagen.quern.QuernBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.barrel.BarrelBlock.available(tfc)) { // PAR-PROC-32: tfp:wood/barrel/<wood> entries, models, textures, names; fixtures have no barrels
            dev.tfp.datagen.barrel.BarrelBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.compost.ComposterBlock.available(tfc)) { // PAR-PROC-34: tfp:composter block entry, models, textures, names; fixtures have no composter
            dev.tfp.datagen.compost.ComposterBlock.run(tfc, out);
        }
        LootTableExport.run(tfc, out); // PAR-INFRA-21: TFC loot tables of delegated blocks and spawned entities for the Paper runtime
        Advancements.run(tfc, out);
        FieldGuide.run(tfc, out);
        Locales.run(tfc, out);
        if (!report.missing().isEmpty()) {
            throw new DatagenException("unresolved model/texture references: " + report.missing());
        }
        VisualOutputs.run(out, demand, model -> modelContent(out, model));
        dev.tfp.datagen.oreoverlay.OreOverlays.run(tfc, out, demand); // PAR-INFRA-15: ore overlay items, composite icons, runtime data
        BetterHudPack.write(out);
        HudLayouts.run(out, HudLayouts.catalogue());
        SeasonalBiomes.write(tfc, out);
        VanillaLootOverrides.run(tfc, out); // PAR-INFRA-30: TFC's replacements of vanilla loot tables, in the bootstrap datapack
        dev.tfp.datagen.damagetypes.DamageTypes.run(tfc, out); // PAR-SURV-10: TFC's damage types and no_knockback tag, in the bootstrap datapack
        CommonTags.run(out); // PAR-INFRA-14: NeoForge c: tags (skipped without -Dtfp.neoforgeJar, e.g. fixtures)
        dev.tfp.datagen.toolblocktags.ToolBlockTags.run(out); // PAR-PROC-24: tfp:mineable/* as server tags; after CommonTags, before CraftingRecipes
        if (dev.tfp.datagen.crafting.CraftingRecipes.available(tfc)) { // PAR-PROC-02: plain workbench recipes as CE YAML; after every CE item and CommonTags
            dev.tfp.datagen.crafting.CraftingRecipes.run(tfc, out);
        }
        dev.tfp.datagen.heat.HeatGlowModels.run(out); // PAR-HEAT-01: heat glow item model definitions; after every CE item and CommonTags
        out.put("third-party.json", "[]\n"); // no non-EUPL file is copied yet (checked by :checkNotice)
        out.put("reports/datagen-summary.json", Json.write(report.toJson()));
        out.put(Manifest.FILE, Manifest.build(tfc.head(), tfc.inputs(), out.files()));
        return new Result(out, report);
    }
}
