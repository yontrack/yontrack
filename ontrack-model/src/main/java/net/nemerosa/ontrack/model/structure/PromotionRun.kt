package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonView

@JsonPropertyOrder(alphabetic = true)
data class PromotionRun(
    override val id: ID,
    @JsonView(
        Project::class,
        PromotionLevel::class,
        PromotionRun::class,
        Build::class,
    )
    val build: Build,
    @JsonView(
        Build::class,
        PromotionRun::class,
    )
    val promotionLevel: PromotionLevel,
    override val signature: Signature,
    override val description: String?,
    val fieldValues: List<PromotionRunFieldValue> = emptyList(),
) : ProjectEntity {

    override val project: Project
        get() = build.project

    override val parent: ProjectEntity? get() = promotionLevel

    override val projectEntityType: ProjectEntityType = ProjectEntityType.PROMOTION_RUN

    override val entityDisplayName: String
        get() = "Promotion run ${build.branch.project.name}/${build.branch.name}/${build.name}/${promotionLevel.name}"

    fun withId(id: ID) = PromotionRun(id, build, promotionLevel, signature, description, fieldValues)

    fun withFieldValues(fieldValues: List<PromotionRunFieldValue>) =
        PromotionRun(id, build, promotionLevel, signature, description, fieldValues)

    companion object {
        @JvmStatic
        fun of(build: Build, promotionLevel: PromotionLevel, signature: Signature, description: String?): PromotionRun =
            PromotionRun(
                ID.NONE,
                build,
                promotionLevel,
                signature,
                description
            )
    }


}
