import {gql} from "graphql-request";

export const gqlDashboardFragment = gql`
    fragment DashboardData on Dashboard {
        uuid
        name
        userScope
        asYaml
        authorizations {
            edit
            share
            delete
        }
        widgets {
            uuid
            key
            config
            layout {
                x
                y
                w
                h
            }
        }
    }
`

export const saveDashboardQuery = gql`
    mutation SaveDashboard(
        $uuid: String,
        $name: String!,
        $userScope: DashboardContextUserScope!,
        $widgets: [WidgetInstanceInput!]!,
    ) {
        saveDashboard(input: {
            uuid: $uuid,
            name: $name,
            userScope: $userScope,
            widgets: $widgets,
            select: true,
        }) {
            errors {
                message
            }
            dashboard {
                ...DashboardData
            }
        }
    }
    
    ${gqlDashboardFragment}
`

export const shareDashboardQuery = gql`
    mutation ShareDashboard($uuid: String!) {
        shareDashboard(input: {uuid: $uuid}) {
            errors {
                message
            }
            dashboard {
                ...DashboardData
            }
        }
    }

    ${gqlDashboardFragment}
`
export const deleteDashboardQuery = gql`
    mutation DeleteDashboard($uuid: String!) {
        deleteDashboard(input: {uuid: $uuid}) {
            errors {
                message
            }
        }
    }
`

export const loadDashboardsQuery = gql`
    query LoadDashboards {
        userDashboards {
            ...DashboardData
        }
        userDashboard {
            ...DashboardData
        }
    }
    ${gqlDashboardFragment}
`

export const selectDashboardQuery = gql`
    mutation SelectDashboard($uuid: String!) {
        selectDashboard(input: {uuid: $uuid}) {
            errors {
                message
            }
        }
    }
`
