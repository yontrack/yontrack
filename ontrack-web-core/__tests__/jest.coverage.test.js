/**
 * @jest-environment node
 */

import fs from 'fs'
import path from 'path'
import config from '../jest.coverage'

/**
 * Guards the coverage settings of `jest.config.js` (#1820).
 *
 * The figure `coverage/coverage-summary.json` carries is only meaningful if its *denominator* is
 * every production source, so that a file no test imports counts as 0% - exactly as JaCoCo counts
 * an untouched class. Jest's default is the opposite: with no `collectCoverageFrom` it reports
 * only the files a test happened to load, and the percentage then measures the tests rather than
 * the code.
 *
 * That makes the explicit list a thing that silently rots: adding a production source root and
 * forgetting to declare it deflates the denominator, and nothing fails. This test is the alarm.
 * When it fires on a new root, either add the root to `collectCoverageFrom` or add it below with
 * the reason it carries no production code.
 *
 * It asserts against `jest.coverage.js` rather than against `jest.config.js`, because the latter
 * cannot be required from inside a running suite: its first line pulls in `next/jest`, which needs
 * `TextDecoder` and an ESM-capable VM. It runs in the `node` environment because it walks the
 * source tree with `fs`.
 */

const root = path.join(__dirname, '..')

/**
 * Root entries that hold no production JavaScript, each with the reason. Anything not listed here
 * and not declared in `collectCoverageFrom` fails the test.
 */
const NOT_PRODUCTION = {
    '__tests__': 'the test tree itself',
    'build': 'Gradle output',
    'coverage': 'the coverage report this very configuration writes',
    'node_modules': 'dependencies',
    'public': 'static assets',
    'reports': 'JUnit output for CI',
    'styles': 'CSS only',
    'jest.config.js': 'test configuration',
    'jest.coverage.js': 'test configuration',
    'jest.setup.js': 'test configuration',
    'next.config.js': 'build configuration',
    '.next': 'Next.js build output',
}

/** The source roots and single files `collectCoverageFrom` declares, without their globs. */
const declaredSources = (patterns) =>
    patterns
        .filter(pattern => !pattern.startsWith('!'))
        .map(pattern => pattern.split('/')[0])

const hasJavaScript = (entry) => {
    const full = path.join(root, entry)
    if (!fs.existsSync(full)) return false
    if (fs.statSync(full).isFile()) return /\.jsx?$/.test(entry)
    const walk = (dir) => fs.readdirSync(dir, {withFileTypes: true}).some(child =>
        child.isDirectory()
            ? child.name !== 'node_modules' && walk(path.join(dir, child.name))
            : /\.jsx?$/.test(child.name)
    )
    return walk(full)
}

describe("Jest coverage configuration", () => {

    it("collects coverage from explicitly declared sources", () => {
        expect(config.collectCoverageFrom).toBeDefined()
        expect(config.collectCoverageFrom.length).toBeGreaterThan(0)
    })

    it("declares every root holding production JavaScript", () => {
        const declared = declaredSources(config.collectCoverageFrom)
        const undeclared = fs.readdirSync(root)
            .filter(entry => !(entry in NOT_PRODUCTION))
            .filter(entry => !declared.includes(entry))
            .filter(hasJavaScript)
        expect(undeclared).toEqual([])
    })

    it("does not collect coverage from the test tree", () => {
        expect(declaredSources(config.collectCoverageFrom)).not.toContain('__tests__')
    })

    /*
     * `json-summary` is the machine-readable contract with #1821 and #1822: the coverage report
     * job reads `total.lines.pct` and `total.branches.pct` out of `coverage/coverage-summary.json`
     * and records them as the `COVERAGE.UI_UNIT` metrics. Dropping the reporter, or moving the
     * directory, breaks a consumer that lives in another repository's workflow file.
     */
    it("writes the machine-readable summary #1821 and #1822 read", () => {
        expect(config.coverageReporters).toContain('json-summary')
        expect(config.coverageDirectory).toBe('coverage')
    })

    it("writes the HTML report uploaded as a CI artifact", () => {
        expect(config.coverageReporters).toContain('html')
    })
})
