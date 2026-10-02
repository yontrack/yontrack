package net.nemerosa.ontrack.kdsl.acceptance.tests.scorecard

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingKind
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingDirection
import net.nemerosa.ontrack.kdsl.spec.createLabel
import net.nemerosa.ontrack.kdsl.spec.extension.license.devLicense
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateMarker
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateReadingConfig
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateSecurity
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.ReadingKeys
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.estates
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.scorecard
import net.nemerosa.ontrack.kdsl.spec.setLabels
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Managing the estates of the delivery scorecard through the KDSL, and their licence.
 */
class ACCDSLEstates : AbstractACCDSLTestSupport() {

    @Test
    fun `Creating, reading, updating and deleting an estate`() {
        val category = uid("estate-")
        val product = ontrack.createLabel(category = category, name = "product")
        val critical = ontrack.createLabel(category = category, name = "critical")
        val selected = project { setLabels(product, critical) }
        project { setLabels(product) } // Not all the labels, not selected

        val name = uid("Estate ")
        val estate = ontrack.estates.create(
            name = name,
            description = "Critical products",
            labels = listOf(product.display, critical.display),
            marker = EstateMarker.Promotion("GOLD"),
            readings = listOf(
                EstateReadingConfig(key = ReadingKeys.DELIVERY_LEAD_TIME, target = 86400.0),
                EstateReadingConfig(key = ReadingKeys.DELIVERY_FREQUENCY, windowDays = 30),
            ),
        )
        try {
            assertEquals(name, estate.name)
            assertEquals("Critical products", estate.description)
            assertEquals(setOf(product.display, critical.display), estate.labels.map { it.display }.toSet())
            assertEquals(EstateMarker.Promotion("GOLD"), estate.marker)
            assertNotNull(estate.readingConfig(ReadingKeys.DELIVERY_LEAD_TIME)) {
                assertEquals(86400.0, it.target)
                assertNull(it.windowDays)
                assertEquals(ReadingDirection.LOWER_IS_BETTER, it.direction)
            }
            assertNotNull(estate.readingConfig(ReadingKeys.DELIVERY_FREQUENCY)) {
                assertNull(it.target)
                assertEquals(30, it.windowDays)
                assertEquals(ReadingDirection.HIGHER_IS_BETTER, it.direction)
            }

            // Read back
            assertNotNull(ontrack.estates.findByName(name)) {
                assertEquals(estate.id, it.id)
                assertEquals(EstateMarker.Promotion("GOLD"), it.marker)
            }
            assertTrue(ontrack.estates.list().any { it.id == estate.id })
            assertEquals(listOf(selected.name), estate.projects.map { it.name })

            // The project knows its estate, even before any reading
            assertEquals(
                listOf("Project", name),
                selected.scorecard().sets.map { it.name },
            )

            // The name is unique
            assertFailsWith<GraphQLClientException> {
                ontrack.estates.create(name = name, labels = listOf(product.display))
            }

            // Update, the fields not given being kept
            val updated = estate.update(
                labels = listOf(product.display),
                marker = EstateMarker.Environment("production"),
            )
            assertEquals(name, updated.name)
            assertEquals("Critical products", updated.description)
            assertEquals(listOf(product.display), updated.labels.map { it.display })
            assertEquals(EstateMarker.Environment("production", qualifier = ""), updated.marker)
            assertEquals(2, updated.readingConfigs.size)
            assertEquals(2, updated.projects.size)

            // Back to the default marker
            assertNull(updated.update(marker = null).marker)
        } finally {
            estate.delete()
        }
        assertNull(ontrack.estates.findByName(name))
        assertEquals(listOf("Project"), selected.scorecard().sets.map { it.name })
    }

    @Test
    fun `What an estate expects of the security scans`() {
        val label = ontrack.createLabel(category = uid("estate-"), name = "product")
        val name = uid("Estate ")
        val estate = ontrack.estates.create(
            name = name,
            labels = listOf(label.display),
            security = EstateSecurity(
                expectedKinds = listOf(FindingKind.IMAGE, FindingKind.CODE),
                freshnessDays = 14,
                criticalTargetDays = 7,
                highTargetDays = 30,
            ),
        )
        try {
            val expected = EstateSecurity(
                expectedKinds = listOf(FindingKind.IMAGE, FindingKind.CODE),
                freshnessDays = 14,
                criticalTargetDays = 7,
                highTargetDays = 30,
            )
            assertEquals(expected, estate.security)
            assertEquals(expected, ontrack.estates.findByName(name)?.security)
            // Kept by an update which does not give it
            assertEquals(expected, estate.update(description = "Products").security)
            // Replaced
            assertEquals(EstateSecurity(), estate.update(security = EstateSecurity()).security)
        } finally {
            estate.delete()
        }
    }

    @Test
    fun `Without the licence, the estates are refused and the scorecard has only the set with no estate`() {
        val label = ontrack.createLabel(category = uid("estate-"), name = "product")
        val project = project { setLabels(label) }
        val name = uid("Estate ")
        val estate = ontrack.estates.create(name = name, labels = listOf(label.display))
        try {
            ontrack.devLicense.withoutFeature(FEATURE_SCORECARD) {
                listOf(
                    { ontrack.estates.list() },
                    { ontrack.estates.findByName(name) },
                    { ontrack.estates.create(name = uid("Estate "), labels = listOf(label.display)) },
                    { estate.recompute() },
                ).forEach { call ->
                    val ex = assertFailsWith<GraphQLClientException> { call() }
                    assertTrue(
                        "Feature not allowed by the license: $FEATURE_SCORECARD" in (ex.message ?: ""),
                        "The feature is named in the error: ${ex.message}"
                    )
                }
                assertEquals(listOf("Project"), project.scorecard().sets.map { it.name })
            }
            // Back with the licence
            assertEquals(listOf("Project", name), project.scorecard().sets.map { it.name })
        } finally {
            estate.delete()
        }
    }

    companion object {
        const val FEATURE_SCORECARD = "extension.scorecard"
    }
}
