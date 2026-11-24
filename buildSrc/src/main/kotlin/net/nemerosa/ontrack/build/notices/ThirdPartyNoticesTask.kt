package net.nemerosa.ontrack.build.notices

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Writes the `THIRD-PARTY-NOTICES.txt` of the backend image (#2182) from the jars of the external
 * modules of a classpath - the project's own modules are not third-party - and the licenses its
 * CycloneDX SBOM declares for them. See [ThirdPartyNotices].
 */
abstract class ThirdPartyNoticesTask : DefaultTask() {

    /** The artifacts of the classpath, telling the external modules apart. */
    @get:Internal
    abstract val artifacts: SetProperty<ResolvedArtifactResult>

    /** The same artifacts, as the files the task reads. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val classpath: ConfigurableFileCollection

    /** The CycloneDX SBOM of the classpath, in JSON. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sbom: RegularFileProperty

    /** The standard license texts, as `<SPDX id or name>.txt`. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenseTexts: DirectoryProperty

    /** First line of the file, naming the image and its version. */
    @get:Input
    abstract val title: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun generate() {
        val declared = ThirdPartyNotices.sbomLicenses(sbom.get().asFile.readText())
        val libraries = artifacts.get()
            .mapNotNull { artifact ->
                (artifact.id.componentIdentifier as? ModuleComponentIdentifier)?.let { it to artifact.file }
            }
            // A module may have several jars (classifiers): one entry, with the files of all of them
            .groupBy({ it.first.moduleIdentifier to it.first.version }, { it.second })
            .map { (id, jars) ->
                val (module, version) = id
                val key = "${module.group}:${module.name}:$version"
                val licenses = declared[key]
                    ?: throw GradleException("$key is on the classpath but not in the SBOM ${sbom.get().asFile}")
                NoticeLibrary(
                    name = "${module.group}:${module.name}",
                    version = version,
                    licenses = licenses.licenses,
                    purl = licenses.purl,
                    files = jars.sortedBy { it.name }.flatMap { ThirdPartyNotices.readJar(it) },
                )
            }
        val dir = licenseTexts.get().asFile
        val notices = try {
            ThirdPartyNotices.render(title.get(), libraries) { key ->
                File(dir, "$key.txt").takeIf { it.isFile }?.readText()
            }
        } catch (e: IllegalStateException) {
            throw GradleException("${e.message} - license texts are read from $dir", e)
        }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(notices)
        }
    }
}
