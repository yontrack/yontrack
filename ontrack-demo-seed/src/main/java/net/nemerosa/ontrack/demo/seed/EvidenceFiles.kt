package net.nemerosa.ontrack.demo.seed

/**
 * The files the dataset attaches as evidence, kept in the `demo/evidence` resources of this module.
 *
 * Resources rather than content generated on the fly: what an evidence is - its SHA-256, which the
 * trail records - has to be the same from one reset to the next, and a generator is one more thing
 * which can change it without anybody noticing.
 */
internal object EvidenceFiles {

    private const val ROOT = "/demo/evidence/"

    /**
     * Whether the dataset can name [resource].
     */
    fun exists(resource: String): Boolean =
        EvidenceFiles::class.java.getResource(ROOT + resource) != null

    /**
     * Content of [resource].
     *
     * @throws IllegalStateException When there is no such resource - which `validate` rules out
     * before the reset
     */
    fun content(resource: String): ByteArray =
        EvidenceFiles::class.java.getResourceAsStream(ROOT + resource)?.use { it.readBytes() }
            ?: error("No evidence file $resource in the resources of the demo seed")
}
