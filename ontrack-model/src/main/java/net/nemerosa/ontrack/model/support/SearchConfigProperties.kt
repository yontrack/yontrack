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

    @APIDescription("Maximum number of results counted and ranked for each type of result. Past it, the count of the type is shown as `1000+` (for the default), and only the most recently updated of its strongest matches are ranked.")
    var countCap = 1000

    @APIDescription("`work_mem` of the Postgres transaction of a search, set with `SET LOCAL` so that it ends with the transaction and no other connection is affected. A search on a frequent word goes through tens of thousands of documents, whose bitmap does not fit in the Postgres default of 4 MB. Empty to keep the setting of the database.")
    var workMem = "64MB"

    class SearchIndexProperties {

        @APIDescription("When rebuilding the search documents of a type, they are written by batch. The parameter below sets the size of these batches. Note: this is a default batch size. Some indexers, like the one of the SCM commits, use it for their own batches.")
        var batch = 1000

        @APIDescription("When rebuilding the search documents of a type, the parameter below generates additional logging about the progress of the rebuild.")
        var logging = false

        @APIDescription("When rebuilding the search documents of a type, the parameter below generates additional deep level logging for all actions on SCM commits and issues. Only used when `logging` is `true` as well. Note: if set to `true` this generates a lot of information at DEBUG level.")
        var tracing = false

        @APIDescription("Forces the rebuild of the search documents of all types at startup, in the background.")
        var reset = false
    }
}
