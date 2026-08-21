import {useQuery} from "@components/services/GraphQL";
import ScmChangeLogContent from "@components/extension/scm/ScmChangeLogContent";
import {gqlChangeLogById} from "@components/extension/scm/ScmChangeLogQueries";

/**
 * Change log between two builds, identified by their IDs.
 */
export default function ScmChangeLogView({from, to}) {

    const ready = !!from && !!to

    const {data: changeLog, loading: fetching, error, finished} = useQuery(
        gqlChangeLogById,
        {
            variables: {from, to},
            deps: [from, to],
            condition: ready,
            initialData: {
                buildFrom: {},
                buildTo: {},
            },
            dataFn: data => data.scmChangeLog,
        }
    )

    // `useQuery` starts with `fetching` false and only flips it inside its effect
    const loading = fetching || (ready && !finished)

    return <ScmChangeLogContent changeLog={changeLog} loading={loading} error={error}/>
}
