package dev.tfp.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Every Java source starts with the EUPL header. Files ported from TerraFirmaCraft also name the
 * TFC file and the pinned commit (see the tfc-feature-porting skill):
 *
 * ```
 * /*
 *  * Licensed under the EUPL, Version 1.2.
 *  * You may obtain a copy of the Licence at:
 *  * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *  *
 *  * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 *  * file: src/main/java/net/dries007/tfc/<path>.java @ <short-commit>
 *  */
 * ```
 */
@CacheableTask
abstract class CheckLicenseHeaders : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Full 40-hex TFC commit pinned in docs/porting/ledger.md. */
    @get:Input
    abstract val pinnedTfcCommit: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val problems = mutableListOf<String>()
        val pinned = pinnedTfcCommit.get()
        for (f in sources.files.filter { it.isFile && it.name.endsWith(".java") }.sortedBy { it.path }) {
            val head = f.useLines { it.take(12).toList() }
            if (head.size < 4 || head[0] != "/*" || head[1] != HEADER[1] || head[2] != HEADER[2] || head[3] != HEADER[3]) {
                problems += "${f.path}: missing EUPL header"
                continue
            }
            val text = head.joinToString("\n")
            if (text.contains("Ported from TerraFirmaCraft")) {
                val m = PORTED_FILE.find(text)
                if (m == null) {
                    problems += "${f.path}: ported file without ' * file: src/main/java/... @ <commit>' line"
                } else if (!pinned.startsWith(m.groupValues[2])) {
                    problems += "${f.path}: ported from commit ${m.groupValues[2]}, but the pinned TFC commit is ${pinned.take(9)} (re-check the port, then update the header)"
                }
            }
        }
        report.get().asFile.writeText(problems.joinToString("\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("License header check failed:\n" + problems.joinToString("\n"))
        }
    }

    companion object {
        val HEADER = listOf(
            "/*",
            " * Licensed under the EUPL, Version 1.2.",
            " * You may obtain a copy of the Licence at:",
            " * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12",
        )
        val PORTED_FILE = Regex("""\* file: (src/\S+) @ ([0-9a-f]{7,40})""")
    }
}
