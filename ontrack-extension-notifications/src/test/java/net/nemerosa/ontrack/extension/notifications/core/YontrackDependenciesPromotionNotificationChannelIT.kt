package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.PreviousPromotionConditionProperty
import net.nemerosa.ontrack.extension.general.PreviousPromotionConditionPropertyType
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.node.NullNode
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@AsAdminTest
class YontrackDependenciesPromotionNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var channel: YontrackDependenciesPromotionNotificationChannel

    @Autowired
    private lateinit var yontrackPromotionNotificationChannel: YontrackPromotionNotificationChannel

    @Test
    fun `Promotes the qualified and unqualified links, and never the build of the event`() {
        val source = source()
        val unqualified = target()
        val qualified = target()
        source.linkTo(unqualified)
        source.linkTo(qualified, "moduleX")
        source.linkTo(source)

        val result = publish(source, config())

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals(1, runs(unqualified))
        assertEquals(1, runs(qualified))
        assertEquals(
            mapOf(
                unqualified.name to listOf(""),
                qualified.name to listOf("moduleX"),
            ),
            result.output?.targets?.associate { it.build to it.qualifiers },
        )
        result.output?.targets?.forEach { target ->
            assertEquals(YontrackDependenciesPromotionNotificationChannelTargetStatus.PROMOTED, target.status)
            assertNotNull(target.runId, "The ID of the run is set")
            assertNull(target.message)
        }
    }

    @Test
    fun `Output identifies the target`() {
        val source = source()
        val target = target()
        source.linkTo(target, "moduleX")

        val entry = publish(source, config()).output?.targets?.single()

        assertNotNull(entry) {
            assertEquals(target.project.name, it.project)
            assertEquals(target.branch.name, it.branch)
            assertEquals(target.name, it.build)
        }
    }

    @Test
    fun `Only the links matching the filters are promoted`() {
        val source = source()
        val matching = target()
        val otherQualifier = target()
        val otherProject = target()
        source.linkTo(matching, "moduleX")
        source.linkTo(otherQualifier, "other")
        source.linkTo(otherProject, "moduleY")

        val result = publish(
            source,
            config(project = "${matching.project.name}|${otherQualifier.project.name}", qualifier = "module.*"),
        )

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals(listOf(matching.name), result.output?.targets?.map { it.build })
        assertEquals(0, runs(otherQualifier))
        assertEquals(0, runs(otherProject))
    }

    @Test
    fun `No matching link is a success without any target`() {
        val result = publish(source(), config())

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals(emptyList(), result.output?.targets)
    }

    @Test
    fun `An event without a build ends in error`() {
        val result = channel.publish(
            recordId = "1",
            config = config(),
            event = eventFactory.newProject(project()),
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals("The event has no build to read the links from.", result.message)
    }

    @Test
    fun `A build already promoted is skipped and a re-run creates no second run`() {
        val source = source()
        val target = target()
        source.linkTo(target)

        assertEquals(
            YontrackDependenciesPromotionNotificationChannelTargetStatus.PROMOTED,
            publish(source, config()).output?.targets?.single()?.status,
        )

        val rerun = publish(source, config())

        assertEquals(NotificationResultType.OK, rerun.type)
        assertEquals(1, runs(target), "No second run")
        val entry = rerun.output?.targets?.single()
        assertEquals(YontrackDependenciesPromotionNotificationChannelTargetStatus.SKIPPED, entry?.status)
        assertNull(entry?.runId)
        assertEquals("Already promoted to $GOLD", entry?.message)
    }

    @Test
    fun `A failed target does not stop the others and the result lists every failure`() {
        val source = source()
        val first = target(withLevel = false)
        val promoted = target()
        val second = target(withLevel = false)
        source.linkTo(first)
        source.linkTo(promoted)
        source.linkTo(second)

        val result = publish(source, config())

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(1, runs(promoted), "The other target is promoted")
        val message = assertNotNull(result.message)
        listOf(first, second).forEach { failed ->
            assertTrue(
                "${failed.project.name}/${failed.branch.name}/${failed.name}: Promotion level not found: $GOLD" in message,
                "The failure of ${failed.name} is listed in: $message",
            )
        }
        assertFalse(promoted.name in message, "The promoted target is not listed")
        assertEquals(
            mapOf(
                first.name to YontrackDependenciesPromotionNotificationChannelTargetStatus.FAILED,
                promoted.name to YontrackDependenciesPromotionNotificationChannelTargetStatus.PROMOTED,
                second.name to YontrackDependenciesPromotionNotificationChannelTargetStatus.FAILED,
            ),
            result.output?.targets?.associate { it.build to it.status },
        )
    }

    @Test
    fun `A promotion refused by a check fails the target`() {
        val source = source()
        val refused = project().branch().let { branch ->
            setProperty(
                branch,
                PreviousPromotionConditionPropertyType::class.java,
                PreviousPromotionConditionProperty(previousPromotionRequired = true),
            )
            branch.promotionLevel()
            branch.promotionLevel(GOLD)
            branch.build()
        }
        val promoted = target()
        source.linkTo(refused)
        source.linkTo(promoted)

        val result = publish(source, config())

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(0, runs(refused))
        assertEquals(1, runs(promoted))
        val entry = result.output?.targets?.single { it.build == refused.name }
        assertEquals(YontrackDependenciesPromotionNotificationChannelTargetStatus.FAILED, entry?.status)
        assertFalse(entry?.message.isNullOrBlank(), "The reason of the refusal is set")
    }

    @Test
    fun `The output is saved after each target`() {
        val source = source()
        source.linkTo(target())
        source.linkTo(target())
        val progress = mutableListOf<Int>()

        publish(source, config()) { progress += it.targets.size }

        assertEquals(listOf(1, 2), progress)
    }

    @Test
    fun `Description and fields are rendered from the event and set on every run`() {
        val source = source()
        val first = target()
        val second = target()
        source.linkTo(first)
        source.linkTo(second)

        channel.publish(
            recordId = "1",
            config = config(
                fields = listOf(
                    YontrackPromotionNotificationChannelConfigField(name = "reason", value = $$"Promoted with ${build}"),
                ),
            ),
            event = eventFactory.newBuild(source),
            context = emptyMap(),
            template = $$"Promotion by ${build}",
        ) { it }

        listOf(first, second).forEach { target ->
            val run = structureService.getPromotionRunsForBuildAndPromotionLevel(target, level(target)).single()
            assertEquals("Promotion by ${source.name}", run.description)
            assertEquals(
                "Promoted with ${source.name}",
                run.fieldValues.single { it.name == "reason" }.value?.asText(),
            )
        }
    }

    @Test
    fun `Waiting is asynchronous when some runs have been created`() {
        val source = source()
        source.linkTo(target())

        assertEquals(NotificationResultType.ASYNC, publish(source, config(waitForPromotion = true)).type)
    }

    @Test
    fun `Skipped builds are not waited for`() {
        val source = source()
        val target = target()
        source.linkTo(target)
        target.promote(level(target))
        pendingSubscription(level(target))

        val result = publish(source, config(waitForPromotion = true))

        assertEquals(NotificationResultType.OK, result.type)
    }

    @Test
    fun `Waiting succeeds when the notifications of the created runs are successful`() {
        val source = source()
        val target = target()
        source.linkTo(target)
        successfulSubscription(level(target))

        assertEquals(NotificationResultType.OK, actualResult(source, config(waitForPromotion = true)).type)
    }

    @Test
    fun `Waiting goes on while a notification is not there yet`() {
        val source = source()
        val target = target()
        source.linkTo(target)
        pendingSubscription(level(target))

        assertEquals(NotificationResultType.ONGOING, actualResult(source, config(waitForPromotion = true)).type)
    }

    @Test
    fun `Waiting ends in error on a timeout`() {
        val source = source()
        val target = target()
        source.linkTo(target)
        pendingSubscription(level(target))

        val config = config(waitForPromotion = true, waitForPromotionTimeout = Duration.ofSeconds(1))
        val published = publish(source, config)
        Thread.sleep(1_100)
        val result = assertNotNull(channel.getNotificationResult(record(config, published)))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(
            "Timeout" in assertNotNull(result.message),
            "The timeout is reported: ${result.message}",
        )
    }

    @Test
    fun `Waiting ends in error when a notification failed`() {
        val source = source()
        val target = target()
        source.linkTo(target)
        failingSubscription(level(target))

        val result = actualResult(source, config(waitForPromotion = true))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(
            target.name in assertNotNull(result.message),
            "The target is listed: ${result.message}",
        )
    }

    @Test
    fun `A failed notification is reported once all the others have finished`() {
        val source = source()
        val failing = target()
        val pending = target()
        source.linkTo(failing)
        source.linkTo(pending)
        failingSubscription(level(failing))
        pendingSubscription(level(pending))

        val config = config(waitForPromotion = true)
        val published = publish(source, config)
        assertEquals(
            NotificationResultType.ONGOING,
            channel.getNotificationResult(record(config, published))?.type,
            "The failure waits for the other notification",
        )

        // The other notification is now over, because of its timeout
        Thread.sleep(1_100)
        val result = channel.getNotificationResult(
            record(config.copy(waitForPromotionTimeout = Duration.ofSeconds(1)), published)
        )

        assertEquals(NotificationResultType.ERROR, result?.type)
        val message = assertNotNull(result?.message)
        listOf(failing, pending).forEach { target ->
            assertTrue(target.name in message, "${target.name} is listed in: $message")
        }
    }

    @Test
    fun `A target which failed to promote still ends in error once the wait is over`() {
        val source = source()
        val failed = target(withLevel = false)
        val promoted = target()
        source.linkTo(failed)
        source.linkTo(promoted)
        successfulSubscription(level(promoted))

        val result = actualResult(source, config(waitForPromotion = true))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(
            "Promotion level not found: $GOLD" in assertNotNull(result.message),
            "The failure is listed: ${result.message}",
        )
    }

    @Test
    fun `A finished record keeps its result`() {
        val source = source()
        source.linkTo(target(withLevel = false))
        val config = config()
        val published = publish(source, config)

        val result = channel.getNotificationResult(record(config, published))

        assertEquals(NotificationResultType.ERROR, result?.type)
        assertEquals(published.message, result?.message)
    }

    @Test
    fun `A record without any output keeps its result`() {
        val config = config()
        val result = channel.getNotificationResult(
            record(config, NotificationResult.error("The event has no build to read the links from.")).let {
                it.copy(result = it.result.copy(output = NullNode.instance))
            }
        )

        assertEquals(NotificationResultType.ERROR, result?.type)
        assertNull(result?.output)
    }

    @Test
    fun `Invalid configurations are rejected`() {
        listOf(
            mapOf("promotion" to " "),
            mapOf("promotion" to GOLD, "project" to "("),
            mapOf("promotion" to GOLD, "qualifier" to "["),
        ).forEach { config ->
            assertFalse(channel.validate(config.asJson()).isOk(), "$config is rejected")
        }
    }

    @Test
    fun `An empty qualifier is a valid configuration kept by a merge`() {
        val merged = channel.mergeConfig(config(qualifier = "module.*"), mapOf("qualifier" to "").asJson())

        assertEquals("", merged.qualifier)
    }

    private fun actualResult(
        source: Build,
        config: YontrackDependenciesPromotionNotificationChannelConfig,
    ): NotificationResult<YontrackDependenciesPromotionNotificationChannelOutput> {
        val published = publish(source, config)
        assertEquals(NotificationResultType.ASYNC, published.type)
        return assertNotNull(channel.getNotificationResult(record(config, published)))
    }

    private fun record(
        config: YontrackDependenciesPromotionNotificationChannelConfig,
        result: NotificationResult<YontrackDependenciesPromotionNotificationChannelOutput>,
    ) = NotificationRecord(
        id = uid("r"),
        source = null,
        timestamp = Time.now,
        channel = channel.type,
        channelConfig = config.asJson(),
        event = emptyMap<String, String>().asJson(),
        result = NotificationRecordResult(
            type = result.type,
            message = result.message,
            output = result.output?.asJson(),
        ),
    )

    private fun publish(
        source: Build,
        config: YontrackDependenciesPromotionNotificationChannelConfig,
        progress: (YontrackDependenciesPromotionNotificationChannelOutput) -> Unit = {},
    ) = channel.publish(
        recordId = "1",
        config = config,
        event = eventFactory.newBuild(source),
        context = emptyMap(),
        template = null,
    ) {
        progress(it)
        it
    }

    private fun config(
        project: String? = null,
        qualifier: String? = null,
        fields: List<YontrackPromotionNotificationChannelConfigField> = emptyList(),
        waitForPromotion: Boolean = false,
        waitForPromotionTimeout: Duration = Duration.ofHours(1),
    ) = YontrackDependenciesPromotionNotificationChannelConfig(
        project = project,
        qualifier = qualifier,
        promotion = GOLD,
        fields = fields,
        waitForPromotion = waitForPromotion,
        waitForPromotionTimeout = waitForPromotionTimeout,
    )

    private fun source(): Build = project().branch().build()

    private fun target(withLevel: Boolean = true): Build {
        val branch = project().branch()
        if (withLevel) branch.promotionLevel(GOLD)
        return branch.build()
    }

    private fun level(build: Build): PromotionLevel =
        assertNotNull(findLevel(build), "The promotion level exists")

    private fun findLevel(build: Build): PromotionLevel? =
        structureService.findPromotionLevelByName(build.project.name, build.branch.name, GOLD).getOrNull()

    private fun runs(build: Build): Int =
        findLevel(build)
            ?.let { structureService.getPromotionRunsForBuildAndPromotionLevel(build, it).size }
            ?: 0

    private fun successfulSubscription(level: PromotionLevel) {
        eventSubscriptionService.subscribe(
            name = uid("s"),
            projectEntity = level,
            channel = mockNotificationChannel,
            channelConfig = MockNotificationChannelConfig(target = uid("t")),
            keywords = null,
            origin = "test",
            contentTemplate = null,
            eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
        )
    }

    /**
     * Notification which ends in error, because the promotion level it targets does not exist.
     */
    private fun failingSubscription(level: PromotionLevel) {
        eventSubscriptionService.subscribe(
            name = uid("s"),
            projectEntity = level,
            channel = yontrackPromotionNotificationChannel,
            channelConfig = YontrackPromotionNotificationChannelConfig(promotion = "not-existing"),
            keywords = null,
            origin = "test",
            contentTemplate = null,
            eventTypes = arrayOf(EventFactory.NEW_PROMOTION_RUN),
        )
    }

    /**
     * Subscription which is never triggered by a promotion: its notification record never comes.
     */
    private fun pendingSubscription(level: PromotionLevel) {
        eventSubscriptionService.subscribe(
            name = uid("s"),
            projectEntity = level,
            channel = mockNotificationChannel,
            channelConfig = MockNotificationChannelConfig(target = uid("t")),
            keywords = null,
            origin = "test",
            contentTemplate = null,
            eventTypes = arrayOf(EventFactory.NEW_VALIDATION_RUN),
        )
    }

    private companion object {
        const val GOLD = "GOLD"
    }
}
