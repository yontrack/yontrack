const nextJest = require('next/jest')
const coverage = require('./jest.coverage')

/** @type {import('jest').Config} */
const createJestConfig = nextJest({
    // Provide the path to your Next.js app to load next.config.js and .env files in your test environment
    dir: './',
})

// Add any custom config to be passed to Jest
const config = {
    coverageProvider: 'v8',
    // What `npm run test:coverage` collects and where it writes it (#1820). Nothing here has any
    // effect on a plain `npm run test`, which never passes `--coverage`: coverage is collected on
    // coverage runs only. See `jest.coverage.js` for the denominator and for the contract the
    // JSON summary is.
    ...coverage,
    testEnvironment: 'jsdom',
    // The default 5s budget is tight for the component tests: the first `render()` in a worker pays
    // antd's cssinjs cold start, which on a contended CI agent can blow past 5s before the test's own
    // `waitFor` (1s) ever gets a chance to run.
    testTimeout: 20000,
    // Add more setup options before each test is run
    // setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
    // Reports for JUnit
    reporters: [
        "default",
        ["jest-junit", {
            "outputDirectory": "reports",
            "outputName": "junit.xml"
        }],
    ]
}

// createJestConfig is exported this way to ensure that next/jest can load the Next.js config which is async
module.exports = createJestConfig(config)