import {Select} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {gqlEventsAgents} from "@components/core/admin/events/eventsQueries";
import {ACTOR_ALL, eventsActorOptions} from "@components/core/admin/events/eventsFilter";

/**
 * Actor filter of the events page (#2031): all the events, those of the persons, those of the
 * agents, or those of one registered agent.
 *
 * @param id ID of the field, and its test ID
 * @param value Selected actor, as given to the `actor` filter of the `events` query
 * @param onChange Called with the selected actor
 */
export default function SelectEventsActor({id, value, onChange}) {
    const {data: agents, loading} = useQuery(gqlEventsAgents, {
        initialData: [],
        dataFn: data => data.agents,
    })

    return (
        <Select
            id={id}
            data-testid={id}
            aria-label="Actor"
            value={value ?? ACTOR_ALL}
            onChange={onChange}
            options={eventsActorOptions(agents ?? [])}
            loading={loading}
            showSearch={{optionFilterProp: "label"}}
            popupMatchSelectWidth={false}
            style={{minWidth: "14em"}}
        />
    )
}
