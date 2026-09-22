import {defineConfig, globalIgnores} from "eslint/config"
import nextCoreWebVitals from "eslint-config-next/core-web-vitals"

// ESLint 9 flat config (#1787) - the same `next/core-web-vitals` rules the
// `.eslintrc.json` extended before `next lint` was removed in Next 16.
export default defineConfig([
    ...nextCoreWebVitals,
    {
        // New in `eslint-plugin-react-hooks` 7, which `eslint-config-next` 16
        // brings in: the React Compiler rules. The code base was not written
        // against them and the upgrade is not the place to adopt them.
        rules: {
            "react-hooks/set-state-in-effect": "off",
            "react-hooks/refs": "off",
            "react-hooks/static-components": "off",
            "react-hooks/immutability": "off",
            "react-hooks/purity": "off",
            "react-hooks/preserve-manual-memoization": "off",
        },
    },
    {
        // antd 6 deprecates `List` (#1853). Advisory only: lint does not run in CI.
        rules: {
            "no-restricted-imports": ["error", {
                paths: [{
                    name: "antd",
                    importNames: ["List"],
                    message: "antd's List is deprecated - use ItemList from @components/common/ItemList.",
                }],
            }],
        },
    },
    globalIgnores(["build/**", "reports/**", "coverage/**"]),
])
