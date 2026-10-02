package net.nemerosa.ontrack.extension.audittrail.canonical

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeType
import java.math.BigInteger

/**
 * Canonical form of a JSON tree, as defined by RFC 8785 (JSON Canonicalization Scheme, JCS), on
 * the subset of JSON a trail accepts:
 *
 * - strings, written as ECMAScript's `JSON.stringify` writes them: `"` and `\` escaped, the control
 *   characters below U+0020 escaped — `\b`, `\t`, `\n`, `\f`, `\r` in their short forms, the others
 *   as `\u00xx` in lowercase hexadecimal — and every other character as it is, `/` included;
 * - objects, their properties sorted by the UTF-16 code units of their names, without whitespace;
 * - arrays, in their order, without whitespace;
 * - `true`, `false` and `null`;
 * - integers within ±(2⁵³ − 1), the exact range of I-JSON, written in decimal.
 *
 * Anything else is rejected with a [CanonicalJsonException] naming where it was found: a decimal
 * number (even `1.0` or `1e3`) — payloads carry decimals as strings, which avoids the ECMAScript
 * number formatting of RFC 8785 altogether; an integer outside the range; a string or a property
 * name holding a lone surrogate, which I-JSON forbids; binary or POJO content.
 *
 * The output, encoded in UTF-8, is what is hashed. It is part of the hash format of the trail
 * (see `TrailHashFormatV1`): it must never change for the accepted subset.
 */
object CanonicalJson {

    /**
     * Largest integer accepted, 2⁵³ − 1 (`Number.MAX_SAFE_INTEGER`).
     */
    private val MAX_INTEGER: BigInteger = BigInteger.TWO.pow(53) - BigInteger.ONE

    /**
     * Smallest integer accepted, −(2⁵³ − 1) (`Number.MIN_SAFE_INTEGER`).
     */
    private val MIN_INTEGER: BigInteger = MAX_INTEGER.negate()

    /**
     * Canonical form of a JSON tree.
     *
     * @param node JSON tree
     * @return Canonical JSON text
     * @throws CanonicalJsonException When the tree holds anything outside the accepted subset
     */
    fun canonicalize(node: JsonNode): String = StringBuilder().apply {
        write(node, "", this)
    }.toString()

    private fun write(node: JsonNode, path: String, out: StringBuilder) {
        when (node.nodeType) {
            JsonNodeType.NULL -> out.append("null")
            JsonNodeType.BOOLEAN -> out.append(if (node.booleanValue()) "true" else "false")
            JsonNodeType.STRING -> writeString(node.stringValue(), path, out)
            JsonNodeType.NUMBER -> writeInteger(node, path, out)
            JsonNodeType.ARRAY -> {
                out.append('[')
                node.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item, "$path/$index", out)
                }
                out.append(']')
            }

            JsonNodeType.OBJECT -> {
                out.append('{')
                // String.compareTo compares UTF-16 code units, as RFC 8785 requires
                node.properties()
                    .sortedWith { a, b -> a.key.compareTo(b.key) }
                    .forEachIndexed { index, (name, value) ->
                        if (index > 0) out.append(',')
                        val propertyPath = "$path/${escapePointer(name)}"
                        writeString(name, propertyPath, out)
                        out.append(':')
                        write(value, propertyPath, out)
                    }
                out.append('}')
            }

            JsonNodeType.BINARY -> throw CanonicalJsonException(
                "Canonical JSON does not accept binary content, found at ${display(path)}."
            )

            else -> throw CanonicalJsonException(
                "Canonical JSON does not accept ${node.nodeType} content, found at ${display(path)}."
            )
        }
    }

    private fun writeInteger(node: JsonNode, path: String, out: StringBuilder) {
        val value: BigInteger? = if (node.isIntegralNumber) node.bigIntegerValue() else null
        if (value == null || value > MAX_INTEGER || value < MIN_INTEGER) {
            throw CanonicalJsonException(
                "Canonical JSON accepts only integers within ±(2^53 - 1), found $node at ${display(path)}. " +
                        "Write decimal numbers as strings."
            )
        }
        out.append(value.toString())
    }

    private fun writeString(value: String, path: String, out: StringBuilder) {
        out.append('"')
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\t' -> out.append("\\t")
                c == '\n' -> out.append("\\n")
                c == '\u000c' -> out.append("\\f")
                c == '\r' -> out.append("\\r")
                c < ' ' -> out.append("\\u").append(String.format("%04x", c.code))
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                    out.append(c).append(value[i + 1])
                    i++
                }

                c.isSurrogate() -> throw CanonicalJsonException(
                    "Canonical JSON accepts only well-formed Unicode strings, found a lone surrogate at ${display(path)}."
                )

                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    /**
     * Escapes a property name as a JSON Pointer token (RFC 6901), for the error messages.
     */
    private fun escapePointer(name: String) = name.replace("~", "~0").replace("/", "~1")

    private fun display(path: String) = path.ifEmpty { "/" }
}
