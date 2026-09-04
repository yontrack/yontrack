package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.model.events.EventRenderer
import org.springframework.stereotype.Service

@Service
class SemanticChangelogRenderingServiceImpl(
    private val semanticChangelogService: SemanticChangelogService,
) : SemanticChangelogRenderingService {

    override fun render(
        changelog: SCMChangeLog,
        config: SemanticChangeLogConfig,
        suffix: String?,
        renderer: EventRenderer
    ): String {
        val entries = changelog.commits.map {
            semanticChangelogService.parseSemanticCommit(it.commit.message)
        }

        val grouped = entries
            .filter { !it.type.isNullOrBlank() }
            .filter { it.type !in config.exclude }
            .groupBy { it.type!! }
            .mapKeys { (type, _) -> getTypeTitle(type, config) }
            .toSortedMap { a, b -> a.title.compareTo(b.title) }

        return buildString {

            var hasIssues = false
            if (config.issues) {
                hasIssues = !changelog.issues?.issues.isNullOrEmpty()
                if (hasIssues) {
                    val issues = renderChangeLogIssues(renderer, changelog)
                    val issuesSection = if (config.emojis) {
                        "$issuesEmoji Issues"
                    } else {
                        "Issues"
                    }
                    append("$issuesSection:\n\n").append(issues).append("\n")
                }
            }

            var no = 0
            grouped.forEach { (section, commits) ->
                if (no++ > 0 || hasIssues) {
                    append("\n")
                }

                val content = buildString {
                    append(
                        renderer.renderList(
                            commits.map { commit ->
                                renderCommit(commit, config.commitsMaxLength, renderer)
                            }
                        )
                    )
                }

                append("$section:").append("\n\n").append(content).append("\n")
            }
        }
    }

    fun renderCommit(
        commit: SemanticCommit,
        commitsMaxLength: Int,
        renderer: EventRenderer
    ): String {
        return buildString {
            if (!commit.scope.isNullOrBlank()) {
                append(renderer.renderStrong(commit.scope))
                append(" - ")
            }
            append(shortCommitMessage(commit.subject, commitsMaxLength))
        }
    }

    fun getTypeTitle(type: String, config: SemanticChangeLogConfig): SectionTitle =
        config.sections.find { it.type == type }
            ?.title?.let { SectionTitle(it, types[type]?.takeIf { config.emojis }?.emoji) }
            ?: types[type]?.let { type ->
                if (config.emojis) {
                    SectionTitle(type.title, type.emoji)
                } else {
                    SectionTitle(type.title, null)
                }
            }
            ?: SectionTitle(type, null)

    data class SectionTitle(
        val title: String,
        val emoji: String?
    ) {
        override fun toString(): String = if (emoji.isNullOrBlank()) {
            title
        } else {
            "$emoji $title"
        }
    }

}