package net.nemerosa.ontrack.model.deprecation

import java.io.File

/**
 * Surface on which a deprecation is declared in the sources.
 *
 * @property code Prefix of the item keys in the baseline
 */
enum class DeprecationMarkerSurface(val code: String) {
    /** Kotlin deprecation annotation */
    KOTLIN("kotlin"),

    /** Java deprecation annotation, its marker in the Javadoc tag */
    JAVA("java"),

    /** GraphQL field or argument deprecated in the code or in an SDL file */
    GRAPHQL("graphql"),

    /** Runtime warning emitted through `DeprecationService`, or a deprecated configuration property */
    WARNING("warning"),

    /** JSDoc tag in the web UI */
    FRONTEND("frontend"),
}

/**
 * Deprecated item found in the sources.
 *
 * @property surface Where it is declared
 * @property path Path of the file, relative to the root of the repository
 * @property line Line of the declaration
 * @property name Name of the item (declaration, GraphQL field, warning item)
 * @property message Marker, `null` when it is not a literal
 * @property external Whether the item is an external contract, to be named on the migration page
 */
data class DeprecatedItem(
    val surface: DeprecationMarkerSurface,
    val path: String,
    val line: Int,
    val name: String,
    val message: String?,
    val external: Boolean,
) {
    /** Identifier of the item in the baseline */
    val key: String get() = "${surface.code} $path#$name"

    /** Where the item is declared */
    val location: String get() = "$path:$line"
}

/**
 * Deprecated item which does not follow the policy of ADR 0018.
 */
data class DeprecationViolation(
    val item: DeprecatedItem,
    val reasons: List<String>,
) {
    val key: String get() = item.key
}

/**
 * Outcome of the check against the baseline.
 *
 * @property newViolations Violations the baseline does not list
 * @property staleEntries Baseline entries whose item conforms now, or is gone
 */
class DeprecationMarkersReport(
    val newViolations: List<DeprecationViolation>,
    val staleEntries: List<String>,
) {
    val isOk: Boolean get() = newViolations.isEmpty() && staleEntries.isEmpty()

    fun message(): String = buildString {
        if (newViolations.isNotEmpty()) {
            appendLine("Deprecations not following ADR 0018 (docs/adr/0018-deprecation-and-removal-across-majors.md).")
            appendLine("The marker is `Removed in V7. Use X instead. See #NNNN` (or `No replacement.`), and an external item")
            appendLine("is named on ${DeprecationMarkers.MIGRATION_PAGE}. Never add them to the baseline.")
            newViolations.forEach { violation ->
                appendLine("- ${violation.item.location} [${violation.key}] ${violation.item.message ?: "<no literal message>"}")
                violation.reasons.forEach { appendLine("    ${violation.item.name} $it") }
            }
        }
        if (staleEntries.isNotEmpty()) {
            appendLine("Baseline entries to remove from ${DeprecationMarkers.BASELINE} - their item conforms now, or is gone:")
            staleEntries.forEach { appendLine("- $it") }
        }
    }
}

/**
 * Scans the sources of the repository for deprecations and checks them against the policy of
 * ADR 0018: the `Removed in V6` / `Removed in V7` marker, a replacement, an issue number, and,
 * for an external item, its name on the migration page.
 *
 * The scan is textual, not a compiler: it reads the declarations the way they are written in this
 * repository.
 *
 * This file is scanned like any other one: none of its patterns is written as a literal it would
 * match itself.
 */
object DeprecationMarkers {

    /** The *Migration to V6* page, where every deprecated external item is named */
    const val MIGRATION_PAGE = "ontrack-docs/docs/content/appendix/migration-to-v6.md"

    /** The baseline of the items which do not conform yet - it may only shrink */
    const val BASELINE = "ontrack-model/src/test/resources/deprecation/markers-baseline.txt"

    /** Modules whose deprecations are external contracts: the KDSL is a client library */
    private val externalRoots = listOf("ontrack-kdsl/src/main/")

    // ===========================================================================================
    // Check
    // ===========================================================================================

    fun parseBaseline(text: String): Set<String> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

