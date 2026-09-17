package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.labels.LabelForm

interface LabelRepository {

    /**
     * Creation of a new label
     */
    fun newLabel(form: LabelForm): LabelRecord

    /**
     * Gets a label using its ID, returning `null` if not found
     */
    fun findLabelById(labelId: Int): LabelRecord?

    /**
     * Gets a label by its ID
     */
    fun getLabel(labelId: Int): LabelRecord

    /**
     * Updates a label
     */
    fun updateLabel(labelId: Int, form: LabelForm): LabelRecord

    /**
     * Deletes a label
     */
    fun deleteLabel(labelId: Int): Ack

    /**
     * Finds a list of labels using their category and/or name.
     */
    fun findLabels(category: String?, name: String?): List<LabelRecord>

    /**
     * Gets list of all labels, ordered by category and name
     */
    val labels: List<LabelRecord>
}

