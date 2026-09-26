# Searching

Yontrack searches everything it knows by name or identifier from one place: the **command
palette**. Open it, type, and pick a result with the keyboard — or go through all the results on
the [search page](#the-search-page).

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
- The last entry, **See all results**, opens the [search page](#the-search-page) with every
  result of your text.

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

## The search page

The search page, `/search`, lists **every result** of a text, best first, 20 per page. It opens
from the **See all results** entry of the palette.

Each result shows its type, its title linking to its page, where it is — `in` its project — and,
when its text matches, an **excerpt** of that text around the matching words.

- **Highlighting** — the words you searched for are highlighted in the titles, and in the
  excerpts of the text: a description, a commit message.
- **Filters** — above the results, one button per type having results, with its count: *All
  (42)*, *Build (30)*, *SCM Commit (12)*… Pressing a type shows its results only, the counts
  of the other types staying there to go to them; *All* shows every type again.
- **Pages** — below the results, once there are more than 20 of them.
- **Searching again** — the search field at the top of the page runs another search, keeping
  the type filter.

The text, the type and the page are all in the address of the page —
`/search?q=billing&type=build&page=2` — so a page of results can be **bookmarked or shared**,
and the browser's back and forward buttons move between your searches, filters and pages. The
person you share it with sees the results their own rights allow.

While the search index is being built, the search page says so, like the palette.

The search page is not available in the [mobile UI](../mobile/index.md) either.

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
   characters, and applies to a type only when it has **fewer than 20** results of the three
   stronger kinds: a word with plenty of real matches does not bring in the ones merely similar
   to it. A type's results and count are then the same on every page, and in the palette.

Results are ranked by how they match, then by how well within the same kind of match. Between
equally good results, the **most recently updated** comes first — the latest build before an
older one with the same name. No type of result is preferred to another: an exact build name
comes before a vague project match.

You only ever find what you are allowed to see: results in projects you cannot view are not
counted, nor listed.

## Counts

Each type counts its results up to **1000**. Past that, its count is shown as **1000+** —
*Commits (1000+)* in the palette, *Build (1000+)* among the filters of the search page — and the
totals made of it too: *All (3000+)*, *3000+ results*. The pages of the search page go as far as
the counted results: 50 pages for a type counting 1000+.

Past 1000 results of a type, the ones ranked are the 1000 which match in the strongest way,
the **most recently updated** first among those matching the same way. An older result which
would have ranked higher is then not shown: type more of what you are looking for to narrow the
search down.

The administrators can change the cap with the `ontrack.config.search.count-cap` setting (see the
[search index](../operations/search-index.md#settings)).
