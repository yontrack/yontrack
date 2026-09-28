package net.nemerosa.ontrack.extension.av.retry

import net.nemerosa.ontrack.common.api.APIDescription
import java.time.LocalDateTime

@APIDescription("Retries & reschedules an auto-versioning audit entry is part of")
data class AutoVersioningAuditEntryLineage(
    @APIDescription("If this entry is an automatic retry, its attempt number, starting at 1")
    val retryAttempt: Int?,
    @APIDescription("If this entry is an automatic retry, the maximum number of automatic retries when it was created")
    val retryMax: Int?,
    @APIDescription("If this entry is an automatic retry, UUID of the entry it retries")
    val retryOf: String?,
    @APIDescription("If this entry is an automatic retry, UUID of the first entry of the chain of retries")
    val retryOriginal: String?,
    @APIDescription("If this entry has been retried automatically, UUID of its retry")
    val retryUuid: String?,
    @APIDescription("If this entry has been retried automatically, time its retry is scheduled at")
    val retryAt: LocalDateTime?,
    @APIDescription("If this entry was rescheduled manually, UUID of the entry it reschedules")
    val rescheduledFrom: String?,
    @APIDescription("UUIDs of the entries rescheduling this one manually")
    val rescheduledAs: List<String>,
    @APIDescription("Maximum number of automatic retries currently configured, 0 when disabled")
    val configuredRetryMaxCount: Int,
)
