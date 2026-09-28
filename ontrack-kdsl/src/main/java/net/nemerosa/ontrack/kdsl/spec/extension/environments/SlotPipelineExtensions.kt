package net.nemerosa.ontrack.kdsl.spec.extension.environments

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.SlotPipelineFragment
import net.nemerosa.ontrack.kdsl.spec.toBuild

fun SlotPipelineFragment.toPipeline(connected: Connected) = SlotPipeline(
    connector = connected.connector,
    id = id,
    number = number!!,
    status = status,
    start = start,
    end = end,
    slot = slot.slotFragment.toSlot(connected),
    build = build.buildFragment.toBuild(connected),
)
