package net.nemerosa.ontrack.git

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitRepositoryTest {

    @Test
    fun getRepositoryId_1() {
        assertEquals(
                "basic_ontrack_git___github_com_nemerosa_ontrack_git",
                GitRepository("basic", "ontrack", "git://github.com/nemerosa/ontrack.git", null).id
        )
    }

    @Test
    fun getRepositoryId_2() {
        assertEquals(
                "basic_ontrack_git_github_com_nemerosa_ontrack_git",
                GitRepository("basic", "ontrack", "git@github.com:nemerosa/ontrack.git", null).id
        )
    }

    @Test
    fun getRepositoryId_3() {
        assertEquals(
                "github_ontrack_git_github_com_nemerosa_ontrack_git",
                GitRepository("github", "ontrack", "git@github.com:nemerosa/ontrack.git", null).id
        )
    }

    @Test
    fun getRepositoryId_4() {
        assertEquals(
                "basic_ontrack_https___github_com_nemerosa_ontrack_git",
                GitRepository("basic", "ontrack", "https://github.com/nemerosa/ontrack.git", null).id
        )
    }

    @Test
    fun getRepositoryId_5() {
        assertEquals(
                "basic_ontrack2_https___github_com_nemerosa_ontrack_git",
                GitRepository("basic", "ontrack2", "https://github.com/nemerosa/ontrack.git", null).id
        )
    }

}