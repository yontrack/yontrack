package net.nemerosa.ontrack.model.structure

/**
 * Item which can be searched upon.
 */
interface SearchItem {

    /**
     * ID of this item
     */
    val id: String

    /**
     * ID of the document storing this item in its search index, unique within this index.
     *
     * Defaults to [id]. An item whose [id] is only unique within a narrower scope - a commit ID
     * within its repository - overrides it, or items of different scopes overwrite each other.
     */
    val documentId: String get() = id

    /**
     * Fields for this item
     */
    val fields: Map<String, Any?>

}