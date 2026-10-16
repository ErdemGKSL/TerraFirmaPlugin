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
}
