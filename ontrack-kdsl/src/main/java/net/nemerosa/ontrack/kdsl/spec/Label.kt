package net.nemerosa.ontrack.kdsl.spec

/**
 * A project label: a coloured `category:name` tag which can be put on any number of projects.
 *
 * Unlike a project or a branch, a label is not a project entity: it is global, managed
 * through [Ontrack] rather than through an entity of its own.
 *
 * @property id Label ID, which is what the mutations identify a label by
 * @property category Category of the label, optional
 * @property name Name of the label
 * @property description Description of the label
 * @property color Color of the label, in the `#RRGGBB` format
 */
data class Label(
    val id: Int,
    val category: String?,
    val name: String,
    val description: String?,
    val color: String,
) {
    /**
     * Display form of the label: `category:name`, or just `name` when the label has no category.
     *
     * This is the form the label filters use, for instance `projects(labels:)`.
     */
    val display: String
        get() = category?.let { "$it:$name" } ?: name
}
