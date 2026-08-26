package net.nemerosa.ontrack.extension.general

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPromotionPropertyTypeTest {

    private lateinit var type: AutoPromotionPropertyType
    private lateinit var structureService: StructureService

    private val branch = Branch.of(
        Project.of(nd("P", "")).withId(ID.of(1)),
        nd("B", "")
    ).withId(ID.of(1))

    private val validationStamp1 = ValidationStamp.of(
        branch,
        nd("VS1", "")
    ).withId(ID.of(1))

    private val validationStamp2 = ValidationStamp.of(
        branch,
        nd("VS2", "")
    ).withId(ID.of(2))

    @BeforeEach
    fun setup() {
        structureService = mockk()
        type = AutoPromotionPropertyType(
            GeneralExtensionFeature(),
            structureService
        )
    }

    @Test
    fun `From client - parsing error returns an empty list`() {
        val property = type.fromClient(
            mapOf(
                "validationStamps" to "VS1"
            ).asJson()
        )
        assertTrue(property.validationStamps.isEmpty(), "Empty list")
    }

    @Test
    fun `From storage`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        every { structureService.getValidationStamp(ID.of(2)) } returns validationStamp2
        val autoPromotionProperty = type.fromStorage(
            mapOf(
                "validationStamps" to listOf(1, 2),
                "include" to "include",
                "exclude" to "exclude"
            ).asJson()
        )
        assertEquals(listOf("VS1", "VS2"), autoPromotionProperty.validationStamps.map { it.name })
        assertEquals("include", autoPromotionProperty.include)
        assertEquals("exclude", autoPromotionProperty.exclude)
    }

    @Test
    fun `From storage - backward compatibility`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        every { structureService.getValidationStamp(ID.of(2)) } returns validationStamp2
        val autoPromotionProperty = type.fromStorage(
            listOf(1, 2).asJson()
        )
        assertEquals(listOf("VS1", "VS2"), autoPromotionProperty.validationStamps.map { it.name })
        assertEquals("", autoPromotionProperty.include)
        assertEquals("", autoPromotionProperty.exclude)
    }

    @Test
    fun `For storage`() {
        val autoPromotionProperty = AutoPromotionProperty(
            listOf(validationStamp1, validationStamp2),
            "include",
            "exclude",
            emptyList()
        )
        val node = type.forStorage(autoPromotionProperty)
        assertEquals(1, node.path("validationStamps")[0].asInt())
        assertEquals(2, node.path("validationStamps")[1].asInt())
        assertEquals("include", node.path("include").asText())
        assertEquals("exclude", node.path("exclude").asText())
    }

    @Test
    fun `For and from storage`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        every { structureService.getValidationStamp(ID.of(2)) } returns validationStamp2
        val autoPromotionProperty = AutoPromotionProperty(
            listOf(validationStamp1, validationStamp2),
            "include",
            "exclude",
            emptyList()
        )
        val node = type.forStorage(autoPromotionProperty)
        val restored = type.fromStorage(node)
        assertEquals(autoPromotionProperty, restored)
    }

    @Test
    fun `From storage - autoRevoke defaults to false when absent`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        val autoPromotionProperty = type.fromStorage(
            mapOf(
                "validationStamps" to listOf(1),
                "include" to "",
                "exclude" to ""
            ).asJson()
        )
        assertFalse(autoPromotionProperty.autoRevoke, "autoRevoke defaults to false")
    }

    @Test
    fun `From storage - backward compatibility - autoRevoke defaults to false`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        every { structureService.getValidationStamp(ID.of(2)) } returns validationStamp2
        val autoPromotionProperty = type.fromStorage(
            listOf(1, 2).asJson()
        )
        assertFalse(autoPromotionProperty.autoRevoke, "autoRevoke defaults to false")
    }

    @Test
    fun `From storage - autoRevoke is read when present`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        val autoPromotionProperty = type.fromStorage(
            mapOf(
                "validationStamps" to listOf(1),
                "include" to "",
                "exclude" to "",
                "autoRevoke" to true
            ).asJson()
        )
        assertTrue(autoPromotionProperty.autoRevoke, "autoRevoke is read")
    }

    @Test
    fun `For storage - autoRevoke is written`() {
        val autoPromotionProperty = AutoPromotionProperty(
            validationStamps = listOf(validationStamp1),
            include = "",
            exclude = "",
            promotionLevels = emptyList(),
            autoRevoke = true,
        )
        val node = type.forStorage(autoPromotionProperty)
        assertTrue(node.path("autoRevoke").asBoolean(), "autoRevoke is stored")
    }

    @Test
    fun `For and from storage - autoRevoke round trip`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        val autoPromotionProperty = AutoPromotionProperty(
            validationStamps = listOf(validationStamp1),
            include = "include",
            exclude = "exclude",
            promotionLevels = emptyList(),
            autoRevoke = true,
        )
        val restored = type.fromStorage(type.forStorage(autoPromotionProperty))
        assertEquals(autoPromotionProperty, restored)
    }

    @Test
    fun `From client`() {
        every { structureService.getValidationStamp(ID.of(1)) } returns validationStamp1
        every { structureService.getValidationStamp(ID.of(2)) } returns validationStamp2
        val autoPromotionProperty = type.fromClient(
            mapOf(
                "validationStamps" to listOf(1, 2)
            ).asJson()
        )
        assertEquals(listOf("VS1", "VS2"), autoPromotionProperty.validationStamps.map { it.name })
    }

}
