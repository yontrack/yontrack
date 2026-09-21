package net.nemerosa.ontrack.test

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.test.TestUtils.uid
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertTrue
import kotlin.test.fail

inline fun <reified T> assertIs(value: Any?, code: (T) -> Unit = {}) {
    if (value is T) {
        code(value)
    } else {
        fail("Not a ${T::class.qualifiedName}")
    }
}

fun <T> assertPresent(o: Optional<T>, message: String = "Optional must be present", code: (T) -> Unit = {}) {
    if (o.isPresent) {
        code(o.get())
    } else {
        fail(message)
    }
}

fun <T> assertNotPresent(o: Optional<T>, message: String = "Optional is not present") {
    if (o.isPresent) {
        fail(message)
    }
}

fun assertJsonNull(node: JsonNode?, message: String = "Node is expected to be null") {
    assertTrue(node == null || node.isNull || node.isMissingNode, message)
}

fun assertJsonNotNull(
    node: JsonNode?,
    message: String = "Node is expected not to be null",
    code: JsonNode.() -> Unit = {}
) {
    if (node == null || node.isNull || node.isMissingNode) {
        fail(message)
    } else {
        node.code()
    }
}

/**
 * Gets a value from the system properties or from the environment.
 */
fun getEnv(property: String): String {
    return getOptionalEnv(property)
        ?: throw IllegalStateException("Cannot find $property system property or ${propertyToEnvName(property)} environment variable.")
}

/**
 * Gets a value from the system properties or from the environment.
 */
fun getOptionalEnv(property: String): String? {
    val sysValue = System.getProperty(property)
    return if (sysValue.isNullOrBlank()) {
        val envName = propertyToEnvName(property)
        val envValue = System.getenv(envName)
        if (envValue.isNullOrBlank()) {
            null
        } else {
            envValue
        }
    } else {
        sysValue
    }
}

private fun propertyToEnvName(property: String) = property.uppercase().replace('.', '_')

/**
 * Gets the content of a resource as a Base64 encoded string
 */
@OptIn(ExperimentalEncodingApi::class)
fun resourceBase64(path: String): String =
    TestUtils::class.java.getResourceAsStream(path)
        ?.use { it.readBytes() }
        ?.let {
            Base64.encode(it)
        }
        ?: error("Could not find resource $path")

/**
 * Generating a random email
 */
fun email(
    name: String = uid("u-"),
    domain: String = "@ontrack.local"
) = "$name$domain"