    fun check(root: File, baseline: Set<String>): DeprecationMarkersReport {
        val violations = violations(root)
        val keys = violations.map { it.key }.toSet()
        return DeprecationMarkersReport(
            newViolations = violations.filter { it.key !in baseline },
            staleEntries = baseline.filter { it !in keys }.sorted(),
        )
    }

    fun violations(root: File): List<DeprecationViolation> {
        val page = File(root, MIGRATION_PAGE).takeIf { it.isFile }?.readText() ?: ""
        val codeSpans = Regex("`([^`\n]+)`").findAll(page).map { it.groupValues[1] }.toList()
        return scan(root).mapNotNull { item ->
            val reasons = markerReasons(item.message).toMutableList()
            if (item.external && !isNamed(item, page, codeSpans)) {
                reasons += NOT_ON_PAGE
            }
            reasons.takeIf { it.isNotEmpty() }?.let { DeprecationViolation(item, it) }
        }
    }

    private const val NO_MESSAGE = "has no literal deprecation message"
    private const val NO_START = "does not start with `Removed in V6.` or `Removed in V7.`"
    private const val NO_REPLACEMENT = "names no replacement (`Use X instead.` or `No replacement.`)"
    private const val NO_ISSUE = "names no issue (`See #NNNN`)"
    private const val NOT_IN_FORMAT = "is not in the format `Removed in V7. Use X instead. See #NNNN`"
    private const val NOT_ON_PAGE = "is external and not named on the migration page"

    private val startRegex = Regex("^Removed in V[67]\\.")
    private val replacementRegex = Regex("\\bUse .+ instead\\b|\\bNo replacement\\b")
    private val issueRegex = Regex("\\bSee #\\d+")
    private val formatRegex = Regex("^Removed in V[67]\\. (?:Use .+ instead|No replacement)\\. See #\\d+$")

    private fun markerReasons(message: String?): List<String> {
        if (message == null) return listOf(NO_MESSAGE)
        val text = message.trim()
        val reasons = mutableListOf<String>()
        if (!startRegex.containsMatchIn(text)) reasons += NO_START
        if (!replacementRegex.containsMatchIn(text)) reasons += NO_REPLACEMENT
        if (!issueRegex.containsMatchIn(text)) reasons += NO_ISSUE
        if (reasons.isEmpty() && !formatRegex.matches(text)) reasons += NOT_IN_FORMAT
        return reasons
    }

    /**
     * A runtime warning is named by its item, verbatim. Any other item by its name, qualified,
     * inside a code span: `Type.field`, `Query.search(token)`, `Connector.uploadFile`...
     */
    private fun isNamed(item: DeprecatedItem, page: String, codeSpans: List<String>): Boolean =
        if (item.surface == DeprecationMarkerSurface.WARNING) {
            page.contains(item.name)
        } else {
            val name = Regex("[.(]" + Regex.escape(item.name) + "(?![\\w$])")
            codeSpans.any { name.containsMatchIn(it) }
        }

    // ===========================================================================================
    // Scan
    // ===========================================================================================

    /**
     * Build outputs, dependencies, hidden directories - in the main working copy, `.claude/worktrees`
     * holds the checkouts of other branches - and test resources, which are data, not code.
     * The `deprecationMarkersTest` task of this module declares the same files as its inputs.
     */
    private fun File.isSkipped() =
        name in setOf("build", "node_modules") ||
                name.startsWith(".") ||
                invariantSeparatorsPath.let { path ->
                    path.endsWith("src/test/resources") ||
                            path.endsWith("ontrack-web-core/coverage") ||
                            path.endsWith("ontrack-web-core/out")
                }

    private val frontendExtensions = setOf("js", "jsx", "ts", "tsx", "mjs")

    fun scan(root: File): List<DeprecatedItem> =
        root.walkTopDown()
            .onEnter { it == root || !it.isSkipped() }
            .filter { it.isFile }
            .flatMap { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                scanFile(path, file)
            }
            .sortedWith(compareBy({ it.key }, { it.line }))
            .toList()

