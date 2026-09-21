package net.nemerosa.ontrack.graphql

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.security.Authorization
import kotlin.test.assertEquals
import kotlin.test.fail

object GraphQLAuthorizationsTestSupport {

    fun assertAuthorizationGranted(
        authorizations: JsonNode,
        name: String,
        action: String
    ) {
        assertAuthorization(
            authorizations = authorizations,
            name = name,
            action = action,
            granted = true
        )
    }

    private fun assertAuthorization(
        authorizations: JsonNode,
        name: String,
        action: String,
        granted: Boolean,
    ) {
        val list = authorizations.parse<List<Authorization>>()
        val auth = list.find { it.name == name && it.action == action }
            ?: fail("Could not find authorization for name $name and action $action")
        assertEquals(
            granted,
            auth.authorized,
            "Authorization for name $name and action $action expected to be $granted but was ${auth.authorized}"
        )
    }

}