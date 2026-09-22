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
    // Polyfills jsdom lacks and antd needs, before each test file
    setupFiles: ['<rootDir>/jest.setup.js'],
    moduleNameMapper: {
        // The CommonJS build of @ant-design/icons 6 requires the ESM build of @ant-design/colors,
        // which Jest does not transform: point it at the CommonJS build of the same file (#1852).
        '^@ant-design/colors/es/(.*)$': '@ant-design/colors/lib/$1',
    },
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