-- Every row
DELETE FROM EVENTS;
-- Some rows only: allowed
DELETE FROM EVENTS WHERE EVENT_TYPE = 'removed_event';
