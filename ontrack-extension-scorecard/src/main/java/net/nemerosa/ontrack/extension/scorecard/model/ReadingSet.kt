package net.nemerosa.ontrack.extension.scorecard.model

import net.nemerosa.ontrack.extension.scorecard.estates.Estate

/**
 * A set of readings: the readings of a project with no estate, or for one estate it belongs to.
 *
 * The set says where its readings are stored ([estateId]), under which name they are shown
 * and exported, and which kind of marker its delivery readings are read up to.
 */
sealed interface ReadingSet {

    /**
     * Estate of the set, `null` for the no-estate set. Part of the storage key of a reading.
     */
    val estateId: Int?

    /**
     * Display name of the set
     */
    val name: String

    /**
     * Value of the `estate` tag of the metrics: `-` for the no-estate set
     */
    val tag: String
}

/**
 * The set every non-disabled project always has: no estate, the promotion marker, no targets.
 * It is never judged green or red.
 */
data object NoEstateReadingSet : ReadingSet {
    override val estateId: Int? = null
    override val name: String = "Project"
    override val tag: String = "-"
}

/**
 * The set of an estate: the readings of the projects the estate selects, read up to its marker,
 * over its windows, against its targets.
 */
data class EstateReadingSet(
    val estate: Estate,
) : ReadingSet {
    override val estateId: Int = estate.id
    override val name: String = estate.name
    override val tag: String = estate.name
}