    private fun scanFile(path: String, file: File): List<DeprecatedItem> {
        val main = path.startsWith("src/main/") || path.contains("/src/main/")
        return when {
            file.extension == "kt" -> {
                val text = file.readText()
                kotlinAnnotations(path, text) +
                        (if (main) graphQLInCode(path, text, templates = true) + warnings(path, text, templates = true) else emptyList())
            }

            file.extension == "java" -> {
                val text = file.readText()
                javaAnnotations(path, text) +
                        (if (main) graphQLInCode(path, text, templates = false) + warnings(path, text, templates = false) else emptyList())
            }

            file.extension == "graphqls" && main -> graphQLInSchema(path, file.readText())
            file.extension in frontendExtensions && path.startsWith("ontrack-web-core/") -> frontend(path, file.readText())
            else -> emptyList()
        }
    }

    // --- Kotlin & Java annotations -------------------------------------------------------------

    /** The annotation, simple or qualified - written in two parts so that this file does not match it */
    private val annotationRegex = Regex("@(?:kotlin\\.|java\\.lang\\.)?" + "Deprecated" + "\\b")

    private fun kotlinAnnotations(path: String, text: String): List<DeprecatedItem> =
        annotationRegex.findAll(text)
            .filterNot { isCommentOrString(text, it.range.first) }
            .map { match ->
                var end = match.range.last + 1
                var message: String? = null
                val open = skipBlanks(text, end)
                if (open < text.length && text[open] == '(') {
                    val close = closing(text, open)
                    val args = arguments(text, open, close)
                    val arg = args.firstOrNull { it.first == "message" } ?: args.firstOrNull { it.first == null }
                    message = arg?.second?.let { literal(it, templates = true) }
                    end = close + 1
                }
                DeprecatedItem(
                    surface = DeprecationMarkerSurface.KOTLIN,
                    path = path,
                    line = lineOf(text, match.range.first),
                    name = kotlinName(text, end),
                    message = message,
                    external = externalRoots.any { path.startsWith(it) },
                )
            }
            .toList()

    private fun javaAnnotations(path: String, text: String): List<DeprecatedItem> =
        annotationRegex.findAll(text)
            .filterNot { isCommentOrString(text, it.range.first) }
            .map { match ->
                var end = match.range.last + 1
                val open = skipBlanks(text, end)
                if (open < text.length && text[open] == '(') {
                    end = closing(text, open) + 1
                }
                DeprecatedItem(
                    surface = DeprecationMarkerSurface.JAVA,
                    path = path,
                    line = lineOf(text, match.range.first),
                    name = javaName(text, end),
                    message = javadocTag(text, match.range.first),
                    external = externalRoots.any { path.startsWith(it) },
                )
            }
            .toList()

    private val kotlinModifiers = setOf(
        "public", "private", "protected", "internal", "open", "abstract", "override", "final", "data",
        "enum", "sealed", "inner", "inline", "suspend", "operator", "infix", "tailrec", "external",
        "const", "lateinit", "annotation", "value", "companion", "expect", "actual", "vararg",
    )

    private fun kotlinName(text: String, from: Int): String {
        var i = skipAnnotations(text, from)
        while (i < text.length) {
            val word = identifierAt(text, i) ?: return "?"
            i = skipAnnotations(text, i + word.length)
            when (word) {
                in kotlinModifiers -> continue
                "fun", "val", "var" -> return lastIdentifierBefore(text, i, setOf('(', ':', '=', '{', '\n'))
                "class", "interface", "typealias" -> return identifierAt(text, i) ?: "?"
                "object" -> return identifierAt(text, i) ?: "Companion"
                else -> return word
            }
        }
        return "?"
    }

    private val javaModifiers = setOf(
        "public", "private", "protected", "static", "final", "abstract", "synchronized", "default",
        "native", "transient", "volatile", "strictfp",
    )

    private fun javaName(text: String, from: Int): String {
        var i = skipAnnotations(text, from)
        while (i < text.length) {
            val word = identifierAt(text, i) ?: return "?"
            if (word in javaModifiers) {
                i = skipAnnotations(text, i + word.length)
                continue
            }
            if (word in setOf("class", "interface", "enum", "record")) {
                return identifierAt(text, skipAnnotations(text, i + word.length)) ?: "?"
            }
            return lastIdentifierBefore(text, i, setOf('(', '=', ';', '{'))
        }
        return "?"
    }

