import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {useContext, useState} from "react";
import {UserContext} from "@components/providers/UserProvider";

export const useProjectBuildSearch = ({project}) => {

    const [values, setValues] = useState({})

    const user = useContext(UserContext)

    // No search is run until the form is submitted
    const [searchCount, setSearchCount] = useState(0)

    const {data, loading} = useQuery(
        gql`
            query ProjectBuildSearch(
                $projectName: String!,
                $filter: BuildSearchForm,
            ) {
                builds(
                    project: $projectName,
                    buildProjectFilter: $filter,
                ) {
                    id
                    name
                    releaseProperty {
                        value
                    }
                    branch {
                        id
                        name
                        displayName
                    }
                    promotionRuns(lastPerLevel: true) {
                        id
                        creation {
                            time
                        }
                        promotionLevel {
                            id
                            name
                            description
                            image
                        }
                    }
                }
            }
        `,
        {
            condition: searchCount > 0,
            variables: {
                projectName: project?.name,
                filter: {
                    ...values,
                    buildExactMatch: true,
                },
            },
            deps: [project?.name, values, searchCount],
            dataFn: data => data.builds,
        }
    )

    // Builds are marked as selected locally, on top of the search result they were made against,
    // until the next search result replaces them
    const [selection, setSelection] = useState({source: null, builds: undefined})
    const builds = selection.source === data ? selection.builds : (data ?? undefined)
    const setBuilds = (update) => setSelection(previous => {
        const current = previous.source === data ? previous.builds : data
        return {
            source: data,
            builds: typeof update === 'function' ? update(current) : update,
        }
    })

    const search = (values) => {
        const extensions = []
        if (user.authorizations.environment?.view && values.environmentName) {
            extensions.push({
                extension: "environment",
                value: values.environmentName,
            })
            delete values.environmentName
        }
        setValues({
            ...values,
            extensions,
        })
        setSearchCount(count => count + 1)
    }

    return {
        builds,
        setBuilds,
        loading,
        search,
    }
}