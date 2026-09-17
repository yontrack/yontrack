package net.nemerosa.ontrack.service.labels

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.labels.*
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.repository.LabelRecord
import net.nemerosa.ontrack.repository.LabelRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class LabelManagementServiceImpl(
        private val labelRepository: LabelRepository,
        private val securityService: SecurityService
) : LabelManagementService {

    override val labels: List<Label>
        get() = labelRepository.labels
                .map { it.toLabel() }

    override fun findLabels(category: String?, name: String?): List<Label> {
        return labelRepository.findLabels(category, name)
                .map { it.toLabel() }
    }

    override fun newLabel(form: LabelForm): Label {
        securityService.checkGlobalFunction(LabelManagement::class.java)
        form.validate()
        return labelRepository.newLabel(form).toLabel()
    }

    override fun findLabelById(labelId: Int): Label? =
            labelRepository.findLabelById(labelId)?.toLabel()

    override fun getLabel(labelId: Int): Label =
            labelRepository.getLabel(labelId).toLabel()

    override fun updateLabel(labelId: Int, form: LabelForm): Label {
        securityService.checkGlobalFunction(LabelManagement::class.java)
        form.validate()
        // Checks the label exists
        getLabel(labelId)
        return labelRepository.updateLabel(labelId, form).toLabel()
    }

    override fun deleteLabel(labelId: Int): Ack {
        securityService.checkGlobalFunction(LabelManagement::class.java)
        // Checks the label exists
        getLabel(labelId)
        return labelRepository.deleteLabel(labelId)
    }

}

internal fun LabelRecord.toLabel() =
        Label(
                id = id,
                category = category,
                name = name,
                description = description,
                color = color,
        )
