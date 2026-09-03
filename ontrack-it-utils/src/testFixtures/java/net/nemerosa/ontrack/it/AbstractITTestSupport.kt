package net.nemerosa.ontrack.it

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.it.AbstractITTestSupport.AbstractIntegrationTestConfiguration
import net.nemerosa.ontrack.json.ObjectMapperFactory.create
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.test.TestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.*
import org.springframework.core.convert.converter.ConverterRegistry
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

@Transactional
@ActiveProfiles(profiles = [RunProfile.DEV])
@SpringBootTest(
    classes = [
        AbstractIntegrationTestConfiguration::class
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(
    properties = [
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.username=ontrack",
        "spring.rabbitmq.password=ontrack",
        "spring.graphql.schema.locations=classpath:graphql/",
        "ontrack.config.search.index.ignoreExisting=true",
        // The "Simulated gate" workflow node executor is off by default and is not picked up from
        // application-dev.yml here: these tests boot their own configuration, not the UI application.
        "ontrack.config.extension.workflows.mock.enabled=true",
    ]
)
abstract class AbstractITTestSupport {

    @Autowired
    protected lateinit var dataSource: DataSource

    /**
     * Named Jdbc template for tests that need direct access to the database
     */
    protected val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
        get() = NamedParameterJdbcTemplate(dataSource!!)

    @Configuration
    @Profile(RunProfile.DEV)
    @ComponentScan("net.nemerosa.ontrack")
    @EnableAutoConfiguration
    class AbstractIntegrationTestConfiguration {

        private val logger: Logger = LoggerFactory.getLogger(AbstractITTestSupport::class.java)

        @Bean
        fun jwtDecoder(): JwtDecoder = mockk<JwtDecoder>(relaxed = true)

        @Bean
        fun meterRegistry(): MeterRegistry {
            return SimpleMeterRegistry()
        }

        @Bean
        fun converterRegistry(): ConverterRegistry {
            return DefaultConversionService()
        }

        @Bean
        @Primary
        fun dataSourceProperties(): DataSourceProperties {
            // Configuration using system properties
            val dbURL = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/ontrack")
            val dbUser = System.getProperty("spring.datasource.username", "ontrack")
            val dbPassword = System.getProperty("spring.datasource.password", "ontrack")
            // Logging
            logger.info("[test][jdbc] URL = {}", dbURL)
            // Properties
            val properties = DataSourceProperties()
            properties.driverClassName = "org.postgresql.Driver"
            properties.url = dbURL
            properties.username = dbUser
            properties.password = dbPassword
            return properties
        }
    }

    protected val objectMapper: ObjectMapper = create()

    companion object {
        fun nameDescription(): NameDescription {
            val uid = TestUtils.uid("")
            return NameDescription(
                uid,
                String.format("%s description", uid)
            )
        }
    }
}
