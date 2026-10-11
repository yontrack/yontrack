-- Fixture of TwoPhaseMigrationsIT (#2057): tables which a previous version reads
CREATE TABLE IT_LEGACY
(
    ID   SERIAL PRIMARY KEY NOT NULL,
    NAME TEXT               NOT NULL
);
INSERT INTO IT_LEGACY (NAME) VALUES ('legacy');

CREATE TABLE IT_OTHER
(
    ID SERIAL PRIMARY KEY NOT NULL
);
