package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.structure.defaultDisplayName

fun Event.matchesKeywords(keywords: String?) =
    if (!keywords.isNullOrBlank()) {
        val tokens = keywords.split(" ").map { it.trim().lowercase() }.toSet()
        tokens.all { matchesKeyword(it) }
    } else {
        true
    }

private fun Event.matchesKeyword(keyword: String) =
    entities.values.any { entity -> entity.defaultDisplayName.lowercase() == keyword }
            || extraEntities.values.any { entity -> entity.defaultDisplayName.lowercase() == keyword }
            || values.values.any { it.value.lowercase() == keyword }
