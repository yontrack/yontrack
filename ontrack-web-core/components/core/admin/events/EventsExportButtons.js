import {Alert, Button} from "antd";
import {FaDownload} from "react-icons/fa";
import {useQuery} from "@components/services/GraphQL";
import {eventsExportUri} from "@components/common/Links";
import {eventsFilterVariables} from "@components/core/admin/events/eventsFilter";
import {gqlEventsExport} from "@components/core/admin/events/eventsQueries";

/**
 * Downloads of the events matching the filter applied to the events page, as CSV or JSON, with a
 * warning when there are more of them than an export holds.
 *
 * The buttons are plain links, so that the browser streams the file to the disk.
 *
 * @param filterFormData Values of the filter form which is applied
 */
export default function EventsExportButtons({filterFormData}) {
    const variables = eventsFilterVariables(filterFormData)

    const {data: info} = useQuery(gqlEventsExport, {
        variables,
        deps: [filterFormData],
        dataFn: data => data.eventsExport,
    })

    return (
        <>
            <Button
                href={eventsExportUri("csv", variables)}
                download
                icon={<FaDownload/>}
                data-testid="events-export-csv"
            >
                Download CSV
            </Button>
            <Button
                href={eventsExportUri("json", variables)}
                download
                icon={<FaDownload/>}
                data-testid="events-export-json"
            >
                Download JSON
            </Button>
            {
                info?.truncated &&
                <Alert
                    type="warning"
                    showIcon
                    data-testid="events-export-truncated"
                    title={`Only the ${info.maxRows} most recent matching events will be exported: narrow the filter to get them all.`}
                />
            }
        </>
    )
}
