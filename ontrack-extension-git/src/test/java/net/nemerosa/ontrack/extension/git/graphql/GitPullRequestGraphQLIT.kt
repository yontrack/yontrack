package net.nemerosa.ontrack.extension.git.graphql

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.extension.git.WithGitPullRequestEnabled
import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurator
import net.nemerosa.ontrack.json.isNullOrNullNode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@WithGitPullRequestEnabled
class GitPullRequestGraphQLIT: AbstractGitTestSupport() {

    @Autowired
    private lateinit var gitMockingConfigurator: GitMockingConfigurator

    @BeforeEach
    fun init() {
        gitMockingConfigurator.clearPullRequests()
    }

    @Test
    fun `Branch configuration for a normal branch is not marked as pull request`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("release/1.0")
                    // Gets the PR for this branch
                    val data = run("""{
                            branches(id: $id) {
                                pullRequest {
                                    id
                                    key
                                    isValid
                                    source
                                    target
                                    title
                                    status
                                    url
                                }
                            }
                        }""")
                    val pr = data["branches"][0].path("pullRequest")
                    assertTrue(pr.isNullOrNullNode(), "No pull request")
                }
            }
        }
    }

    @Test
    fun `Branch configuration for a PR is marked as pull request`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            gitMockingConfigurator.registerPullRequest(1)
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    // Gets the PR for this branch
                    val data = run("""{
                            branches(id: $id) {
                                pullRequest {
                                    id
                                    key
                                    isValid
                                    source
                                    target
                                    title
                                    status
                                    url
                                    sourceBranch {
                                        id
                                        name
                                    }
                                    targetBranch {
                                        id
                                        name
                                    }
                                }
                            }
                        }""")
                    val pr = data["branches"][0].path("pullRequest")
                    assertEquals(1, pr.path("id").asInt())
                    assertEquals("#1", pr.path("key").asText())
                    assertEquals(true, pr.path("isValid").asBoolean())
                    assertEquals("feature/TK-1-feature", pr.path("source").asText())
                    assertEquals("release/1.0", pr.path("target").asText())
                    assertEquals("PR n°1", pr.path("title").asText())
                    assertEquals("open", pr.path("status").asText())
                    assertEquals("uri:testing:web:git:pr:1", pr.path("url").asText())
                    assertTrue(pr.path("sourceBranch").isNullOrNullNode(), "No source branch can be identified")
                    assertTrue(pr.path("targetBranch").isNullOrNullNode(), "No target branch can be identified")
                }
            }
        }
    }

    @Test
    fun `Branch identified PR linked to source and target branch`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            gitMockingConfigurator.registerPullRequest(1)
            project {
                prGitProject()
                val source = branch("source") {
                    gitBranch("feature/TK-1-feature")
                }
                val target = branch("target") {
                    gitBranch("release/1.0")
                }
                branch {
                    gitBranch("PR-1")
                    // Gets the PR for this branch
                    val data = run("""{
                            branches(id: $id) {
                                pullRequest {
                                    id
                                    key
                                    isValid
                                    source
                                    target
                                    title
                                    status
                                    url
                                    sourceBranch {
                                        id
                                        name
                                    }
                                    targetBranch {
                                        id
                                        name
                                    }
                                }
                            }
                        }""")
                    val pr = data["branches"][0].path("pullRequest")
                    assertEquals(1, pr.path("id").asInt())
                    assertEquals("#1", pr.path("key").asText())
                    assertEquals(true, pr.path("isValid").asBoolean())
                    assertEquals("feature/TK-1-feature", pr.path("source").asText())
                    assertEquals("release/1.0", pr.path("target").asText())
                    assertEquals("PR n°1", pr.path("title").asText())
                    assertEquals("open", pr.path("status").asText())
                    assertEquals("uri:testing:web:git:pr:1", pr.path("url").asText())
                    assertNotNull(pr.path("sourceBranch")) {
                        assertEquals(source.id(), it["id"].asInt())
                        assertEquals(source.name, it["name"].asText())
                    }
                    assertNotNull(pr.path("targetBranch")) {
                        assertEquals(target.id(), it["id"].asInt())
                        assertEquals(target.name, it["name"].asText())
                    }
                }
            }
        }
    }
}