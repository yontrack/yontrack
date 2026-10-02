package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.extension.api.support.TestNumberValidationDataType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.BuildLinkForm
import net.nemerosa.ontrack.model.structure.BuildLinkFormItem
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.ValidationRunService
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.data
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Build mutations which used to change the story of a build without posting any event (#1957).
 */
@AsAdminTest
class BuildMutationEventsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var validationRunService: ValidationRunService

    @Autowired
    private lateinit var testNumberValidationDataType: TestNumberValidationDataType

    @Test
    fun `Adding a build link posts the new_build_link event`() {
        val source = doCreateBuild()
        val target = doCreateBuild()
        structureService.createBuildLink(source, target, "dep")
        val event = eventQueryService.getLastEvent(source, EventFactory.NEW_BUILD_LINK)
        assertNotNull(event, "Link event posted") {
            assertEquals(source.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals(target.id, it.extraEntities[ProjectEntityType.BUILD]?.id)
            assertEquals(target.branch.id, it.extraEntities[ProjectEntityType.BRANCH]?.id)
            assertEquals(target.project.id, it.extraEntities[ProjectEntityType.PROJECT]?.id)
            assertEquals("dep", it.getValue("QUALIFIER"))
        }
    }

    @Test
    fun `Adding an existing build link again posts no event`() {
        val source = doCreateBuild()
        val target = doCreateBuild()
        structureService.createBuildLink(source, target, "dep")
        structureService.createBuildLink(source, target, "dep")
        assertEquals(1, source.events(EventFactory.NEW_BUILD_LINK).size)
    }

    @Test
    fun `Deleting a build link posts the delete_build_link event`() {
        val source = doCreateBuild()
        val target = doCreateBuild()
        structureService.createBuildLink(source, target, "dep")
        structureService.deleteBuildLink(source, target, "dep")
        val event = eventQueryService.getLastEvent(source, EventFactory.DELETE_BUILD_LINK)
        assertNotNull(event, "Link deletion event posted") {
            assertEquals(source.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals(target.id, it.extraEntities[ProjectEntityType.BUILD]?.id)
            assertEquals("dep", it.getValue("QUALIFIER"))
        }
    }

    @Test
    fun `Deleting a missing build link posts no event`() {
        val source = doCreateBuild()
        val target = doCreateBuild()
        structureService.deleteBuildLink(source, target, "dep")
        assertEquals(0, source.events(EventFactory.DELETE_BUILD_LINK).size)
    }

    @Test
    fun `Editing the links of a build posts one event per added or removed link`() {
        val source = doCreateBuild()
        val kept = doCreateBuild()
        val removed = doCreateBuild()
        val added = doCreateBuild()
        structureService.createBuildLink(source, kept, "")
        structureService.createBuildLink(source, removed, "")
        structureService.editBuildLinks(
            source,
            BuildLinkForm(
                false,
                BuildLinkFormItem(kept.project.name, kept.name, ""),
                BuildLinkFormItem(added.project.name, added.name, ""),
            )
        )
        assertEquals(
            listOf(added.id, removed.id, kept.id),
            source.events(EventFactory.NEW_BUILD_LINK).map { it.extraEntities[ProjectEntityType.BUILD]?.id },
            "Added links, most recent first, with no event for the kept link"
        )
        assertEquals(
            listOf(removed.id),
            source.events(EventFactory.DELETE_BUILD_LINK).map { it.extraEntities[ProjectEntityType.BUILD]?.id },
        )
    }

    @Test
    fun `Setting the run info of a build posts the update_run_info event with the new values`() {
        val build = doCreateBuild()
        runInfoService.setRunInfo(
            build,
            RunInfoInput(
                sourceType = "github",
                sourceUri = "https://github.com/yontrack/yontrack/actions/runs/1",
                triggerType = "push",
                triggerData = "main",
                runTime = 42,
            )
        )
        val event = eventQueryService.getLastEvent(build, EventFactory.UPDATE_RUN_INFO)
        assertNotNull(event, "Run info event posted") {
            assertEquals(build.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertNull(it.entities[ProjectEntityType.VALIDATION_RUN])
            assertEquals("build", it.getValue("RUNNABLE_ENTITY_TYPE"))
            assertEquals("github", it.getValue("SOURCE_TYPE"))
            assertEquals("https://github.com/yontrack/yontrack/actions/runs/1", it.getValue("SOURCE_URI"))
            assertEquals("push", it.getValue("TRIGGER_TYPE"))
            assertEquals("main", it.getValue("TRIGGER_DATA"))
            assertEquals("42", it.getValue("RUN_TIME"))
        }
    }

    @Test
    fun `Setting the run info of a validation run posts the update_run_info event for the validation run and its build`() {
        val vs = doCreateValidationStamp()
        val build = doCreateBuild(vs.branch, nameDescription())
        val run = doValidateBuild(build, vs, ValidationRunStatusID.STATUS_PASSED)
        runInfoService.setRunInfo(run, RunInfoInput(runTime = 0, sourceType = "jenkins"))
        val event = eventQueryService.getLastEvent(run, EventFactory.UPDATE_RUN_INFO)
        assertNotNull(event, "Run info event posted") {
            assertEquals(build.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals(vs.id, it.entities[ProjectEntityType.VALIDATION_STAMP]?.id)
            assertEquals(run.id, it.entities[ProjectEntityType.VALIDATION_RUN]?.id)
            assertEquals("validation_run", it.getValue("RUNNABLE_ENTITY_TYPE"))
            assertEquals("jenkins", it.getValue("SOURCE_TYPE"))
            assertNull(it.values["RUN_TIME"], "A run time of zero is stored, and posted, as no run time")
            assertNull(it.values["SOURCE_URI"])
        }
    }

    @Test
    fun `Deleting the run info of a build posts the delete_run_info event`() {
        val build = doCreateBuild()
        runInfoService.setRunInfo(build, RunInfoInput(runTime = 10))
        runInfoService.deleteRunInfo(build)
        val event = eventQueryService.getLastEvent(build, EventFactory.DELETE_RUN_INFO)
        assertNotNull(event, "Run info deletion event posted") {
            assertEquals(build.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals("build", it.getValue("RUNNABLE_ENTITY_TYPE"))
        }
    }

    @Test
    fun `Deleting the run info of a validation run posts the delete_run_info event`() {
        val vs = doCreateValidationStamp()
        val build = doCreateBuild(vs.branch, nameDescription())
        val run = doValidateBuild(build, vs, ValidationRunStatusID.STATUS_PASSED)
        runInfoService.setRunInfo(run, RunInfoInput(runTime = 10))
        runInfoService.deleteRunInfo(run)
        val event = eventQueryService.getLastEvent(run, EventFactory.DELETE_RUN_INFO)
        assertNotNull(event, "Run info deletion event posted") {
            assertEquals(build.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals(run.id, it.entities[ProjectEntityType.VALIDATION_RUN]?.id)
            assertEquals("validation_run", it.getValue("RUNNABLE_ENTITY_TYPE"))
        }
    }

    @Test
    fun `Deleting a missing run info posts no event`() {
        val build = doCreateBuild()
        runInfoService.deleteRunInfo(build)
        assertEquals(0, build.events(EventFactory.DELETE_RUN_INFO).size)
    }

    @Test
    fun `Deleting a validation run posts the delete_validation_run event`() {
        val vs = doCreateValidationStamp()
        val build = doCreateBuild(vs.branch, nameDescription())
        val run = doValidateBuild(build, vs, ValidationRunStatusID.STATUS_FAILED)
        structureService.deleteValidationRun(run)
        val event = eventQueryService.getLastEvent(build, EventFactory.DELETE_VALIDATION_RUN)
        assertNotNull(event, "Validation run deletion event posted") {
            assertEquals(build.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals(vs.id, it.entities[ProjectEntityType.VALIDATION_STAMP]?.id)
            assertNull(it.entities[ProjectEntityType.VALIDATION_RUN], "The deleted run is not referenced")
            assertEquals(run.id(), it.getIntValue("VALIDATION_RUN_ID"))
            assertEquals("1", it.getValue("VALIDATION_RUN_ORDER"))
            assertEquals("FAILED", it.getValue("STATUS"))
        }
    }

    @Test
    fun `Updating the data of a validation run posts the update_validation_run_data event with the new data`() {
        val vs = doCreateValidationStamp()
        val build = doCreateBuild(vs.branch, nameDescription())
        val run = doValidateBuild(build, vs, ValidationRunStatusID.STATUS_PASSED)
        validationRunService.updateValidationRunData(run, testNumberValidationDataType.data(12))
        val event = eventQueryService.getLastEvent(run, EventFactory.UPDATE_VALIDATION_RUN_DATA)
        assertNotNull(event, "Validation run data event posted") {
            assertEquals(build.id, it.entities[ProjectEntityType.BUILD]?.id)
            assertEquals(vs.id, it.entities[ProjectEntityType.VALIDATION_STAMP]?.id)
            assertEquals(run.id, it.entities[ProjectEntityType.VALIDATION_RUN]?.id)
            assertEquals(TestNumberValidationDataType::class.qualifiedName, it.getValue("DATA_TYPE"))
            assertEquals("12", it.getValue("DATA"))
        }
    }

    @Test
    fun `Removing the data of a validation run posts the update_validation_run_data event without data`() {
        val vs = doCreateValidationStamp()
        val build = doCreateBuild(vs.branch, nameDescription())
        val run = doValidateBuild(build, vs, ValidationRunStatusID.STATUS_PASSED)
        validationRunService.updateValidationRunData(run, testNumberValidationDataType.data(12))
        validationRunService.updateValidationRunData(run, null)
        val events = run.events(EventFactory.UPDATE_VALIDATION_RUN_DATA)
        assertEquals(2, events.size)
        val removal = events.first()
        assertEquals(run.id, removal.entities[ProjectEntityType.VALIDATION_RUN]?.id)
        assertNull(removal.values["DATA_TYPE"])
        assertNull(removal.values["DATA"])
    }

    private fun ProjectEntity.events(eventType: EventType) =
        eventQueryService.getEvents(projectEntityType, id, eventType, 0, 100)

}
