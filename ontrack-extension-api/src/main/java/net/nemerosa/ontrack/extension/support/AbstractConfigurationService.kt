package net.nemerosa.ontrack.extension.support

import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.security.EncryptionException
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.*

abstract class AbstractConfigurationService<T : Configuration<T>>(
    private val configurationClass: Class<T>,
    private val configurationRepository: ConfigurationRepository,
    private val securityService: SecurityService,
    private val encryptionService: EncryptionService,
    private val eventPostService: EventPostService,
    private val eventFactory: EventFactory,
    private val ontrackConfigProperties: OntrackConfigProperties
) : ConfigurationService<T> {

    /**
     * Checks the accesses by checking the [net.nemerosa.ontrack.model.security.GlobalSettings] function.
     */
    private fun checkAccess() {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
    }

    override val configurations: List<T>
        get() = configurationRepository.list(configurationClass)
            .map { config: T -> decrypt(config) }

    override fun newConfiguration(configuration: T): T {
        checkAccess()

        val existing = findConfiguration(configuration.name)
        if (existing != null) {
            throw ConfigurationAlreadyExistsException(configuration.name)
        }

        validateAndCheck(configuration)
        configurationRepository.save(encrypt(configuration))
        eventPostService.post(eventFactory.newConfiguration(configuration))
        return configuration.obfuscate()
    }

    override fun getConfiguration(name: String): T {
        return findConfiguration(name)
            ?: throw ConfigurationNotFoundException(name)
    }

    override fun findConfiguration(name: String): T? =
        configurationRepository
            .find(configurationClass, name)
            ?.run { decrypt(this) }

    override fun deleteConfiguration(name: String) {
        checkAccess()
        val configuration = getConfiguration(name)
        // Notifies of the deletion BEFORE the actual deletion, giving the event listeners a last chance to access the configuration
        eventPostService.post(eventFactory.deleteConfiguration(configuration))
        // Actual deletion
        configurationRepository.delete(configurationClass, name)
    }

    override fun updateConfiguration(name: String, configuration: T) {
        checkAccess()
        check(name == configuration.name) { "Configuration name must match" }
        val configToSave = injectCredentials(configuration)
        validateAndCheck(configToSave)
        configurationRepository.save(encrypt(configToSave))
        eventPostService.post(eventFactory.updateConfiguration(configuration))
    }

    /**
     * Adjust a configuration so that it contains a password if
     * 1) the password is empty
     * 2) the configuration already exists
     * 3) the user name is the same
     */
    private fun injectCredentials(configuration: T): T {
        val oldConfig = findConfiguration(configuration.name)
        return if (oldConfig != null) {
            configuration.injectCredentials(oldConfig)
        } else {
            configuration
        }
    }

    private fun validateAndCheck(configuration: T) {
        checkConfigurationFields(configuration)
        if (ontrackConfigProperties.configurationTest) {
            val result = validate(configuration)
            if (result.type == ConnectionResultType.ERROR) {
                throw ConfigurationValidationException(configuration, result.message)
            }
        }
    }

    override fun test(configuration: T): ConnectionResult {
        val completeConfiguration = injectCredentials(configuration)
        checkConfigurationFields(completeConfiguration)
        return validate(completeConfiguration)
    }

    /**
     * Checking the fields of this configuration. Throws [net.nemerosa.ontrack.model.exceptions.InputException] in case of error.
     */
    protected open fun checkConfigurationFields(configuration: T) {}

    /**
     * Validates a configuration by connecting to its target
     */
    protected abstract fun validate(configuration: T): ConnectionResult

    override val configurationType: Class<T> = configurationClass

    protected fun encrypt(config: T): T {
        return config.encrypt {
            encryptionService.encrypt(it)
        }
    }

    protected fun decrypt(config: T): T {
        return try {
            config.decrypt {
                encryptionService.decrypt(it)
            }
        } catch (ex: EncryptionException) {
            config.obfuscate()
        }
    }
}