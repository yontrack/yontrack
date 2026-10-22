package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.forRecursiveLinks
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.security.BuildConfig
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.BuildEdit
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AsAdminTest
class BuildLinkIT : AbstractDSLTestSupport() {

    @Test
    fun `Adding qualified build links`() {
        asAdmin {
            val source = doCreateBuild()
            val target = doCreateBuild()
            // Default link
            structureService.createBuildLink(source, target, BuildLink.DEFAULT)
            // Other qualifiers
            structureService.createBuildLink(source, target, "dep1")
            structureService.createBuildLink(source, target, "dep2")
            // Gets the list of links
            val links = structureService.getQualifiedBuildsUsedBy(source).pageItems
            assertEquals(3, links.size)
            assertEquals(
                mapOf(
                    "" to target.id(),
                    "dep1" to target.id(),
                    "dep2" to target.id(),
                ),
                links.associate {
                    it.qualifier to it.build.id()
                }
            )
        }
    }

    @Test
    fun `Edition of links - project not found at all`() {
        val source = doCreateBuild()
        asUser().withProjectFunction(source, BuildConfig::class.java).call {
            // Adds the link using a form
            assertFailsWith<ProjectNotFoundException> {
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        false,
                        BuildLinkFormItem(uid("P"), "xxx", "")
                    )
                )
            }
        }
    }

    @Test
    fun `Edition of links - project not authorised`() {
        withNoGrantViewToAll {
            val source = doCreateBuild()
            val target = doCreateBuild()
            asUser().withProjectFunction(source, BuildConfig::class.java).call {
                // Adds the link using a form
                assertFailsWith<ProjectNotFoundException> {
                    structureService.editBuildLinks(
                        source,
                        BuildLinkForm(
                            false,
                            BuildLinkFormItem(target.project.name, target.name, "")
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `Edition of links - build not found`() {
        val source = doCreateBuild()
        val target = doCreateProject()
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target).call {
            // Adds the link using a form
            assertFailsWith<BuildNotFoundException> {
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        false,
                        BuildLinkFormItem(target.name, "xxx", "")
                    )
                )
            }
        }
    }

    @Test
    fun `Edition of links - full rights - adding one link`() {
        val source = doCreateBuild()
        val target1 = doCreateBuild()
        val target2 = doCreateBuild()
        val target3 = doCreateBuild()
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).call {
            structureService.createBuildLink(source, target1, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target2).call {
            structureService.createBuildLink(source, target2, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target2)
            .withView(target3).call {
                // Adds the link using a form
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        false,
                        BuildLinkFormItem(target1.project.name, target1.name, ""), // Existing
                        BuildLinkFormItem(target2.project.name, target2.name, ""), // Existing
                        BuildLinkFormItem(target3.project.name, target3.name, "") // New
                    )
                )
                // Checks all builds are still linked
                assertEquals(
                    setOf(target1.id, target2.id, target3.id),
                    structureService.getQualifiedBuildsUsedBy(source).pageItems.map { it.build.id }.toSet()
                )
            }
    }

    @Test
    fun `Edition of links - full rights - adding and removing`() {
        val source = doCreateBuild()
        val target1 = doCreateBuild()
        val target2 = doCreateBuild()
        val target3 = doCreateBuild()
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).call {
            structureService.createBuildLink(source, target1, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target2).call {
            structureService.createBuildLink(source, target2, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target2)
            .withView(target3).call {
                // Adds the link using a form
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        false,
                        BuildLinkFormItem(target1.project.name, target1.name, ""), // Existing
                        // BuildLinkFormItem(target2.project.name, target2.name, ""), // Removing
                        BuildLinkFormItem(target3.project.name, target3.name, "") // New
                    )
                )
                // Checks all builds are still linked
                assertEquals(
                    setOf(target1.id, target3.id),
                    structureService.getQualifiedBuildsUsedBy(source).pageItems.map { it.build.id }.toSet()
                )
            }
    }

    @Test
    fun `Edition of links - full rights - adding only`() {
        val source = doCreateBuild()
        val target1 = doCreateBuild()
        val target2 = doCreateBuild()
        val target3 = doCreateBuild()
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).call {
            structureService.createBuildLink(source, target1, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target2).call {
            structureService.createBuildLink(source, target2, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target2)
            .withView(target3).call {
                // Adds the link using a form
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        true,
                        BuildLinkFormItem(target3.project.name, target3.name, "") // New
                    )
                )
                // Checks all builds are still linked
                assertEquals(
                    setOf(target1.id, target2.id, target3.id),
                    structureService.getQualifiedBuildsUsedBy(source).pageItems.map { it.build.id }.toSet()
                )
            }
    }

    @Test
    fun `Edition of links - partial rights - adding one link`() {
        withNoGrantViewToAll {
            val source = doCreateBuild()
            val target1 = doCreateBuild()
            val target2 = doCreateBuild()
            val target3 = doCreateBuild()
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).call {
                structureService.createBuildLink(source, target1, BuildLink.DEFAULT)
            }
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target2).call {
                structureService.createBuildLink(source, target2, BuildLink.DEFAULT)
            }
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target3).call {
                // Adds the link using a form
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        false,
                        BuildLinkFormItem(target1.project.name, target1.name, ""), // Existing
                        BuildLinkFormItem(target3.project.name, target3.name, "") // New
                    )
                )
            }
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target2)
                .withView(target3).call {
                    // Checks all builds are still linked
                    assertEquals(
                        setOf(target1.id, target2.id, target3.id),
                        structureService.getQualifiedBuildsUsedBy(source).pageItems.map { it.build.id }.toSet()
                    )
                }
        }
    }

    @Test
    fun `Edition of links - partial rights - adding and removing`() {
        withNoGrantViewToAll {
            val source = doCreateBuild()
            val target1 = doCreateBuild()
            val target2 = doCreateBuild()
            val target3 = doCreateBuild()
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).call {
                structureService.createBuildLink(source, target1, BuildLink.DEFAULT)
            }
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target2).call {
                structureService.createBuildLink(source, target2, BuildLink.DEFAULT)
            }
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target3).call {
                // Adds the link using a form
                structureService.editBuildLinks(
                    source,
                    BuildLinkForm(
                        false,
                        // BuildLinkFormItem(target1.project.name, target1.name, ""), // Removing
                        BuildLinkFormItem(target3.project.name, target3.name, "") // New
                    )
                )
            }
            asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target2)
                .withView(target3).call {
                    // Checks all builds are still linked
                    assertEquals(
                        setOf(target2.id, target3.id),
                        structureService.getQualifiedBuildsUsedBy(source).pageItems.map { it.build.id }.toSet()
                    )
                }
        }
    }

    @Test
    fun `Edition of links - partial rights - adding only`() {
        val source = doCreateBuild()
        val target1 = doCreateBuild()
        val target2 = doCreateBuild()
        val target3 = doCreateBuild()
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).call {
            structureService.createBuildLink(source, target1, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target2).call {
            structureService.createBuildLink(source, target2, BuildLink.DEFAULT)
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target3).call {
            // Adds the link using a form
            structureService.editBuildLinks(
                source,
                BuildLinkForm(
                    true,
                    BuildLinkFormItem(target3.project.name, target3.name, "") // New
                )
            )
        }
        asUser().withProjectFunction(source, BuildConfig::class.java).withView(target1).withView(target2)
            .withView(target3).call {
                // Checks all builds are still linked
                assertEquals(
                    setOf(target1.id, target2.id, target3.id),
                    structureService.getQualifiedBuildsUsedBy(source).pageItems.map { it.build.id }.toSet()
                )
            }
    }

    @Test
    fun `Automation role can create links`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asGlobalRole("AUTOMATION").call {
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
        }
        // The build link is created
        val targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build)
        }.pageItems.map { it.build }
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.name == target.name })
    }

    @Test
    fun `Build config is needed on source build to create a link`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asUser().withView(target).call {
            assertFailsWith<AccessDeniedException> {
                structureService.createBuildLink(build, target, BuildLink.DEFAULT)
            }
        }
    }

    @Test
    fun `Build view is needed on target build to create a link`() {
        withNoGrantViewToAll {
            // Creates a build
            val build = doCreateBuild()
            // Creates a second build to link
            val target = doCreateBuild()
            // Build link creation
            asUser().withProjectFunction(build, BuildConfig::class.java).call {
                assertFailsWith<AccessDeniedException> {
                    structureService.createBuildLink(build, target, BuildLink.DEFAULT)
                }
            }
        }
    }

    @Test
    fun `Adding and deleting a build`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asUser().withProjectFunction(build, BuildConfig::class.java).withView(target).call {
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
        }
        // The build link is created
        var targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build).map { it.build }
        }.pageItems
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.name == target.name })
        // Deleting the build
        asUser().withProjectFunction(build, BuildConfig::class.java).withView(target).call {
            structureService.deleteBuildLink(build, target, "")
        }
        // The build link is deleted
        targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build)
        }.pageItems.map { it.build }
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `Adding twice a build`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asUser().withProjectFunction(build, BuildConfig::class.java).withView(target).call {
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
            // ... twice
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
        }
        // The build link is created
        val targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build)
        }.pageItems.map { it.build }
        assertTrue(targets.isNotEmpty())
        assertEquals(1, targets.size)
        assertTrue(targets.any { it.name == target.name })
    }

    @Test
    fun `Controller role can create links`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asGlobalRole("CONTROLLER").call {
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
        }
        // The build link is created
        val targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build)
        }.pageItems.map { it.build }
        assertEquals(listOf(target.name), targets.map { it.name })
    }

    @Test
    fun `Creator role cannot create links`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asGlobalRole("CREATOR").call {
            assertFailsWith<AccessDeniedException> {
                structureService.createBuildLink(build, target, BuildLink.DEFAULT)
            }
        }
    }

    @Test
    fun `Build config function grants access to create links`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asUser().withProjectFunction(build, BuildConfig::class.java).withView(target).call {
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
        }
        // The build link is created
        val targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build)
        }.pageItems.map { it.build }
        assertEquals(listOf(target.name), targets.map { it.name })
    }

    @Test
    fun `Build edit function grants access to create links`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asUser().withProjectFunction(build, BuildEdit::class.java).withView(target).call {
            structureService.createBuildLink(build, target, BuildLink.DEFAULT)
        }
        // The build link is created
        val targets = asUser().withView(build).withView(target).call {
            structureService.getQualifiedBuildsUsedBy(build)
        }.pageItems.map { it.build }
        assertEquals(listOf(target.name), targets.map { it.name })
    }

    @Test
    fun `Build create function does not grant access to create links`() {
        // Creates a build
        val build = doCreateBuild()
        // Creates a second build to link
        val target = doCreateBuild()
        // Build link creation
        asUser().withProjectFunction(build, BuildCreate::class.java).withView(target).call {
            assertFailsWith<AccessDeniedException> {
                structureService.createBuildLink(build, target, BuildLink.DEFAULT)
            }
        }
    }

    @Test
    fun `Testing the links`() {
        // Creates all builds
        val b1 = doCreateBuild()
        val b2 = doCreateBuild()
        val t1 = doCreateBuild()
        val t2 = doCreateBuild()
        val t3 = doCreateBuild()
        // Creates the links
        asUser().withProjectFunction(b1, BuildConfig::class.java).withView(t1).withView(t2).withView(t3).call {
            structureService.createBuildLink(b1, t1, BuildLink.DEFAULT)
            structureService.createBuildLink(b1, t2, BuildLink.DEFAULT)
            structureService.createBuildLink(b1, t3, "dep3")
        }
        asUser().withProjectFunction(b2, BuildConfig::class.java).withView(t2).call {
            structureService.createBuildLink(b2, t2, BuildLink.DEFAULT)
        }
        // With full rights
        asUserWithView(b1, b2, t1, t2, t3).call {
            assertTrue(structureService.isLinkedTo(b1, t1.project.name, ""))
            assertTrue(structureService.isLinkedTo(b1, t2.project.name, ""))
            assertTrue(structureService.isLinkedTo(b1, t1.project.name, t1.name))
            assertTrue(structureService.isLinkedTo(b1, t2.project.name, t2.name))
            assertTrue(structureService.isLinkedTo(b1, t1.project.name, t1.name.substring(0, 5) + "*"))
            assertTrue(structureService.isLinkedTo(b1, t2.project.name, t2.name.substring(0, 5) + "*"))
            assertTrue(structureService.isLinkedTo(b1, t3.project.name, ""))
            assertTrue(structureService.isLinkedTo(b1, t3.project.name, buildPattern = t3.name))
            assertTrue(structureService.isLinkedTo(b1, t3.project.name, buildPattern = t3.name, qualifier = "dep3"))
            assertTrue(structureService.isLinkedTo(b1, t3.project.name, "", qualifier = "dep3"))
            assertFalse(structureService.isLinkedTo(b1, t3.project.name, "", qualifier = BuildLink.DEFAULT))
            assertFalse(
                structureService.isLinkedTo(
                    b1,
                    t3.project.name,
                    buildPattern = t3.name,
                    qualifier = BuildLink.DEFAULT
                )
            )

            assertTrue(structureService.isLinkedFrom(t2, b1.project.name, ""))
            assertTrue(structureService.isLinkedFrom(t2, b1.project.name, b1.name))
            assertTrue(structureService.isLinkedFrom(t2, b1.project.name, b1.name.substring(0, 5) + "*"))

            assertTrue(structureService.isLinkedFrom(t2, b2.project.name, ""))
            assertTrue(structureService.isLinkedFrom(t2, b2.project.name, b2.name))
            assertTrue(structureService.isLinkedFrom(t2, b2.project.name, b2.name.substring(0, 5) + "*"))
        }
    }

    @Test
    fun `Filter on linked to`() {
        // Source branch
        val source = doCreateBranch()
        val b1 = doCreateBuild(source, nd("1.0.0", ""))
        val b2 = doCreateBuild(source, nd("1.1.0", ""))
        // Target branch
        val target = doCreateBranch()
        val t1 = doCreateBuild(target, nd("2.0.0", ""))
        val t2 = doCreateBuild(target, nd("2.1.0", ""))
        // Creates links
        asUser().withView(target).withProjectFunction(source, BuildConfig::class.java).call {
            structureService.createBuildLink(b1, t1, BuildLink.DEFAULT)
            structureService.createBuildLink(b2, t2, BuildLink.DEFAULT)
        }
        // Standard filter on project
        assertBuildLinkedToFilter(source, target, target.project.name, listOf(b2, b1))
        // Standard filter on project and all builds
        assertBuildLinkedToFilter(source, target, "${target.project.name}:", listOf(b2, b1))
        // Standard filter on project and all builds, using *
        assertBuildLinkedToFilter(source, target, "${target.project.name}:*", listOf(b2, b1))
        // Standard filter on project and common build prefix
        assertBuildLinkedToFilter(source, target, "${target.project.name}:2*", listOf(b2, b1))
        // Standard filter on project and prefix for one
        assertBuildLinkedToFilter(source, target, "${target.project.name}:2.0*", listOf(b1))
        // Standard filter on project and exact build
        assertBuildLinkedToFilter(source, target, "${target.project.name}:2.0.0", listOf(b1))
    }

    private fun assertBuildLinkedToFilter(source: Branch, target: Branch, pattern: String, expected: List<Build>) {
        asUserWithView(source, target).call {
            val builds = buildFilterService
                .standardFilterProviderData(10)
                .withLinkedTo(pattern)
                .build()
                .filterBranchBuilds(source)
            assertEquals(
                expected.map { it.id },
                builds.map { it.id }
            )
        }
    }

    @Test
    fun `Filter on linked from`() {
        // Source branch
        val source = doCreateBranch()
        val b1 = doCreateBuild(source, nd("1.0.0", ""))
        val b2 = doCreateBuild(source, nd("1.1.0", ""))
        // Target branch
        val target = doCreateBranch()
        val t1 = doCreateBuild(target, nd("2.0.0", ""))
        val t2 = doCreateBuild(target, nd("2.1.0", ""))
        // Creates links
        asUser().withView(target).withProjectFunction(source, BuildConfig::class.java).call {
            structureService.createBuildLink(b1, t1, BuildLink.DEFAULT)
            structureService.createBuildLink(b2, t2, BuildLink.DEFAULT)
        }
        // Standard filter on project
        assertBuildLinkedFromFilter(source, target, source.project.name, listOf(t2, t1))
        // Standard filter on project and all builds
        assertBuildLinkedFromFilter(source, target, "${source.project.name}:", listOf(t2, t1))
        // Standard filter on project and all builds, using *
        assertBuildLinkedFromFilter(source, target, "${source.project.name}:*", listOf(t2, t1))
        // Standard filter on project and common build prefix
        assertBuildLinkedFromFilter(source, target, "${source.project.name}:1*", listOf(t2, t1))
        // Standard filter on project and prefix for one
        assertBuildLinkedFromFilter(source, target, "${source.project.name}:1.0*", listOf(t1))
        // Standard filter on project and exact build
        assertBuildLinkedFromFilter(source, target, "${source.project.name}:1.0.0", listOf(t1))
    }

    private fun assertBuildLinkedFromFilter(source: Branch, target: Branch, pattern: String, expected: List<Build>) {
        asUserWithView(source, target).call {
            val builds = buildFilterService
                .standardFilterProviderData(10)
                .withLinkedFrom(pattern)
                .build()
                .filterBranchBuilds(target)
            assertEquals(
                expected.map { it.id },
                builds.map { it.id }
            )
        }
    }

    @Test
    fun `Project search on build linked from`() {
        // Source branch
        val source = doCreateBranch()
        val b1 = doCreateBuild(source, nd("1.0.0", ""))
        val b2 = doCreateBuild(source, nd("1.1.0", ""))
        // Target branch
        val target = doCreateBranch()
        val t1 = doCreateBuild(target, nd("2.0.0", ""))
        val t2 = doCreateBuild(target, nd("2.1.0", ""))
        // Creates links
        asUser().withView(target).withProjectFunction(source, BuildConfig::class.java).call {
            structureService.createBuildLink(b1, t1, BuildLink.DEFAULT)
            structureService.createBuildLink(b2, t2, BuildLink.DEFAULT)
        }
        // Standard filter on project
        assertProjectLinkedFromFilter(source, target, source.project.name, listOf(t2, t1))
        // Standard filter on project and all builds
        assertProjectLinkedFromFilter(source, target, "${source.project.name}:", listOf(t2, t1))
        // Standard filter on project and all builds, using *
        assertProjectLinkedFromFilter(source, target, "${source.project.name}:*", listOf(t2, t1))
        // Standard filter on project and common build prefix
        assertProjectLinkedFromFilter(source, target, "${source.project.name}:1*", listOf(t2, t1))
        // Standard filter on project and prefix for one
        assertProjectLinkedFromFilter(source, target, "${source.project.name}:1.0*", listOf(t1))
        // Standard filter on project and exact build
        assertProjectLinkedFromFilter(source, target, "${source.project.name}:1.0.0", listOf(t1))
    }

    private fun assertProjectLinkedFromFilter(source: Branch, target: Branch, pattern: String, expected: List<Build>) {
        asUserWithView(source, target).call {
            val builds = structureService.buildSearch(
                target.project.id,
                BuildSearchForm(linkedFrom = pattern)
            )
            assertEquals(
                expected.map { it.id },
                builds.map { it.id }
            )
        }
    }

    @Test
    fun `Project search on linked to`() {
        // Source branch
        val source = doCreateBranch()
        val b1 = doCreateBuild(source, nd("1.0.0", ""))
        val b2 = doCreateBuild(source, nd("1.1.0", ""))
        // Target branch
        val target = doCreateBranch()
        val t1 = doCreateBuild(target, nd("2.0.0", ""))
        val t2 = doCreateBuild(target, nd("2.1.0", ""))
        // Creates links
        asUser().withView(target).withProjectFunction(source, BuildConfig::class.java).call {
            structureService.createBuildLink(b1, t1, BuildLink.DEFAULT)
            structureService.createBuildLink(b2, t2, BuildLink.DEFAULT)
        }
        // Standard filter on project
        assertProjectLinkedToFilter(source, target, target.project.name, listOf(b2, b1))
        // Standard filter on project and all builds
        assertProjectLinkedToFilter(source, target, "${target.project.name}:", listOf(b2, b1))
        // Standard filter on project and all builds, using *
        assertProjectLinkedToFilter(source, target, "${target.project.name}:*", listOf(b2, b1))
        // Standard filter on project and common build prefix
        assertProjectLinkedToFilter(source, target, "${target.project.name}:2*", listOf(b2, b1))
        // Standard filter on project and prefix for one
        assertProjectLinkedToFilter(source, target, "${target.project.name}:2.0*", listOf(b1))
        // Standard filter on project and exact build
        assertProjectLinkedToFilter(source, target, "${target.project.name}:2.0.0", listOf(b1))
    }

    @Test
    fun `Counting the upstream links`() {
        val target = doCreateBuild()
        val source1 = doCreateBuild()
        val source2 = doCreateBuild()
        val other = doCreateBuild()
        asAdmin {
            structureService.createBuildLink(source1, target)
            structureService.createBuildLink(source1, target, "dep")
            structureService.createBuildLink(source2, target)
            // Downstream of the target, not counted as upstream
            structureService.createBuildLink(target, other)
        }
        asUserWithView(target, source1, source2).call {
            assertEquals(3, structureService.getCountQualifiedBuildsUsing(target))
            assertEquals(0, structureService.getCountQualifiedBuildsUsing(source1))
        }
    }

    @Test
    fun `Counting the upstream links leaves out the builds the user cannot see`() {
        withNoGrantViewToAll {
            val target = doCreateBuild()
            val visible = doCreateBuild()
            val hidden = doCreateBuild()
            asAdmin {
                structureService.createBuildLink(visible, target)
                structureService.createBuildLink(hidden, target)
            }
            asUserWithView(target, visible).call {
                assertEquals(1, structureService.getCountQualifiedBuildsUsing(target))
            }
        }
    }

    @Test
    fun `Counting the downstream links leaves out the builds the user cannot see`() {
        withNoGrantViewToAll {
            val source = doCreateBuild()
            val visible = doCreateBuild()
            val hidden = doCreateBuild()
            asAdmin {
                structureService.createBuildLink(source, visible)
                structureService.createBuildLink(source, hidden)
            }
            asUserWithView(source, visible).call {
                assertEquals(1, structureService.getCountQualifiedBuildsUsedBy(source))
            }
        }
    }

    @Test
    fun `Getting first level builds by default`() {
        forRecursiveLinks { _, p, builds ->
            val links = structureService.getQualifiedBuildsUsedBy(p)
            assertEquals(
                listOf(
                    builds["q2"],
                    builds["q1"],
                ),
                links.pageItems.map { it.build }
            )
        }
    }

    @Test
    fun `Filtering builds on project labels`() {
        forRecursiveLinks { label, p, builds ->
            val links = structureService.getQualifiedBuildsUsedBy(
                build = p
            ) { candidate ->
                projectLabelManagementService.hasProjectLabel(candidate.build.project, label)
            }
            assertEquals(
                listOf(
                    builds["q1"],
                ),
                links.pageItems.map { it.build }
            )
        }
    }

    @Test
    fun `Getting recursive links with max depth`() {
        forRecursiveLinks { _, p, builds ->
            val links = structureService.getQualifiedBuildsUsedBy(
                build = p,
                depth = 1, // We don't expect s layer
            )
            assertEquals(
                listOf(
                    builds["q2"],
                    builds["r5"],
                    builds["r4"],
                    builds["r3"],
                    builds["q1"],
                    builds["r2"],
                    builds["r1"],
                ),
                links.pageItems.map { it.build }
            )
        }
    }

    @Test
    fun `Getting recursive links with max depth and label filtering`() {
        forRecursiveLinks { label, p, builds ->
            val links = structureService.getQualifiedBuildsUsedBy(
                build = p,
                depth = 1, // We don't expect s layer
            ) { candidate ->
                projectLabelManagementService.hasProjectLabel(candidate.build.project, label)
            }
            assertEquals(
                listOf(
                    builds["r4"],
                    builds["q1"],
                    builds["r1"],
                ),
                links.pageItems.map { it.build }
            )
        }
    }

    @Test
    fun `Getting recursive links with deep depth and label filtering`() {
        forRecursiveLinks { label, p, builds ->
            val links = structureService.getQualifiedBuildsUsedBy(
                build = p,
                depth = 10,
            ) { candidate ->
                projectLabelManagementService.hasProjectLabel(candidate.build.project, label)
            }
            assertEquals(
                listOf(
                    builds["r4"],
                    builds["q1"],
                    builds["s2"],
                    builds["r1"],
                ),
                links.pageItems.map { it.build }
            )
        }
    }

    private fun assertProjectLinkedToFilter(source: Branch, target: Branch, pattern: String, expected: List<Build>) {
        asUserWithView(source, target).call {
            val builds = structureService.buildSearch(
                source.project.id,
                BuildSearchForm(linkedTo = pattern)
            )
            assertEquals(
                expected.map { it.id },
                builds.map { it.id }
            )
        }
    }

}
