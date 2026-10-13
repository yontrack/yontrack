package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.service.security.EncryptionServiceKeys.ENCRYPTION_KEY
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * Default encryption service
 */
@Component
class EncryptionServiceImpl(
    private val securityService: SecurityService,
    private val key: ConfidentialKey,
) : EncryptionService {

    @Autowired
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    constructor(
        securityService: SecurityService,
        confidentialStore: ConfidentialStore,
        confidentialKeyVersions: ConfidentialKeyVersions,
    ) : this(
        securityService,
        CryptoConfidentialKey(
            confidentialStore,
            ENCRYPTION_KEY,
            confidentialKeyVersions,
        )
    )

    override fun encrypt(plain: String?): String? = plain?.let { key.encrypt(it) }

    override fun decrypt(crypted: String?): String? = crypted?.let { key.decrypt(it) }

    override fun exportKey(): String? {
        checkAdmin()
        return try {
            key.exportKey()
        } catch (e: IOException) {
            throw EncryptionException(e)
        }
    }

    override fun importKey(key: String) {
        checkAdmin()
        try {
            this.key.importKey(key)
        } catch (e: IOException) {
            throw EncryptionException(e)
        }
    }

    private fun checkAdmin() {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        securityService.checkGlobalFunction(GlobalSettings::class.java)
    }

}