package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.extension.api.support.TestConfiguration
import net.nemerosa.ontrack.extension.api.support.TestPropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.structure.RunInfo
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class EventFactoryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var eventTemplatingService: EventTemplatingService

    @Autowired
    private lateinit var testPropertyType: TestPropertyType


    @Test
    fun `Rendering of all the default events`() {

        val oldTemplatingErrors = ontrackConfigProperties.templating.errors
        try {

            ontrackConfigProperties.templating.errors = OntrackConfigProperties.TemplatingErrors.LOGGING_STACK

            asAdmin {

                // NEW_PROJECT
                project {
                    val event = eventFactory.newProject(project)
                    assertEquals(
                        "New project $name.",
                        eventTemplatingService.renderEvent(event, emptyMap())
                    )
                }

                // UPDATE_PROJECT
                project {
                    val event = eventFactory.updateProject(project)
                    assertEquals(
                        "Project $name has been updated.",
                        eventTemplatingService.renderEvent(event, emptyMap())
                    )
                }

                // ENABLE_PROJECT
                project {
                    val event = eventFactory.enableProject(project)
                    assertEquals(
                        "Project $name has been enabled.",
                        eventTemplatingService.renderEvent(event, emptyMap())
                    )
                }

                // DISABLE_PROJECT
                project {
                    val event = eventFactory.disableProject(project)
                    assertEquals(
                        "Project $name has been disabled.",
                        eventTemplatingService.renderEvent(event, emptyMap())
                    )
                }

                // DELETE_PROJECT
                project {
                    val event = eventFactory.deleteProject(project)
                    assertEquals(
                        "Project $name has been deleted.",
                        eventTemplatingService.renderEvent(event, emptyMap())
                    )
                }

                // NEW_BRANCH
                project {
                    branch {
                        val event = eventFactory.newBranch(this)
                        assertEquals(
                            "New branch $name for project ${project.name}.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // UPDATE_BRANCH
                project {
                    branch {
                        val event = eventFactory.updateBranch(this)
                        assertEquals(
                            "Branch $name in ${project.name} has been updated.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // ENABLE_BRANCH
                project {
                    branch {
                        val event = eventFactory.enableBranch(this)
                        assertEquals(
                            "Branch $name in ${project.name} has been enabled.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // DISABLE_BRANCH
                project {
                    branch {
                        val event = eventFactory.disableBranch(this)
                        assertEquals(
                            "Branch $name in ${project.name} has been disabled.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // DELETE_BRANCH
                project {
                    branch {
                        val event = eventFactory.deleteBranch(this)
                        assertEquals(
                            "Branch $name has been deleted from ${project.name}.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // NEW_BUILD
                project {
                    branch {
                        build {
                            val event = eventFactory.newBuild(this)
                            assertEquals(
                                "New build $name for branch ${branch.name} in ${project.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // UPDATE_BUILD
                project {
                    branch {
                        build {
                            val event = eventFactory.updateBuild(this)
                            assertEquals(
                                "Build $name for branch ${branch.name} in ${project.name} has been updated.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // DELETE_BUILD
                project {
                    branch {
                        build {
                            val event = eventFactory.deleteBuild(this)
                            assertEquals(
                                "Build $name for branch ${branch.name} in ${project.name} has been deleted.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // NEW_PROMOTION_LEVEL
                project {
                    branch {
                        promotionLevel {
                            val event = eventFactory.newPromotionLevel(this)
                            assertEquals(
                                "New promotion level $name for branch ${branch.name} in ${project.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // IMAGE_PROMOTION_LEVEL
                project {
                    branch {
                        promotionLevel {
                            val event = eventFactory.imagePromotionLevel(this)
                            assertEquals(
                                "Image for promotion level $name for branch ${branch.name} in ${project.name} has changed.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // UPDATE_PROMOTION_LEVEL
                project {
                    branch {
                        promotionLevel {
                            val event = eventFactory.updatePromotionLevel(this)
                            assertEquals(
                                "Promotion level $name for branch ${branch.name} in ${project.name} has changed.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // DELETE_PROMOTION_LEVEL
                project {
                    branch {
                        promotionLevel {
                            val event = eventFactory.deletePromotionLevel(this)
                            assertEquals(
                                "Promotion level $name for branch ${branch.name} in ${project.name} has been deleted.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // REORDER_PROMOTION_LEVEL
                project {
                    branch {
                        val event = eventFactory.reorderPromotionLevels(this)
                        assertEquals(
                            "Promotion levels for branch $name in ${project.name} have been reordered.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // NEW_VALIDATION_STAMP
                project {
                    branch {
                        val vs = validationStamp()
                        val event = eventFactory.newValidationStamp(vs)
                        assertEquals(
                            "New validation stamp ${vs.name} for branch ${vs.branch.name} in ${vs.project.name}.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // IMAGE_VALIDATION_STAMP
                project {
                    branch {
                        val vs = validationStamp()
                        val event = eventFactory.imageValidationStamp(vs)
                        assertEquals(
                            "Image for validation stamp ${vs.name} for branch ${vs.branch.name} in ${vs.project.name} has changed.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // UPDATE_VALIDATION_STAMP
                project {
                    branch {
                        val vs = validationStamp()
                        val event = eventFactory.updateValidationStamp(vs)
                        assertEquals(
                            "Validation stamp ${vs.name} for branch ${vs.branch.name} in ${vs.project.name} has been updated.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // DELETE_VALIDATION_STAMP
                project {
                    branch {
                        val vs = validationStamp()
                        val event = eventFactory.deleteValidationStamp(vs)
                        assertEquals(
                            "Validation stamp ${vs.name} for branch ${vs.branch.name} in ${vs.project.name} has been deleted.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // REORDER_VALIDATION_STAMP
                project {
                    branch {
                        val event = eventFactory.reorderValidationStamps(this)
                        assertEquals(
                            "Validation stamps for branch $name in ${project.name} have been reordered.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // NEW_PROMOTION_RUN
                project {
                    branch {
                        val pl = promotionLevel()
                        build {
                            val run = promote(pl)
                            val event = eventFactory.newPromotionRun(run)
                            assertEquals(
                                "Build $name has been promoted to ${pl.name} for branch ${branch.name} in ${project.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // DELETE_PROMOTION_RUN
                project {
                    branch {
                        val pl = promotionLevel()
                        build {
                            val run = promote(pl)
                            val event = eventFactory.deletePromotionRun(run)
                            assertEquals(
                                "Promotion ${pl.name} of build $name has been deleted for branch ${branch.name} in ${project.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // NEW_VALIDATION_RUN
                project {
                    branch {
                        val vs = validationStamp()
                        build {
                            val run = validate(vs)
                            val event = eventFactory.newValidationRun(run)
                            assertEquals(
                                "Build $name has run for the ${vs.name} with status ${run.lastStatus.statusID.name} in branch ${branch.name} in ${project.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // NEW_VALIDATION_RUN_STATUS
                project {
                    branch {
                        val vs = validationStamp()
                        build {
                            val run = validate(vs)
                            val event = eventFactory.newValidationRunStatus(run)
                            assertEquals(
                                "Status for the ${vs.name} validation #${run.runOrder} for build $name in branch ${branch.name} of ${project.name} has changed to ${run.lastStatus.statusID.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // UPDATE_VALIDATION_RUN_STATUS_COMMENT
                project {
                    branch {
                        val vs = validationStamp()
                        build {
                            val run = validate(vs)
                            val event = eventFactory.updateValidationRunStatusComment(run)
                            assertEquals(
                                "A status message for the ${vs.name} validation #${run.runOrder} for build $name in branch ${branch.name} of ${project.name} has changed.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // NEW_BUILD_LINK & DELETE_BUILD_LINK
                project {
                    branch {
                        build {
                            val target = doCreateBuild()
                            assertEquals(
                                "Build $name for branch ${branch.name} in ${project.name} has been linked to build ${target.name} for branch ${target.branch.name} in ${target.project.name}.",
                                eventTemplatingService.renderEvent(eventFactory.newBuildLink(this, target, "dep"), emptyMap())
                            )
                            assertEquals(
                                "Build $name for branch ${branch.name} in ${project.name} is no longer linked to build ${target.name} for branch ${target.branch.name} in ${target.project.name}.",
                                eventTemplatingService.renderEvent(eventFactory.deleteBuildLink(this, target, "dep"), emptyMap())
                            )
                        }
                    }
                }

                // UPDATE_RUN_INFO & DELETE_RUN_INFO
                project {
                    branch {
                        val vs = validationStamp()
                        build {
                            val runInfo = RunInfo(1, "github", null, null, null, 10, null)
                            assertEquals(
                                "Run info of build ${project.name}/${branch.name}/$name has been set.",
                                eventTemplatingService.renderEvent(eventFactory.updateRunInfo(this, runInfo), emptyMap())
                            )
                            assertEquals(
                                "Run info of build ${project.name}/${branch.name}/$name has been deleted.",
                                eventTemplatingService.renderEvent(eventFactory.deleteRunInfo(this), emptyMap())
                            )
                            val run = validate(vs)
                            assertEquals(
                                "Run info of validation run ${vs.name}#1 for ${project.name}/${branch.name}/$name has been set.",
                                eventTemplatingService.renderEvent(eventFactory.updateRunInfo(run, runInfo), emptyMap())
                            )
                        }
                    }
                }

                // DELETE_VALIDATION_RUN
                project {
                    branch {
                        val vs = validationStamp()
                        build {
                            val run = validate(vs)
                            val event = eventFactory.deleteValidationRun(run)
                            assertEquals(
                                "Validation run #1 for ${vs.name} of build $name has been deleted for branch ${branch.name} in ${project.name}.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // UPDATE_VALIDATION_RUN_DATA
                project {
                    branch {
                        val vs = validationStamp()
                        build {
                            val run = validate(vs)
                            val event = eventFactory.updateValidationRunData(run)
                            assertEquals(
                                "Data of the ${vs.name} validation #1 for build $name in branch ${branch.name} of ${project.name} has changed.",
                                eventTemplatingService.renderEvent(event, emptyMap())
                            )
                        }
                    }
                }

                // PROPERTY_CHANGE
                project {
                    branch {
                        val event = eventFactory.propertyChange(this, testPropertyType)
                        assertEquals(
                            "Configuration value property has changed for branch ${project.name}/$name.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                // PROPERTY_DELETE
                project {
                    branch {
                        val event = eventFactory.propertyDelete(this, testPropertyType)
                        assertEquals(
                            "Configuration value property has been removed from branch ${project.name}/$name.",
                            eventTemplatingService.renderEvent(event, emptyMap())
                        )
                    }
                }

                val configuration = TestConfiguration(
                    name = uid("conf-"),
                    user = "some-user",
                    password = "some-password",
                )

                // NEW_CONFIGURATION
                val eventNewConfiguration = eventFactory.newConfiguration(configuration)
                assertEquals(
                    "${configuration.name} configuration has been created.",
                    eventTemplatingService.renderEvent(eventNewConfiguration, emptyMap())
                )

                // UPDATE_CONFIGURATION
                val eventUpdateConfiguration = eventFactory.updateConfiguration(configuration)
                assertEquals(
                    "${configuration.name} configuration has been updated.",
                    eventTemplatingService.renderEvent(eventUpdateConfiguration, emptyMap())
                )

                // DELETE_CONFIGURATION
                val eventDeleteConfiguration = eventFactory.deleteConfiguration(configuration)
                assertEquals(
                    "${configuration.name} configuration has been deleted.",
                    eventTemplatingService.renderEvent(eventDeleteConfiguration, emptyMap())
                )

            }
        } finally {
            ontrackConfigProperties.templating.errors = oldTemplatingErrors
        }
    }

}