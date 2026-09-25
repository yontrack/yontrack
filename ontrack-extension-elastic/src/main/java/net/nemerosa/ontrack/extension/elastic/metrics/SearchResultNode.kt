package net.nemerosa.ontrack.extension.elastic.metrics

class SearchResultNode(
        val index: String,
        val id: String,
        val score: Double,
        val source: Map<String, Any?>
)