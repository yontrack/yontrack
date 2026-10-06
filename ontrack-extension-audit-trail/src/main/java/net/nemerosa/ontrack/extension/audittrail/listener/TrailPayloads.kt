package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.evidence.Evidence
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.math.BigInteger
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.HexFormat

/**
 * Building blocks of the payloads of the entries: JSON objects within the subset of canonical
 * JSON, without the values which are not set.
 */
object TrailPayloads {

    private val factory = JsonNodeFactory.instance

    private val MAX_INTEGER: BigInteger = BigInteger.TWO.pow(53) - BigInteger.ONE

    /**
     * A JSON object of the given properties, those whose value is `null` left out.
     *
     * A value is a [JsonNode], a [String], an [Int], a [Long], a [Boolean], or a nested map of the
     * same.
     */
    fun payload(vararg properties: Pair<String, Any?>): ObjectNode = obj(properties.toMap())

    private fun obj(properties: Map<String, Any?>): ObjectNode = factory.objectNode().apply {
        properties.forEach { (name, value) ->
            when (value) {
                null -> {}
                is JsonNode -> set(name, value)
                is String -> put(name, value)
                is Int -> put(name, value)
                is Long -> put(name, value)
                is Boolean -> put(name, value)
                is Map<*, *> -> set(name, obj(value.mapKeys { it.key.toString() }))
                else -> error("Unsupported value in a trail payload: ${value::class.java.name}")
            }
        }
    }

    /**
     * A build, as the first entry of its trail names it, and as the other entries refer to it.
     */
    fun build(build: Build): Map<String, Any> = mapOf(
        "id" to build.id(),
        "project" to build.project.name,
        "branch" to build.branch.name,
        "name" to build.name,
    )

    /**
     * A validation stamp, as an entry refers to it.
     */
    fun validationStamp(validationStamp: ValidationStamp): Map<String, Any> = mapOf(
        "id" to validationStamp.id(),
        "name" to validationStamp.name,
    )

    /**
     * A validation run, as an entry refers to it.
     */
    fun validationRun(id: Int, order: Int): Map<String, Any> = mapOf(
        "id" to id,
        "order" to order,
    )

    /**
     * A validation run, as an entry refers to it.
     */
    fun validationRun(validationRun: ValidationRun): Map<String, Any> =
        validationRun(validationRun.id(), validationRun.runOrder)

    /**
     * An evidence, as its deletion refers to it — by the user, or with its validation run.
     */
    fun deletedEvidence(evidence: Evidence): Map<String, Any> = mapOf(
        "id" to evidence.id,
        "fileName" to evidence.fileName,
        "sha256" to evidence.sha256,
    )

    /**
     * A promotion level, as an entry refers to it.
     */
    fun promotionLevel(promotionLevel: PromotionLevel): Map<String, Any> = mapOf(
        "id" to promotionLevel.id(),
        "name" to promotionLevel.name,
    )

    /**
     * What a signature claims: a time, and a user name.
     */
    fun claimed(signature: Signature): Map<String, Any> = claimed(signature.time, signature.user.name)

    /**
     * A claimed time and user: what a caller supplied, as opposed to the server time and the actor
     * of the entry.
     */
    fun claimed(time: LocalDateTime, user: String): Map<String, Any> = mapOf(
        "time" to TrailHashFormatV1.formatTime(time),
        "user" to user,
    )

    /**
     * A JSON value brought within the subset of canonical JSON: the numbers which canonical JSON
     * rejects — decimals, integers beyond ±(2⁵³ − 1) — are written as strings, as Jackson writes
     * them.
     *
     * Used for the values the trail does not choose, like the stored value of a property.
     */
    fun canonicalValue(node: JsonNode): JsonNode = when {
        node.isNumber && !(node.isIntegralNumber && node.bigIntegerValue().abs() <= MAX_INTEGER) ->
            factory.stringNode(node.asString())

        node.isObject -> factory.objectNode().apply {
            node.properties().forEach { (name, value) -> set(name, canonicalValue(value)) }
        }

        node.isArray -> factory.arrayNode().apply {
            node.forEach { add(canonicalValue(it)) }
        }

        else -> node
    }

    /**
     * SHA-256 of the canonical form of a [canonical value][canonicalValue], as 64 lowercase
     * hexadecimal characters: how an entry refers to data it does not carry.
     */
    fun sha256(node: JsonNode): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(CanonicalJson.canonicalize(canonicalValue(node)).toByteArray(Charsets.UTF_8))
        )
}
