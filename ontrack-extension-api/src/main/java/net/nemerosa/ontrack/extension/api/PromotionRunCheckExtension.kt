package net.nemerosa.ontrack.extension.api

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.Signature

/**
 * Check on a promotion run for it creation.
 */
interface PromotionRunCheckExtension : Extension {

    /**
     * Checks if the given [promotionRun] can be created or not. Throws an [InputException] if the
     * promotion run cannot be created.
     */
    @Throws(InputException::class)
    fun checkPromotionRunCreation(promotionRun: PromotionRun)

    /**
     * Explains, without throwing, why the [build] could not be promoted to the [promotionLevel] -
     * the same question as [checkPromotionRunCreation], read by the readiness of a build.
     *
     * The default implementation checks an unsaved promotion run and returns the message of the
     * [InputException] it throws, which is therefore the first reason only. A check which may fail for
     * several reasons overrides it to list them all.
     *
     * @return The reasons why the promotion would be refused, empty when it would be accepted
     */
    fun explainPromotionRunCreation(build: Build, promotionLevel: PromotionLevel): List<String> =
        try {
            checkPromotionRunCreation(
                PromotionRun.of(build, promotionLevel, Signature.anonymous(), null)
            )
            emptyList()
        } catch (ex: InputException) {
            listOfNotNull(ex.message?.oneLine())
        }

    /**
     * Name of the check, as the readiness of a build reports it.
     */
    val checkName: String
        get() = javaClass.simpleName.removeSuffix("Extension")

    /**
     * Order to the check. The lowest number is checked first.
     */
    val order: Int

}

/**
 * The messages of the checks are often written as indented blocks: a reason is one line.
 */
internal fun String.oneLine(): String = trim().replace(Regex("\\s+"), " ")
