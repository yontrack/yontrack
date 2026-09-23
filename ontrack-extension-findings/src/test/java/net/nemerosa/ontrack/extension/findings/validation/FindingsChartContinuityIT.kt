package net.nemerosa.ontrack.extension.findings.validation

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.core.ValidationStampChartParameters
import net.nemerosa.ontrack.extension.chart.core.ValidationStampMetricsChartProvider
import net.nemerosa.ontrack.extension.general.validation.*
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Switching a validation stamp to `security-findings` keeps its metrics chart, when the stamp
 * had a data type the findings are compatible with.
 */
@AsAdminTest
class FindingsChartContinuityIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var provider: ValidationStampMetricsChartProvider

    @Autowired
    private lateinit var chmlValidationDataType: CHMLValidationDataType

    @Autowired
    private lateinit var thresholdNumberValidationDataType: ThresholdNumberValidationDataType

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    private val chmlConfig = CHMLValidationDataTypeConfig(
        warningLevel = CHMLLevel(CHML.HIGH, 1),
        failedLevel = CHMLLevel(CHML.CRITICAL, 1),
    )

    @Test
    fun `A stamp switched from CHML to security-findings keeps its chart history`() {
        project {
            branch {
                val ref = Time.now()
                val vs = validationStamp(validationDataTypeConfig = chmlValidationDataType.config(chmlConfig))
                build {
                    validateWithData(
                        validationStamp = vs,
                        validationDataTypeId = CHMLValidationDataType::class.java.name,
                        validationRunData = CHMLValidationDataTypeData(levels(critical = 2, high = 5)),
                        signature = Signature.of(ref.minusDays(4), "test"),
                    )
                }
                build {
                    validateWithData(
                        validationStamp = vs,
                        validationDataTypeId = CHMLValidationDataType::class.java.name,
                        validationRunData = CHMLValidationDataTypeData(levels(critical = 1, high = 4)),
                        signature = Signature.of(ref.minusDays(3), "test"),
                    )
                }

                val switched = vs.switchToFindings()

                build {
                    validateWithData(
                        validationStamp = switched,
                        validationDataTypeId = FindingsValidationDataType::class.java.name,
                        validationRunData = FindingsValidationDataTypeData(
                            levels(high = 3, medium = 1),
                            unknown = 7,
                            accepted = 2,
                        ),
                        signature = Signature.of(ref.minusDays(2), "test"),
                    )
                }
                build {
                    validateWithData(
                        validationStamp = switched,
                        validationDataTypeId = FindingsValidationDataType::class.java.name,
                        validationRunData = FindingsValidationDataTypeData(levels(high = 1)),
                        signature = Signature.of(ref.minusDays(1), "test"),
                    )
                }

                val chart = chart(switched, ref)
                assertEquals(listOf("critical", "high", "medium", "low"), chart.metricNames)
                assertEquals(
                    listOf(
                        emptyMap(),
                        emptyMap(),
                        metrics(critical = 2, high = 5),
                        metrics(critical = 1, high = 4),
                        metrics(high = 3, medium = 1),
                        metrics(high = 1),
                        emptyMap(),
                    ),
                    chart.metricValues
                )
            }
        }
    }

    @Test
    fun `A stamp switched from ThresholdNumber to security-findings restarts its chart`() {
        project {
            branch {
                val ref = Time.now()
                val vs = validationStamp(
                    validationDataTypeConfig = thresholdNumberValidationDataType.config(
                        ThresholdConfig(warningThreshold = 1, failureThreshold = 5, okIfGreater = false)
                    )
                )
                build {
                    validateWithData(
                        validationStamp = vs,
                        validationDataTypeId = ThresholdNumberValidationDataType::class.java.name,
                        validationRunData = 3,
                        signature = Signature.of(ref.minusDays(3), "test"),
                    )
                }

                val switched = vs.switchToFindings()

                build {
                    validateWithData(
                        validationStamp = switched,
                        validationDataTypeId = FindingsValidationDataType::class.java.name,
                        validationRunData = FindingsValidationDataTypeData(levels(critical = 1)),
                        signature = Signature.of(ref.minusDays(1), "test"),
                    )
                }

                val chart = chart(switched, ref)
                assertEquals(listOf("critical", "high", "medium", "low"), chart.metricNames)
                assertEquals(
                    listOf(
                        emptyMap(),
                        emptyMap(),
                        emptyMap(),
                        // The ThresholdNumber run is not part of the chart
                        emptyMap(),
                        emptyMap(),
                        metrics(critical = 1),
                        emptyMap(),
                    ),
                    chart.metricValues
                )
            }
        }
    }

    private fun ValidationStamp.switchToFindings(): ValidationStamp {
        structureService.saveValidationStamp(
            withDataType(findingsValidationDataType.config(chmlConfig))
        )
        return structureService.getValidationStamp(id)
    }

    private fun chart(vs: ValidationStamp, ref: LocalDateTime) =
        provider.getChart(
            GetChartOptions(
                ref = ref,
                interval = "1w",
                period = "1d",
            ),
            ValidationStampChartParameters(vs.id())
        )

    private fun levels(critical: Int = 0, high: Int = 0, medium: Int = 0, low: Int = 0) = mapOf(
        CHML.CRITICAL to critical,
        CHML.HIGH to high,
        CHML.MEDIUM to medium,
        CHML.LOW to low,
    )

    private fun metrics(critical: Int = 0, high: Int = 0, medium: Int = 0, low: Int = 0) = mapOf(
        "critical" to critical.toDouble(),
        "high" to high.toDouble(),
        "medium" to medium.toDouble(),
        "low" to low.toDouble(),
    )
}
