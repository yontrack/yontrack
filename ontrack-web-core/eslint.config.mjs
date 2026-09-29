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
        // Advisory only: lint does not run in CI.
        // - antd 6 deprecates `List` (#1853)
        // - the frontend helpers removed in 6.0 (#1929) must not come back
        rules: {
            "no-restricted-imports": ["error", {
                paths: [
                    {
                        name: "antd",
                        importNames: ["List"],
                        message: "antd's List is deprecated - use ItemList from @components/common/ItemList.",
                    },
                    {
                        name: "@components/services/graphql-utils",
                        importNames: ["getUserErrors"],
                        message: "getUserErrors was removed in 6.0 - use getGraphQLErrors(data, userNodeName).",
                    },
                    {
                        name: "@components/services/fragments",
                        importNames: ["getPromotionLevelById", "usePromotionLevel"],
                        message: "Removed in 6.0 - use usePromotionLevelById({id}), or gqlPromotionLevelByIdQuery with callGraphQL.",
                    },
                    {
                        name: "@components/services/fragments",
                        importNames: ["getValidationStampById", "useValidationStamp"],
                        message: "Removed in 6.0 - use useValidationStampById({id}), or gqlValidationStampByIdQuery with callGraphQL.",
                    },
                    {
                        name: "@components/services/fragments",
                        importNames: ["gqlProjectCommonFragment", "gqlBranchCommonFragment"],
                        message: "Removed in 6.0 - use gqlProjectContentFragment / gqlBranchContentFragment.",
                    },
                ],
                patterns: [
                    {
                        group: ["**/services/useQuery"],
                        message: "services/useQuery was removed in 6.0 - use useQuery from @components/services/GraphQL.",
                    },
                    {
                        group: ["**/common/StateUtils"],
                        message: "useReloadState was removed in 6.0 - use useRefresh from @components/common/RefreshUtils.",
                    },
                ],
            }],
            "no-restricted-syntax": ["error",
                {
                    selector: "VariableDeclarator[init.callee.name='useProjectEntityPageInfo'] > ObjectPattern > Property[key.name='closeUri']",
                    message: "closeUri was removed in 6.0 - use uri.",
                },
                {
                    selector: "MemberExpression[object.callee.name='useProjectEntityPageInfo'][property.name='closeUri']",
                    message: "closeUri was removed in 6.0 - use uri.",
                },
            ],
        },
    },
    globalIgnores(["build/**", "reports/**", "coverage/**"]),
])
