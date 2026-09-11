import {branchNamePattern, buildDisplayNamePattern} from "@components/mobile/entities/namePatterns"

describe('the branch name filter', () => {

    it('is nothing at all until something is typed', () => {
        // `null`, never `''`: the server's own filter tests the argument with
        // `isNullOrBlank`, and the screen must not call itself filtered when it
        // is showing the whole list.
        expect(branchNamePattern('')).toBeNull()
        expect(branchNamePattern('   ')).toBeNull()
        expect(branchNamePattern(undefined)).toBeNull()
    })

    it('ignores the spaces around what was typed', () => {
        expect(branchNamePattern('  main  ')).toEqual('(?i)main')
    })

    it('matches without regard to case', () => {
        // The project list filters through an `ILIKE`, so a phone user typing
        // "MAIN" there finds `main`. `~` is case-sensitive, and the two screens
        // behaving differently would read as one of them being broken.
        expect(branchNamePattern('MAIN')).toEqual('(?i)MAIN')
    })

    it('matches anywhere in the name, not only at the start', () => {
        // Unanchored on purpose: the server matches with `~`, which already
        // searches rather than tests the whole string. Nothing here anchors it.
        const pattern = branchNamePattern('lease')
        expect(pattern.startsWith('^')).toBe(false)
        expect(pattern.endsWith('$')).toBe(false)
    })

    it('takes what was typed literally', () => {
        // The argument reaches Postgres as a POSIX regular expression. A branch
        // name is full of characters that mean something there - `release/1.0`
        // has a dot, and a stray `(` is not a filter that matches nothing but a
        // query that fails outright.
        expect(branchNamePattern('release/1.0')).toEqual('(?i)release/1\\.0')
        expect(branchNamePattern('feature(x)')).toEqual('(?i)feature\\(x\\)')
        expect(branchNamePattern('a+b')).toEqual('(?i)a\\+b')
        expect(branchNamePattern('a|b')).toEqual('(?i)a\\|b')
        expect(branchNamePattern('a[b]')).toEqual('(?i)a\\[b\\]')
        expect(branchNamePattern('a*')).toEqual('(?i)a\\*')
        expect(branchNamePattern('a?')).toEqual('(?i)a\\?')
        expect(branchNamePattern('a{2}')).toEqual('(?i)a\\{2\\}')
        expect(branchNamePattern('a\\b')).toEqual('(?i)a\\\\b')
        expect(branchNamePattern('^a$')).toEqual('(?i)\\^a\\$')
    })

    it('produces a pattern that actually matches what was typed', () => {
        // The escaping above is only right if the result still finds the branch.
        // Checked with JavaScript's own engine, which shares the metacharacters
        // that matter here with the server's.
        const names = ['release/1.0', 'feature(x)', 'a+b', 'main']
        names.forEach(name => {
            const pattern = branchNamePattern(name)
            expect(new RegExp(pattern.replace('(?i)', ''), 'i').test(name)).toBe(true)
        })
    })
})

describe('the build name filter', () => {

    it('is nothing at all until something is typed', () => {
        // Same reason as above, and the same consequence: the repository skips
        // the criterion entirely when `withDisplayName` is null or blank.
        expect(buildDisplayNamePattern('')).toBeNull()
        expect(buildDisplayNamePattern('   ')).toBeNull()
        expect(buildDisplayNamePattern(undefined)).toBeNull()
    })

    it('ignores the spaces around what was typed', () => {
        expect(buildDisplayNamePattern('  rc  ')).toEqual('rc')
    })

    it('carries no case flag, because the server already ignores case', () => {
        // The one difference from the branch filter, and it is the server's:
        // `withDisplayName` is matched with `~*` rather than `~`, so a `(?i)`
        // here would be a prefix that says nothing.
        expect(buildDisplayNamePattern('1.4.0')).not.toContain('(?i)')
    })

    it('takes what was typed literally', () => {
        // A version is mostly dots, and a dot in a regular expression matches
        // anything - `1.4.0` would otherwise find `104x0`. And a lone `(` is
        // worse than either: the repository rejects the pattern and the provider
        // answers with an empty page, so the screen says "No build matches"
        // about a search that never ran.
        expect(buildDisplayNamePattern('1.4.0')).toEqual('1\\.4\\.0')
        expect(buildDisplayNamePattern('1.4.0-rc.1+build')).toEqual('1\\.4\\.0-rc\\.1\\+build')
        expect(buildDisplayNamePattern('build(2)')).toEqual('build\\(2\\)')
        expect(buildDisplayNamePattern('^a$')).toEqual('\\^a\\$')
    })

    it('produces a pattern that actually matches what was typed', () => {
        const names = ['1.4.0', '1.4.0-rc.1+build', '20260901055547-36']
        names.forEach(name => {
            expect(new RegExp(buildDisplayNamePattern(name), 'i').test(name)).toBe(true)
        })
    })

    it('does not match a version it only looks like', () => {
        // The point of the escaping, stated as the behaviour a user would
        // notice: typing a version must not bring back a different one.
        expect(new RegExp(buildDisplayNamePattern('1.4.0')).test('104x0')).toBe(false)
    })
})
