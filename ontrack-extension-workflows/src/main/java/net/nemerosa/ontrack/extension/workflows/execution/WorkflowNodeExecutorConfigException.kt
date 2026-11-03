package net.nemerosa.ontrack.extension.workflows.execution

import net.nemerosa.ontrack.model.exceptions.InputException

class WorkflowNodeExecutorConfigException(message: String, cause: Exception? = null) : InputException(cause, message)
