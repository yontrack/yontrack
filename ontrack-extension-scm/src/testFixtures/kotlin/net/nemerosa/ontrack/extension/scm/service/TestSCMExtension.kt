package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.stereotype.Component

@Component
class TestSCMExtension(
    extensionFeature: SCMExtensionFeature,
) : AbstractExtension(extensionFeature), SCMExtension {

    companion object {
        val instance = TestSCMExtension(SCMExtensionFeature())
    }

    private val projects = mutableMapOf<String, TestSCMExtensionSetup>()
    private val configs = mutableMapOf<String, TestSCMExtensionSetup>()

    fun registerProjectForTestSCM(project: Project, init: TestSCMExtensionInitContext.() -> Unit) {
        val context = TestSCMExtensionInitContext()
        context.init()
        projects[project.name] = context.createSetup()
    }

    fun registerConfigForTestSCM(configName: String, init: TestSCMExtensionInitContext.() -> Unit = {}) {
        val context = TestSCMExtensionInitContext()
        context.init()
        configs[configName] = context.createSetup()
    }

    override val type: String = "test"

    override fun getSCMPath(configName: String, ref: String): SCMPath? =
        configs[configName]?.let {
            SCMPath(
                scm = TestSCM(name = configName, config = it),
                path = ref, // path = ref
            )
        }

    override fun getSCM(project: Project): SCM? =
        projects[project.name]?.let {
            TestSCM(project.name, it)
        }

    inner class TestSCM(
        name: String,
        private val config: TestSCMExtensionSetup,
    ) : SCM {

        override val type: String = "mock"
        override val engine: String = "test"

        override val repositoryURI: String = "uri://test/${name}"

        override val repositoryHtmlURL: String = "uri://test/${name}.html"
        override val repository: String = name

        override fun getSCMBranch(branch: Branch): String = branch.name

        override fun deleteBranch(branch: String) {
        }

        override fun createBranch(sourceBranch: String, newBranch: String): String =
            uid("$newBranch-commit-")

        override fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean): ByteArray? {
            val file = config.files.find {
                it.branch == scmBranch && it.path == path
            }
            return file?.content?.invoke()
        }

        override fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String) {}

        override fun getDiffLink(commitFrom: String, commitTo: String): String? {
            TODO("Not yet implemented")
        }

        override fun createPR(
            from: String,
            to: String,
            title: String,
            description: String,
            autoApproval: Boolean,
            remoteAutoMerge: Boolean,
            message: String,
            reviewers: List<String>,
        ): SCMPullRequest {
            TODO("Not yet implemented")
        }

        override fun getPullRequestByName(prName: String): SCMPullRequest? {
            TODO("Not yet implemented")
        }

        override fun getBranchLastCommit(branch: String): String? {
            TODO("Not yet implemented")
        }

        override fun findBranchFromScmBranchName(
            project: Project,
            scmBranch: String
        ): Branch? {
            TODO("Not yet implemented")
        }

        override fun getBranchesForCommit(project: Project, commit: String): List<String> {
            TODO("Not yet implemented")
        }

        override fun mergeBranch(
            head: String,
            base: String
        ): SCMCommit {
            TODO("Not yet implemented")
        }
    }

    class TestSCMExtensionFile(
        val path: String,
        val branch: String?,
        val content: () -> ByteArray?,
    )

    class TestSCMExtensionSetup(
        val files: List<TestSCMExtensionFile>,
    )

    class TestSCMExtensionInitContext {

        private val files = mutableListOf<TestSCMExtensionFile>()

        fun withFile(path: String, branch: String = "main", content: () -> String?) {
            files += TestSCMExtensionFile(path, branch) {
                content()?.toByteArray()
            }
        }

        fun withBinaryFile(path: String, branch: String? = "main", content: () -> ByteArray?) {
            files += TestSCMExtensionFile(path, branch, content)
        }

        fun createSetup(): TestSCMExtensionSetup =
            TestSCMExtensionSetup(
                files
            )
    }

}