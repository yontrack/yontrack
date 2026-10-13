package net.nemerosa.ontrack.kdsl.acceptance.tests

import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

object ACCProperties {

    object Connection {

        const val DEFAULT_USERNAME = "admin@ontrack.local"

        @DefaultValue("http://localhost:8080")
        val url: String by fromEnv()

        val token: String by lazy {
            obtainToken()
        }

        @DefaultValue(DEFAULT_USERNAME)
        val username: String? by fromEnv()

        object Mgt {
            @DefaultValue("http://localhost:8800/manage")
            val url: String by fromEnv()
        }

        object Internal {
            @DefaultValue("http://localhost:8080")
            val url: String by fromEnv()
        }

    }

    object AutoVersioning {
        @DefaultValue("30000") // 30 seconds
        val autoVersioningCompletion: Long by longFromEnv()
    }

    object MockSCM {
        object Timeouts {
            @DefaultValue("30000")
            val general: Long by longFromEnv()

            @DefaultValue("500")
            val interval: Long by longFromEnv()
        }
    }

    object GitHub {
        val token: String? by optionalFromEnv()
    }

    object InfluxDB {
        @DefaultValue("http://localhost:8086")
        val url: String by fromEnv()
    }

    /**
     * The two backends of the cross-replica stack (#2076), each reached directly. Read by the `ha`
     * package only, which reaches the proxy in front of them through [Connection.url].
     */
    object HA {
        object A {
            @DefaultValue("http://localhost:18080")
            val url: String by fromEnv()

            object Mgt {
                @DefaultValue("http://localhost:18880/manage")
                val url: String by fromEnv()
            }
        }

        object B {
            @DefaultValue("http://localhost:18081")
            val url: String by fromEnv()

            object Mgt {
                @DefaultValue("http://localhost:18881/manage")
                val url: String by fromEnv()
            }
        }

        /**
         * Compose project of the stack, naming the containers of the backends, which a test kills
         * and starts again (#2079)
         */
        val project: String? by optionalFromEnv()
    }

    @Target(AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class DefaultValue(
        val value: String = NONE,
    ) {
        companion object {
            const val NONE = "<NONE>"
        }
    }

    private fun obtainToken(): String {
        val providedToken = optionalFromEnv().getValue(Connection, Connection::token)
        return if (providedToken != null) {
            providedToken
        } else {
            getOrCreateToken()
        }
    }

    fun getOrCreateToken(username: String = Connection.username ?: Connection.DEFAULT_USERNAME): String {
        // Creating a management connector
        val connector = DefaultConnector(url = Connection.Mgt.url)
        return connector.get("/account/$username")
            .apply {
                if (statusCode != 200) {
                    error("Cannot get a new token")
                }
            }
            .body.asText()
    }

    private fun fromEnv(): ReadOnlyProperty<Any, String> =
        ReadOnlyProperty { thisRef, property ->
            optionalFromEnv().getValue(thisRef, property)
                ?: error("No system property not environment variable found for $property in $thisRef.")
        }

    private fun longFromEnv(): ReadOnlyProperty<Any, Long> =
        ReadOnlyProperty { thisRef, property ->
            optionalLongFromEnv().getValue(thisRef, property)
                ?: error("No system property not environment variable found for $property in $thisRef.")
        }

    private fun optionalFromEnv(): ReadOnlyProperty<Any, String?> =
        ReadOnlyProperty { thisRef, property ->
            getValue(thisRef, property) {
                it
            }
        }

    private fun optionalLongFromEnv(): ReadOnlyProperty<Any, Long?> =
        ReadOnlyProperty { thisRef, property ->
            getValue(thisRef, property) {
                it.toLong()
            }
        }

    private fun <T> getValue(
        thisRef: Any,
        property: KProperty<*>,
        conversion: (String) -> T,
    ): T? {
        val className = thisRef::class.qualifiedName ?: error("Expecting a full class name")
        val finalName = className.replace(ACCProperties::class.java.name, "ontrack.acceptance")
        val propName = property.name
        val sysProperty = "$finalName.$propName".lowercase()
        val sysValue = System.getProperty(sysProperty)
        return if (!sysValue.isNullOrBlank()) {
            conversion(sysValue)
        } else {
            val envProperty = sysProperty.replace(".", "_").uppercase()
            val envValue = System.getenv(envProperty)
            if (!envValue.isNullOrBlank()) {
                conversion(envValue)
            } else {
                property.findAnnotation<DefaultValue>()
                    ?.value
                    ?.takeIf { it != DefaultValue.NONE }
                    ?.let { conversion(it) }
            }
        }
    }

}