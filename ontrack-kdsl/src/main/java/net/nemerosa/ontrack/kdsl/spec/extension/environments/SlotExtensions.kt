package net.nemerosa.ontrack.kdsl.spec.extension.environments

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.SlotFragment
import net.nemerosa.ontrack.kdsl.spec.toProject

fun SlotFragment.toSlot(connected: Connected) = Slot(
    connector = connected.connector,
    id = id,
    environment = environment.environmentFragment.toEnvironment(connected),
    project = project.projectFragment.toProject(connected),
    qualifier = qualifier,
    description = description ?: "",
    agentsAdmitted = agentsAdmitted ?: false,
)