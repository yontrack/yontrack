package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.labels.LabelForm

class LabelRecord(
        val id: Int,
        val category: String?,
        val name: String,
        val description: String?,
        val color: String,
) {
    /**
     * Conversion to a form
     */
    fun toLabelForm() = LabelForm(
            category = category,
            name = name,
            description = description,
            color = color
    )

    /**
     * Comparing the [category] and [name].
     */
    fun sameThan(form: LabelForm): Boolean {
        return category == form.category && name == form.name
    }
}
