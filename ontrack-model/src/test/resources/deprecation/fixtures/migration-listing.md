# Migration to V6

## Newly deprecated

* `Fixture.oldField` — use `Fixture.newField` instead.
* `Query.fixture(token)` — use `Query.fixture(query)` instead.
* `POST /rest/old` — use `PUT /rest/new` instead.

Unqualified, the `info` name does not name the `HookResponse` field.
