package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurator
import net.nemerosa.ontrack.extension.stale.DefaultStaleBranchCheckContext
import net.nemerosa.ontrack.extension.stale.StaleBranchStatus
import net.nemerosa.ontrack.model.structure.Signature.Companion.of
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@WithGitPullRequestEnabled
class PullRequestStaleBranchCheckIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var check: PullRequestStaleBranchCheck

    @Autowired
    private lateinit var gitMockingConfigurator: GitMockingConfigurator

    @BeforeEach
    fun init() {
        gitMockingConfigurator.clearPullRequests()
    }

    @Test
    fun `Project not eligible if not configured for Git`() {
        project {
            assertFalse(check.isProjectEligible(this), "Project is not eligible for PR branch check because not on Git")
        }
    }

    @Test
    fun `Project not eligible if PR are not enabled`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                withPRDisabled {
                    assertFalse(
                        check.isProjectEligible(this),
                        "Project is not eligible for PR branch check because PR are not enabled"
                    )
                }
                assertTrue(
                    check.isProjectEligible(this),
                    "Project is eligible for PR branch check because PR are enabled"
                )
            }
        }
    }

    @Test
    fun `Project not eligible if PR cleanup is not enabled`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                withPRCleanupDisabled {
                    assertFalse(
                        check.isProjectEligible(this),
                        "Project is not eligible for PR branch check because PR cleanup is not enabled"
                    )
                }
                assertTrue(
                    check.isProjectEligible(this),
                    "Project is eligible for PR branch check because PR cleanup is enabled"
                )
            }
        }
    }

    @Test
    fun `Branch not eligible if not configured for Git`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    assertFalse(
                        check.isBranchEligible(this),
                        "Branch is not eligible for PR branch check because not on Git"
                    )
                }
            }
        }
    }

    @Test
    fun `Branch not eligible if not a PR`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("main")
                    assertFalse(
                        check.isBranchEligible(this),
                        "Branch is not eligible for PR branch check because not a PR"
                    )
                }
            }
        }
    }

    @Test
    fun `Branch not eligible if PR are not enabled`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    withPRDisabled {
                        assertFalse(
                            check.isBranchEligible(this),
                            "Branch is not eligible for PR branch check because PR are not enabled"
                        )
                    }
                    assertTrue(
                        check.isBranchEligible(this),
                        "Branch is eligible for PR branch check because PR are enabled"
                    )
                }
            }
        }
    }

    @Test
    fun `Branch not eligible if PR cleanup is not enabled`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    withPRCleanupDisabled {
                        assertFalse(
                            check.isBranchEligible(this),
                            "Branch is not eligible for PR branch check because PR cleanup is not enabled"
                        )
                    }
                    assertTrue(
                        check.isBranchEligible(this),
                        "Branch is eligible for PR branch check because PR cleanup is enabled"
                    )
                }
            }
        }
    }

    @Test
    fun `Valid PR on recent build`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitMockingConfigurator.registerPullRequest(1)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build()
                    assertEquals(
                        null,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "No decision, keeping the branch"
                    )
                }
            }
        }
    }

    @Test
    fun `Valid PR on old build`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitMockingConfigurator.registerPullRequest(1)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build().withSignature(of(Time.now().minusDays(10), "test"))
                    assertEquals(
                        null,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "No decision, keeping the branch"
                    )
                }
            }
        }
    }

    @Test
    fun `Missing PR on recent build`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    // gitMockingConfigurator.registerPullRequest(1)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build()
                    assertEquals(
                        null,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "No decision, keeping the branch"
                    )
                }
            }
        }
    }

    @Test
    fun `Missing PR on build to disable`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    // gitMockingConfigurator.registerPullRequest(1)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build().withSignature(of(Time.now().minusDays(6), "test"))
                    assertEquals(
                        StaleBranchStatus.DISABLE,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "Branch must be disabled"
                    )
                }
            }
        }
    }

    @Test
    fun `Missing PR on build to delete`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    // gitMockingConfigurator.registerPullRequest(1)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build().withSignature(of(Time.now().minusDays(10), "test"))
                    assertEquals(
                        StaleBranchStatus.DELETE,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "Branch must be deleted"
                    )
                }
            }
        }
    }

    @Test
    fun `Invalid PR on recent build`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitMockingConfigurator.registerPullRequest(1, invalid = true)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build()
                    assertEquals(
                        null,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "No decision, keeping the branch"
                    )
                }
            }
        }
    }

    @Test
    fun `Invalid PR on build to disable`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitMockingConfigurator.registerPullRequest(1, invalid = true)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build().withSignature(of(Time.now().minusDays(6), "test"))
                    assertEquals(
                        StaleBranchStatus.DISABLE,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "Branch must be disabled"
                    )
                }
            }
        }
    }

    @Test
    fun `Invalid PR on build to delete`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitMockingConfigurator.registerPullRequest(1, invalid = true)
                    gitBranch("PR-1") {
                        commitAsProperty()
                    }
                    val build = build().withSignature(of(Time.now().minusDays(10), "test"))
                    assertEquals(
                        StaleBranchStatus.DELETE,
                        check.getBranchStaleness(DefaultStaleBranchCheckContext(), this, build),
                        "Branch must be deleted"
                    )
                }
            }
        }
    }

}