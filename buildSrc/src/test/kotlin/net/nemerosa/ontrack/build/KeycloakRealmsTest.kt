package net.nemerosa.ontrack.build

import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Keycloak realms under `compose/keycloak/import/` list the UI's redirect URIs literally, since
 * a realm import cannot read [KdslStack]. A KDSL stack in slot N serves its UI on `3000 + N * 100`,
 * and a realm missing that port sends every login from that slot to Keycloak's
 * "Invalid parameter: redirect_uri" page -- which is how `uiLdapTest` and `uiOidcTest` failed from
 * every linked worktree. These checks keep the realms in step with the slot range.
 */
class KeycloakRealmsTest {

    private val kdslUiPorts: List<Int> =
        (0..KdslStack.SLOT_MAX).map { KdslStackInstance(slug = "yontrack", slot = it).uiPort }

    private val realms: List<File> by lazy {
        val importDir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "compose/keycloak/import") }
            .firstOrNull { it.isDirectory }
            ?: error("compose/keycloak/import not found above ${File("").absolutePath}")
        importDir.listFiles()!!
            .map { File(it, "ontrack.json") }
            .filter { it.isFile }
            .sortedBy { it.path }
            .also { assertTrue(it.isNotEmpty(), "no realm found in $importDir") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun uiClient(realm: File): Map<String, Any?> {
        val json = JsonSlurper().parse(realm) as Map<String, Any?>
        val clients = json["clients"] as List<Map<String, Any?>>
        return clients.firstOrNull { it["clientId"] == "ontrack-client" }
            ?: fail("${realm.path} has no ontrack-client")
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.strings(key: String): List<String> =
        (this[key] as List<String>?) ?: emptyList()

    @Test
    fun `every realm redirects to the UI of every KDSL slot`() {
        realms.forEach { realm ->
            val redirectUris = uiClient(realm).strings("redirectUris").map { URI(it) }
            val callbackPaths = redirectUris.map { it.path }.toSet()
            assertTrue(callbackPaths.isNotEmpty(), "${realm.path} has no redirect URI")
            callbackPaths.forEach { path ->
                val ports = redirectUris.filter { it.path == path }.map { it.port }.toSet()
                val missing = kdslUiPorts - ports
                assertTrue(
                    missing.isEmpty(),
                    "${realm.path}: redirect URI $path misses the UI ports $missing of the KDSL slots",
                )
            }
        }
    }

    @Test
    fun `every realm accepts the origin of the UI of every KDSL slot`() {
        realms.forEach { realm ->
            val webOrigins = uiClient(realm).strings("webOrigins")
            // "+" means "the origins of the redirect URIs", checked above
            if ("+" !in webOrigins && "*" !in webOrigins) {
                val missing = kdslUiPorts.map { "http://localhost:$it" } - webOrigins.toSet()
                assertTrue(missing.isEmpty(), "${realm.path}: web origins miss $missing")
            }
        }
    }
}
