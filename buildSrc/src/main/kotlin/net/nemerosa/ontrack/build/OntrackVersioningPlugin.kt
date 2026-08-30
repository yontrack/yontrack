package net.nemerosa.ontrack.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.newInstance
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@Suppress("unused")
class OntrackVersioningPlugin : Plugin<Project> {

    interface InjectedExecOps {
        @get:Inject
        val execOps: ExecOperations
    }

    override fun apply(project: Project) {
        val computed = versionCalculator(project).computeVersion()
        val isCI = System.getenv("CI")?.equals("true", ignoreCase = true) == true
        val finalVersion = if (isCI) computed else "$computed-dev"
        project.version = finalVersion
        project.logger.lifecycle("Computed version: $finalVersion")

        project.tasks.register("writeVersion") {
            description = "Called by the CI engine to write the version into a file"
            doLast {
                val versionFile = project.file("build/version.txt")
                versionFile.parentFile.mkdirs()
                versionFile.writeText(project.version.toString())
                project.logger.lifecycle("Version written to: ${versionFile.absolutePath}")
            }
        }
    }

    private fun versionCalculator(project: Project) = VersionCalculator(
        githubRefName = System.getenv("GITHUB_REF_NAME"),
        gitBranch = { execGit(project, "git", "rev-parse", "--abbrev-ref", "HEAD").trim() },
        versionFile = { project.file("VERSION").takeIf { it.exists() }?.readText() },
        gitTags = {
            execGit(project, "git", "tag", "-l").trim()
                .lines()
                .filter { it.isNotBlank() }
        },
        gitShortCommit = { execGit(project, "git", "rev-parse", "--short", "HEAD").trim() },
    )

    private fun execGit(project: Project, vararg command: String): String {
        val injected = project.objects.newInstance<InjectedExecOps>()
        val output = ByteArrayOutputStream()
        val result = injected.execOps.exec {
            commandLine(*command)
            standardOutput = output
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) output.toString().trim() else ""
    }
}
