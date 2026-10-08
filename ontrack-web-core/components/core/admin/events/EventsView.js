import {DatePicker, Form, Input} from "antd";
import StandardTable from "@components/common/table/StandardTable";
import TimestampText from "@components/common/TimestampText";
import EventDisplay from "@components/core/model/EventDisplay";
import SafeHTMLComponent from "@components/common/SafeHTMLComponent";
import ProjectLink from "@components/projects/ProjectLink";
import SelectMultipleEvents from "@components/core/model/SelectMultipleEvents";
import SelectProject from "@components/projects/SelectProject";
import EventRowDetails from "@components/core/admin/events/EventRowDetails";
import {eventsFilterVariables} from "@components/core/admin/events/eventsFilter";
import EventsExportButtons from "@components/core/admin/events/EventsExportButtons";
import SelectEventsActor from "@components/core/admin/events/SelectEventsActor";
import ActorBadge from "@components/common/actors/ActorBadge";
import {gqlEvents} from "@components/core/admin/events/eventsQueries";

/**
 * Read-only list of all the events of the instance, newest first. The `events` query requires
 * the events audit function: without it, the query is refused and its error is displayed.
 *
 * The events can be filtered on their actor - persons, agents, or one agent - and the user of an
 * event done by an agent is shown as the badge of the agent.
 *
 * The events matching the applied filter can be downloaded as CSV or JSON.
 */
export default function EventsView() {
    return (
        <StandardTable
            id="events"
            query={gqlEvents}
            queryNode="events"
            size={20}
            rowKey={event => event.id}
            filterFormVariables={eventsFilterVariables}
            filterExtraButtons={(filterFormData) => [
                <EventsExportButtons key="export" filterFormData={filterFormData}/>,
            ]}
            filterForm={[
                <Form.Item
                    key="range"
                    name="range"
                    label="Time"
                >
                    <DatePicker.RangePicker
                        showTime={{format: "HH:mm"}}
                        format="YYYY-MM-DD HH:mm"
                        allowEmpty={[true, true]}
                    />
                </Form.Item>,
                <Form.Item
                    key="user"
                    name="user"
                    label="User"
                >
                    <Input
                        data-testid="events-filter-user"
                        placeholder="User name prefix"
                        style={{width: "14em"}}
                        allowClear
                    />
                </Form.Item>,
                <Form.Item
                    key="eventTypes"
                    name="eventTypes"
                    label="Event types"
                >
                    <SelectMultipleEvents
                        id="events-filter-event-types"
                        style={{minWidth: "20em"}}
                    />
                </Form.Item>,
                <Form.Item
                    key="project"
                    name="project"
                    label="Project"
                >
                    <SelectProject id="events-filter-project"/>
                </Form.Item>,
                <Form.Item
                    key="actor"
                    name="actor"
                    label="Actor"
                >
                    <SelectEventsActor id="events-filter-actor"/>
                </Form.Item>,
            ]}
            columns={[
                {
                    key: 'time',
                    title: 'Time',
                    dataIndex: 'time',
                    render: (value) => <TimestampText value={value} format="YYYY MMM DD, HH:mm:ss"/>,
                },
                {
                    key: 'user',
                    title: 'User',
                    render: (_, event) => <ActorBadge signature={event} testId={`event-actor-${event.id}`}/>,
                },
                {
                    key: 'type',
                    title: 'Type',
                    render: (_, event) => <EventDisplay event={event.eventType.id}/>,
                },
                {
                    key: 'message',
                    title: 'Message',
                    dataIndex: 'message',
                    render: (value) => <SafeHTMLComponent htmlContent={value}/>,
                },
                {
                    key: 'project',
                    title: 'Project',
                    render: (_, event) => event.project ? <ProjectLink project={event.project}/> : null,
                },
            ]}
            expandable={{
                expandedRowRender: (event) => <EventRowDetails event={event}/>,
            }}
        />
    )
}
