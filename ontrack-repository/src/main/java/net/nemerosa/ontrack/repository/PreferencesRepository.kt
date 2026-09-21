package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode

interface PreferencesRepository {

    fun getPreferences(accountId: Int): JsonNode?

    fun setPreferences(accountId: Int, json: JsonNode)

}