# Ant Design

The UI, desktop and `/mobile` alike, is built on **Ant Design 6** (`antd`), which supports React 19
natively. `@ant-design/icons` is a declared dependency of its own; import icons from it, never through
`antd`.

The look is antd 6's default look. `ThemeProvider` only switches between the light and dark
algorithms and sets `cssVar: true` — do not recreate an older look through theme tokens.

## Props to use

antd 6 renamed a number of props. The old names still work but log a deprecation warning, and the
console is expected to stay free of them (see *Known exception* below). The ones this code base
meets:

| Component                  | Write                                          | Not                                  |
|----------------------------|------------------------------------------------|--------------------------------------|
| `Space`                    | `orientation="vertical"`, `separator`          | `direction`, `split`                 |
| `Alert`                    | `title`                                        | `message`                            |
| `Card`                     | `styles={{header: …, body: …}}`, `variant`     | `headStyle`, `bodyStyle`, `bordered` |
| `Modal`, `Drawer`          | `destroyOnHidden`, `styles={{body: …}}`        | `destroyOnClose`, `bodyStyle`        |
| `Drawer`                   | `size` (a number, or `"auto"`)                 | `width`, `height`                    |
| `Drawer` `styles`          | `section`                                      | `content`                            |
| `Select`, `AutoComplete`, `Dropdown` | `popupRender`                        | `dropdownRender`                     |
| `Divider`                  | `orientation="vertical"`                       | `type="vertical"`                    |
| `Tag`                      | `variant="filled"`                             | `bordered={false}`                   |
| `Timeline`                 | `mode="start"\|"end"`; items `title`, `content`, `icon` | `mode="left"\|"right"`; `label`, `children`, `dot` |
| split button               | `Space.Compact` + `Button` + `Dropdown`        | `Dropdown.Button`                    |

`CloseableAlert` and `InlineError` keep a `message` prop: they are our own components, not `Alert`.

## Known exception

`List` / `List.Item` are deprecated in antd 6 but not removed. Replacing them is a redesign rather than
a rename (#1853); until then their warning is the one accepted in the console.

## Testing against antd

- **Selectors.** antd 6 changed a good part of its DOM (`.ant-select-selector` is gone, `Input.Search`
  is a `Space.Compact`). A test reaches an antd control by role or test id, never by an `.ant-*` class:
  `within(screen.getByTestId('…')).getByRole('combobox')` opens a `Select`, and its clear affordance is
  `getByRole('button', {name: 'Clear'})`, acting on `click`.
- **`data-testid` on `Input.Search`** lands on its `Space.Compact` wrapper, not on the `<input>`: type
  into `getByTestId('…').getByRole('searchbox')` (its input is `type="search"`).
- **Confirm dialogs render their title twice**, as the dialog's label and as its heading: match the visible one,
  `getByText(title).filter({visible: true})` (see `confirmBox` in `ontrack-web-tests`).
- **Column titles appear twice in a table** — see *Column titles spanning several columns* below: a
  test id inside a title is found through `getByRole('columnheader')`.
- **`Form.useWatch` is one macro task late.** What it draws shows up after the next task, not
  synchronously: `await act(() => new Promise(resolve => setTimeout(resolve, 0)))`, or a `findBy…`.
- **jsdom gaps.** antd 6 needs `ResizeObserver` and `MessageChannel`, which jest-environment-jsdom
  lacks; `jest.setup.js` provides both for every test file.

## Column titles spanning several columns

antd 6's `Table` sizes its columns from a hidden measure row (`.ant-table-measure-row`) that holds
each column's `title` alone, **ignoring `colSpan`**. A wide title on a narrow column that spans its
neighbours — the toolbar over the range selector in `BranchBuilds` — widens that column to the full
title and leaves it blank. Give the title a class and hide it in the measure row only; the real
header still lays it out across the spanned columns:

```css
.ant-table-measure-row .ot-branch-builds-toolbar {
    display: none;
}
```
