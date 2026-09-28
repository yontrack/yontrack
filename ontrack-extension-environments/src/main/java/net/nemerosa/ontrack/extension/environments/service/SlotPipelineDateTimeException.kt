package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.structure.Build
import java.time.LocalDateTime

/**
 * Thrown when the date/time given to backdate a slot pipeline action breaks one of its constraints:
 * a deployment's history must stay a history - nothing in the future, nothing before the build
 * existed, nothing before what already happened to the pipeline or to its slot.
 */
class SlotPipelineDateTimeException private constructor(message: String) : InputException(message) {

    companion object {

        fun inTheFuture(dateTime: LocalDateTime) = SlotPipelineDateTimeException(
            "The date/time of a deployment action ($dateTime) cannot be in the future."
        )

        fun beforeBuildCreation(dateTime: LocalDateTime, build: Build) = SlotPipelineDateTimeException(
            "The date/time of a deployment action ($dateTime) cannot be before the creation of build ${build.entityDisplayName} (${build.signature.time})."
        )

        fun beforePreviousChange(dateTime: LocalDateTime, pipeline: SlotPipeline, previous: LocalDateTime) =
            SlotPipelineDateTimeException(
                "The date/time of a deployment action ($dateTime) cannot be before the previous change of pipeline ${pipeline.fullName()} ($previous)."
            )

        fun beforeLatestPipelineStart(dateTime: LocalDateTime, slot: Slot, latest: SlotPipeline) =
            SlotPipelineDateTimeException(
                "The start of a pipeline ($dateTime) cannot be before the start of the latest pipeline ${latest.fullName()} of slot ${slot.fullName()} (${latest.start})."
            )
    }

}
