/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Project-wide rules from ARCHITECTURE.md and the paper-folia-threading skill. */
class ArchitectureTest {
    static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.tfp");
    }

    @Test
    void noBukkitScheduler() {
        noClasses().that().resideInAPackage("dev.tfp..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitRunnable")
                .orShould().callMethod(Bukkit.class, "getScheduler")
                .because("Folia has no BukkitScheduler; use dev.tfp.paper.scheduler.Scheduler")
                .check(classes);
    }

    @Test
    void noFoliaForbiddenApis() {
        noClasses().that().resideInAPackage("dev.tfp..")
                .should().callMethod(Bukkit.class, "createWorld", org.bukkit.WorldCreator.class)
                .orShould().callMethod(Bukkit.class, "unloadWorld", org.bukkit.World.class, boolean.class)
                .orShould().callMethod(Bukkit.class, "getScoreboardManager")
                .because("not available on Folia (paper-folia-threading skill)")
                .check(classes);
    }

    @Test
    void corePackagesArePlatformFree() {
        noClasses().that().resideInAnyPackage("dev.tfp.core..", "dev.tfp.worldgen..")
                .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..", "io.papermc..",
                        "net.momirealms..", "kr.toxicity..", "com.github.retrooper..")
                .check(classes);
    }

    /** CraftEngine (net.momirealms), BetterModel (kr.toxicity.model) and BetterHud (kr.toxicity.hud) APIs. */
    private static final String[] CLIENT_PLUGIN_APIS = {"net.momirealms..", "kr.toxicity.."};

    /**
     * M1-INT-01: feature code (core, worldgen, paper incl. paper.features) reaches CraftEngine/BetterModel/
     * BetterHud only through the dev.tfp.paper.integration interfaces; only dev.tfp.integration.* imports them.
     */
    @Test
    void onlyIntegrationModulesTouchClientPluginApis() {
        noClasses().that().resideInAPackage("dev.tfp..").and().resideOutsideOfPackage("dev.tfp.integration..")
                .should().dependOnClassesThat().resideInAnyPackage(CLIENT_PLUGIN_APIS)
                .because("only integration/* may import CraftEngine/BetterModel/BetterHud (ARCHITECTURE.md §3)")
                .check(classes);
    }

    /** Feature code sees the interfaces, never an adapter class (adapters are found by ServiceLoader). */
    @Test
    void featureCodeDoesNotDependOnAdapters() {
        noClasses().that().resideInAnyPackage("dev.tfp.core..", "dev.tfp.worldgen..", "dev.tfp.paper..", "dev.tfp.api..")
                .should().dependOnClassesThat().resideInAPackage("dev.tfp.integration..")
                .because("feature code compiles against dev.tfp.paper.integration interfaces only (M1-INT-01)")
                .check(classes);
    }

    /** Each adapter imports only its own plugin's API. */
    @Test
    void adaptersImportOnlyTheirOwnPlugin() {
        noClasses().that().resideInAPackage("dev.tfp.integration.craftengine..")
                .should().dependOnClassesThat().resideInAnyPackage("kr.toxicity..")
                .check(classes);
        noClasses().that().resideInAPackage("dev.tfp.integration.bettermodel..")
                .should().dependOnClassesThat().resideInAnyPackage("net.momirealms..", "kr.toxicity.hud..")
                .check(classes);
        noClasses().that().resideInAPackage("dev.tfp.integration.betterhud..")
                .should().dependOnClassesThat().resideInAnyPackage("net.momirealms..", "kr.toxicity.model..")
                .check(classes);
    }

    /** The rules above must actually see the adapters (guards against an empty import). */
    @Test
    void adapterClassesAreImported() {
        for (String pkg : new String[] {"dev.tfp.integration.craftengine", "dev.tfp.integration.bettermodel",
            "dev.tfp.integration.betterhud", "dev.tfp.paper.integration"}) {
            org.assertj.core.api.Assertions.assertThat(classes.stream().anyMatch(c -> c.getPackageName().equals(pkg)))
                    .as(pkg).isTrue();
        }
    }
}
