package net.nemerosa.ontrack.extension.audittrail.canonical

import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.node.JsonNodeFactory
import java.math.BigInteger
import kotlin.test.assertEquals

/**
 * The canonical form of RFC 8785 (JCS), on the subset of JSON accepted in a trail: strings,
 * objects, arrays, booleans, null and integers within ±(2⁵³ − 1).
 *
 * The expected outputs are the ones of RFC 8785 itself where it gives one, and worked by hand
 * otherwise — never computed by the code under test.
 */
class CanonicalJsonTest {

    private fun canonical(json: String): String = CanonicalJson.canonicalize(json.parseAsJson())

    // RFC 8785 vectors of the accepted subset

    @Test
    fun `RFC 8785 sorting of the object properties by their UTF-16 code units`() {
        // RFC 8785, section 3.2.3
        val input = """
            {
              "\u20ac": "Euro Sign",
              "\r": "Carriage Return",
              "\ufb33": "Hebrew Letter Dalet With Dagesh",
              "1": "One",
              "\ud83d\ude00": "Emoji: Grinning Face",
              "\u0080": "Control",
              "\u00f6": "Latin Small Letter O With Diaeresis"
            }
        """
        assertEquals(
            "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"\u0080\":\"Control\"," +
                    "\"\u00f6\":\"Latin Small Letter O With Diaeresis\",\"\u20ac\":\"Euro Sign\"," +
                    "\"\ud83d\ude00\":\"Emoji: Grinning Face\",\"\ufb33\":\"Hebrew Letter Dalet With Dagesh\"}",
            canonical(input)
        )
    }

    @Test
    fun `RFC 8785 serialization of the strings and the literals`() {
        // RFC 8785, section 3.2.4, without the numbers, which are outside the accepted subset
        val input = """
            {
              "string": "\u20ac$\u000F\u000aA'\u0042\u0022\u005c\\\"\/",
              "literals": [null, true, false]
            }
        """
        assertEquals(
            """{"literals":[null,true,false],"string":"€$\u000f\nA'B\"\\\\\"/"}""",
            canonical(input)
        )
    }

    @Test
    fun `RFC 8785 arrays keep their order, and nested objects are sorted`() {
        // Test data of the RFC 8785 reference implementation, "arrays", without its numbers
        assertEquals(
            """[56,{"1":[],"10":null,"d":true}]""",
            canonical("""[56, {"d": true, "10": null, "1": [ ]}]""")
        )
    }

    @Test
    fun `RFC 8785 structures`() {
        // Test data of the RFC 8785 reference implementation, "structures", with 56 for 56.0
        assertEquals(
            """{"":"empty","1":{"\n":56,"f":{"F":5,"f":"hi"}},"10":{},"111":[{"E":"no","e":"yes"}],"A":{},"a":{}}""",
            canonical(
                """
                    {
                      "1": {"f": {"f": "hi","F": 5} ,"\n": 56},
                      "10": { },
                      "": "empty",
                      "a": { },
                      "111": [ {"e": "yes","E": "no" } ],
                      "A": { }
                    }
                """
            )
        )
    }

    // Strings

    @Test
    fun `Control characters are escaped, with the short forms where JSON has one`() {
        val node = JsonNodeFactory.instance.stringNode("\b\t\n\u000c\r\u0000\u001f \u007f")
        assertEquals(""""\b\t\n\f\r\u0000\u001f ${"\u007f"}"""", CanonicalJson.canonicalize(node))
    }

    @Test
    fun `Non-ASCII characters, line and paragraph separators are written as they are`() {
        val node = JsonNodeFactory.instance.stringNode("é\u2028\u2029\ud83d\ude00")
        assertEquals("\"é\u2028\u2029\ud83d\ude00\"", CanonicalJson.canonicalize(node))
    }

    @Test
    fun `The solidus is not escaped`() {
        assertEquals("\"a/b\"", canonical("\"a\\/b\""))
    }

    // Integers

    @Test
    fun `Integers are written in decimal, without exponent nor sign for zero`() {
        assertEquals("[0,1,-1,42,-1000000]", canonical("[0, 1, -1, 42, -1000000]"))
    }

    @Test
    fun `Integers up to the I-JSON limits are accepted`() {
        assertEquals(
            "[9007199254740991,-9007199254740991]",
            canonical("[9007199254740991, -9007199254740991]")
        )
        val big = JsonNodeFactory.instance.numberNode(BigInteger("9007199254740991"))
        assertEquals("9007199254740991", CanonicalJson.canonicalize(big))
    }

    // Rejections

    @Test
    fun `A decimal number is rejected, with its path`() {
        val ex = assertThrows<CanonicalJsonException> { canonical("""{"a": [1, 1.5]}""") }
        assertEquals(
            "Canonical JSON accepts only integers within ±(2^53 - 1), found 1.5 at /a/1. " +
                    "Write decimal numbers as strings.",
            ex.message
        )
    }

    @Test
    fun `A decimal number is rejected even when its value is integral`() {
        assertThrows<CanonicalJsonException> { canonical("1.0") }
        assertThrows<CanonicalJsonException> { canonical("1e3") }
    }

    @Test
    fun `An integer beyond the I-JSON limits is rejected`() {
        val ex = assertThrows<CanonicalJsonException> { canonical("""{"n": 9007199254740992}""") }
        assertEquals(
            "Canonical JSON accepts only integers within ±(2^53 - 1), found 9007199254740992 at /n. " +
                    "Write decimal numbers as strings.",
            ex.message
        )
        assertThrows<CanonicalJsonException> { canonical("-9007199254740992") }
        assertThrows<CanonicalJsonException> { canonical("123456789012345678901234567890") }
    }

    @Test
    fun `A lone surrogate in a string is rejected`() {
        val node = JsonNodeFactory.instance.objectNode().put("text", "a\ud800b")
        val ex = assertThrows<CanonicalJsonException> { CanonicalJson.canonicalize(node) }
        assertEquals("Canonical JSON accepts only well-formed Unicode strings, found a lone surrogate at /text.", ex.message)
    }

    @Test
    fun `A lone surrogate in a property name is rejected`() {
        val node = JsonNodeFactory.instance.objectNode().put("\udc00", "x")
        assertThrows<CanonicalJsonException> { CanonicalJson.canonicalize(node) }
    }

    @Test
    fun `Binary content is rejected`() {
        val node = JsonNodeFactory.instance.objectNode().put("bytes", byteArrayOf(1, 2, 3))
        val ex = assertThrows<CanonicalJsonException> { CanonicalJson.canonicalize(node) }
        assertEquals("Canonical JSON does not accept binary content, found at /bytes.", ex.message)
    }
}
