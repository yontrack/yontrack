-- Indexes supporting the lookup of notification records by the entity targeted by their event.
--
-- `NotificationRecordFilter.eventEntityId` (see DefaultNotificationRecordingService) filters on
--   (DATA->'event'->'entities'->'<TYPE>'->>'id')::int = ?
-- and STORAGE carries no index other than its (STORE, NAME) primary key, so that lookup used to
-- scan the whole table. It is on the hot path of the `workflowInstances` field, which is exposed on
-- every project entity type -- hence one index per type.
--
-- The indexes are partial on the notification record store so that the rest of STORAGE, which is
-- shared by every extension, pays no write cost for them.
--
-- Two costs are accepted here, deliberately:
--
--  * Writes. An event carries several entities at once (a promotion event names its project,
--    branch, build, promotion level and promotion run), so recording one notification maintains
--    several of these indexes. That is a small, bounded cost on an insert-only path, traded against
--    a read that was scanning the whole of STORAGE.
--  * Upgrade. CREATE INDEX takes a SHARE lock, so writes to STORAGE are blocked while these are
--    built. CONCURRENTLY is not used because Flyway runs each script in a transaction, and moving
--    this one out of a transaction would leave a failed build to be cleaned up by hand. On an
--    install with a large STORAGE table, expect this migration to take a noticeable moment.

CREATE INDEX STORAGE_NOTIFICATION_EVENT_PROJECT
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'PROJECT' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';

CREATE INDEX STORAGE_NOTIFICATION_EVENT_BRANCH
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'BRANCH' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';

CREATE INDEX STORAGE_NOTIFICATION_EVENT_PROMOTION_LEVEL
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'PROMOTION_LEVEL' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';

CREATE INDEX STORAGE_NOTIFICATION_EVENT_VALIDATION_STAMP
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'VALIDATION_STAMP' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';

CREATE INDEX STORAGE_NOTIFICATION_EVENT_BUILD
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'BUILD' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';

CREATE INDEX STORAGE_NOTIFICATION_EVENT_PROMOTION_RUN
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'PROMOTION_RUN' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';

CREATE INDEX STORAGE_NOTIFICATION_EVENT_VALIDATION_RUN
    ON STORAGE (((DATA -> 'event' -> 'entities' -> 'VALIDATION_RUN' ->> 'id')::int))
    WHERE STORE = 'net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord';
