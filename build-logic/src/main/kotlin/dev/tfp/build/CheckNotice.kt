package dev.tfp.build

import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * NOTICE consistency: datagen writes `content/generated/content/third-party.json`, one entry per copied
 * file whose license is not EUPL-1.2 (`[{"path": ..., "license": ..., "source": ...}]`).
 * Every (license, source) pair must be mentioned in NOTICE.
 */
abstract class CheckNotice : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val notice: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val thirdParty: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val text = notice.get().asFile.readText()
        val problems = mutableListOf<String>()
        val manifest = thirdParty.orNull?.asFile
        if (manifest != null && manifest.exists()) {
            for (e in JsonParser.parseString(manifest.readText()).asJsonArray) {
                val o = e.asJsonObject
                val license = o["license"].asString
                val source = o["source"].asString
                if (!text.contains(license) || !text.contains(source)) {
                    problems += "${o["path"].asString}: $license from $source is not listed in NOTICE"
                }
            }
        }
        report.get().asFile.writeText(problems.joinToString("\n"))
        if (problems.isNotEmpty()) throw GradleException("NOTICE check failed:\n" + problems.joinToString("\n"))
    }
}
