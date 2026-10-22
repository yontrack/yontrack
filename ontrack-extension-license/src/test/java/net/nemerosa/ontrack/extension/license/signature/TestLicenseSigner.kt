package net.nemerosa.ontrack.extension.license.signature

import net.nemerosa.ontrack.json.asJson
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Signs licence keys the way the licence service does, with a key pair which exists for these
 * tests only: the public half is [PUBLIC_KEY_PATH], the private one sits next to it.
 *
 * No private key of the other key pairs is in the repository, so a test which needs a key the
 * fixtures do not have - a development key, for example - signs its own.
 */
@OptIn(ExperimentalEncodingApi::class)
object TestLicenseSigner {

    /**
     * Resource path to the public key verifying the keys signed here
     */
    const val PUBLIC_KEY_PATH = "/testing/signing/public.key"

    private const val PRIVATE_KEY_PATH = "/testing/signing/private.key"

    /**
     * Signs a licence and encodes it as a licence key.
     */
    fun sign(license: SignatureLicense): String {
        val data = license.asJson().toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey())
            update(data)
        }.sign()
        val container = mapOf(
            "data" to Base64.encode(data),
            "signature" to Base64.encode(signature),
        ).asJson().toString()
        return Base64.encode(container.toByteArray(Charsets.UTF_8))
    }

    private fun privateKey() =
        KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.decode(
                    TestLicenseSigner::class.java.getResource(PRIVATE_KEY_PATH)!!.readText()
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\s".toRegex(), "")
                )
            )
        )
}
