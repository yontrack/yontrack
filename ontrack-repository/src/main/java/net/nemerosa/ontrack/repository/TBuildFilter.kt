package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode

data class TBuildFilter(
    val accountId: Int?,
    val branchId: Int,
    val name: String,
    val type: String,
    val data: JsonNode
) {
    val isShared: Boolean = accountId == null
}
