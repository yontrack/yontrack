import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import Link from "next/link";
import {CloseCommand} from "@components/common/Commands";
import NotificationRecordDetails from "@components/extension/notifications/NotificationRecordDetails";
import LoadingContainer from "@components/common/LoadingContainer";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {gqlNotificationRecordContent} from "@components/extension/notifications/NotificationRecordsGraphQLFragments";

const EMPTY_RECORD = {}

export default function NotificationRecordingView({id}) {

    const {data, loading, finished} = useQuery(
        gql`
            query NotificationRecord($id: String!) {
                notificationRecord(id: $id) {
                    ...NotificationRecordContent
                }
            }

            ${gqlNotificationRecordContent}
        `,
        {
            variables: {id},
            deps: [id],
            dataFn: data => data.notificationRecord,
        }
    )
    const record = data ?? EMPTY_RECORD

    return (
        <>
            <Head>
                {pageTitle(`Notification recording ${id}`)}
            </Head>
            <MainPage
                title={id}
                breadcrumbs={[
                    ...homeBreadcrumbs(),
                    <Link
                        key="notifications"
                        href={'/extension/notifications/recordings'}
                    >
                        Notification recordings
                    </Link>,
                ]}
                commands={[
                    <CloseCommand key="close" href={'/extension/notifications/recordings'}/>
                ]}
            >
                <LoadingContainer loading={loading || !finished}>
                    <NotificationRecordDetails record={record} includeAll={true}/>
                </LoadingContainer>
            </MainPage>
        </>
    )
}