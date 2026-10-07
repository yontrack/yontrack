import StandardPage from "@components/layouts/StandardPage";
import {CloseToHomeCommand} from "@components/common/Commands";
import EventsView from "@components/core/admin/events/EventsView";

export default function EventsPage() {
    return (
        <>
            <StandardPage
                pageTitle="Events"
                commands={[
                    <CloseToHomeCommand key="home"/>,
                ]}
            >
                <EventsView/>
            </StandardPage>
        </>
    )
}
