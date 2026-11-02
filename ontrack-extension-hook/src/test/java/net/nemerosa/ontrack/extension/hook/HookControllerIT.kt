package net.nemerosa.ontrack.extension.hook

import net.nemerosa.ontrack.extension.hook.records.HookRecordState
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@AsAdminTest
class HookControllerIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var hookTestSupport: HookTestSupport

    @Autowired
    private lateinit var tokensService: TokensService

    @Test
    fun `Endpoint not defined`() {
        val hook = uid("H_")
        assertFailsWith<HookNotFoundException> {
            hookTestSupport.hook(
                    hook = hook,
                    body = "Sample body",
                    parameters = emptyMap(),
                    headers = emptyMap(),
            )
        }
        hookTestSupport.assertLatestHookRecord(hook) {
            assertEquals(HookRecordState.UNDEFINED, it.state)
        }
    }

    @Test
    fun `Endpoint disabled`() {
        val response = hookTestSupport.testHook(enabled = false)
        assertEquals(HookResponseType.IGNORED, response.type)
        hookTestSupport.assertLatestHookRecord("test") {
            assertEquals(HookRecordState.DISABLED, it.state)
        }
    }

    @Test
    fun `Endpoint denied`() {
        assertFailsWith<AccessDeniedException> {
            hookTestSupport.testHook(denied = true)
        }
        hookTestSupport.assertLatestHookRecord("test") {
            assertEquals(HookRecordState.DENIED, it.state)
        }
    }

    @Test
    fun `A read-only token is refused and recorded as denied`() {
        val account = doCreateAccount()
        val token = tokensService.generateToken(account.id(), TokenOptions(name = "ro", readOnly = true))
        assertFailsWith<AccessDeniedException> {
            hookTestSupport.testHook(token = token.value)
        }
        hookTestSupport.assertLatestHookRecord("test") {
            assertEquals(HookRecordState.DENIED, it.state)
        }
    }

    @Test
    fun `Endpoint processing in error`() {
        assertFailsWith<RuntimeException> {
            hookTestSupport.testHook(error = true)
        }
        hookTestSupport.assertLatestHookRecord("test") {
            assertEquals(HookRecordState.ERROR, it.state)
            assertFalse(it.exception.isNullOrBlank(), "Exception is filled in")
        }
    }

    @Test
    fun `Endpoint processing success`() {
        val response = hookTestSupport.testHook()
        assertEquals(HookResponseType.PROCESSED, response.type)
        hookTestSupport.assertLatestHookRecord("test") {
            assertEquals(HookRecordState.SUCCESS, it.state)
            assertNotNull(it.response) { response ->
                assertEquals(HookResponseType.PROCESSED, response.type)
            }
        }
    }

    @Test
    fun `Provided token not valid`() {
        assertFailsWith<AccessDeniedException> {
            hookTestSupport.testHook(token = "xxxx")
        }
    }

}