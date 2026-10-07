-- #2013 Events audit: filtering the events on their time and on a prefix of their user

CREATE INDEX EVENTS_IX_EVENT_TIME ON EVENTS (EVENT_TIME);
CREATE INDEX EVENTS_IX_EVENT_USER ON EVENTS (LOWER(EVENT_USER) text_pattern_ops);
