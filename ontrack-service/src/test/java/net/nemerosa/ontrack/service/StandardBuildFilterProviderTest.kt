package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.repository.CoreBuildFilterRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardBuildFilterProviderTest {

    private lateinit var provider: StandardBuildFilterProvider

    @BeforeEach
    fun before() {
        val structureService = mock(StructureService::class.java)
        val propertyService = mock(PropertyService::class.java)
        val coreBuildFilterRepository = mock(CoreBuildFilterRepository::class.java)
        provider = StandardBuildFilterProvider(
            structureService = structureService,
            propertyService = propertyService,
            coreBuildFilterRepository = coreBuildFilterRepository,
            buildDisplayNameService = mock(BuildDisplayNameService::class.java)
        )
    }

    @Test
    fun `Parsing default`() {
        val data = provider.parse(
            emptyMap<String, String>().asJson()
        )
        assertNotNull(data) {
            assertEquals(10, it.count)
        }
    }

    @Test
    fun `Parsing without count`() {
        val data = provider.parse(
            mapOf(
                "withPromotionLevel" to "IRON",
            ).asJson()
        )
        assertNotNull(data) {
            assertEquals(10, it.count)
            assertEquals("IRON", it.withPromotionLevel)
        }
    }

    @Test
    fun parse_count_only() {
        val data = provider.parse(
            mapOf("count" to 5).asJson()
        )
        assertNotNull(data) {
            assertEquals(5, it.count)
            assertNull(it.withPromotionLevel)
        }
    }

    @Test
    fun parse_with_promotion_level_null() {
        val data = provider.parse(
            mapOf(
                "count" to 5,
                "withPromotionLevel" to null,
            ).asJson()
        )
        assertNotNull(data) {
            assertEquals(5, it.count)
            assertNull(it.withPromotionLevel)
        }
    }

    @Test
    fun parse_with_after_date_null() {
        val data = provider.parse(
            mapOf(
                "count" to 5,
                "afterDate" to null,
            ).asJson()
        )
        assertNotNull(data) {
            assertEquals(5, it.count)
            assertNull(it.afterDate)
        }
    }

    @Test
    fun `Parsing the display name filter`() {
        val data = provider.parse(
            mapOf(
                "withDisplayName" to "^1\\.2\\.",
            ).asJson()
        )
        assertNotNull(data) {
            assertEquals("^1\\.2\\.", it.withDisplayName)
        }
    }

    @Test
    fun `Parsing without any display name filter`() {
        val data = provider.parse(
            mapOf("count" to 5).asJson()
        )
        assertNotNull(data) {
            assertNull(it.withDisplayName)
        }
    }

    @Test
    fun `Validation accepts a correct display name regex`() {
        val error = provider.validateData(
            mock(Branch::class.java),
            StandardBuildFilterData.of(10).withWithDisplayName("(1\\.2|1\\.3)\\..*")
        )
        assertNull(error, "A valid regular expression is accepted")
    }

    @Test
    fun `Validation rejects an incorrect display name regex`() {
        val error = provider.validateData(
            mock(Branch::class.java),
            StandardBuildFilterData.of(10).withWithDisplayName("[unclosed")
        )
        assertNotNull(error) {
            assertTrue(
                it.contains("With display name"),
                "The error message mentions the faulty field, was: $it"
            )
        }
    }

    @Test
    fun `Validation ignores a blank display name regex`() {
        val error = provider.validateData(
            mock(Branch::class.java),
            StandardBuildFilterData.of(10).withWithDisplayName("")
        )
        assertNull(error, "A blank regular expression is not validated")
    }

}
