# Promotion level fields

Promotion levels can have configurable typed fields. When a build is promoted, users fill in values for those fields. The values are stored with the promotion run and displayed in the UI wherever the promotion run is shown.

## Field types

`TEXT`
:   Free text input.

`NUMBER`
:   A numeric value.

`BOOLEAN`
:   A true/false value.

`CHOICE`
:   One value chosen from a predefined list of options. The allowed options are part of the field definition.

`LINK`
:   A URL. Displayed as a clickable link in the UI.

## Field properties

`name`
:   Internal identifier used in GraphQL mutations and queries. Must be unique within the promotion level.

`displayName`
:   Human-readable label shown in forms and the UI.

`description`
:   Optional description of what the field represents.

`type`
:   One of the [field types](#field-types) above.

`required`
:   If `true`, the field must have a value when promoting a build. Omitting a required field causes a validation error.

`options`
:   For `CHOICE` fields only: the list of allowed values.

## Configuring fields (UI)

Fields are defined per promotion level from the promotion level management page:

1. On a branch page, click **Promotions** to open the promotion levels management page
2. Select a promotion level
3. Click **Manage fields** in the commands bar
4. In the field editor dialog:
    - Click **Add field** to add a new field row
    - Set the **name** (internal ID, no spaces), **display name** (label shown to users), **type**, and whether it is **required**
    - For **CHOICE** fields, enter the allowed values as a comma-separated list
5. Click **OK** to save — this replaces all existing fields for that promotion level

## Promoting a build with field values (UI)

When a build is promoted to a promotion level that has fields configured:

1. Click the **promote** action (thumb-up icon) on the build page or the branch page
2. The promotion dialog displays one input per field, labelled with their display names
3. Fill in the values — required fields must be completed before submitting
4. For **CHOICE** fields, a dropdown shows the allowed options
5. Click **OK** to confirm — the values are stored on the promotion run

## Configuring fields (GraphQL)

Use the `setPromotionLevelFields` mutation. Calling it replaces **all** existing fields for the promotion level:

```graphql
mutation {
  setPromotionLevelFields(input: {
    promotionLevelId: 42,
    fields: [
      { name: "ticket", displayName: "Ticket", type: TEXT, required: false },
      {
        name: "status",
        displayName: "Status",
        type: CHOICE,
        required: false,
        options: ["Open", "Closed"]
      }
    ]
  }) {
    errors { message }
  }
}
```

Query field definitions on a promotion level:

```graphql
query {
  promotionLevel(id: 42) {
    fields {
      name
      displayName
      type
      required
      options
    }
  }
}
```

## Promoting with field values (GraphQL)

Pass `fieldValues` when creating a promotion run:

```graphql
mutation {
  createPromotionRunById(input: {
    buildId: 123,
    promotion: "GOLD",
    fieldValues: [
      { name: "ticket", value: "TICKET-42" },
      { name: "status",  value: "Open" }
    ]
  }) {
    errors { message }
    promotionRun {
      id
      fieldValues { name value }
    }
  }
}
```

Query field values on an existing promotion run:

```graphql
query {
  promotionRun(id: 99) {
    fieldValues { name value }
    promotionLevel {
      fields { name displayName type }
    }
  }
}
```

## Viewing field values

- **Build page** — hover a promotion run in the _Promotions_ section to see its field values in the popover.
- **Branch page** — hover a promotion run icon in the builds table to see its field values in the popover.

## Validation rules

- Required fields must have a non-empty value when promoting.
- `CHOICE` fields reject values that are not in the configured `options` list.
