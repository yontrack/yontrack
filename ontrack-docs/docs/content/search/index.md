# Searching

Yontrack searches everything it knows by name or identifier from one place: the **command
palette**. Open it, type, and pick a result with the keyboard.

## The command palette

Open it from any page of the desktop UI:

- press **⌘K** on a Mac, or **Ctrl+K** elsewhere — this works even while you are typing in a
  field;
- press **`/`** when you are not typing in a field;
- or click the **Search…** button of the navigation bar.

### Before you type

The palette lists the **last 10 projects, branches and builds you visited**, the most recent
first, so that going back to where you just were is two keys away. The list is kept by your
browser: another browser, or another device, has its own.

### As you type

- **Pages of the user menu** whose name matches what you typed — *Settings*,
  *Notifications*, *Auto-versioning audit*… — are listed first. You only see the ones your
  rights give you access to, as in the menu itself. Every word you type must match the name of
  the page or of its menu group: `config` lists every configuration page.
- From **2 characters** on, the palette searches: it shows the **3 best results of each type**
  — projects, branches, builds, releases, build links, Git branches, commits, issues, SCM
  catalog entries, security findings — grouped by type. Each group says how many results its
  type has in total.
- The last entry, **See all results**, opens the search page with every result of your text.

While the search index is being built — after an upgrade, for example — the palette says so,
and shows what has been indexed so far.

### Keyboard

| Key                            | What it does                                  |
|--------------------------------|-----------------------------------------------|
| ⌘K / Ctrl+K                    | Opens the palette, from anywhere              |
| `/`                            | Opens the palette, when not typing in a field |
| ↑ ↓                            | Moves to the previous or next entry           |
| Enter                          | Opens the entry                               |
| ⌘Enter / Ctrl+Enter            | Opens the entry in a new tab                  |
| Esc                            | Closes the palette                            |

A click opens an entry too — and a ⌘ or Ctrl click opens it in a new tab.

The palette is not available in the [mobile UI](../mobile/index.md), which has no global search.

## How results are matched

The search looks at the **names and identifiers** of what it indexes — project, branch and
build names, display names, release labels, commit hashes, issue keys, finding identifiers —
and at their **text**, such as descriptions and commit messages. It ignores the case.

A result matches in one of four ways, the strongest first:

1. **Exact** — an identifier is exactly what you typed: `1.2.3`, `release/4.1`, a full commit
   hash, `CVE-2021-44228`.
2. **Prefix** — an identifier or a name starts with what you typed: `rel` finds `release/4.1`,
   a few characters of a hash find the commit.
3. **Full text** — the words you typed are in the text: a word of a commit message, of a
   description.
4. **Similar** — close enough to what you typed, to forgive a typo. This one starts at 3
   characters.

Results are ranked by how they match, then by how well within the same kind of match. Between
equally good results, the **most recently updated** comes first — the latest build before an
older one with the same name. No type of result is preferred to another: an exact build name
comes before a vague project match.

You only ever find what you are allowed to see: results in projects you cannot view are not
counted, nor listed.
