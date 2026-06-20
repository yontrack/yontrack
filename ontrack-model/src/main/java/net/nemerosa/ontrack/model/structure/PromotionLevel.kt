package net.nemerosa.ontrack.model.structure

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonView

@JsonPropertyOrder(value = ["id", "name", "description", "branch", "image"])
data class PromotionLevel(
        override val id: ID,
        val name: String,
        override val description: String?,
        @JsonView(PromotionLevel::class)
        val branch: Branch,
        @JsonProperty("image")
        val isImage: Boolean,
        override val signature: Signature,
        val fields: List<PromotionLevelField> = emptyList(),
) : ProjectEntity {

    companion object {
        @JvmStatic
        fun of(branch: Branch, nameDescription: NameDescription): PromotionLevel {
            Entity.isEntityDefined(branch, "Branch must be defined")
            Entity.isEntityDefined(branch.project, "Project must be defined")
            return PromotionLevel(
                    ID.NONE,
                    nameDescription.name,
                    nameDescription.description,
                    branch,
                    isImage = false,
                    signature = Signature.anonymous()
            )
        }

    }

    override val project: Project
        get() = branch.project

    override val parent: ProjectEntity? get() = branch

    override val projectEntityType: ProjectEntityType = ProjectEntityType.PROMOTION_LEVEL

    override val entityDisplayName: String
        get() = "Promotion level ${branch.project.name}/${branch.name}/$name"

    fun withDescription(description: String?) = PromotionLevel(id, name, description, branch, isImage, signature, fields)

    fun withSignature(signature: Signature) = PromotionLevel(id, name, description, branch, isImage, signature, fields)

    fun withId(id: ID) = PromotionLevel(id, name, description, branch, isImage, signature, fields)

    fun withImage(isImage: Boolean) = PromotionLevel(id, name, description, branch, isImage, signature, fields)

    fun withFields(fields: List<PromotionLevelField>) = PromotionLevel(id, name, description, branch, isImage, signature, fields)

    fun update(nameDescription: NameDescription): PromotionLevel = PromotionLevel(
            id,
            nameDescription.name,
            nameDescription.description,
            branch,
            isImage,
            signature,
            fields
    )
}
