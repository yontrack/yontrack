---
paths:
  - "ontrack-web-core/**"
---

# Frontend rules (`ontrack-web-core`)

## Rules

- **Always** call the GraphQL API with `useQuery`, `useMutation` or `callGraphQL` from
  `@components/services/GraphQL` — the older client hooks were removed in 6.0, and ESLint rejects
  their imports. `useQuery` starts with `loading` false and only flips it inside its effect: when a
  component must never render as "loaded" before the first fetch resolves, use `loading || !finished`.
- **Never** use antd's `List` / `List.Item` — deprecated in antd 6. Use `ItemList` from
  `@components/common/ItemList`; ESLint flags the import.
- **Never** import `Table` from `antd` — always use `Table` from `@components/common/table/Table`,
  which sticks its header by default (#1932). Opt out with `sticky={false}` only for a table nested
  in another table's expanded row or with `showHeader={false}`; ESLint flags the import.
- Write antd 6's prop names, never the deprecated ones (`orientation`, not `direction`, on `Space`;
  `title`, not `message`, on `Alert`; ...) — the table is in `doc/dev-guide/ui/ant-design.md`.
- **Never** access `localStorage` directly — always use the wrapper functions in
  `@components/storage/local`, with a dedicated `get`/`set` (or `is`/`set`) pair for each new
  preference token.
- **Never** store a value in `useState` + `useEffect` when it's purely derived from props/state —
  compute it directly in the render body instead (e.g. `const items = changeLog ? [...] : []`, not
  `useState([])` filled by a `useEffect`). Beyond being an unnecessary extra render, a value that's
  briefly wrong/empty on first render before the effect fires can break children that make first-render
  assumptions — e.g. `GridTable`'s `items` starting empty while `layout` was already fully populated
  made `react-grid-layout` sync its internal layout against 0 children, permanently collapsing every
  widget to a default 1x1 slot once the items arrived a tick later (issue #1634). See `BuildContent`
  for the correct pattern: compute `items` as a plain `const` from already-available props.
- A change here states whether it affects the mobile UI under `/mobile` — see *Definition of done*
  in `CLAUDE.md` and `doc/dev-guide/ui/mobile-impact.md`.

## Patterns

Each one has a page in `doc/dev-guide/ui/` — read it before using the pattern:

| Need                                         | Use                                                         | Page                                           |
|----------------------------------------------|-------------------------------------------------------------|------------------------------------------------|
| Read data / run a mutation                   | `useQuery`, `useMutation`, `callGraphQL`                    | `ui-graphql-call.md`                           |
| A list, a table or a grid of cards           | `ItemList`, our `Table`, `Row` / `Col`, `MobileEntityList`  | `ant-design.md` › *Lists*                      |
| antd props, testing against antd             |                                                             | `ant-design.md`                                |
| A modal form, possibly backed by a mutation  | `useFormDialog` + `<FormDialog>`                            | `ui-form-dialog.md`                            |
| What the user may do on an entity            | `authorizations` field + `isAuthorized(entity, name, action)` | `entity-ui-permissions.md`                   |
| What the user may do globally                | `useContext(UserContext).authorizations`                    | `global-ui-permissions.md`                     |
| Preferences saved in the user profile        | `usePreferences()` / `setPreferences`                       | `ui-user-preferences.md`                       |
| Preferences saved in the browser             | `@components/storage/local`                                 | `ui-local-preferences.md`                      |
| One component refreshing another on a page   | `EventsContext.fireEvent` / `useEventForRefresh`            | `local-events.md`                              |
| Immutable reference data (run statuses, ...) | `useRefData()`                                              | `ui-ref-data.md`                               |
| An entry in the global user menu             | `UserMenuItemExtension` (server) + icon in `UserMenu.js`    | `user-menu.md`                                 |
| A Tools dropdown on an entity page           | `ProjectEntityUserMenuItemExtension` + `userMenuActions`    | `page-tools.md`                                |
| A property's UI                              | `framework/properties/{fqcn}/` — `Icon`, `Display`, `Form`, `FormPrepare` | `framework-properties.md`        |
| An auto-versioning post-processing's UI      | `framework/auto-versioning-post-processing/{id}/Display.js` | `framework-auto-versioning-post-processing.md` |
| Anything under `/mobile`                     |                                                             | `mobile-ui.md`                                 |
