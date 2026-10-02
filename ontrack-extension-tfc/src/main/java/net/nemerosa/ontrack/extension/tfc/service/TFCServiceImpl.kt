package net.nemerosa.ontrack.extension.tfc.service

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.tfc.client.TFCClientFactory
import net.nemerosa.ontrack.extension.tfc.config.TFCConfigurationService
import net.nemerosa.ontrack.extension.tfc.metrics.TFCMetrics
import net.nemerosa.ontrack.model.metrics.time
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class TFCServiceImpl(
        private val structureService: StructureService,
        private val securityService: SecurityService,
        private val tfcConfigurationService: TFCConfigurationService,
        private val tfcClientFactory: TFCClientFactory,
        private val meterRegistry: MeterRegistry,
        private val tfcBuildService: TFCBuildService,
) : TFCService {

    override fun validate(
            params: TFCParameters,
            status: ValidationRunStatusID,
            workspaceId: String,
            runUrl: String
    ) {
        // Getting the actual parameters
        val actualParams = expandParams(params, workspaceId, runUrl)
        securityService.asAdmin("tfc") {
            // Looking for the build
            val build = findBuild(actualParams) ?: return@asAdmin
            // Forcing the creation of the validation stamp if not existing
            val stamp = structureService.findValidationStampByName(
                    params.project, build.branch.name, params.validation
            ).getOrNull()
            if (stamp == null) {
                structureService.newValidationStamp(
                        ValidationStamp.of(
                                build.branch,
                                NameDescription.nd(params.validation, "")
                        )
                )
            }
            // Validation
            structureService.newValidationRun(
                    build, ValidationRunRequest(
                    validationStampName = params.validation,
                    validationRunStatusId = status,
                    description = runUrl,
            )
            )
        }
    }

    private fun findBuild(params: TFCParameters): Build? = tfcBuildService.findBuild(params)

    private fun expandParams(params: TFCParameters, workspaceId: String, runUrl: String): TFCParameters =
            if (params.hasVariables()) {
                // Gets the list of variables from TFC
                val variables = getWorkspaceVariables(workspaceId, runUrl)
                // Expands the parameters using these variables
                params.expand(variables, workspaceId)
            } else {
                params
            }

    private fun getWorkspaceVariables(workspaceId: String, runUrl: String): Map<String, String> {
        // Gets a configuration using the URL
        val config = tfcConfigurationService.findConfigurationByURL(runUrl)
                ?: throw TFCNoConfigurationException(runUrl)
        // Creating a client
        val client = tfcClientFactory.createClient(config)
        // Getting the list of variables for the workspace
        val variables = meterRegistry.time(TFCMetrics.tfc_variables) {
            client.getWorkspaceVariables(workspaceId)
        } ?: emptyList()
        // Indexing
        return variables.associate { v ->
            v.key to (v.value ?: "")
        }
    }

}