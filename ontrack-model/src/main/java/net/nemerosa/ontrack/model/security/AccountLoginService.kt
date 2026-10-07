package net.nemerosa.ontrack.model.security

interface AccountLoginService {

    /**
     * Finds the Yontrack account for this email.
     *
     * It not existing, it'll be created.
     *
     * If existing, its IdP (Identity Provider) groups will be synched if they are different from what is stored.
     *
     * An agent identifier (`<slug>[agent]`) is refused with an [AgentIdentifierRefusedException]: an agent
     * never logs in through the identity provider.
     */
    fun login(email: String, fullName: String, idpGroups: List<String>): Account

}