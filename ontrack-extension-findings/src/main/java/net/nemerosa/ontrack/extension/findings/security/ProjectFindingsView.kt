package net.nemerosa.ontrack.extension.findings.security

import net.nemerosa.ontrack.model.security.ProjectFunction

/**
 * Seeing the findings of a project.
 *
 * Granted by default to every built-in role which has the project view, so that the default
 * behaves as the project view. It is a function of its own so that it can be withheld from a
 * role, for example when the DAST findings of a live deployment are sensitive.
 *
 * Deliberately not a sub-function of `ProjectView`: being granted the findings must not grant
 * the project.
 */
interface ProjectFindingsView : ProjectFunction