    /**
     * Text of the `@deprecated` tag of the Javadoc preceding the annotations of a declaration.
     */
    private fun javadocTag(text: String, annotation: Int): String? {
        val end = text.lastIndexOf("*/", annotation)
        if (end < 0) return null
        // Only annotations between the comment and this one
        var i = end + 2
        while (i < annotation) {
            val c = text[i]
            when {
                c == '(' -> i = closing(text, i) + 1
                c == ';' || c == '{' || c == '}' -> return null
                else -> i++
            }
        }
        val open = text.lastIndexOf("/**", end)
        if (open < 0) return null
        return docTag(text.substring(open + 3, end))
    }

    // --- GraphQL --------------------------------------------------------------------------------

    private val deprecateCallRegex = Regex("\\.deprecate" + "\\(")
    private val deprecationArgRegex = Regex("\\bdeprecation" + "\\s*=\\s*(?=\")")
    private val fieldNameRegex = Regex("\\.name\\(\\s*(?:\"(\\w+)\"|(\\w+))\\s*\\)|\\b(?:field)?[nN]ame\\s*=\\s*\"(\\w+)\"")
    private val propertyRefRegex = Regex("::(\\w+)")

    private fun graphQLInCode(path: String, text: String, templates: Boolean): List<DeprecatedItem> {
        val calls = deprecateCallRegex.findAll(text).mapNotNull { match ->
            val open = match.range.last
            val args = arguments(text, open, closing(text, open))
            args.singleOrNull()?.second?.let { literal(it, templates) }?.let { match.range.first to it }
        }
        val named = deprecationArgRegex.findAll(text).mapNotNull { match ->
            val start = match.range.last + 1
            val end = endOfLiteralChain(text, start)
            literal(text.substring(start, end), templates)?.let { match.range.first to it }
        }
        return (calls + named)
            .filterNot { (index, _) -> isCommentOrString(text, index) }
            .map { (index, message) ->
                DeprecatedItem(
                    surface = DeprecationMarkerSurface.GRAPHQL,
                    path = path,
                    line = lineOf(text, index),
                    name = graphQLFieldName(text, index),
                    message = message,
                    external = true,
                )
            }
            .toList()
    }

    /**
     * Name of the field a deprecation is attached to: a property reference on the same line, or
     * else the closest field name above - a constant being resolved in the same file.
     */
    private fun graphQLFieldName(text: String, index: Int): String {
        val lineStart = text.lastIndexOf('\n', index - 1) + 1
        propertyRefRegex.findAll(text.substring(lineStart, index))
            .map { it.groupValues[1] }
            .lastOrNull { it != "class" }
            ?.let { return it }
        val window = text.substring(maxOf(0, index - 1000), index)
        val match = fieldNameRegex.findAll(window).lastOrNull() ?: return "?"
        val (literal, constant, named) = match.destructured
        return when {
            literal.isNotEmpty() -> literal
            named.isNotEmpty() -> named
            else -> Regex("\\b" + Regex.escape(constant) + "\\s*=\\s*\"(\\w+)\"").find(text)?.groupValues?.get(1) ?: constant
        }
    }

    private val schemaDeprecationRegex = Regex("@" + "deprecated\\s*\\(\\s*reason\\s*:\\s*(?=\")")

    private fun graphQLInSchema(path: String, text: String): List<DeprecatedItem> =
        schemaDeprecationRegex.findAll(text).map { match ->
            val start = match.range.last + 1
            val lineStart = text.lastIndexOf('\n', match.range.first) + 1
            DeprecatedItem(
                surface = DeprecationMarkerSurface.GRAPHQL,
                path = path,
                line = lineOf(text, match.range.first),
                name = identifierAt(text, skipBlanks(text, lineStart)) ?: "?",
                message = literal(text.substring(start, endOfLiteralChain(text, start)), templates = false),
                external = true,
            )
        }.toList()

    // --- Runtime warnings -----------------------------------------------------------------------

    private val warningRegex = Regex("\\b(deprecatedUsage|DeprecatedConfigurationProperty)" + "\\(")

