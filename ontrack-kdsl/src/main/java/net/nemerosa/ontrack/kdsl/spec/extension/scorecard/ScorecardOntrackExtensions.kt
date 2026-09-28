package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Management of the estates of the delivery scorecard.
 */
val Ontrack.estates: EstatesMgt get() = EstatesMgt(connector)
