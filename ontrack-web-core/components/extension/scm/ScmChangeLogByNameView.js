import {useQuery} from "@components/services/GraphQL";
import ScmChangeLogContent from "@components/extension/scm/ScmChangeLogContent";
import {gqlChangeLogByName} from "@components/extension/scm/ScmChangeLogQueries";

/**
 * Change log between two builds of a project, identified by their names or display names.
 *
 * This is the change log behind the permalinks which can be written by hand, without
 * knowing the IDs of the builds.
 */
export default function ScmChangeLogByNameView({project, from, to, fromBranch, toBranch}) {

    const ready = !!project && !!from && !!to

    const {data: changeLog, loading: fetching, error, finished} = useQuery(
        gqlChangeLogByName,
        {
            variables: {project, from, to, fromBranch, toBranch},
            deps: [project, from, to, fromBranch, toBranch],
            condition: ready,
            initialData: {
                buildFrom: {},
                buildTo: {},
            },
            dataFn: data => data.scmChangeLogByName,
        }
    )

    // `useQuery` starts with `fetching` false and only flips it inside its effect
    const loading = fetching || (ready && !finished)

    return <ScmChangeLogContent changeLog={changeLog} loading={loading} error={error}/>
}
