package net.nemerosa.ontrack.graphql.deprecation

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.deprecation.DeprecationMetrics
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class DeprecationInstrumentationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private fun count(item: String): Double =
        meterRegistry.find(DeprecationMetrics.usage)
            .tag("surface", "graphql")
            .tag("item", item)
            .counter()?.count() ?: 0.0

    @Test
    fun `Querying a deprecated field through the GraphQL API is counted`() {
        val before = count("SearchResults.pageItems")
        asAdmin {
            run("""{ search(query: "deprecated-usage") { pageItems { title } } }""")
            run("""{ search(query: "deprecated-usage") { items { title } } }""")
        }
        assertEquals(1.0, count("SearchResults.pageItems") - before)
    }
}
