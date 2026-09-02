package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.RunInfo
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import kotlin.test.*

@AsAdminTest
class RunInfoServiceIT : AbstractDSLTestSupport() {

    @Test
    fun `Needs authorization to add run info to a build`() {
        val build = doCreateBuild()
        assertFailsWith(AccessDeniedException::class) {
            asUserWithView(build).execute {
                runInfoService.setRunInfo(build, RunInfoInput(runTime = 30))
            }
        }
        val info: RunInfo = asUser().withProjectFunction(build, BuildCreate::class.java).call {
            runInfoService.setRunInfo(build, RunInfoInput(runTime = 30))
        }
        assertEquals(30, info.runTime)
        // Deletion
        assertFailsWith(AccessDeniedException::class) {
            asUserWithView(build).execute {
                runInfoService.deleteRunInfo(build)
            }
        }
        val ack: Ack = asUser().withProjectFunction(build, ProjectEdit::class.java).call {
            runInfoService.deleteRunInfo(build)
        }
        assertTrue(ack.success)
    }

    @Test
    fun `Needs authorization to add run info to a validation run`() {
        val vs = doCreateValidationStamp()
        val build = doCreateBuild(vs.branch, nameDescription())
        val run = doValidateBuild(build, vs, ValidationRunStatusID.STATUS_PASSED)
        assertFailsWith(AccessDeniedException::class) {
            asUserWithView(run).execute {
                runInfoService.setRunInfo(run, RunInfoInput(runTime = 30))
            }
        }
        val info: RunInfo = asUser().withProjectFunction(run, ValidationRunCreate::class.java).call {
            runInfoService.setRunInfo(run, RunInfoInput(runTime = 30))
        }
        assertEquals(30, info.runTime)
    }

    @Test
    fun `No run info by default for a build`() {
        val build = doCreateBuild()
        val info = asUserWithView(build).call { runInfoService.getRunInfo(build) }
        assertNull(info, "No run info")
    }

    /**
     * The Go CLI's `RunInfo.RunTime` is a plain `int` with no `omitempty`, so every caller that
     * does not measure a duration sends `runTime: 0` rather than omitting the field - there is no
     * way for it to express "not measured". Recording that verbatim would mean an untimed run
     * reads as "ran in 0 seconds" and, worse, emits a 0.0 sample into
     * `ontrack_run_<type>_time_seconds` - which is exactly what `ci.yml` does at every validation
     * call site that has no recorded start to measure from.
     *
     * Zero is therefore normalised to no run time at all. Nothing is lost: a genuine zero-second
     * duration is not a measurement anyone can act on, and the UI already treats it as absent.
     */
    @Test
    fun `A run time of zero records no run time at all`() {
        val build = doCreateBuild()
        val info = asUser().withProjectFunction(build, BuildCreate::class.java).call {
            runInfoService.setRunInfo(
                build,
                RunInfoInput(
                    sourceType = "github-workflow",
                    sourceUri = "https://github.com/yontrack/yontrack/actions/runs/1234",
                    triggerType = "push",
                    triggerData = "cafebabe",
                    runTime = 0,
                )
            )
        }
        assertNull(info.runTime, "Zero run time is not recorded")
        // ... and it stays absent when read back
        val reloaded = asUserWithView(build).call { runInfoService.getRunInfo(build) }
        assertNotNull(reloaded, "Run info is recorded")
        assertNull(reloaded.runTime, "Zero run time is not recorded")
        assertEquals("https://github.com/yontrack/yontrack/actions/runs/1234", reloaded.sourceUri)
    }

    /**
     * The counterpart to the test above: a real duration is untouched.
     */
    @Test
    fun `A non-zero run time is recorded as it is`() {
        val build = doCreateBuild()
        val info = asUser().withProjectFunction(build, BuildCreate::class.java).call {
            runInfoService.setRunInfo(build, RunInfoInput(runTime = 1))
        }
        assertEquals(1, info.runTime)
    }

    @Test
    fun `Sets and gets the run info for a build`() {
        val build = doCreateBuild()
        val info = asUser().withProjectFunction(build, BuildCreate::class.java).call {
            runInfoService.setRunInfo(
                build,
                RunInfoInput(
                    sourceType = "jenkins",
                    sourceUri = "http://jenkins/job/build/1",
                    triggerType = "scm",
                    triggerData = "1234cde",
                    runTime = 26
                )
            )
        }
        assertTrue(info.id != 0)
        assertEquals("jenkins", info.sourceType)
        assertEquals("http://jenkins/job/build/1", info.sourceUri)
        assertEquals("scm", info.triggerType)
        assertEquals("1234cde", info.triggerData)
        assertEquals(26, info.runTime)
    }

    @Test
    fun `Sets and deletes the run info for a build`() {
        val build = doCreateBuild()
        asUser().withProjectFunction(build, BuildCreate::class.java).call {
            runInfoService.setRunInfo(
                build,
                RunInfoInput(
                    sourceType = "jenkins",
                    sourceUri = "http://jenkins/job/build/1",
                    triggerType = "scm",
                    triggerData = "1234cde",
                    runTime = 26
                )
            )
        }
        // Deletion
        asUser().withProjectFunction(build, ProjectEdit::class.java).execute {
            runInfoService.deleteRunInfo(build)
        }
        val newInfo = asUserWithView(build).call { runInfoService.getRunInfo(build) }
        assertNull(newInfo, "No run info")
    }

    @Test
    fun `Sets and deletes the run info for a validation run`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validate(vs)
                    runInfoService.setRunInfo(
                        run,
                        RunInfoInput(
                            sourceType = "jenkins",
                            sourceUri = "http://jenkins/job/build/1",
                            triggerType = "scm",
                            triggerData = "1234cde",
                            runTime = 26
                        )
                    )
                    assertNotNull(runInfoService.getRunInfo(run), "Validatio run run info set") {
                        assertEquals("jenkins", it.sourceType)
                        assertEquals("http://jenkins/job/build/1", it.sourceUri)
                        assertEquals("scm", it.triggerType)
                        assertEquals("1234cde", it.triggerData)
                        assertEquals(26, it.runTime)
                    }
                }
            }
        }
    }

}