package net.nemerosa.ontrack.model.support

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Configuration properties for the search
 */
class SearchConfigProperties {

    /**
     * Index configuration
     */
    var index = SearchIndexProperties()

    class SearchIndexProperties {

        @APIDescription("By default, indexation is ElasticSearch is done after some time after the index has been requested. The flag below forces the index to be refreshed immediately. This SHOULD NOT be used in production but is very useful when testing Ontrack search capabilities")
        @Deprecated("Removed in V6. No replacement. See #1882")
        var immediate = false

        @APIDescription("When performing full indexation, the indexation is performed by batch. The parameter below allows to set the size of this batch processing. Note: this is a default batch size. Custom indexers can override it.")
        var batch = 1000

        @APIDescription("When performing full indexation, the indexation is performed by batch. The parameter below allows to generate additional logging when indexing actions are actually taken.")
        var logging = false

        @APIDescription("When performing full indexation, the indexation is performed by batch. The parameter below allows to generate additional deep level logging for all actions on Git issues. Note: if set to `true` this generates a lot of information at DEBUG level.")
        var tracing = false

        @APIDescription("Option to ignore errors when creating indexes. For test only, allowing for concurrent testing.")
        @Deprecated("Removed in V6. No replacement. See #1882")
        var ignoreExisting = false

        @APIDescription("Forces a reset of all indexes at startup")
        var reset = false
    }
}