import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";

export const gqlUserMenuActionFragment = gql`
    fragment userMenuActionFragment on UserMenuAction {
        groupId
        extension
        id
        name
        local
        arguments
    }
`

export const gqlPromotionLevelFragment = gql`
    fragment PromotionLevelData on PromotionLevel {
        id
        name
        description
        image
        fields {
            id
            name
            displayName
            description
            type
            required
            options
            position
        }
        authorizations {
            name
            action
            authorized
        }
        userMenuActions {
            ...userMenuActionFragment
        }
        branch {
            id
            name
            project {
                id
                name
            }
        }
    }
    ${gqlUserMenuActionFragment}
`

export const usePromotionLevelById = ({id, refreshCount = 0}) => {
    const {data: promotionLevel, loading} = useQuery(
        gqlPromotionLevelByIdQuery,
        {
            variables: {
                id: Number(id)
            },
            deps: [refreshCount],
            dataFn: data => data.promotionLevel,
        }
    )
    return {promotionLevel, loading}
}

export const useBuild = (id) => {
    const {loading, data: build} = useQuery(
        gql`
            query Build($id: Int!) {
                build(id: $id) {
                    id
                    name
                    displayName
                    creation {
                        time
                        user
                    }
                    description
                    annotatedDescription
                    branch {
                        id
                        name
                        displayName
                        project {
                            id
                            name
                        }
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            dataFn: data => data.build,
        }
    )
    return {loading, build}
}

export const useBranch = (id) => {
    const {loading, data: branch} = useQuery(
        gql`
            query Branch($id: Int!) {
                branch(id: $id) {
                    id
                    name
                    displayName
                    creation {
                        time
                        user
                    }
                    description
                    annotatedDescription
                    project {
                        id
                        name
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            initialData: {project: {}},
            dataFn: data => data.branch,
        }
    )
    return {loading, branch}
}

export const useProject = (id) => {
    const {loading, data: project} = useQuery(
        gql`
            query Project($id: Int!) {
                project(id: $id) {
                    id
                    name
                    creation {
                        time
                        user
                    }
                    description
                    annotatedDescription
                }
            }
        `,
        {
            variables: {id: Number(id)},
            dataFn: data => data.project,
        }
    )
    return {loading, project}
}

export const gqlValidationStampFragment = gql`
    fragment ValidationStampData on ValidationStamp {
        id
        name
        description
        image
        dataType {
            descriptor {
                id
                displayName
            }
            config
            formConfig
        }
        authorizations {
            name
            action
            authorized
        }
        charts {
            id
            title
            type
            config
            parameters
        }
        branch {
            id
            name
            project {
                id
                name
            }
        }
    }
`

export const useValidationStampById = ({id, refreshCount = 0, deps = []}) => {
    const {data: validationStamp, loading} = useQuery(
        gqlValidationStampByIdQuery,
        {
            variables: {
                id: Number(id)
            },
            deps: [refreshCount, ...deps],
            dataFn: data => data.validationStamp,
        }
    )
    return {validationStamp, loading}
}

export const gqlDecorationFragment = gql`
    fragment decorationContent on Decoration {
        decorationType
        error
        data
        feature {
            id
        }
    }
`

export const gqlPropertiesFragment = gql`
    fragment propertiesFragment on Property {
        type {
            typeName
            name
        }
        editable
        value
    }
`

export const gqlInformationFragment = gql`
    fragment informationFragment on EntityInformation {
        type
        title
        data
    }
`

export const gqlValidationStampByIdQuery = gql`
    query ValidationStampById($id: Int!) {
        validationStamp(id: $id) {
            ...ValidationStampData
            properties {
                ...propertiesFragment
            }
            information {
                ...informationFragment
            }
            userMenuActions {
                ...userMenuActionFragment
            }
        }
    }
    ${gqlValidationStampFragment}
    ${gqlPropertiesFragment}
    ${gqlInformationFragment}
    ${gqlUserMenuActionFragment}
`

export const gqlPromotionLevelByIdQuery = gql`
    query PromotionLevelById($id: Int!) {
        promotionLevel(id: $id) {
            ...PromotionLevelData
            properties {
                ...propertiesFragment
            }
            information {
                ...informationFragment
            }
            decorations {
                ...decorationContent
            }
        }
    }
    ${gqlPromotionLevelFragment}
    ${gqlPropertiesFragment}
    ${gqlInformationFragment}
    ${gqlDecorationFragment}
`
