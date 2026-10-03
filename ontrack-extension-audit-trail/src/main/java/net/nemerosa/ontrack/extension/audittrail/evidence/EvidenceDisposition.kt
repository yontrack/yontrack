package net.nemerosa.ontrack.extension.audittrail.evidence

import org.springframework.util.InvalidMimeTypeException
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * How an evidence is served on download.
 *
 * An evidence is inline only when its declared type is in the allow-list — PDF, JSON, plain text,
 * PNG, JPEG, GIF, WebP — **and** its first bytes agree with that type. Anything else — HTML and SVG
 * included, or an allow-listed type whose content is something else — is an attachment served as
 * `application/octet-stream`, so that no browser, nor a UI building a blob from the response, ever
 * renders active content in the origin of Yontrack. The declared type is what a client claimed; it
 * is never served as such outside the allow-list.
 *
 * @property inline Whether the evidence may be displayed inline
 * @property contentType Content type to serve the evidence with
 */
data class EvidenceDisposition(
    val inline: Boolean,
    val contentType: String,
) {
    companion object {

        /**
         * Number of first bytes of the content the decision reads
         */
        const val HEAD_SIZE = 512

        /**
         * Content type of every evidence served as an attachment
         */
        const val ATTACHMENT_CONTENT_TYPE = "application/octet-stream"

        private val ATTACHMENT = EvidenceDisposition(inline = false, contentType = ATTACHMENT_CONTENT_TYPE)

        private const val PDF = "application/pdf"
        private const val JSON = "application/json"
        private const val TEXT = "text/plain"
        private const val TEXT_UTF8 = "text/plain;charset=UTF-8"

        private val PNG_MAGIC = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG_MAGIC = bytes(0xFF, 0xD8, 0xFF)
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val GIF87_MAGIC = "GIF87a".toByteArray(Charsets.US_ASCII)
        private val GIF89_MAGIC = "GIF89a".toByteArray(Charsets.US_ASCII)
        private val RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
        private val WEBP_MAGIC = "WEBP".toByteArray(Charsets.US_ASCII)
        private val UTF8_BOM = bytes(0xEF, 0xBB, 0xBF)

        /**
         * Decides how to serve an evidence.
         *
         * @param mediaType Declared media type of the evidence
         * @param head First bytes of the evidence — at most [HEAD_SIZE], fewer when it is shorter
         * @return How to serve it
         */
        fun of(mediaType: String, head: ByteArray): EvidenceDisposition {
            val type = parse(mediaType) ?: return ATTACHMENT
            val essence = "${type.type}/${type.subtype}"
            return when {
                essence == PDF && head.startsWith(PDF_MAGIC) -> inline(PDF)
                essence == "image/png" && head.startsWith(PNG_MAGIC) -> inline(essence)
                essence == "image/jpeg" && head.startsWith(JPEG_MAGIC) -> inline(essence)
                essence == "image/gif" && (head.startsWith(GIF87_MAGIC) || head.startsWith(GIF89_MAGIC)) -> inline(essence)
                essence == "image/webp" && head.startsWith(RIFF_MAGIC) && head.startsWith(WEBP_MAGIC, offset = 8) -> inline(essence)
                isJson(type) && looksLikeJson(head) -> inline(JSON)
                essence == TEXT && isUtf8Text(head) -> inline(TEXT_UTF8)
                else -> ATTACHMENT
            }
        }

        private fun inline(contentType: String) = EvidenceDisposition(inline = true, contentType = contentType)

        private fun parse(mediaType: String): MimeType? =
            try {
                MimeTypeUtils.parseMimeType(mediaType).takeIf { !it.isWildcardType && !it.isWildcardSubtype }
            } catch (_: InvalidMimeTypeException) {
                null
            }

        private fun isJson(type: MimeType) =
            type.type == "application" && (type.subtype == "json" || type.subtype.endsWith("+json"))

        /**
         * Valid UTF-8 text, without any control character but tabs and line breaks, a head cut in
         * the middle of a character being still text.
         */
        private fun isUtf8Text(head: ByteArray): Boolean {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try {
                // Leaves out the trailing bytes of a character the head may have cut
                decoder.decode(ByteBuffer.wrap(head, 0, head.size - incompleteTail(head))).toString()
            } catch (_: CharacterCodingException) {
                return false
            }
            return text.none { it.isISOControl() && it != '\t' && it != '\n' && it != '\r' && it != '\u000C' }
        }

        /**
         * JSON text whose first significant character opens an object or an array — the only JSON
         * an evidence holds — and which is UTF-8 text.
         */
        private fun looksLikeJson(head: ByteArray): Boolean {
            val content = if (head.startsWith(UTF8_BOM)) head.copyOfRange(UTF8_BOM.size, head.size) else head
            val first = content.firstOrNull { it != ' '.code.toByte() && it != '\t'.code.toByte() && it != '\n'.code.toByte() && it != '\r'.code.toByte() }
            return (first == '{'.code.toByte() || first == '['.code.toByte()) && isUtf8Text(content)
        }

        /**
         * Number of bytes at the end of [head] which start a UTF-8 character without completing it
         * — 0 to 3.
         */
        private fun incompleteTail(head: ByteArray): Int {
            // Looks back at most 3 bytes for the lead byte of the last character
            for (back in 1..minOf(3, head.size)) {
                val b = head[head.size - back].toInt() and 0xFF
                if (b and 0xC0 != 0x80) {
                    // Lead byte (or ASCII): its expected length
                    val length = when {
                        b and 0x80 == 0 -> 1
                        b and 0xE0 == 0xC0 -> 2
                        b and 0xF0 == 0xE0 -> 3
                        b and 0xF8 == 0xF0 -> 4
                        else -> return 0 // Not UTF-8: the decoder reports it
                    }
                    return if (length > back) back else 0
                }
            }
            return 0
        }

        private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean =
            size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    }
}