    /**
     * A runtime warning names an external item. Only the literal items are checked: an item built
     * at runtime has no name to look for.
     */
    private fun warnings(path: String, text: String, templates: Boolean): List<DeprecatedItem> =
        warningRegex.findAll(text)
            .filterNot { isCommentOrString(text, it.range.first) }
            .mapNotNull { match ->
                val open = match.range.last
                val args = arguments(text, open, closing(text, open))
                val (itemName, itemIndex) = when (match.groupValues[1]) {
                    "deprecatedUsage" -> "item" to 1
                    else -> "name" to 0
                }
                val messageIndex = itemIndex + 1
                fun arg(name: String, index: Int) =
                    args.firstOrNull { it.first == name }?.second
                        ?: args.getOrNull(index)?.takeIf { it.first == null }?.second
                val item = arg(itemName, itemIndex)?.let { literal(it, templates) } ?: return@mapNotNull null
                DeprecatedItem(
                    surface = DeprecationMarkerSurface.WARNING,
                    path = path,
                    line = lineOf(text, match.range.first),
                    name = item,
                    message = arg("message", messageIndex)?.let { literal(it, templates) },
                    external = true,
                )
            }
            .toList()

    // --- Frontend -------------------------------------------------------------------------------

    private val docCommentRegex = Regex("/\\*\\*(.*?)\\*/", RegexOption.DOT_MATCHES_ALL)
    private val jsDeclarationRegex = Regex(
        "^(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?(?:function\\*?\\s+|const\\s+|let\\s+|var\\s+|class\\s+)?([A-Za-z_$][\\w$]*)"
    )

    private fun frontend(path: String, text: String): List<DeprecatedItem> =
        docCommentRegex.findAll(text)
            .filter { it.groupValues[1].contains("@" + "deprecated") }
            .map { match ->
                val after = skipBlanks(text, match.range.last + 1)
                val declaration = text.substring(after).lineSequence().firstOrNull() ?: ""
                DeprecatedItem(
                    surface = DeprecationMarkerSurface.FRONTEND,
                    path = path,
                    line = lineOf(text, match.range.first + match.value.indexOf("@" + "deprecated")),
                    name = jsDeclarationRegex.find(declaration)?.groupValues?.get(1) ?: "?",
                    message = docTag(match.groupValues[1]),
                    external = false,
                )
            }
            .toList()

    // ===========================================================================================
    // Lexing
    // ===========================================================================================

    /**
     * Text of the `@deprecated` tag of a doc comment, up to the next tag.
     */
    private fun docTag(comment: String): String? {
        val tag = "@" + "deprecated"
        val lines = comment.lines().map { it.trim().removePrefix("*").trim() }
        val first = lines.indexOfFirst { it.contains(tag) }
        if (first < 0) return null
        val parts = mutableListOf(lines[first].substringAfter(tag))
        lines.drop(first + 1).takeWhile { !it.startsWith("@") }.forEach { parts += it }
        return parts.joinToString(" ").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
    }

    private fun lineOf(text: String, index: Int) = text.substring(0, index).count { it == '\n' } + 1

    /**
     * Whether a position is in a comment or a string literal, as far as its own line tells.
     */
    private fun isCommentOrString(text: String, index: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', index - 1) + 1
        val before = text.substring(lineStart, index)
        val trimmed = before.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return true
        var inString = false
        var i = 0
        while (i < before.length) {
            val c = before[i]
            when {
                c == '\\' && inString -> i++
                c == '"' -> inString = !inString
                !inString && before.startsWith("//", i) -> return true
            }
            i++
        }
        return inString
    }

    private fun skipBlanks(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    /**
     * Skips blanks, comments and annotations (with their arguments).
     */
    private fun skipAnnotations(text: String, from: Int): Int {
        var i = skipBlanks(text, from)
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> i = skipBlanks(text, text.indexOf('\n', i).let { if (it < 0) text.length else it })
                text.startsWith("/*", i) -> i = skipBlanks(text, text.indexOf("*/", i).let { if (it < 0) text.length else it + 2 })
                text[i] == '@' -> {
                    i++
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "_.:")) i++
                    val open = skipBlanks(text, i)
                    if (open < text.length && text[open] == '(') i = closing(text, open) + 1
                    i = skipBlanks(text, i)
                }

