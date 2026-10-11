package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

/**
 * Application started in a process of its own by [ContractMigrationsExitIT]: the database and the
 * Flyway of Spring Boot, and the [StartupStrategy], reading the contract migrations from the
 * locations of the `it.contract.locations` system property.
 *
 * Not a `@Configuration`: the integration tests of the application, which scan every component of
 * `net.nemerosa.ontrack`, would get its [StartupStrategy] as well.
 */
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, FlywayAutoConfiguration::class)
@EnableConfigurationProperties(OntrackConfigProperties::class)
class ContractPhaseTestApplication {

    @Bean
    fun startupStrategy(applicationContext: ApplicationContext, ontrackConfigProperties: OntrackConfigProperties) =
        StartupStrategy(
            applicationContext,
            ontrackConfigProperties.database,
            System.getProperty("it.contract.locations").split(","),
            System::exit,
        )

    /**
     * As FlywayConfiguration
     */
    @Bean
    fun flywayConfigurationCustomizer() = FlywayConfigurationCustomizer { configuration ->
        configuration.table("schema_version")
        configuration.configuration(mapOf(ContractMigrations.POSTGRESQL_TRANSACTIONAL_LOCK to "false"))
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication(ContractPhaseTestApplication::class.java).apply {
                setWebApplicationType(WebApplicationType.NONE)
            }.run(*args)
        }
    }
}
