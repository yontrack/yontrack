import {generate} from "@ontrack/utils";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";
import {createBranch} from "@ontrack/branch";
import {registerNotificationExtensions} from "@ontrack/extensions/notifications/notifications";

const gqlProjectData = gql`
    fragment ProjectData on Project {
        id
        name
        disabled
    }
`

export const projectList = async (ontrack) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query ProjectList {
                projects {
                    ...ProjectData
                }
            }
            ${gqlProjectData}
        `
    )
    return data.projects.map(it => projectInstance(ontrack, it))
}

export const getProjectById = async (ontrack, id) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query GetProjectById($id: Int!) {
                project(id: $id) {
                    ...ProjectData
                }
            }
            ${gqlProjectData}
        `,
        {id: Number(id)}
    )
    return projectInstance(ontrack, data.project)
}

export const createProject = async (ontrack, name) => {
    const actualName = name ?? generate('prj_')

    const data = await graphQLCallMutation(
        ontrack.connection,
        'createProject',
        gql`
            mutation CreateProject(
                $name: String!,
            ) {
                createProject(input: {
                    name: $name,
                }) {
                    project {
                        ...ProjectData
                    }
                    errors {
                        message
                    }
                }
            }
            ${gqlProjectData}
        `,
        {
            name: actualName,
        }
    )

    return projectInstance(ontrack, data.createProject.project)
}

export const projectInstance = (ontrack, data) => {
    const project = {
        ontrack,
        type: 'PROJECT',
        ...data,
    }

    project.createBranch = async (name) => createBranch(project, name)
    project.favourite = async () => favouriteProject(project)
    project.setProperty = async (type, value) => setProjectProperty(project, type, value)

    // Notifications methods
    registerNotificationExtensions(project)

    return project
}

/**
 * Sets a property on the project, by the FQCN of its type and its raw JSON value.
 */
const setProjectProperty = async (project, type, value) => {
    await graphQLCallMutation(
        project.ontrack.connection,
        'setGenericProperty',
        gql`
            mutation SetProjectProperty(
                $id: Int!,
                $type: String!,
                $value: JSON!,
            ) {
                setGenericProperty(input: {
                    entityType: PROJECT,
                    entityId: $id,
                    type: $type,
                    value: $value,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            id: Number(project.id),
            type,
            value,
        }
    )
}

/**
 * Marks the project as a favourite of the account the connection authenticates as - which is the
 * same account the browser signs in with, so what this sets is what the UI then shows. The same
 * shape as `branch.favourite`, for the same reason.
 */
const favouriteProject = async (project) => {
    await graphQLCallMutation(
        project.ontrack.connection,
        'favouriteProject',
        gql`
            mutation FavouriteProject($projectId: Int!) {
                favouriteProject(input: {id: $projectId}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {projectId: Number(project.id)}
    )
}
