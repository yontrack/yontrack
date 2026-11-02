package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Service

@Service
class NotGrantedMedalsServiceImpl(
    private val propertyService: PropertyService,
    private val cachedSettingsService: CachedSettingsService,
) : NotGrantedMedalsService {

    override fun isEnabled(branch: Branch): Boolean =
        listOf(branch, branch.project)
            .firstNotNullOfOrNull { entity ->
                propertyService.getPropertyValue(entity, NotGrantedMedalsPropertyType::class.java)
            }
            ?.enabled
            ?: cachedSettingsService.getCachedSettings(NotGrantedMedalsSettings::class.java).enabled

}
