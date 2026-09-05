/**
 * Guards on the screenshot catalogue and on the file naming.
 *
 * Pure: no browser, no network, no `page` fixture. The `capture` project depends on this one,
 * so these run first and fail in milliseconds - a duplicate slug would otherwise be found by
 * one shot silently overwriting another, after three minutes of driving the demo.
 *
 * It is also the only part of this directory that can be run without a demo to point at:
 *
 *     npx playwright test --config playwright.screenshots.config.js --project guard
 */

const {test, expect} = require('@playwright/test')
const {screenshotFileName, assertVersion, SLUG_PATTERN} = require('./naming')
const {catalogue} = require('./catalogue')

test.describe('file naming', () => {

    test('builds the <version>-<slug>.png name the wiki expects', () => {
        expect(screenshotFileName('5.3.0', 'dashboard')).toBe('5.3.0-dashboard.png')
    })

    test('accepts a pre-release version', () => {
        expect(screenshotFileName('5.3.0-rc-41', 'build')).toBe('5.3.0-rc-41-build.png')
    })

    test('rejects a missing version', () => {
        expect(() => screenshotFileName('', 'dashboard')).toThrow(/version/i)
        expect(() => screenshotFileName(undefined, 'dashboard')).toThrow(/version/i)
    })

    test('rejects a version that would escape the output directory', () => {
        expect(() => screenshotFileName('../5.3.0', 'dashboard')).toThrow(/version/i)
        expect(() => screenshotFileName('5.3.0/x', 'dashboard')).toThrow(/version/i)
    })

    test('rejects a missing slug', () => {
        expect(() => screenshotFileName('5.3.0', '')).toThrow(/slug/i)
        expect(() => screenshotFileName('5.3.0', undefined)).toThrow(/slug/i)
    })

    test('checks a version on its own, before anything expensive happens', () => {
        expect(assertVersion('5.3.0-rc-41')).toBe('5.3.0-rc-41')
        expect(() => assertVersion('')).toThrow(/version/i)
        expect(() => assertVersion('../5.3.0')).toThrow(/version/i)
    })

    test('rejects a slug that is not lower-case kebab', () => {
        expect(() => screenshotFileName('5.3.0', 'Dashboard')).toThrow(/slug/i)
        expect(() => screenshotFileName('5.3.0', 'my_slug')).toThrow(/slug/i)
        expect(() => screenshotFileName('5.3.0', 'a--b')).toThrow(/slug/i)
        expect(() => screenshotFileName('5.3.0', '-a')).toThrow(/slug/i)
    })
})

test.describe('catalogue', () => {

    test('is not empty', () => {
        expect(catalogue.length).toBeGreaterThan(0)
    })

    test('has unique slugs', () => {
        const slugs = catalogue.map(it => it.slug)
        expect(new Set(slugs).size).toBe(slugs.length)
    })

    test('has a well-formed slug on every entry', () => {
        for (const entry of catalogue) {
            expect(entry.slug, `slug of ${JSON.stringify(entry.slug)}`).toMatch(SLUG_PATTERN)
        }
    })

    test('has a rooted path on every entry', () => {
        for (const entry of catalogue) {
            expect(entry.path, `path of ${entry.slug}`).toMatch(/^\//)
        }
    })

    test('has a description on every entry', () => {
        for (const entry of catalogue) {
            expect(entry.description, `description of ${entry.slug}`).toBeTruthy()
        }
    })

    test('has a ready function on every entry', () => {
        for (const entry of catalogue) {
            expect(typeof entry.ready, `ready of ${entry.slug}`).toBe('function')
        }
    })
})
