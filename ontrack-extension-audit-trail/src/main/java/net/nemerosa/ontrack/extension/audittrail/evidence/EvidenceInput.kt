package net.nemerosa.ontrack.extension.audittrail.evidence

import org.springframework.util.InvalidMimeTypeException
import org.springframework.util.MimeTypeUtils
import java.net.URI
import java.net.URISyntaxException
import java.text.Normalizer

/**
 * What a client sends with an evidence, made safe to store, to write in the trail and to serve
 * back. Nothing a client sends ever reaches the key of the evidence in the storage, which is its
 * SHA-256 alone.
 */
object EvidenceInput {

    /**
     * File name of an evidence sent without any usable one
     */
    const val DEFAULT_FILE_NAME = "evidence"

    /**
     * Maximum length of a file name, in characters
     */
    const val MAX_FILE_NAME_LENGTH = 255

    /**
     * Media type of an evidence sent without any
     */
    const val DEFAULT_MEDIA_TYPE = "application/octet-stream"

    /**
     * Maximum length of the tool and version of a source
     */
    const val MAX_SOURCE_LENGTH = 255

    /**
     * Maximum length of the URL of a source
     */
    const val MAX_SOURCE_URL_LENGTH = 2000

    /**
     * Longest extension kept when a file name is cut
     */
    private const val MAX_KEPT_EXTENSION = 16

    private val SHA256 = Regex("^(?:sha256:)?([0-9a-f]{64})$", RegexOption.IGNORE_CASE)

    /**
     * File name of an evidence: its last path segment, without control characters nor bidirectional
     * overrides, at most [MAX_FILE_NAME_LENGTH] characters long — [DEFAULT_FILE_NAME] when nothing
     * is left.
     *
     * The file name is only ever a label — of the evidence, of its download — never a path.
     *
     * @param raw File name as sent
     */
    fun fileName(raw: String?): String {
        val lastSegment = raw?.substringAfterLast('/')?.substringAfterLast('\\') ?: ""
        val cleaned = Normalizer.normalize(lastSegment, Normalizer.Form.NFC)
            .filterNot { it.isISOControl() || it.isBidiControl() }
            .trim()
        if (cleaned.isEmpty() || cleaned.all { it == '.' }) {
            return DEFAULT_FILE_NAME
        }
        return cut(cleaned)
    }

    /**
     * Media type of an evidence: the lowercase `type/subtype` of what the client declared, without
     * its parameters — [DEFAULT_MEDIA_TYPE] when none is declared.
     *
     * @param raw Declared media type
     * @throws EvidenceException [EvidenceError.INVALID] when the type cannot be read, or is a wildcard
     */
    fun mediaType(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return DEFAULT_MEDIA_TYPE
        }
        val type = try {
            MimeTypeUtils.parseMimeType(raw.trim())
        } catch (_: InvalidMimeTypeException) {
            invalid("The media type of the evidence cannot be read.")
        }
        if (type.isWildcardType || type.isWildcardSubtype) {
            invalid("The media type of the evidence must not be a wildcard.")
        }
        val essence = "${type.type}/${type.subtype}"
        if (essence.length > MAX_SOURCE_LENGTH) {
            invalid("The media type of the evidence is too long.")
        }
        return essence
    }

    /**
     * Digest of the evidence as claimed by the client — a SHA-256, as 64 hexadecimal characters,
     * prefixed by `sha256:` or not.
     *
     * @param raw Claimed digest
     * @return SHA-256 in lowercase hexadecimal, `null` when none is claimed
     * @throws EvidenceException [EvidenceError.INVALID] when the digest is not a SHA-256
     */
    fun externalDigest(raw: String?): String? {
        if (raw.isNullOrEmpty()) {
            return null
        }
        val match = SHA256.matchEntire(raw)
            ?: invalid("The external digest must be a SHA-256: 64 hexadecimal characters, optionally prefixed by sha256:.")
        return match.groupValues[1].lowercase()
    }

    /**
     * Source of an evidence.
     *
     * @param tool Tool which produced the evidence
     * @param version Version of the tool
     * @param url Where the evidence was produced: an absolute HTTP or HTTPS URL
     * @return Source, `null` when nothing is set
     * @throws EvidenceException [EvidenceError.INVALID] when a value is too long, holds control
     * characters, or when the URL is not an HTTP or HTTPS one
     */
    fun source(tool: String?, version: String?, url: String?): EvidenceSource? {
        val sourceTool = text("tool", tool, MAX_SOURCE_LENGTH)
        val sourceVersion = text("version", version, MAX_SOURCE_LENGTH)
        val sourceUrl = text("URL", url, MAX_SOURCE_URL_LENGTH)?.also { checkUrl(it) }
        return if (sourceTool == null && sourceVersion == null && sourceUrl == null) {
            null
        } else {
            EvidenceSource(tool = sourceTool, version = sourceVersion, url = sourceUrl)
        }
    }

    private fun text(name: String, raw: String?, maxLength: Int): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.length > maxLength) {
            invalid("The source $name of the evidence is longer than $maxLength characters.")
        }
        if (value.any { it.isISOControl() || it.isBidiControl() }) {
            invalid("The source $name of the evidence holds control characters.")
        }
        return value
    }

    private fun checkUrl(url: String) {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            invalid("The source URL of the evidence is not a URL.")
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            invalid("The source URL of the evidence must be an absolute HTTP or HTTPS URL.")
        }
    }

    /**
     * Cuts a file name to [MAX_FILE_NAME_LENGTH] characters, keeping a short extension.
     */
    private fun cut(name: String): String {
        if (name.length <= MAX_FILE_NAME_LENGTH) {
            return name
        }
        val extension = name.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= MAX_KEPT_EXTENSION && it.length < name.length - 1 }
        return if (extension != null) {
            safeTake(name.dropLast(extension.length + 1), MAX_FILE_NAME_LENGTH - extension.length - 1) + "." + extension
        } else {
            safeTake(name, MAX_FILE_NAME_LENGTH)
        }
    }

    /**
     * First [n] characters of [text], never ending on half of a surrogate pair.
     */
    private fun safeTake(text: String, n: Int): String {
        val taken = text.take(n)
        return if (taken.lastOrNull()?.isHighSurrogate() == true) taken.dropLast(1) else taken
    }

    /**
     * Bidirectional formatting characters, which make a name display otherwise than it reads:
     * LRE, RLE, PDF, LRO, RLO (U+202A–U+202E), LRI, RLI, FSI, PDI (U+2066–U+2069), LRM, RLM
     * (U+200E–U+200F) and ALM (U+061C).
     */
    private fun Char.isBidiControl(): Boolean =
        this in '‪'..'‮' || this in '⁦'..'⁩' || this == '‎' || this == '‏' || this == '؜'

    private fun invalid(message: String): Nothing = throw EvidenceException(EvidenceError.INVALID, message)
}
