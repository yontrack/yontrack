package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.DEFECTIVE
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.EXPLAINED
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.FAILED
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.FIXED
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.INTERRUPTED
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.INVESTIGATING
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.PASSED
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID.Companion.WARNING
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ValidationRunStatusServiceImplTest {

    private lateinit var service: ValidationRunStatusServiceImpl

    @BeforeEach
    fun init() {
        service = ValidationRunStatusServiceImpl()
        service.start()
    }

    private fun next(id: String): List<String> =
        service.getNextValidationRunStatusList(id).map { it.id }

    @Test
    fun `Transition table`() {
        assertEquals(
            mapOf(
                PASSED to emptyList(),
                FIXED to emptyList(),
                DEFECTIVE to emptyList(),
                EXPLAINED to listOf(FIXED),
                INVESTIGATING to listOf(DEFECTIVE, EXPLAINED, FIXED),
                INTERRUPTED to listOf(INVESTIGATING, FIXED),
                FAILED to listOf(INTERRUPTED, INVESTIGATING, EXPLAINED, DEFECTIVE, FIXED),
                WARNING to listOf(INTERRUPTED, INVESTIGATING, EXPLAINED, DEFECTIVE, FIXED),
            ),
            service.validationRunStatusList.associate { it.id to next(it.id) }
        )
    }

    @Test
    fun `WARNING can be fixed directly`() {
        service.checkTransition(
            service.getValidationRunStatus(WARNING),
            service.getValidationRunStatus(FIXED),
        )
    }

    @Test
    fun `FAILED can be fixed directly`() {
        service.checkTransition(
            service.getValidationRunStatus(FAILED),
            service.getValidationRunStatus(FIXED),
        )
    }

}
