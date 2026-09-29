-- 93. Moving the entity data into the entity store (#1925)

-- Every ENTITY_DATA row becomes an ENTITY_STORE record, for the same entity:
-- * its NAME becomes the STORE
-- * the record is named 'default' (EntityStore.DEFAULT_NAME)
-- * its JSON value is kept as it is
-- When an entity has the same name twice, the most recent row wins.
-- A row without any value has nothing to move.
INSERT INTO ENTITY_STORE (PROJECT, BRANCH, PROMOTION_LEVEL, VALIDATION_STAMP, BUILD, PROMOTION_RUN, VALIDATION_RUN,
                          STORE, NAME, DATA)
SELECT PROJECT, BRANCH, PROMOTION_LEVEL, VALIDATION_STAMP, BUILD, PROMOTION_RUN, VALIDATION_RUN, NAME, 'default', JSON_VALUE
FROM (SELECT DISTINCT ON (PROJECT, BRANCH, PROMOTION_LEVEL, VALIDATION_STAMP, BUILD, PROMOTION_RUN, VALIDATION_RUN, NAME) *
      FROM ENTITY_DATA
      WHERE JSON_VALUE IS NOT NULL
        AND COALESCE(PROJECT, BRANCH, PROMOTION_LEVEL, VALIDATION_STAMP, BUILD, PROMOTION_RUN, VALIDATION_RUN) IS NOT NULL
      ORDER BY PROJECT, BRANCH, PROMOTION_LEVEL, VALIDATION_STAMP, BUILD, PROMOTION_RUN, VALIDATION_RUN, NAME, ID DESC) latest
-- Keeping the order in which the rows were created, which the "most recent first" queries rely on
ORDER BY ID
ON CONFLICT DO NOTHING;

-- Nothing reads ENTITY_DATA any longer
DELETE
FROM ENTITY_DATA;
