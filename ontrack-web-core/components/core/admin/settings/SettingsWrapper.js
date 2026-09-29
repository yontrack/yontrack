import {Dynamic} from "@components/common/Dynamic";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import PageSection from "@components/common/PageSection";

export default function SettingsWrapper({entryId}) {

    const {data: entry} = useQuery(
        gql`
            query SettingsEntry($id: String!) {
                settings {
                    settingsById(id: $id) {
                        id
                        title
                        values
                    }
                }
            }
        `,
        {
            variables: {id: entryId},
            deps: [entryId],
            condition: !!entryId,
            dataFn: data => data.settings.settingsById,
        }
    )

    return (
        <>
            {
                entry &&
                <PageSection
                    title={entry.title}
                    padding={true}
                >
                    <Dynamic
                        path={`framework/settings/${entry.id}-form`}
                        props={{...entry.values, id: entry.id}}
                    />
                </PageSection>
            }
        </>
    )
}