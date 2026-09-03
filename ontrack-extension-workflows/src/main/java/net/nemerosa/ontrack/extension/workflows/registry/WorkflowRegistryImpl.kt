package net.nemerosa.ontrack.extension.workflows.registry

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.workflows.acl.WorkflowRegistration
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation.Companion.validateWorkflow
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Service
import java.util.*

@Service
class WorkflowRegistryImpl(
    private val storageService: StorageService,
    private val securityService: SecurityService,
    private val workflowNodeExecutorService: WorkflowNodeExecutorService,
) : WorkflowRegistry {

    companion object {
        private val STORE = WorkflowRegistry::class.java.name
    }

    override fun validateJsonWorkflow(workflow: JsonNode): WorkflowValidation {
        // Parsing of the workflow
        val workflowObj: Workflow = try {
            WorkflowParser.parseJsonWorkflow(workflow)
        } catch (ex: Exception) {
            val name = workflow.path("name").asText()
            if (name.isNullOrBlank()) {
                return WorkflowValidation.unnamedError(ex)
            } else {
                return WorkflowValidation.error(name, ex)
            }
        }
        // Structural validation
        val validation = validateWorkflow(workflowObj)
        if (validation.error) {
            return validation
        }
        // Validation of the data of each node, so that the preview shown by the edition dialog
        // reports exactly what the saving would reject.
        // The catch is as broad as the parsing one above on purpose: this method reports problems, it
        // never throws them, and an executor is free to reject its data with any exception it likes.
        return try {
            workflowNodeExecutorService.validateWorkflowNodes(workflowObj)
            validation
        } catch (ex: Exception) {
            WorkflowValidation.error(workflowObj.name, ex)
        }
    }

    override fun saveJsonWorkflow(workflow: JsonNode): String {
        securityService.checkGlobalFunction(WorkflowRegistration::class.java)
        val workflowObj: Workflow = WorkflowParser.parseJsonWorkflow(workflow)
        return saveWorkflow(workflowObj)
    }

    override fun saveYamlWorkflow(workflow: String): String {
        securityService.checkGlobalFunction(WorkflowRegistration::class.java)
        val workflowObj: Workflow = WorkflowParser.parseYamlWorkflow(workflow)
        return saveWorkflow(workflowObj)
    }

    private fun saveWorkflow(workflow: Workflow): String {
        // Validation
        validateWorkflow(workflow).throwErrorIfAny()
        workflowNodeExecutorService.validateWorkflowNodes(workflow)
        // Generating an ID
        val id = UUID.randomUUID().toString()
        // Record to save
        val record = InternalRecord(
            workflow = workflow,
        )
        // Saving the record
        storageService.store(STORE, id, record)
        // OK
        return id
    }

    override fun findWorkflow(workflowId: String): WorkflowRecord? {
        securityService.checkGlobalFunction(WorkflowRegistration::class.java)
        return storageService.find(STORE, workflowId, InternalRecord::class)
            ?.run {
                WorkflowRecord(
                    id = workflowId,
                    workflow = workflow,
                )
            }
    }

    private data class InternalRecord(
        val workflow: Workflow,
    )
}