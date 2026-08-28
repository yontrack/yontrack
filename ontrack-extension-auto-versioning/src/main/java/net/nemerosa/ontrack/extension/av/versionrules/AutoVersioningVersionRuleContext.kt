package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder

/**
 * Version change submitted to an [AutoVersioningVersionRule].
 *
 * This is a context object rather than a list of parameters so that the extension point can grow
 * a field without breaking the external implementations of [AutoVersioningVersionRule].
 *
 * @property order Auto-versioning order being processed
 * @property path Path of the target file being checked
 * @property currentVersion Version currently present in the target file
 * @property targetVersion Version the auto-versioning order wants to set in the target file
 */
data class AutoVersioningVersionRuleContext(
    val order: AutoVersioningOrder,
    val path: String,
    val currentVersion: String,
    val targetVersion: String,
)
