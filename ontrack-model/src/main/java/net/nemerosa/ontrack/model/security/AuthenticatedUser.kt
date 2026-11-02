package net.nemerosa.ontrack.model.security

import java.security.Principal

interface AuthenticatedUser : AuthorisationsCheck, Principal {

    val account: Account?
    val groups: List<AuthorizedGroup>
    val assignedGroups: List<AccountGroup>
    val mappedGroups: List<AccountGroup>
    val idpGroups: List<String>

    /**
     * Name of the read-only token this call is authenticated with, `null` otherwise. It stays set
     * inside `asAdmin`, where the functions are all granted.
     */
    val readOnlyTokenName: String? get() = null

}
