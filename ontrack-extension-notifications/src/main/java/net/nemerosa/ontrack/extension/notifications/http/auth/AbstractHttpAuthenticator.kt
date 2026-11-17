package net.nemerosa.ontrack.extension.notifications.http.auth

abstract class AbstractHttpAuthenticator<C> : HttpAuthenticator<C> {

    protected fun merge(input: String, existing: String): String =
        input.ifBlank { existing }

}
