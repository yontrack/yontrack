package net.nemerosa.ontrack.build.notices

import groovy.json.JsonSlurper
import java.io.File
import java.util.jar.JarFile

/**
 * A license, notice or copying file a library packages, with its text.
 *
 * @property path Where it is in the library - for a jar, `<jar name>!/<entry>`
 */
data class NoticeFile(val path: String, val text: String)

/**
 * A third-party library of an image, as its notices present it.
 *
 * @property name `group:artifact` for a Maven module, the package name for npm
 * @property licenses The licenses its SBOM declares: SPDX ids, names or SPDX expressions
 * @property files The license, notice and copying files it packages itself
 */
data class NoticeLibrary(
    val name: String,
    val version: String,
    val licenses: List<String>,
    val purl: String?,
    val files: List<NoticeFile>,
)

/**
 * The licenses a CycloneDX SBOM declares for a component, and its package URL.
 */
data class DeclaredLicenses(val licenses: List<String>, val purl: String?)

/**
 * The `THIRD-PARTY-NOTICES.txt` of an image (#2182): every third-party library it ships, with the
 * license, notice and copying files the library packages, verbatim, and an appendix with the
 * standard text of the licenses of the libraries which package none.
 *
 * The UI image has the same file, in the same format, from `ontrack-web-core/notices/third-party-notices.js`:
 * change both together.
 */
object ThirdPartyNotices {

    private val RULE = "=".repeat(80)
    private val THIN = "-".repeat(80)

    private val NOTICE_SEGMENT = Regex("(?i)^(licen[cs]es?|notices?|copying)([^a-z].*)?$")
    private val EXPRESSION = Regex("""[()]|\s(AND|OR|WITH)\s""")
    private val OPERATORS = setOf("AND", "OR", "WITH")

    /**
     * Renders the notices.
     *
     * @param title First line of the file, naming the image and its version
     * @param licenseText Standard text of a license, by SPDX id or name, `null` when there is none
     * @throws IllegalStateException When a library packages no file and declares no license, or
     * when the text of a license which the appendix needs is missing
     */
    fun render(title: String, libraries: List<NoticeLibrary>, licenseText: (String) -> String?): String {
        val sorted = libraries.sortedWith(compareBy({ it.name }, { it.version }))

        val undeclared = sorted.filter { it.files.isEmpty() && it.licenses.isEmpty() }
        check(undeclared.isEmpty()) {
            "These libraries package no license file and declare no license: " +
                    undeclared.joinToString { "${it.name} ${it.version}" }
        }

        // License key -> the libraries needing its text from the appendix
        val appendix = sortedMapOf<String, MutableList<NoticeLibrary>>()
        sorted.filter { it.files.isEmpty() }.forEach { library ->
            licenseKeys(library.licenses).forEach { key ->
                appendix.getOrPut(key) { mutableListOf() } += library
            }
        }
        val texts = appendix.keys.associateWith(licenseText)
        val missing = texts.filterValues { it == null }.keys
        check(missing.isEmpty()) {
            "No text for the license(s) declared by libraries which package no license file of their own: " +
                    missing.joinToString("; ") { key ->
                        "$key (${appendix.getValue(key).joinToString { "${it.name} ${it.version}" }})"
                    }
        }

        // Normalized text -> path of the file which gave it first
        val given = mutableMapOf<String, String>()

        return buildString {
            append(title).append("\n\n")
            append(
                """
                |This file lists the third-party software shipped in this image, each library with the
                |licenses it declares and, verbatim, the license, notice and copying files it packages.
                |The text of the licenses of the libraries which package no such file is in the
                |appendix at the end of this file.
                """.trimMargin()
            ).append("\n\n")
            sorted.forEach { library ->
                append(RULE).append('\n')
                append(library.name).append(' ').append(library.version).append('\n')
                append("License: ").append(library.licenses.joinToString(", ")).append('\n')
                library.purl?.let { append("Package: ").append(it).append('\n') }
                if (library.files.isEmpty()) {
                    append("\nThis library packages no license file of its own: the text of its license is in the appendix.\n")
                } else {
                    library.files.forEach { file ->
                        val text = normalize(file.text)
                        val first = given[text]
                        if (first != null) {
                            append('\n').append(file.path).append(": same text as ").append(first).append(", above\n")
                        } else {
                            given[text] = file.path
                            append('\n').append(file.path).append('\n').append(THIN).append('\n').append(text)
                        }
                    }
                }
                append('\n')
            }
            if (texts.isNotEmpty()) {
                append(RULE).append('\n')
                append("APPENDIX - LICENSE TEXTS\n")
                append(RULE).append("\n\n")
                texts.forEach { (key, text) ->
                    append(key).append('\n').append(THIN).append('\n').append(normalize(text!!)).append('\n')
                }
            }
        }
    }

    /**
     * The licenses whose text the declared ones need: the ids of an SPDX expression, or the
     * declared id or name as it is.
     */
    fun licenseKeys(declared: List<String>): List<String> =
        declared.flatMap { license ->
            if (EXPRESSION.containsMatchIn(license)) {
                license.replace("(", " ").replace(")", " ")
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() && it !in OPERATORS }
            } else {
                listOf(license.trim())
            }
        }.distinct()

    /**
     * Whether an entry of a jar is a license, notice or copying file: at the root of the jar, or
     * anywhere under `META-INF/` - including the `META-INF/license/` directories of the jars
     * bundling other libraries' code. Never a class.
     */
    fun isJarNoticeFile(path: String): Boolean {
        if (path.endsWith("/") || path.endsWith(".class")) return false
        val segments = path.split('/')
        return when {
            segments.size == 1 -> NOTICE_SEGMENT.matches(segments[0])
            segments[0] == "META-INF" -> segments.drop(1).any { NOTICE_SEGMENT.matches(it) }
            else -> false
        }
    }

    /**
     * The license, notice and copying files of a jar, in path order.
     */
    fun readJar(jar: File): List<NoticeFile> =
        JarFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { isJarNoticeFile(it.name) }
                .sortedBy { it.name }
                .map { entry ->
                    val text = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
                    NoticeFile("${jar.name}!/${entry.name}", text.replace("\r\n", "\n").replace('\r', '\n'))
                }
                .toList()
        }

    /**
     * The licenses a CycloneDX SBOM (JSON) declares for each of its components, by
     * `group:name:version` - the group being empty when the component has none.
     */
    @Suppress("UNCHECKED_CAST")
    fun sbomLicenses(json: String): Map<String, DeclaredLicenses> {
        val sbom = JsonSlurper().parseText(json) as Map<String, Any?>
        val components = sbom["components"] as List<Map<String, Any?>>? ?: emptyList()
        return components.associate { component ->
            val key = "${component["group"] ?: ""}:${component["name"]}:${component["version"]}"
            val licenses = (component["licenses"] as List<Map<String, Any?>>? ?: emptyList()).mapNotNull { entry ->
                val license = entry["license"] as Map<String, Any?>?
                (license?.get("id") ?: license?.get("name") ?: entry["expression"])?.toString()
            }
            key to DeclaredLicenses(licenses, component["purl"]?.toString())
        }
    }

    private fun normalize(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').trimEnd() + "\n"
}
