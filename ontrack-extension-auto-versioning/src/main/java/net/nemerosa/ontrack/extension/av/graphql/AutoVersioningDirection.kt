package net.nemerosa.ontrack.extension.av.graphql

@Deprecated("Removed in V7. Use AutoVersioningRefRole instead. See #2130")
enum class AutoVersioningDirection(val refRole: AutoVersioningRefRole) {
    UP(AutoVersioningRefRole.DEPENDENCY),
    DOWN(AutoVersioningRefRole.CONSUMER)
}
