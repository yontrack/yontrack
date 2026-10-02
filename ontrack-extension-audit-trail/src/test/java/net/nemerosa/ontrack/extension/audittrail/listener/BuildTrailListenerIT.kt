package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Entries of the creation and edition of a build.
 */
class BuildTrailListenerIT : AbstractTrailListenerITSupport() {

    @Test
    fun `Creating a build opens its trail with build created, the build's own signature being claimed`() {
        asAdmin {
            project {
                branch {
                    val claimedTime = LocalDateTime.of(2026, 9, 30, 14, 5, 7, 123_456_789)
                    val (build, actor) = asCi {
                        structureService.newBuild(
                            Build.of(
                                this,
                                NameDescription.nd("1.0.0", "First release"),
                                Signature.of(claimedTime, "jenkins"),
                            )
                        )
                    }
                    build.trail().assertSingleEntry(
                        TrailEntryTypes.BUILD_CREATED,
                        mapOf(
                            "build" to build.ref(),
                            "description" to "First release",
                            "claimed" to claimed(claimedTime, "jenkins"),
                        ),
                        actor,
                    )
                }
            }
        }
    }

    @Test
    fun `Editing a build writes build updated with the old and new values, renames and back-dating visible`() {
        asAdmin {
            project {
                branch {
                    val created = LocalDateTime.of(2026, 9, 30, 14, 5, 7, 0)
                    val build = structureService.newBuild(
                        Build.of(this, NameDescription.nd("1.0.0", "First release"), Signature.of(created, "jenkins"))
                    )
                    val backDated = LocalDateTime.of(2026, 9, 1, 8, 0, 0, 450_000_000)
                    val (_, actor) = asCi {
                        structureService.saveBuild(
                            Build(
                                id = build.id,
                                name = "1.0.1",
                                description = null,
                                signature = Signature.of(backDated, "someone-else"),
                                branch = build.branch,
                            )
                        )
                    }
                    build.trailAfter(1).assertSingleEntry(
                        TrailEntryTypes.BUILD_UPDATED,
                        mapOf(
                            "old" to mapOf(
                                "name" to "1.0.0",
                                "description" to "First release",
                                "creation" to claimed(created, "jenkins")["time"],
                                "creator" to "jenkins",
                            ),
                            "new" to mapOf(
                                "name" to "1.0.1",
                                "creation" to "2026-09-01T08:00:00.450Z",
                                "creator" to "someone-else",
                            ),
                        ),
                        actor,
                    )
                }
            }
        }
    }

    @Test
    fun `Setting a property on a build writes property set with its type and canonical value`() {
        asAdmin {
            project {
                branch {
                    build {
                        val (_, actor) = asCi {
                            propertyService.editProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("v1"))
                        }
                        trailAfter(1).assertSingleEntry(
                            TrailEntryTypes.PROPERTY_SET,
                            mapOf(
                                "propertyType" to "net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType",
                                "value" to mapOf("value" to "v1"),
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting a property of a build writes property deleted with its type`() {
        asAdmin {
            project {
                branch {
                    build {
                        propertyService.editProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("v1"))
                        val seq = lastSeq()
                        val (_, actor) = asCi {
                            propertyService.deleteProperty(this, TestSimplePropertyType::class.java)
                        }
                        trailAfter(seq).assertSingleEntry(
                            TrailEntryTypes.PROPERTY_DELETED,
                            mapOf(
                                "propertyType" to "net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType",
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A property set on anything but a build writes no entry`() {
        asAdmin {
            project {
                branch {
                    val build = build()
                    val seq = build.lastSeq()
                    propertyService.editProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("v1"))
                    propertyService.editProperty(project, TestSimplePropertyType::class.java, TestSimpleProperty("v1"))
                    assertEquals(seq, build.lastSeq(), "No entry written")
                }
            }
        }
    }

    @Test
    fun `Linking a build to another writes link added with the target build and the qualifier`() {
        asAdmin {
            val target = project<Build> { branch<Build> { build() } }
            project {
                branch {
                    build {
                        val (_, actor) = asCi {
                            structureService.createBuildLink(this, target, "dependency")
                        }
                        trailAfter(1).assertSingleEntry(
                            TrailEntryTypes.LINK_ADDED,
                            mapOf(
                                "target" to target.ref(),
                                "qualifier" to "dependency",
                            ),
                            actor,
                        )
                        assertEquals(1, target.lastSeq(), "Nothing written in the trail of the target")
                    }
                }
            }
        }
    }

    @Test
    fun `Unlinking a build from another writes link removed with the target build and the qualifier`() {
        asAdmin {
            val target = project<Build> { branch<Build> { build() } }
            project {
                branch {
                    build {
                        structureService.createBuildLink(this, target, "")
                        val seq = lastSeq()
                        val (_, actor) = asCi {
                            structureService.deleteBuildLink(this, target, "")
                        }
                        trailAfter(seq).assertSingleEntry(
                            TrailEntryTypes.LINK_REMOVED,
                            mapOf(
                                "target" to target.ref(),
                                "qualifier" to "",
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Setting the run info of a build writes run info set`() {
        asAdmin {
            project {
                branch {
                    build {
                        val (_, actor) = asCi {
                            runInfoService.setRunInfo(
                                this,
                                RunInfoInput(
                                    sourceType = "github",
                                    sourceUri = "https://github.com/yontrack/yontrack/actions/runs/1",
                                    triggerType = "scm",
                                    triggerData = "abc1234",
                                    runTime = 42,
                                )
                            )
                        }
                        trailAfter(1).assertSingleEntry(
                            TrailEntryTypes.RUN_INFO_SET,
                            mapOf(
                                "runnable" to mapOf("type" to "build"),
                                "runInfo" to mapOf(
                                    "sourceType" to "github",
                                    "sourceUri" to "https://github.com/yontrack/yontrack/actions/runs/1",
                                    "triggerType" to "scm",
                                    "triggerData" to "abc1234",
                                    "runTime" to 42,
                                ),
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Setting and deleting the run info of a validation run writes run info set and deleted in the trail of its build`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    build {
                        val run = validate(vs)
                        val seq = lastSeq()
                        val (_, actor) = asCi {
                            runInfoService.setRunInfo(run, RunInfoInput(runTime = 12))
                            runInfoService.deleteRunInfo(run)
                        }
                        val runnable = mapOf(
                            "type" to "validation_run",
                            "validationStamp" to mapOf("id" to vs.id(), "name" to "unit-tests"),
                            "validationRun" to mapOf("id" to run.id(), "order" to run.runOrder),
                        )
                        val (set, deleted) = trailAfter(seq).apply { assertEquals(2, size) }
                        set.assertEntry(
                            TrailEntryTypes.RUN_INFO_SET,
                            mapOf("runnable" to runnable, "runInfo" to mapOf("runTime" to 12)),
                            actor,
                        )
                        deleted.assertEntry(TrailEntryTypes.RUN_INFO_DELETED, mapOf("runnable" to runnable), actor)
                    }
                }
            }
        }
    }
}
