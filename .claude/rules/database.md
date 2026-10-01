---
paths:
  - "ontrack-database/**"
---

# Database rules (Flyway)

Migrations are SQL files in `ontrack-database/src/main/resources/db/migration/`, named
`V{N}__short_description.sql` where `N` is the next sequential number.

- **Never** modify an existing migration file — always add a new one.
- **Never** put a migration in a patch release. A patch is cherry-picked onto a `release/X.Y`
  branch while `main` keeps moving, so a `V82` on the patch branch and an unrelated `V82` on `main`
  give any user upgrading from the patch to the next minor a checksum conflict. If a fix needs a
  schema change it is not a patch — see `doc/dev-guide/patch-release.md`.
- **Always** add `ON DELETE CASCADE` on FK references to entity tables.
- Use `SERIAL PRIMARY KEY NOT NULL` for auto-increment primary keys.

```sql
-- V75__my_new_feature.sql
CREATE TABLE MY_TABLE (
    ID          SERIAL PRIMARY KEY NOT NULL,
    NAME        VARCHAR(100)       NOT NULL,
    ENTITY_ID   INTEGER            NOT NULL REFERENCES ENTITIES (ID) ON DELETE CASCADE
);
CREATE INDEX MY_TABLE_ENTITY_IDX ON MY_TABLE (ENTITY_ID);
```
