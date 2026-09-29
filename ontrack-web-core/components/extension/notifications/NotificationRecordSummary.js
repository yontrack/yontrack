import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {gqlNotificationRecordContent} from "@components/extension/notifications/NotificationRecordsGraphQLFragments";
import LoadingInline from "@components/common/LoadingInline";
import {Divider, Space} from "antd";
import Link from "next/link";
import {FaInfoCircle} from "react-icons/fa";
import NotificationSourceData from "@components/extension/notifications/NotificationSourceData";
import EventDetails from "@components/core/model/EventDetails";

const noRecord = {}

export default function NotificationRecordSummary({recordId}) {

    const {data, loading, finished} = useQuery(
        gql`
            query NotificationRecord($recordId: String!) {
                notificationRecord(id: $recordId) {
                    ...NotificationRecordContent
                }
            }

            ${gqlNotificationRecordContent}
        `,
        {
            variables: {recordId},
            deps: [recordId],
            dataFn: data => data.notificationRecord,
        }
    )
    const record = data ?? noRecord

    return (
        <>
            <LoadingInline loading={loading || !finished}>
                <Space>
                    {/* Link to the record */}
                    <Link href={`/extension/notifications/recordings/${record.id}`}
                          title="Link to the full notification record">
                        <FaInfoCircle/>
                    </Link>
                    {/* Notification source */}
                    {
                        record.source && <>
                            <Divider orientation="vertical"/>
                            <NotificationSourceData source={record.source}/>
                        </>
                    }
                    {/* Event */}
                    {
                        record.event && <>
                            <Divider orientation="vertical"/>
                            <EventDetails event={record.event}/>
                        </>
                    }
                </Space>
            </LoadingInline>
        </>
    )
}