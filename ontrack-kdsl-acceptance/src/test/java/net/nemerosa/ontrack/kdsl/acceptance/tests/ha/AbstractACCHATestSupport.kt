package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Cross-replica tests (#2076), run by `kdslHaTest` against `compose/docker-compose-kdsl-ha.yml`:
 * two backends, A and B, behind a round-robin proxy.
 *
 * - [ontrack] goes through the proxy, so that two calls in a row land on two backends
 * - [ontrackA] and [ontrackB] go to one backend each, bypassing the proxy
 *
 * The three share the token, obtained once from A's management endpoint: it lives in the database
 * and works on both backends.
 */
abstract class AbstractACCHATestSupport : AbstractACCDSLTestSupport() {

    /**
     * Backend A, directly
     */
    protected val ontrackA: Ontrack by lazy { direct(ACCProperties.HA.A.url) }

    /**
     * Backend B, directly
     */
    protected val ontrackB: Ontrack by lazy { direct(ACCProperties.HA.B.url) }

    private fun direct(url: String) =
        Ontrack(connector = DefaultConnector(url = url, token = ACCProperties.Connection.token))

}