                else -> return i
            }
        }
        return i
    }

    private fun identifierAt(text: String, index: Int): String? {
        if (index >= text.length || !(text[index].isLetter() || text[index] == '_')) return null
        var end = index
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
        return text.substring(index, end)
    }

    /**
     * Last identifier before the first of the [stops], generic parameters and annotations left out:
     * the name in `fun <T> List<T>.name(`, in `LocalDateTime name(`, or a backticked test name.
     */
    private fun lastIdentifierBefore(text: String, from: Int, stops: Set<Char>): String {
        var i = from
        var depth = 0
        var last: String? = null
        while (i < text.length) {
            val c = text[i]
            when {
                c == '<' -> depth++
                c == '>' -> depth--
                depth == 0 && c in stops && last != null -> return last
                depth == 0 && c == '`' -> {
                    val end = text.indexOf('`', i + 1).let { if (it < 0) text.length else it }
                    last = text.substring(i + 1, end)
                    i = end + 1
                    continue
                }

                depth == 0 && c == '@' -> {
                    i = skipAnnotations(text, i)
                    continue
                }

                depth == 0 && (c.isLetter() || c == '_') -> {
                    val word = identifierAt(text, i)!!
                    last = word
                    i += word.length
                    continue
                }
            }
            i++
        }
        return last ?: "?"
    }

    private fun endOfString(text: String, start: Int): Int {
        if (text.startsWith("\"\"\"", start)) {
            val end = text.indexOf("\"\"\"", start + 3)
            return if (end < 0) text.length else end + 3
        }
        val quote = text[start]
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                quote, '\n' -> return i + 1
            }
            i++
        }
        return text.length
    }

    /**
     * Index of the bracket closing the one at [open], strings skipped.
     */
    private fun closing(text: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            when (text[i]) {
                '"', '\'' -> {
                    i = endOfString(text, i)
                    continue
                }

                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return text.length - 1
    }

    /**
     * Arguments between two brackets, each with its name when it is given one.
     */
    private fun arguments(text: String, open: Int, close: Int): List<Pair<String?, String>> {
        val args = mutableListOf<String>()
        var depth = 0
        var start = open + 1
        var i = open + 1
        while (i < close) {
            when (text[i]) {
                '"', '\'' -> {
                    i = endOfString(text, i)
                    continue
                }

                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) {
                    args += text.substring(start, i)
                    start = i + 1
                }
            }
            i++
        }
        args += text.substring(start, minOf(close, text.length))
        return args
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { arg ->
                val named = Regex("^(\\w+)\\s*=(?!=)\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(arg)
                if (named != null) named.groupValues[1] to named.groupValues[2] else null to arg
            }
    }

    private fun endOfLiteralChain(text: String, start: Int): Int {
        var i = start
        while (true) {
            i = skipBlanks(text, i)
            if (i >= text.length || text[i] != '"') return i
            i = skipBlanks(text, endOfString(text, i))
            if (i < text.length && text[i] == '+') i++ else return i
        }
    }

    /**
     * Value of an expression made only of string literals joined by `+`, or `null`.
     *
     * @param templates Whether `$` starts a template (Kotlin), making the value not a literal
     */
    private fun literal(expression: String, templates: Boolean): String? {
        val text = expression.trim()
        val value = StringBuilder()
        var i = 0
        while (true) {
            i = skipBlanks(text, i)
            if (i >= text.length || text[i] != '"') return null
            val raw = text.startsWith("\"\"\"", i)
            val end = endOfString(text, i)
            val content = if (raw) text.substring(i + 3, end - 3) else text.substring(i + 1, end - 1)
            if (templates && Regex("(?<!\\\\)\\$[{\\w]").containsMatchIn(content)) return null
            value.append(if (raw) content else content.replace(Regex("\\\\(.)"), "$1"))
            i = skipBlanks(text, end)
            if (i >= text.length) return value.toString()
            if (text[i] != '+') return null
            i++
        }
    }
}
