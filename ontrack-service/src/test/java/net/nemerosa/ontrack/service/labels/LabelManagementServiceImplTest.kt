package net.nemerosa.ontrack.service.labels

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.labels.LabelDeletionGuard
import net.nemerosa.ontrack.model.labels.LabelInUseException
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.repository.LabelRecord
import net.nemerosa.ontrack.repository.LabelRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LabelManagementServiceImplTest {

    private val labelRepository = mockk<LabelRepository>()
    private val securityService = mockk<SecurityService>()

    @BeforeEach
    fun init() {
        every { securityService.checkGlobalFunction(LabelManagement::class.java) } just runs
        every { labelRepository.getLabel(10) } returns
                LabelRecord(id = 10, category = "team", name = "a", description = null, color = "#FF0000")
        every { labelRepository.deleteLabel(10) } returns Ack.OK
    }

    private fun guard(reason: String?) = object : LabelDeletionGuard {
        override fun checkLabelDeletion(label: Label): String? = reason
    }

    @Test
    fun `A label nothing needs is deleted`() {
        val service = LabelManagementServiceImpl(labelRepository, securityService, listOf(guard(null)))
        service.deleteLabel(10)
        verify { labelRepository.deleteLabel(10) }
    }

    @Test
    fun `A label a guard needs is not deleted, with the reasons of every guard`() {
        val service = LabelManagementServiceImpl(
            labelRepository,
            securityService,
            listOf(guard("it selects the estates A, B"), guard(null), guard("it is needed elsewhere")),
        )
        val ex = assertFailsWith<LabelInUseException> {
            service.deleteLabel(10)
        }
        assertEquals(
            "Label team:a cannot be deleted: it selects the estates A, B; it is needed elsewhere.",
            ex.message
        )
        verify(exactly = 0) { labelRepository.deleteLabel(any()) }
    }
}
