package net.nemerosa.ontrack.extension.audittrail.evidence

import jakarta.servlet.MultipartConfigElement
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.servlet.MultipartConfigFactory
import org.springframework.boot.servlet.autoconfigure.MultipartProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Multipart limits raised for the upload of the evidences.
 *
 * The servlet container applies the multipart limits to every multipart request: Spring Boot's
 * defaults — 1 MB per file, 10 MB per request — would refuse an evidence long before the upload
 * sees it. This configuration replaces the `MultipartConfigElement` of Spring Boot by the one of the
 * `spring.servlet.multipart.*` properties, its limits raised to the maximum size of an evidence
 * ([EvidenceMultipartLimits]). The upload enforces that maximum itself, as it streams the content.
 *
 * The multipart requests are resolved lazily (`spring.servlet.multipart.resolve-lazily: true` in
 * `application.yml`): a request is only parsed once a controller reads its parts — for an
 * evidence, once the upload is authorized.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(name = ["spring.servlet.multipart.enabled"], matchIfMissing = true)
class EvidenceMultipartConfiguration {

    private val logger = LoggerFactory.getLogger(EvidenceMultipartConfiguration::class.java)

    @Bean
    fun multipartConfigElement(
        multipartProperties: MultipartProperties,
        auditTrailConfigProperties: AuditTrailConfigProperties,
    ): MultipartConfigElement {
        val limits = EvidenceMultipartLimits(
            maxFileSize = multipartProperties.maxFileSize,
            maxRequestSize = multipartProperties.maxRequestSize,
        ).raisedFor(auditTrailConfigProperties.storage.maxSize)
        logger.info(
            "[audit-trail] Multipart limits for the evidence: file {}, request {}",
            limits.maxFileSize,
            limits.maxRequestSize,
        )
        // As MultipartProperties.createMultipartConfig, with the raised limits
        val factory = MultipartConfigFactory()
        factory.setFileSizeThreshold(multipartProperties.fileSizeThreshold)
        multipartProperties.location?.takeIf { it.isNotBlank() }?.let { factory.setLocation(it) }
        factory.setMaxFileSize(limits.maxFileSize)
        factory.setMaxRequestSize(limits.maxRequestSize)
        return factory.createMultipartConfig()
    }
}
