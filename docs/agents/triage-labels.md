# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

Edit the right-hand column to match whatever vocabulary you actually use.

## Notes for this repo

- `wontfix` already exists in `yontrack/yontrack`. The other four labels do not yet exist and are created on first use
  (`gh label create <name>`).
- The repo's existing `status:*` labels (`status:wip`, `status:ready`, `status:tomerge`, `status:released`,
  `status:waiting-feedback`) track the **lifecycle** of an issue that has already been accepted. They are not triage roles
  and must not be substituted for the labels above — the two vocabularies coexist.
