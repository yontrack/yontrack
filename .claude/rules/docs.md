---
paths:
  - "ontrack-docs/**"
---

# User documentation rules (`ontrack-docs`)

- User documentation lives in **mkdocs** under `ontrack-docs/docs/content/`. A new page must be
  added to the `nav:` in `ontrack-docs/mkdocs.yml` or it will not be reachable.
- **Never** hand-edit `ontrack-docs/docs/content/generated/` — it is gitignored and rebuilt by the
  `ontrack-docs` integration tests from `@APIDescription` and the event/metric/property declarations.
  To change generated docs, change the annotations, then run `./gradlew :ontrack-docs:integrationTest`.
- Verify docs changes with `./gradlew :ontrack-docs:buildDocs`, which renders the site into
  `ontrack-docs/site/` and surfaces broken links and missing nav entries.
- A commit touching `ontrack-docs/` never carries `[skip ci]` — the `docs` job is what catches
  broken links and missing nav entries.
