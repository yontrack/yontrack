package net.nemerosa.ontrack.extension.github.app

import io.jsonwebtoken.io.AbstractSerializer
import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.json.JsonMapper
import java.io.OutputStream

/**
 * Writes the header and the claims of a JWT with Jackson 3.
 *
 * jjwt has no Jackson 3 module: `jjwt-jackson` is built on Jackson 2, which Yontrack no longer
 * ships (ADR 0016). A JWT only holds strings and numbers — jjwt turns the dates into seconds
 * itself — so a plain mapper is enough.
 */
internal class JwtJsonSerializer : AbstractSerializer<Map<String, *>>() {

    private val mapper = JsonMapper.builderWithJackson2Defaults()
        // jjwt owns the stream it gives
        .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
        .build()

    override fun doSerialize(t: Map<String, *>, out: OutputStream) {
        mapper.writeValue(out, t)
        out.flush()
    }
}
