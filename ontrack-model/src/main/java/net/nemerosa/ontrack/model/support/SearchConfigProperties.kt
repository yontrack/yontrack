package net.nemerosa.ontrack.model.support

import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Configuration properties for the search
 */
class SearchConfigProperties {

    /**
     * Index configuration
     */
    var index = SearchIndexProperties()

    /**
     * Testing type of search results
     */
    var testing = SearchTestingProperties()

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

        @DurationUnit(ChronoUnit.SECONDS)
        @APIDescription("Delay before the rebuild of the search documents at startup (in seconds by default, or as `2m`, `90s`...), past the time when a previous instance, being replaced by a rolling upgrade, still writes documents in their previous format. Only one of the instances started side by side rebuilds the documents of a type. `0` rebuilds them at once.")
        var startupDelay: Duration = Duration.ofMinutes(2)
    }

    /**
     * Slow testing type of search results, for the acceptance tests of the rebuilds across several
     * replicas (#2088).
     */
    class SearchTestingProperties {
        @APIDescription("Registers the `testing-slow` type of search results, whose rebuild runs until it is stopped while a project whose name starts with `search-testing-slow-` exists. For the acceptance tests only: never to be enabled in production.")
        var enabled = false
    }
}
