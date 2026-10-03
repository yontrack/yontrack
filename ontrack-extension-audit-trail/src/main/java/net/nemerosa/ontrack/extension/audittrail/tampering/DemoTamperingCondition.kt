package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * The bean only exists while `ontrack.extension.audit-trail.demo-tampering.enabled` is `true`:
 * when the switch is off, the tampering is not forbidden, it is absent — its endpoint answers 404.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = AuditTrailConfigProperties.DEMO_TAMPERING_PREFIX,
    name = ["enabled"],
    havingValue = "true",
)
annotation class DemoTamperingCondition
