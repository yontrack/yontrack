-- 91. Delivery scorecard: the estates (#1900)
--
-- The vocabulary is the one of CONTEXT.md: an estate is a group of projects selected by labels,
-- every one of them required, read together against a marker and targets.
--
-- The marker of an estate is stored flat: MARKER_KIND is NULL when the estate uses the default
-- marker, PROMOTION with MARKER_LEVEL, or ENVIRONMENT with MARKER_ENVIRONMENT and MARKER_QUALIFIER
-- ('' for the default qualifier).
CREATE TABLE SCORECARD_ESTATES
(
    ID                 SERIAL PRIMARY KEY NOT NULL,
    NAME               VARCHAR(100)       NOT NULL,
    DESCRIPTION        VARCHAR(500)       NULL,
    MARKER_KIND        VARCHAR(20)        NULL, -- PROMOTION | ENVIRONMENT, NULL for the default marker
    MARKER_LEVEL       VARCHAR(120)       NULL,
    MARKER_ENVIRONMENT VARCHAR(120)       NULL,
    MARKER_QUALIFIER   VARCHAR(120)       NULL,
    CONSTRAINT SCORECARD_ESTATES_UQ_NAME UNIQUE (NAME)
);

-- The labels selecting the projects of an estate, all of them required.
-- Deleting a label an estate uses is refused by the application; the cascade is a backstop.
CREATE TABLE SCORECARD_ESTATE_LABELS
(
    ID        SERIAL PRIMARY KEY NOT NULL,
    ESTATE_ID INTEGER            NOT NULL,
    LABEL_ID  INTEGER            NOT NULL,
    CONSTRAINT SCORECARD_ESTATE_LABELS_FK_ESTATE FOREIGN KEY (ESTATE_ID) REFERENCES SCORECARD_ESTATES (ID) ON DELETE CASCADE,
    CONSTRAINT SCORECARD_ESTATE_LABELS_FK_LABEL FOREIGN KEY (LABEL_ID) REFERENCES LABEL (ID) ON DELETE CASCADE,
    CONSTRAINT SCORECARD_ESTATE_LABELS_UQ UNIQUE (ESTATE_ID, LABEL_ID)
);

CREATE INDEX SCORECARD_ESTATE_LABELS_IX_LABEL ON SCORECARD_ESTATE_LABELS (LABEL_ID);

-- Per reading of an estate: the window override (NULL for the one of the settings) and the target
-- (NULL for none: the reading is shown, not judged).
CREATE TABLE SCORECARD_ESTATE_READINGS
(
    ID          SERIAL PRIMARY KEY NOT NULL,
    ESTATE_ID   INTEGER            NOT NULL,
    READING     VARCHAR(100)       NOT NULL,
    WINDOW_DAYS INTEGER            NULL,
    TARGET      DOUBLE PRECISION   NULL,
    CONSTRAINT SCORECARD_ESTATE_READINGS_FK_ESTATE FOREIGN KEY (ESTATE_ID) REFERENCES SCORECARD_ESTATES (ID) ON DELETE CASCADE,
    CONSTRAINT SCORECARD_ESTATE_READINGS_UQ UNIQUE (ESTATE_ID, READING)
);

-- The readings of an estate go with it
ALTER TABLE SCORECARD_READINGS
    ADD CONSTRAINT SCORECARD_READINGS_FK_ESTATE FOREIGN KEY (ESTATE_ID) REFERENCES SCORECARD_ESTATES (ID) ON DELETE CASCADE;
