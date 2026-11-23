package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KubernetesAllowListsTest {

    // Images

    @Test
    fun `An empty list of images denies every image`() {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkImage("alpine:3", emptyList())
        }
        assertTrue("alpine:3" in ex.message!!, ex.message)
        assertTrue(KubernetesConfigProperties.ALLOWED_IMAGES in ex.message!!, ex.message)
    }

    @Test
    fun `An image matching no pattern is refused with a message naming the image and the property`() {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkImage("docker.io/evil/image:1", listOf("registry.example.com/tools/*"))
        }
        assertTrue("docker.io/evil/image:1" in ex.message!!, ex.message)
        assertTrue("ontrack.extension.kubernetes.allowed.images" in ex.message!!, ex.message)
    }

    @Test
    fun `An image equal to a pattern without wildcard is allowed`() {
        assertDoesNotThrow {
            KubernetesAllowLists.checkImage("alpine:3", listOf("node:20", "alpine:3"))
        }
    }

    @Test
    fun `A pattern without wildcard matches the whole image only`() {
        assertFalse(KubernetesAllowLists.imageMatches("alpine:3.20", "alpine:3"))
        assertFalse(KubernetesAllowLists.imageMatches("my/alpine:3", "alpine:3"))
    }

    @Test
    fun `A trailing wildcard matches any sequence of characters, slashes included`() {
        val pattern = "registry.example.com/tools/*"
        assertTrue(KubernetesAllowLists.imageMatches("registry.example.com/tools/gradle:8", pattern))
        assertTrue(KubernetesAllowLists.imageMatches("registry.example.com/tools/sub/gradle:8", pattern))
        assertTrue(KubernetesAllowLists.imageMatches("registry.example.com/tools/", pattern))
        assertFalse(KubernetesAllowLists.imageMatches("registry.example.com/other/gradle:8", pattern))
        assertFalse(KubernetesAllowLists.imageMatches("evil.com/registry.example.com/tools/gradle:8", pattern))
    }

    @Test
    fun `Several wildcards in a pattern`() {
        val pattern = "*/x:*"
        assertTrue(KubernetesAllowLists.imageMatches("registry/x:1.0", pattern))
        assertTrue(KubernetesAllowLists.imageMatches("a/b/x:latest", pattern))
        assertFalse(KubernetesAllowLists.imageMatches("x:1.0", pattern))
        assertFalse(KubernetesAllowLists.imageMatches("registry/y:1.0", pattern))
    }

    @Test
    fun `A lone wildcard allows every image`() {
        assertDoesNotThrow {
            KubernetesAllowLists.checkImage("anything/at:all", listOf("*"))
        }
    }

    @Test
    fun `Regex metacharacters in a pattern are literal`() {
        // A dot is a dot, not any character
        assertTrue(KubernetesAllowLists.imageMatches("registry.example.com/x", "registry.example.com/*"))
        assertFalse(KubernetesAllowLists.imageMatches("registryXexampleYcom/x", "registry.example.com/*"))
        // Other metacharacters
        assertTrue(KubernetesAllowLists.imageMatches("a+b/c?d:[1]", "a+b/c?d:[1]"))
        assertFalse(KubernetesAllowLists.imageMatches("aab/cd:1", "a+b/c?d:[1]"))
        assertTrue(KubernetesAllowLists.imageMatches("x\\Ey:1", "x\\Ey:*"))
        assertFalse(KubernetesAllowLists.imageMatches("anything", "(.*)"))
        assertFalse(KubernetesAllowLists.imageMatches("image:1", "^image:1$"))
    }

    // Secrets

    @Test
    fun `An empty list of Secrets denies every Secret`() {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkSecret("npm-token", emptyList())
        }
        assertTrue("npm-token" in ex.message!!, ex.message)
        assertTrue(KubernetesConfigProperties.ALLOWED_SECRETS in ex.message!!, ex.message)
    }

    @Test
    fun `A Secret not in the list is refused with a message naming the Secret and the property`() {
        val ex = assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkSecret("db-password", listOf("npm-token"))
        }
        assertTrue("db-password" in ex.message!!, ex.message)
        assertTrue("ontrack.extension.kubernetes.allowed.secrets" in ex.message!!, ex.message)
    }

    @Test
    fun `A Secret in the list is allowed`() {
        assertDoesNotThrow {
            KubernetesAllowLists.checkSecret("npm-token", listOf("other", "npm-token"))
        }
    }

    @Test
    fun `Secret names are matched exactly, wildcards included`() {
        assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkSecret("npm-token", listOf("npm-*"))
        }
        assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkSecret("npm-token-2", listOf("npm-token"))
        }
        assertThrows<KubernetesPostProcessingConfigException> {
            KubernetesAllowLists.checkSecret("NPM-TOKEN", listOf("npm-token"))
        }
    }

}
