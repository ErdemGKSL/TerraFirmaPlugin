# TFC configuration parity (PAR-DATA-06)

Every `define(...)` key of TFC's `ServerConfig`, `CommonConfig`, `ClientConfig` and `animals/*Config` at the pinned commit
(`docs/porting/tfc-pin.txt`), with TFC's default and where it lives here. Status:

- **mapped**: a key in `config.yml` that a feature reads (reload applies it with `/tfp reload config`).
- **pending: consumer item**: no key yet. A key without a reading feature would mislead operators, so each feature item adds
  its key (with this default) together with its consumer; until then the TFC default is in force.
- **pending: animal config**: the per-species animal options, added by the fauna items.
- **client option (NP-CLIENT-04)**: a per-player client option in TFC; here a server setting only where a `hud.*` or
  `item-bars.*` key exists.

`tests`: `ConfigParityDocTest` fails when a TFC key is missing from this table or a mapped key is missing from `config.yml`.

| TFC file | TFC key | TFC default | Our key | Status |
|---|---|---|---|---|
| ClientConfig | `ignoreExperimentalWorldGenWarning` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableDebug` | `!FMLEnvironment.production` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableHungerBar` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableHealthBar` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableThirstBar` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableExperienceBar` | `true` | `hud.enable-experience-bar` | mapped |
| ClientConfig | `enableInkSplatter` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableScreenParticles` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableVanillaTutorialToasts` | `false` | — | client option (NP-CLIENT-04) |
| ClientConfig | `effectHorizontalAdjustment` | `20` | — | client option (NP-CLIENT-04) |
| ClientConfig | `healthDisplayStyle` | `HealthDisplayStyle.TFC` | `hud.health-display-style` | mapped |
| ClientConfig | `foodExpiryTooltipStyle` | `FoodExpiryTooltipStyle.BOTH` | — | client option (NP-CLIENT-04) |
| ClientConfig | `foodExpiryOverlayColor1` | `0xFF88CC33` | — | client option (NP-CLIENT-04) |
| ClientConfig | `heatTooltipStyle` | `ItemTemperatureDisplayStyle.COLOR` | — | client option (NP-CLIENT-04) |
| ClientConfig | `climateTooltipStyle` | `ClimateTemperatureDisplayStyle.CELSIUS` | `hud.climate-temperature-style` | mapped |
| ClientConfig | `timeDeltaTooltipStyle` | `TimeDeltaTooltipStyle.DAYS_MONTHS_YEARS` | — | client option (NP-CLIENT-04) |
| ClientConfig | `disabledExperienceBarStyle` | `DisabledExperienceBarStyle.HOVER` | `hud.disabled-experience-bar-style` | mapped |
| ClientConfig | `rotationDisplayStyle` | `RotationDisplayStyle.REVOLUTIONS_PER_MINUTE` | — | client option (NP-CLIENT-04) |
| ClientConfig | `sendProspectResultsToActionbar` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `showHoeOverlaysOnlyWhenShifting` | `false` | — | client option (NP-CLIENT-04) |
| ClientConfig | `showHoeOverlaysInInfoMods` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `displayFamiliarityAsPercent` | `false` | — | client option (NP-CLIENT-04) |
| ClientConfig | `showGuideBookLinksAlways` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `showGuideBookTabInInventory` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `enableTabsInCreative` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `displayItemContentsAsImages` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `displayItemHeatBars` | `true` | `item-bars.heat` | mapped |
| ClientConfig | `enableWindParticles` | `true` | — | client option (NP-CLIENT-04) |
| ClientConfig | `additionalSpecialModels` | `List.of()` | — | client option (NP-CLIENT-04) |
| CommonConfig | `defaultWorldPreset` | `"tfc:overworld"` | — | pending: consumer item |
| CommonConfig | `defaultMonthLength` | `Calendar.DEFAULT_MONTH_LENGTH` | — | pending: consumer item |
| CommonConfig | `defaultCalendarStartDay` | `(5 * 8)` | — | pending: consumer item |
| CommonConfig | `defaultCalendarDayLength` | `24` | — | pending: consumer item |
| CommonConfig | `enableDatapackTests` | `!FMLEnvironment.production` | — | pending: consumer item |
| ServerConfig | `enableNetherPortals` | `false` | — | pending: consumer item |
| ServerConfig | `enableForcedTFCGameRules` | `true` | — | pending: consumer item |
| ServerConfig | `enableTimeStopWhenServerEmpty` | `true` | — | pending: consumer item |
| ServerConfig | `enableFireArrowSpreading` | `true` | — | pending: consumer item |
| ServerConfig | `fireStarterChance` | `0.5` | — | pending: consumer item |
| ServerConfig | `enableInfestations` | `true` | — | pending: consumer item |
| ServerConfig | `enableLightning` | `true` | — | pending: consumer item |
| ServerConfig | `enableLightningStrippingLogs` | `true` | — | pending: consumer item |
| ServerConfig | `oceanWindScale` | `5000` | — | pending: consumer item |
| ServerConfig | `inferredMetals` | `List.of()` | — | pending: consumer item |
| ServerConfig | `enableFarmlandCreation` | `true` | — | pending: consumer item |
| ServerConfig | `enableGrassPathCreation` | `true` | — | pending: consumer item |
| ServerConfig | `grassSpawningRocksChance` | `0.001` | — | pending: consumer item |
| ServerConfig | `enableRootedDirtToDirtCreation` | `true` | — | pending: consumer item |
| ServerConfig | `enableDirtToMudCreation` | `true` | — | pending: consumer item |
| ServerConfig | `enableSnowSlowEntities` | `true` | — | pending: consumer item |
| ServerConfig | `snowMaxAccumulationOnUpdate` | `64` | — | pending: consumer item |
| ServerConfig | `ticksPerSnowAccumulation` | `80` | — | pending: consumer item |
| ServerConfig | `snowMeltMultiplier` | `3` | — | pending: consumer item |
| ServerConfig | `plantGrowthChance` | `0.04` | — | pending: consumer item |
| ServerConfig | `plantLongGrowthChance` | `0.03` | — | pending: consumer item |
| ServerConfig | `plantSpreadChance` | `0.001` | — | pending: consumer item |
| ServerConfig | `plantsMovementModifier` | `0.2` | — | pending: consumer item |
| ServerConfig | `leavesMovementModifier` | `0.4` | — | pending: consumer item |
| ServerConfig | `enableMossyRockSpreading` | `true` | — | pending: consumer item |
| ServerConfig | `mossyRockSpreadRate` | `20` | — | pending: consumer item |
| ServerConfig | `chestMaximumItemSize` | `Size.LARGE` | — | pending: consumer item |
| ServerConfig | `largeVesselEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `crateEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `quernEnableAutomation` | `true` | `automation.quern` | mapped |
| ServerConfig | `torchTicks` | `86400` | — | pending: consumer item |
| ServerConfig | `candleTicks` | `288000` | — | pending: consumer item |
| ServerConfig | `mudBricksTicks` | `28800` | — | pending: consumer item |
| ServerConfig | `charcoalTicks` | `18000` | — | pending: consumer item |
| ServerConfig | `pitKilnTicks` | `7200` | — | pending: consumer item |
| ServerConfig | `pitKilnTemperature` | `1400` | — | pending: consumer item |
| ServerConfig | `crucibleCapacity` | `4000` | — | pending: consumer item |
| ServerConfig | `cruciblePouringRate` | `4` | — | pending: consumer item |
| ServerConfig | `crucibleFastPouringRate` | `1` | — | pending: consumer item |
| ServerConfig | `crucibleEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `anvilAcceptableWorkRange` | `0` | — | pending: consumer item |
| ServerConfig | `anvilModestlyForgedThreshold` | `10.0` | — | pending: consumer item |
| ServerConfig | `anvilWellForgedThreshold` | `5.0` | — | pending: consumer item |
| ServerConfig | `anvilExpertForgedThreshold` | `2.0` | — | pending: consumer item |
| ServerConfig | `anvilPerfectlyForgedThreshold` | `1.5` | — | pending: consumer item |
| ServerConfig | `anvilMaxEfficiencyMultiplier` | `1.8` | — | pending: consumer item |
| ServerConfig | `anvilMaxDurabilityMultiplier` | `0.5` | — | pending: consumer item |
| ServerConfig | `anvilMaxDamageMultiplier` | `1.5` | — | pending: consumer item |
| ServerConfig | `barrelCapacity` | `10000` | — | pending: consumer item |
| ServerConfig | `barrelEnableAutomation` | `true` | `automation.barrel` | mapped |
| ServerConfig | `barrelEnableRedstoneSeal` | `true` | — | pending: consumer item |
| ServerConfig | `largeVesselEnableRedstoneSeal` | `true` | — | pending: consumer item |
| ServerConfig | `composterTicks` | `288000` | — | pending: consumer item |
| ServerConfig | `composterEnableAutomation` | `true` | `automation.composter` | mapped |
| ServerConfig | `sluiceTicks` | `100` | — | pending: consumer item |
| ServerConfig | `lampCapacity` | `250` | — | pending: consumer item |
| ServerConfig | `enablePumpkinCarving` | `true` | — | pending: consumer item |
| ServerConfig | `jackOLanternTicks` | `108000` | — | pending: consumer item |
| ServerConfig | `bloomeryCapacity` | `16` | — | pending: consumer item |
| ServerConfig | `bloomeryMaxChimneyHeight` | `3` | — | pending: consumer item |
| ServerConfig | `blastFurnaceCapacity` | `4` | — | pending: consumer item |
| ServerConfig | `blastFurnaceFluidCapacity` | `10_000` | — | pending: consumer item |
| ServerConfig | `blastFurnaceFuelConsumptionMultiplier` | `4` | — | pending: consumer item |
| ServerConfig | `blastFurnaceMaxChimneyHeight` | `5` | — | pending: consumer item |
| ServerConfig | `blastFurnaceEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `loomEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `thatchMovementMultiplier` | `0.6` | — | pending: consumer item |
| ServerConfig | `enableThatchBedSpawnSetting` | `true` | — | pending: consumer item |
| ServerConfig | `enableThatchBedSleeping` | `false` | — | pending: consumer item |
| ServerConfig | `thatchBedNoSleepInThunderstorms` | `true` | — | pending: consumer item |
| ServerConfig | `enableLeavesDecaySlowly` | `false` | — | pending: consumer item |
| ServerConfig | `maxPlacedItemSize` | `Size.LARGE` | — | pending: consumer item |
| ServerConfig | `maxPlacedLargeItemSize` | `Size.HUGE` | — | pending: consumer item |
| ServerConfig | `enablePlacingItems` | `true` | — | pending: consumer item |
| ServerConfig | `charcoalForgeEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `firePitEnableAutomation` | `true` | `automation.fire-pit` | mapped |
| ServerConfig | `nestBoxEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `powderKegEnabled` | `true` | — | pending: consumer item |
| ServerConfig | `powderKegOnlyBreaksNaturalBlocks` | `false` | — | pending: consumer item |
| ServerConfig | `powderKegEnableAutomation` | `true` | `automation.powder-keg` | mapped |
| ServerConfig | `powderKegStrengthModifier` | `1d` | — | pending: consumer item |
| ServerConfig | `powderKegFuseTime` | `80` | — | pending: consumer item |
| ServerConfig | `fireboxEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `hotWaterHealAmount` | `0.08` | — | pending: consumer item |
| ServerConfig | `bubbleColumnProvidesAir` | `false` | — | pending: consumer item |
| ServerConfig | `saplingGrowthModifier` | `1d` | — | pending: consumer item |
| ServerConfig | `fruitBranchGrowthTicks` | `5 * ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY` | — | pending: consumer item |
| ServerConfig | `bananaSaplingGrowthTicks` | `6 * ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY` | — | pending: consumer item |
| ServerConfig | `bananaPlantGrowthTicks` | `4 * ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY` | — | pending: consumer item |
| ServerConfig | `fruitPickBloomDelayTicks` | `10 * ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY` | — | pending: consumer item |
| ServerConfig | `berryBushGrowthTicks` | `4 * ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY` | — | pending: consumer item |
| ServerConfig | `cropGrowthModifier` | `1` | — | pending: consumer item |
| ServerConfig | `cropExpiryModifier` | `1` | — | pending: consumer item |
| ServerConfig | `wildCropMaturityStartMonth` | `Month.JUNE` | — | pending: consumer item |
| ServerConfig | `wildCropMaturityEndMonth` | `Month.OCTOBER` | — | pending: consumer item |
| ServerConfig | `dispenserEnableLighting` | `true` | — | pending: consumer item |
| ServerConfig | `powderBowlEnableAutomation` | `true` | — | pending: consumer item |
| ServerConfig | `smallVesselCapacity` | `3000` | — | pending: consumer item |
| ServerConfig | `smallVesselMaximumItemSize` | `Size.SMALL` | — | pending: consumer item |
| ServerConfig | `enableSmallVesselInventoryInteraction` | `true` | — | pending: consumer item |
| ServerConfig | `moldIngotCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldPickaxeHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldPropickHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldAxeHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldShovelHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldHoeHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldChiselHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldHammerHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldSawBladeCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldJavelinHeadCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldSwordBladeCapacity` | `200` | — | pending: consumer item |
| ServerConfig | `moldMaceHeadCapacity` | `200` | — | pending: consumer item |
| ServerConfig | `moldKnifeBladeCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `moldScytheBladeCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `jugCapacity` | `100` | — | pending: consumer item |
| ServerConfig | `jugBreakChance` | `0.02` | — | pending: consumer item |
| ServerConfig | `silicaGlassBottleCapacity` | `500` | — | pending: consumer item |
| ServerConfig | `silicaGlassBottleBreakChance` | `0.005` | — | pending: consumer item |
| ServerConfig | `hematiticGlassBottleCapacity` | `400` | — | pending: consumer item |
| ServerConfig | `hematiticGlassBottleBreakChance` | `0.02` | — | pending: consumer item |
| ServerConfig | `volcanicGlassBottleCapacity` | `400` | — | pending: consumer item |
| ServerConfig | `volcanicGlassBottleBreakChance` | `0.04` | — | pending: consumer item |
| ServerConfig | `olivineGlassBottleCapacity` | `400` | — | pending: consumer item |
| ServerConfig | `olivineGlassBottleBreakChance` | `0.01` | — | pending: consumer item |
| ServerConfig | `woodenBucketCapacity` | `FluidHelpers.BUCKET_VOLUME` | — | pending: consumer item |
| ServerConfig | `woodenBucketCanPlaceSources` | `false` | `buckets.wooden-bucket-can-place-sources` | mapped |
| ServerConfig | `metalBucketCapacity` | `FluidHelpers.BUCKET_VOLUME` | — | pending: consumer item |
| ServerConfig | `metalBucketCanPlaceSources` | `false` | `buckets.metal-bucket-can-place-sources` | mapped |
| ServerConfig | `deviceHeatingModifier` | `1` | — | pending: consumer item |
| ServerConfig | `itemHeatingModifier` | `1` | — | pending: consumer item |
| ServerConfig | `itemCoolingModifier` | `0.8` | — | pending: consumer item |
| ServerConfig | `coolHotItemEntities` | `true` | — | pending: consumer item |
| ServerConfig | `ticksBeforeItemCool` | `10` | — | pending: consumer item |
| ServerConfig | `enableBlockCollapsing` | `true` | — | pending: consumer item |
| ServerConfig | `enableExplosionCollapsing` | `true` | — | pending: consumer item |
| ServerConfig | `enableBlockLandslides` | `true` | — | pending: consumer item |
| ServerConfig | `enableChiselsStartCollapses` | `true` | — | pending: consumer item |
| ServerConfig | `collapseTriggerChance` | `0.1` | — | pending: consumer item |
| ServerConfig | `collapseFakeTriggerChance` | `0.35` | — | pending: consumer item |
| ServerConfig | `collapsePropagateChance` | `0.55` | — | pending: consumer item |
| ServerConfig | `collapseExplosionPropagateChance` | `0.3` | — | pending: consumer item |
| ServerConfig | `collapseMinRadius` | `3` | — | pending: consumer item |
| ServerConfig | `collapseRadiusVariance` | `16` | — | pending: consumer item |
| ServerConfig | `enablePeacefulDifficultyPassiveRegeneration` | `false` | `survival.peaceful-passive-regeneration` | mapped |
| ServerConfig | `passiveExhaustionMultiplier` | `1d` | — | pending: consumer item |
| ServerConfig | `thirstModifier` | `5d` | — | pending: consumer item |
| ServerConfig | `enableThirstOverheating` | `true` | — | pending: consumer item |
| ServerConfig | `thirstGainedFromDrinkingInTheRain` | `5d / 24d` | — | pending: consumer item |
| ServerConfig | `naturalRegenerationModifier` | `1d` | `survival.natural-regeneration-modifier` | mapped |
| ServerConfig | `nutritionRotationHungerWindow` | `80` | — | pending: consumer item |
| ServerConfig | `keepNutritionAfterDeath` | `true` | `survival.keep-nutrition-after-death` | mapped |
| ServerConfig | `foodDecayStackTicks` | `6 * ICalendar.PLAYER_TICKS_IN_DEFAULT_HOUR` | — | pending: consumer item |
| ServerConfig | `foodDecayModifier` | `1d` | — | pending: consumer item |
| ServerConfig | `enableOverburdening` | `true` | — | pending: consumer item |
| ServerConfig | `nutritionMinimumHealthModifier` | `0.2` | — | pending: consumer item |
| ServerConfig | `nutritionDefaultHealthModifier` | `0.85` | — | pending: consumer item |
| ServerConfig | `nutritionMaximumHealthModifier` | `3.0` | — | pending: consumer item |
| ServerConfig | `maxIntoxicationTicks` | `36 * ICalendar.PLAYER_TICKS_IN_DEFAULT_HOUR` | — | pending: consumer item |
| ServerConfig | `traitSaltedModifier` | `0.5` | — | pending: consumer item |
| ServerConfig | `traitBrinedModifier` | `1` | — | pending: consumer item |
| ServerConfig | `traitPickledModifier` | `0.5` | — | pending: consumer item |
| ServerConfig | `traitPreservedModifier` | `0.5` | — | pending: consumer item |
| ServerConfig | `traitVinegarModifier` | `0.1` | — | pending: consumer item |
| ServerConfig | `traitCharcoalGrilledModifier` | `1.25` | — | pending: consumer item |
| ServerConfig | `traitWoodGrilledModifier` | `0.8` | — | pending: consumer item |
| ServerConfig | `traitBurntToACrispModifier` | `2.5` | — | pending: consumer item |
| ServerConfig | `traitWildModifier` | `0.5` | — | pending: consumer item |
| ServerConfig | `traitCannedModifier` | `0` | — | pending: consumer item |
| ServerConfig | `enableVanillaBonemeal` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaSkeletonHorseSpawning` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaMobsSpawningWithEnchantments` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaMobsSpawningWithVanillaEquipment` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaGolems` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaMonsters` | `true` | — | pending: consumer item |
| ServerConfig | `enableVanillaMonstersOnSurface` | `false` | — | pending: consumer item |
| ServerConfig | `enableChickenJockies` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaEggThrowing` | `false` | — | pending: consumer item |
| ServerConfig | `enableVanillaDrinkingMilkClearsPotionEffects` | `false` | — | pending: consumer item |
| ServerConfig | `familiarityDecayLimit` | `0.3` | — | pending: consumer item |
| ServerConfig | `farmlandMakesTheBestRaceTracks` | `false` | — | pending: consumer item |
