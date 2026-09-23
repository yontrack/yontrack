package net.nemerosa.ontrack.extension.findings.events

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionResult
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Notifications of the `security_finding_new` and `security_finding_resolved` events, through
 * subscriptions on a branch and on its project.
 */
@QueueNoAsync
@AsAdminTest
class FindingsEventsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @Autowired
    private lateinit var mockNotificationChannel: MockNotificationChannel

    private val today: LocalDate get() = Time.now.toLocalDate()

    @Test
    fun `New HIGH on main for the branch, every new finding of every branch for the project`() {
        project {
            val main = branch("main")
            val release = branch("release-1.0")
            val vsMain = main.findingsStamp()
            val vsRelease = release.findingsStamp()

            val onMainHigh = main.subscribe(FindingsEvents.SECURITY_FINDING_NEW, keywords = "HIGH")
            val onProject = subscribe(FindingsEvents.SECURITY_FINDING_NEW)

            main.scan(vsMain, entry("CVE-1", severity = "HIGH"), entry("CVE-2", severity = "LOW"))
            release.scan(vsRelease, entry("CVE-3", severity = "HIGH"))

            waitForMessages(onProject, 3)
            assertEquals(
                listOf(
                    "new|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|main|false",
                    "new|CVE-2|LOW|pkg:maven/org.x/y|trivy|IMAGE|main|false",
                    "new|CVE-3|HIGH|pkg:maven/org.x/y|trivy|IMAGE|release-1.0|false",
                ),
                messages(onProject)
            )
            assertEquals(
                listOf("new|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|main|false"),
                messages(onMainHigh)
            )
        }
    }

    @Test
    fun `A finding no longer reported by the latest scan of the stamp is resolved on the branch`() {
        project {
            branch {
                val vs = findingsStamp()
                val onBranch = subscribe(FindingsEvents.SECURITY_FINDING_RESOLVED)
                val onProject = project.subscribe(FindingsEvents.SECURITY_FINDING_RESOLVED)

                scan(vs, entry("CVE-1", severity = "CRITICAL"), entry("CVE-2"))
                scan(vs, entry("CVE-2"))

                val expected = listOf("resolved|CVE-1|CRITICAL|pkg:maven/org.x/y|trivy|IMAGE|$name|false")
                waitForMessages(onProject, 1)
                assertEquals(expected, messages(onProject))
                assertEquals(expected, messages(onBranch))
            }
        }
    }

    @Test
    fun `A return after resolution is new again and flagged as reopened`() {
        project {
            branch {
                val vs = findingsStamp()
                val onBranch = subscribe(FindingsEvents.SECURITY_FINDING_NEW, FindingsEvents.SECURITY_FINDING_RESOLVED)

                scan(vs, entry("CVE-1"))
                scan(vs)
                scan(vs, entry("CVE-1", severity = "CRITICAL"))

                waitForMessages(onBranch, 3)
                assertEquals(
                    listOf(
                        "new|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|$name|false",
                        "resolved|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|$name|false",
                        "new|CVE-1|CRITICAL|pkg:maven/org.x/y|trivy|IMAGE|$name|true",
                    ),
                    messages(onBranch)
                )
            }
        }
    }

    @Test
    fun `No event per observation of an exposed finding`() {
        project {
            branch {
                val vs = findingsStamp()
                val onProject = project.subscribe(FindingsEvents.SECURITY_FINDING_NEW, FindingsEvents.SECURITY_FINDING_RESOLVED)

                scan(vs, entry("CVE-1"))
                scan(vs, entry("CVE-1"))
                scan(vs, entry("CVE-1"), entry("CVE-2"))

                waitForMessages(onProject, 2)
                assertEquals(
                    listOf(
                        "new|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|$name|false",
                        "new|CVE-2|HIGH|pkg:maven/org.x/y|trivy|IMAGE|$name|false",
                    ),
                    messages(onProject)
                )
            }
        }
    }

    @Test
    fun `No event for a finding first seen accepted, until it is reported without acceptance`() {
        project {
            branch {
                val vs = findingsStamp()
                val onProject = project.subscribe(FindingsEvents.SECURITY_FINDING_NEW, FindingsEvents.SECURITY_FINDING_RESOLVED)

                scan(vs, entry("CVE-1", acceptedUntil = today.plusYears(1)), entry("CVE-2"))
                scan(vs, entry("CVE-1"), entry("CVE-2"))

                waitForMessages(onProject, 2)
                assertEquals(
                    listOf(
                        "new|CVE-2|HIGH|pkg:maven/org.x/y|trivy|IMAGE|$name|false",
                        "new|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|$name|true",
                    ),
                    messages(onProject)
                )
            }
        }
    }

    @Test
    fun `No event on the deletion of a branch`() {
        project {
            val main = branch("main")
            val feature = branch("feature-y")
            val vsFeature = feature.findingsStamp()
            val onProject = subscribe(FindingsEvents.SECURITY_FINDING_NEW, FindingsEvents.SECURITY_FINDING_RESOLVED)

            feature.scan(vsFeature, entry("CVE-1"))
            waitForMessages(onProject, 1)

            feature.delete()
            // A later event, to be sure nothing was queued in between
            val vsMain = main.findingsStamp()
            main.scan(vsMain, entry("CVE-2"))

            waitForMessages(onProject, 2)
            assertEquals(
                listOf(
                    "new|CVE-1|HIGH|pkg:maven/org.x/y|trivy|IMAGE|feature-y|false",
                    "new|CVE-2|HIGH|pkg:maven/org.x/y|trivy|IMAGE|main|false",
                ),
                messages(onProject)
            )
        }
    }

    @Test
    fun `Default templates of the events`() {
        project {
            branch {
                val vs = findingsStamp()
                val target = uid("t-")
                eventSubscriptionService.subscribe(
                    name = uid("s-"),
                    channel = mockNotificationChannel,
                    channelConfig = MockNotificationChannelConfig(target = target),
                    projectEntity = this,
                    keywords = null,
                    origin = "test",
                    contentTemplate = null,
                    FindingsEvents.SECURITY_FINDING_NEW,
                    FindingsEvents.SECURITY_FINDING_RESOLVED,
                )

                scan(vs, entry("CVE-1"))
                scan(vs)

                waitForMessages(target, 2)
                assertEquals(
                    listOf(
                        "New HIGH finding CVE-1 reported by trivy on ${project.name}/$name, at pkg:maven/org.x/y.",
                        "Finding CVE-1 (HIGH) reported by trivy is resolved on ${project.name}/$name, at pkg:maven/org.x/y.",
                    ),
                    messages(target)
                )
            }
        }
    }

    /**
     * Subscribes the entity to the given events, with a template rendering the event type and
     * the context of the event, and returns the target of the notifications. One subscription per
     * event type, all to the same target, for the template to say which type was received.
     */
    private fun ProjectEntity.subscribe(
        vararg eventTypes: EventType,
        keywords: String? = null,
    ): String {
        val target = uid("t-")
        eventTypes.forEach { eventType ->
            eventSubscriptionService.subscribe(
                name = uid("s-"),
                channel = mockNotificationChannel,
                channelConfig = MockNotificationChannelConfig(target = target),
                projectEntity = this,
                keywords = keywords,
                origin = "test",
                contentTemplate = template(eventType),
                eventType,
            )
        }
        return target
    }

    private fun template(eventType: EventType): String {
        val prefix = when (eventType) {
            FindingsEvents.SECURITY_FINDING_NEW -> "new"
            FindingsEvents.SECURITY_FINDING_RESOLVED -> "resolved"
            else -> error("Unexpected event type: ${eventType.id}")
        }
        return listOf(
            prefix,
            "\${EXTERNAL_ID}",
            "\${SEVERITY}",
            "\${LOCATION}",
            "\${SCANNER}",
            "\${KIND}",
            "\${branch}",
            "\${REOPENED}",
        ).joinToString("|")
    }

    private fun waitForMessages(target: String, count: Int) {
        mockNotificationChannel.waitUntilReceivedCountMessages(
            what = "Waiting for $count message(s) on $target",
            target = target,
            expectedCount = count,
        )
    }

    private fun messages(target: String): List<String> =
        mockNotificationChannel.targetMessages(target).map { it.trim() }

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, vararg entries: String): FindingsIngestionResult =
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )

    private fun entry(externalId: String, severity: String = "HIGH", acceptedUntil: LocalDate? = null): String {
        val acceptance = acceptedUntil?.let {
            """, "acceptance": {"statement": "Not reachable", "expiresAt": "$it", "source": ".trivyignore.yaml"}"""
        } ?: ""
        return """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "$severity", "title": "Title of $externalId"$acceptance}"""
    }

}
