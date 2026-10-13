package net.nemerosa.ontrack.boot.support

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * Which JWT `typ` headers the API accepts, as a client sees it: a call with a bearer token, answered
 * `200` when the token is accepted and `401` when it is not.
 */
abstract class AbstractJwtTypeIT : AbstractJwtHttpIT() {

    /**
     * Status of an authenticated call whose token carries the given `typ` header, none if null.
     */
    protected fun callWithTyp(typ: String?): Int =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/rest/info"))
                .header("Authorization", "Bearer ${jwt(typ = typ, email = "jwt-typ@yontrack.test")}")
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        ).statusCode()

    protected fun assertAccepted(typ: String?) {
        assertEquals(200, callWithTyp(typ), "A token with typ=$typ is accepted")
    }

    protected fun assertRejected(typ: String?) {
        assertEquals(401, callWithTyp(typ), "A token with typ=$typ is rejected")
    }

}
