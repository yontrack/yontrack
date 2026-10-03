package net.nemerosa.ontrack.extension.audittrail.security

import net.nemerosa.ontrack.model.security.ProjectFunction

/**
 * Deleting the evidences of the validation runs of a project.
 *
 * Granted to the project owner and to the administrator only — never to those who create the
 * validation runs, so that a CI token which attaches evidence cannot erase it.
 */
interface EvidenceDelete : ProjectFunction
