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
        demand = dev.tfp.datagen.soil.SoilBlocks.available(tfc) ? dev.tfp.datagen.soil.SoilBlocks.run(tfc, out, demand) : demand; // PAR-WORLD-04: soil block set
        MetalFamilies.run(tfc, out);
        FruitFamilies.run(tfc, out);
        Firepit.run(tfc, out);
        Grill.run(tfc, out);
        Pot.run(tfc, out);
        dev.tfp.datagen.device.DeviceTemplates.run(out); // PAR-INFRA-08: tfp:block/device and tfp:block/device_rendered
        dev.tfp.datagen.rendered.RenderedTemplates.run(out); // PAR-INFRA-17: tfp:block/rendered_cube, tfp:settings/destroy_stages
        dev.tfp.datagen.hitbox.HitboxReport.run(out); // PAR-INFRA-13: hitbox carrier allocation reports (fails on overflow)
        if (dev.tfp.datagen.volcano.VolcanicRockBlocks.available(tfc)) { // PAR-WORLD-15: hardened rock and magma blocks the volcanic features place; fixtures have none
            dev.tfp.datagen.volcano.VolcanicRockBlocks.run(tfc, out);
        }
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
        if (dev.tfp.datagen.aquatic.MobBucketItems.available(tfc)) { // PAR-FAUNA-25: the 20 mob buckets; before the coverage gate
            dev.tfp.datagen.aquatic.MobBucketItems.run(tfc, out);
        }
        if (dev.tfp.datagen.basket.BasketItem.available(tfc)) { // PAR-PROC-22: tfp:basket (TFC bundle item); before the coverage gate
            dev.tfp.datagen.basket.BasketItem.run(tfc, out);
        }
        if (dev.tfp.datagen.fire.FireStarterItems.available(tfc)) { // PAR-HEAT-05: tfp:firestarter, tfp:flint_and_pyrite; before the coverage gate
            dev.tfp.datagen.fire.FireStarterItems.run(tfc, out);
        }
        if (dev.tfp.datagen.pottery.PotteryItems.available(tfc)) { // PAR-POT-02: ceramic/* items; before the coverage gate
            dev.tfp.datagen.pottery.PotteryItems.run(tfc, out);
            dev.tfp.datagen.potteryheat.PotteryHeat.run(tfc, out); // PAR-POT-11: item_heat 1.2 for fired vessels and molds
        }
        if (dev.tfp.datagen.glassbottle.GlassBottleItems.available(tfc)) { // PAR-POT-12: the four glass bottles and the jug fluid overlay; after PotteryItems, before the coverage gate
            dev.tfp.datagen.glassbottle.GlassBottleItems.run(tfc, out);
        }
        if (dev.tfp.datagen.tool.RockTools.available(tfc)) { // PAR-TOOL-01: rock tool heads and tools; before the coverage gate
            dev.tfp.datagen.tool.RockTools.run(tfc, out);
        }
        if (dev.tfp.datagen.tool.ObsidianTools.available(tfc)) { // PAR-TOOL-11: obsidian tool heads and tools; before the coverage gate
            dev.tfp.datagen.tool.ObsidianTools.run(tfc, out);
        }
        if (dev.tfp.datagen.weapon.Macuahuitl.available(tfc)) { // PAR-TOOL-04: tfp:obsidian_macuahuitl; before the coverage gate
            dev.tfp.datagen.weapon.Macuahuitl.run(tfc, out);
        }
        if (dev.tfp.datagen.tool.MetalJavelins.available(tfc)) { // PAR-TOOL-05: metal javelins; before the coverage gate
            dev.tfp.datagen.tool.MetalJavelins.run(tfc, out);
        }
        if (dev.tfp.datagen.toolitems.MetalToolItems.available(tfc)) { // PAR-TOOL-06: metal chisel, propick, tuyere, shears; before the coverage gate
            dev.tfp.datagen.toolitems.MetalToolItems.run(tfc, out);
        }
        if (dev.tfp.datagen.tool.GlowArrowItem.available(tfc)) { // PAR-TOOL-10: tfp:glow_arrow; before the coverage gate
            dev.tfp.datagen.tool.GlowArrowItem.run(tfc, out);
        }
        if (dev.tfp.datagen.shield.ShieldItems.available(tfc)) { // PAR-TOOL-07: metal shields; before the coverage gate
            dev.tfp.datagen.shield.ShieldItems.run(tfc, out);
        }
        if (dev.tfp.datagen.fishing.FishingRodItems.available(tfc)) { // PAR-FAUNA-35: the nine metal fishing rods; before the coverage gate
            dev.tfp.datagen.fishing.FishingRodItems.run(tfc, out);
        }
        if (dev.tfp.datagen.pan.PanItems.available(tfc)) { // PAR-PROC-12: pan items, models and deposit loot tables; before the coverage gate; fixtures have no pan
            dev.tfp.datagen.pan.PanItems.run(tfc, out);
        }
        if (dev.tfp.datagen.hunt.HideItems.available(tfc)) { // PAR-FAUNA-17: small/medium/large raw, soaked, scraped, prepared, sheepskin hides; before the coverage gate
            dev.tfp.datagen.hunt.HideItems.run(tfc, out);
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
        if (dev.tfp.datagen.placed.PlacedItemBlock.available(tfc)) { // PAR-POT-13: tfp:placed_item block entry; fixtures have no PlacedItemBlock
            dev.tfp.datagen.placed.PlacedItemBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.quern.QuernBlock.available(tfc)) { // PAR-PROC-04: tfp:quern block entry, models, textures, names; fixtures have no quern
            dev.tfp.datagen.quern.QuernBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.crucible.CrucibleBlock.available(tfc)) { // PAR-METAL-12: tfp:crucible block entry, models, textures, names; fixtures have no crucible
            dev.tfp.datagen.crucible.CrucibleBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.anvil.AnvilBlocks.available(tfc)) { // PAR-METAL-03: tfp:metal/anvil/<metal> and tfp:rock/anvil/<rock> entries, models, textures, names; fixtures have no anvils
            dev.tfp.datagen.anvil.AnvilBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.barrel.BarrelBlock.available(tfc)) { // PAR-PROC-32: tfp:wood/barrel/<wood> entries, models, textures, names; fixtures have no barrels
            dev.tfp.datagen.barrel.BarrelBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.loom.LoomBlock.available(tfc)) { // PAR-PROC-06: tfp:wood/loom/<wood> entries, models, textures, names; fixtures have no looms
            dev.tfp.datagen.loom.LoomBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.tables.TableBlocks.available(tfc)) { // PAR-PROC-49: tfp:wood/scribing_table/<wood> and tfp:wood/sewing_table/<wood>; fixtures have none
            dev.tfp.datagen.tables.TableBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.sluice.SluiceBlock.available(tfc)) { // PAR-PROC-13: tfp:wood/sluice/<wood> entries, models, textures, names; fixtures have no sluices
            dev.tfp.datagen.sluice.SluiceBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.barrelrack.BarrelRackBlock.available(tfc)) { // PAR-PROC-38: tfp:barrel_rack entry, models, texture, names; fixtures have no rack
            dev.tfp.datagen.barrelrack.BarrelRackBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.compost.ComposterBlock.available(tfc)) { // PAR-PROC-34: tfp:composter block entry, models, textures, names; fixtures have no composter
            dev.tfp.datagen.compost.ComposterBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.charcoal.CharcoalBlocks.available(tfc)) { // PAR-PROC-37: tfp:log_pile, tfp:burning_log_pile, tfp:charcoal_pile entries, models, textures; fixtures have no log pile
            dev.tfp.datagen.charcoal.CharcoalBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.kiln.PitKilnBlock.available(tfc)) { // PAR-POT-14: tfp:pit_kiln entry (17 stages), models, textures; fixtures have no pit kiln
            dev.tfp.datagen.kiln.PitKilnBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.wood.WoodPlantBlocks.available(tfc)) { // PAR-WORLD-44: tfp:wood/{sapling,potted_sapling,fallen_leaves,twig}/<wood> entries, models, baked textures, loot; fixtures have none
            dev.tfp.datagen.wood.WoodPlantBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.weather.WeatherBlocks.available(tfc)) { // PAR-CLIM-04: tfp:snow_pile, ice_pile, sea_ice, icicle entries, models, texture, loot; fixtures have no weather blocks
            dev.tfp.datagen.weather.WeatherBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.crop.CropBlocks.available(tfc)) { // PAR-FARM-03: tfp:crop/*, dead_crop/*, wild_crop/* entries on the hitbox carriers, models, textures, seeds, loot, names; fixtures have no crops
            dev.tfp.datagen.crop.CropBlocks.run(tfc, out);
            dev.tfp.datagen.crop.FruitBlocks.run(tfc, out); // PAR-FARM-23: tfp:fruit/pumpkin, melon and their rotten forms (solid pool)
        }
        if (dev.tfp.datagen.berry.BerryBlocks.available(tfc)) { // PAR-FARM-14: tfp:berry_bush/<species> and /dead entries on the hitbox carriers, models, textures, loot, names; fixtures have no bushes
            dev.tfp.datagen.berry.BerryBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.instrument.InstrumentBlocks.available(tfc)) { // PAR-CLIM-27: tfp:thermometer, anemometer, vane, calendar_clock entries on the hitbox carriers, converted entity models, loot, names; fixtures have none
            dev.tfp.datagen.instrument.InstrumentBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.fruittree.FruitTreeBlocks.available(tfc)) { // PAR-FARM-16/17: tfp:plant/<tree>_* fruit tree blocks and the banana, models, loot, names
            dev.tfp.datagen.fruittree.FruitTreeBlocks.run(tfc, out);
            dev.tfp.datagen.banana.BananaBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.berry.BerryBlocks.available(tfc)) { // PAR-FARM-15: tfp:spreading_bush/* entries (bushes, canes, cranberry, dead), models, loot, names
            dev.tfp.datagen.berry.SpreadingBushBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.misc.MiscCubes.available(tfc)) { // PAR-DECO-12: tfp:wood/crate/<wood> and tfp:wood/planks/palm_mosaic renderer cubes; fixtures have none
            dev.tfp.datagen.misc.MiscCubes.run(tfc, out);
        }
        if (dev.tfp.datagen.sign.SignBlocks.available(tfc)) { // PAR-DECO-03: tfp:wood/(wall_)sign and (wall_)hanging_sign blocks and items as item displays; fixtures have none
            dev.tfp.datagen.sign.SignBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.chest.ChestBlocks.available(tfc)) { // PAR-DECO-04: tfp:wood/chest and trapped_chest device blocks drawn as item displays; fixtures have none
            dev.tfp.datagen.chest.ChestBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.plant.GrowingPlantBlocks.available(tfc)) { // PAR-FARM-25: tfp:plant/* growing shapes (columns, kelp, vines, creepers, epiphytes) as item displays on the hitbox carriers
            dev.tfp.datagen.plant.GrowingPlantBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.farm.FarmlandBlocks.available(tfc)) { // PAR-FARM-26: tfp:farmland/<soil> entries on the farmland carriers, TFC's models, loot, names; fixtures have none
            dev.tfp.datagen.farm.FarmlandBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.groundcover.GroundcoverCeBlocks.available(tfc)) { // PAR-WORLD-31: loose rocks, small ores and groundcover on hitbox carriers, models, loot, names; fixtures have none
            dev.tfp.datagen.groundcover.GroundcoverCeBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.light.TorchBlocks.available(tfc)) { // PAR-HEAT-21: tfp:torch, wall, dead torch entries on the selector's carriers, models, texture, loot, names; fixtures have no torches
            dev.tfp.datagen.light.TorchBlocks.run(tfc, out, dev.tfp.datagen.light.TorchBlocks::selected);
        }
        if (dev.tfp.datagen.light.candle.CandleBlocks.available(tfc)) { // PAR-HEAT-26: tfp:candle*, candle_cake*, jack_o_lantern on their vanilla twins, item models, loot, names; fixtures have no candles
            dev.tfp.datagen.light.candle.CandleBlocks.run(tfc, out, dev.tfp.datagen.light.candle.CandleBlocks::selected);
        }
        if (dev.tfp.datagen.coral.CoralBlockGen.available(tfc)) { // PAR-WORLD-50: tfp:coral/*, tfp:sea_pickle on their vanilla twins
            dev.tfp.datagen.coral.CoralBlockGen.run(tfc, out);
        }
        if (dev.tfp.datagen.powderkeg.PowderkegScreenArt.available(tfc)) { // PAR-PROC-18: powderkeg screen art; fixtures have no powderkeg screen
            dev.tfp.datagen.powderkeg.PowderkegScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.powderkeg.PowderkegBlock.available(tfc)) { // PAR-PROC-39: tfp:powderkeg entry on the selector's carriers, models, textures, names; fixtures have no powderkeg
            dev.tfp.datagen.powderkeg.PowderkegBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.nestbox.NestBoxScreenArt.available(tfc)) { // PAR-FAUNA-07: nest box screen art; fixtures have no nest box screen
            dev.tfp.datagen.nestbox.NestBoxScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.vesselgui.VesselScreenArt.available(tfc)) { // PAR-POT-10: small vessel screen art; fixtures have no vessel screen
            dev.tfp.datagen.vesselgui.VesselScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.nestbox.NestBoxBlock.available(tfc)) { // PAR-FAUNA-27: tfp:nest_box entry on the selector's carrier, model, texture, names; fixtures have no nest box
            dev.tfp.datagen.nestbox.NestBoxBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.lobstertrap.LobsterTrapBlock.available(tfc)) { // PAR-FAUNA-36: tfp:lobster_trap on the selector's carrier, models, texture, names; fixtures have no trap
            dev.tfp.datagen.lobstertrap.LobsterTrapBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.firebox.FireboxScreenArt.available(tfc)) { // PAR-HEAT-12: firebox screen art; fixtures have no firebox screen
            dev.tfp.datagen.firebox.FireboxScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.salad.SaladArt.available(tfc)) { // PAR-PROC-36: salad screen art; fixtures have no salad screen
            dev.tfp.datagen.salad.SaladArt.run(tfc, out);
        }
        if (dev.tfp.datagen.firepitgui.FirepitScreenArt.available(tfc)) { // PAR-HEAT-20: firepit screen art and thermometer; fixtures have no firepit screen
            dev.tfp.datagen.firepitgui.FirepitScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.bloomery.BloomeryBlocks.available(tfc)) { // PAR-METAL-14: tfp:bloomery, tfp:bloom and tfp:molten blocks, models, names; fixtures have none
            dev.tfp.datagen.bloomery.BloomeryBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.blastfurnace.BlastFurnaceBlock.available(tfc)) { // PAR-METAL-15: tfp:blast_furnace block, models, names; fixtures have none
            dev.tfp.datagen.blastfurnace.BlastFurnaceBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.channel.ChannelBlocks.available(tfc)) { // PAR-METAL-13: tfp:channel and tfp:mold_table blocks, models, names; fixtures have none
            dev.tfp.datagen.channel.ChannelBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.pile.PileBlocks.available(tfc)) { // PAR-METAL-16: tfp:ingot_pile and tfp:double_ingot_pile blocks, display models, metal textures; fixtures have none
            dev.tfp.datagen.pile.PileBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.bell.BellBlocks.available(tfc)) { // PAR-METAL-18: tfp:bronze_bell and tfp:brass_bell blocks, models, names; fixtures have none
            dev.tfp.datagen.bell.BellBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.bowl.BowlBlocks.available(tfc)) { // PAR-POT-09: tfp:wooden_bowl and tfp:ceramic/bowl blocks, models, powder layers, names; fixtures have none
            dev.tfp.datagen.bowl.BowlBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.crucible.CrucibleScreenArt.available(tfc)) { // PAR-METAL-12: crucible screen art, thermometer and fill; fixtures have no crucible screen
            dev.tfp.datagen.crucible.CrucibleScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.blastfurnace.BlastFurnaceScreenArt.available(tfc)) { // PAR-METAL-27: blast furnace screen art, meters, thermometer and fill; fixtures have none
            dev.tfp.datagen.blastfurnace.BlastFurnaceScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.anvil.AnvilPlanArt.available(tfc)) { // PAR-METAL-04: anvil plan screen art, button frames and arrows; fixtures have no plan screen
            dev.tfp.datagen.anvil.AnvilPlanArt.run(tfc, out);
        }
        if (dev.tfp.datagen.charcoalforge.CharcoalForgeScreenArt.available(tfc)) { // PAR-METAL-09: charcoal forge screen art and thermometer; fixtures have no forge screen
            dev.tfp.datagen.charcoalforge.CharcoalForgeScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.anvil.AnvilForgeArt.available(tfc)) { // PAR-METAL-05: anvil forging screen art, markers, rule and step overlays; fixtures have none
            dev.tfp.datagen.anvil.AnvilForgeArt.run(tfc, out);
        }
        if (dev.tfp.datagen.grillgui.GrillScreenArt.available(tfc)) { // PAR-HEAT-24: grill screen art; fixtures have no grill screen
            dev.tfp.datagen.grillgui.GrillScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.potgui.PotScreenArt.available(tfc)) { // PAR-HEAT-25: pot screen art; fixtures have no pot screen
            dev.tfp.datagen.potgui.PotScreenArt.run(tfc, out);
        }
        if (dev.tfp.datagen.plant.PlantBlockGen.available(tfc)) { // PAR-WORLD-12: tfp:plant/* land and floating plants on the plant pools, models, baked tints, items, loot, names; fixtures have no plants
            dev.tfp.datagen.plant.PlantBlockGen.run(tfc, out);
        }
        if (dev.tfp.datagen.plant.FlowerCutting.available(tfc)) { // PAR-FARM-21: tfp:flower_cutting (the item shears on a flower drop); fixtures have no TFCItems
            dev.tfp.datagen.plant.FlowerCutting.run(tfc, out);
        }
        if (dev.tfp.datagen.plant.shape.PlantShapeGen.available(tfc)) { // PAR-WORLD-47: the other plant shapes (water, creeping, epiphytes, columns, cacti, kelp, bamboo) on the kelp/weeping pools and hitbox-only carriers
            dev.tfp.datagen.plant.shape.PlantShapeGen.run(tfc, out);
        }
        if (dev.tfp.datagen.rotation.RotationBlocks.available(tfc)) { // PAR-MECH-01: axles, encased axles, clutches, gearboxes, creative motor; fixtures have no rotation blocks
            dev.tfp.datagen.rotation.RotationBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.pump.PumpBlocks.available(tfc)) { // PAR-MECH-09: steel pipe and steel pump; fixtures have no pump blocks
            dev.tfp.datagen.pump.PumpBlocks.run(tfc, out);
        }
        if (dev.tfp.datagen.crankshaft.CrankshaftBlock.available(tfc)) { // PAR-MECH-21: tfp:crankshaft; fixtures have no crankshaft
            dev.tfp.datagen.crankshaft.CrankshaftBlock.run(tfc, out);
        }
        if (dev.tfp.datagen.coverage.BlockCoverage.available(tfc)) { // PAR-INFRA-49: TFC blockstate coverage gate; after every block generator and ItemCoverage; fixtures have no TFCItems
            dev.tfp.datagen.coverage.BlockCoverage.run(tfc, out);
        }
        dev.tfp.datagen.animal.AnimalSounds.run(tfc, out); // PAR-FAUNA-20: TFC's entity sound events and oggs as tfp_animals
        dev.tfp.datagen.spawn.SpawnTable.run(tfc, out); // PAR-FAUNA-04: biome spawners and fauna gates for the spawn pass
        dev.tfp.datagen.plantslow.PlantSlowFactors.run(tfc, out); // PAR-GATHER-10: Plant.speedFactor per plant for the slow-block provider
        dev.tfp.datagen.plant.stage.PlantStageGen.run(tfc, out); // PAR-WORLD-48: plant bloom fields and tfc:plant stage models
        if (dev.tfp.datagen.flammability.FlammabilityPass.available(tfc)) { // PAR-INFRA-47: CE burn settings on every block whose TFC counterpart is flammable; after every block generator
            dev.tfp.datagen.flammability.FlammabilityPass.run(tfc, out);
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
        dev.tfp.datagen.fog.FogVariants.run(tfc, out); // PAR-CLIM-30: foggy biome variants (datapack tfp_fog, not registered until the client spike)
        VanillaLootOverrides.run(tfc, out); // PAR-INFRA-30: TFC's replacements of vanilla loot tables, in the bootstrap datapack
        dev.tfp.datagen.damagetypes.DamageTypes.run(tfc, out); // PAR-SURV-10: TFC's damage types and no_knockback tag, in the bootstrap datapack
        CommonTags.run(out); // PAR-INFRA-14: NeoForge c: tags (skipped without -Dtfp.neoforgeJar, e.g. fixtures)
        dev.tfp.datagen.toolblocktags.ToolBlockTags.run(out); // PAR-PROC-24: tfp:mineable/* as server tags; after CommonTags, before CraftingRecipes
        if (dev.tfp.datagen.crafting.CraftingRecipes.available(tfc)) { // PAR-PROC-02: plain workbench recipes as CE YAML; after every CE item and CommonTags
            dev.tfp.datagen.crafting.CraftingRecipes.run(tfc, out);
        }
        dev.tfp.datagen.heat.HeatGlowModels.run(out); // PAR-HEAT-01: heat glow item model definitions; after every CE item and CommonTags
        Advancements.writeDatapack(tfc, out); // PAR-DATA-02: datapack advancements (bootstrap datapack) + CE lang; after every CE item (icons)
        if (dev.tfp.datagen.sound.TfcSounds.available(tfc)) { // PAR-DATA-08: TFC sound events and oggs under tfp; fixtures have no sounds.json
            dev.tfp.datagen.sound.TfcSounds.run(tfc, out);
        }
        out.put("third-party.json", dev.tfp.datagen.sound.TfcSounds.thirdParty(out)); // the CC-BY-4.0 slide ogg (checked by :checkNotice)
        report.ceStates(Report.countCeStates(out.files())); // PAR-PROC-37: the installer sizes serverside-blocks from the states
        out.put("reports/datagen-summary.json", Json.write(report.toJson()));
        out.put(Manifest.FILE, Manifest.build(tfc.head(), tfc.inputs(), out.files()));
        return new Result(out, report);
    }
}
