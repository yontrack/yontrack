-- Flyway runs as 'postgres' (superuser) but the app connects as 'yontrack'.
-- Tables created by V78 are missing grants for the 'yontrack' user.
GRANT ALL ON TABLE PROMOTION_LEVEL_FIELDS TO yontrack;
GRANT ALL ON TABLE PROMOTION_RUN_FIELD_VALUES TO yontrack;
GRANT USAGE, SELECT ON SEQUENCE promotion_level_fields_id_seq TO yontrack;
GRANT USAGE, SELECT ON SEQUENCE promotion_run_field_values_id_seq TO yontrack;

-- Ensure all future tables and sequences created by Flyway (as 'postgres') are automatically accessible to 'yontrack'.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON TABLES TO yontrack;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO yontrack;
