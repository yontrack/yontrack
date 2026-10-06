package net.nemerosa.ontrack.extension.audittrail.archive

import net.nemerosa.ontrack.extension.audittrail.evidence.isBidiControl

/**
 * Paths of the files of an evidence archive, `<validationStamp>/<runOrder>/<evidenceId>-<fileName>`.
 *
 * The names come from users and clients: each one is made a single, safe segment, so that no file
 * escapes its folder once unzipped (zip-slip), whatever the system it is unzipped on. The ID of the
 * evidence keeps every path unique.
 */
object EvidenceArchiveNames {

    /**
     * Replacement of every character which cannot stay in a segment
     */
    private const val REPLACEMENT = '_'

    /**
     * Separators of paths, and the characters Windows file systems refuse
     */
    private const val REFUSED = "/\\:*?\"<>|"

    /**
     * Path of the file of an evidence in its archive.
     *
     * @param validationStamp Name of the validation stamp of its run
     * @param runOrder Order of its run
     * @param evidenceId ID of the evidence
     * @param fileName File name of the evidence
     */
    fun path(validationStamp: String, runOrder: Int, evidenceId: Int, fileName: String): String =
        "${segment(validationStamp)}/$runOrder/$evidenceId-${segment(fileName)}"

    /**
     * A name made one safe segment of a path: separators, characters refused by Windows, control
     * characters and bidirectional overrides are replaced, and so are leading dots — no segment is
     * `.`, `..` nor hidden. A blank name is replaced as a whole.
     *
     * @param name Name, as stored
     */
    fun segment(name: String): String {
        val replaced = name.trim()
            .map { c -> if (c in REFUSED || c.isISOControl() || c.isBidiControl()) REPLACEMENT else c }
            .joinToString("")
        val dots = replaced.takeWhile { it == '.' }.length
        val segment = REPLACEMENT.toString().repeat(dots) + replaced.substring(dots)
        return segment.ifEmpty { REPLACEMENT.toString() }
    }
}
