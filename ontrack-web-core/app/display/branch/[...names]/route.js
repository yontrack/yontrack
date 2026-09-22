import {NextResponse} from 'next/server'
import {graphQL} from "@/app/api/protected/graphql/graphql";
import {gql} from "graphql-request";
import {redirect} from "next/navigation";

export async function GET(request, props) {
    const params = await props.params
    const names = params.names
    if (names.length < 2) {
        return NextResponse.json({error: 'Missing branch name'}, {status: 400})
    } else if (names.length > 2) {
        return NextResponse.json({error: 'Too many arguments'}, {status: 400})
    } else {
        const project = names[0]
        const branch = names[1]
        const {data, response} = await graphQL(request, {
            query: gql`
                query GetBranchByName($project: String!, $branch: String!) {
                    branchByName(project: $project, name: $branch) {
                        id
                    }
                }
            `,
            variables: {
                project,
                branch,
            }
        })
        if (response) {
            return response
        } else if (data) {
            const branchByName = data.branchByName
            if (branchByName) {
                const id = branchByName.id
                redirect(`/branch/${id}`)
            } else {
                return NextResponse.json({error: 'No branch found'}, {status: 404})
            }
        } else {
            return NextResponse.json({error: 'No data returned'}, {status: 404})
        }
    }
}