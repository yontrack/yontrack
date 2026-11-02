package net.nemerosa.ontrack.model.structure

import java.time.Duration

data class TokenOptions(
    val name: String,
    val validity: Duration? = null,
    val forceUnlimited: Boolean = false,
    val readOnly: Boolean = false,
)