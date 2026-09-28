package net.nemerosa.ontrack.extension.scorecard.security

import net.nemerosa.ontrack.model.security.GlobalFunction

/**
 * Creating, updating, deleting and recomputing the estates.
 *
 * Granted to the built-in roles which manage the labels, since the labels are what select the
 * projects of an estate. Reading the estates needs no function: every authenticated user can.
 */
interface EstateManagement : GlobalFunction
