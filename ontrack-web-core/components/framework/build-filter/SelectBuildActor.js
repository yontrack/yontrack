import {useState} from "react";
import {Select} from "antd";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import {buildActorOptions} from "@components/framework/build-filter/buildFilterAgents";

const NO_AGENTS = []

/**
 * Actor criterion of the build filters (#2036): the builds of the persons, those of any agent, or
 * those of one agent - a registered agent the user can see, or the identifier of any other one,
 * typed in the control.
 *
 * @param id ID of the field, and its test ID
 * @param value Selected actor, as given to the `actor` field of the build filters
 * @param onChange Called with the selected actor
 */
export default function SelectBuildActor({id, value, onChange}) {
    const [search, setSearch] = useState("")

    const {data, loading} = useQuery(
        gql`
            query BuildFilterAgents {
                agents {
                    id
                    email
                    fullName
                }
            }
        `,
        {
            initialData: NO_AGENTS,
            dataFn: data => data.agents,
        }
    )

    return (
        <Select
            id={id}
            data-testid={id}
            value={value}
            onChange={onChange}
            options={buildActorOptions(data ?? NO_AGENTS, search, value)}
            loading={loading}
            allowClear={true}
            placeholder="Any actor"
            showSearch={{optionFilterProp: "label", onSearch: setSearch}}
            popupMatchSelectWidth={false}
            style={{minWidth: "14em"}}
        />
    )
}
