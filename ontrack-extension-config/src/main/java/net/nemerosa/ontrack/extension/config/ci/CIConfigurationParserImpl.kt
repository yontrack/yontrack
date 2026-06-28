package net.nemerosa.ontrack.extension.config.ci

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.config.ci.model.*
import net.nemerosa.ontrack.extension.config.ci.properties.PropertyAliasService
import net.nemerosa.ontrack.extension.config.ci.validations.ValidationDataTypeAliasService
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtensionNotFoundException
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtensionTypeNotSupportedException
import net.nemerosa.ontrack.extension.config.model.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.yaml.Yaml
import org.springframework.stereotype.Component

@Component
class CIConfigurationParserImpl(
    private val propertyService: PropertyService,
    private val validationDataTypeService: ValidationDataTypeService,
    private val propertyAliasService: PropertyAliasService,
    private val validationDataTypeAliasService: ValidationDataTypeAliasService,
    private val extensionManager: ExtensionManager,
) : CIConfigurationParser {

    private val ciExtensions: Map<String, CIConfigExtension<*>> by lazy {
        extensionManager.getExtensions(CIConfigExtension::class.java).associateBy { it.id }
    }

    override fun parseConfig(yaml: String): ConfigurationInput {
        // JSON parsing
        val documents = Yaml().read(yaml)
        return if (documents.size == 1) {
            val document = documents.first()
            val version = document.getTextField("version")
            if (version.isNullOrBlank()) {
                throw CIConfigVersionMissingException()
            } else if (version == "v1") {
                val ciConfigInput = document.parse<CIConfigInput>()
                convert(ciConfigInput)
            } else {
                throw CIConfigVersionUnsupportedException(version)
            }
        } else if (documents.size > 1) {
            throw CIConfigurationSeveralDocumentsException()
        } else {
            throw CIConfigurationNoDocumentException()
        }
    }

    private fun convert(ciConfigInput: CIConfigInput): ConfigurationInput {
        return ConfigurationInput(
            configuration = convertRootConfiguration(ciConfigInput.configuration),
        )
    }

    private fun convertRootConfiguration(ciConfigRoot: CIConfigRoot): RootConfiguration {
        return RootConfiguration(
            defaults = convertConfiguration(ciConfigRoot.defaults),
            custom = convertCustom(ciConfigRoot.custom),
        )
    }

    private fun convertCustom(custom: CICustom): Custom {
        return Custom(
            configs = custom.configs.map {
                convertCustomConfig(it)
            }
        )
    }

    private fun convertCustomConfig(customConfig: CICustomConfig): CustomConfig {
        return CustomConfig(
            conditions = customConfig.conditions.map { (name, data) ->
                ConditionConfig(name, data)
            },
            project = convertProject(customConfig.project, custom = true),
            branch = convertBranch(customConfig.branch, custom = true),
            build = convertBuild(customConfig.build, custom = true),
        )
    }

    private fun convertConfiguration(ciConfig: CIConfig): Configuration {
        return Configuration(
            project = convertProject(ciConfig.project, custom = false),
            branch = convertBranch(ciConfig.branch, custom = false),
            build = convertBuild(ciConfig.build, custom = false),
        )
    }

    private fun convertBuild(build: CIBuildConfig, custom: Boolean): BuildConfiguration {
        return BuildConfiguration(
            buildName = build.buildName,
            properties = convertProperties(build),
            extensions = convertExtensions(ProjectEntityType.BUILD, build, custom),
        )
    }

    private fun convertBranch(branch: CIBranchConfig, custom: Boolean): BranchConfiguration {
        return BranchConfiguration(
            properties = convertProperties(branch),
            extensions = convertExtensions(ProjectEntityType.BRANCH, branch, custom),
            validations = convertValidations(branch.validations),
            promotions = convertPromotions(branch.promotions),
        )
    }

    private fun convertPromotions(promotions: Map<String, CIPromotionConfig>): List<PromotionLevelConfiguration> {
        return promotions.map { (name, config) ->
            PromotionLevelConfiguration(
                name = name,
                description = "",
                validations = config.validations,
                promotions = config.promotions,
                dependencies = config.dependsOn,
                fields = config.fields.mapIndexed { index, field ->
                    PromotionLevelField(
                        id = 0,
                        name = field.name,
                        displayName = field.displayName,
                        description = field.description,
                        type = field.type,
                        required = field.required,
                        options = field.options,
                        position = index,
                    )
                },
            )
        }
    }

    private fun convertValidations(validations: Map<String, JsonNode>): List<ValidationStampConfiguration> {
        return validations.map { (name, config) ->
            convertValidation(name, config)
        }
    }

    private fun convertValidation(
        name: String,
        config: JsonNode
    ): ValidationStampConfiguration {
        return ValidationStampConfiguration(
            name = name,
            description = config.getTextField("description") ?: "",
            validationStampDataConfiguration = convertValidationData(name, config),
        )
    }

    private fun convertValidationData(name: String, config: JsonNode): ValidationStampDataConfiguration? {
        return if (config is ObjectNode) {
            val names = config.fieldNames().asSequence().toList().toMutableList()
            names.remove("description")
            if (names.isEmpty()) {
                null
            } else if (names.size == 1) {
                val type = names.first()
                val typeConfig = config.get(type)
                convertValidationDataType(type, typeConfig)
            } else {
                throw ValidationStampDataConfigurationWrongFormatException(name)
            }
        } else {
            null
        }
    }

    private fun convertValidationDataType(
        type: String,
        typeConfig: JsonNode
    ): ValidationStampDataConfiguration {
        val validationDataTypeAlias = validationDataTypeAliasService.findValidationDataTypeAlias(type)
        return if (validationDataTypeAlias != null) {
            convertActualValidationDataType(
                type = validationDataTypeAlias.type,
                data = validationDataTypeAlias.parseConfig(typeConfig),
            )
        } else {
            convertActualValidationDataType(
                type = type,
                data = typeConfig,
            )
        }
    }

    private fun convertActualValidationDataType(
        type: String,
        data: JsonNode
    ): ValidationStampDataConfiguration {
        validationDataTypeService.validateValidationDataTypeConfig<Any>(type, data)
            ?: throw ValidationStampDataConfigurationTypeNotFoundException(type)
        return ValidationStampDataConfiguration(
            type = type,
            data = data,
        )
    }

    private fun convertProject(
        project: CIProjectConfig,
        custom: Boolean,
    ): ProjectConfiguration {
        return ProjectConfiguration(
            properties = convertProperties(project),
            extensions = convertExtensions(ProjectEntityType.PROJECT, project, custom),
            projectName = project.name,
            scmConfig = project.scmConfig,
            issueServiceIdentifier = project.issueServiceIdentifier,
            scmIndexationInterval = project.scmIndexationInterval,
        )
    }

    private fun convertProperties(ciPropertiesConfig: CIPropertiesConfig): List<PropertyConfiguration> {
        return ciPropertiesConfig.properties.map { (type, data) ->
            convertProperty(type, data)
        }
    }

    private fun convertExtensions(
        projectEntityType: ProjectEntityType,
        ciExtensionsConfig: CIExtensionsConfig,
        custom: Boolean,
    ): List<ExtensionConfiguration> {
        return ciExtensionsConfig.extensions.map { (id, data) ->
            convertExtension(projectEntityType, id, data, custom)
        }
    }

    private fun convertExtension(
        projectEntityType: ProjectEntityType,
        id: String,
        data: JsonNode,
        custom: Boolean,
    ): ExtensionConfiguration {
        val extension = ciExtensions[id]
            ?: throw CIConfigExtensionNotFoundException(id)
        if (projectEntityType in extension.projectEntityTypes) {
            val extensionData = if (custom) {
                data
            } else {
                // Validation through parsing
                extension.parseData(data).asJson()
            }
            return ExtensionConfiguration(
                id = id,
                data = extensionData,
            )
        } else {
            throw CIConfigExtensionTypeNotSupportedException(projectEntityType, id)
        }
    }

    private fun convertProperty(
        type: String,
        data: JsonNode
    ): PropertyConfiguration {
        val propertyAlias = propertyAliasService.findPropertyAlias(type)
        return if (propertyAlias != null) {
            convertActualProperty(
                type = propertyAlias.type,
                data = propertyAlias.parseConfig(data),
            )
        } else {
            convertActualProperty(
                type = type,
                data = data,
            )
        }
    }

    private fun convertActualProperty(
        type: String,
        data: JsonNode
    ): PropertyConfiguration {
        val propertyType = propertyService.getPropertyTypeByName<Any>(type)
        return convertPropertyType(propertyType, data)
    }

    private fun <T> convertPropertyType(
        propertyType: PropertyType<T>,
        data: JsonNode
    ): PropertyConfiguration {
        val value = propertyType.fromClient(data)
        return PropertyConfiguration(
            type = propertyType.typeName,
            data = propertyType.forStorage(value)
        )
    }

}