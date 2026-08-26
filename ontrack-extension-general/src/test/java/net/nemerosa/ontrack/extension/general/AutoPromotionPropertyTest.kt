package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPromotionPropertyTest {

    private lateinit var branch: Branch

    @BeforeEach
    fun setup() {
        branch = Branch.of(
                Project.of(nd("P", "")).withId(ID.of(1)),
                nd("B", "")
        ).withId(ID.of(1))
    }

    @Test
    fun `Empty property`() {
        val vs1 = ValidationStamp.of(
            branch,
            nd("VS1", "")
        ).withId(ID.of(1))
        val pl = PromotionLevel.of(
            branch,
            nd("PL1", "")
        ).withId(ID.of(1))
        assertTrue(AutoPromotionProperty(emptyList(), "", "", emptyList()).isEmpty())
        assertFalse(AutoPromotionProperty(listOf(vs1), "", "", emptyList()).isEmpty())
        assertFalse(AutoPromotionProperty(emptyList(), "", "", listOf(pl)).isEmpty())
        assertFalse(AutoPromotionProperty(emptyList(), "CI.*", "", emptyList()).isEmpty())
    }

    @Test
    fun `A property carrying only autoRevoke stays empty`() {
        assertTrue(
            AutoPromotionProperty(emptyList(), "", "", emptyList(), autoRevoke = true).isEmpty(),
            "autoRevoke alone has nothing to check and does not make the property eligible"
        )
    }

    @Test
    fun `Checks that a validation stamp is contained`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("VS1", "")
        ).withId(ID.of(1))

        val vs2 = ValidationStamp.of(
                branch,
                nd("VS2", "")
        ).withId(ID.of(2))

        val property = AutoPromotionProperty(
                listOf(
                        vs1,
                        vs2
                ), "", "",
                emptyList()
        )

        assertTrue(vs1 in property)
    }

    @Test
    fun `Checks that a validation stamp is not contained`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("VS1", "")
        ).withId(ID.of(1))

        val vs2 = ValidationStamp.of(
                branch,
                nd("VS2", "")
        ).withId(ID.of(2))

        val property = AutoPromotionProperty(
                listOf(
                        vs2
                ), "", "",
                emptyList()
        )

        assertTrue(vs1 !in property)
    }

    @Test
    fun `Inclusion based on pattern`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("CI.1", "")
        ).withId(ID.of(1))
        val vs2 = ValidationStamp.of(
                branch,
                nd("CI.2", "")
        ).withId(ID.of(2))
        val qa = ValidationStamp.of(
                branch,
                nd("QA", "")
        ).withId(ID.of(3))

        val property = AutoPromotionProperty(
                emptyList(), "CI.*", "", emptyList()
        )

        assertTrue(vs1 in property)
        assertTrue(vs2 in property)
        assertTrue(qa !in property)
    }

    @Test
    fun `Inclusion based on pattern and list`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("CI.1", "")
        ).withId(ID.of(1))
        val vs2 = ValidationStamp.of(
                branch,
                nd("CI.2", "")
        ).withId(ID.of(2))
        val qa = ValidationStamp.of(
                branch,
                nd("QA", "")
        ).withId(ID.of(3))

        val property = AutoPromotionProperty(
                listOf(qa), "CI.*", "", emptyList()
        )

        assertTrue(vs1 in property)
        assertTrue(vs2 in property)
        assertTrue(qa in property)
    }

    @Test
    fun `Inclusion and exclusion based on pattern`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("CI.1", "")
        ).withId(ID.of(1))
        val vs2 = ValidationStamp.of(
                branch,
                nd("CI.NIGHT", "")
        ).withId(ID.of(2))
        val qa = ValidationStamp.of(
                branch,
                nd("QA", "")
        ).withId(ID.of(3))

        val property = AutoPromotionProperty(
                emptyList(), "CI.*", ".*NIGHT.*", emptyList()
        )

        assertTrue(vs1 in property)
        assertTrue(vs2 !in property)
        assertTrue(qa !in property)
    }

    @Test
    fun `Inclusion and exclusion based on pattern and list`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("CI.1", "")
        ).withId(ID.of(1))
        val vs2 = ValidationStamp.of(
                branch,
                nd("CI.NIGHT", "")
        ).withId(ID.of(2))
        val qa = ValidationStamp.of(
                branch,
                nd("QA", "")
        ).withId(ID.of(3))

        val property = AutoPromotionProperty(
                listOf(qa), "CI.*", ".*NIGHT.*", emptyList()
        )

        assertTrue(vs1 in property)
        assertTrue(vs2 !in property)
        assertTrue(qa in property)
    }

    @Test
    fun `List has priority on inclusion and exclusion patterns`() {
        val vs1 = ValidationStamp.of(
                branch,
                nd("CI.1", "")
        ).withId(ID.of(1))
        val vs2 = ValidationStamp.of(
                branch,
                nd("CI.NIGHT", "")
        ).withId(ID.of(2))

        val property = AutoPromotionProperty(
                listOf(vs2), "CI.*", ".*NIGHT.*", emptyList()
        )

        assertTrue(vs1 in property)
        assertTrue(vs2 in property)
    }

}
