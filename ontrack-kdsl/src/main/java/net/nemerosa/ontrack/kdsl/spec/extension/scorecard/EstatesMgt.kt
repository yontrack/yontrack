package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreateEstateMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.EstateByNameQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.EstatesQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Management of the estates of the delivery scorecard.
 *
 * Every call needs the licensed feature "Delivery scorecard" (`extension.scorecard`) and fails
 * without it; managing the estates also needs the `EstateManagement` global function.
 */
class EstatesMgt(connector: Connector) : Connected(connector) {

    /**
     * All the estates, by name.
     */
    fun list(): List<Estate> =
        graphqlConnector.query(
            EstatesQuery()
        )?.estates?.map {
            it.estateFragment.toEstate(connector)
        } ?: emptyList()

    /**
     * Estate by name, `null` if none.
     */
    fun findByName(name: String): Estate? =
        graphqlConnector.query(
            EstateByNameQuery(name)
        )?.estate?.estateFragment?.toEstate(connector)

    /**
     * Creates an estate. Its readings are computed by its daily job, or on [recompute][Estate.recompute].
     *
     * @param name Unique name of the estate
     * @param labels Labels selecting the projects, as `category:name` or `name` (see
     * [net.nemerosa.ontrack.kdsl.spec.Label.display]). At least one; a project must carry all of them.
     * @param description Description of the estate
     * @param marker Marker the delivery readings are read up to, `null` for the default one
     * @param readings Window override and target per reading, at most one per reading
     * @return Created estate
     */
    fun create(
        name: String,
        labels: List<String>,
        description: String? = null,
        marker: EstateMarker? = null,
        readings: List<EstateReadingConfig> = emptyList(),
    ): Estate =
        graphqlConnector.mutate(
            CreateEstateMutation(
                name = name,
                description = Optional.presentIfNotNull(description),
                labels = labels,
                marker = Optional.presentIfNotNull(marker?.toInput()),
                readings = Optional.present(readings.map { it.toInput() }),
            )
        ) {
            it?.createEstate?.payloadUserErrors?.convert()
        }
            ?.checkData { it.createEstate?.estate }
            ?.estateFragment?.toEstate(connector)
            ?: throw GraphQLMissingDataException("Did not get back the created estate")
}
