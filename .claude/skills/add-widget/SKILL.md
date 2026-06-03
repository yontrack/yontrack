---
name: add-widget
description: Scaffold a new Yontrack dashboard widget end-to-end — Kotlin AbstractWidget class with config data class, and two frontend components (display + form). Use when asked to create a new dashboard widget.
user-invocable: true
---

# /add-widget — Add a Yontrack Dashboard Widget

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for the widget name in PascalCase (e.g. `MyFeature`). If not provided, ask the user for:
1. Widget name (PascalCase, e.g. `MyFeature`)
2. A short human-readable name and description
3. The config fields it needs (name, type, default value) — or none
4. The preferred height (default 20)

Derive:
- `{Name}` — the PascalCase widget name (e.g. `BranchStatuses`)
- `{key}` — `home/{Name}` (e.g. `home/BranchStatuses`)
- `{KtFile}` — `{Name}Widget.kt`
- `{JsWidget}` — `{Name}Widget.js`
- `{JsForm}` — `{Name}WidgetForm.js`

---

## Step 1 — Kotlin widget class

Create `ontrack-model/src/main/java/net/nemerosa/ontrack/model/dashboards/widgets/{Name}Widget.kt`:

```kotlin
package net.nemerosa.ontrack.model.dashboards.widgets

import org.springframework.stereotype.Component

@Component
class {Name}Widget : AbstractWidget<{Name}WidgetConfig>(
    key = "home/{Name}",
    name = "{Human name}",
    description = "{Description}",
    defaultConfig = {Name}WidgetConfig(),
    preferredHeight = 20,
)

data class {Name}WidgetConfig(
    // add config fields here, e.g.:
    // val count: Int = 10,
) : WidgetConfig
```

- The `key` value **must** match `"home/{Name}"` exactly — it is used by the frontend to locate the JS components.
- If the widget has no configuration fields, keep the data class empty.
- Add `val refreshInterval: Duration = Duration.ZERO` (and `import java.time.Duration`) if the widget should support auto-refresh.
- For complex configs, nest additional data classes inside `{Name}WidgetConfig` or extract them to the same file.

**Examples:**
- Simple (one int field): `ontrack-model/.../dashboards/widgets/LastActiveProjectsWidget.kt`
- No config: `ontrack-model/.../dashboards/widgets/FavouriteProjectsWidget.kt`
- Complex config: `ontrack-model/.../dashboards/widgets/BranchStatusesWidget.kt`

---

## Step 2 — Display component

Create `ontrack-web-core/components/widgets/home/{Name}Widget.js`:

```javascript
import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
// import other hooks / components as needed

export default function {Name}Widget({
    // destructure all config fields here, e.g.:
    // count = 10,
}) {
    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle("{Human name}")
    }, [])

    // Fetch and render data here

    return (
        <>
            {/* widget content */}
        </>
    )
}
```

Key rules:
- Props mirror the config fields of `{Name}WidgetConfig` (Kotlin field names, camelCase).
- Call `setTitle(...)` via `DashboardWidgetCellContext` so the cell header shows a meaningful title.
- For data fetching prefer `useQuery` from `@components/services/GraphQL` (declarative) over manual `useEffect` + `useGraphQLClient`, unless imperative fetching is needed.
- Wrap content in `<PaddedContent>` (`@components/common/PaddedContent`) when the widget displays a list or table that needs padding.

**Examples:**
- `ontrack-web-core/components/widgets/home/LastActiveProjectsWidget.js` (simple, useQuery)
- `ontrack-web-core/components/widgets/home/BranchStatusesWidget.js` (complex, multi-query)

---

## Step 3 — Form component

Create `ontrack-web-core/components/widgets/home/{Name}WidgetForm.js`:

**When the widget has config fields:**

```javascript
import {Form, InputNumber} from "antd";
import {useContext} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";

export default function {Name}WidgetForm({
    // destructure all config fields here (same as widget component)
    // count = 10,
}) {
    const {widgetEditionForm} = useContext(DashboardWidgetCellContext)

    return (
        <>
            <Form
                layout="vertical"
                form={widgetEditionForm}
            >
                {/* one Form.Item per config field, e.g.: */}
                <Form.Item
                    name="count"
                    label="Number of items to display"
                    initialValue={count}
                >
                    <InputNumber min={1} max={100} step={1}/>
                </Form.Item>
            </Form>
        </>
    )
}
```

**When the widget has no config fields:**

```javascript
import {Typography} from "antd";

export default function {Name}WidgetForm() {
    return (
        <>
            <Typography.Text type="secondary">
                No configuration is needed.
            </Typography.Text>
        </>
    )
}
```

Key rules:
- Always use `<Form layout="vertical" form={widgetEditionForm}>` — never create a new form instance.
- Each `Form.Item` `name` must exactly match the corresponding Kotlin config field name (camelCase).
- Pass the current prop value as `initialValue` on each `Form.Item` so the form is pre-populated when opened.
- For list-of-objects config fields, use `<Form.List>` (see `BranchStatusesWidgetForm.js` for an example).
- Add a `refreshInterval` field with `<SelectInterval/>` from `@components/common/SelectInterval` if the config includes `refreshInterval`.

**Examples:**
- `ontrack-web-core/components/widgets/home/LastActiveProjectsWidgetForm.js` (simple)
- `ontrack-web-core/components/widgets/home/FavouriteProjectsWidgetForm.js` (no config)
- `ontrack-web-core/components/widgets/home/BranchStatusesWidgetForm.js` (complex with Form.List)

---

## Step 4 — Final checklist

- [ ] `{Name}Widget.kt` created with `key = "home/{Name}"` and `@Component`
- [ ] `{Name}WidgetConfig` data class implements `WidgetConfig`
- [ ] `{Name}Widget.js` created; props match config fields; `setTitle` called
- [ ] `{Name}WidgetForm.js` created; `widgetEditionForm` used; all `Form.Item` names match config fields
- [ ] Verify that the widget key `home/{Name}` matches the JS file paths (`{Name}Widget.js` / `{Name}WidgetForm.js`)
