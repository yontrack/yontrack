package net.nemerosa.ontrack.extension.api

import io.mockk.mockk
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PromotionRunCheckExtensionTest {

    private val build = mockk<Build>()
    private val promotionLevel = mockk<PromotionLevel>()

    private class TestCheckException(message: String) : InputException(message)

    /**
     * A check which only implements [PromotionRunCheckExtension.checkPromotionRunCreation], and
     * so relies on the default explanation.
     */
    private class TestCheck(
        private val check: (PromotionRun) -> Unit,
    ) : PromotionRunCheckExtension {
        var checked: PromotionRun? = null
        override fun checkPromotionRunCreation(promotionRun: PromotionRun) {
            checked = promotionRun
            check(promotionRun)
        }

        override val order: Int = 0
        override val feature: ExtensionFeature get() = error("Not used")
    }

    @Test
    fun `A passing check explains nothing`() {
        val check = TestCheck { }
        assertTrue(check.explainPromotionRunCreation(build, promotionLevel).isEmpty())
    }

    @Test
    fun `The default explanation checks an unsaved run for the build and the level`() {
        val check = TestCheck { }
        check.explainPromotionRunCreation(build, promotionLevel)
        val run = check.checked
        assertSame(build, run?.build)
        assertSame(promotionLevel, run?.promotionLevel)
        assertEquals(ID.NONE, run?.id, "The run is not saved")
    }

    @Test
    fun `A refusing check explains why`() {
        val check = TestCheck { throw TestCheckException("Not granted because of reasons") }
        assertEquals(
            listOf("Not granted because of reasons"),
            check.explainPromotionRunCreation(build, promotionLevel)
        )
    }

    @Test
    fun `A failure which is not a refusal is not explained away`() {
        val check = TestCheck { throw IllegalStateException("Broken") }
        assertFailsWith<IllegalStateException> {
            check.explainPromotionRunCreation(build, promotionLevel)
        }
    }
}
