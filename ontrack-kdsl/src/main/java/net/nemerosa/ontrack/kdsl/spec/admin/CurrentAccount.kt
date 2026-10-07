package net.nemerosa.ontrack.kdsl.spec.admin

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CurrentAccountQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * The account this client is connected as.
 *
 * @property id ID of the account
 * @property email Email of a person, `<slug>[agent]` identifier of an agent
 * @property fullName Full name of a person, display name of an agent
 * @property kind `HUMAN` or `AGENT`
 * @property owner For an agent, the email of its owner
 */
data class CurrentAccount(
    val id: Int,
    val email: String,
    val fullName: String,
    val kind: String,
    val owner: String?,
)

/**
 * Gets the account this client is connected as, through the `user` query.
 *
 * @return The account, or `null` when not authenticated
 */
fun Ontrack.currentAccount(): CurrentAccount? =
    graphqlConnector.query(CurrentAccountQuery())?.user?.account?.let {
        CurrentAccount(
            id = it.id.toInt(),
            email = it.email,
            fullName = it.fullName,
            kind = it.kind.rawValue,
            owner = it.owner?.email,
        )
    }
