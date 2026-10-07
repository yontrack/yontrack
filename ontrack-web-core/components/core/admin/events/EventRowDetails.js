import {Descriptions, Space, Typography} from "antd";
import Link from "next/link";
import Table from "@components/common/table/Table";
import EventDisplay from "@components/core/model/EventDisplay";
import {eventEntityLink} from "@components/core/admin/events/eventsFilter";

function EventEntities({entities}) {
    if (!entities || entities.length === 0) {
        return <Typography.Text type="secondary" italic>None</Typography.Text>
    }
    return (
        <Space orientation="vertical" size={0}>
            {
                entities.map(entity => {
                    const {href, text} = eventEntityLink(entity)
                    return (
                        <span key={`${entity.type}-${entity.id}`}>
                            {href ? <Link href={href}>{text}</Link> : text}
                        </span>
                    )
                })
            }
        </Space>
    )
}

/**
 * Details of an event, in the expanded row of the events page: its type, the entities it is
 * about, its reference and its values.
 */
export default function EventRowDetails({event}) {
    return (
        <Descriptions
            data-testid={`event-details-${event.id}`}
            column={1}
            size="small"
            bordered
            items={[
                {
                    key: 'type',
                    label: 'Type',
                    children: <EventDisplay event={event.eventType.id}/>,
                },
                {
                    key: 'entities',
                    label: 'Entities',
                    children: <EventEntities entities={event.entities}/>,
                },
                {
                    key: 'extraEntities',
                    label: 'Extra entities',
                    children: <EventEntities entities={event.extraEntities}/>,
                },
                {
                    key: 'ref',
                    label: 'Reference',
                    children: event.ref ?
                        <Typography.Text code>{event.ref}</Typography.Text> :
                        <Typography.Text type="secondary" italic>None</Typography.Text>,
                },
                {
                    key: 'values',
                    label: 'Values',
                    children: event.values.length > 0 ?
                        <Table
                            data-testid={`event-values-${event.id}`}
                            sticky={false}
                            size="small"
                            pagination={false}
                            rowKey="name"
                            dataSource={event.values}
                            columns={[
                                {
                                    key: 'name',
                                    title: 'Name',
                                    dataIndex: 'name',
                                    render: (value) => <Typography.Text code>{value}</Typography.Text>,
                                },
                                {
                                    key: 'value',
                                    title: 'Value',
                                    dataIndex: 'value',
                                },
                            ]}
                        /> :
                        <Typography.Text type="secondary" italic>None</Typography.Text>,
                },
            ]}
        />
    )
}
