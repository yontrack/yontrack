package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.model.preferences.ThemeMode
import org.springframework.stereotype.Component

@Component
class GQLEnumThemeMode : AbstractGQLEnum<ThemeMode>(
    ThemeMode::class,
    ThemeMode.values(),
    "Theme selected by a user for the web UI"
)
