package net.nemerosa.ontrack.model.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SecurityRoleTest {

    @Test
    fun administrator() {
        assertEquals("ROLE_ADMIN", SecurityRole.ADMINISTRATOR.roleName)
        assertEquals("ADMIN", SecurityRole.ADMINISTRATOR.roleAbbreviatedName)
    }

    @Test
    fun user() {
        assertEquals("ROLE_USER", SecurityRole.USER.roleName)
        assertEquals("USER", SecurityRole.USER.roleAbbreviatedName)
    }

}