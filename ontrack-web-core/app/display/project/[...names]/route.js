import {NextResponse} from 'next/server'
import {graphQL} from "@/app/api/protected/graphql/graphql";
import {gql} from "graphql-request";
import {redirect} from "next/navigation";

export async function GET(request, props) {
    const params = await props.params
    const names = params.names
    if (names.length === 0) {
        return NextResponse.json({error: 'Missing project name'}, {status: 400})
    } else if (names.length > 1) {
        return NextResponse.json({error: 'Too many arguments'}, {status: 400})
    } else {
        const project = names[0]
        const {data, response} = await graphQL(request, {
            query: gql`
                query GetProjectByName($project: String!) {
                    projects(name: $project) {
                        id
                    }
                }
            `,
            variables: {
                project,
            }
        })
        if (response) {
            return response
        } else if (data) {
            const projects = data.projects
            if (projects) {
                const id = projects[0].id
                redirect(`/project/${id}`)
            } else {
                return NextResponse.json({error: 'No project found'}, {status: 404})
            }
        } else {
            return NextResponse.json({error: 'No data returned'}, {status: 404})
        }
    }
}