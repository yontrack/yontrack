package net.nemerosa.ontrack.extension.findings.location

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FindingLocationsTest {

    @Test
    fun `A purl loses its version`() {
        assertEquals(
            NormalisedLocation("pkg:maven/org.apache.logging.log4j/log4j-core", "2.14.1"),
            FindingLocations.normalise("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1")
        )
    }

    @Test
    fun `A purl loses its version and its qualifiers`() {
        assertEquals(
            NormalisedLocation("pkg:deb/debian/openssl", "3.0.11-1~deb12u2"),
            FindingLocations.normalise("pkg:deb/debian/openssl@3.0.11-1~deb12u2?arch=amd64&distro=debian-12.4")
        )
    }

    @Test
    fun `A purl without version loses its qualifiers`() {
        assertEquals(
            NormalisedLocation("pkg:maven/org.x/y", null),
            FindingLocations.normalise("pkg:maven/org.x/y?type=jar")
        )
    }

    @Test
    fun `A purl without version nor qualifiers is kept`() {
        assertEquals(
            NormalisedLocation("pkg:maven/org.x/y", null),
            FindingLocations.normalise("pkg:maven/org.x/y")
        )
    }

    @Test
    fun `A purl keeps its subpath`() {
        assertEquals(
            NormalisedLocation("pkg:golang/google.golang.org/genproto#googleapis/api/annotations", "abcdedf"),
            FindingLocations.normalise("pkg:golang/google.golang.org/genproto@abcdedf#googleapis/api/annotations")
        )
    }

    @Test
    fun `A purl with an encoded npm scope`() {
        assertEquals(
            NormalisedLocation("pkg:npm/%40angular/core", "16.2.0"),
            FindingLocations.normalise("pkg:npm/%40angular/core@16.2.0")
        )
    }

    @Test
    fun `A purl with an unencoded npm scope`() {
        assertEquals(
            NormalisedLocation("pkg:npm/@angular/core", "16.2.0"),
            FindingLocations.normalise("pkg:npm/@angular/core@16.2.0")
        )
    }

    @Test
    fun `The version of a purl is percent-decoded, keeping the plus sign`() {
        assertEquals(
            NormalisedLocation("pkg:golang/github.com/x/y", "v1.0.0+incompatible"),
            FindingLocations.normalise("pkg:golang/github.com/x/y@v1.0.0%2Bincompatible")
        )
        assertEquals(
            NormalisedLocation("pkg:golang/github.com/x/y", "v1.0.0+incompatible"),
            FindingLocations.normalise("pkg:golang/github.com/x/y@v1.0.0+incompatible")
        )
    }

    @Test
    fun `A path is kept as it is`() {
        assertEquals(
            NormalisedLocation("src/main/java/App.java", null),
            FindingLocations.normalise("src/main/java/App.java")
        )
    }

    @Test
    fun `A path with an at sign is not a purl`() {
        assertEquals(
            NormalisedLocation("node_modules/@angular/core/index.js", null),
            FindingLocations.normalise("node_modules/@angular/core/index.js")
        )
    }

    @Test
    fun `An empty location is kept empty`() {
        assertEquals(NormalisedLocation("", null), FindingLocations.normalise(""))
    }
}
