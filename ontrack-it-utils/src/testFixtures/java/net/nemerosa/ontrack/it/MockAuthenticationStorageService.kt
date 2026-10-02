package net.nemerosa.ontrack.it

import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.AuthenticationStorageService

class MockAuthenticationStorageService : AuthenticationStorageService {

    override fun getAccountId(): String = "user@email.com"

    override fun getActor(): Actor = Actor(account = "user@email.com", via = ActorVia.TOKEN)

    override fun withAccountId(accountId: String, actor: Actor?, code: () -> Unit) {
        code()
    }
}