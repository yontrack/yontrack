package net.nemerosa.ontrack.extension.github.autoversioning

import tools.jackson.dataformat.yaml.YAMLFactory
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.extension.github.ingestion.FileLoaderService
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventProcessingResultDetails
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayload
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayloadListener
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayloadListenerCheck
import net.nemerosa.ontrack.extension.github.ingestion.support.IngestionModelAccessService
import net.nemerosa.ontrack.extension.github.ingestion.support.getOrCreateBranch
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.dataformat.yaml.YAMLMapper

/**
 * This push listener uses the `.github/ontrack/auto-versioning.yml` file to define the auto versioning
 * on the branch.
 */
@Component
class AutoVersioningConfigPushPayloadListener(
    private val ingestionModelAccessService: IngestionModelAccessService,
    private val autoVersioningConfigurationService: AutoVersioningConfigurationService,
    private val fileLoaderService: FileLoaderService,
) : PushPayloadListener {

    private val mapper = YAMLMapper.builder(
        YAMLFactory.builder()
            .configureForJackson2()
            .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
            .build()
    )
        .configureForJackson2()
        .build()

    companion object {
        const val AUTO_VERSIONING_CONFIG_FILE_PATH = ".github/ontrack/auto-versioning.yml"
    }

    override fun preProcessCheck(payload: PushPayload): PushPayloadListenerCheck =
        when {
            payload.getTag() != null -> PushPayloadListenerCheck.IGNORED
            payload.isAddedOrModified(AUTO_VERSIONING_CONFIG_FILE_PATH) ||
                    payload.isRemoved(AUTO_VERSIONING_CONFIG_FILE_PATH) -> PushPayloadListenerCheck.TO_BE_PROCESSED
            else -> PushPayloadListenerCheck.IGNORED
        }

    override fun process(payload: PushPayload, configuration: String?): IngestionEventProcessingResultDetails {
        val branch = ingestionModelAccessService.getOrCreateBranch(
            repository = payload.repository,
            configuration = configuration,
            headBranch = payload.branchName,
            pullRequest = null, // No PR on push (see PR `synchronize` event)
        )
        return if (payload.isAddedOrModified(AUTO_VERSIONING_CONFIG_FILE_PATH)) {
            val content = fileLoaderService.loadFile(branch, AUTO_VERSIONING_CONFIG_FILE_PATH)
            if (content != null) {
                val config = mapper.readTree(content).parse<JsonAutoVersioningConfigs>().toConfig()
                autoVersioningConfigurationService.setupAutoVersioning(branch, config)
                IngestionEventProcessingResultDetails.processed("Auto versioning set for the $branch branch.")
            } else {
                autoVersioningConfigurationService.setupAutoVersioning(branch, null)
                IngestionEventProcessingResultDetails.processed("Auto versioning removed from the $branch branch because config file at $AUTO_VERSIONING_CONFIG_FILE_PATH not found.")
            }
        } else if (payload.isRemoved(AUTO_VERSIONING_CONFIG_FILE_PATH)) {
            // Removing the auto versioning config from the branch
            autoVersioningConfigurationService.setupAutoVersioning(branch, null)
            IngestionEventProcessingResultDetails.processed("Auto versioning removed from the $branch branch.")
        } else {
            IngestionEventProcessingResultDetails.ignored("$AUTO_VERSIONING_CONFIG_FILE_PATH file not touched in any way.")
        }
    }
}