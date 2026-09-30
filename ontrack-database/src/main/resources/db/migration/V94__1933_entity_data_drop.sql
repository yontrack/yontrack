-- 94. Dropping the ENTITY_DATA table (#1933)

-- V93 moved every row into ENTITY_STORE and emptied the table, and nothing reads or writes it any
-- longer. Its indexes and constraints go with it; no other table references it.
DROP TABLE ENTITY_DATA;
