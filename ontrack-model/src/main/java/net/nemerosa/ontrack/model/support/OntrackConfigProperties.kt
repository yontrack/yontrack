package net.nemerosa.ontrack.model.support

import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DataSizeUnit
import org.springframework.boot.convert.DurationUnit
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.time.temporal.ChronoUnit


/**
 * Configuration properties for Ontrack.
 */
@Component
@ConfigurationProperties(prefix = OntrackConfigProperties.PREFIX)
@Validated
@APIName("General configuration")
@APIDescription("General configuration of Ontrack.")
class OntrackConfigProperties {

    private val logger: Logger = LoggerFactory.getLogger(OntrackConfigProperties::class.java)

    @APIDescription("Root URL for this Ontrack installation, used for notifications")
    var url: String = "http://localhost:3000"

    @Min(1)
    @APIDescription("# Maximum number of builds which can be returned by a build filter. Any number above is truncated down to this value")
    var buildFilterCountMax = 200

    @APIDescription("Directory which contains all the working files of Ontrack. It is usually set by the installation.")
    var applicationWorkingDir = "work/files"

    @APIDescription("Testing the configurations of external configurations. Used only for internal testing, to disable the checks when creating external configurations.")
    var configurationTest = true

    /**
     * Job configuration
     */
    @Valid
    var jobs: JobConfigProperties = JobConfigProperties()

    @APIDescription("Activation of the provided labels collection job")
    var jobLabelProviderEnabled = false

    /**
     * Search configuration
     */
    var search = SearchConfigProperties()

    /**
     * Security configuration
     */
    var security = SecurityProperties()

    /**
     * Document storage properties
     */
    var documents = DocumentProperties()

    /**
     * Key store settings
     */
    var fileKeyStore = FileKeyStoreProperties()

    /**
     * Templating settings
     */
    var templating = TemplatingProperties()

    /**
     * Transaction settings
     */
    @Valid
    var tx = TransactionProperties()

    /**
     * Key-store type
     */
    @APIDescription("Key store type to use to store encryption keys")
    var keyStore: String = "file"

    @PostConstruct
    fun log() {
        if (!configurationTest) {
            logger.warn("[config] Tests of external configurations are disabled")
        }
        logger.info("[security] Tokens validity: ${security.tokens.validity}")
        logger.info("[search] Index immediate refresh = ${search.index.immediate}")
        logger.info("[search] Index batch size = ${search.index.batch}")
        logger.info("[search] Index batch logging = ${search.index.logging}")
        logger.info("[search] Index batch tracing = ${search.index.tracing}")
        logger.info("[search] Index creation error ignoring = ${search.index.ignoreExisting}")
        logger.info("[document] Documents engine = ${documents.engine}")
        logger.info("[templating] Errors = ${templating.errors}")
    }

    /**
     * Transaction settings
     */
    @APIName("Transaction configuration")
    @APIDescription("Management of the ad-hoc transactions and of their retries in case of transient database failures.")
    class TransactionProperties {

        @Min(0)
        @APIDescription("Number of times an ad-hoc transaction is retried when it fails because of a transient database issue, like a connection loss or an exhausted connection pool. Set to 0 to disable the retries.")
        var retries: Int = 3

        @APIDescription("Time to wait before the first retry of a transaction. This delay is doubled at each attempt, up to `retryMaxDelay`.")
        @DurationUnit(ChronoUnit.SECONDS)
        var retryDelay: Duration = Duration.ofSeconds(1)

        @APIDescription("Maximum time to wait between two retries of a transaction.")
        @DurationUnit(ChronoUnit.SECONDS)
        var retryMaxDelay: Duration = Duration.ofSeconds(10)

    }

    /**
     * Authorization settings
     */
    class AuthorizationConfigProperties {
        /**
         * Initial provisioning
         */
        var provisioning = true
        /**
         * Initial admin properties
         */
        var admin = AdminConfigProperties()
        /**
         * Custom JWT config
         */
        var jwt = JwtConfigProperties()
    }

    class JwtConfigProperties {
        /**
         * Debugging
         */
        var debug = false
        /**
         * Custom claims
         */
        var claims = JwtClaimsConfigProperties()
        /**
         * Custom JWT `typ`
         */
        var typ: String = ""
    }

    class JwtClaimsConfigProperties {
        /**
         * Custom claim for the email
         */
        var email: String = ""
        /**
         * Custom claim for the list of groups
         */
        var groups: String = ""
    }

    class AdminConfigProperties {
        /**
         * Email
         */
        var email = "admin@ontrack.local"
        /**
         * Full name
         */
        var fullName = "Administrator"
        /**
         * Name of the group to assign (must exists or must be blank)
         */
        var groupName = "Administrators"
        /**
         * Forcing the update of the admin account when already existing
         */
        var force = false
    }

    /**
     * File key store settings
     */
    class FileKeyStoreProperties {
        /**
         * Directory to use for the `file` confidential store
         * Optional. If not filled in, will use a subdirectory of the working directory
         */
        var directory: String = ""
    }

    /**
     * Document storage properties
     */
    class DocumentProperties {
        /**
         * Engine to be used
         */
        var engine: String = DEFAULT

        /**
         * Maximum size
         */
        @DataSizeUnit(DataUnit.KILOBYTES)
        var maxSize: DataSize = DataSize.ofKilobytes(16)

        /**
         * Properties & default values
         */
        companion object {
            /**
             * JDBC based
             */
            const val JDBC = "jdbc"

            /**
             * Default value
             */
            const val DEFAULT = JDBC
        }
    }

    /**
     * Templating settings
     */
    class TemplatingProperties {
        /**
         * How to deal with templating errors?
         */
        var errors: TemplatingErrors = TemplatingErrors.IGNORE

        /**
         * HTML tags to accept on top of the default ones
         */
        @APIDescription("HTML tags to accept on top of the default ones")
        var htmlTags: List<String> = emptyList()
    }

    /**
     * How to deal with templating errors?
     */
    enum class TemplatingErrors {
        /**
         * Ignoring the errors (default)
         */
        IGNORE,

        /**
         * Message in the rendering
         */
        MESSAGE,

        /**
         * Logging the errors stacks (ok for testing) and using the message into the rendering
         */
        LOGGING_STACK,

        /**
         * Throwing the errors (not recommended)
         */
        THROW,
    }

    /**
     * Security settings
     */
    class SecurityProperties {
        /**
         * Authorization settings
         */
        var authorization = AuthorizationConfigProperties()
        /**
         * Security token settings
         */
        var tokens = TokensProperties()
    }

    /**
     * Security token settings
     */
    class TokensProperties {
        /**
         * Default validity duration for the tokens.
         *
         * If set to 0 or negative, the generated tokens do not expire.
         *
         * By default, the tokens do not expire.
         */
        @DurationUnit(ChronoUnit.DAYS)
        var validity: Duration = Duration.ofDays(0)

    }

    companion object {
        /**
         * Properties prefix
         */
        const val PREFIX = "ontrack.config"

        /**
         * Search service key
         */
        internal const val SEARCH = "search"

        /**
         * Documents service key
         */
        internal const val DOCUMENTS = "documents"

        /**
         * Documents engine
         */
        const val DOCUMENTS_ENGINE = "$DOCUMENTS.engine"

        /**
         * Search complete key
         */
        const val SEARCH_PROPERTY = "$PREFIX.$SEARCH"

        /**
         * Key store type
         */
        const val KEY_STORE = "ontrack.config.key-store"
    }
}