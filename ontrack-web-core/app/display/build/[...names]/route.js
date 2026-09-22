import {NextResponse} from 'next/server'
import {redirect} from "next/navigation";
import {graphQL} from "@/app/api/protected/graphql/graphql";
import {gql} from "graphql-request";

const findBuildByRelease = async (request, project, branch, release) => {
    const {data, response} = await graphQL(request, {
        query: gql`
            query GetBuildByRelease($project: String!, $branch: String!, $release: String!) {
                builds(
                    project: $project,
                    branch: $branch,
                    buildBranchFilter: {
                        count: 1,
                        withProperty: "net.nemerosa.ontrack.extension.general.ReleasePropertyType",
                        withPropertyValue: $release
                    }
                ) {
                    id
                }
            }
        `,
        variables: {
            project,
            branch,
            release,
        }
    })
    if (response) {
        return {response, build: null}
    } else if (data) {
        const builds = data.builds
        if (builds && builds.length > 0) {
            return {build: builds[0], response: null}
        } else {
            return {build: null, response: null}
        }
    } else {
        return {build: null, response: null}
    }
}

const findBuildByExactName = async (request, project, branch, name) => {
    const {data, response} = await graphQL(request, {
        query: gql`
            query GetBuildByName($project: String!, $branch: String!, $name: String!) {
                builds(
                    project: $project,
                    branch: $branch,
                    name: $name,
                ) {
                    id
                }
            }
        `,
        variables: {
            project,
            branch,
            name,
        }
    })
    if (response) {
        return {response, build: null}
    } else if (data) {
        const builds = data.builds
        if (builds && builds.length > 0) {
            return {build: builds[0], response: null}
        } else {
            return {build: null, response: null}
        }
    } else {
        return {build: null, response: null}
    }
}

const findBuildByName = async (request, project, branch, name) => {
    const {build, response} = await findBuildByRelease(request, project, branch, name)
    if (response) {
        return {response, build: null}
    } else if (build) {
        return {build, response: null}
    } else {
        return findBuildByExactName(request, project, branch, name)
    }
}

export async function GET(request, props) {
    const params = await props.params
    const names = params.names
    if (names.length < 3) {
        return NextResponse.json({error: 'Missing build name'}, {status: 400})
    } else if (names.length > 3) {
        return NextResponse.json({error: 'Too many arguments'}, {status: 400})
    } else {
        const project = names[0]
        const branch = names[1]
        const name = names[2]
        const {build, response} = await findBuildByName(request, project, branch, name)
        if (response) {
            return response
        } else if (build) {
            const id = build.id
            redirect(`/build/${id}`)
        } else {
            return NextResponse.json({error: 'No build found'}, {status: 404})
        }
    }
}