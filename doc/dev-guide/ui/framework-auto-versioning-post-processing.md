# Auto-Versioning Post-Processing UI Components

Each backend `PostProcessing<T>` extension that users can configure in auto-versioning
rules needs a matching frontend **Display component** so the configured post-processing
is rendered in the UI.

## How It Works

The parent component
`ontrack-web-core/components/extension/auto-versioning/AutoVersioningPostProcessing.js`
uses the `Dynamic` loader to resolve the display component at runtime:

```javascript
<Dynamic
    path={`framework/auto-versioning-post-processing/${type}/Display`}
    props={config}
/>
```

- `type` is the value of `PostProcessing.id` from the backend Kotlin class.
- `config` is the raw JSON config object — all its fields are spread as individual props
  into `Display.js`.

## File Location

```
ontrack-web-core/components/framework/auto-versioning-post-processing/
└── {id}/
    └── Display.js      ← the only required file
```

`{id}` must match `PostProcessing.id` exactly (e.g. `github`, `jenkins`).

> Unlike property type UI components, **only `Display.js` is needed** — there is no
> Icon.js, Form.js, or FormPrepare.js for post-processing components.

## Props Contract

`Display.js` receives all fields of the config data class as individual props. For example,
if the backend config is:

```kotlin
data class MyPostProcessingConfig(
    val dockerImage: String,
    val dockerCommand: String,
    val commitMessage: String,
    val myField: String?,
    val parameters: List<MyParam> = emptyList(),
)
```

Then `Display.js` receives `{ dockerImage, dockerCommand, commitMessage, myField, parameters }`.

## Implementation Template

```javascript
import {Descriptions, Space, Typography} from "antd";

export default function Display({
    dockerImage,
    dockerCommand,
    commitMessage,
    myField,
    parameters = [],
}) {
    const parametersItems = parameters.map(({name, value}) => ({
        key: name,
        label: name,
        children: <Typography.Text code>{value}</Typography.Text>,
    }))

    const items = [
        {
            key: 'dockerImage',
            label: "Docker image",
            children: <Typography.Text code>{dockerImage}</Typography.Text>,
            span: 12,
        },
        {
            key: 'dockerCommand',
            label: "Docker command",
            children: <Typography.Text code>{dockerCommand}</Typography.Text>,
            span: 12,
        },
        {
            key: 'commitMessage',
            label: "Commit message",
            children: <Typography.Text code>{commitMessage}</Typography.Text>,
            span: 12,
        },
        {
            key: 'myField',
            label: "My field",
            children: <Typography.Text code>{myField}</Typography.Text>,
            span: 12,
        },
        {
            key: 'parameters',
            label: "Extra parameters",
            children: <Descriptions items={parametersItems}/>,
            span: 12,
        },
    ]

    return (
        <>
            <Space direction="vertical">
                <Typography.Text code>my-id</Typography.Text>
                <Descriptions items={items} span={12}/>
            </Space>
        </>
    )
}
```

## Conventions

- Always include a `<Typography.Text code>{id}</Typography.Text>` header so the user can
  see which post-processing type is configured.
- Use `span: 12` on each item for a two-column layout.
- Optional fields that may be `null`/`undefined` will simply render as empty — no need for
  conditional guards unless you want to hide the row entirely.
- If the config has a `parameters` list (name/value pairs), render it as a nested
  `<Descriptions>` just like the Jenkins and GitHub examples.

## Existing Examples

| Type    | File                                                                                                        |
|---------|-------------------------------------------------------------------------------------------------------------|
| github  | `ontrack-web-core/components/framework/auto-versioning-post-processing/github/Display.js`                   |
| jenkins | `ontrack-web-core/components/framework/auto-versioning-post-processing/jenkins/Display.js`                  |

## Backend Reference

The `id` is defined in the Kotlin class implementing `PostProcessing<T>`:

```kotlin
@Component
class MyPostProcessing(
    extensionFeature: MyExtensionFeature,
) : AbstractExtension(extensionFeature), PostProcessing<MyPostProcessingConfig> {

    override val id: String = "my-id"   // ← must match the folder name
    override val name: String = "My post processing"

    // ...
}
```
